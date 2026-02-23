"""
LangChain RAG pipeline for MyMoney financial chatbot.
Handles knowledge base loading, vector store creation, and LLM orchestration.
"""

import json
import os
import logging
from typing import List, Optional

from langchain.schema import Document, HumanMessage, SystemMessage, AIMessage
from langchain_openai import ChatOpenAI
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_community.vectorstores import FAISS

from config import settings
from models import FinancialContext, SourceDocument
from memory import conversation_memory

logger = logging.getLogger(__name__)


class RAGChain:
    """
    LangChain-based RAG pipeline that:
    1. Loads the bilingual financial knowledge base into a FAISS vector store
    2. Retrieves relevant documents for user queries
    3. Builds prompts with financial context from the Android app
    4. Calls the LLM via OpenRouter with conversation memory
    """

    def __init__(self):
        self.vector_store: Optional[FAISS] = None
        self.embeddings: Optional[HuggingFaceEmbeddings] = None
        self.llm: Optional[ChatOpenAI] = None
        self.documents: List[Document] = []
        self._initialized = False

    def initialize(self):
        """Initialize the RAG pipeline: embeddings, vector store, and LLM."""
        if self._initialized:
            return

        logger.info("Initializing RAG chain...")

        # 1. Initialize embeddings model
        logger.info(f"Loading embedding model: {settings.EMBEDDING_MODEL}")
        self.embeddings = HuggingFaceEmbeddings(
            model_name=settings.EMBEDDING_MODEL,
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True}
        )

        # 2. Load knowledge base and create/load vector store
        self._load_or_create_vector_store()

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
            f"RAG chain initialized: {len(self.documents)} documents, "
            f"vector store ready"
        )

    def _load_or_create_vector_store(self):
        """Load existing vector store from disk or create a new one."""
        store_path = settings.VECTOR_STORE_PATH

        # Check if we can load from disk
        if os.path.exists(store_path) and os.path.exists(
            os.path.join(store_path, "index.faiss")
        ):
            try:
                logger.info(f"Loading vector store from {store_path}")
                self.vector_store = FAISS.load_local(
                    store_path,
                    self.embeddings,
                    allow_dangerous_deserialization=True
                )
                # Also load documents for reference
                self.documents = self._load_knowledge_base()
                logger.info("Vector store loaded from disk")
                return
            except Exception as e:
                logger.warning(f"Failed to load vector store: {e}, recreating...")

        # Create new vector store
        self.documents = self._load_knowledge_base()
        if not self.documents:
            logger.error("No documents loaded from knowledge base!")
            return

        logger.info(f"Creating vector store from {len(self.documents)} documents...")
        langchain_docs = [
            Document(
                page_content=doc.page_content,
                metadata=doc.metadata
            )
            for doc in self.documents
        ]
        self.vector_store = FAISS.from_documents(langchain_docs, self.embeddings)

        # Persist to disk
        os.makedirs(store_path, exist_ok=True)
        self.vector_store.save_local(store_path)
        logger.info(f"Vector store saved to {store_path}")

    def _load_knowledge_base(self) -> List[Document]:
        """Load the financial knowledge base JSON into LangChain Documents."""
        kb_path = settings.KNOWLEDGE_BASE_PATH

        if not os.path.exists(kb_path):
            logger.error(f"Knowledge base not found: {kb_path}")
            return []

        with open(kb_path, "r", encoding="utf-8") as f:
            data = json.load(f)

        documents = []

        # Iterate over all categories in the JSON
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

                # Combine content for rich embedding
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

        # 1. Retrieve relevant documents
        retrieved_docs = []
        sources = []
        if self.vector_store:
            retrieved_docs = self.vector_store.similarity_search(
                query, k=settings.RAG_TOP_K
            )
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

    def _build_messages(
        self,
        query: str,
        financial_context: FinancialContext,
        retrieved_docs: List[Document],
        history: list
    ) -> list:
        """Build the message list for the LLM call."""
        messages = []

        # System prompt (matches RAGService.buildSystemPrompt style)
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

        # Financial data
        if financial_context.summary:
            parts.append("[DỮ LIỆU TÀI CHÍNH]\n")
            parts.append(financial_context.summary)
            parts.append("\n")

        # Budget context
        if financial_context.budget_context:
            parts.append(financial_context.budget_context)
            parts.append("\n")

        # Spending patterns
        if financial_context.pattern_context:
            parts.append(financial_context.pattern_context)
            parts.append("\n")

        # The actual question
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

    @property
    def is_ready(self) -> bool:
        return self._initialized and self.vector_store is not None

    @property
    def document_count(self) -> int:
        return len(self.documents)


# Global singleton
rag_chain = RAGChain()
