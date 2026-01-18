package com.example.mymoney.rag;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * EmbeddingService provides text embedding capabilities for semantic search.
 * 
 * This implementation uses a hybrid approach:
 * 1. Keyword-based matching for fast retrieval
 * 2. TF-IDF weighted cosine similarity for semantic ranking
 * 
 * Note: For full ONNX-based embeddings (all-MiniLM-L6-v2), the model file 
 * (~23MB) would need to be added to assets. This implementation provides
 * a lightweight alternative that works well for the financial knowledge base.
 */
public class EmbeddingService {
    private static final String TAG = "EmbeddingService";
    
    // Vocabulary built from knowledge base
    private Map<String, Integer> vocabulary;
    private Map<String, Float> idfScores;
    private int vocabularySize;
    private boolean isInitialized = false;
    
    // Common Vietnamese stop words to filter out
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        // Vietnamese
        "và", "của", "là", "cho", "với", "có", "được", "này", "đó", "để",
        "trong", "những", "các", "một", "về", "từ", "như", "khi", "thì", "bạn",
        "không", "nếu", "nhưng", "cũng", "hoặc", "hay", "đã", "sẽ", "đang",
        // English
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
        "being", "have", "has", "had", "do", "does", "did", "will", "would",
        "could", "should", "may", "might", "must", "shall", "can", "this",
        "that", "these", "those", "it", "its", "you", "your", "we", "our",
        "they", "their", "he", "she", "him", "her", "his"
    ));
    
    private Context context;
    
    public EmbeddingService(Context context) {
        this.context = context;
        this.vocabulary = new HashMap<>();
        this.idfScores = new HashMap<>();
        this.vocabularySize = 0;
    }
    
    /**
     * Initialize the embedding service with documents from the knowledge base.
     * This builds the vocabulary and calculates IDF scores.
     */
    public void initialize(List<String> documents) {
        Log.d(TAG, "Initializing embedding service with " + documents.size() + " documents");
        
        // Build vocabulary from all documents
        Map<String, Integer> documentFrequency = new HashMap<>();
        Set<String> allTerms = new HashSet<>();
        
        for (String doc : documents) {
            Set<String> docTerms = tokenize(doc);
            allTerms.addAll(docTerms);
            
            for (String term : docTerms) {
                documentFrequency.put(term, documentFrequency.getOrDefault(term, 0) + 1);
            }
        }
        
        // Build vocabulary mapping
        int index = 0;
        for (String term : allTerms) {
            vocabulary.put(term, index++);
        }
        vocabularySize = vocabulary.size();
        
        // Calculate IDF scores
        int numDocs = documents.size();
        for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
            float idf = (float) Math.log((double) numDocs / (1 + entry.getValue()));
            idfScores.put(entry.getKey(), idf);
        }
        
        isInitialized = true;
        Log.d(TAG, "Embedding service initialized with vocabulary size: " + vocabularySize);
    }
    
    /**
     * Generate embedding vector for a text string.
     * Uses TF-IDF weighting for better semantic representation.
     */
    public float[] generateEmbedding(String text) {
        if (!isInitialized || vocabularySize == 0) {
            Log.w(TAG, "Embedding service not initialized, returning empty embedding");
            return new float[0];
        }
        
        // Tokenize and count term frequencies
        Map<String, Integer> termFrequency = new HashMap<>();
        Set<String> terms = tokenize(text);
        
        for (String term : terms) {
            termFrequency.put(term, termFrequency.getOrDefault(term, 0) + 1);
        }
        
        // Build TF-IDF vector
        float[] embedding = new float[vocabularySize];
        float norm = 0;
        
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            if (vocabulary.containsKey(term)) {
                int idx = vocabulary.get(term);
                float tf = 1 + (float) Math.log(entry.getValue()); // Log-scaled TF
                float idf = idfScores.getOrDefault(term, 1.0f);
                float tfidf = tf * idf;
                embedding[idx] = tfidf;
                norm += tfidf * tfidf;
            }
        }
        
        // L2 normalize
        if (norm > 0) {
            norm = (float) Math.sqrt(norm);
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
        }
        
        return embedding;
    }
    
    /**
     * Calculate cosine similarity between two embedding vectors.
     */
    public float cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length || vec1.length == 0) {
            return 0;
        }
        
        float dotProduct = 0;
        float norm1 = 0;
        float norm2 = 0;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        if (norm1 == 0 || norm2 == 0) {
            return 0;
        }
        
        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Calculate keyword match score for quick filtering.
     * This is faster than embedding similarity and good for initial filtering.
     */
    public float keywordMatchScore(String query, List<String> keywords) {
        Set<String> queryTerms = tokenize(query.toLowerCase());
        int matches = 0;
        
        for (String keyword : keywords) {
            Set<String> keywordTerms = tokenize(keyword.toLowerCase());
            for (String kt : keywordTerms) {
                if (queryTerms.contains(kt)) {
                    matches++;
                    break;
                }
            }
        }
        
        return keywords.isEmpty() ? 0 : (float) matches / keywords.size();
    }
    
    /**
     * Calculate hybrid score combining keyword match and semantic similarity.
     */
    public float hybridScore(String query, String documentContent, List<String> keywords, 
                            float keywordWeight, float semanticWeight) {
        float keywordScore = keywordMatchScore(query, keywords);
        
        float semanticScore = 0;
        if (isInitialized) {
            float[] queryEmb = generateEmbedding(query);
            float[] docEmb = generateEmbedding(documentContent);
            semanticScore = cosineSimilarity(queryEmb, docEmb);
        }
        
        return (keywordWeight * keywordScore) + (semanticWeight * semanticScore);
    }
    
    /**
     * Tokenize text into terms, removing stop words and normalizing.
     */
    private Set<String> tokenize(String text) {
        Set<String> terms = new HashSet<>();
        
        // Convert to lowercase and split on non-alphanumeric characters
        String normalized = text.toLowerCase(Locale.ROOT);
        String[] tokens = normalized.split("[^a-zA-Z0-9àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ]+");
        
        for (String token : tokens) {
            if (token.length() > 1 && !STOP_WORDS.contains(token)) {
                terms.add(token);
            }
        }
        
        return terms;
    }
    
    /**
     * Check if the service is initialized and ready.
     */
    public boolean isReady() {
        return isInitialized;
    }
    
    /**
     * Get the vocabulary size (dimension of embeddings).
     */
    public int getEmbeddingDimension() {
        return vocabularySize;
    }
}
