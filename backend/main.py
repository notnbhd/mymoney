"""
MyMoney LangChain Backend
FastAPI server providing RAG-enhanced financial chatbot capabilities.

Usage:
    uvicorn main:app --host 0.0.0.0 --port 8000
    # or
    python main.py
"""

import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from models import (
    ChatResponse, HealthResponse, SourceDocument,
    ParseRequest, ParseResponse, TimeRange,
    RetrieveRequest, RetrieveResponse, GenerateRequest,
)
from rag_chain import rag_chain
from memory import conversation_memory

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Startup/shutdown lifecycle.
    Initialize the RAG chain on startup.
    """
    logger.info("Starting MyMoney LangChain Backend...")
    logger.info(f"OpenRouter model: {settings.OPENROUTER_MODEL}")
    logger.info(f"Knowledge base: {settings.KNOWLEDGE_BASE_PATH}")

    # Initialize RAG chain (loads embeddings + vector store + LLM)
    try:
        rag_chain.initialize()
        logger.info(
            f"RAG chain ready: {rag_chain.document_count} documents loaded"
        )
    except Exception as e:
        logger.error(f"Failed to initialize RAG chain: {e}")
        logger.warning("Server will start but /chat will return errors")

    yield

    # Cleanup
    logger.info("Shutting down MyMoney LangChain Backend...")
    conversation_memory.clear_all()


# Create FastAPI app
app = FastAPI(
    title="MyMoney LangChain Backend",
    description="RAG-enhanced financial chatbot API for the MyMoney Android app",
    version="1.0.0",
    lifespan=lifespan
)

# CORS - allow Android app and local development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Allow all origins for mobile app
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Health check endpoint. Used by the Android app to detect backend availability."""
    return HealthResponse(
        status="ok",
        knowledge_base_loaded=rag_chain.is_ready,
        document_count=rag_chain.document_count
    )



@app.get("/stats")
async def stats():
    """Get server statistics."""
    memory_stats = conversation_memory.get_stats()
    cache_stats = _retrieval_cache.stats()
    return {
        "rag": {
            "initialized": rag_chain.is_ready,
            "document_count": rag_chain.document_count,
            "vector_store_type": rag_chain.store_type,
        },
        "memory": memory_stats,
        "retrieval_cache": cache_stats,
    }


@app.delete("/memory/{conversation_id}")
async def clear_memory(conversation_id: str):
    """Clear conversation memory for a specific conversation."""
    conversation_memory.clear(conversation_id)
    return {"status": "cleared", "conversation_id": conversation_id}


import json
import uuid

from langchain.schema import Document


class RetrievalCache:
    """
    Redis-backed cache for retrieved documents.
    Shared across all Uvicorn workers — safe with WORKERS > 1.
    Each entry expires automatically after RETRIEVAL_TTL seconds.
    """

    RETRIEVAL_TTL = 300     # 5 minutes
    KEY_PREFIX = "mymoney:retrieval:"

    def __init__(self):
        import redis as _redis
        self._redis = _redis.from_url(settings.REDIS_URL, decode_responses=True)

    def _key(self, retrieval_id: str) -> str:
        return f"{self.KEY_PREFIX}{retrieval_id}"

    # ── Serialize / Deserialize Documents ───────────────────────────

    @staticmethod
    def _serialize_doc(doc: Document) -> dict:
        return {"page_content": doc.page_content, "metadata": doc.metadata}

    @staticmethod
    def _deserialize_doc(data: dict) -> Document:
        return Document(
            page_content=data["page_content"],
            metadata=data["metadata"],
        )

    @staticmethod
    def _serialize_source(src: SourceDocument) -> dict:
        return {"id": src.id, "topic": src.topic, "category": src.category}

    @staticmethod
    def _deserialize_source(data: dict) -> SourceDocument:
        return SourceDocument(**data)

    # ── Public API ───────────────────────────────────────────────────

    def store(
        self,
        retrieval_id: str,
        docs: list[Document],
        sources: list[SourceDocument],
        query: str,
    ) -> None:
        """Serialize and store retrieval result in Redis with TTL."""
        payload = json.dumps({
            "docs":    [self._serialize_doc(d) for d in docs],
            "sources": [self._serialize_source(s) for s in sources],
            "query":   query,
        })
        self._redis.setex(self._key(retrieval_id), self.RETRIEVAL_TTL, payload)

    def pop(
        self, retrieval_id: str
    ) -> dict | None:
        """
        Atomically fetch-and-delete the cached entry.
        Returns dict with 'docs', 'sources', 'query' or None if missing/expired.
        """
        key = self._key(retrieval_id)
        # Pipeline: GET + DEL in one round-trip
        pipe = self._redis.pipeline()
        pipe.get(key)
        pipe.delete(key)
        raw, _ = pipe.execute()

        if raw is None:
            return None

        data = json.loads(raw)
        return {
            "docs":    [self._deserialize_doc(d) for d in data["docs"]],
            "sources": [self._deserialize_source(s) for s in data["sources"]],
            "query":   data["query"],
        }

    def stats(self) -> dict:
        keys = self._redis.keys(f"{self.KEY_PREFIX}*")
        return {"pending_retrievals": len(keys)}


# Module-level singleton (one per worker process, but all hit the same Redis)
_retrieval_cache = RetrievalCache()

@app.post("/retrieve", response_model=RetrieveResponse)
async def retrieve(request: RetrieveRequest):
    """
    Phase 1: Retrieve and re-rank documents (no LLM call).
    Returns a retrieval_id that the client uses when calling /generate.
    Can be called in parallel with /parse + on-device financial data gathering.
    The result is stored in Redis — safe with multiple Uvicorn workers.
    """
    if not rag_chain.is_ready:
        raise HTTPException(
            status_code=503,
            detail="RAG service is not initialized."
        )

    logger.info(f"Retrieve request: '{request.message[:80]}'")

    try:
        final_docs, sources = await asyncio.to_thread(
            rag_chain.retrieve_documents, request.message
        )

        # Cache in Redis (shared across all workers)
        retrieval_id = str(uuid.uuid4())
        _retrieval_cache.store(retrieval_id, final_docs, sources, request.message)

        logger.info(
            f"Retrieved {len(sources)} docs, cached in Redis as {retrieval_id[:8]}..."
        )

        return RetrieveResponse(
            retrieval_id=retrieval_id,
            sources=sources,
        )

    except Exception as e:
        logger.error(f"Retrieve error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/generate", response_model=ChatResponse)
async def generate(request: GenerateRequest):
    """
    Phase 2: Generate LLM response using pre-retrieved documents.
    Called after both /retrieve and financial context gathering are complete.
    """
    if not rag_chain.is_ready:
        raise HTTPException(
            status_code=503,
            detail="RAG service is not initialized."
        )

    # Atomically fetch-and-delete from Redis (works across all workers)
    cached = _retrieval_cache.pop(request.retrieval_id)
    if cached is None:
        raise HTTPException(
            status_code=404,
            detail=f"Retrieval ID '{request.retrieval_id}' not found or expired. "
                   "Call /retrieve first."
        )

    conversation_id = request.conversation_id
    if not conversation_id:
        conversation_id = f"user_{request.user_id}_wallet_{request.wallet_id}"

    logger.info(
        f"Generate request: retrieval={request.retrieval_id[:8]}..., "
        f"conv={conversation_id}"
    )

    try:
        response_text = await asyncio.to_thread(
            rag_chain.generate_response,
            request.message,
            request.financial_context,
            cached["docs"],
            conversation_id,
        )

        logger.info(f"Response generated: {len(response_text)} chars")

        return ChatResponse(
            response=response_text,
            sources=cached["sources"],
            conversation_id=conversation_id,
        )

    except Exception as e:
        logger.error(f"Generate error: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(e))


# ─── Parse Endpoint ────────────────────────────────────────────

CATEGORY_LIST = (
    "Food, Home, Transport, Relationship, Entertainment, Medical, Tax, "
    "Gym & Fitness, Beauty, Clothing, Education, Childcare, Groceries, Others, "
    "Salary, Bonus, Investment, Gift, Freelance, Refund, Rental, Interest"
)


def _build_parsing_prompt(current_month: int, current_year: int) -> str:
    """Build the system prompt for LLM-based query parsing."""
    prev_month = 12 if current_month == 1 else current_month - 1
    prev_month_year = current_year - 1 if current_month == 1 else current_year

    return (
        "You are a financial query parser. Parse the user's question and extract structured information.\n"
        f"Current date context: Month {current_month}, Year {current_year}\n\n"
        "Output ONLY valid JSON (no markdown, no explanation) in this exact format:\n"
        "{\n"
        '  "timeRange": {"type": "current_month|specific_month|year|last_n_days|all_time", "month": 1-12, "year": 2020-2030, "days": number},\n'
        '  "category": "category_name or null",\n'
        '  "queryType": "spending|income|comparison|trend|category_list|general"\n'
        "}\n\n"
        "IMPORTANT RULES:\n"
        "- NEVER ask for clarification. Always provide a complete response.\n"
        "- If no time period specified, DEFAULT to current_month.\n"
        "- If no category specified, set category to null (will show all categories).\n\n"
        f"Valid categories: {CATEGORY_LIST}\n\n"
        "Time parsing rules:\n"
        "- 'tháng 12' or 'December' = specific_month with month=12\n"
        f"- 'tháng trước' or 'last month' = specific_month with month={prev_month}\n"
        f"- 'năm nay' or 'this year' = year with year={current_year}\n"
        f"- 'năm ngoái' or 'last year' = year with year={current_year - 1}\n"
        "- 'tuần qua' or 'last week' = last_n_days with days=7\n"
        "- If no time specified = current_month (DO NOT ask for clarification)\n\n"
        "Category matching:\n"
        "- 'clothes/quần áo' = Clothing\n"
        "- 'food/ăn uống/đồ ăn' = Food\n"
        "- 'transport/đi lại/xe' = Transport\n"
        "- 'gym/tập thể dục' = Gym & Fitness\n"
        "- If no category mentioned = null (show all categories, DO NOT ask for clarification)\n\n"
        "Examples:\n"
        f'- \'How much did I spend on clothes in December?\' → {{"timeRange":{{"type":"specific_month","month":12,"year":{current_year}}},"category":"Clothing","queryType":"spending"}}\n'
        f'- \'Tháng trước tôi chi bao nhiêu tiền ăn?\' → {{"timeRange":{{"type":"specific_month","month":{prev_month},"year":{prev_month_year}}},"category":"Food","queryType":"spending"}}\n'
        '- \'Show my spending\' → {"timeRange":{"type":"current_month"},"category":null,"queryType":"spending"}\n'
        '- \'Tôi chi tiêu bao nhiêu?\' → {"timeRange":{"type":"current_month"},"category":null,"queryType":"spending"}'
    )


def _parse_llm_response(raw: str) -> ParseResponse:
    """Parse the raw LLM JSON output into a ParseResponse."""
    import json as _json

    # Strip markdown fences if present
    text = raw.strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[-1]  # remove first line
        text = text.rsplit("```", 1)[0]  # remove closing fence
        text = text.strip()

    data = _json.loads(text)

    # Build TimeRange
    tr_data = data.get("timeRange", {})
    time_range = TimeRange(
        type=tr_data.get("type", "current_month"),
        month=tr_data.get("month"),
        year=tr_data.get("year"),
        days=tr_data.get("days"),
    )

    return ParseResponse(
        time_range=time_range,
        category=data.get("category"),
        query_type=data.get("queryType", "general"),
    )


@app.post("/parse", response_model=ParseResponse)
async def parse_query(request: ParseRequest):
    """
    Parse a natural language query into structured intent.
    Replaces the on-device QueryParser that previously called OpenRouter directly.
    """
    logger.info(f"Parse request: '{request.message[:80]}'")

    system_prompt = _build_parsing_prompt(request.current_month, request.current_year)

    try:
        from langchain.schema import HumanMessage, SystemMessage

        messages = [
            SystemMessage(content=system_prompt),
            HumanMessage(content=request.message),
        ]

        llm_response = await asyncio.to_thread(
            rag_chain.llm.invoke,
            messages,
            temperature=0.1,
            max_tokens=200,
        )
        raw_text = llm_response.content.strip()
        logger.info(f"Parse LLM response: {raw_text}")

        result = _parse_llm_response(raw_text)
        return result

    except Exception as e:
        logger.error(f"Parse failed, returning default intent: {e}")
        return ParseResponse(
            time_range=TimeRange(type="current_month"),
            category=None,
            query_type="general",
        )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",          # string form required when workers > 1
        host=settings.HOST,
        port=settings.PORT,
        workers=settings.WORKERS,
        reload=False,
    )
