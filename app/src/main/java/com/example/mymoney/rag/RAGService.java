package com.example.mymoney.rag;

import android.content.Context;
import android.util.Log;

import com.example.mymoney.chatbot.QueryIntent;
import com.example.mymoney.database.AppDatabase;

import java.util.List;

/**
 * RAGService orchestrates the complete RAG pipeline.
 * Combines knowledge retrieval with financial context to create enhanced prompts.
 */
public class RAGService {
    private static final String TAG = "RAGService";
    
    private Context context;
    private AppDatabase database;
    private FinancialKnowledgeBase knowledgeBase;
    private FinancialContextBuilder contextBuilder;
    
    // Configuration
    private static final int DEFAULT_TOP_K = 3;
    private boolean isInitialized = false;
    
    public RAGService(Context context, AppDatabase database) {
        this.context = context;
        this.database = database;
        this.knowledgeBase = new FinancialKnowledgeBase(context);
        this.contextBuilder = new FinancialContextBuilder(database);
    }
    
    /**
     * Initialize the RAG service.
     * Should be called once, preferably at app startup.
     */
    public void initialize() {
        if (isInitialized) return;
        
        try {
            Log.d(TAG, "Initializing RAG service...");
            knowledgeBase.initialize();
            isInitialized = true;
            Log.d(TAG, "RAG service initialized with " + 
                    knowledgeBase.getDocumentCount() + " knowledge documents");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize RAG service", e);
        }
    }
    
    /**
     * Main RAG pipeline: retrieve relevant knowledge and build context.
     */
    public RAGContext retrieveContext(int userId, int walletId, String query, QueryIntent intent) {
        if (!isInitialized) {
            Log.w(TAG, "RAG service not initialized, initializing now...");
            initialize();
        }
        
        // Build financial context from user's data
        RAGContext ragContext = contextBuilder.buildContext(userId, walletId, query);
        ragContext.setOriginalQuery(query);
        
        // Determine query type and category from intent
        if (intent != null) {
            ragContext.setQueryType(intent.getQueryType() != null ? 
                    intent.getQueryType().toString() : "GENERAL");
            ragContext.setDetectedCategory(intent.getCategoryName());
        }
        
        // Retrieve relevant knowledge documents
        List<KnowledgeDocument> relevantDocs;
        
        String categoryName = ragContext.getDetectedCategory();
        if (categoryName != null && !categoryName.isEmpty()) {
            // Category-specific retrieval
            relevantDocs = knowledgeBase.retrieveByCategory(query, categoryName, DEFAULT_TOP_K);
            Log.d(TAG, "Category-specific retrieval for: " + categoryName);
        } else {
            // General retrieval based on query
            relevantDocs = knowledgeBase.retrieveRelevant(query, DEFAULT_TOP_K);
        }
        
        // Add documents to context
        for (KnowledgeDocument doc : relevantDocs) {
            ragContext.addDocument(doc);
        }
        
        // If query is about specific category, add category context
        if (categoryName != null && !categoryName.isEmpty()) {
            String categoryContext = contextBuilder.buildCategoryContext(userId, walletId, categoryName);
            if (!categoryContext.isEmpty()) {
                ragContext.setFinancialSummary(
                        ragContext.getFinancialSummary() + "\n" + categoryContext);
            }
        }
        
        Log.d(TAG, "RAG context built: " + ragContext.getSummary());
        return ragContext;
    }
    
    /**
     * Simplified retrieval when QueryIntent is not available.
     */
    public RAGContext retrieveContext(int userId, int walletId, String query) {
        return retrieveContext(userId, walletId, query, null);
    }
    
    /**
     * Get just the knowledge documents without financial context.
     * Useful for quick tips or FAQ-style queries.
     */
    public List<KnowledgeDocument> retrieveKnowledge(String query, int topK) {
        if (!isInitialized) {
            initialize();
        }
        return knowledgeBase.retrieveRelevant(query, topK);
    }
    
    /**
     * Check if service is ready.
     */
    public boolean isReady() {
        return isInitialized && knowledgeBase.isReady();
    }
    
    /**
     * Get knowledge base stats.
     */
    public String getStats() {
        return String.format("RAGService: initialized=%b, documents=%d, categories=%d",
                isInitialized,
                knowledgeBase.getDocumentCount(),
                knowledgeBase.getCategories().size());
    }
    
    /**
     * Build the enhanced system prompt for the LLM.
     */
    public String buildSystemPrompt(RAGContext context) {
        StringBuilder systemPrompt = new StringBuilder();
        
        systemPrompt.append("Bạn là trợ lý tài chính cá nhân thông minh của ứng dụng MyMoney. ");
        systemPrompt.append("Hãy đưa ra lời khuyên dựa trên dữ liệu thực tế của người dùng và kiến thức tài chính bên dưới.\n\n");
        
        // Add knowledge context to system prompt
        if (!context.getRelevantDocuments().isEmpty()) {
            systemPrompt.append("[KIẾN THỨC TÀI CHÍNH]\n");
            for (KnowledgeDocument doc : context.getRelevantDocuments()) {
                systemPrompt.append("• ").append(doc.getTopic()).append(": ");
                systemPrompt.append(doc.getContent(context.isPreferVietnamese())).append("\n");
            }
            systemPrompt.append("\n");
        }
        
        systemPrompt.append("Quy tắc:\n");
        systemPrompt.append("1. Trả lời ngắn gọn (3-5 câu), tập trung vào hành động cụ thể\n");
        systemPrompt.append("2. Sử dụng số liệu thực từ dữ liệu người dùng khi được hỏi\n");
        systemPrompt.append("3. Áp dụng kiến thức tài chính để đưa ra lời khuyên phù hợp\n");
        systemPrompt.append("4. Nếu không có đủ dữ liệu, hãy đưa ra lời khuyên chung\n");
        
        return systemPrompt.toString();
    }
    
    /**
     * Build the user prompt with financial context.
     */
    public String buildUserPrompt(RAGContext context, String userQuery) {
        StringBuilder userPrompt = new StringBuilder();
        
        // Add financial data
        if (context.getFinancialSummary() != null && !context.getFinancialSummary().isEmpty()) {
            userPrompt.append("[DỮ LIỆU TÀI CHÍNH]\n");
            userPrompt.append(context.getFinancialSummary()).append("\n");
        }
        
        // Add budget context
        if (context.getBudgetContext() != null && !context.getBudgetContext().isEmpty()) {
            userPrompt.append(context.getBudgetContext()).append("\n");
        }
        
        // Add comparison and trend
        if (context.getComparisonContext() != null && !context.getComparisonContext().isEmpty()) {
            userPrompt.append(context.getComparisonContext()).append("\n");
        }
        
        if (context.getTrendContext() != null && !context.getTrendContext().isEmpty()) {
            userPrompt.append(context.getTrendContext()).append("\n");
        }
        
        // Add the actual question
        userPrompt.append("[CÂU HỎI]\n");
        userPrompt.append(userQuery);
        
        return userPrompt.toString();
    }
}
