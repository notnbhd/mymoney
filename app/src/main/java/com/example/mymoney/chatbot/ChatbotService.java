package com.example.mymoney.chatbot;

import android.content.Context;
import android.util.Log;

import com.example.mymoney.BuildConfig;
import com.example.mymoney.budget.BudgetContextProvider;
import com.example.mymoney.budget.BudgetNotificationService;
import com.example.mymoney.budget.BudgetRuleEngine;
import com.example.mymoney.budget.SpendingPatternAnalyzer;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.database.entity.SavingGoal;
import com.example.mymoney.database.entity.Transaction;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ChatbotService {
    private static final String TAG = "ChatbotService";
    // OpenRouter configuration
    private static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/";
    private static final String API_TOKEN = BuildConfig.OPENROUTER_API_TOKEN;
    private static final String MODEL = "mistralai/devstral-2512:free";

    private OpenRouterApiService apiService;
    private AppDatabase database;
    private Context context;
    private BudgetContextProvider budgetContextProvider;
    private BudgetNotificationService notificationService;
    private SpendingPatternAnalyzer patternAnalyzer;
    private QueryParser queryParser;

    public ChatbotService(Context context) {
        this.context = context;
        this.database = AppDatabase.getInstance(context);
        this.budgetContextProvider = new BudgetContextProvider(context);
        this.notificationService = new BudgetNotificationService(context);
        this.patternAnalyzer = new SpendingPatternAnalyzer(context);
        this.queryParser = new QueryParser(context);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(OPENROUTER_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiService = retrofit.create(OpenRouterApiService.class);
    }

    public void generateFinancialAdvice(int userId, int walletId, String userMessage, ChatbotCallback callback) {
        Log.d(TAG, "Starting financial advice generation for user: " + userId + ", wallet: " + walletId);

        // Parse query intent and analyze data in background
        new Thread(() -> {
            try {
                // Step 1: Parse query to understand what user is asking
                QueryIntent intent = queryParser.parseQuerySync(userMessage);
                Log.d(TAG, "Parsed intent: " + intent);

                // Check if we need clarification from user
                if (intent.needsClarification() && intent.getClarificationMessage() != null) {
                    callback.onSuccess("💬 " + intent.getClarificationMessage());
                    return;
                }

                // Step 2: Fetch targeted financial data based on parsed intent
                String financialAnalysis = analyzeUserFinancialData(userId, walletId, intent);

                // Get budget analysis from rule-based engine
                BudgetRuleEngine.BudgetAnalysisResult budgetAnalysis =
                        budgetContextProvider.analyzeBudgetsSync(walletId);

                // Get spending pattern analysis
                SpendingPatternAnalyzer.PatternAnalysisResult patternResult =
                        patternAnalyzer.analyzePatterns(walletId);

                // Check for notifications based on budget status
                if (budgetAnalysis != null) {
                    notificationService.checkAndNotify(budgetAnalysis);
                }

                // Build enhanced prompt with budget context
                String budgetContext = "";
                if (budgetAnalysis != null && isBudgetRelatedQuery(userMessage)) {
                    budgetContext = BudgetContextProvider.buildPromptEnhancement(budgetAnalysis);
                }

                // Build pattern context for habit-related queries
                String patternContext = "";
                if (patternResult != null && isPatternRelatedQuery(userMessage)) {
                    patternContext = buildPatternPromptEnhancement(patternResult);
                }

                Log.d(TAG, "Financial analysis: " + financialAnalysis);

                // Step 3: Generate response using LLM
                OpenRouterRequest request = new OpenRouterRequest(MODEL);
                request.setTemperature(0.7);
                request.setMax_tokens(500);

                // System message with budget and pattern context if applicable
                String systemPrompt = "Bạn là trợ lý tài chính cá nhân chuyên nghiệp. " +
                        "Hãy đưa ra lời khuyên ngắn gọn, thực tế và hữu ích bằng tiếng Việt. " +
                        "Trả lời trong 3-4 câu, tập trung vào hành động cụ thể. " +
                        "Nếu người dùng hỏi về số tiền cụ thể, hãy trả lời con số chính xác từ dữ liệu.";

                if (!budgetContext.isEmpty()) {
                    systemPrompt += budgetContext;
                }

                if (!patternContext.isEmpty()) {
                    systemPrompt += patternContext;
                }

                request.addMessage("system", systemPrompt);

                // User message with financial data
                String userPrompt = "Dữ liệu tài chính:\n" + financialAnalysis + "\n\nCâu hỏi: " + userMessage;
                request.addMessage("user", userPrompt);

                Call<OpenRouterResponse> call = apiService.generateResponse(
                        "Bearer " + API_TOKEN,
                        "https://github.com/notnbhd/mymoney",
                        "MyMoney App",
                        request
                );

                call.enqueue(new Callback<OpenRouterResponse>() {
                    @Override
                    public void onResponse(Call<OpenRouterResponse> call, Response<OpenRouterResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "API Response successful");
                            String generatedText = response.body().getGeneratedText();

                            if (generatedText != null && !generatedText.isEmpty()) {
                                String cleanedResponse = cleanGeneratedText(generatedText);
                                callback.onSuccess(cleanedResponse);
                            } else {
                                Log.w(TAG, "Empty response from API, using local advice");
                                callback.onSuccess(generateLocalFinancialAdvice(userId, walletId, userMessage, financialAnalysis));
                            }
                        } else {
                            Log.e(TAG, "API Error: " + response.code() + " - " + response.message());
                            try {
                                String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                                Log.e(TAG, "Error body: " + errorBody);
                            } catch (Exception e) {
                                Log.e(TAG, "Error reading error body", e);
                            }
                            callback.onSuccess(generateLocalFinancialAdvice(userId, walletId, userMessage, financialAnalysis));
                        }
                    }

                    @Override
                    public void onFailure(Call<OpenRouterResponse> call, Throwable t) {
                        Log.e(TAG, "API Failure: " + t.getMessage(), t);
                        callback.onSuccess(generateLocalFinancialAdvice(userId, walletId, userMessage, financialAnalysis));
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in financial analysis", e);
                callback.onError("Đã xảy ra lỗi khi phân tích dữ liệu tài chính");
            }
        }).start();
    }

    /**
     * Analyze financial data based on parsed query intent.
     * Fetches data for the specific time range and category requested.
     */
    private String analyzeUserFinancialData(int userId, int walletId, QueryIntent intent) {
        StringBuilder analysis = new StringBuilder();

        long startTimestamp = intent.getTimeRangeStart();
        long endTimestamp = intent.getTimeRangeEnd();

        // Debug: Log timestamps in human-readable format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        Log.d(TAG, "Query time range: " + sdf.format(new Date(startTimestamp)) + " to " + sdf.format(new Date(endTimestamp)));
        Log.d(TAG, "Query timestamps: start=" + startTimestamp + ", end=" + endTimestamp);

        // Build time period description
        String periodDescription = buildPeriodDescription(intent);

        // Get transactions for the specified time range
        List<Transaction> transactions = database.transactionDao()
                .getTransactionsByWalletAndDateRange(walletId, startTimestamp, endTimestamp);
        
        Log.d(TAG, "Found " + transactions.size() + " transactions in date range for wallet " + walletId);
        
        // Debug: Show all unique category IDs in the found transactions
        StringBuilder categoryDebug = new StringBuilder("Categories in transactions: ");
        java.util.Set<Integer> uniqueCatIds = new java.util.HashSet<>();
        for (Transaction t : transactions) {
            uniqueCatIds.add(t.getCategoryId());
        }
        for (Integer catId : uniqueCatIds) {
            Category cat = database.categoryDao().getCategoryById(catId);
            categoryDebug.append(catId).append("=").append(cat != null ? cat.getName() : "null").append(", ");
        }
        Log.d(TAG, categoryDebug.toString());

        // Filter by category if specified - ALWAYS use category name to avoid ID mismatch issues
        if (intent.getCategoryName() != null && !intent.getCategoryName().isEmpty()) {
            String categoryName = intent.getCategoryName();
            int beforeCount = transactions.size();
            Log.d(TAG, "Filtering by categoryName: " + categoryName);
            transactions.removeIf(t -> {
                Category cat = database.categoryDao().getCategoryById(t.getCategoryId());
                return cat == null || !cat.getName().equalsIgnoreCase(categoryName);
            });
            Log.d(TAG, "After category filter: " + transactions.size() + " transactions (was " + beforeCount + ")");
        }

        // Calculate totals
        double totalExpenses = 0;
        double totalIncome = 0;
        Map<Integer, Double> categoryExpenses = new HashMap<>();
        Map<Integer, Double> categoryIncomes = new HashMap<>();

        for (Transaction transaction : transactions) {
            if ("expense".equals(transaction.getType())) {
                totalExpenses += transaction.getAmount();
                categoryExpenses.put(
                        transaction.getCategoryId(),
                        categoryExpenses.getOrDefault(transaction.getCategoryId(), 0.0) + transaction.getAmount()
                );
            } else if ("income".equals(transaction.getType())) {
                totalIncome += transaction.getAmount();
                categoryIncomes.put(
                        transaction.getCategoryId(),
                        categoryIncomes.getOrDefault(transaction.getCategoryId(), 0.0) + transaction.getAmount()
                );
            }
        }

        // Build analysis based on query type
        analysis.append("📊 ").append(periodDescription).append(" (Ví hiện tại):\n");

        if (intent.getCategoryName() != null) {
            // Category-specific query
            analysis.append(String.format("Chi tiêu cho %s: %.0f VNĐ\n", intent.getCategoryName(), totalExpenses));
            analysis.append(String.format("Số giao dịch: %d\n", transactions.size()));
        } else {
            // General query
            analysis.append(String.format("Thu nhập: %.0f VNĐ\n", totalIncome));
            analysis.append(String.format("Chi tiêu: %.0f VNĐ\n", totalExpenses));
            analysis.append(String.format("Tiết kiệm: %.0f VNĐ\n", totalIncome - totalExpenses));

            // Top spending categories
            if (!categoryExpenses.isEmpty()) {
                analysis.append("\n💰 Chi tiêu theo danh mục:\n");
                categoryExpenses.entrySet().stream()
                        .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                        .limit(5)
                        .forEach(entry -> {
                            Category category = database.categoryDao().getCategoryById(entry.getKey());
                            if (category != null) {
                                analysis.append(String.format("- %s: %.0f VNĐ\n",
                                        category.getName(), entry.getValue()));
                            }
                        });
            }
        }

        return analysis.toString();
    }

    /**
     * Build human-readable description of the time period.
     */
    private String buildPeriodDescription(QueryIntent intent) {
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", new Locale("vi", "VN"));
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.getDefault());
        Calendar calendar = Calendar.getInstance();

        switch (intent.getTimeRangeType()) {
            case CURRENT_MONTH:
                return "Tháng này";
            case SPECIFIC_MONTH:
                calendar.set(Calendar.YEAR, intent.getYear());
                calendar.set(Calendar.MONTH, intent.getMonth() - 1);
                return "Tháng " + intent.getMonth() + "/" + intent.getYear();
            case YEAR:
                return "Năm " + intent.getYear();
            case LAST_N_DAYS:
                return intent.getDays() + " ngày qua";
            case ALL_TIME:
                return "Toàn bộ thời gian";
            default:
                return "Tháng này";
        }
    }

    /**
     * Legacy method for backward compatibility - uses current month.
     */
    private String analyzeUserFinancialData(int userId, int walletId) {
        QueryIntent defaultIntent = new QueryIntent();
        defaultIntent.setTimeRangeType(QueryIntent.TimeRangeType.CURRENT_MONTH);
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        defaultIntent.setTimeRangeStart(calendar.getTimeInMillis());
        defaultIntent.setTimeRangeEnd(System.currentTimeMillis());
        
        return analyzeUserFinancialData(userId, walletId, defaultIntent);
    }

    private String cleanGeneratedText(String generatedText) {
        if (generatedText == null) return "";

        // OpenRouter/DeepSeek returns clean text, just trim
        return generatedText.trim();
    }

    private String generateLocalFinancialAdvice(int userId, int walletId, String userMessage, String financialAnalysis) {
        StringBuilder advice = new StringBuilder();

        advice.append(financialAnalysis);
        advice.append("\n\n💡 Lời khuyên:\n");

        String lowerMessage = userMessage.toLowerCase();

        if (lowerMessage.contains("chi tiêu") || lowerMessage.contains("tiêu")) {
            advice.append("• Theo dõi chi tiêu hàng ngày để kiểm soát tốt hơn\n");
            advice.append("• Ưu tiên các khoản chi tiêu cần thiết\n");
            advice.append("• Cân nhắc giảm chi tiêu không cần thiết");
        } else if (lowerMessage.contains("tiết kiệm") || lowerMessage.contains("save")) {
            advice.append("• Đặt mục tiêu tiết kiệm cụ thể và khả thi\n");
            advice.append("• Tự động chuyển tiền tiết kiệm mỗi tháng\n");
            advice.append("• Áp dụng quy tắc 50/30/20: 50% nhu cầu, 30% mong muốn, 20% tiết kiệm");
        } else if (lowerMessage.contains("thu nhập") || lowerMessage.contains("income")) {
            advice.append("• Đa dạng hóa nguồn thu nhập nếu có thể\n");
            advice.append("• Đầu tư vào kỹ năng để tăng thu nhập\n");
            advice.append("• Cân bằng giữa thu nhập và chi tiêu");
        } else {
            advice.append("• Theo dõi tài chính đều đặn để có cái nhìn tổng quan\n");
            advice.append("• Cân bằng giữa chi tiêu và tiết kiệm\n");
            advice.append("• Đặt mục tiêu tài chính rõ ràng và đo lường được");
        }

        return advice.toString();
    }

    /**
     * Check if user message is related to budget
     */
    private boolean isBudgetRelatedQuery(String message) {
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("ngân sách") ||
                lowerMessage.contains("budget") ||
                lowerMessage.contains("chi tiêu") ||
                lowerMessage.contains("spending") ||
                lowerMessage.contains("tiền") ||
                lowerMessage.contains("money") ||
                lowerMessage.contains("tiết kiệm") ||
                lowerMessage.contains("save") ||
                lowerMessage.contains("giới hạn") ||
                lowerMessage.contains("limit") ||
                lowerMessage.contains("còn bao nhiêu") ||
                lowerMessage.contains("how much") ||
                lowerMessage.contains("đề xuất") ||
                lowerMessage.contains("recommend") ||
                lowerMessage.contains("lời khuyên") ||
                lowerMessage.contains("advice");
    }

    /**
     * Get quick budget recommendation without LLM
     */
    public void getQuickBudgetRecommendation(int walletId, ChatbotCallback callback) {
        new Thread(() -> {
            try {
                BudgetRuleEngine.BudgetAnalysisResult result =
                        budgetContextProvider.analyzeBudgetsSync(walletId);

                if (result == null) {
                    callback.onSuccess("Bạn chưa thiết lập ngân sách nào. " +
                            "Hãy tạo ngân sách để tôi có thể đưa ra lời khuyên chi tiêu!");
                    return;
                }

                String quickResponse = BudgetRuleEngine.generateQuickResponse(result);
                callback.onSuccess(quickResponse);

                // Also check for notifications
                notificationService.checkAndNotify(result);

            } catch (Exception e) {
                callback.onError("Không thể phân tích ngân sách: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Get spending pattern analysis
     */
    public void getSpendingPatternAnalysis(int walletId, ChatbotCallback callback) {
        new Thread(() -> {
            try {
                SpendingPatternAnalyzer.PatternAnalysisResult result =
                        patternAnalyzer.analyzePatterns(walletId);

                if (result == null || result.regularHabits.isEmpty()) {
                    callback.onSuccess("📊 Chưa đủ dữ liệu để phân tích thói quen chi tiêu. " +
                            "Hãy tiếp tục ghi chép chi tiêu để tôi có thể đưa ra đề xuất phù hợp!");
                    return;
                }

                StringBuilder response = new StringBuilder();
                response.append("📊 **Phân tích thói quen chi tiêu của bạn:**\n\n");

                // Add regular habits
                if (!result.regularHabits.isEmpty()) {
                    response.append("🔄 **Thói quen chi tiêu thường xuyên:**\n");
                    for (SpendingPatternAnalyzer.SpendingHabit habit : result.regularHabits) {
                        response.append(String.format("• %s: ~%.0f VNĐ/%s\n",
                                habit.categoryName, habit.averageAmount, habit.pattern));
                    }
                    response.append("\n");
                }

                // Add missing purchases
                if (!result.missingPurchases.isEmpty()) {
                    response.append("💡 **Có thể bạn quên chi tiêu:**\n");
                    for (SpendingPatternAnalyzer.MissingPurchase missing : result.missingPurchases) {
                        response.append(String.format("• %s (thường ~%.0f VNĐ)\n",
                                missing.categoryName, missing.usualAmount));
                    }
                    response.append("\n");
                }

                // Add recommendations
                if (!result.recommendations.isEmpty()) {
                    response.append("💰 **Đề xuất:**\n");
                    for (SpendingPatternAnalyzer.SmartRecommendation rec : result.recommendations) {
                        String emoji = getRecommendationEmoji(rec.type);
                        response.append(emoji).append(" ").append(rec.title).append("\n");
                        response.append("   ").append(rec.actionableAdvice).append("\n");
                    }
                }

                callback.onSuccess(response.toString());

            } catch (Exception e) {
                Log.e(TAG, "Error analyzing spending patterns", e);
                callback.onError("Không thể phân tích thói quen chi tiêu: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Check if user message is related to spending patterns/habits
     */
    private boolean isPatternRelatedQuery(String message) {
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("thói quen") ||
                lowerMessage.contains("pattern") ||
                lowerMessage.contains("habit") ||
                lowerMessage.contains("thường xuyên") ||
                lowerMessage.contains("frequently") ||
                lowerMessage.contains("hay mua") ||
                lowerMessage.contains("often buy") ||
                lowerMessage.contains("tháng trước") ||
                lowerMessage.contains("last month") ||
                lowerMessage.contains("quần áo") ||
                lowerMessage.contains("clothes") ||
                lowerMessage.contains("định kỳ") ||
                lowerMessage.contains("recurring") ||
                lowerMessage.contains("phân tích") ||
                lowerMessage.contains("analyze") ||
                lowerMessage.contains("lịch sử") ||
                lowerMessage.contains("history") ||
                lowerMessage.contains("xu hướng") ||
                lowerMessage.contains("trend") ||
                lowerMessage.contains("nên mua") ||
                lowerMessage.contains("should buy") ||
                lowerMessage.contains("đề xuất") ||
                lowerMessage.contains("suggest");
    }

    /**
     * Build prompt enhancement from spending patterns
     */
    private String buildPatternPromptEnhancement(SpendingPatternAnalyzer.PatternAnalysisResult result) {
        if (result == null || result.regularHabits.isEmpty()) {
            return "";
        }

        StringBuilder enhancement = new StringBuilder();
        enhancement.append("\n\n[THÓI QUEN CHI TIÊU CỦA NGƯỜI DÙNG]\n");

        // Add detected habits
        for (SpendingPatternAnalyzer.SpendingHabit habit : result.regularHabits) {
            if (habit.frequency >= 0.6) { // Only include high-frequency habits
                enhancement.append(String.format("- %s: Chi tiêu %s, TB %.0f VNĐ (tần suất: %.0f%%)\n",
                        habit.categoryName,
                        habit.pattern,
                        habit.averageAmount,
                        habit.frequency * 100));
            }
        }

        // Add missing purchases
        if (!result.missingPurchases.isEmpty()) {
            enhancement.append("\n[CHI TIÊU BỊ BỎ LỠ THÁNG NÀY]\n");
            for (SpendingPatternAnalyzer.MissingPurchase missing : result.missingPurchases) {
                enhancement.append(String.format("- %s (thường ~%.0f VNĐ)\n",
                        missing.categoryName, missing.usualAmount));
            }
        }

        // Add LLM summary if available
        if (result.summaryForLLM != null && !result.summaryForLLM.isEmpty()) {
            enhancement.append("\n").append(result.summaryForLLM);
        }

        enhancement.append("\nDựa trên thói quen này, hãy đưa ra lời khuyên cụ thể về chi tiêu.");

        return enhancement.toString();
    }

    /**
     * Get emoji for recommendation type
     */
    private String getRecommendationEmoji(String type) {
        switch (type) {
            case "spend": return "🛒";
            case "save": return "💰";
            case "warning": return "⚠️";
            case "celebrate": return "🎉";
            default: return "💡";
        }
    }

    public interface ChatbotCallback {
        void onSuccess(String response);
        void onError(String error);
    }
}