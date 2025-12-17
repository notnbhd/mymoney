package com.example.mymoney.budget;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.mymoney.MainActivity;
import com.example.mymoney.R;

import java.util.HashSet;
import java.util.Set;

/**
 * Handles budget-related notifications.
 * Sends alerts when budgets are at risk or exceeded.
 */
public class BudgetNotificationService {

    private static final String CHANNEL_ID = "budget_alerts";
    private static final String CHANNEL_NAME = "Cảnh báo ngân sách";
    private static final String CHANNEL_DESC = "Thông báo về tình trạng ngân sách và đề xuất chi tiêu";

    // Track which notifications have been sent to avoid spam
    private static final Set<String> sentNotifications = new HashSet<>();

    private final Context context;
    private final NotificationManagerCompat notificationManager;

    public BudgetNotificationService(Context context) {
        this.context = context;
        this.notificationManager = NotificationManagerCompat.from(context);
        createNotificationChannel();
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(CHANNEL_DESC);
            channel.enableVibration(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Check budget analysis result and send appropriate notifications
     */
    public void checkAndNotify(BudgetRuleEngine.BudgetAnalysisResult result) {
        if (result == null) return;

        for (BudgetRuleEngine.BudgetInsight insight : result.budgetInsights) {
            checkBudgetAndNotify(insight);
        }

        // Check overall health
        if (result.overallHealth.status.equals("critical")) {
            sendOverallHealthNotification(result.overallHealth);
        }
    }

    /**
     * Check individual budget and send notification if needed
     */
    private void checkBudgetAndNotify(BudgetRuleEngine.BudgetInsight insight) {
        String notificationKey = "budget_" + insight.budgetId + "_" + insight.status;

        // Don't send duplicate notifications
        if (sentNotifications.contains(notificationKey)) {
            return;
        }

        String title;
        String message;
        int priority;

        switch (insight.status) {
            case "exceeded":
                title = "🔴 Ngân sách đã vượt!";
                message = String.format("%s: Vượt %.0f VNĐ",
                        insight.budgetName, Math.abs(insight.remainingAmount));
                priority = NotificationCompat.PRIORITY_HIGH;
                break;

            case "critical":
                title = "🟠 Ngân sách sắp hết!";
                message = String.format("%s: Đã sử dụng %.0f%%, còn %d ngày",
                        insight.budgetName, insight.usagePercentage, insight.daysRemaining);
                priority = NotificationCompat.PRIORITY_HIGH;
                break;

            case "warning":
                title = "🟡 Cảnh báo ngân sách";
                message = String.format("%s: %.0f%% đã sử dụng. Đề xuất chi %.0f VNĐ/ngày",
                        insight.budgetName, insight.usagePercentage, insight.recommendedDailyLimit);
                priority = NotificationCompat.PRIORITY_DEFAULT;
                break;

            default:
                // Don't notify for on_track or caution status
                return;
        }

        sendNotification(insight.budgetId, title, message, priority);
        sentNotifications.add(notificationKey);
    }

    /**
     * Send overall health notification
     */
    private void sendOverallHealthNotification(BudgetRuleEngine.OverallFinancialHealth health) {
        String notificationKey = "overall_health_critical";

        if (sentNotifications.contains(notificationKey)) {
            return;
        }

        String title = "⚠️ Tài chính cần chú ý";
        String message = String.format("Điểm sức khỏe: %d/100. %d ngân sách gặp rủi ro.",
                health.healthScore, health.budgetsAtRisk + health.budgetsExceeded);

        sendNotification(999, title, message, NotificationCompat.PRIORITY_HIGH);
        sentNotifications.add(notificationKey);
    }

    /**
     * Send a notification
     */
    private void sendNotification(int notificationId, String title, String message, int priority) {
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        // Create intent to open app when notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("open_fragment", "budget");

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(priority)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        try {
            notificationManager.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            // Permission denied
        }
    }

    /**
     * Send a daily budget summary notification
     */
    public void sendDailySummary(BudgetRuleEngine.BudgetAnalysisResult result) {
        if (result == null) return;

        String statusEmoji;
        switch (result.overallHealth.status) {
            case "healthy": statusEmoji = "✅"; break;
            case "moderate": statusEmoji = "⚠️"; break;
            case "at_risk": statusEmoji = "🔶"; break;
            default: statusEmoji = "🔴"; break;
        }

        String title = statusEmoji + " Tóm tắt ngân sách hôm nay";
        String message = String.format("Điểm: %d/100 | %d ổn định, %d cần chú ý",
                result.overallHealth.healthScore,
                result.overallHealth.budgetsOnTrack,
                result.overallHealth.budgetsAtRisk + result.overallHealth.budgetsExceeded);

        sendNotification(1000, title, message, NotificationCompat.PRIORITY_LOW);
    }

    /**
     * Clear sent notification tracking (call when new budget period starts)
     */
    public static void clearNotificationHistory() {
        sentNotifications.clear();
    }

    /**
     * Send recommendation notification
     */
    public void sendRecommendationNotification(BudgetRuleEngine.ActionRecommendation recommendation) {
        if (recommendation == null) return;

        String emoji;
        int priority;
        switch (recommendation.priority) {
            case "high":
                emoji = "🔴";
                priority = NotificationCompat.PRIORITY_HIGH;
                break;
            case "medium":
                emoji = "🟡";
                priority = NotificationCompat.PRIORITY_DEFAULT;
                break;
            default:
                emoji = "🟢";
                priority = NotificationCompat.PRIORITY_LOW;
                break;
        }

        String title = emoji + " " + recommendation.title;
        String message = recommendation.actionableAdvice;

        sendNotification(recommendation.hashCode(), title, message, priority);
    }
}