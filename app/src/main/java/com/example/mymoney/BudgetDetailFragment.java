package com.example.mymoney;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.BudgetDao;
import com.example.mymoney.database.dao.TransactionDao;
import com.example.mymoney.database.entity.Budget;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executors;

public class BudgetDetailFragment extends Fragment {

    private static final String ARG_BUDGET_ID = "budget_id";

    private TextView tvBudgetName, tvPeriodType, tvTotalPeriod, tvGlobalBudget;
    private TextView tvCurrentPeriod, tvProgressPercentage, tvDaysLeft;
    private TextView tvSpentAmount, tvDailyAverage;
    private ImageButton btnPreviousPeriod, btnNextPeriod;
    private ProgressBar progressCircular;
    private TextView btnAddFilter;

    private BudgetDao budgetDao;
    private TransactionDao transactionDao;
    private Budget currentBudget;
    private Calendar currentPeriodStart;
    private Calendar currentPeriodEnd;

    private final DecimalFormat df = new DecimalFormat("#,###");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.getDefault());

    public static BudgetDetailFragment newInstance(int budgetId) {
        BudgetDetailFragment fragment = new BudgetDetailFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_BUDGET_ID, budgetId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_budget_detail, container, false);

        // Initialize views
        tvBudgetName = view.findViewById(R.id.tv_detail_budget_name);
        tvPeriodType = view.findViewById(R.id.tv_detail_period_type);
        tvTotalPeriod = view.findViewById(R.id.tv_detail_total_period);
        tvGlobalBudget = view.findViewById(R.id.tv_detail_global_budget);
        tvCurrentPeriod = view.findViewById(R.id.tv_current_period);
        tvProgressPercentage = view.findViewById(R.id.tv_progress_percentage);
        tvDaysLeft = view.findViewById(R.id.tv_days_left);
        tvSpentAmount = view.findViewById(R.id.tv_spent_amount);
        tvDailyAverage = view.findViewById(R.id.tv_daily_average);
        btnPreviousPeriod = view.findViewById(R.id.btn_previous_period);
        btnNextPeriod = view.findViewById(R.id.btn_next_period);
        progressCircular = view.findViewById(R.id.progress_circular);
        btnAddFilter = view.findViewById(R.id.btn_add_filter);

        // Initialize database
        budgetDao = AppDatabase.getInstance(requireContext()).budgetDao();
        transactionDao = AppDatabase.getInstance(requireContext()).transactionDao();

        // Load budget data
        int budgetId = getArguments() != null ? getArguments().getInt(ARG_BUDGET_ID, -1) : -1;
        if (budgetId != -1) {
            loadBudgetData(budgetId);
        }

        // Button listeners
        btnPreviousPeriod.setOnClickListener(v -> navigatePeriod(-1));
        btnNextPeriod.setOnClickListener(v -> navigatePeriod(1));
        btnAddFilter.setOnClickListener(v -> {
            // TODO: Implement filter functionality
        });

        return view;
    }

    private void loadBudgetData(int budgetId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            currentBudget = budgetDao.getBudgetById(budgetId);

            requireActivity().runOnUiThread(() -> {
                if (currentBudget != null) {
                    displayBudgetInfo();
                    calculateCurrentPeriod();
                    updatePeriodDisplay();
                }
            });
        });
    }

    private void displayBudgetInfo() {
        tvBudgetName.setText(currentBudget.getName());
        tvPeriodType.setText(capitalizeFirst(currentBudget.getBudgetType()));

        String totalPeriod = "Total Period:" + currentBudget.getStartDate() + "~Permanent";
        tvTotalPeriod.setText(totalPeriod);

        if (currentBudget.getCategoryId() == null) {
            tvGlobalBudget.setText("Global B... 🔔");
            tvGlobalBudget.setVisibility(View.VISIBLE);
        } else {
            tvGlobalBudget.setVisibility(View.GONE);
        }
    }

    private void calculateCurrentPeriod() {
        currentPeriodStart = Calendar.getInstance();
        currentPeriodEnd = Calendar.getInstance();

        String budgetType = currentBudget.getBudgetType();

        switch (budgetType) {
            case "daily":
                // Same day
                break;
            case "weekly":
                currentPeriodStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                currentPeriodEnd.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                break;
            case "monthly":
                currentPeriodStart.set(Calendar.DAY_OF_MONTH, 1);
                currentPeriodEnd.set(Calendar.DAY_OF_MONTH,
                        currentPeriodEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
                break;
            case "yearly":
                currentPeriodStart.set(Calendar.DAY_OF_YEAR, 1);
                currentPeriodEnd.set(Calendar.DAY_OF_YEAR,
                        currentPeriodEnd.getActualMaximum(Calendar.DAY_OF_YEAR));
                break;
            case "custom":
                // Use budget's start and end dates
                // TODO: Parse budget dates
                break;
        }
    }

    private void navigatePeriod(int direction) {
        String budgetType = currentBudget.getBudgetType();

        switch (budgetType) {
            case "daily":
                currentPeriodStart.add(Calendar.DAY_OF_MONTH, direction);
                currentPeriodEnd.add(Calendar.DAY_OF_MONTH, direction);
                break;
            case "weekly":
                currentPeriodStart.add(Calendar.WEEK_OF_YEAR, direction);
                currentPeriodEnd.add(Calendar.WEEK_OF_YEAR, direction);
                break;
            case "monthly":
                currentPeriodStart.add(Calendar.MONTH, direction);
                currentPeriodEnd.add(Calendar.MONTH, direction);
                currentPeriodEnd.set(Calendar.DAY_OF_MONTH,
                        currentPeriodEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
                break;
            case "yearly":
                currentPeriodStart.add(Calendar.YEAR, direction);
                currentPeriodEnd.add(Calendar.YEAR, direction);
                break;
        }

        updatePeriodDisplay();
    }

    private void updatePeriodDisplay() {
        String periodText = dateFormat.format(currentPeriodStart.getTime()) +
                " - " +
                dateFormat.format(currentPeriodEnd.getTime());
        tvCurrentPeriod.setText(periodText);

        // Calculate days left
        Calendar today = Calendar.getInstance();
        long diffMillis = currentPeriodEnd.getTimeInMillis() - today.getTimeInMillis();
        long daysLeft = diffMillis / (1000 * 60 * 60 * 24);
        tvDaysLeft.setText(daysLeft + "d left");

        // Load transaction data for current period
        loadTransactionData();
    }

    private void loadTransactionData() {
        long startMillis = currentPeriodStart.getTimeInMillis();
        long endMillis = currentPeriodEnd.getTimeInMillis();

        Executors.newSingleThreadExecutor().execute(() -> {
            // TODO: Query transactions for current period
            double totalSpent = transactionDao.getTotalExpenseSince(startMillis);

            requireActivity().runOnUiThread(() -> {
                double budgetAmount = currentBudget.getBudgetAmount();

                // Update spent amount
                tvSpentAmount.setText(df.format(totalSpent) + " / " + df.format(budgetAmount));

                // Calculate and update progress
                int progress = budgetAmount > 0 ? (int) ((totalSpent / budgetAmount) * 100) : 0;
                if (progress > 100) progress = 100;
                progressCircular.setProgress(progress);
                tvProgressPercentage.setText(progress + "%");

                // Calculate daily average
                Calendar today = Calendar.getInstance();
                long daysPassed = (today.getTimeInMillis() - startMillis) / (1000 * 60 * 60 * 24) + 1;
                double dailyAvg = daysPassed > 0 ? totalSpent / daysPassed : 0;
                tvDailyAverage.setText("Daily:" + formatShortAmount(dailyAvg));
            });
        });
    }

    private String formatShortAmount(double amount) {
        if (amount >= 1000000) {
            return String.format("%.2fM", amount / 1000000);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000);
        } else {
            return df.format(amount);
        }
    }

    private String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}