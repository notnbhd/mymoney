package com.example.mymoney.budget;

import android.content.Context;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.CategoryDao;
import com.example.mymoney.database.dao.TransactionDao;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.database.entity.Transaction;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes user spending patterns over time to provide intelligent recommendations.
 * Detects habits, missing regular purchases, unusual spending, and opportunities.
 */
public class SpendingPatternAnalyzer {

    private static final int MONTHS_TO_ANALYZE = 6; // Look back 6 months for patterns
    private static final double REGULAR_THRESHOLD = 0.6; // 60% occurrence = regular habit
    private static final double UNUSUAL_SPIKE_THRESHOLD = 1.5; // 50% above average = unusual
    private static final double UNUSUAL_DROP_THRESHOLD = 0.5; // 50% below average = unusual

    private final Context context;
    private final TransactionDao transactionDao;
    private final CategoryDao categoryDao;

    /**
     * Map English category names to Vietnamese
     */
    private static String getCategoryVietnamese(String englishName) {
        if (englishName == null) return "Khác";
        
        switch (englishName.toLowerCase()) {
            case "food":
                return "Ăn uống";
            case "home":
                return "Nhà cửa";
            case "transport":
                return "Di chuyển";
            case "entertainment":
                return "Giải trí";
            case "medical":
                return "Y tế";
            case "beauty":
                return "Làm đẹp";
            case "clothing":
                return "Quần áo";
            case "education":
                return "Giáo dục";
            case "groceries":
                return "Tạp hóa";
            case "salary":
                return "Lương";
            case "business":
                return "Kinh doanh";
            case "gifts":
                return "Quà tặng";
            case "others":
                return "Khác";
            case "shopping":
                return "Mua sắm";
            case "travel":
                return "Du lịch";
            case "utilities":
                return "Tiện ích";
            case "insurance":
                return "Bảo hiểm";
            case "investment":
                return "Đầu tư";
            default:
                return englishName; // Return original if no mapping
        }
    }

    public SpendingPatternAnalyzer(Context context) {
        this.context = context;
        AppDatabase db = AppDatabase.getInstance(context);
        this.transactionDao = db.transactionDao();
        this.categoryDao = db.categoryDao();
    }

    /**
     * Complete spending pattern analysis result
     */
    public static class PatternAnalysisResult {
        public List<SpendingHabit> regularHabits;
        public List<MissingPurchase> missingPurchases;
        public List<UnusualSpending> unusualSpendings;
        public List<SavingOpportunity> savingOpportunities;
        public List<SmartRecommendation> recommendations;
        public MonthlyComparison monthlyComparison;
        public String summaryForLLM;

        public PatternAnalysisResult() {
            regularHabits = new ArrayList<>();
            missingPurchases = new ArrayList<>();
            unusualSpendings = new ArrayList<>();
            savingOpportunities = new ArrayList<>();
            recommendations = new ArrayList<>();
        }
    }

    /**
     * Detected regular spending habit
     */
    public static class SpendingHabit {
        public String categoryName;
        public int categoryId;
        public double averageAmount;
        public int occurrenceCount; // How many months this appears
        public double frequency; // Percentage of months with this spending
        public String pattern; // "monthly", "weekly", "occasional"
        public boolean isEssential; // Food, transport, etc.

        public SpendingHabit(String categoryName, int categoryId, double averageAmount,
                            int occurrenceCount, int totalMonths, boolean isEssential) {
            this.categoryName = getCategoryVietnamese(categoryName);
            this.categoryId = categoryId;
            this.averageAmount = averageAmount;
            this.occurrenceCount = occurrenceCount;
            this.frequency = (double) occurrenceCount / totalMonths;
            this.pattern = frequency >= 0.8 ? "hàng tháng" : (frequency >= 0.5 ? "thường xuyên" : "thỉnh thoảng");
            this.isEssential = isEssential;
        }
    }

    /**
     * Regular purchase that's missing this period
     */
    public static class MissingPurchase {
        public String categoryName;
        public int categoryId;
        public double usualAmount;
        public int consecutiveMonths; // How many consecutive months they usually buy
        public String lastPurchaseDate;
        public String suggestion;

        public MissingPurchase(String categoryName, int categoryId, double usualAmount,
                              int consecutiveMonths, String lastPurchaseDate) {
            this.categoryName = getCategoryVietnamese(categoryName);
            this.categoryId = categoryId;
            this.usualAmount = usualAmount;
            this.consecutiveMonths = consecutiveMonths;
            this.lastPurchaseDate = lastPurchaseDate;
            this.suggestion = String.format("Bạn thường chi %.0f VNĐ cho %s mỗi tháng. " +
                    "Tháng này bạn chưa có khoản chi này.", usualAmount, this.categoryName);
        }
    }

    /**
     * Unusual spending detected (spike or drop)
     */
    public static class UnusualSpending {
        public String categoryName;
        public int categoryId;
        public double currentAmount;
        public double averageAmount;
        public double percentageChange;
        public String type; // "spike" or "drop"
        public String analysis;

        public UnusualSpending(String categoryName, int categoryId, double currentAmount,
                              double averageAmount, String type) {
            this.categoryName = getCategoryVietnamese(categoryName);
            this.categoryId = categoryId;
            this.currentAmount = currentAmount;
            this.averageAmount = averageAmount;
            this.percentageChange = ((currentAmount - averageAmount) / averageAmount) * 100;
            this.type = type;

            if (type.equals("spike")) {
                this.analysis = String.format("Chi tiêu %s tăng %.0f%% so với mức thông thường (%.0f so với %.0f VNĐ)",
                        this.categoryName, percentageChange, currentAmount, averageAmount);
            } else {
                this.analysis = String.format("Chi tiêu %s giảm %.0f%% so với mức thông thường (%.0f so với %.0f VNĐ)",
                        this.categoryName, Math.abs(percentageChange), currentAmount, averageAmount);
            }
        }
    }

    /**
     * Identified saving opportunity
     */
    public static class SavingOpportunity {
        public String categoryName;
        public double potentialSaving;
        public String reason;
        public String suggestion;

        public SavingOpportunity(String categoryName, double potentialSaving, 
                                String reason, String suggestion) {
            this.categoryName = getCategoryVietnamese(categoryName);
            this.potentialSaving = potentialSaving;
            this.reason = reason;
            this.suggestion = suggestion;
        }
    }

    /**
     * Smart recommendation based on analysis
     */
    public static class SmartRecommendation {
        public String type; // "spend", "save", "warning", "celebrate"
        public String priority; // "high", "medium", "low"
        public String title;
        public String description;
        public String actionableAdvice;
        public String categoryName;
        public double amount;

        public SmartRecommendation(String type, String priority, String title,
                                   String description, String actionableAdvice,
                                   String categoryName, double amount) {
            this.type = type;
            this.priority = priority;
            this.title = title;
            this.description = description;
            this.actionableAdvice = actionableAdvice;
            this.categoryName = categoryName != null ? getCategoryVietnamese(categoryName) : null;
            this.amount = amount;
        }
    }

    /**
     * Monthly spending comparison
     */
    public static class MonthlyComparison {
        public double thisMonthTotal;
        public double lastMonthTotal;
        public double averageMonthly;
        public double percentageVsLastMonth;
        public double percentageVsAverage;
        public String trend; // "increasing", "decreasing", "stable"
    }

    /**
     * Analyze spending patterns for a wallet
     */
    public PatternAnalysisResult analyzePatterns(int walletId) {
        PatternAnalysisResult result = new PatternAnalysisResult();

        // Get historical data
        Map<Integer, List<MonthlySpending>> categoryHistory = getMonthlySpendingHistory(walletId);
        Map<Integer, Double> currentMonthSpending = getCurrentMonthSpending(walletId);

        // Detect regular habits
        result.regularHabits = detectRegularHabits(categoryHistory);

        // Find missing regular purchases
        result.missingPurchases = findMissingPurchases(categoryHistory, currentMonthSpending);

        // Detect unusual spending
        result.unusualSpendings = detectUnusualSpending(categoryHistory, currentMonthSpending);

        // Find saving opportunities
        result.savingOpportunities = findSavingOpportunities(categoryHistory, currentMonthSpending);

        // Generate smart recommendations
        result.recommendations = generateSmartRecommendations(result, currentMonthSpending);

        // Calculate monthly comparison
        result.monthlyComparison = calculateMonthlyComparison(walletId);

        // Generate LLM summary
        result.summaryForLLM = generateLLMSummary(result);

        return result;
    }

    /**
     * Helper class for monthly spending data
     */
    private static class MonthlySpending {
        int year;
        int month;
        double amount;

        MonthlySpending(int year, int month, double amount) {
            this.year = year;
            this.month = month;
            this.amount = amount;
        }
    }

    /**
     * Get spending history grouped by category and month
     */
    private Map<Integer, List<MonthlySpending>> getMonthlySpendingHistory(int walletId) {
        Map<Integer, List<MonthlySpending>> history = new HashMap<>();

        Calendar cal = Calendar.getInstance();
        
        for (int i = 0; i < MONTHS_TO_ANALYZE; i++) {
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);

            // Get date range for this month
            Calendar startCal = Calendar.getInstance();
            startCal.set(year, month, 1, 0, 0, 0);
            startCal.set(Calendar.MILLISECOND, 0);

            Calendar endCal = Calendar.getInstance();
            endCal.set(year, month, startCal.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59);
            endCal.set(Calendar.MILLISECOND, 999);

            // Get transactions for this month
            List<Transaction> transactions = transactionDao.getTransactionsByWalletAndDateRange(
                    walletId, startCal.getTimeInMillis(), endCal.getTimeInMillis());

            // Group by category
            Map<Integer, Double> monthlyByCategory = new HashMap<>();
            for (Transaction t : transactions) {
                if ("expense".equals(t.getType())) {
                    int catId = t.getCategoryId();
                    monthlyByCategory.put(catId, monthlyByCategory.getOrDefault(catId, 0.0) + t.getAmount());
                }
            }

            // Add to history
            for (Map.Entry<Integer, Double> entry : monthlyByCategory.entrySet()) {
                int catId = entry.getKey();
                if (!history.containsKey(catId)) {
                    history.put(catId, new ArrayList<>());
                }
                history.get(catId).add(new MonthlySpending(year, month, entry.getValue()));
            }

            // Go to previous month
            cal.add(Calendar.MONTH, -1);
        }

        return history;
    }

    /**
     * Get current month spending by category
     */
    private Map<Integer, Double> getCurrentMonthSpending(int walletId) {
        Map<Integer, Double> spending = new HashMap<>();

        Calendar startCal = Calendar.getInstance();
        startCal.set(Calendar.DAY_OF_MONTH, 1);
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);

        Calendar endCal = Calendar.getInstance();
        endCal.set(Calendar.HOUR_OF_DAY, 23);
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);

        List<Transaction> transactions = transactionDao.getTransactionsByWalletAndDateRange(
                walletId, startCal.getTimeInMillis(), endCal.getTimeInMillis());

        for (Transaction t : transactions) {
            if ("expense".equals(t.getType())) {
                int catId = t.getCategoryId();
                spending.put(catId, spending.getOrDefault(catId, 0.0) + t.getAmount());
            }
        }

        return spending;
    }

    /**
     * Detect regular spending habits
     */
    private List<SpendingHabit> detectRegularHabits(Map<Integer, List<MonthlySpending>> history) {
        List<SpendingHabit> habits = new ArrayList<>();

        for (Map.Entry<Integer, List<MonthlySpending>> entry : history.entrySet()) {
            int categoryId = entry.getKey();
            List<MonthlySpending> monthlyData = entry.getValue();

            if (monthlyData.size() >= 2) { // Need at least 2 months of data
                double totalAmount = 0;
                for (MonthlySpending ms : monthlyData) {
                    totalAmount += ms.amount;
                }
                double average = totalAmount / monthlyData.size();
                int occurrences = monthlyData.size();

                Category category = categoryDao.getCategoryById(categoryId);
                if (category != null) {
                    boolean isEssential = isEssentialCategory(category.getName());
                    SpendingHabit habit = new SpendingHabit(
                            category.getName(), categoryId, average,
                            occurrences, MONTHS_TO_ANALYZE, isEssential);

                    if (habit.frequency >= REGULAR_THRESHOLD) {
                        habits.add(habit);
                    }
                }
            }
        }

        return habits;
    }

    /**
     * Find missing regular purchases this month
     */
    private List<MissingPurchase> findMissingPurchases(
            Map<Integer, List<MonthlySpending>> history,
            Map<Integer, Double> currentMonthSpending) {

        List<MissingPurchase> missing = new ArrayList<>();

        for (Map.Entry<Integer, List<MonthlySpending>> entry : history.entrySet()) {
            int categoryId = entry.getKey();
            List<MonthlySpending> monthlyData = entry.getValue();

            // Check if this is a regular category (appears in most months)
            if (monthlyData.size() >= (MONTHS_TO_ANALYZE * REGULAR_THRESHOLD)) {
                // Check if missing this month
                double currentAmount = currentMonthSpending.getOrDefault(categoryId, 0.0);

                if (currentAmount == 0) {
                    // Calculate average
                    double totalAmount = 0;
                    for (MonthlySpending ms : monthlyData) {
                        totalAmount += ms.amount;
                    }
                    double average = totalAmount / monthlyData.size();

                    Category category = categoryDao.getCategoryById(categoryId);
                    if (category != null && !isEssentialCategory(category.getName())) {
                        // Find last purchase date
                        MonthlySpending lastPurchase = monthlyData.get(0);
                        String lastDate = String.format("%d/%d", lastPurchase.month + 1, lastPurchase.year);

                        missing.add(new MissingPurchase(
                                category.getName(), categoryId, average,
                                monthlyData.size(), lastDate));
                    }
                }
            }
        }

        return missing;
    }

    /**
     * Detect unusual spending patterns
     */
    private List<UnusualSpending> detectUnusualSpending(
            Map<Integer, List<MonthlySpending>> history,
            Map<Integer, Double> currentMonthSpending) {

        List<UnusualSpending> unusual = new ArrayList<>();

        for (Map.Entry<Integer, Double> entry : currentMonthSpending.entrySet()) {
            int categoryId = entry.getKey();
            double currentAmount = entry.getValue();

            List<MonthlySpending> monthlyData = history.get(categoryId);
            if (monthlyData != null && monthlyData.size() >= 2) {
                double totalAmount = 0;
                for (MonthlySpending ms : monthlyData) {
                    totalAmount += ms.amount;
                }
                double average = totalAmount / monthlyData.size();

                Category category = categoryDao.getCategoryById(categoryId);
                if (category != null) {
                    if (currentAmount > average * UNUSUAL_SPIKE_THRESHOLD) {
                        unusual.add(new UnusualSpending(
                                category.getName(), categoryId, currentAmount, average, "spike"));
                    } else if (currentAmount < average * UNUSUAL_DROP_THRESHOLD && currentAmount > 0) {
                        unusual.add(new UnusualSpending(
                                category.getName(), categoryId, currentAmount, average, "drop"));
                    }
                }
            }
        }

        return unusual;
    }

    /**
     * Find saving opportunities
     */
    private List<SavingOpportunity> findSavingOpportunities(
            Map<Integer, List<MonthlySpending>> history,
            Map<Integer, Double> currentMonthSpending) {

        List<SavingOpportunity> opportunities = new ArrayList<>();

        for (Map.Entry<Integer, Double> entry : currentMonthSpending.entrySet()) {
            int categoryId = entry.getKey();
            double currentAmount = entry.getValue();

            Category category = categoryDao.getCategoryById(categoryId);
            if (category == null) continue;

            List<MonthlySpending> monthlyData = history.get(categoryId);

            // Non-essential categories with high spending
            if (!isEssentialCategory(category.getName()) && currentAmount > 500000) {
                // Find minimum month spending
                double minAmount = currentAmount;
                if (monthlyData != null) {
                    for (MonthlySpending ms : monthlyData) {
                        if (ms.amount < minAmount) {
                            minAmount = ms.amount;
                        }
                    }
                }

                if (currentAmount > minAmount * 1.3) {
                    double potentialSaving = currentAmount - minAmount;
                    String catNameVi = getCategoryVietnamese(category.getName());
                    opportunities.add(new SavingOpportunity(
                            category.getName(),
                            potentialSaving,
                            "Chi tiêu " + catNameVi + " cao hơn mức thấp nhất trong 6 tháng qua",
                            String.format("Bạn có thể tiết kiệm %.0f VNĐ nếu giảm chi tiêu %s về mức %.0f VNĐ như các tháng trước",
                                    potentialSaving, catNameVi, minAmount)
                    ));
                }
            }

            // Increasing trend detection
            if (monthlyData != null && monthlyData.size() >= 3) {
                boolean increasing = true;
                for (int i = 0; i < monthlyData.size() - 1; i++) {
                    if (monthlyData.get(i).amount <= monthlyData.get(i + 1).amount) {
                        increasing = false;
                        break;
                    }
                }

                if (increasing && !isEssentialCategory(category.getName())) {
                    double firstMonth = monthlyData.get(monthlyData.size() - 1).amount;
                    double potentialSaving = currentAmount - firstMonth;
                    if (potentialSaving > 100000) {
                        String catNameVi = getCategoryVietnamese(category.getName());
                        opportunities.add(new SavingOpportunity(
                                category.getName(),
                                potentialSaving,
                                "Chi tiêu " + catNameVi + " tăng liên tục trong " + monthlyData.size() + " tháng",
                                String.format("Chi tiêu %s tăng từ %.0f lên %.0f VNĐ. Bạn nên cân nhắc giảm về mức ban đầu.",
                                        catNameVi, firstMonth, currentAmount)
                        ));
                    }
                }
            }
        }

        return opportunities;
    }

    /**
     * Generate smart recommendations based on all analysis
     */
    private List<SmartRecommendation> generateSmartRecommendations(
            PatternAnalysisResult analysis,
            Map<Integer, Double> currentMonthSpending) {

        List<SmartRecommendation> recommendations = new ArrayList<>();

        // Recommendation for missing regular purchases
        for (MissingPurchase missing : analysis.missingPurchases) {
            recommendations.add(new SmartRecommendation(
                    "spend",
                    "medium",
                    "💡 Có thể bạn cần chi cho " + missing.categoryName,
                    missing.suggestion,
                    String.format("Bạn thường chi khoảng %.0f VNĐ cho %s mỗi tháng. Nếu cần thiết, bạn vẫn còn ngân sách.",
                            missing.usualAmount, missing.categoryName),
                    missing.categoryName,
                    missing.usualAmount
            ));
        }

        // Warning for unusual spikes
        for (UnusualSpending unusual : analysis.unusualSpendings) {
            if (unusual.type.equals("spike")) {
                recommendations.add(new SmartRecommendation(
                        "warning",
                        "high",
                        "⚠️ Chi tiêu " + unusual.categoryName + " cao bất thường",
                        unusual.analysis,
                        String.format("Bạn nên xem xét lại các khoản chi %s trong tháng này. Có thể có những chi phí không cần thiết.",
                                unusual.categoryName),
                        unusual.categoryName,
                        unusual.currentAmount - unusual.averageAmount
                ));
            }
        }

        // Celebration for unusual drops (good behavior)
        for (UnusualSpending unusual : analysis.unusualSpendings) {
            if (unusual.type.equals("drop") && !isEssentialCategory(unusual.categoryName)) {
                recommendations.add(new SmartRecommendation(
                        "celebrate",
                        "low",
                        "🎉 Tuyệt vời! Chi tiêu " + unusual.categoryName + " giảm đáng kể",
                        unusual.analysis,
                        String.format("Bạn đã tiết kiệm được %.0f VNĐ so với mức thông thường. Hãy tiếp tục phát huy nhé!",
                                unusual.averageAmount - unusual.currentAmount),
                        unusual.categoryName,
                        unusual.averageAmount - unusual.currentAmount
                ));
            }
        }

        // Saving opportunities
        for (SavingOpportunity opportunity : analysis.savingOpportunities) {
            recommendations.add(new SmartRecommendation(
                    "save",
                    "medium",
                    "💰 Cơ hội tiết kiệm: " + opportunity.categoryName,
                    opportunity.reason,
                    opportunity.suggestion,
                    opportunity.categoryName,
                    opportunity.potentialSaving
            ));
        }

        // Check for good habits
        int onTrackHabits = 0;
        for (SpendingHabit habit : analysis.regularHabits) {
            double current = currentMonthSpending.getOrDefault(habit.categoryId, 0.0);
            if (current <= habit.averageAmount * 1.1) {
                onTrackHabits++;
            }
        }

        if (onTrackHabits >= 3) {
            recommendations.add(new SmartRecommendation(
                    "celebrate",
                    "low",
                    "✅ Bạn đang có thói quen chi tiêu tốt",
                    String.format("Bạn đang duy trì %d thói quen chi tiêu ổn định", onTrackHabits),
                    "Hãy tiếp tục giữ mức chi tiêu đều đặn như thế này nhé! Đây là dấu hiệu cho thấy bạn quản lý tài chính rất tốt!",
                    null,
                    0
            ));
        }

        // End of month budget remaining
        Calendar cal = Calendar.getInstance();
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        
        if (dayOfMonth >= daysInMonth - 5) { // Last 5 days of month
            for (MissingPurchase missing : analysis.missingPurchases) {
                recommendations.add(new SmartRecommendation(
                        "spend",
                        "high",
                        "📅 Chỉ còn " + (daysInMonth - dayOfMonth) + " ngày nữa là hết tháng",
                        String.format("Bạn thường chi cho %s mỗi tháng nhưng tháng này chưa có khoản chi nào", missing.categoryName),
                        String.format("Nếu cần chi cho %s, hãy cân nhắc trong %d ngày tới nhé. Ngân sách thông thường: %.0f VNĐ",
                                missing.categoryName, daysInMonth - dayOfMonth, missing.usualAmount),
                        missing.categoryName,
                        missing.usualAmount
                ));
            }
        }

        return recommendations;
    }

    /**
     * Calculate monthly comparison
     */
    private MonthlyComparison calculateMonthlyComparison(int walletId) {
        MonthlyComparison comparison = new MonthlyComparison();

        Calendar cal = Calendar.getInstance();

        // This month
        Calendar thisMonthStart = Calendar.getInstance();
        thisMonthStart.set(Calendar.DAY_OF_MONTH, 1);
        thisMonthStart.set(Calendar.HOUR_OF_DAY, 0);
        thisMonthStart.set(Calendar.MINUTE, 0);
        thisMonthStart.set(Calendar.SECOND, 0);

        Calendar thisMonthEnd = Calendar.getInstance();

        comparison.thisMonthTotal = transactionDao.getTotalExpenseBetweenForWallet(
                thisMonthStart.getTimeInMillis(), thisMonthEnd.getTimeInMillis(), walletId);

        // Last month
        Calendar lastMonthStart = Calendar.getInstance();
        lastMonthStart.add(Calendar.MONTH, -1);
        lastMonthStart.set(Calendar.DAY_OF_MONTH, 1);
        lastMonthStart.set(Calendar.HOUR_OF_DAY, 0);
        lastMonthStart.set(Calendar.MINUTE, 0);
        lastMonthStart.set(Calendar.SECOND, 0);

        Calendar lastMonthEnd = Calendar.getInstance();
        lastMonthEnd.add(Calendar.MONTH, -1);
        lastMonthEnd.set(Calendar.DAY_OF_MONTH, lastMonthEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
        lastMonthEnd.set(Calendar.HOUR_OF_DAY, 23);
        lastMonthEnd.set(Calendar.MINUTE, 59);
        lastMonthEnd.set(Calendar.SECOND, 59);

        comparison.lastMonthTotal = transactionDao.getTotalExpenseBetweenForWallet(
                lastMonthStart.getTimeInMillis(), lastMonthEnd.getTimeInMillis(), walletId);

        // Average of last 6 months
        double totalForAverage = 0;
        Calendar avgCal = Calendar.getInstance();
        for (int i = 1; i <= MONTHS_TO_ANALYZE; i++) {
            avgCal.add(Calendar.MONTH, -1);
            Calendar monthStart = (Calendar) avgCal.clone();
            monthStart.set(Calendar.DAY_OF_MONTH, 1);
            monthStart.set(Calendar.HOUR_OF_DAY, 0);
            monthStart.set(Calendar.MINUTE, 0);
            monthStart.set(Calendar.SECOND, 0);

            Calendar monthEnd = (Calendar) avgCal.clone();
            monthEnd.set(Calendar.DAY_OF_MONTH, monthEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
            monthEnd.set(Calendar.HOUR_OF_DAY, 23);
            monthEnd.set(Calendar.MINUTE, 59);
            monthEnd.set(Calendar.SECOND, 59);

            totalForAverage += transactionDao.getTotalExpenseBetweenForWallet(
                    monthStart.getTimeInMillis(), monthEnd.getTimeInMillis(), walletId);
        }
        comparison.averageMonthly = totalForAverage / MONTHS_TO_ANALYZE;

        // Calculate percentages
        if (comparison.lastMonthTotal > 0) {
            comparison.percentageVsLastMonth = 
                    ((comparison.thisMonthTotal - comparison.lastMonthTotal) / comparison.lastMonthTotal) * 100;
        }

        if (comparison.averageMonthly > 0) {
            comparison.percentageVsAverage = 
                    ((comparison.thisMonthTotal - comparison.averageMonthly) / comparison.averageMonthly) * 100;
        }

        // Determine trend
        if (comparison.percentageVsAverage > 10) {
            comparison.trend = "increasing";
        } else if (comparison.percentageVsAverage < -10) {
            comparison.trend = "decreasing";
        } else {
            comparison.trend = "stable";
        }

        return comparison;
    }

    /**
     * Check if category is essential (food, transport, medical, etc.)
     */
    private boolean isEssentialCategory(String categoryName) {
        String lower = categoryName.toLowerCase();
        return lower.contains("food") || lower.contains("ăn") || lower.contains("thực phẩm") ||
               lower.contains("transport") || lower.contains("đi lại") || lower.contains("xăng") ||
               lower.contains("medical") || lower.contains("y tế") || lower.contains("thuốc") ||
               lower.contains("home") || lower.contains("nhà") || lower.contains("điện") ||
               lower.contains("water") || lower.contains("nước") ||
               lower.contains("groceries") || lower.contains("siêu thị") ||
               lower.contains("utilities") || lower.contains("tiện ích");
    }

    /**
     * Generate summary for LLM
     */
    private String generateLLMSummary(PatternAnalysisResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== PHÂN TÍCH MẪU CHI TIÊU ===\n\n");

        // Monthly comparison
        if (result.monthlyComparison != null) {
            sb.append("SO SÁNH HÀNG THÁNG:\n");
            sb.append(String.format("- Tháng này: %.0f VNĐ\n", result.monthlyComparison.thisMonthTotal));
            sb.append(String.format("- Tháng trước: %.0f VNĐ (%.1f%%)\n", 
                    result.monthlyComparison.lastMonthTotal, result.monthlyComparison.percentageVsLastMonth));
            sb.append(String.format("- Trung bình 6 tháng: %.0f VNĐ (%.1f%%)\n", 
                    result.monthlyComparison.averageMonthly, result.monthlyComparison.percentageVsAverage));
            sb.append(String.format("- Xu hướng: %s\n\n", getTrendVietnamese(result.monthlyComparison.trend)));
        }

        // Regular habits
        if (!result.regularHabits.isEmpty()) {
            sb.append("THÓI QUEN CHI TIÊU THƯỜNG XUYÊN:\n");
            for (SpendingHabit habit : result.regularHabits) {
                sb.append(String.format("- %s: %.0f VNĐ/tháng (%s)\n", 
                        habit.categoryName, habit.averageAmount, habit.pattern));
            }
            sb.append("\n");
        }

        // Missing purchases
        if (!result.missingPurchases.isEmpty()) {
            sb.append("KHOẢN CHI THƯỜNG XUYÊN CHƯA CÓ THÁNG NÀY:\n");
            for (MissingPurchase missing : result.missingPurchases) {
                sb.append(String.format("- %s: thường %.0f VNĐ (mua %d/%d tháng gần đây)\n", 
                        missing.categoryName, missing.usualAmount, missing.consecutiveMonths, MONTHS_TO_ANALYZE));
            }
            sb.append("\n");
        }

        // Unusual spending
        if (!result.unusualSpendings.isEmpty()) {
            sb.append("CHI TIÊU BẤT THƯỜNG:\n");
            for (UnusualSpending unusual : result.unusualSpendings) {
                sb.append(String.format("- %s\n", unusual.analysis));
            }
            sb.append("\n");
        }

        // Recommendations
        if (!result.recommendations.isEmpty()) {
            sb.append("ĐỀ XUẤT THÔNG MINH:\n");
            for (SmartRecommendation rec : result.recommendations) {
                sb.append(String.format("- [%s] %s: %s\n", 
                        rec.type.toUpperCase(), rec.title, rec.actionableAdvice));
            }
        }

        sb.append("\n=== KẾT THÚC PHÂN TÍCH ===");

        return sb.toString();
    }

    private String getTrendVietnamese(String trend) {
        switch (trend) {
            case "increasing": return "Tăng";
            case "decreasing": return "Giảm";
            default: return "Ổn định";
        }
    }

    /**
     * Generate quick spending insight
     */
    public static String generateQuickInsight(PatternAnalysisResult result) {
        if (result == null) {
            return "Chưa có đủ dữ liệu chi tiêu để phân tích. Bạn hãy tiếp tục ghi chép giao dịch nhé!";
        }

        StringBuilder response = new StringBuilder();

        // Monthly trend
        if (result.monthlyComparison != null) {
            String emoji = result.monthlyComparison.trend.equals("decreasing") ? "📉" :
                          result.monthlyComparison.trend.equals("increasing") ? "📈" : "➡️";
            response.append(String.format("%s **Xu hướng chi tiêu: %s**\n",
                    emoji, getTrendVietnameseStatic(result.monthlyComparison.trend)));
            response.append(String.format("Tháng này bạn đã chi: %.0f VNĐ (so với trung bình 6 tháng: %+.1f%%)\n\n",
                    result.monthlyComparison.thisMonthTotal, result.monthlyComparison.percentageVsAverage));
        }

        // Top recommendations (max 3)
        if (!result.recommendations.isEmpty()) {
            response.append("💡 **Gợi ý dành cho bạn:**\n");
            int count = 0;
            for (SmartRecommendation rec : result.recommendations) {
                if (count >= 3) break;
                response.append(String.format("• %s\n", rec.actionableAdvice));
                count++;
            }
        }

        return response.toString();
    }

    private static String getTrendVietnameseStatic(String trend) {
        switch (trend) {
            case "increasing": return "Tăng";
            case "decreasing": return "Giảm";
            default: return "Ổn định";
        }
    }
}
