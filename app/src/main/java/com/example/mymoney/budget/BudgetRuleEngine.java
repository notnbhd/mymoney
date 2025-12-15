package com.example.mymoney.budget;

import com.example.mymoney.database.entity.Budget;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rule-based recommendation engine for budget analysis.
 * Generates structured insights that can be passed to LLM for personalized advice.
 */
public class BudgetRuleEngine {

    // Spending velocity thresholds
    private static final double VELOCITY_CRITICAL = 1.5;  // Spending 50% faster than should
    private static final double VELOCITY_WARNING = 1.2;   // Spending 20% faster than should
    private static final double VELOCITY_GOOD = 0.8;      // Spending 20% slower than should

    // Budget usage thresholds
    private static final double USAGE_CRITICAL = 90.0;
    private static final double USAGE_WARNING = 75.0;
    private static final double USAGE_CAUTION = 50.0;

    /**
     * Main analysis result containing all insights
     */
    public static class BudgetAnalysisResult {
        public List<BudgetInsight> budgetInsights;
        public OverallFinancialHealth overallHealth;
        public List<Rule> triggeredRules;
        public List<ActionRecommendation> recommendations;
        public String summaryForLLM;

        public BudgetAnalysisResult() {
            budgetInsights = new ArrayList<>();
            triggeredRules = new ArrayList<>();
            recommendations = new ArrayList<>();
        }
    }

    /**
     * Individual budget insight
     */
    public static class BudgetInsight {
        public int budgetId;
        public String budgetName;
        public String budgetType;
        public Integer categoryId;       // null = global budget, non-null = category-specific
        public String categoryName;      // Category name for display
        public double budgetAmount;
        public double spentAmount;
        public double remainingAmount;
        public double usagePercentage;
        public int daysElapsed;
        public int daysRemaining;
        public int totalDays;
        public double dailyAverageSpent;
        public double recommendedDailyLimit;
        public double spendingVelocity;
        public String status;
        public String trend;
        
        public boolean isCategorySpecific() {
            return categoryId != null;
        }
    }

    /**
     * Overall financial health assessment
     */
    public static class OverallFinancialHealth {
        public String status;
        public int healthScore;
        public int budgetsOnTrack;
        public int budgetsAtRisk;
        public int budgetsExceeded;
        public int totalBudgetCount;
    }

    /**
     * Rule that was triggered
     */
    public static class Rule {
        public String ruleId;
        public String ruleName;
        public String severity;
        public String description;
        public String budgetName;

        public Rule(String ruleId, String ruleName, String severity, String description, String budgetName) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.severity = severity;
            this.description = description;
            this.budgetName = budgetName;
        }
    }

    /**
     * Actionable recommendation
     */
    public static class ActionRecommendation {
        public String type;
        public String priority;
        public String title;
        public String description;
        public String actionableAdvice;
        public double suggestedAmount;
        public String budgetName;  // Related budget name

        public ActionRecommendation(String type, String priority, String title,
                                    String description, String actionableAdvice, 
                                    double suggestedAmount, String budgetName) {
            this.type = type;
            this.priority = priority;
            this.title = title;
            this.description = description;
            this.actionableAdvice = actionableAdvice;
            this.suggestedAmount = suggestedAmount;
            this.budgetName = budgetName;
        }
    }

    /**
     * Analyze all budgets and generate comprehensive insights
     * @param budgets List of budgets to analyze
     * @param spentAmounts Map of budget ID to spent amount
     * @return Analysis result
     */
    public static BudgetAnalysisResult analyzeBudgets(List<Budget> budgets, Map<Integer, Double> spentAmounts) {
        return analyzeBudgets(budgets, spentAmounts, null);
    }

    /**
     * Analyze all budgets and generate comprehensive insights with category name mapping
     * @param budgets List of budgets to analyze
     * @param spentAmounts Map of budget ID to spent amount
     * @param categoryNames Map of category ID to category name (for translation)
     * @return Analysis result
     */
    public static BudgetAnalysisResult analyzeBudgets(List<Budget> budgets, Map<Integer, Double> spentAmounts, 
                                                       Map<Integer, String> categoryNames) {
        BudgetAnalysisResult result = new BudgetAnalysisResult();

        for (Budget budget : budgets) {
            double spent = spentAmounts.getOrDefault(budget.getId(), 0.0);
            String categoryName = null;
            if (categoryNames != null && budget.getCategoryId() != null) {
                categoryName = categoryNames.get(budget.getCategoryId());
            }
            BudgetInsight insight = analyzeSingleBudget(budget, spent, categoryName);
            result.budgetInsights.add(insight);

            applyRules(insight, result.triggeredRules, result.recommendations);
        }

        result.overallHealth = calculateOverallHealth(result.budgetInsights);
        applyCrossBudgetRules(result);
        result.summaryForLLM = generateLLMSummary(result);

        return result;
    }

    /**
     * Analyze a single budget
     * @param budget Budget to analyze
     * @param spent Amount spent
     * @param categoryName Category name from database (for translation), can be null
     */
    private static BudgetInsight analyzeSingleBudget(Budget budget, double spent, String categoryName) {
        BudgetInsight insight = new BudgetInsight();

        insight.budgetId = budget.getId();
        insight.budgetName = budget.getName();
        insight.budgetType = budget.getBudgetType();
        insight.categoryId = budget.getCategoryId();
        insight.categoryName = categoryName; // Store original category name for translation
        insight.budgetAmount = budget.getBudgetAmount();
        insight.spentAmount = spent;
        insight.remainingAmount = budget.getBudgetAmount() - spent;
        insight.usagePercentage = (spent / budget.getBudgetAmount()) * 100;

        int[] timeMetrics = calculateTimeMetrics(budget);
        insight.daysElapsed = timeMetrics[0];
        insight.daysRemaining = timeMetrics[1];
        insight.totalDays = timeMetrics[2];

        insight.dailyAverageSpent = insight.daysElapsed > 0 ? spent / insight.daysElapsed : spent;
        insight.recommendedDailyLimit = insight.daysRemaining > 0 ?
                insight.remainingAmount / insight.daysRemaining : 0;

        double expectedSpentByNow = insight.totalDays > 0 ? 
                (budget.getBudgetAmount() / insight.totalDays) * insight.daysElapsed : budget.getBudgetAmount();
        insight.spendingVelocity = expectedSpentByNow > 0 ? spent / expectedSpentByNow : 1.0;

        insight.status = determineStatus(insight);
        insight.trend = determineTrend(insight);

        return insight;
    }

    /**
     * Calculate time metrics for a budget period
     */
    private static int[] calculateTimeMetrics(Budget budget) {
        Calendar now = Calendar.getInstance();
        Calendar startCal = Calendar.getInstance();
        Calendar endCal = Calendar.getInstance();

        switch (budget.getBudgetType().toLowerCase()) {
            case "daily":
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                break;

            case "weekly":
                int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
                int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
                startCal.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                endCal.setTime(startCal.getTime());
                endCal.add(Calendar.DAY_OF_MONTH, 6);
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                break;

            case "monthly":
                startCal.set(Calendar.DAY_OF_MONTH, 1);
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                break;

            case "yearly":
                startCal.set(Calendar.MONTH, Calendar.JANUARY);
                startCal.set(Calendar.DAY_OF_MONTH, 1);
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                endCal.set(Calendar.MONTH, Calendar.DECEMBER);
                endCal.set(Calendar.DAY_OF_MONTH, 31);
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                break;

            case "custom":
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
                    if (budget.getStartDate() != null) {
                        startCal.setTime(sdf.parse(budget.getStartDate()));
                    }
                    if (budget.getEndDate() != null) {
                        endCal.setTime(sdf.parse(budget.getEndDate()));
                    }
                } catch (ParseException e) {
                    // Use default
                }
                break;
        }

        long totalMillis = endCal.getTimeInMillis() - startCal.getTimeInMillis();
        long elapsedMillis = now.getTimeInMillis() - startCal.getTimeInMillis();
        long remainingMillis = endCal.getTimeInMillis() - now.getTimeInMillis();

        int totalDays = Math.max(1, (int) (totalMillis / (1000 * 60 * 60 * 24)) + 1);
        int daysElapsed = Math.max(1, Math.min(totalDays, (int) (elapsedMillis / (1000 * 60 * 60 * 24)) + 1));
        int daysRemaining = Math.max(0, (int) (remainingMillis / (1000 * 60 * 60 * 24)));

        return new int[]{daysElapsed, daysRemaining, totalDays};
    }

    private static String determineStatus(BudgetInsight insight) {
        if (insight.usagePercentage >= 100) {
            return "exceeded";
        } else if (insight.usagePercentage >= USAGE_CRITICAL || insight.spendingVelocity >= VELOCITY_CRITICAL) {
            return "critical";
        } else if (insight.usagePercentage >= USAGE_WARNING || insight.spendingVelocity >= VELOCITY_WARNING) {
            return "warning";
        } else if (insight.usagePercentage >= USAGE_CAUTION) {
            return "caution";
        } else {
            return "on_track";
        }
    }

    private static String determineTrend(BudgetInsight insight) {
        if (insight.spendingVelocity <= VELOCITY_GOOD) {
            return "improving";
        } else if (insight.spendingVelocity >= VELOCITY_WARNING) {
            return "worsening";
        } else {
            return "stable";
        }
    }

    /**
     * Apply rules to a budget insight and generate recommendations
     */
    private static void applyRules(BudgetInsight insight, List<Rule> triggeredRules,
                                   List<ActionRecommendation> recommendations) {

        String budgetNameVi = getLocalizedBudgetName(insight);
        String budgetTypeVi = getBudgetTypeVietnamese(insight.budgetType);

        // Rule 1: Budget Exceeded
        if (insight.status.equals("exceeded")) {
            triggeredRules.add(new Rule(
                    "BUDGET_EXCEEDED",
                    "Ngân sách đã vượt",
                    "critical",
                    String.format("Bạn đã chi vượt %.0f VNĐ so với ngân sách %s (%.0f VNĐ)",
                            Math.abs(insight.remainingAmount), budgetNameVi, insight.budgetAmount),
                    budgetNameVi
            ));

            recommendations.add(new ActionRecommendation(
                    "reduce_spending",
                    "high",
                    "Dừng chi tiêu không cần thiết",
                    String.format("Ngân sách %s đã vượt %.0f VNĐ",
                            budgetNameVi, Math.abs(insight.remainingAmount)),
                    String.format("Bạn nên tạm dừng chi tiêu cho %s cho đến khi bắt đầu kỳ ngân sách mới. " +
                            "Hãy xem lại các giao dịch gần đây để tìm cách cắt giảm.", budgetNameVi.toLowerCase()),
                    Math.abs(insight.remainingAmount),
                    budgetNameVi
            ));
        }

        // Rule 2: Critical Spending Velocity
        else if (insight.spendingVelocity >= VELOCITY_CRITICAL && insight.daysRemaining > 0) {
            triggeredRules.add(new Rule(
                    "VELOCITY_CRITICAL",
                    "Chi tiêu quá nhanh",
                    "critical",
                    String.format("Bạn đang chi tiêu cho %s nhanh hơn %.0f%% so với kế hoạch",
                            budgetNameVi.toLowerCase(), (insight.spendingVelocity - 1) * 100),
                    budgetNameVi
            ));

            int daysUntil = estimateDaysUntilExceeded(insight);
            recommendations.add(new ActionRecommendation(
                    "reduce_spending",
                    "high",
                    "Giảm chi tiêu ngay",
                    String.format("Với tốc độ hiện tại, bạn sẽ vượt ngân sách %s trong %d ngày",
                            budgetNameVi, daysUntil),
                    String.format("Với tốc độ chi tiêu hiện tại cho %s, bạn có nguy cơ vượt ngân sách trong %d ngày nữa. " +
                            "Hãy hạn chế chi tối đa %.0f VNĐ/ngày trong %d ngày còn lại.",
                            budgetNameVi.toLowerCase(), daysUntil, insight.recommendedDailyLimit, insight.daysRemaining),
                    insight.recommendedDailyLimit,
                    budgetNameVi
            ));
        }

        // Rule 3: Critical - High Usage (>= 90%)
        else if (insight.usagePercentage >= USAGE_CRITICAL) {
            triggeredRules.add(new Rule(
                    "USAGE_CRITICAL",
                    "Gần hết ngân sách",
                    "critical",
                    String.format("Đã sử dụng %.0f%% ngân sách %s, chỉ còn %.0f VNĐ",
                            insight.usagePercentage, budgetNameVi, insight.remainingAmount),
                    budgetNameVi
            ));

            recommendations.add(new ActionRecommendation(
                    "reduce_spending",
                    "high",
                    "Hạn chế chi tiêu ngay",
                    String.format("Ngân sách %s gần hết - chỉ còn %.0f VNĐ",
                            budgetNameVi, insight.remainingAmount),
                    String.format("Bạn đã dùng %.0f%% ngân sách %s và chỉ còn %.0f VNĐ cho %d ngày tới. " +
                            "Hãy cân nhắc kỹ trước mỗi khoản chi và chỉ chi tiêu cho những việc thực sự cần thiết.",
                            insight.usagePercentage, budgetNameVi.toLowerCase(), insight.remainingAmount, insight.daysRemaining),
                    insight.recommendedDailyLimit,
                    budgetNameVi
            ));
        }

        // Rule 4: Warning - Approaching Limit (75-90%)
        else if (insight.usagePercentage >= USAGE_WARNING) {
            triggeredRules.add(new Rule(
                    "APPROACHING_LIMIT",
                    "Sắp đạt giới hạn",
                    "warning",
                    String.format("Đã sử dụng %.0f%% ngân sách %s, còn %d ngày",
                            insight.usagePercentage, budgetNameVi, insight.daysRemaining),
                    budgetNameVi
            ));

            recommendations.add(new ActionRecommendation(
                    "reduce_spending",
                    "medium",
                    "Theo dõi chi tiêu chặt chẽ",
                    String.format("Còn %.0f VNĐ trong ngân sách %s",
                            insight.remainingAmount, budgetNameVi),
                    String.format("Bạn còn %.0f VNĐ cho %s trong %d ngày tới. Để an toàn, hãy cố gắng chi không quá %.0f VNĐ/ngày " +
                            "và tạm hoãn các khoản chi tiêu không cấp bách.",
                            insight.remainingAmount, budgetNameVi.toLowerCase(), insight.daysRemaining, insight.recommendedDailyLimit),
                    insight.recommendedDailyLimit,
                    budgetNameVi
            ));
        }

        // Rule 5: Warning - Spending velocity elevated (1.2-1.5x)
        else if (insight.spendingVelocity >= VELOCITY_WARNING) {
            triggeredRules.add(new Rule(
                    "VELOCITY_WARNING",
                    "Chi tiêu hơi nhanh",
                    "warning",
                    String.format("Bạn đang chi cho %s nhanh hơn %.0f%% so với kế hoạch",
                            budgetNameVi.toLowerCase(), (insight.spendingVelocity - 1) * 100),
                    budgetNameVi
            ));

            recommendations.add(new ActionRecommendation(
                    "reduce_spending",
                    "medium",
                    "Chú ý tốc độ chi tiêu",
                    String.format("Ngân sách %s đang chi nhanh hơn dự kiến",
                            budgetNameVi),
                    String.format("Bạn đang chi tiêu cho %s nhanh hơn %.0f%% so với kế hoạch. Còn %.0f VNĐ cho %d ngày tới. " +
                            "Nên giảm xuống còn %.0f VNĐ/ngày để đảm bảo không vượt ngân sách.",
                            budgetNameVi.toLowerCase(), (insight.spendingVelocity - 1) * 100, insight.remainingAmount,
                            insight.daysRemaining, insight.recommendedDailyLimit),
                    insight.recommendedDailyLimit,
                    budgetNameVi
            ));
        }

        // Rule 6: On Track - Positive Reinforcement
        else if (insight.status.equals("on_track") && insight.spendingVelocity <= VELOCITY_GOOD) {
            triggeredRules.add(new Rule(
                    "ON_TRACK",
                    "Tiến độ tốt",
                    "info",
                    String.format("Bạn đang quản lý ngân sách %s tốt - chi chậm hơn %.0f%% so với kế hoạch",
                            budgetNameVi, (1 - insight.spendingVelocity) * 100),
                    budgetNameVi
            ));

            double surplus = (insight.budgetAmount / insight.totalDays * insight.daysElapsed) - insight.spentAmount;
            if (surplus > 0) {
                recommendations.add(new ActionRecommendation(
                        "celebrate",
                        "low",
                        "Bạn đang làm tốt!",
                        String.format("Bạn đã tiết kiệm được %.0f VNĐ cho %s", surplus, budgetNameVi.toLowerCase()),
                        String.format("Tuyệt vời! Bạn đang kiểm soát chi tiêu cho %s rất tốt và đã tiết kiệm được %.0f VNĐ. " +
                                "Tiếp tục duy trì nhé! Bạn có thể cân nhắc để dành phần tiết kiệm này vào quỹ dự phòng.",
                                budgetNameVi.toLowerCase(), surplus),
                        surplus,
                        budgetNameVi
                ));
            }
        }

        // Rule 7: End of Period Warning
        if (insight.daysRemaining <= 2 && insight.daysRemaining > 0 && insight.remainingAmount > 0) {
            double dailyRemaining = insight.remainingAmount / insight.daysRemaining;
            triggeredRules.add(new Rule(
                    "PERIOD_ENDING",
                    "Kỳ ngân sách sắp kết thúc",
                    "info",
                    String.format("Ngân sách %s kết thúc trong %d ngày",
                            budgetNameVi, insight.daysRemaining),
                    budgetNameVi
            ));

            String dayText = insight.daysRemaining == 1 ? "hôm nay" : insight.daysRemaining + " ngày tới";
            recommendations.add(new ActionRecommendation(
                    "redistribute",
                    "low",
                    "Lên kế hoạch chi tiêu còn lại",
                    String.format("Bạn còn %.0f VNĐ cho %s trong %s",
                            insight.remainingAmount, budgetNameVi.toLowerCase(), dayText),
                    String.format("Kỳ ngân sách %s sắp kết thúc. Bạn còn %.0f VNĐ, có thể chi tối đa %.0f VNĐ/ngày. " +
                            "Hãy ưu tiên những khoản chi thực sự cần thiết.",
                            budgetNameVi, insight.remainingAmount, dailyRemaining),
                    dailyRemaining,
                    budgetNameVi
            ));
        }
    }

    private static int estimateDaysUntilExceeded(BudgetInsight insight) {
        if (insight.dailyAverageSpent <= 0) return insight.daysRemaining;
        int daysUntilExceeded = (int) (insight.remainingAmount / insight.dailyAverageSpent);
        return Math.max(0, Math.min(daysUntilExceeded, insight.daysRemaining));
    }

    private static OverallFinancialHealth calculateOverallHealth(List<BudgetInsight> insights) {
        OverallFinancialHealth health = new OverallFinancialHealth();

        int onTrack = 0, atRisk = 0, exceeded = 0;

        // Count status for all budgets
        for (BudgetInsight insight : insights) {
            switch (insight.status) {
                case "on_track":
                case "caution":
                    onTrack++;
                    break;
                case "warning":
                case "critical":
                    atRisk++;
                    break;
                case "exceeded":
                    exceeded++;
                    break;
            }
        }
        health.budgetsOnTrack = onTrack;
        health.budgetsAtRisk = atRisk;
        health.budgetsExceeded = exceeded;
        health.totalBudgetCount = insights.size();

        int totalBudgets = insights.size();
        if (totalBudgets == 0) {
            health.healthScore = 100;
            health.status = "healthy";
        } else {
            double onTrackRatio = (double) onTrack / totalBudgets;
            double exceededPenalty = (double) exceeded / totalBudgets * 50;
            double atRiskPenalty = (double) atRisk / totalBudgets * 25;

            health.healthScore = (int) Math.max(0, Math.min(100,
                    onTrackRatio * 100 - exceededPenalty - atRiskPenalty));

            if (health.healthScore >= 80) {
                health.status = "healthy";
            } else if (health.healthScore >= 60) {
                health.status = "moderate";
            } else if (health.healthScore >= 40) {
                health.status = "at_risk";
            } else {
                health.status = "critical";
            }
        }

        return health;
    }

    private static void applyCrossBudgetRules(BudgetAnalysisResult result) {
        OverallFinancialHealth health = result.overallHealth;

        if (health.budgetsAtRisk + health.budgetsExceeded >= 2) {
            result.triggeredRules.add(new Rule(
                    "MULTIPLE_BUDGETS_AT_RISK",
                    "Nhiều ngân sách cần chú ý",
                    "critical",
                    String.format("%d ngân sách đang gặp rủi ro hoặc đã vượt",
                            health.budgetsAtRisk + health.budgetsExceeded),
                    "Tổng thể"
            ));

            result.recommendations.add(new ActionRecommendation(
                    "reduce_spending",
                    "high",
                    "Xem xét lại toàn bộ chi tiêu",
                    "Nhiều ngân sách đang gặp rủi ro. Cần đánh giá lại chi tiêu tổng thể.",
                    String.format("Hiện có %d ngân sách đang có vấn đề. Bạn nên dừng lại và xem xét lại toàn bộ chi tiêu. " +
                            "Hãy xác định 3 danh mục chi nhiều nhất và tìm cách giảm thiểu ở mỗi danh mục.",
                            health.budgetsAtRisk + health.budgetsExceeded),
                    0,
                    "Tổng thể"
            ));
        }

        if (health.status.equals("healthy") && health.budgetsExceeded == 0) {
            result.triggeredRules.add(new Rule(
                    "FINANCES_HEALTHY",
                    "Tài chính ổn định",
                    "info",
                    String.format("Điểm sức khỏe tài chính: %d/100", health.healthScore),
                    "Tổng thể"
            ));
        }
    }

    /**
     * Generate a structured summary for the LLM
     */
    private static String generateLLMSummary(BudgetAnalysisResult result) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== BÁO CÁO PHÂN TÍCH NGÂN SÁCH ===\n\n");

        // Overall Health
        sb.append("TÌNH TRẠNG TÀI CHÍNH TỔNG THỂ:\n");
        sb.append(String.format("- Trạng thái: %s\n", getStatusVietnamese(result.overallHealth.status)));
        sb.append(String.format("- Điểm sức khỏe: %d/100\n", result.overallHealth.healthScore));
        sb.append(String.format("- Tổng số ngân sách: %d\n", result.overallHealth.totalBudgetCount));
        sb.append(String.format("- Đúng tiến độ: %d | Cần chú ý: %d | Đã vượt: %d\n\n",
                result.overallHealth.budgetsOnTrack,
                result.overallHealth.budgetsAtRisk,
                result.overallHealth.budgetsExceeded));

        // Individual Budgets
        sb.append("CHI TIẾT TỪNG NGÂN SÁCH:\n");
        for (BudgetInsight insight : result.budgetInsights) {
            String budgetNameVi = getLocalizedBudgetName(insight);
            String budgetTypeVi = getBudgetTypeVietnamese(insight.budgetType);
            sb.append(String.format("- %s (theo %s): %.0f/%.0f VNĐ (%.1f%%) | Trạng thái: %s | Còn %d ngày\n",
                    budgetNameVi,
                    budgetTypeVi,
                    insight.spentAmount,
                    insight.budgetAmount,
                    insight.usagePercentage,
                    getStatusVietnamese(insight.status),
                    insight.daysRemaining));
            sb.append(String.format("  Trung bình/ngày: %.0f VNĐ | Đề xuất: %.0f VNĐ/ngày | Tốc độ chi: %.2fx\n",
                    insight.dailyAverageSpent,
                    insight.recommendedDailyLimit,
                    insight.spendingVelocity));
        }

        // Triggered Rules
        if (!result.triggeredRules.isEmpty()) {
            sb.append("\nCÁC QUY TẮC ĐƯỢC KÍCH HOẠT:\n");
            for (Rule rule : result.triggeredRules) {
                sb.append(String.format("- [%s] %s: %s\n",
                        getSeverityVietnamese(rule.severity), rule.ruleName, rule.description));
            }
        }

        // Recommendations
        if (!result.recommendations.isEmpty()) {
            sb.append("\nĐỀ XUẤT HÀNH ĐỘNG:\n");
            for (ActionRecommendation rec : result.recommendations) {
                sb.append(String.format("- [%s] %s: %s\n",
                        getPriorityVietnamese(rec.priority), rec.title, rec.actionableAdvice));
            }
        }

        sb.append("\n=== KẾT THÚC BÁO CÁO ===");

        return sb.toString();
    }

    private static String getStatusVietnamese(String status) {
        switch (status) {
            case "healthy": return "Khỏe mạnh";
            case "moderate": return "Trung bình";
            case "at_risk": return "Có rủi ro";
            case "critical": return "Nghiêm trọng";
            case "on_track": return "Đúng tiến độ";
            case "caution": return "Cần chú ý";
            case "warning": return "Cảnh báo";
            case "exceeded": return "Đã vượt";
            default: return status;
        }
    }

    private static String getSeverityVietnamese(String severity) {
        switch (severity) {
            case "critical": return "NGHIÊM TRỌNG";
            case "warning": return "CẢNH BÁO";
            case "info": return "THÔNG TIN";
            default: return severity.toUpperCase();
        }
    }

    private static String getPriorityVietnamese(String priority) {
        switch (priority) {
            case "high": return "CAO";
            case "medium": return "TRUNG BÌNH";
            case "low": return "THẤP";
            default: return priority.toUpperCase();
        }
    }

    /**
     * Map English category names to Vietnamese
     */
    private static String getCategoryVietnamese(String categoryName) {
        if (categoryName == null || categoryName.isEmpty()) return categoryName;
        
        // Normalize: trim and lowercase for comparison
        String normalized = categoryName.trim().toLowerCase();
        
        switch (normalized) {
            // Default expense categories from AppDatabase
            case "food": return "Ăn uống";
            case "home": return "Nhà cửa";
            case "transport": return "Di chuyển";
            case "relationship": return "Mối quan hệ";
            case "entertainment": return "Giải trí";
            case "medical": return "Y tế";
            case "tax": return "Thuế";
            case "gym & fitness":
            case "gym":
            case "fitness": return "Thể dục";
            case "beauty": return "Làm đẹp";
            case "clothing":
            case "clothes": return "Quần áo";
            case "education": return "Giáo dục";
            case "childcare": return "Chăm sóc trẻ";
            case "groceries":
            case "grocery": return "Tạp hóa";
            case "others":
            case "other": return "Khác";
            
            // Default income categories from AppDatabase
            case "salary": return "Lương";
            case "business": return "Kinh doanh";
            case "gifts":
            case "gift": return "Quà tặng";
            
            // Additional common categories
            case "food & drinks":
            case "food and drinks":
            case "food & drink":
            case "food and drink": return "Ăn uống";
            case "transportation": return "Di chuyển";
            case "shopping": return "Mua sắm";
            case "bills": 
            case "bill": return "Hóa đơn";
            case "health": 
            case "healthcare": return "Sức khỏe";
            case "personal care": return "Chăm sóc cá nhân";
            case "sports": 
            case "sport": return "Thể thao";
            case "travel": return "Du lịch";
            case "pets": 
            case "pet": return "Thú cưng";
            case "housing":
            case "rent": return "Thuê nhà";
            case "utilities": 
            case "utility": return "Tiện ích";
            case "insurance": return "Bảo hiểm";
            case "savings": 
            case "saving": return "Tiết kiệm";
            case "charity": 
            case "donation": return "Từ thiện";
            case "family": return "Gia đình";
            case "electronics": 
            case "electronic":
            case "tech":
            case "technology": return "Điện tử";
            case "subscriptions": 
            case "subscription": return "Đăng ký dịch vụ";
            case "cafe":
            case "coffee": return "Cà phê";
            case "restaurant":
            case "dining": return "Nhà hàng";
            case "gas":
            case "fuel":
            case "petrol": return "Xăng dầu";
            case "phone":
            case "mobile": return "Điện thoại";
            case "internet":
            case "wifi": return "Internet";
            case "electricity":
            case "electric": return "Điện";
            case "water": return "Nước";
            case "bonus": return "Thưởng";
            case "investment": return "Đầu tư";
            case "income": return "Thu nhập";
            case "other income": return "Thu nhập khác";
            case "other expense": return "Chi khác";
            
            default: return categoryName;
        }
    }

    /**
     * Map budget type to Vietnamese
     */
    private static String getBudgetTypeVietnamese(String budgetType) {
        if (budgetType == null) return "";
        
        switch (budgetType.toLowerCase()) {
            case "daily": return "ngày";
            case "weekly": return "tuần";
            case "monthly": return "tháng";
            case "yearly": return "năm";
            case "custom": return "tùy chỉnh";
            default: return budgetType;
        }
    }

    /**
     * Get localized budget name (translate if needed)
     * Priority: 1. Translate categoryName if available, 2. Translate budgetName, 3. Return original
     */
    private static String getLocalizedBudgetName(BudgetInsight insight) {
        // First, try to translate category name if available
        if (insight.categoryName != null && !insight.categoryName.isEmpty()) {
            String translated = getCategoryVietnamese(insight.categoryName);
            if (!translated.equals(insight.categoryName)) {
                return translated;
            }
            // Category name is already Vietnamese or not in dictionary
            return insight.categoryName;
        }
        
        // Fall back to translating budget name
        String translated = getCategoryVietnamese(insight.budgetName);
        if (!translated.equals(insight.budgetName)) {
            return translated;
        }
        
        return insight.budgetName;
    }

    /**
     * Generate quick response without LLM
     */
    public static String generateQuickResponse(BudgetAnalysisResult result) {
        if (result == null || result.budgetInsights.isEmpty()) {
            return "Bạn chưa thiết lập ngân sách nào. Hãy tạo ngân sách để tôi có thể đưa ra lời khuyên nhé!";
        }

        StringBuilder response = new StringBuilder();

        // Overall status
        String statusEmoji;
        switch (result.overallHealth.status) {
            case "healthy": statusEmoji = "✅"; break;
            case "moderate": statusEmoji = "⚠️"; break;
            case "at_risk": statusEmoji = "🔶"; break;
            default: statusEmoji = "🔴"; break;
        }

        response.append(String.format("%s **Sức khỏe tài chính: %s** (Điểm: %d/100)\n\n",
                statusEmoji,
                getStatusVietnamese(result.overallHealth.status),
                result.overallHealth.healthScore));

        // Top recommendations with budget names
        if (!result.recommendations.isEmpty()) {
            response.append("💡 **Lời khuyên dành cho bạn:**\n\n");
            int count = 0;
            for (ActionRecommendation rec : result.recommendations) {
                if (count >= 3) break;
                String emoji = rec.priority.equals("high") ? "🔴" :
                        rec.priority.equals("medium") ? "🟡" : "🟢";
                
                // Display budget name and advice
                String budgetDisplay = rec.budgetName != null && !rec.budgetName.equals("Tổng thể") 
                        ? String.format("**[%s]** ", rec.budgetName) 
                        : "";
                response.append(String.format("%s %s%s\n\n", emoji, budgetDisplay, rec.actionableAdvice));
                count++;
            }
        }

        // Add brief summary
        response.append(String.format("_Tổng kết: %d ngân sách ổn định, %d cần chú ý_",
                result.overallHealth.budgetsOnTrack,
                result.overallHealth.budgetsAtRisk + result.overallHealth.budgetsExceeded));

        return response.toString();
    }
}
