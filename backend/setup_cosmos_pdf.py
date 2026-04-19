"""
One-time setup script to upload PDF documents to Azure Cosmos DB
with vector embeddings for semantic search.

This script:
1. Scans the pdf_knowledge/ directory for .pdf files
2. Loads and splits each PDF using LangChain's PyPDFLoader
3. Chunks the text using SemanticChunker
4. Computes embeddings using the same multilingual model
5. Uploads each chunk to Cosmos DB (appends to existing container)

Usage:
    cd backend
    pip install -r requirements.txt
    # Place your PDF files in backend/pdf_knowledge/
    python setup_cosmos_pdf.py

Prerequisites:
    - Set COSMOS_DB_ENDPOINT and COSMOS_DB_KEY in your .env file
    - Run setup_cosmos.py first (to create the container)
    - Place PDF files in backend/pdf_knowledge/
"""

import os
import re
import sys
import logging

from langchain_community.document_loaders import PyPDFLoader
from langchain_experimental.text_splitter import SemanticChunker
from azure.cosmos import CosmosClient
from langchain_huggingface import HuggingFaceEmbeddings

from config import settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)
logger = logging.getLogger(__name__)


def load_and_chunk_pdf(pdf_path: str, embeddings_model) -> list[dict]:
    """
    Load a PDF with LangChain's PyPDFLoader and split into semantic chunks.
    Returns a list of dicts with chunk metadata.
    """
    filename = os.path.basename(pdf_path)
    safe_name = re.sub(r"[^a-zA-Z0-9_-]", "_", filename)

    # Load PDF pages using LangChain
    loader = PyPDFLoader(pdf_path)
    pages = loader.load()
    logger.info(f"Loaded {len(pages)} pages from {filename}")

    if not pages:
        logger.warning(f"No content extracted from {filename}")
        return []

    # Split into chunks semantically
    logger.info(f"Splitting {filename} using SemanticChunker...")
    splitter = SemanticChunker(embeddings_model)
    chunks_docs = splitter.split_documents(pages)

    # Convert to our format
    chunks = []
    for idx, doc in enumerate(chunks_docs):
        text = doc.page_content.strip()
        if not text:
            continue

        page_num = doc.metadata.get("page", 0) + 1  # PyPDFLoader uses 0-indexed

        chunks.append({
            "doc_id": f"pdf_{safe_name}_{idx:04d}",
            "topic": filename,
            "category": "pdf_document",
            "page_number": page_num,
            "text_content": text,
            "source_type": "pdf",
        })

    logger.info(
        f"{filename}: {len(pages)} pages → {len(chunks)} semantic chunks"
    )
    return chunks


def compute_embeddings(chunks: list[dict], embeddings_model) -> list[dict]:
    """Compute embedding vectors for each chunk."""
    logger.info(f"Computing embeddings for {len(chunks)} chunks...")

    for i, chunk in enumerate(chunks):
        text = (
            f"Source: {chunk['topic']} (page {chunk['page_number']})\n"
            f"{chunk['text_content']}"
        )
        chunk["embedding"] = embeddings_model.embed_query(text)

        if (i + 1) % 10 == 0:
            logger.info(f"  Embedded {i + 1}/{len(chunks)}")

    logger.info(f"All {len(chunks)} embeddings computed!")
    return chunks


def upload_to_cosmos(chunks: list[dict]):
    """Upload PDF chunks to the existing Cosmos DB container (append mode)."""
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

    logger.info(f"Connecting to Cosmos DB: {endpoint}")
    client = CosmosClient(endpoint, credential=key)
    database = client.get_database_client(settings.COSMOS_DB_DATABASE)
    container = database.get_container_client(settings.COSMOS_DB_CONTAINER)

    logger.info(f"Uploading {len(chunks)} PDF chunks to '{settings.COSMOS_DB_CONTAINER}'...")

    for i, chunk in enumerate(chunks):
        document = {
            "id": chunk["doc_id"],
            "topic": chunk["topic"],
            "category": chunk["category"],
            "content_en": chunk["text_content"],
            "content_vi": chunk["text_content"],
            "keywords": [],
            "text_content": chunk["text_content"],
            "embedding": chunk["embedding"],
            "source_type": chunk["source_type"],
            "page_number": chunk["page_number"],
        }
        container.upsert_item(document)

        if (i + 1) % 10 == 0:
            logger.info(f"  Uploaded {i + 1}/{len(chunks)}")

    logger.info(f"✅ All {len(chunks)} PDF chunks uploaded to Cosmos DB!")


def main():
    # 1. Find PDF files
    pdf_dir = settings.PDF_KNOWLEDGE_DIR
    logger.info(f"Scanning for PDFs in: {pdf_dir}")

    if not os.path.isdir(pdf_dir):
        logger.error(
            f"PDF directory not found: {pdf_dir}\n"
            f"Create it and add your PDF files:\n"
            f"  mkdir -p {pdf_dir}\n"
            f"  cp your_document.pdf {pdf_dir}/"
        )
        sys.exit(1)

    pdf_files = sorted([
        f for f in os.listdir(pdf_dir)
        if f.lower().endswith(".pdf")
    ])

    if not pdf_files:
        logger.error(f"No PDF files found in {pdf_dir}")
        sys.exit(1)

    logger.info(f"Found {len(pdf_files)} PDF file(s): {pdf_files}")

    # 2. Loading embedding model
    logger.info(f"Loading embedding model: {settings.EMBEDDING_MODEL}")
    embeddings_model = HuggingFaceEmbeddings(
        model_name=settings.EMBEDDING_MODEL,
        model_kwargs={"device": "cpu"},
        encode_kwargs={"normalize_embeddings": True},
    )

    # 3. Load, split, and chunk all PDFs using SemanticChunker
    all_chunks = []
    for pdf_file in pdf_files:
        pdf_path = os.path.join(pdf_dir, pdf_file)
        chunks = load_and_chunk_pdf(
            pdf_path,
            embeddings_model=embeddings_model
        )
        all_chunks.extend(chunks)

    logger.info(f"Total: {len(all_chunks)} semantic chunks from {len(pdf_files)} PDF(s)")

    # 4. Compute embeddings
    all_chunks = compute_embeddings(all_chunks, embeddings_model)

    # 5. Upload to Cosmos DB
    upload_to_cosmos(all_chunks)

    logger.info(
        f"\n🎉 PDF ingestion complete!\n"
        f"   {len(pdf_files)} PDF(s) → {len(all_chunks)} chunks uploaded\n"
        f"   Container: {settings.COSMOS_DB_CONTAINER}\n"
        f"   These documents are now searchable alongside your JSON knowledge base."
    )


if __name__ == "__main__":
    main()
