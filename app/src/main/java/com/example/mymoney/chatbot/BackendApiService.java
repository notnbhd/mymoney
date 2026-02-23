package com.example.mymoney.chatbot;

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
}
