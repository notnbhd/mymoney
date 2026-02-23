package com.example.mymoney.chatbot;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response model from the LangChain backend /chat endpoint.
 */
public class BackendChatResponse {

    @SerializedName("response")
    private String response;

    @SerializedName("sources")
    private List<Source> sources;

    @SerializedName("conversation_id")
    private String conversationId;

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public List<Source> getSources() { return sources; }
    public void setSources(List<Source> sources) { this.sources = sources; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    /**
     * A knowledge document source used in the response.
     */
    public static class Source {
        @SerializedName("id")
        private String id;

        @SerializedName("topic")
        private String topic;

        @SerializedName("category")
        private String category;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }
}
