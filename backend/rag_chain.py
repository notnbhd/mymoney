"""
LangChain RAG pipeline for MyMoney financial chatbot.
Uses PostgreSQL + pgvector for hybrid search (Dense + BM25 via RRF),
cross-encoder re-ranking, and OpenRouter for response generation.
"""

import logging
import math
from contextlib import contextmanager
from typing import List, Optional, Tuple

import psycopg2
import psycopg2.extras
import psycopg2.pool
from langchain.schema import Document, HumanMessage, SystemMessage, AIMessage
from langchain_community.cross_encoders import HuggingFaceCrossEncoder
from langchain_openai import ChatOpenAI
from langchain_huggingface import HuggingFaceEmbeddings

from config import settings
from models import FinancialContext, SourceDocument
from memory import conversation_memory

logger = logging.getLogger(__name__)


class RAGChain:
    """
    LangChain-based RAG pipeline that:
    1. Connects to PostgreSQL + pgvector for hybrid search
    2. Retrieves relevant documents using Dense + BM25 (Reciprocal Rank Fusion)
    3. Filters by similarity threshold
    4. Re-ranks results using a cross-encoder model
    5. Builds prompts with financial context from the Android app
    6. Calls the LLM via OpenRouter with conversation memory
    """

    def __init__(self):
        self.embeddings: Optional[HuggingFaceEmbeddings] = None
        self.llm: Optional[ChatOpenAI] = None
        self.cross_encoder: Optional[HuggingFaceCrossEncoder] = None
        self._pool: Optional[psycopg2.pool.ThreadedConnectionPool] = None
        self._doc_count: int = 0
        self._initialized = False

    def initialize(self):
        """Initialize the RAG pipeline: embeddings, reranker, vector store, and LLM."""
        if self._initialized:
            return

        logger.info("Initializing RAG chain (PostgreSQL + pgvector)...")

        # 1. Multilingual embeddings model
        logger.info(f"Loading embedding model: {settings.EMBEDDING_MODEL}")
        self.embeddings = HuggingFaceEmbeddings(
            model_name=settings.EMBEDDING_MODEL,
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True},
        )

        # 2. Cross-encoder re-ranker
        logger.info(f"Loading re-ranker model: {settings.RAG_RERANKER_MODEL}")
        self.cross_encoder = HuggingFaceCrossEncoder(
            model_name=settings.RAG_RERANKER_MODEL
        )
        logger.info("Re-ranker model loaded.")

        # 3. PostgreSQL connection
        self._init_postgres()

        # 4. LLM via OpenRouter
        self.llm = ChatOpenAI(
            model=settings.OPENROUTER_MODEL,
            openai_api_key=settings.OPENROUTER_API_TOKEN,
            openai_api_base=settings.OPENROUTER_BASE_URL,
            temperature=0.1,
            max_tokens=1024,
            default_headers={
                "HTTP-Referer": "https://github.com/notnbhd/mymoney",
                "X-Title": "MyMoney App",
            },
        )

        self._initialized = True
        logger.info(
            f"RAG chain initialized: {self._doc_count} documents, "
            "PostgreSQL hybrid search ready, re-ranker ready"
        )

    # ─── PostgreSQL Initialization ──────────────────────────────────

    # Pool configuration
    _POOL_MIN_CONN = 2   # Always-open connections
    _POOL_MAX_CONN = 10  # Max simultaneous connections

    def _init_postgres(self):
        """Create a ThreadedConnectionPool and verify the knowledge table."""
        try:
            logger.info(
                f"Creating PostgreSQL pool ({self._POOL_MIN_CONN}-{self._POOL_MAX_CONN} conns): "
                f"{settings.POSTGRES_URL}"
            )
            self._pool = psycopg2.pool.ThreadedConnectionPool(
                minconn=self._POOL_MIN_CONN,
                maxconn=self._POOL_MAX_CONN,
                dsn=settings.POSTGRES_URL,
            )

            # Quick sanity check with one connection
            with self._get_conn() as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        "SELECT extname FROM pg_extension WHERE extname = 'vector'"
                    )
                    if not cur.fetchone():
                        raise RuntimeError(
                            "pgvector extension is not installed! "
                            "Run: CREATE EXTENSION vector; in psql"
                        )
                    cur.execute(
                        f"SELECT COUNT(*) FROM {settings.POSTGRES_TABLE}"  # noqa: S608
                    )
                    row = cur.fetchone()
                    self._doc_count = row[0] if row else 0

            logger.info(
                f"PostgreSQL pool ready: {self._doc_count} documents "
                f"in '{settings.POSTGRES_TABLE}'"
            )
            if self._doc_count == 0:
                logger.warning(
                    "knowledge table is empty! "
                    "Run 'python setup_postgres.py' to upload the knowledge base."
                )

        except psycopg2.OperationalError as e:
            raise RuntimeError(f"Failed to connect to PostgreSQL: {e}") from e

    @contextmanager
    def _get_conn(self):
        """
        Context manager: borrow a connection from the pool, return it when done.
        Each asyncio.to_thread() call gets its own dedicated connection,
        so concurrent requests never share a connection.
        """
        conn = self._pool.getconn()
        try:
            conn.autocommit = True
            yield conn
        except Exception:
            # Roll back any open transaction before returning to pool
            try:
                conn.rollback()
            except Exception:
                pass
            raise
        finally:
            self._pool.putconn(conn)

    # ─── Dense Retrieval ────────────────────────────────────────────

    def _retrieve_dense(self, query: str, top_k: int) -> List[Document]:
        """
        Pure vector (cosine) search using pgvector <=> operator.
        Returns docs with metadata['similarity_score'] in [0, 1].
        Each call borrows its own connection from the pool.
        """
        query_embedding = self.embeddings.embed_query(query)
        embedding_literal = "[" + ",".join(map(str, query_embedding)) + "]"

        sql = f"""
            SELECT
                id, topic, category,
                content_vi, content_en, keywords,
                text_content, source_type, page_number,
                1 - (embedding <=> %s::vector) AS similarity_score,
                'dense' AS retrieval_type
            FROM {settings.POSTGRES_TABLE}
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """  # noqa: S608
        try:
            with self._get_conn() as conn:
                with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
                    cur.execute(sql, (embedding_literal, embedding_literal, top_k))
                    rows = cur.fetchall()

            docs = [self._row_to_document(row) for row in rows]
            logger.info(f"Dense search: {len(docs)} docs for query '{query[:50]}...'")
            return docs

        except Exception as e:
            logger.error(f"Dense search failed: {e}")
            return []

    # ─── BM25 / Full-text Retrieval ─────────────────────────────────

    def _retrieve_bm25(self, query: str, top_k: int) -> List[Document]:
        """
        Full-text search using PostgreSQL's ts_rank over the GIN-indexed
        search_vector column (tsvector built from content_vi + topic + keywords).
        Each call borrows its own connection from the pool.
        """
        sql = f"""
            SELECT
                id, topic, category,
                content_vi, content_en, keywords,
                text_content, source_type, page_number,
                ts_rank(search_vector,
                        plainto_tsquery('simple', %s)) AS similarity_score,
                'bm25' AS retrieval_type
            FROM {settings.POSTGRES_TABLE}
            WHERE search_vector @@ plainto_tsquery('simple', %s)
            ORDER BY similarity_score DESC
            LIMIT %s
        """  # noqa: S608
        try:
            with self._get_conn() as conn:
                with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
                    cur.execute(sql, (query, query, top_k))
                    rows = cur.fetchall()

            docs = [self._row_to_document(row) for row in rows]
            logger.info(f"BM25 search: {len(docs)} docs for query '{query[:50]}...'")
            return docs

        except Exception as e:
            logger.error(f"BM25 search failed: {e}")
            return []

    # ─── Reciprocal Rank Fusion ──────────────────────────────────────

    def _reciprocal_rank_fusion(
        self,
        dense_docs: List[Document],
        bm25_docs: List[Document],
        k: int = None,
    ) -> List[Document]:
        """
        Merge dense + BM25 rankings via Reciprocal Rank Fusion (RRF).
        RRF score = Σ weight_i / (k + rank_i)
        Higher is better. Returns deduplicated, merged list sorted by RRF score.
        """
        k = k or settings.RAG_RRF_K
        rrf_scores: dict[str, float] = {}
        doc_map: dict[str, Document] = {}

        # Dense ranking contribution
        for rank, doc in enumerate(dense_docs):
            doc_id = doc.metadata["id"]
            rrf_scores[doc_id] = rrf_scores.get(doc_id, 0.0) + (
                settings.RAG_HYBRID_DENSE_WEIGHT / (k + rank + 1)
            )
            doc_map[doc_id] = doc

        # BM25 ranking contribution
        for rank, doc in enumerate(bm25_docs):
            doc_id = doc.metadata["id"]
            rrf_scores[doc_id] = rrf_scores.get(doc_id, 0.0) + (
                settings.RAG_HYBRID_BM25_WEIGHT / (k + rank + 1)
            )
            if doc_id not in doc_map:
                doc_map[doc_id] = doc

        # Attach RRF score and sort
        merged = list(doc_map.values())
        for doc in merged:
            doc.metadata["rrf_score"] = rrf_scores.get(doc.metadata["id"], 0.0)

        merged.sort(key=lambda d: d.metadata["rrf_score"], reverse=True)
        logger.info(
            f"RRF fusion: {len(dense_docs)} dense + {len(bm25_docs)} BM25"
            f" → {len(merged)} unique docs"
        )
        return merged

    # ─── Similarity Threshold Filtering ─────────────────────────────

    def _filter_by_similarity(
        self, documents: List[Document], threshold: float
    ) -> List[Document]:
        """
        Keep only docs whose dense similarity_score >= threshold.
        Docs coming purely from BM25 (no similarity_score) are kept by default
        since they matched an exact keyword.
        """
        before = len(documents)
        filtered = [
            doc for doc in documents
            if doc.metadata.get("retrieval_type") == "bm25"
            or doc.metadata.get("similarity_score", 0.0) >= threshold
        ]
        logger.info(
            f"Similarity threshold ({threshold}): {before} → {len(filtered)} docs"
        )
        return filtered

    # ─── Cross-Encoder Re-ranking ────────────────────────────────────

    @staticmethod
    def _sigmoid(x: float) -> float:
        try:
            return 1.0 / (1.0 + math.exp(-x))
        except OverflowError:
            return 0.0 if x < 0 else 1.0

    def _rerank(
        self, query: str, documents: List[Document], top_k: int
    ) -> List[Document]:
        """
        Re-rank retrieved documents using the cross-encoder.
        Scores each (query, document) pair, normalizes with sigmoid,
        stores the score in metadata['relevance_score'].
        """
        if not documents or self.cross_encoder is None:
            return documents[:top_k]

        try:
            pairs = [(query, doc.page_content) for doc in documents]
            raw_scores = self.cross_encoder.score(pairs)

            for doc, raw in zip(documents, raw_scores):
                doc.metadata["relevance_score"] = self._sigmoid(float(raw))

            reranked = sorted(
                documents,
                key=lambda d: d.metadata["relevance_score"],
                reverse=True,
            )[:top_k]

            logger.info(
                f"Re-ranked {len(documents)} → top {len(reranked)}: "
                + ", ".join(
                    f"[{d.metadata.get('topic', '?')}: "
                    f"sim={d.metadata.get('similarity_score', 0):.3f}, "
                    f"rrf={d.metadata.get('rrf_score', 0):.4f}, "
                    f"rerank={d.metadata.get('relevance_score', 0):.3f}]"
                    for d in reranked
                )
            )
            return reranked

        except Exception as e:
            logger.error(f"Re-ranking failed, falling back to RRF order: {e}")
            return documents[:top_k]

    # ─── Main RAG Pipeline ──────────────────────────────────────────

    def retrieve_documents(
        self, query: str
    ) -> Tuple[List[Document], List[SourceDocument]]:
        """
        Phase 1: Hybrid retrieval + re-ranking (no LLM call).
        Pipeline:
          1. Dense search (pgvector)  ─┐
          2. BM25 full-text search    ─┤─ RRF fusion
          3. Similarity threshold filter
          4. Cross-encoder re-rank
        """
        if not self._initialized:
            self.initialize()

        top_k = settings.RAG_TOP_K

        # 1. Run dense + BM25 in sequence (psycopg2 is sync)
        dense_docs = self._retrieve_dense(query, top_k)
        bm25_docs = self._retrieve_bm25(query, top_k)

        # 2. Merge via RRF
        merged_docs = self._reciprocal_rank_fusion(dense_docs, bm25_docs)

        # 3. Similarity threshold filter
        filtered_docs = self._filter_by_similarity(
            merged_docs, settings.RAG_SIMILARITY_THRESHOLD
        )

        # 4. Cross-encoder re-rank → final top-K
        final_docs = self._rerank(query, filtered_docs, settings.RAG_RERANK_TOP_K)

        sources = [
            SourceDocument(
                id=doc.metadata.get("id", ""),
                topic=doc.metadata.get("topic", ""),
                category=doc.metadata.get("category", ""),
            )
            for doc in final_docs
        ]

        return final_docs, sources

    def generate_response(
        self,
        query: str,
        financial_context: FinancialContext,
        retrieved_docs: List[Document],
        conversation_id: str,
    ) -> str:
        """
        Phase 2: Generate LLM response using pre-retrieved documents.
        Called after both retrieval and financial context are ready.
        """
        if not self._initialized:
            self.initialize()

        history = conversation_memory.get_history(conversation_id)
        messages = self._build_messages(query, financial_context, retrieved_docs, history)

        try:
            response = self.llm.invoke(messages)
            response_text = response.content.strip()
        except Exception as e:
            logger.error(f"LLM call failed: {e}")
            response_text = self._generate_fallback_response(
                query, financial_context, retrieved_docs
            )

        conversation_memory.add_exchange(conversation_id, query, response_text)
        return response_text

    # ─── Prompt Building ─────────────────────────────────────────────

    def _build_messages(
        self,
        query: str,
        financial_context: FinancialContext,
        retrieved_docs: List[Document],
        history: list,
    ) -> list:
        messages = []
        messages.append(SystemMessage(content=self._build_system_prompt(retrieved_docs)))

        for role, content in history:
            if role == "human":
                messages.append(HumanMessage(content=content))
            elif role == "ai":
                messages.append(AIMessage(content=content))

        messages.append(HumanMessage(content=self._build_user_prompt(query, financial_context)))
        return messages

    def _build_system_prompt(self, retrieved_docs: List[Document]) -> str:
        parts = [
            "Bạn là trợ lý tài chính cá nhân thông minh của ứng dụng MyMoney. ",
            "Hãy đưa ra lời khuyên dựa trên dữ liệu thực tế của người dùng "
            "và kiến thức tài chính bên dưới.\n\n",
        ]

        if retrieved_docs:
            parts.append("[KIẾN THỨC TÀI CHÍNH]\n")
            for doc in retrieved_docs:
                topic = doc.metadata.get("topic", "")
                content = doc.metadata.get("content_vi", "") or doc.metadata.get("content_en", "")
                rerank_score = doc.metadata.get("relevance_score")
                relevance_tag = (
                    f" (relevance: {rerank_score:.2f})" if rerank_score is not None else ""
                )
                parts.append(f"• {topic}: {content}{relevance_tag}\n")
            parts.append("\n")

        parts.extend([
            "Quy tắc:\n",
            "1. Trả lời ngắn gọn (3-5 câu), tập trung vào hành động cụ thể\n",
            "2. Sử dụng số liệu thực từ dữ liệu người dùng khi được hỏi\n",
            "3. Áp dụng kiến thức tài chính để đưa ra lời khuyên phù hợp\n",
            "4. Nếu không có đủ dữ liệu, hãy đưa ra lời khuyên chung\n",
            "5. Nếu có lịch sử hội thoại, hãy tham chiếu bối cảnh trước đó\n",
        ])
        return "".join(parts)

    def _build_user_prompt(
        self, query: str, financial_context: FinancialContext
    ) -> str:
        parts = []
        if financial_context.summary:
            parts.append("[DỮ LIỆU TÀI CHÍNH]\n")
            parts.append(financial_context.summary)
            parts.append("\n")
        if financial_context.budget_context:
            parts.append(financial_context.budget_context)
            parts.append("\n")
        if financial_context.pattern_context:
            parts.append(financial_context.pattern_context)
            parts.append("\n")
        parts.append("[CÂU HỎI]\n")
        parts.append(query)
        return "".join(parts)

    def _generate_fallback_response(
        self,
        query: str,
        financial_context: FinancialContext,
        retrieved_docs: List[Document],
    ) -> str:
        parts = []
        if financial_context.summary:
            parts.append(financial_context.summary)
            parts.append("\n")
        parts.append("\n💡 Lời khuyên:\n")
        q = query.lower()
        if "chi tiêu" in q or "tiêu" in q or "spend" in q:
            parts += [
                "• Theo dõi chi tiêu hàng ngày để kiểm soát tốt hơn\n",
                "• Ưu tiên các khoản chi tiêu cần thiết\n",
                "• Cân nhắc giảm chi tiêu không cần thiết",
            ]
        elif "tiết kiệm" in q or "save" in q:
            parts += [
                "• Đặt mục tiêu tiết kiệm cụ thể và khả thi\n",
                "• Tự động chuyển tiền tiết kiệm mỗi tháng\n",
                "• Áp dụng quy tắc 50/30/20",
            ]
        else:
            parts += [
                "• Theo dõi tài chính đều đặn\n",
                "• Cân bằng giữa chi tiêu và tiết kiệm\n",
                "• Đặt mục tiêu tài chính rõ ràng",
            ]
        return "".join(parts)

    # ─── Helpers ────────────────────────────────────────────────────

    @staticmethod
    def _row_to_document(row: dict) -> Document:
        """Convert a psycopg2 RealDictRow to a LangChain Document."""
        vi = row.get("content_vi") or ""
        page_text = vi if vi else (row.get("text_content") or "")
        keywords = row.get("keywords") or []
        # psycopg2 returns arrays as Python lists already
        return Document(
            page_content=page_text,
            metadata={
                "id": row.get("id", ""),
                "topic": row.get("topic", ""),
                "category": row.get("category", ""),
                "content_en": row.get("content_en", ""),
                "content_vi": vi,
                "keywords": keywords,
                "similarity_score": float(row.get("similarity_score") or 0.0),
                "source_type": row.get("source_type", "json"),
                "page_number": row.get("page_number"),
                "retrieval_type": row.get("retrieval_type", "dense"),
            },
        )

    # ─── Properties ─────────────────────────────────────────────────

    @property
    def is_ready(self) -> bool:
        return self._initialized and self._pool is not None and not self._pool.closed

    @property
    def document_count(self) -> int:
        return self._doc_count

    @property
    def store_type(self) -> str:
        return "postgres+pgvector"


# Global singleton
rag_chain = RAGChain()
