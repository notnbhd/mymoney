# MyMoney Backend API Documentation

FastAPI backend that powers the MyMoney assistant with:
- RAG-based financial chat
- Query parsing
- Split retrieval/generation flow for lower client latency

Base URL (local): `http://localhost:8000`

Interactive docs:
- Swagger UI: `/docs`
- ReDoc: `/redoc`

---

## Run the API

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

or:

```bash
python main.py
```

---

## CORS

The API currently allows all origins, methods, and headers (`*`) for mobile/dev usage.

---

## Common Data Models

### FinancialContext
```json
{
  "summary": "string",
  "budget_context": "string",
  "pattern_context": "string"
}
```

### SourceDocument
```json
{
  "id": "string",
  "topic": "string",
  "category": "string"
}
```

---

## Endpoints

### 1) Health Check
**GET** `/health`

Returns API and knowledge-base status.

**Response**
```json
{
  "status": "ok",
  "knowledge_base_loaded": true,
  "document_count": 123
}
```

---

### 2) Chat (single-call pipeline)
**POST** `/chat`

Runs retrieve + generate in one call.

**Request**
```json
{
  "user_id": 1,
  "wallet_id": 10,
  "message": "How can I reduce food spending this month?",
  "financial_context": {
    "summary": "Income 30M VND, expense 24M VND",
    "budget_context": "Food budget exceeded by 10%",
    "pattern_context": "Weekend food spending is high"
  },
  "conversation_id": "optional-string"
}
```

If `conversation_id` is omitted, backend auto-generates:
`user_{user_id}_wallet_{wallet_id}`.

**Response**
```json
{
  "response": "string",
  "sources": [
    {
      "id": "doc_1",
      "topic": "Budgeting",
      "category": "Finance"
    }
  ],
  "conversation_id": "user_1_wallet_10"
}
```

**Errors**
- `503`: RAG service not initialized
- `500`: Response generation failed

---

### 3) Retrieve (phase 1)
**POST** `/retrieve`

Retrieves and reranks knowledge documents only (no final LLM answer).  
Returns a short-lived `retrieval_id` for `/generate`.

**Request**
```json
{
  "message": "How can I optimize transportation costs?"
}
```

**Response**
```json
{
  "retrieval_id": "uuid",
  "sources": [
    {
      "id": "doc_2",
      "topic": "Transportation",
      "category": "Spending"
    }
  ]
}
```

Notes:
- Retrieval cache TTL is 5 minutes.
- A retrieval result is consumed on first successful `/generate` call.

**Errors**
- `503`: RAG service not initialized
- `500`: Retrieval failed

---

### 4) Generate (phase 2)
**POST** `/generate`

Generates response from:
- original user message
- device financial context
- cached retrieval docs from `/retrieve`

**Request**
```json
{
  "retrieval_id": "uuid-from-retrieve",
  "message": "How can I optimize transportation costs?",
  "financial_context": {
    "summary": "",
    "budget_context": "",
    "pattern_context": ""
  },
  "conversation_id": "optional-string",
  "user_id": 1,
  "wallet_id": 10
}
```

**Response**
```json
{
  "response": "string",
  "sources": [
    {
      "id": "doc_2",
      "topic": "Transportation",
      "category": "Spending"
    }
  ],
  "conversation_id": "user_1_wallet_10"
}
```

**Errors**
- `503`: RAG service not initialized
- `404`: retrieval_id missing/expired/already used
- `500`: Generation failed

---

### 5) Parse Natural Language Query
**POST** `/parse`

Parses a user query into structured fields for analytics/filter logic.

**Request**
```json
{
  "message": "How much did I spend on clothes in December?",
  "current_month": 4,
  "current_year": 2026
}
```

**Response**
```json
{
  "time_range": {
    "type": "specific_month",
    "month": 12,
    "year": 2026,
    "days": null
  },
  "category": "Clothing",
  "query_type": "spending"
}
```

`time_range.type` values:
- `current_month`
- `specific_month`
- `year`
- `last_n_days`
- `all_time`

`query_type` values:
- `spending`
- `income`
- `comparison`
- `trend`
- `category_list`
- `general`

On parse failure, backend returns default:
- `time_range.type = current_month`
- `category = null`
- `query_type = general`

---

### 6) Server Stats
**GET** `/stats`

Returns runtime stats from RAG and conversation memory.

**Response (shape)**
```json
{
  "rag": {
    "initialized": true,
    "document_count": 123,
    "vector_store_type": "string"
  },
  "memory": {}
}
```

---

### 7) Clear Conversation Memory
**DELETE** `/memory/{conversation_id}`

Clears in-memory conversation history for one conversation.

**Response**
```json
{
  "status": "cleared",
  "conversation_id": "user_1_wallet_10"
}
```

---

## Error Format

FastAPI errors return:
```json
{
  "detail": "error message"
}
```
