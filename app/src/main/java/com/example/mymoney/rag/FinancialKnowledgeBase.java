package com.example.mymoney.rag;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * FinancialKnowledgeBase manages the knowledge base and provides retrieval methods.
 * Loads knowledge from JSON file in assets and supports hybrid search.
 */
public class FinancialKnowledgeBase {
    private static final String TAG = "FinancialKnowledgeBase";
    private static final String KNOWLEDGE_FILE = "knowledge/financial_knowledge_base.json";
    
    private Context context;
    private EmbeddingService embeddingService;
    private List<KnowledgeDocument> documents;
    private boolean isInitialized = false;
    
    // Search configuration
    private static final float KEYWORD_WEIGHT = 0.6f;
    private static final float SEMANTIC_WEIGHT = 0.4f;
    private static final int DEFAULT_TOP_K = 5;
    
    public FinancialKnowledgeBase(Context context) {
        this.context = context;
        this.embeddingService = new EmbeddingService(context);
        this.documents = new ArrayList<>();
    }
    
    /**
     * Initialize the knowledge base by loading from JSON file.
     * Should be called once at startup.
     */
    public void initialize() {
        if (isInitialized) return;
        
        try {
            Log.d(TAG, "Loading knowledge base from assets...");
            loadKnowledgeFromAssets();
            
            // Initialize embedding service with document contents
            List<String> allContents = new ArrayList<>();
            for (KnowledgeDocument doc : documents) {
                allContents.add(doc.getCombinedContent());
            }
            embeddingService.initialize(allContents);
            
            // Pre-compute embeddings for all documents
            for (KnowledgeDocument doc : documents) {
                float[] embedding = embeddingService.generateEmbedding(doc.getCombinedContent());
                doc.setEmbedding(embedding);
            }
            
            isInitialized = true;
            Log.d(TAG, "Knowledge base initialized with " + documents.size() + " documents");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize knowledge base", e);
        }
    }
    
    /**
     * Load knowledge documents from the JSON file in assets.
     */
    private void loadKnowledgeFromAssets() {
        try {
            InputStream is = context.getAssets().open(KNOWLEDGE_FILE);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            
            parseKnowledgeJson(sb.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load knowledge file: " + KNOWLEDGE_FILE, e);
        }
    }
    
    /**
     * Parse the JSON knowledge base into KnowledgeDocument objects.
     */
    private void parseKnowledgeJson(String jsonString) {
        Gson gson = new Gson();
        JsonObject root = gson.fromJson(jsonString, JsonObject.class);
        
        // Parse each category in the JSON
        for (String category : root.keySet()) {
            JsonElement element = root.get(category);
            
            // Skip metadata fields
            if (!element.isJsonArray()) continue;
            
            JsonArray items = element.getAsJsonArray();
            for (JsonElement itemElement : items) {
                try {
                    JsonObject item = itemElement.getAsJsonObject();
                    KnowledgeDocument doc = parseDocument(item, category);
                    if (doc != null) {
                        documents.add(doc);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse document in category: " + category, e);
                }
            }
        }
        
        Log.d(TAG, "Parsed " + documents.size() + " documents from knowledge base");
    }
    
    /**
     * Parse a single document from JSON.
     */
    private KnowledgeDocument parseDocument(JsonObject item, String category) {
        String id = item.has("id") ? item.get("id").getAsString() : "";
        String topic = item.has("topic") ? item.get("topic").getAsString() : "";
        String contentEn = item.has("content_en") ? item.get("content_en").getAsString() : "";
        String contentVi = item.has("content_vi") ? item.get("content_vi").getAsString() : "";
        
        List<String> keywords = new ArrayList<>();
        if (item.has("keywords") && item.get("keywords").isJsonArray()) {
            JsonArray keywordArray = item.getAsJsonArray("keywords");
            for (JsonElement kw : keywordArray) {
                keywords.add(kw.getAsString());
            }
        }
        
        return new KnowledgeDocument(id, topic, category, contentEn, contentVi, keywords);
    }
    
    /**
     * Retrieve relevant documents using hybrid search (keyword + semantic).
     */
    public List<KnowledgeDocument> retrieveRelevant(String query, int topK) {
        if (!isInitialized || documents.isEmpty()) {
            Log.w(TAG, "Knowledge base not initialized");
            return new ArrayList<>();
        }
        
        // Generate query embedding
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        
        // Calculate relevance scores for all documents
        for (KnowledgeDocument doc : documents) {
            float keywordScore = embeddingService.keywordMatchScore(query, doc.getKeywords());
            
            float semanticScore = 0;
            if (doc.getEmbedding() != null && queryEmbedding.length > 0) {
                semanticScore = embeddingService.cosineSimilarity(queryEmbedding, doc.getEmbedding());
            }
            
            float hybridScore = (KEYWORD_WEIGHT * keywordScore) + (SEMANTIC_WEIGHT * semanticScore);
            doc.setRelevanceScore(hybridScore);
        }
        
        // Sort by relevance score
        List<KnowledgeDocument> sorted = new ArrayList<>(documents);
        Collections.sort(sorted, (d1, d2) -> Double.compare(d2.getRelevanceScore(), d1.getRelevanceScore()));
        
        // Return top K with non-zero scores
        List<KnowledgeDocument> results = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
            if (sorted.get(i).getRelevanceScore() > 0) {
                results.add(sorted.get(i));
            }
        }
        
        Log.d(TAG, "Retrieved " + results.size() + " relevant documents for query: " + 
              query.substring(0, Math.min(50, query.length())) + "...");
        return results;
    }
    
    /**
     * Retrieve documents filtered by category.
     */
    public List<KnowledgeDocument> retrieveByCategory(String query, String category, int topK) {
        List<KnowledgeDocument> categoryDocs = new ArrayList<>();
        
        // Normalize category name for matching
        String normalizedCategory = normalizeCategory(category);
        
        for (KnowledgeDocument doc : documents) {
            if (doc.getCategory().toLowerCase().contains(normalizedCategory)) {
                categoryDocs.add(doc);
            }
        }
        
        if (categoryDocs.isEmpty()) {
            return retrieveRelevant(query, topK);
        }
        
        // Score and sort within category
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        for (KnowledgeDocument doc : categoryDocs) {
            float keywordScore = embeddingService.keywordMatchScore(query, doc.getKeywords());
            float semanticScore = 0;
            if (doc.getEmbedding() != null && queryEmbedding.length > 0) {
                semanticScore = embeddingService.cosineSimilarity(queryEmbedding, doc.getEmbedding());
            }
            doc.setRelevanceScore((KEYWORD_WEIGHT * keywordScore) + (SEMANTIC_WEIGHT * semanticScore));
        }
        
        Collections.sort(categoryDocs, (d1, d2) -> Double.compare(d2.getRelevanceScore(), d1.getRelevanceScore()));
        
        return categoryDocs.subList(0, Math.min(topK, categoryDocs.size()));
    }
    
    /**
     * Normalize category name to match JSON category keys.
     */
    private String normalizeCategory(String category) {
        if (category == null) return "";
        
        String lower = category.toLowerCase().trim();
        
        // Map app category names to JSON category keys
        if (lower.contains("food") || lower.contains("thực phẩm") || lower.contains("ăn")) {
            return "food";
        } else if (lower.contains("transport") || lower.contains("đi lại") || lower.contains("xe")) {
            return "transport";
        } else if (lower.contains("entertainment") || lower.contains("giải trí")) {
            return "entertainment";
        } else if (lower.contains("medical") || lower.contains("y tế") || lower.contains("sức khỏe")) {
            return "medical";
        } else if (lower.contains("education") || lower.contains("giáo dục") || lower.contains("học")) {
            return "education";
        } else if (lower.contains("clothing") || lower.contains("quần áo")) {
            return "clothing";
        } else if (lower.contains("home") || lower.contains("nhà")) {
            return "home";
        } else if (lower.contains("gym") || lower.contains("fitness") || lower.contains("thể dục")) {
            return "gym";
        } else if (lower.contains("beauty") || lower.contains("làm đẹp")) {
            return "beauty";
        } else if (lower.contains("child") || lower.contains("trẻ em")) {
            return "childcare";
        } else if (lower.contains("grocer") || lower.contains("đi chợ") || lower.contains("tạp hóa")) {
            return "groceries";
        } else if (lower.contains("budget") || lower.contains("ngân sách")) {
            return "budgeting";
        } else if (lower.contains("sav") || lower.contains("tiết kiệm")) {
            return "saving";
        } else if (lower.contains("debt") || lower.contains("nợ")) {
            return "debt";
        } else if (lower.contains("invest") || lower.contains("đầu tư")) {
            return "investing";
        }
        
        return lower;
    }
    
    /**
     * Get default (non-targeted) retrieval.
     */
    public List<KnowledgeDocument> retrieveRelevant(String query) {
        return retrieveRelevant(query, DEFAULT_TOP_K);
    }
    
    /**
     * Check if the knowledge base is ready.
     */
    public boolean isReady() {
        return isInitialized;
    }
    
    /**
     * Get total document count.
     */
    public int getDocumentCount() {
        return documents.size();
    }
    
    /**
     * Get all unique categories.
     */
    public List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        for (KnowledgeDocument doc : documents) {
            if (!categories.contains(doc.getCategory())) {
                categories.add(doc.getCategory());
            }
        }
        return categories;
    }
}
