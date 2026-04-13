"""
LangChain RAG pipeline for MyMoney financial chatbot.
Uses Azure Cosmos DB vector search for retrieval, cross-encoder re-ranking,
and OpenRouter for response generation.
"""

import logging
from typing import List, Optional

from langchain.schema import Document, HumanMessage, SystemMessage, AIMessage
from langchain.retrievers.document_compressors import CrossEncoderReranker
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
    1. Connects to Azure Cosmos DB for vector search
    2. Retrieves relevant documents with similarity threshold filtering
    3. Re-ranks results using a cross-encoder model
    4. Builds prompts with financial context from the Android app
    5. Calls the LLM via OpenRouter with conversation memory
    """

    def __init__(self):
        self.embeddings: Optional[HuggingFaceEmbeddings] = None
        self.llm: Optional[ChatOpenAI] = None
        self.reranker: Optional[CrossEncoderReranker] = None
        self.documents: List[Document] = []
        self._initialized = False
        self._cosmos_container = None

    def initialize(self):
        """Initialize the RAG pipeline: embeddings, reranker, vector store, and LLM."""
        if self._initialized:
            return

        logger.info("Initializing RAG chain...")
        logger.info("Vector store type: cosmos")

        # 1. Initialize multilingual embeddings model
        logger.info(f"Loading embedding model: {settings.EMBEDDING_MODEL}")
        self.embeddings = HuggingFaceEmbeddings(
            model_name=settings.EMBEDDING_MODEL,
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True}
        )

        # 2. Initialize cross-encoder re-ranker (LangChain)
        logger.info(f"Loading re-ranker model: {settings.RAG_RERANKER_MODEL}")
        cross_encoder = HuggingFaceCrossEncoder(model_name=settings.RAG_RERANKER_MODEL)
        self.reranker = CrossEncoderReranker(
            model=cross_encoder,
            top_n=settings.RAG_RERANK_TOP_K,
        )
        logger.info("Re-ranker model loaded.")

        # 3. Initialize Cosmos DB vector store
        self._init_cosmos_vector_store()

        # 4. Initialize LLM via OpenRouter
        self.llm = ChatOpenAI(
            model=settings.OPENROUTER_MODEL,
            openai_api_key=settings.OPENROUTER_API_TOKEN,
            openai_api_base=settings.OPENROUTER_BASE_URL,
            temperature=0.7,
            max_tokens=500,
            default_headers={
                "HTTP-Referer": "https://github.com/notnbhd/mymoney",
                "X-Title": "MyMoney App"
            }
        )

        self._initialized = True
        logger.info(
            f"RAG chain initialized: {self.document_count} documents, "
            "vector store (cosmos) ready, re-ranker ready"
        )

    # ─── Vector Store Initialization ───────────────────────────────

    def _init_cosmos_vector_store(self):
        """Connect to Azure Cosmos DB and set up vector search."""
        from azure.cosmos import CosmosClient

        if not settings.COSMOS_DB_KEY:
            raise RuntimeError("COSMOS_DB_KEY not set! Add it to your .env file.")

        try:
            logger.info(f"Connecting to Cosmos DB: {settings.COSMOS_DB_ENDPOINT}")
            client = CosmosClient(
                settings.COSMOS_DB_ENDPOINT,
                credential=settings.COSMOS_DB_KEY
            )
            database = client.get_database_client(settings.COSMOS_DB_DATABASE)
            self._cosmos_container = database.get_container_client(
                settings.COSMOS_DB_CONTAINER
            )

            # Count documents to verify connection
            count_query = "SELECT VALUE COUNT(1) FROM c"
            results = list(self._cosmos_container.query_items(
                query=count_query,
                enable_cross_partition_query=True
            ))
            doc_count = results[0] if results else 0

            logger.info(f"Connected to Cosmos DB: {doc_count} documents in container")
            self.documents = [None] * doc_count  # Track count for health check

            if doc_count == 0:
                logger.warning(
                    "Cosmos DB container is empty! "
                    "Run 'python setup_cosmos.py' to upload the knowledge base."
                )

        except Exception as e:
            raise RuntimeError(f"Failed to connect to Cosmos DB: {e}") from e

    # ─── Retrieval (Cosmos DB) ──────────────────────────────────────

    def _retrieve_from_cosmos(self, query: str, top_k: int) -> List[Document]:
        """
        Retrieve relevant documents from Azure Cosmos DB using vector search.
        Computes the query embedding, then uses Cosmos DB's VectorDistance function.
        """
        # Compute query embedding
        query_embedding = self.embeddings.embed_query(query)

        # Cosmos DB vector search query using VectorDistance
        vector_search_query = (
            "SELECT TOP @top_k "
            "c.id, c.topic, c.category, c.content_en, c.content_vi, "
            "c.keywords, c.text_content, "
            "VectorDistance(c.embedding, @embedding) AS similarity_score "
            "FROM c "
            "ORDER BY VectorDistance(c.embedding, @embedding)"
        )

        parameters = [
            {"name": "@top_k", "value": top_k},
            {"name": "@embedding", "value": query_embedding},
        ]

        try:
            results = list(self._cosmos_container.query_items(
                query=vector_search_query,
                parameters=parameters,
                enable_cross_partition_query=True,
            ))

            documents = []
            for item in results:
                doc = Document(
                    page_content=item.get("text_content", ""),
                    metadata={
                        "id": item.get("id", ""),
                        "topic": item.get("topic", ""),
                        "category": item.get("category", ""),
                        "content_en": item.get("content_en", ""),
                        "content_vi": item.get("content_vi", ""),
                        "keywords": item.get("keywords", []),
                        "similarity_score": item.get("similarity_score", 0),
                        "source_type": item.get("source_type", "json"),
                        "page_number": item.get("page_number"),
                    }
                )
                documents.append(doc)

            logger.info(
                f"Cosmos DB returned {len(documents)} documents for query: "
                f"'{query[:50]}...'"
            )
            return documents

        except Exception as e:
            logger.error(f"Cosmos DB vector search failed: {e}")
            return []

    # ─── Similarity Threshold Filtering ─────────────────────────────

    def _filter_by_similarity(
        self, documents: List[Document], threshold: float
    ) -> List[Document]:
        """
        Filter out documents whose similarity score is below the threshold.
        Cosmos DB VectorDistance with cosine returns values in [0, 1] where
        higher = more similar (when distanceFunction is cosine).
        """
        before_count = len(documents)
        filtered = [
            doc for doc in documents
            if doc.metadata.get("similarity_score", 0) >= threshold
        ]
        logger.info(
            f"Similarity threshold filter ({threshold}): "
            f"{before_count} → {len(filtered)} documents"
        )
        return filtered

    # ─── Cross-Encoder Re-ranking ───────────────────────────────────

    def _rerank(
        self, query: str, documents: List[Document], top_k: int
    ) -> List[Document]:
        """
        Re-rank retrieved documents using LangChain's CrossEncoderReranker.
        Uses compress_documents() which scores each (query, document) pair
        and returns the top_n most relevant documents.
        """
        if not documents or self.reranker is None:
            return documents[:top_k]

        try:
            reranked = self.reranker.compress_documents(documents, query)
            reranked = list(reranked)[:top_k]

            logger.info(
                f"Re-ranked {len(documents)} docs → top {len(reranked)}: "
                + ", ".join(
                    f"[{d.metadata.get('topic', '?')}: "
                    f"sim={d.metadata.get('similarity_score', 0):.3f}, "
                    f"rerank={d.metadata.get('relevance_score', 0):.3f}]"
                    for d in reranked
                )
            )
            return reranked

        except Exception as e:
            logger.error(f"Re-ranking failed, falling back to similarity order: {e}")
            return documents[:top_k]

    # ─── Main RAG Pipeline ─────────────────────────────────────────

    def retrieve_and_respond(
        self,
        query: str,
        financial_context: FinancialContext,
        conversation_id: str
    ) -> tuple[str, List[SourceDocument]]:
        """
        Main RAG pipeline:
        1. Retrieve a broad pool of documents from Cosmos DB vector search
        2. Filter by similarity threshold
        3. Re-rank with cross-encoder
        4. Get conversation history
        5. Build system + user prompts
        6. Call LLM
        7. Store in conversation memory
        8. Return response + sources
        """
        if not self._initialized:
            self.initialize()

        # 1. Retrieve initial pool from Cosmos DB
        retrieved_docs = self._retrieve_from_cosmos(query, settings.RAG_TOP_K)

        # 2. Filter by similarity threshold
        filtered_docs = self._filter_by_similarity(
            retrieved_docs, settings.RAG_SIMILARITY_THRESHOLD
        )

        # 3. Re-rank and select final top-K
        final_docs = self._rerank(query, filtered_docs, settings.RAG_RERANK_TOP_K)

        sources = [
            SourceDocument(
                id=doc.metadata.get("id", ""),
                topic=doc.metadata.get("topic", ""),
                category=doc.metadata.get("category", "")
            )
            for doc in final_docs
        ]

        # 4. Get conversation history
        history = conversation_memory.get_history(conversation_id)

        # 5. Build messages
        messages = self._build_messages(
            query, financial_context, final_docs, history
        )

        # 6. Call LLM
        try:
            response = self.llm.invoke(messages)
            response_text = response.content.strip()
        except Exception as e:
            logger.error(f"LLM call failed: {e}")
            response_text = self._generate_fallback_response(
                query, financial_context, final_docs
            )

        # 7. Store in conversation memory
        conversation_memory.add_exchange(conversation_id, query, response_text)

        return response_text, sources

    # ─── Prompt Building ───────────────────────────────────────────

    def _build_messages(
        self,
        query: str,
        financial_context: FinancialContext,
        retrieved_docs: List[Document],
        history: list
    ) -> list:
        """Build the message list for the LLM call."""
        messages = []

        # System prompt (persona + retrieved knowledge)
        system_content = self._build_system_prompt(retrieved_docs)
        messages.append(SystemMessage(content=system_content))

        # Add conversation history
        for role, content in history:
            if role == "human":
                messages.append(HumanMessage(content=content))
            elif role == "ai":
                messages.append(AIMessage(content=content))

        # User prompt with financial context
        user_content = self._build_user_prompt(query, financial_context)
        messages.append(HumanMessage(content=user_content))

        return messages

    def _build_system_prompt(self, retrieved_docs: List[Document]) -> str:
        """
        Build system prompt matching the existing RAGService.buildSystemPrompt.
        Persona: Vietnamese financial advisor for the MyMoney app.
        """
        parts = [
            "Bạn là trợ lý tài chính cá nhân thông minh của ứng dụng MyMoney. ",
            "Hãy đưa ra lời khuyên dựa trên dữ liệu thực tế của người dùng "
            "và kiến thức tài chính bên dưới.\n\n"
        ]

        # Add retrieved knowledge (already filtered & re-ranked)
        if retrieved_docs:
            parts.append("[KIẾN THỨC TÀI CHÍNH]\n")
            for doc in retrieved_docs:
                topic = doc.metadata.get("topic", "")
                # Prefer Vietnamese content
                content = doc.metadata.get("content_vi", "")
                if not content:
                    content = doc.metadata.get("content_en", "")
                rerank_score = doc.metadata.get("relevance_score")
                relevance_tag = ""
                if rerank_score is not None:
                    relevance_tag = f" (relevance: {rerank_score:.2f})"
                parts.append(f"• {topic}: {content}{relevance_tag}\n")
            parts.append("\n")

        # Rules
        parts.append("Quy tắc:\n")
        parts.append("1. Trả lời ngắn gọn (3-5 câu), tập trung vào hành động cụ thể\n")
        parts.append("2. Sử dụng số liệu thực từ dữ liệu người dùng khi được hỏi\n")
        parts.append("3. Áp dụng kiến thức tài chính để đưa ra lời khuyên phù hợp\n")
        parts.append("4. Nếu không có đủ dữ liệu, hãy đưa ra lời khuyên chung\n")
        parts.append("5. Nếu có lịch sử hội thoại, hãy tham chiếu bối cảnh trước đó\n")

        return "".join(parts)

    def _build_user_prompt(
        self,
        query: str,
        financial_context: FinancialContext
    ) -> str:
        """
        Build user prompt matching the existing RAGService.buildUserPrompt.
        Includes financial data from the Android app.
        """
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
        retrieved_docs: List[Document]
    ) -> str:
        """Generate a fallback response when the LLM call fails."""
        parts = []

        if financial_context.summary:
            parts.append(financial_context.summary)
            parts.append("\n")

        parts.append("\n💡 Lời khuyên:\n")

        query_lower = query.lower()
        if "chi tiêu" in query_lower or "tiêu" in query_lower or "spend" in query_lower:
            parts.append("• Theo dõi chi tiêu hàng ngày để kiểm soát tốt hơn\n")
            parts.append("• Ưu tiên các khoản chi tiêu cần thiết\n")
            parts.append("• Cân nhắc giảm chi tiêu không cần thiết")
        elif "tiết kiệm" in query_lower or "save" in query_lower:
            parts.append("• Đặt mục tiêu tiết kiệm cụ thể và khả thi\n")
            parts.append("• Tự động chuyển tiền tiết kiệm mỗi tháng\n")
            parts.append("• Áp dụng quy tắc 50/30/20")
        else:
            parts.append("• Theo dõi tài chính đều đặn\n")
            parts.append("• Cân bằng giữa chi tiêu và tiết kiệm\n")
            parts.append("• Đặt mục tiêu tài chính rõ ràng")

        return "".join(parts)

    # ─── Properties ────────────────────────────────────────────────

    @property
    def is_ready(self) -> bool:
        return self._initialized and self._cosmos_container is not None

    @property
    def document_count(self) -> int:
        return len(self.documents)

    @property
    def store_type(self) -> str:
        return "cosmos"


# Global singleton
rag_chain = RAGChain()
