"""
LangChain RAG pipeline for MyMoney financial chatbot.
Supports both Azure Cosmos DB vector search and local FAISS as vector stores.
Handles knowledge base loading, vector store creation, and LLM orchestration.
"""

import json
import os
import logging
from typing import List, Optional

from langchain.schema import Document, HumanMessage, SystemMessage, AIMessage
from langchain_openai import ChatOpenAI
from langchain_huggingface import HuggingFaceEmbeddings

from config import settings
from models import FinancialContext, SourceDocument
from memory import conversation_memory

logger = logging.getLogger(__name__)


class RAGChain:
    """
    LangChain-based RAG pipeline that:
    1. Connects to Azure Cosmos DB (or local FAISS) for vector search
    2. Retrieves relevant documents for user queries
    3. Builds prompts with financial context from the Android app
    4. Calls the LLM via OpenRouter with conversation memory
    """

    def __init__(self):
        self.vector_store = None
        self.embeddings: Optional[HuggingFaceEmbeddings] = None
        self.llm: Optional[ChatOpenAI] = None
        self.documents: List[Document] = []
        self._initialized = False
        self._store_type = settings.VECTOR_STORE_TYPE  # "cosmos" or "faiss"

    def initialize(self):
        """Initialize the RAG pipeline: embeddings, vector store, and LLM."""
        if self._initialized:
            return

        logger.info("Initializing RAG chain...")
        logger.info(f"Vector store type: {self._store_type}")

        # 1. Initialize embeddings model
        logger.info(f"Loading embedding model: {settings.EMBEDDING_MODEL}")
        self.embeddings = HuggingFaceEmbeddings(
            model_name=settings.EMBEDDING_MODEL,
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True}
        )

        # 2. Initialize vector store
        if self._store_type == "cosmos":
            self._init_cosmos_vector_store()
        else:
            self._init_faiss_vector_store()

        # 3. Initialize LLM via OpenRouter
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
            f"vector store ({self._store_type}) ready"
        )

    # ─── Vector Store Initialization ───────────────────────────────

    def _init_cosmos_vector_store(self):
        """Connect to Azure Cosmos DB and set up vector search."""
        from azure.cosmos import CosmosClient

        if not settings.COSMOS_DB_KEY:
            logger.error(
                "COSMOS_DB_KEY not set! Add it to your .env file. "
                "Falling back to FAISS."
            )
            self._store_type = "faiss"
            self._init_faiss_vector_store()
            return

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
            logger.error(
                f"Failed to connect to Cosmos DB: {e}. "
                f"Falling back to FAISS."
            )
            self._store_type = "faiss"
            self._init_faiss_vector_store()

    def _init_faiss_vector_store(self):
        """Load or create a local FAISS vector store (fallback)."""
        from langchain_community.vectorstores import FAISS

        store_path = settings.VECTOR_STORE_PATH

        # Try loading from disk
        if os.path.exists(store_path) and os.path.exists(
            os.path.join(store_path, "index.faiss")
        ):
            try:
                logger.info(f"Loading FAISS vector store from {store_path}")
                self.vector_store = FAISS.load_local(
                    store_path,
                    self.embeddings,
                    allow_dangerous_deserialization=True
                )
                self.documents = self._load_knowledge_base()
                logger.info("FAISS vector store loaded from disk")
                return
            except Exception as e:
                logger.warning(f"Failed to load FAISS: {e}, recreating...")

        # Create new FAISS vector store from knowledge base JSON
        self.documents = self._load_knowledge_base()
        if not self.documents:
            logger.error("No documents loaded from knowledge base!")
            return

        logger.info(f"Creating FAISS store from {len(self.documents)} documents...")
        self.vector_store = FAISS.from_documents(self.documents, self.embeddings)

        # Persist to disk
        os.makedirs(store_path, exist_ok=True)
        self.vector_store.save_local(store_path)
        logger.info(f"FAISS vector store saved to {store_path}")

    # ─── Knowledge Base Loading (FAISS only) ───────────────────────

    def _load_knowledge_base(self) -> List[Document]:
        """Load the financial knowledge base JSON into LangChain Documents."""
        kb_path = settings.KNOWLEDGE_BASE_PATH

        if not os.path.exists(kb_path):
            logger.error(f"Knowledge base not found: {kb_path}")
            return []

        with open(kb_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        documents = []
        skip_keys = {"version", "lastUpdated", "description"}

        for category, items in data.items():
            if category in skip_keys:
                continue
            if not isinstance(items, list):
                continue

            for item in items:
                doc_id = item.get("id", "")
                topic = item.get("topic", "")
                content_en = item.get("content_en", "")
                content_vi = item.get("content_vi", "")
                keywords = item.get("keywords", [])

                page_content = (
                    f"Topic: {topic}\n"
                    f"Category: {category}\n"
                    f"English: {content_en}\n"
                    f"Vietnamese: {content_vi}\n"
                    f"Keywords: {', '.join(keywords)}"
                )

                doc = Document(
                    page_content=page_content,
                    metadata={
                        "id": doc_id,
                        "topic": topic,
                        "category": category,
                        "content_en": content_en,
                        "content_vi": content_vi,
                        "keywords": keywords
                    }
                )
                documents.append(doc)

        logger.info(f"Loaded {len(documents)} knowledge documents from {kb_path}")
        return documents

    # ─── Retrieval (Cosmos DB or FAISS) ────────────────────────────

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

    def _retrieve_from_faiss(self, query: str, top_k: int) -> List[Document]:
        """Retrieve relevant documents from local FAISS vector store."""
        if not self.vector_store:
            return []
        return self.vector_store.similarity_search(query, k=top_k)

    # ─── Main RAG Pipeline ─────────────────────────────────────────

    def retrieve_and_respond(
        self,
        query: str,
        financial_context: FinancialContext,
        conversation_id: str
    ) -> tuple[str, List[SourceDocument]]:
        """
        Main RAG pipeline:
        1. Retrieve relevant knowledge from vector store
        2. Get conversation history
        3. Build system + user prompts
        4. Call LLM
        5. Store in conversation memory
        6. Return response + sources
        """
        if not self._initialized:
            self.initialize()

        # 1. Retrieve relevant documents (Cosmos DB or FAISS)
        if self._store_type == "cosmos":
            retrieved_docs = self._retrieve_from_cosmos(query, settings.RAG_TOP_K)
        else:
            retrieved_docs = self._retrieve_from_faiss(query, settings.RAG_TOP_K)

        sources = [
            SourceDocument(
                id=doc.metadata.get("id", ""),
                topic=doc.metadata.get("topic", ""),
                category=doc.metadata.get("category", "")
            )
            for doc in retrieved_docs
        ]

        # 2. Get conversation history
        history = conversation_memory.get_history(conversation_id)

        # 3. Build messages
        messages = self._build_messages(
            query, financial_context, retrieved_docs, history
        )

        # 4. Call LLM
        try:
            response = self.llm.invoke(messages)
            response_text = response.content.strip()
        except Exception as e:
            logger.error(f"LLM call failed: {e}")
            response_text = self._generate_fallback_response(
                query, financial_context, retrieved_docs
            )

        # 5. Store in conversation memory
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

        # Add retrieved knowledge
        if retrieved_docs:
            parts.append("[KIẾN THỨC TÀI CHÍNH]\n")
            for doc in retrieved_docs:
                topic = doc.metadata.get("topic", "")
                # Prefer Vietnamese content
                content = doc.metadata.get("content_vi", "")
                if not content:
                    content = doc.metadata.get("content_en", "")
                parts.append(f"• {topic}: {content}\n")
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
        if self._store_type == "cosmos":
            return self._initialized and hasattr(self, "_cosmos_container")
        return self._initialized and self.vector_store is not None

    @property
    def document_count(self) -> int:
        return len(self.documents)

    @property
    def store_type(self) -> str:
        return self._store_type


# Global singleton
rag_chain = RAGChain()
