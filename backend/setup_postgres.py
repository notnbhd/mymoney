"""
One-time setup script: create the knowledge table and upload the financial
knowledge base + any PDF documents to PostgreSQL with pgvector embeddings.

Usage:
    cd backend
    python setup_postgres.py

    # To also ingest PDFs from backend/pdf_knowledge/
    python setup_postgres.py --pdfs

Prerequisites:
    - PostgreSQL running with pgvector extension installed
    - POSTGRES_URL in backend/.env (default: postgresql://god@localhost:5432/mymoney)
    - financial_knowledge_base.json in the assets folder
"""

import argparse
import json
import logging
import os
import re
import sys

import psycopg2
import psycopg2.extras
from langchain_huggingface import HuggingFaceEmbeddings

from config import settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger(__name__)


# ─── Schema DDL ─────────────────────────────────────────────────────

DDL_TABLE = """
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS {table} (
    id           TEXT PRIMARY KEY,
    topic        TEXT NOT NULL DEFAULT '',
    category     TEXT NOT NULL DEFAULT '',
    content_vi   TEXT,
    content_en   TEXT,
    keywords     TEXT[],
    text_content TEXT,
    source_type  TEXT NOT NULL DEFAULT 'json',
    page_number  INT,
    embedding    vector(384),
    search_vector TSVECTOR
);

-- HNSW index for fast ANN vector search (cosine distance)
CREATE INDEX IF NOT EXISTS {table}_embedding_hnsw
    ON {table} USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- GIN index for full-text search
CREATE INDEX IF NOT EXISTS {table}_fts_gin
    ON {table} USING gin (search_vector);

-- Index on category for analytics queries
CREATE INDEX IF NOT EXISTS {table}_category_idx
    ON {table} (category);
"""

DDL_TRIGGER = """
CREATE OR REPLACE FUNCTION {table}_update_search_vector()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('simple',
            coalesce(NEW.content_vi,  '') || ' ' ||
            coalesce(NEW.content_en,  '') || ' ' ||
            coalesce(NEW.topic,       '') || ' ' ||
            coalesce(array_to_string(NEW.keywords, ' '), '')
        );
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS {table}_search_vector_trigger ON {table};
CREATE TRIGGER {table}_search_vector_trigger
    BEFORE INSERT OR UPDATE ON {table}
    FOR EACH ROW EXECUTE FUNCTION {table}_update_search_vector();
"""


def ensure_schema(conn: psycopg2.extensions.connection):
    """Create the knowledge table, indexes, and search_vector trigger."""
    table = settings.POSTGRES_TABLE
    logger.info(f"Ensuring schema for table '{table}'...")
    with conn.cursor() as cur:
        cur.execute(DDL_TABLE.format(table=table))
        cur.execute(DDL_TRIGGER.format(table=table))
    conn.commit()
    logger.info("Schema ready (table + HNSW index + GIN index + trigger).")


# ─── Knowledge Base (JSON) ───────────────────────────────────────────

def load_knowledge_base(path: str) -> list[dict]:
    """Load and flatten the financial knowledge base JSON."""
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)

    entries = []
    skip_keys = {"version", "lastUpdated", "description"}

    for category, items in data.items():
        if category in skip_keys or not isinstance(items, list):
            continue
        for item in items:
            entries.append({
                "doc_id":     item.get("id", ""),
                "topic":      item.get("topic", ""),
                "category":   category,
                "content_en": item.get("content_en", ""),
                "content_vi": item.get("content_vi", ""),
                "keywords":   item.get("keywords", []),
                "source_type": "json",
                "page_number": None,
            })

    logger.info(f"Loaded {len(entries)} entries from knowledge base JSON.")
    return entries


# ─── PDF Ingestion ───────────────────────────────────────────────────

def load_pdf_chunks(pdf_dir: str, embeddings_model) -> list[dict]:
    """Load PDFs from pdf_dir and split into semantic chunks."""
    try:
        from langchain_community.document_loaders import PyPDFLoader
        from langchain_experimental.text_splitter import SemanticChunker
    except ImportError as e:
        logger.error(f"Missing dependency for PDF ingestion: {e}")
        return []

    pdf_files = sorted(
        f for f in os.listdir(pdf_dir) if f.lower().endswith(".pdf")
    )
    if not pdf_files:
        logger.warning(f"No PDF files found in {pdf_dir}")
        return []

    splitter = SemanticChunker(embeddings_model)
    all_chunks = []

    for filename in pdf_files:
        path = os.path.join(pdf_dir, filename)
        safe_name = re.sub(r"[^a-zA-Z0-9_-]", "_", filename)
        logger.info(f"Processing PDF: {filename}")

        loader = PyPDFLoader(path)
        pages = loader.load()
        if not pages:
            logger.warning(f"No content in {filename}, skipping.")
            continue

        chunks_docs = splitter.split_documents(pages)
        for idx, doc in enumerate(chunks_docs):
            text = doc.page_content.strip()
            if not text:
                continue
            page_num = doc.metadata.get("page", 0) + 1
            all_chunks.append({
                "doc_id":      f"pdf_{safe_name}_{idx:04d}",
                "topic":       filename,
                "category":    "pdf_document",
                "content_vi":  text,
                "content_en":  text,
                "keywords":    [],
                "text_content": text,
                "source_type": "pdf",
                "page_number": page_num,
            })

        logger.info(
            f"  {filename}: {len(pages)} pages → "
            f"{sum(1 for c in all_chunks if c['topic'] == filename)} chunks"
        )

    logger.info(f"Total PDF chunks: {len(all_chunks)}")
    return all_chunks


# ─── Embedding ───────────────────────────────────────────────────────

def compute_embeddings(entries: list[dict], model) -> list[dict]:
    """Compute 384-dim embeddings for each entry in-place."""
    logger.info(f"Computing embeddings for {len(entries)} entries...")
    for i, entry in enumerate(entries):
        # Rich text combining all fields for a better embedding
        text = (
            f"Topic: {entry['topic']}\n"
            f"Category: {entry['category']}\n"
            f"Vietnamese: {entry.get('content_vi', '')}\n"
            f"English: {entry.get('content_en', '')}\n"
            f"Keywords: {', '.join(entry.get('keywords', []))}"
        )
        entry["text_content"] = entry.get("text_content") or text
        entry["embedding"] = model.embed_query(text)

        if (i + 1) % 10 == 0 or (i + 1) == len(entries):
            logger.info(f"  Embedded {i + 1}/{len(entries)}")

    return entries


# ─── Upload ──────────────────────────────────────────────────────────

def upload_to_postgres(conn: psycopg2.extensions.connection, entries: list[dict]):
    """Upsert entries into the knowledge table."""
    table = settings.POSTGRES_TABLE
    sql = f"""
        INSERT INTO {table}
            (id, topic, category, content_vi, content_en,
             keywords, text_content, source_type, page_number, embedding)
        VALUES
            (%(id)s, %(topic)s, %(category)s, %(content_vi)s, %(content_en)s,
             %(keywords)s, %(text_content)s, %(source_type)s, %(page_number)s,
             %(embedding)s::vector)
        ON CONFLICT (id) DO UPDATE SET
            topic        = EXCLUDED.topic,
            category     = EXCLUDED.category,
            content_vi   = EXCLUDED.content_vi,
            content_en   = EXCLUDED.content_en,
            keywords     = EXCLUDED.keywords,
            text_content = EXCLUDED.text_content,
            source_type  = EXCLUDED.source_type,
            page_number  = EXCLUDED.page_number,
            embedding    = EXCLUDED.embedding
    """  # noqa: S608

    logger.info(f"Uploading {len(entries)} documents to '{table}'...")
    with conn.cursor() as cur:
        for i, entry in enumerate(entries):
            embedding_str = "[" + ",".join(map(str, entry["embedding"])) + "]"
            cur.execute(sql, {
                "id":           entry["doc_id"],
                "topic":        entry["topic"],
                "category":     entry["category"],
                "content_vi":   entry.get("content_vi", ""),
                "content_en":   entry.get("content_en", ""),
                "keywords":     entry.get("keywords", []),
                "text_content": entry.get("text_content", ""),
                "source_type":  entry.get("source_type", "json"),
                "page_number":  entry.get("page_number"),
                "embedding":    embedding_str,
            })
            if (i + 1) % 10 == 0 or (i + 1) == len(entries):
                logger.info(f"  Uploaded {i + 1}/{len(entries)}")

    conn.commit()
    logger.info(f"✅ All {len(entries)} documents uploaded to PostgreSQL!")


# ─── Main ────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="Setup MyMoney knowledge base in PostgreSQL")
    parser.add_argument(
        "--pdfs",
        action="store_true",
        help="Also ingest PDFs from backend/pdf_knowledge/",
    )
    parser.add_argument(
        "--reset",
        action="store_true",
        help="Drop and recreate the knowledge table before ingesting",
    )
    args = parser.parse_args()

    # 1. Connect
    logger.info(f"Connecting to PostgreSQL: {settings.POSTGRES_URL}")
    try:
        conn = psycopg2.connect(settings.POSTGRES_URL)
    except Exception as e:
        logger.error(f"Connection failed: {e}")
        sys.exit(1)

    # 2. Optionally reset
    if args.reset:
        logger.warning(f"Dropping table '{settings.POSTGRES_TABLE}'...")
        with conn.cursor() as cur:
            cur.execute(f"DROP TABLE IF EXISTS {settings.POSTGRES_TABLE}")
        conn.commit()
        logger.info("Table dropped.")

    # 3. Ensure schema
    ensure_schema(conn)

    # 4. Load embedding model
    logger.info(f"Loading embedding model: {settings.EMBEDDING_MODEL}")
    model = HuggingFaceEmbeddings(
        model_name=settings.EMBEDDING_MODEL,
        model_kwargs={"device": "cpu"},
        encode_kwargs={"normalize_embeddings": True},
    )

    # 5. Load JSON knowledge base
    kb_path = settings.KNOWLEDGE_BASE_PATH
    if not os.path.exists(kb_path):
        logger.error(f"Knowledge base not found: {kb_path}")
        sys.exit(1)

    entries = load_knowledge_base(kb_path)
    entries = compute_embeddings(entries, model)
    upload_to_postgres(conn, entries)

    # 6. Optionally ingest PDFs
    if args.pdfs:
        pdf_dir = settings.PDF_KNOWLEDGE_DIR
        if os.path.isdir(pdf_dir):
            pdf_chunks = load_pdf_chunks(pdf_dir, model)
            if pdf_chunks:
                pdf_chunks = compute_embeddings(pdf_chunks, model)
                upload_to_postgres(conn, pdf_chunks)
        else:
            logger.warning(f"PDF directory not found: {pdf_dir}")

    conn.close()
    logger.info(
        "\n🎉 Setup complete!\n"
        f"   Table: {settings.POSTGRES_TABLE}\n"
        "   Start the backend with: python main.py"
    )


if __name__ == "__main__":
    main()
