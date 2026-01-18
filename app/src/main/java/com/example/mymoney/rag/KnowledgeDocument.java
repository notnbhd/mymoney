package com.example.mymoney.rag;

import java.util.List;

/**
 * Represents a document from the financial knowledge base.
 * Each document contains bilingual content (English/Vietnamese) and metadata for retrieval.
 */
public class KnowledgeDocument {
    
    private String id;
    private String topic;
    private String category;
    private String contentEn;      // English content
    private String contentVi;      // Vietnamese content
    private List<String> keywords; // Keywords for matching
    private float[] embedding;     // Pre-computed embedding vector
    private double relevanceScore; // Score calculated during retrieval
    
    public KnowledgeDocument() {}
    
    public KnowledgeDocument(String id, String topic, String category, 
                            String contentEn, String contentVi, List<String> keywords) {
        this.id = id;
        this.topic = topic;
        this.category = category;
        this.contentEn = contentEn;
        this.contentVi = contentVi;
        this.keywords = keywords;
        this.relevanceScore = 0;
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTopic() {
        return topic;
    }
    
    public void setTopic(String topic) {
        this.topic = topic;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getContentEn() {
        return contentEn;
    }
    
    public void setContentEn(String contentEn) {
        this.contentEn = contentEn;
    }
    
    public String getContentVi() {
        return contentVi;
    }
    
    public void setContentVi(String contentVi) {
        this.contentVi = contentVi;
    }
    
    public List<String> getKeywords() {
        return keywords;
    }
    
    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
    
    public float[] getEmbedding() {
        return embedding;
    }
    
    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }
    
    public double getRelevanceScore() {
        return relevanceScore;
    }
    
    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
    
    /**
     * Get content in the preferred language.
     * Falls back to the other language if preferred is empty.
     */
    public String getContent(boolean preferVietnamese) {
        if (preferVietnamese) {
            return (contentVi != null && !contentVi.isEmpty()) ? contentVi : contentEn;
        } else {
            return (contentEn != null && !contentEn.isEmpty()) ? contentEn : contentVi;
        }
    }
    
    /**
     * Get combined content for embedding generation.
     */
    public String getCombinedContent() {
        StringBuilder sb = new StringBuilder();
        if (topic != null) sb.append(topic).append(" ");
        if (contentEn != null) sb.append(contentEn).append(" ");
        if (contentVi != null) sb.append(contentVi).append(" ");
        if (keywords != null) {
            for (String keyword : keywords) {
                sb.append(keyword).append(" ");
            }
        }
        return sb.toString().trim();
    }
    
    @Override
    public String toString() {
        return "KnowledgeDocument{" +
                "id='" + id + '\'' +
                ", topic='" + topic + '\'' +
                ", category='" + category + '\'' +
                ", relevanceScore=" + relevanceScore +
                '}';
    }
}
