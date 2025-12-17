package com.example.mymoney;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.adapter.DailyTransactionGroupAdapter;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Transaction;
import com.example.mymoney.database.entity.Wallet;
import com.example.mymoney.model.DailyTransactionGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private TextView balanceAmount;

    private TextView expensesAmount;
    private TextView incomesAmount;
    private RecyclerView recentTransactionsRecyclerView;
    private DailyTransactionGroupAdapter dailyGroupAdapter;
    private FloatingActionButton fabAddTransaction;
    // Period selector views
    private TextView periodToday;
    private TextView periodThisWeek;
    private TextView periodThisMonth;
    private TextView periodThisYear;
    private TextView periodCustom;

    // Current selected period
    private enum Period { TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, CUSTOM }
    private Period currentPeriod = Period.THIS_MONTH;
    private long customStartDate = 0;
    private long customEndDate = 0;
    // ===== SAVING GOALS =====
    private TextView tvGoalName1, tvGoalName2;
    private TextView tvGoalPercent1, tvGoalPercent2;
    private ProgressBar progressGoal1, progressGoal2;
    private View layoutGoal2;
    private View layoutGoal1Circle;
    private TextView tvNoSavingGoal;



    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // === TEXT SUMMARY (NEW UI) ===
        balanceAmount = view.findViewById(R.id.balance_amount);
        expensesAmount = view.findViewById(R.id.expenses_amount);
        incomesAmount = view.findViewById(R.id.incomes_amount);

        recentTransactionsRecyclerView =
                view.findViewById(R.id.recent_transactions_recycler_view);
        fabAddTransaction = view.findViewById(R.id.fab_add_transaction);

        // === PERIOD SELECTOR ===
        periodToday = view.findViewById(R.id.period_today);
        periodThisWeek = view.findViewById(R.id.period_this_week);
        periodThisMonth = view.findViewById(R.id.period_this_month);
        periodThisYear = view.findViewById(R.id.period_this_year);
        periodCustom = view.findViewById(R.id.period_custom);
        // ===== SAVING GOAL VIEWS =====
        tvGoalName1 = view.findViewById(R.id.tv_goal_name_1);
        tvGoalName2 = view.findViewById(R.id.tv_goal_name_2);

        tvGoalPercent1 = view.findViewById(R.id.tv_goal_percent_1);
        tvGoalPercent2 = view.findViewById(R.id.tv_goal_percent_2);

        progressGoal1 = view.findViewById(R.id.progress_goal_1);
        progressGoal2 = view.findViewById(R.id.progress_goal_2);
        layoutGoal2 = view.findViewById(R.id.layout_goal_2);
        layoutGoal1Circle = view.findViewById(R.id.layoutGoal1);
        tvNoSavingGoal = view.findViewById(R.id.tv_no_saving_goal);


        setupRecyclerView();
        setupFab();
        setupPeriodSelector();
        loadWalletData();
    }

    private void setupPeriodSelector() {
        periodToday.setOnClickListener(v -> selectPeriod(Period.TODAY));
        periodThisWeek.setOnClickListener(v -> selectPeriod(Period.THIS_WEEK));
        periodThisMonth.setOnClickListener(v -> selectPeriod(Period.THIS_MONTH));
        periodThisYear.setOnClickListener(v -> selectPeriod(Period.THIS_YEAR));
        periodCustom.setOnClickListener(v -> showCustomDatePicker());

        // Set initial selection
        updatePeriodSelectorUI();
    }

    private void selectPeriod(Period period) {
        currentPeriod = period;
        updatePeriodSelectorUI();
        loadWalletData();
    }

    private void updatePeriodSelectorUI() {
        // Reset all to unselected state
        resetPeriodButton(periodToday);
        resetPeriodButton(periodThisWeek);
        resetPeriodButton(periodThisMonth);
        resetPeriodButton(periodThisYear);
        resetPeriodButton(periodCustom);

        // Set selected state for current period
        TextView selectedButton = null;
        switch (currentPeriod) {
            case TODAY:
                selectedButton = periodToday;
                break;
            case THIS_WEEK:
                selectedButton = periodThisWeek;
                break;
            case THIS_MONTH:
                selectedButton = periodThisMonth;
                break;
            case THIS_YEAR:
                selectedButton = periodThisYear;
                break;
            case CUSTOM:
                selectedButton = periodCustom;
                break;
        }

        if (selectedButton != null) {
            selectedButton.setBackgroundResource(R.drawable.period_selector_bg_selected);
            selectedButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        }
    }

    private void resetPeriodButton(TextView button) {
        button.setBackgroundResource(R.drawable.period_selector_bg);
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_text));
    }

    private void showCustomDatePicker() {
        Calendar calendar = Calendar.getInstance();

        // Show start date picker first
        DatePickerDialog startDatePicker = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    Calendar startCal = Calendar.getInstance();
                    startCal.set(year, month, dayOfMonth, 0, 0, 0);
                    startCal.set(Calendar.MILLISECOND, 0);
                    customStartDate = startCal.getTimeInMillis();

                    // Show end date picker
                    DatePickerDialog endDatePicker = new DatePickerDialog(
                            requireContext(),
                            (view2, year2, month2, dayOfMonth2) -> {
                                Calendar endCal = Calendar.getInstance();
                                endCal.set(year2, month2, dayOfMonth2, 23, 59, 59);
                                endCal.set(Calendar.MILLISECOND, 999);
                                customEndDate = endCal.getTimeInMillis();

                                currentPeriod = Period.CUSTOM;
                                updatePeriodSelectorUI();

                                // Update custom button text to show date range
                                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM", Locale.getDefault());
                                String dateRange = sdf.format(new Date(customStartDate)) + " - " + sdf.format(new Date(customEndDate));
                                periodCustom.setText(dateRange);

                                loadWalletData();
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                    );
                    endDatePicker.setTitle(getString(R.string.filter_to_date));
                    endDatePicker.show();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        startDatePicker.setTitle(getString(R.string.filter_from_date));
        startDatePicker.show();
    }

    private long[] getDateRangeForPeriod() {
        Calendar calendar = Calendar.getInstance();
        long startDate, endDate;

        switch (currentPeriod) {
            case TODAY:
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startDate = calendar.getTimeInMillis();

                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                endDate = calendar.getTimeInMillis();
                break;

            case THIS_WEEK:
                // Get start of week (Monday)
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                // If today is Sunday and first day of week is Monday, we need to go back
                if (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    calendar.add(Calendar.WEEK_OF_YEAR, -1);
                }
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startDate = calendar.getTimeInMillis();

                // Get end of week (Sunday)
                calendar.add(Calendar.DAY_OF_MONTH, 6);
                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                endDate = calendar.getTimeInMillis();
                break;

            case THIS_MONTH:
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startDate = calendar.getTimeInMillis();

                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                endDate = calendar.getTimeInMillis();
                break;

            case THIS_YEAR:
                calendar.set(Calendar.DAY_OF_YEAR, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startDate = calendar.getTimeInMillis();

                calendar.set(Calendar.MONTH, Calendar.DECEMBER);
                calendar.set(Calendar.DAY_OF_MONTH, 31);
                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                endDate = calendar.getTimeInMillis();
                break;

            case CUSTOM:
                startDate = customStartDate;
                endDate = customEndDate;
                break;

            default:
                startDate = 0;
                endDate = System.currentTimeMillis();
        }

        return new long[]{startDate, endDate};
    }

    private void setupFab() {
        fabAddTransaction.setOnClickListener(v -> {
            // Add scale animation
            v.animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        v.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(100)
                                .start();
                    })
                    .start();

            // Navigate to ImportFragment
            if (getActivity() instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) getActivity();
                androidx.fragment.app.FragmentTransaction transaction =
                        mainActivity.getSupportFragmentManager().beginTransaction();
                transaction.setCustomAnimations(
                        R.anim.fade_in_up,
                        R.anim.fade_out_down,
                        R.anim.fade_in_up,
                        R.anim.fade_out_down
                );
                transaction.replace(R.id.fragment_container, new ImportFragment());
                transaction.addToBackStack(null);
                transaction.commit();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadWalletData();
    }

    private void setupRecyclerView() {
        recentTransactionsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        dailyGroupAdapter = new DailyTransactionGroupAdapter(
                AppDatabase.getInstance(requireContext()),
                transaction -> {
                    // Show transaction detail dialog
                    TransactionDetailDialog dialog = new TransactionDetailDialog(
                            getActivity(),
                            transaction,
                            new TransactionDetailDialog.OnTransactionActionListener() {
                                @Override
                                public void onDelete(Transaction transactionToDelete) {
                                    deleteTransaction(transactionToDelete);
                                }

                                @Override
                                public void onEdit(Transaction editedTransaction) {
                                    // Refresh data after edit
                                    loadWalletData();
                                }
                            }
                    );
                    dialog.show();
                }
        );
        recentTransactionsRecyclerView.setAdapter(dailyGroupAdapter);
    }

    private void loadWalletData() {

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                int userId = MainActivity.getCurrentUserId();
                int walletId = MainActivity.getSelectedWalletId();

                // ================= AUTO SELECT WALLET =================
                if (walletId == -1) {
                    List<Wallet> wallets =
                            db.walletDao().getActiveWalletsByUserId(userId);
                    if (!wallets.isEmpty()) {
                        walletId = wallets.get(0).getId();
                        MainActivity.setSelectedWalletId(walletId);
                    }
                }

                if (walletId == -1 || getActivity() == null) return;

                // ================= LOAD WALLET =================
                Wallet wallet = db.walletDao().getWalletById(walletId);

                // ================= LOAD SAVING GOALS =================
                List<com.example.mymoney.database.entity.SavingGoal> savingGoals =
                        db.savingGoalDao().getSavingGoalsByUserAndWallet(userId, walletId);

                // ================= PERIOD RANGE =================
                long[] range = getDateRangeForPeriod();
                long startDate = range[0];
                long endDate = range[1];

                // ================= LOAD EXPENSE / INCOME =================
                Double expenseResult =
                        db.transactionDao().getTotalExpensesByDateRange(userId, startDate, endDate);
                Double incomeResult =
                        db.transactionDao().getTotalIncomeByDateRange(userId, startDate, endDate);

                double totalExpense = expenseResult != null ? expenseResult : 0;
                double totalIncome = incomeResult != null ? incomeResult : 0;

                // ================= LOAD TRANSACTIONS =================
                List<Transaction> txs =
                        db.transactionDao().getTransactionsByWalletAndDateRange(
                                walletId, startDate, endDate);

                List<DailyTransactionGroup> groups = groupTransactionsByDate(txs);
                List<DailyTransactionGroup> recentGroups =
                        groups.size() > 5 ? groups.subList(0, 5) : groups;

                final String currency = MainActivity.getSelectedWalletCurrency();
                final Wallet finalWallet = wallet;
                final double fe = totalExpense;
                final double fi = totalIncome;
                final List<DailyTransactionGroup> fg = recentGroups;
                final List<com.example.mymoney.database.entity.SavingGoal> finalSavingGoals = savingGoals;

                // ================= UPDATE UI =================
                getActivity().runOnUiThread(() -> {

                    // ===== BALANCE / EXPENSE / INCOME (BỊ THIẾU TRƯỚC ĐÓ) =====
                    if (finalWallet != null) {
                        balanceAmount.setText(
                                String.format("Số dư: %,.0f %s",
                                        finalWallet.getBalance(), currency)
                        );
                    } else {
                        balanceAmount.setText("Số dư: 0 " + currency);
                    }

                    expensesAmount.setText(
                            String.format("Chi tiêu: -%,.0f %s", fe, currency)
                    );

                    incomesAmount.setText(
                            String.format("Thu nhập: +%,.0f %s", fi, currency)
                    );

                    // ===== SAVING GOALS =====
                    if (finalSavingGoals == null || finalSavingGoals.isEmpty()) {

                        tvNoSavingGoal.setVisibility(View.VISIBLE);

                        layoutGoal1Circle.setVisibility(View.GONE);
                        tvGoalName1.setVisibility(View.GONE);
                        layoutGoal2.setVisibility(View.GONE);

                    } else {

                        tvNoSavingGoal.setVisibility(View.GONE);

                        // GOAL 1
                        layoutGoal1Circle.setVisibility(View.VISIBLE);
                        tvGoalName1.setVisibility(View.VISIBLE);

                        var g1 = finalSavingGoals.get(0);
                        int percent1 = calcPercent(g1);

                        tvGoalName1.setText(g1.getName());
                        tvGoalPercent1.setText(percent1 + "%");
                        progressGoal1.setProgress(percent1);

                        // GOAL 2
                        if (finalSavingGoals.size() >= 2) {
                            layoutGoal2.setVisibility(View.VISIBLE);

                            var g2 = finalSavingGoals.get(1);
                            int percent2 = calcPercent(g2);

                            tvGoalName2.setText(g2.getName());
                            tvGoalPercent2.setText(percent2 + "%");
                            progressGoal2.setProgress(percent2);
                        } else {
                            layoutGoal2.setVisibility(View.GONE);
                        }
                    }

                    // ===== TRANSACTION LIST =====
                    dailyGroupAdapter.setDailyGroups(fg);
                });

            } catch (Exception e) {
                android.util.Log.e("HomeFragment", "loadWalletData error", e);
            }
        }).start();
    }


    /**
     * Group transactions by date
     */
    private List<DailyTransactionGroup> groupTransactionsByDate(List<Transaction> transactions) {
        // Use LinkedHashMap to maintain order
        Map<String, List<Transaction>> groupedMap = new LinkedHashMap<>();
        SimpleDateFormat dateKeyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat displayFormat = new SimpleDateFormat("EEE, dd/MM", Locale.getDefault());

        for (Transaction transaction : transactions) {
            Date date = new Date(transaction.getCreatedAt());
            String dateKey = dateKeyFormat.format(date);

            if (!groupedMap.containsKey(dateKey)) {
                groupedMap.put(dateKey, new ArrayList<>());
            }
            groupedMap.get(dateKey).add(transaction);
        }

        // Convert to list of DailyTransactionGroup
        List<DailyTransactionGroup> dailyGroups = new ArrayList<>();
        for (Map.Entry<String, List<Transaction>> entry : groupedMap.entrySet()) {
            String dateKey = entry.getKey();
            List<Transaction> dayTransactions = entry.getValue();

            if (!dayTransactions.isEmpty()) {
                long timestamp = dayTransactions.get(0).getCreatedAt();
                Date date = new Date(timestamp);
                String displayDate = displayFormat.format(date);

                // Get day of week for full date
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                String fullDate = new SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()).format(date);

                DailyTransactionGroup group = new DailyTransactionGroup(
                        displayDate,
                        fullDate,
                        timestamp,
                        dayTransactions
                );
                dailyGroups.add(group);
            }
        }

        return dailyGroups;
    }

    public void refreshData() {
        loadWalletData();
    }

    private void deleteTransaction(Transaction transaction) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());

                // Update wallet balance
                Wallet wallet = db.walletDao().getWalletById(transaction.getWalletId());
                if (wallet != null) {
                    double amount = transaction.getAmount();
                    if ("income".equals(transaction.getType())) {
                        wallet.setBalance(wallet.getBalance() - amount);
                    } else {
                        wallet.setBalance(wallet.getBalance() + amount);
                    }
                    db.walletDao().update(wallet);
                }

                // Delete transaction
                db.transactionDao().delete(transaction);

                // Refresh UI
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        loadWalletData();
                        android.widget.Toast.makeText(requireContext(),
                                "Transaction deleted",
                                android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("HomeFragment", "Error deleting transaction", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        android.widget.Toast.makeText(requireContext(),
                                "Failed to delete transaction",
                                android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }
    private int calcPercent(com.example.mymoney.database.entity.SavingGoal goal) {
        if (goal.getTarget() <= 0) return 0;

        int percent = (int) ((goal.getCurrentAmount() * 100f) / goal.getTarget());

        return Math.min(percent, 100); // không vượt quá 100%
    }

}