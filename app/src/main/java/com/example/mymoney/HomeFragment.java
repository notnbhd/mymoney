package com.example.mymoney;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
        
        setupRecyclerView();
        setupFab();
        
        loadWalletData();
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

                    double totalExpenses = db.transactionDao().getTotalExpensesByWallet(walletId);
                    double totalIncomes = db.transactionDao().getTotalIncomeByWallet(walletId);
                    
                    List<Transaction> allTransactions = db.transactionDao().getTransactionsByWalletId(walletId);
                    
                    List<DailyTransactionGroup> dailyGroups = groupTransactionsByDate(allTransactions);
                    
                    // Limit to 5 days
                    List<DailyTransactionGroup> recentGroups = dailyGroups.size() > 5 
                        ? dailyGroups.subList(0, 5) 
                        : dailyGroups;
                    
                    android.util.Log.d("HomeFragment", "Loading wallet ID: " + walletId + 
                        ", daily groups: " + recentGroups.size());
                    
                    // Update UI on main thread
                    if (getActivity() != null) {
                        final Wallet finalWallet = wallet;
                        final double finalExpenses = totalExpenses;
                        final double finalIncomes = totalIncomes;
                        final List<DailyTransactionGroup> finalGroups = recentGroups;
                        
                        getActivity().runOnUiThread(() -> {
                            if (finalWallet != null) {
                                balanceAmount.setText(String.format(Locale.getDefault(),
                                        "%,.2f %s", finalWallet.getBalance(), finalWallet.getCurrency()));
                            } else {
                                balanceAmount.setText("0 VND");
                            }
                            
                            expensesAmount.setText(String.format(Locale.getDefault(), 
                                "-%,.2f VND", finalExpenses));
                            incomesAmount.setText(String.format(Locale.getDefault(), 
                                "+%,.2f VND", finalIncomes));
                            
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
                        getActivity().runOnUiThread(() -> {
                            balanceAmount.setText("0 VND");
                            expensesAmount.setText("-0 VND");
                            incomesAmount.setText("+0 VND");
                            
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