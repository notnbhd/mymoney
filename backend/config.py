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
    OPENROUTER_MODEL: str = "mistralai/devstral-2512:free"
    OPENROUTER_BASE_URL: str = "https://openrouter.ai/api/v1"

    # Server
    HOST: str = "0.0.0.0"
    PORT: int = 8000

    # RAG
    KNOWLEDGE_BASE_PATH: str = os.path.join(
        _project_root,
        "app", "src", "main", "assets", "knowledge",
        "financial_knowledge_base.json"
    )
    VECTOR_STORE_TYPE: str = "cosmos"  # "faiss" for local file, "cosmos" for Azure Cosmos DB
    VECTOR_STORE_PATH: str = os.path.join(_backend_dir, "vector_store")  # FAISS only
    EMBEDDING_MODEL: str = "sentence-transformers/all-MiniLM-L6-v2"
    RAG_TOP_K: int = 3

    # Azure Cosmos DB
    COSMOS_DB_ENDPOINT: str = "https://mymoney.documents.azure.com:443/"
    COSMOS_DB_KEY: str = ""
    COSMOS_DB_DATABASE: str = "mymoney"
    COSMOS_DB_CONTAINER: str = "knowledge_vectors"

    # Memory
    MEMORY_WINDOW_SIZE: int = 10  # Keep last N messages per conversation
    MEMORY_TTL_SECONDS: int = 3600  # Auto-cleanup after 1 hour of inactivity

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
