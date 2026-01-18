package com.example.mymoney.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * RAGContext holds all retrieved context for generating LLM responses.
 * This includes relevant knowledge documents, user financial data, and query metadata.
 */
public class RAGContext {
    
    // Retrieved knowledge documents
    private List<KnowledgeDocument> relevantDocuments;
    
    // User's financial context
    private String financialSummary;
    private String budgetContext;
    private String spendingPatternContext;
    private String comparisonContext;
    private String trendContext;
    
    // Query metadata
    private String originalQuery;
    private String detectedLanguage; // "vi" or "en"
    private String detectedCategory; // Category if query is category-specific
    private String queryType;        // "spending", "saving", "investment", etc.
    
    // Configuration
    private int maxDocuments = 5;
    private boolean preferVietnamese = true;
    
    public RAGContext() {
        this.relevantDocuments = new ArrayList<>();
    }
    
    /**
     * Build the enhanced prompt with all retrieved context.
     */
    public String buildEnhancedPrompt(String userQuery) {
        StringBuilder prompt = new StringBuilder();
        
        // Add knowledge context
        if (!relevantDocuments.isEmpty()) {
            prompt.append("[KIẾN THỨC TÀI CHÍNH LIÊN QUAN]\n");
            for (int i = 0; i < Math.min(relevantDocuments.size(), maxDocuments); i++) {
                KnowledgeDocument doc = relevantDocuments.get(i);
                prompt.append("• ").append(doc.getTopic()).append(": ");
                prompt.append(doc.getContent(preferVietnamese)).append("\n");
            }
            prompt.append("\n");
        }
        
        // Add financial data context
        if (financialSummary != null && !financialSummary.isEmpty()) {
            prompt.append("[DỮ LIỆU TÀI CHÍNH NGƯỜI DÙNG]\n");
            prompt.append(financialSummary).append("\n\n");
        }
        
        // Add budget context
        if (budgetContext != null && !budgetContext.isEmpty()) {
            prompt.append(budgetContext).append("\n");
        }
        
        // Add spending pattern context
        if (spendingPatternContext != null && !spendingPatternContext.isEmpty()) {
            prompt.append(spendingPatternContext).append("\n");
        }
        
        // Add comparison context
        if (comparisonContext != null && !comparisonContext.isEmpty()) {
            prompt.append("[SO SÁNH]\n");
            prompt.append(comparisonContext).append("\n\n");
        }
        
        // Add trend context
        if (trendContext != null && !trendContext.isEmpty()) {
            prompt.append("[XU HƯỚNG]\n");
            prompt.append(trendContext).append("\n\n");
        }
        
        // Add user query
        prompt.append("[CÂU HỎI NGƯỜI DÙNG]\n");
        prompt.append(userQuery);
        
        return prompt.toString();
    }
    
    /**
     * Get a summary of the retrieved context for logging/debugging.
     */
    public String getSummary() {
        return String.format("RAGContext: %d docs, hasFinancial=%b, hasBudget=%b, lang=%s",
                relevantDocuments.size(),
                financialSummary != null,
                budgetContext != null,
                detectedLanguage);
    }
    
    // Getters and Setters
    public List<KnowledgeDocument> getRelevantDocuments() {
        return relevantDocuments;
    }
    
    public void setRelevantDocuments(List<KnowledgeDocument> relevantDocuments) {
        this.relevantDocuments = relevantDocuments;
    }
    
    public void addDocument(KnowledgeDocument document) {
        this.relevantDocuments.add(document);
    }
    
    public String getFinancialSummary() {
        return financialSummary;
    }
    
    public void setFinancialSummary(String financialSummary) {
        this.financialSummary = financialSummary;
    }
    
    public String getBudgetContext() {
        return budgetContext;
    }
    
    public void setBudgetContext(String budgetContext) {
        this.budgetContext = budgetContext;
    }
    
    public String getSpendingPatternContext() {
        return spendingPatternContext;
    }
    
    public void setSpendingPatternContext(String spendingPatternContext) {
        this.spendingPatternContext = spendingPatternContext;
    }
    
    public String getComparisonContext() {
        return comparisonContext;
    }
    
    public void setComparisonContext(String comparisonContext) {
        this.comparisonContext = comparisonContext;
    }
    
    public String getTrendContext() {
        return trendContext;
    }
    
    public void setTrendContext(String trendContext) {
        this.trendContext = trendContext;
    }
    
    public String getOriginalQuery() {
        return originalQuery;
    }
    
    public void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery;
    }
    
    public String getDetectedLanguage() {
        return detectedLanguage;
    }
    
    public void setDetectedLanguage(String detectedLanguage) {
        this.detectedLanguage = detectedLanguage;
    }
    
    public String getDetectedCategory() {
        return detectedCategory;
    }
    
    public void setDetectedCategory(String detectedCategory) {
        this.detectedCategory = detectedCategory;
    }
    
    public String getQueryType() {
        return queryType;
    }
    
    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }
    
    public int getMaxDocuments() {
        return maxDocuments;
    }
    
    public void setMaxDocuments(int maxDocuments) {
        this.maxDocuments = maxDocuments;
    }
    
    public boolean isPreferVietnamese() {
        return preferVietnamese;
    }
    
    public void setPreferVietnamese(boolean preferVietnamese) {
        this.preferVietnamese = preferVietnamese;
    }
}
