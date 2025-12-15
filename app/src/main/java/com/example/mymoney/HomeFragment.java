package com.example.mymoney;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.adapter.DailyTransactionGroupAdapter;
import com.example.mymoney.adapter.TransactionAdapter;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Transaction;
import com.example.mymoney.database.entity.Wallet;
import com.example.mymoney.model.DailyTransactionGroup;
import com.example.mymoney.view.HalfDoughnutChartView;
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
    private TextView balanceDate;
    private TextView expensesAmount;
    private TextView incomesAmount;
    private RecyclerView recentTransactionsRecyclerView;
    private DailyTransactionGroupAdapter dailyGroupAdapter;
    private FloatingActionButton fabAddTransaction;
    private HalfDoughnutChartView halfDoughnutChart;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        balanceAmount = view.findViewById(R.id.balance_amount);
        balanceDate = view.findViewById(R.id.balance_date);
        expensesAmount = view.findViewById(R.id.expenses_amount);
        incomesAmount = view.findViewById(R.id.incomes_amount);
        recentTransactionsRecyclerView = view.findViewById(R.id.recent_transactions_recycler_view);
        fabAddTransaction = view.findViewById(R.id.fab_add_transaction);
        halfDoughnutChart = view.findViewById(R.id.half_doughnut_chart);
        
        // Initialize period selector views
        periodToday = view.findViewById(R.id.period_today);
        periodThisWeek = view.findViewById(R.id.period_this_week);
        periodThisMonth = view.findViewById(R.id.period_this_month);
        periodThisYear = view.findViewById(R.id.period_this_year);
        periodCustom = view.findViewById(R.id.period_custom);
        
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
        android.util.Log.d("HomeFragment", "loadWalletData() called - Current user: " + MainActivity.getCurrentUserId() + ", Selected wallet: " + MainActivity.getSelectedWalletId());

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                int walletId = MainActivity.getSelectedWalletId();

                // If no wallet selected, use the first available wallet
                if (walletId == -1) {
                    List<Wallet> wallets = db.walletDao().getActiveWalletsByUserId(MainActivity.getCurrentUserId());
                    android.util.Log.d("HomeFragment", "No wallet selected. Found " + wallets.size() + " wallets for user " + MainActivity.getCurrentUserId());
                    if (!wallets.isEmpty()) {
                        walletId = wallets.get(0).getId();
                        MainActivity.setSelectedWalletId(walletId);
                        android.util.Log.d("HomeFragment", "Auto-selected first wallet: ID " + walletId);
                    }
                }

                if (walletId != -1) {
                    Wallet wallet = db.walletDao().getWalletById(walletId);

                    // Get date range based on selected period
                    long[] dateRange = getDateRangeForPeriod();
                    long startDate = dateRange[0];
                    long endDate = dateRange[1];
                    
                    // Get expenses and income for the selected period
                    Double totalExpensesResult = db.transactionDao().getTotalExpensesByDateRange(MainActivity.getCurrentUserId(), startDate, endDate);
                    Double totalIncomesResult = db.transactionDao().getTotalIncomeByDateRange(MainActivity.getCurrentUserId(), startDate, endDate);
                    double totalExpenses = totalExpensesResult != null ? totalExpensesResult : 0;
                    double totalIncomes = totalIncomesResult != null ? totalIncomesResult : 0;
                    
                    // Get transactions for the selected period
                    List<Transaction> periodTransactions = db.transactionDao().getTransactionsByWalletAndDateRange(walletId, startDate, endDate);
                    
                    List<DailyTransactionGroup> dailyGroups = groupTransactionsByDate(periodTransactions);
                    
                    // Limit to 5 days
                    List<DailyTransactionGroup> recentGroups = dailyGroups.size() > 5 
                        ? dailyGroups.subList(0, 5) 
                        : dailyGroups;
                    
                    android.util.Log.d("HomeFragment", "Loading wallet ID: " + walletId + 
                        ", daily groups: " + recentGroups.size() + ", period: " + currentPeriod);
                    
                    // Update UI on main thread
                    if (getActivity() != null) {
                        final Wallet finalWallet = wallet;
                        final double finalExpenses = totalExpenses;
                        final double finalIncomes = totalIncomes;
                        final List<DailyTransactionGroup> finalGroups = recentGroups;
                        final String currency = MainActivity.getSelectedWalletCurrency();
                        
                        getActivity().runOnUiThread(() -> {
                            if (finalWallet != null) {
                                balanceAmount.setText(String.format(Locale.getDefault(),
                                        "%,.2f %s", finalWallet.getBalance(), currency));
                            } else {
                                balanceAmount.setText("0 " + currency);
                            }
                            
                            expensesAmount.setText(String.format(Locale.getDefault(), 
                                "-%,.2f %s", finalExpenses, currency));
                            incomesAmount.setText(String.format(Locale.getDefault(), 
                                "+%,.2f %s", finalIncomes, currency));
                            
                            // Update the chart
                            halfDoughnutChart.setData(finalExpenses, finalIncomes);
                            
                            // Set current date
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                            balanceDate.setText(sdf.format(new Date()));
                            
                            // Update recent transactions (grouped by date)
                            dailyGroupAdapter.setDailyGroups(finalGroups);
                            
                            android.util.Log.d("HomeFragment", "UI updated with " + finalGroups.size() + " daily groups");
                        });
                    }
                } else {
                    android.util.Log.d("HomeFragment", "No wallet available for user " + MainActivity.getCurrentUserId());
                    if (getActivity() != null) {
                        final String currency = MainActivity.getSelectedWalletCurrency();
                        getActivity().runOnUiThread(() -> {
                            balanceAmount.setText("0 " + currency);
                            expensesAmount.setText("-0 " + currency);
                            incomesAmount.setText("+0 " + currency);
                            
                            // Update chart with zero values
                            halfDoughnutChart.setData(0, 0);
                            
                            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                            balanceDate.setText(sdf.format(new Date()));
                            
                            dailyGroupAdapter.setDailyGroups(new ArrayList<>());
                        });
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("HomeFragment", "Error loading wallet data", e);
                e.printStackTrace();
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
}