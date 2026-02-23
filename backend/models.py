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
