"""
Configuration management using Pydantic Settings.
Loads from .env file or environment variables.
"""

import os
from pydantic_settings import BaseSettings
from dotenv import load_dotenv

# Load .env from backend dir or project root
_backend_dir = os.path.dirname(os.path.abspath(__file__))
_project_root = os.path.dirname(_backend_dir)

# Try backend/.env first, then project root .env
if os.path.exists(os.path.join(_backend_dir, ".env")):
    load_dotenv(os.path.join(_backend_dir, ".env"))
elif os.path.exists(os.path.join(_project_root, ".env")):
    load_dotenv(os.path.join(_project_root, ".env"))


class Settings(BaseSettings):
    # OpenRouter / LLM
    OPENROUTER_API_TOKEN: str = ""
    OPENROUTER_MODEL: str = "openai/gpt-oss-120b:free"
    OPENROUTER_BASE_URL: str = "https://openrouter.ai/api/v1"

    # Server
    HOST: str = "0.0.0.0"
    PORT: int = 8010
    # Workers: number of Uvicorn processes.
    # Keep at 1 for dev (each worker loads ~700MB of ML models).
    # Set to 2-4 on a production server with enough RAM.
    WORKERS: int = 1

    # RAG
    KNOWLEDGE_BASE_PATH: str = os.path.join(
        _project_root,
        "app", "src", "main", "assets", "knowledge",
        "financial_knowledge_base.json"
    )
    EMBEDDING_MODEL: str = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    RAG_TOP_K: int = 15          # Initial retrieval pool (before re-ranking)
    RAG_RERANK_TOP_K: int = 4   # Final top-K after re-ranking
    RAG_SIMILARITY_THRESHOLD: float = 0.30  # Min cosine similarity to keep a doc
    RAG_RERANKER_MODEL: str = "cross-encoder/mmarco-mMiniLMv2-L12-H384-v1"
    # Hybrid Search weights (dense + BM25 via RRF)
    RAG_HYBRID_DENSE_WEIGHT: float = 0.6   # Weight for vector similarity
    RAG_HYBRID_BM25_WEIGHT: float = 0.4    # Weight for full-text (BM25) score
    RAG_RRF_K: int = 60                    # RRF smoothing constant

    # PDF Ingestion
    PDF_KNOWLEDGE_DIR: str = os.path.join(_backend_dir, "pdf_knowledge")
    PDF_CHUNK_SIZE: int = 1024
    PDF_CHUNK_OVERLAP: int = 100

    # PostgreSQL + pgvector
    POSTGRES_URL: str = "postgresql://god@localhost:5432/mymoney"
    POSTGRES_TABLE: str = "knowledge"

    # Memory
    REDIS_URL: str = "redis://localhost:6379/0"
    MEMORY_WINDOW_SIZE: int = 10  # Keep last N messages per conversation
    MEMORY_TTL_SECONDS: int = 3600  # Auto-cleanup after 1 hour of inactivity

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
