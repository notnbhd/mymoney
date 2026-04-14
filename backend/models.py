"""
Pydantic models for API request/response validation.
"""

from typing import List, Optional
from pydantic import BaseModel, Field


class FinancialContext(BaseModel):
    """Financial data sent from the Android app."""
    summary: str = Field(default="", description="Financial summary (income, expenses, etc.)")
    budget_context: str = Field(default="", description="Budget analysis context")
    pattern_context: str = Field(default="", description="Spending pattern analysis")


class ChatRequest(BaseModel):
    """Request body for the /chat endpoint."""
    user_id: int = Field(..., description="User ID from the Android app")
    wallet_id: int = Field(..., description="Wallet ID from the Android app")
    message: str = Field(..., description="User's query message")
    financial_context: FinancialContext = Field(
        default_factory=FinancialContext,
        description="Financial data collected on-device"
    )
    conversation_id: Optional[str] = Field(
        default=None,
        description="Conversation ID for memory. Auto-generated if not provided."
    )


class SourceDocument(BaseModel):
    """A knowledge document used to generate the response."""
    id: str
    topic: str
    category: str


class ChatResponse(BaseModel):
    """Response body from the /chat endpoint."""
    response: str = Field(..., description="Generated financial advice")
    sources: List[SourceDocument] = Field(
        default_factory=list,
        description="Knowledge documents used in the response"
    )
    conversation_id: str = Field(..., description="Conversation ID for follow-up messages")


class HealthResponse(BaseModel):
    """Response body from the /health endpoint."""
    status: str = "ok"
    knowledge_base_loaded: bool = False
    document_count: int = 0


# ─── Parse endpoint models ─────────────────────────────────────

class ParseRequest(BaseModel):
    """Request body for the /parse endpoint."""
    message: str = Field(..., description="User's natural language query")
    current_month: int = Field(..., description="Current month (1-12)")
    current_year: int = Field(..., description="Current year")

class TimeRange(BaseModel):
    """Parsed time range from user query."""
    type: str = Field(default="current_month", description="current_month|specific_month|year|last_n_days|all_time")
    month: Optional[int] = Field(default=None, description="Month number (1-12)")
    year: Optional[int] = Field(default=None, description="Year")
    days: Optional[int] = Field(default=None, description="Number of days for last_n_days type")

class ParseResponse(BaseModel):
    """Response body from the /parse endpoint."""
    time_range: TimeRange = Field(default_factory=TimeRange)
    category: Optional[str] = Field(default=None, description="Category name or null")
    query_type: str = Field(default="general", description="spending|income|comparison|trend|category_list|general")
