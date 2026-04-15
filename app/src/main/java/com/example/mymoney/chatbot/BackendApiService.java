package com.example.mymoney.chatbot;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * Retrofit interface for the LangChain backend API.
 */
public interface BackendApiService {

    @POST("chat")
    Call<BackendChatResponse> chat(@Body BackendChatRequest request);

    @POST("parse")
    Call<BackendParseResponse> parse(@Body BackendParseRequest request);

    @POST("retrieve")
    Call<BackendRetrieveResponse> retrieve(@Body BackendRetrieveRequest request);

    @POST("generate")
    Call<BackendChatResponse> generate(@Body BackendGenerateRequest request);

    @GET("health")
    Call<BackendHealthResponse> health();

    /**
     * Simple health check response.
     */
    class BackendHealthResponse {
        private String status;
        private boolean knowledge_base_loaded;
        private int document_count;

        public String getStatus() { return status; }
        public boolean isKnowledgeBaseLoaded() { return knowledge_base_loaded; }
        public int getDocumentCount() { return document_count; }
    }

    // ─── Parse models ─────────────────────────────────────────

    class BackendParseRequest {
        @SerializedName("message")
        private String message;

        @SerializedName("current_month")
        private int currentMonth;

        @SerializedName("current_year")
        private int currentYear;

        public BackendParseRequest(String message, int currentMonth, int currentYear) {
            this.message = message;
            this.currentMonth = currentMonth;
            this.currentYear = currentYear;
        }
    }

    class BackendParseResponse {
        @SerializedName("time_range")
        private ParsedTimeRange timeRange;

        @SerializedName("category")
        private String category;

        @SerializedName("query_type")
        private String queryType;

        public ParsedTimeRange getTimeRange() { return timeRange; }
        public String getCategory() { return category; }
        public String getQueryType() { return queryType; }
    }

    class ParsedTimeRange {
        @SerializedName("type")
        private String type;

        @SerializedName("month")
        private Integer month;

        @SerializedName("year")
        private Integer year;

        @SerializedName("days")
        private Integer days;

        public String getType() { return type != null ? type : "current_month"; }
        public Integer getMonth() { return month; }
        public Integer getYear() { return year; }
        public Integer getDays() { return days; }
    }

    // ─── Retrieve models ──────────────────────────────────────

    class BackendRetrieveRequest {
        @SerializedName("message")
        private String message;

        public BackendRetrieveRequest(String message) {
            this.message = message;
        }
    }

    class BackendRetrieveResponse {
        @SerializedName("retrieval_id")
        private String retrievalId;

        @SerializedName("sources")
        private java.util.List<BackendChatResponse.Source> sources;

        public String getRetrievalId() { return retrievalId; }
        public java.util.List<BackendChatResponse.Source> getSources() { return sources; }
    }

    // ─── Generate models ──────────────────────────────────────

    class BackendGenerateRequest {
        @SerializedName("retrieval_id")
        private String retrievalId;

        @SerializedName("message")
        private String message;

        @SerializedName("financial_context")
        private BackendChatRequest.FinancialContext financialContext;

        @SerializedName("conversation_id")
        private String conversationId;

        @SerializedName("user_id")
        private int userId;

        @SerializedName("wallet_id")
        private int walletId;

        public BackendGenerateRequest(String retrievalId, String message,
                                       BackendChatRequest.FinancialContext financialContext,
                                       String conversationId, int userId, int walletId) {
            this.retrievalId = retrievalId;
            this.message = message;
            this.financialContext = financialContext;
            this.conversationId = conversationId;
            this.userId = userId;
            this.walletId = walletId;
        }
    }
}
