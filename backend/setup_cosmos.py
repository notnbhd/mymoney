"""
One-time setup script to upload the financial knowledge base to Azure Cosmos DB
with vector embeddings for semantic search.

This script:
1. Connects to your Cosmos DB instance
2. Creates a vector-search-enabled container (knowledge_vectors)
3. Reads the financial_knowledge_base.json
4. Computes embeddings for each knowledge entry
5. Uploads each entry as a separate document with its embedding

Usage:
    cd backend
    pip install -r requirements.txt
    python setup_cosmos.py

Prerequisites:
    - Set COSMOS_DB_ENDPOINT and COSMOS_DB_KEY in your .env file
    - Have the financial_knowledge_base.json in the assets folder
"""

import json
import os
import sys
import logging

from azure.cosmos import CosmosClient, PartitionKey, exceptions
from langchain_huggingface import HuggingFaceEmbeddings

from config import settings

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)

# Container config for vector search
VECTOR_EMBEDDING_POLICY = {
    "vectorEmbeddings": [
        {
            "path": "/embedding",
            "dataType": "float32",
            "distanceFunction": "cosine",
            "dimensions": 384,  # all-MiniLM-L6-v2 produces 384-dim vectors
        }
    ]
}

INDEXING_POLICY = {
    "indexingMode": "consistent",
    "includedPaths": [{"path": "/*"}],
    "excludedPaths": [{"path": '/"_etag"/?'}],
    "vectorIndexes": [
        {
            "path": "/embedding",
            "type": "quantizedFlat",
        }
    ],
}


def load_knowledge_base(path: str) -> list[dict]:
    """Load and flatten the knowledge base JSON into individual entries."""
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    entries = []
    skip_keys = {"version", "lastUpdated", "description"}

    for category, items in data.items():
        if category in skip_keys:
            continue
        if not isinstance(items, list):
            continue

        for item in items:
            entries.append({
                "doc_id": item.get("id", ""),
                "topic": item.get("topic", ""),
                "category": category,
                "content_en": item.get("content_en", ""),
                "content_vi": item.get("content_vi", ""),
                "keywords": item.get("keywords", []),
            })

    return entries


def compute_embeddings(entries: list[dict], embeddings_model) -> list[dict]:
    """Compute embedding vectors for each knowledge entry."""
    logger.info(f"Computing embeddings for {len(entries)} entries...")

    for i, entry in enumerate(entries):
        # Combine all text for a rich embedding
        text = (
            f"Topic: {entry['topic']}\n"
            f"Category: {entry['category']}\n"
            f"English: {entry['content_en']}\n"
            f"Vietnamese: {entry['content_vi']}\n"
            f"Keywords: {', '.join(entry['keywords'])}"
        )
        entry["text_content"] = text
        entry["embedding"] = embeddings_model.embed_query(text)

        if (i + 1) % 5 == 0:
            logger.info(f"  Embedded {i + 1}/{len(entries)}")

    logger.info(f"All {len(entries)} embeddings computed!")
    return entries


def setup_cosmos_db(entries: list[dict]):
    """Create container and upload documents to Cosmos DB."""
    endpoint = settings.COSMOS_DB_ENDPOINT
    key = settings.COSMOS_DB_KEY

    if not endpoint or not key:
        logger.error(
            "COSMOS_DB_ENDPOINT and COSMOS_DB_KEY must be set in .env!\n"
            "Add these to backend/.env:\n"
            "  COSMOS_DB_ENDPOINT=https://mymoney.documents.azure.com:443/\n"
            "  COSMOS_DB_KEY=your-primary-key-here"
        )
        sys.exit(1)

    # Connect to Cosmos DB
    logger.info(f"Connecting to Cosmos DB: {endpoint}")
    client = CosmosClient(endpoint, credential=key)

    # Get or create database
    database = client.create_database_if_not_exists(id=settings.COSMOS_DB_DATABASE)
    logger.info(f"Database: {settings.COSMOS_DB_DATABASE}")

    # Create the vector-search container
    # Using a separate container name to preserve your existing container1 data
    container_name = settings.COSMOS_DB_CONTAINER
    logger.info(f"Creating vector-search container: {container_name}")

    try:
        # Delete existing container if it exists (clean slate)
        database.delete_container(container_name)
        logger.info(f"  Deleted existing container '{container_name}'")
    except exceptions.CosmosResourceNotFoundError:
        pass

    container = database.create_container(
        id=container_name,
        partition_key=PartitionKey(path="/category"),
        vector_embedding_policy=VECTOR_EMBEDDING_POLICY,
        indexing_policy=INDEXING_POLICY,
        offer_throughput=400,  # Minimal RU/s (cost-effective)
    )
    logger.info(f"  Container created with vector search policy!")

    # Upload documents
    logger.info(f"Uploading {len(entries)} documents...")
    for i, entry in enumerate(entries):
        document = {
            "id": entry["doc_id"],
            "topic": entry["topic"],
            "category": entry["category"],
            "content_en": entry["content_en"],
            "content_vi": entry["content_vi"],
            "keywords": entry["keywords"],
            "text_content": entry["text_content"],
            "embedding": entry["embedding"],
        }
        container.upsert_item(document)

        if (i + 1) % 5 == 0:
            logger.info(f"  Uploaded {i + 1}/{len(entries)}")

    logger.info(f"✅ All {len(entries)} documents uploaded to Cosmos DB!")
    logger.info(f"   Database: {settings.COSMOS_DB_DATABASE}")
    logger.info(f"   Container: {container_name}")
    logger.info(f"   Each document has a 384-dim embedding vector for semantic search")


def main():
    # 1. Load knowledge base
    kb_path = settings.KNOWLEDGE_BASE_PATH
    logger.info(f"Loading knowledge base from: {kb_path}")

    if not os.path.exists(kb_path):
        logger.error(f"Knowledge base not found: {kb_path}")
        sys.exit(1)

    entries = load_knowledge_base(kb_path)
    logger.info(f"Loaded {len(entries)} knowledge entries")

    # 2. Compute embeddings
    logger.info(f"Loading embedding model: {settings.EMBEDDING_MODEL}")
    embeddings_model = HuggingFaceEmbeddings(
        model_name=settings.EMBEDDING_MODEL,
        model_kwargs={"device": "cpu"},
        encode_kwargs={"normalize_embeddings": True},
    )
    entries = compute_embeddings(entries, embeddings_model)

    # 3. Upload to Cosmos DB
    setup_cosmos_db(entries)

    logger.info("\n🎉 Setup complete! Your backend is ready to use Cosmos DB vector search.")
    logger.info("Start the backend with: python main.py")


if __name__ == "__main__":
    main()
