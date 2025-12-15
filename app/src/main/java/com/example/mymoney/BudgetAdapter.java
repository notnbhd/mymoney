package com.example.mymoney;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.database.entity.Budget;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.BudgetViewHolder> {

    private final Context context;
    private final List<Budget> budgetList;
    private final OnBudgetClickListener listener;
    private final OnBudgetDeleteListener deleteListener;
    private final java.util.Map<Integer, Double> spentAmountsMap;
    private java.util.Map<Integer, String> categoryNamesMap;  // categoryId -> categoryName
    private final DecimalFormat df = new DecimalFormat("#,###");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());
    private final SimpleDateFormat dateParseFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

    public interface OnBudgetClickListener {
        void onBudgetClick(Budget budget);
    }

    public interface OnBudgetDeleteListener {
        void onBudgetDelete(Budget budget);
    }

    public BudgetAdapter(Context context, List<Budget> budgetList, OnBudgetClickListener listener, OnBudgetDeleteListener deleteListener, java.util.Map<Integer, Double> spentAmountsMap) {
        this.context = context;
        this.budgetList = budgetList;
        this.listener = listener;
        this.deleteListener = deleteListener;
        this.spentAmountsMap = spentAmountsMap;
        this.categoryNamesMap = new java.util.HashMap<>();
    }

    /**
     * Set category names map for displaying category tags on budget cards
     */
    public void setCategoryNamesMap(java.util.Map<Integer, String> categoryNamesMap) {
        this.categoryNamesMap = categoryNamesMap;
        notifyDataSetChanged();
    }

    // Public static helper method to calculate period range for a budget
    public static long[] calculatePeriodRange(Budget budget) {
        return calculatePeriodRangeInternal(budget);
    }

    private static long[] calculatePeriodRangeInternal(Budget budget) {
        Calendar startCal = Calendar.getInstance();
        Calendar endCal = Calendar.getInstance();
        SimpleDateFormat dateParseFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());
        
        switch (budget.getBudgetType()) {
            case "daily":
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                startCal.set(Calendar.MILLISECOND, 0);
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                endCal.set(Calendar.MILLISECOND, 999);
                break;
            case "weekly":
                // Set to Monday of current week
                int dayOfWeek = startCal.get(Calendar.DAY_OF_WEEK);
                int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
                startCal.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                startCal.set(Calendar.MILLISECOND, 0);
                
                // Set to Sunday of current week (6 days after Monday)
                endCal.setTimeInMillis(startCal.getTimeInMillis());
                endCal.add(Calendar.DAY_OF_MONTH, 6);
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                endCal.set(Calendar.MILLISECOND, 999);
                break;
            case "monthly":
                startCal.set(Calendar.DAY_OF_MONTH, 1);
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                startCal.set(Calendar.MILLISECOND, 0);
                endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH));
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                endCal.set(Calendar.MILLISECOND, 999);
                break;
            case "yearly":
                startCal.set(Calendar.DAY_OF_YEAR, 1);
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                startCal.set(Calendar.MILLISECOND, 0);
                endCal.set(Calendar.DAY_OF_YEAR, endCal.getActualMaximum(Calendar.DAY_OF_YEAR));
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                endCal.set(Calendar.MILLISECOND, 999);
                break;
            case "custom":
                try {
                    if (budget.getStartDate() != null && budget.getEndDate() != null) {
                        Date start = dateParseFormat.parse(budget.getStartDate());
                        Date end = dateParseFormat.parse(budget.getEndDate());
                        if (start != null && end != null) {
                            startCal.setTime(start);
                            endCal.setTime(end);
                        }
                    }
                } catch (ParseException e) {
                    // Use current day if parsing fails
                }
                break;
        }
        
        return new long[]{startCal.getTimeInMillis(), endCal.getTimeInMillis()};
    }

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_budget_card, parent, false);
        return new BudgetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder holder, int position) {
        Budget budget = budgetList.get(position);
        holder.bind(budget, listener);
    }

    @Override
    public int getItemCount() {
        return budgetList.size();
    }

    class BudgetViewHolder extends RecyclerView.ViewHolder {
        TextView tvBudgetName, tvPeriodType, tvDateRange, tvStatus;
        TextView tvSpent, tvProgress, tvTotal;
        TextView tvBudgetCategory;  // Category tag
        ImageView ivAlertIcon, ivChart;
        ImageButton btnDelete;

        public BudgetViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBudgetName = itemView.findViewById(R.id.tv_budget_name);
            tvPeriodType = itemView.findViewById(R.id.tv_budget_period_type);
            tvBudgetCategory = itemView.findViewById(R.id.tv_budget_category);
            tvDateRange = itemView.findViewById(R.id.tv_budget_date_range);
            tvStatus = itemView.findViewById(R.id.tv_budget_status);
            tvSpent = itemView.findViewById(R.id.tv_budget_spent);
            tvProgress = itemView.findViewById(R.id.tv_budget_progress);
            tvTotal = itemView.findViewById(R.id.tv_budget_total);
            ivAlertIcon = itemView.findViewById(R.id.iv_alert_icon);
            ivChart = itemView.findViewById(R.id.iv_budget_chart);
            btnDelete = itemView.findViewById(R.id.btn_delete_budget);
        }

        public void bind(Budget budget, OnBudgetClickListener listener) {
            tvBudgetName.setText(budget.getName());
            
            String periodType = budget.getBudgetType();
            tvPeriodType.setText(capitalizeFirst(periodType));

            // Show category tag if it's a category-specific budget
            if (budget.getCategoryId() != null && categoryNamesMap != null) {
                String categoryName = categoryNamesMap.get(budget.getCategoryId());
                if (categoryName != null && !categoryName.isEmpty()) {
                    tvBudgetCategory.setText(categoryName);
                    tvBudgetCategory.setVisibility(View.VISIBLE);
                } else {
                    tvBudgetCategory.setVisibility(View.GONE);
                }
            } else {
                tvBudgetCategory.setVisibility(View.GONE);
            }

            // Calculate date range based on period type
            String dateRange = calculateDateRange(budget);
            tvDateRange.setText(dateRange);

            // Get pre-calculated spent amount from map
            double spent = spentAmountsMap.getOrDefault(budget.getId(), 0.0);
            double total = budget.getBudgetAmount();
            
            // Get currency from MainActivity
            String currency = MainActivity.getSelectedWalletCurrency();
            
            // Debug logging
            android.util.Log.d("BudgetAdapter", "Binding budget: " + budget.getName() + 
                    " (ID: " + budget.getId() + ") | Spent from map: " + spent + " " + currency + 
                    " | Map size: " + spentAmountsMap.size());
            
            tvSpent.setText(df.format(spent) + " " + currency);
            tvTotal.setText(df.format(total) + " " + currency);

            int progress = total > 0 ? (int) ((spent / total) * 100) : 0;
            tvProgress.setText(progress + "%");

            // Set status
            if (spent == 0) {
                tvStatus.setText("Not Started");
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.red_expense));
            } else if (progress >= 100) {
                tvStatus.setText("Exceeded");
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.red_expense));
                ivAlertIcon.setVisibility(View.VISIBLE);
            } else if (progress >= budget.getAlertThreshold()) {
                tvStatus.setText("Warning");
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.chart_orange));
                ivAlertIcon.setVisibility(View.VISIBLE);
            } else {
                tvStatus.setText("On Track");
                tvStatus.setTextColor(itemView.getContext().getColor(R.color.green_income));
                ivAlertIcon.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onBudgetClick(budget));
            btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) {
                    deleteListener.onBudgetDelete(budget);
                }
            });
        }

        private String calculateDateRange(Budget budget) {
            Calendar cal = Calendar.getInstance();
            String startDate = dateFormat.format(cal.getTime());
            
            switch (budget.getBudgetType()) {
                case "daily":
                    return startDate;
                case "weekly":
                    cal.add(Calendar.DAY_OF_MONTH, 6);
                    return startDate + " ~ " + dateFormat.format(cal.getTime());
                case "monthly":
                    int lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                    cal.set(Calendar.DAY_OF_MONTH, lastDay);
                    return startDate + " ~ " + dateFormat.format(cal.getTime());
                case "yearly":
                    cal.set(Calendar.MONTH, Calendar.DECEMBER);
                    cal.set(Calendar.DAY_OF_MONTH, 31);
                    return startDate + " ~ " + dateFormat.format(cal.getTime());
                case "custom":
                    return budget.getStartDate() + " ~ " + budget.getEndDate();
                default:
                    return startDate;
            }
        }

        private String capitalizeFirst(String text) {
            if (text == null || text.isEmpty()) return text;
            return text.substring(0, 1).toUpperCase() + text.substring(1);
        }
    }
}
