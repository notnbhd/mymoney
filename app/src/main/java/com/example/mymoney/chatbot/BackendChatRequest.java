package com.example.mymoney.chatbot;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Request model for the LangChain backend /chat endpoint.
 */
public class BackendChatRequest {

    @SerializedName("user_id")
    private int userId;

    @SerializedName("wallet_id")
    private int walletId;

    @SerializedName("message")
    private String message;

    @SerializedName("financial_context")
    private FinancialContext financialContext;

    @SerializedName("conversation_id")
    private String conversationId;

    public BackendChatRequest(int userId, int walletId, String message) {
        this.userId = userId;
        this.walletId = walletId;
        this.message = message;
        this.financialContext = new FinancialContext();
    }

    // Getters and Setters
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getWalletId() { return walletId; }
    public void setWalletId(int walletId) { this.walletId = walletId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public FinancialContext getFinancialContext() { return financialContext; }
    public void setFinancialContext(FinancialContext financialContext) { this.financialContext = financialContext; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    /**
     * Financial context data collected on-device and sent to the backend.
     */
    public static class FinancialContext {
        @SerializedName("summary")
        private String summary = "";

        @SerializedName("budget_context")
        private String budgetContext = "";

        @SerializedName("pattern_context")
        private String patternContext = "";

        public FinancialContext() {}

        public FinancialContext(String summary, String budgetContext, String patternContext) {
            this.summary = summary != null ? summary : "";
            this.budgetContext = budgetContext != null ? budgetContext : "";
            this.patternContext = patternContext != null ? patternContext : "";
        }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public String getBudgetContext() { return budgetContext; }
        public void setBudgetContext(String budgetContext) { this.budgetContext = budgetContext; }

        public String getPatternContext() { return patternContext; }
        public void setPatternContext(String patternContext) { this.patternContext = patternContext; }
    }
}
