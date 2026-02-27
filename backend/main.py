"""
MyMoney LangChain Backend
FastAPI server providing RAG-enhanced financial chatbot capabilities.

Usage:
    uvicorn main:app --host 0.0.0.0 --port 8000
    # or
    python main.py
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from config import settings
from models import ChatRequest, ChatResponse, HealthResponse, SourceDocument
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


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest):
    """
    Main chat endpoint.
    Receives user query + financial context from the Android app,
    runs through the LangChain RAG pipeline, and returns a response.
    """
    if not rag_chain.is_ready:
        raise HTTPException(
            status_code=503,
            detail="RAG service is not initialized. Please try again later."
        )

    # Generate conversation_id if not provided
    conversation_id = request.conversation_id
    if not conversation_id:
        conversation_id = f"user_{request.user_id}_wallet_{request.wallet_id}"

    logger.info(
        f"Chat request: user={request.user_id}, wallet={request.wallet_id}, "
        f"conv={conversation_id}, message='{request.message[:50]}...'"
    )

    try:
        # Run the RAG pipeline
        response_text, sources = rag_chain.retrieve_and_respond(
            query=request.message,
            financial_context=request.financial_context,
            conversation_id=conversation_id
        )

        logger.info(
            f"Response generated: {len(response_text)} chars, "
            f"{len(sources)} sources"
        )

        return ChatResponse(
            response=response_text,
            sources=sources,
            conversation_id=conversation_id
        )

    except Exception as e:
        logger.error(f"Chat error: {e}", exc_info=True)
        raise HTTPException(
            status_code=500,
            detail=f"Failed to generate response: {str(e)}"
        )


@app.get("/stats")
async def stats():
    """Get server statistics."""
    memory_stats = conversation_memory.get_stats()
    return {
        "rag": {
            "initialized": rag_chain.is_ready,
            "document_count": rag_chain.document_count,
            "vector_store_type": rag_chain.store_type,
        },
        "memory": memory_stats
    }


@app.delete("/memory/{conversation_id}")
async def clear_memory(conversation_id: str):
    """Clear conversation memory for a specific conversation."""
    conversation_memory.clear(conversation_id)
    return {"status": "cleared", "conversation_id": conversation_id}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=True
    )
