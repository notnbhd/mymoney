package com.example.mymoney;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.adapter.TransactionAdapter;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.database.entity.Transaction;
import com.example.mymoney.database.entity.Wallet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private RecyclerView transactionsRecyclerView;
    private TransactionAdapter adapter;
    private EditText searchEditText;
    private ImageView filterIcon;

    private List<Transaction> allTransactions = new ArrayList<>();

    // Filter state
    private String filterType = "all"; // "all", "expense", "income"
    private int filterCategoryId = -1; // -1 means all categories
    private long filterStartDate = 0;
    private long filterEndDate = 0;
    private List<Category> categoryList = new ArrayList<>();

    @Override
    public void onAttach(@NonNull Context context) {
        // ✅ Đây là nơi áp dụng ngôn ngữ cho Fragment
        super.onAttach(LocaleHelper.onAttach(context));
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        transactionsRecyclerView = view.findViewById(R.id.transactions_recycler_view);
        searchEditText = view.findViewById(R.id.search_edit_text);
        filterIcon = view.findViewById(R.id.filter_icon);

        // Set up RecyclerView
        setupRecyclerView();

        // Set up search functionality
        setupSearch();

        // Load transactions
        loadTransactions();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload transactions when fragment becomes visible
        loadTransactions();
    }

    private void setupRecyclerView() {
        transactionsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TransactionAdapter(
                AppDatabase.getInstance(requireContext()),
                transaction -> {
                    // Show transaction detail dialog with edit capability
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
                                    loadTransactions();
                                }
                            }
                    );
                    dialog.show();
                }
        );
        transactionsRecyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTransactions(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // TODO: Implement filter functionality
        filterIcon.setOnClickListener(v -> showFilterDialog());

    }

    private void loadTransactions() {
        android.util.Log.d("HistoryFragment", "loadTransactions() called - Current user: " + MainActivity.getCurrentUserId() + ", Selected wallet: " + MainActivity.getSelectedWalletId());

        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                int walletId = MainActivity.getSelectedWalletId();

                // If no wallet selected, get all transactions for user
                List<Transaction> transactions;
                if (walletId == -1) {
                    transactions = db.transactionDao().getTransactionsByUserId(MainActivity.getCurrentUserId());
                    android.util.Log.d("HistoryFragment", "Loading all transactions for user " + MainActivity.getCurrentUserId() + ": " + transactions.size() + " found");
                } else {
                    transactions = db.transactionDao().getTransactionsByWalletId(walletId);
                    android.util.Log.d("HistoryFragment", "Loading transactions for wallet " + walletId + ": " + transactions.size() + " found");
                }

                allTransactions = transactions;

                // Update UI on main thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.setTransactions(transactions);
                        android.util.Log.d("HistoryFragment", "Loaded " + transactions.size() + " transactions");
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("HistoryFragment", "Error loading transactions", e);
                e.printStackTrace();
            }
        }).start();
    }

    private void filterTransactions(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.setTransactions(allTransactions);
            return;
        }

        // Filter transactions by description or amount
        new Thread(() -> {
            try {
                List<Transaction> filtered = new ArrayList<>();
                String lowerQuery = query.toLowerCase();

                AppDatabase db = AppDatabase.getInstance(requireContext());

                for (Transaction transaction : allTransactions) {
                    // Check description
                    if (transaction.getDescription() != null &&
                            transaction.getDescription().toLowerCase().contains(lowerQuery)) {
                        filtered.add(transaction);
                        continue;
                    }

                    // Check amount
                    String amountStr = String.valueOf((int) transaction.getAmount());
                    if (amountStr.contains(query)) {
                        filtered.add(transaction);
                        continue;
                    }

                    // Check category name
                    com.example.mymoney.database.entity.Category category =
                            db.categoryDao().getCategoryById(transaction.getCategoryId());
                    if (category != null &&
                            category.getName().toLowerCase().contains(lowerQuery)) {
                        filtered.add(transaction);
                    }
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.setTransactions(filtered);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Public method to refresh data from outside (e.g., after importing transaction)
     */
    public void refreshData() {
        android.util.Log.d("HistoryFragment", "refreshData() called from MainActivity");
        loadTransactions();
    }

    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // Initialize views
        RadioGroup typeRadioGroup = dialogView.findViewById(R.id.type_radio_group);
        Spinner categorySpinner = dialogView.findViewById(R.id.category_spinner);
        TextView fromDateText = dialogView.findViewById(R.id.from_date_text);
        TextView toDateText = dialogView.findViewById(R.id.to_date_text);
        View resetButton = dialogView.findViewById(R.id.reset_button);
        View applyButton = dialogView.findViewById(R.id.apply_button);

        // Set up type radio buttons based on current filter
        switch (filterType) {
            case "expense":
                typeRadioGroup.check(R.id.radio_expense);
                break;
            case "income":
                typeRadioGroup.check(R.id.radio_income);
                break;
            default:
                typeRadioGroup.check(R.id.radio_all);
                break;
        }

        // Load categories
        loadCategoriesForFilter(categorySpinner);

        // Set up date pickers
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        if (filterStartDate > 0) {
            fromDateText.setText(sdf.format(new Date(filterStartDate)));
        }
        if (filterEndDate > 0) {
            toDateText.setText(sdf.format(new Date(filterEndDate)));
        }

        fromDateText.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (filterStartDate > 0) {
                cal.setTimeInMillis(filterStartDate);
            }

            DatePickerDialog datePicker = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        Calendar selectedCal = Calendar.getInstance();
                        selectedCal.set(year, month, dayOfMonth, 0, 0, 0);
                        selectedCal.set(Calendar.MILLISECOND, 0);
                        filterStartDate = selectedCal.getTimeInMillis();
                        fromDateText.setText(sdf.format(new Date(filterStartDate)));
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.show();
        });

        toDateText.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (filterEndDate > 0) {
                cal.setTimeInMillis(filterEndDate);
            }

            DatePickerDialog datePicker = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        Calendar selectedCal = Calendar.getInstance();
                        selectedCal.set(year, month, dayOfMonth, 23, 59, 59);
                        selectedCal.set(Calendar.MILLISECOND, 999);
                        filterEndDate = selectedCal.getTimeInMillis();
                        toDateText.setText(sdf.format(new Date(filterEndDate)));
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.show();
        });

        // Reset button
        resetButton.setOnClickListener(v -> {
            filterType = "all";
            filterCategoryId = -1;
            filterStartDate = 0;
            filterEndDate = 0;

            typeRadioGroup.check(R.id.radio_all);
            categorySpinner.setSelection(0);
            fromDateText.setText("");
            toDateText.setText("");

            applyFilters();
            dialog.dismiss();
        });

        // Apply button
        applyButton.setOnClickListener(v -> {
            // Get type filter
            int checkedId = typeRadioGroup.getCheckedRadioButtonId();
            if (checkedId == R.id.radio_expense) {
                filterType = "expense";
            } else if (checkedId == R.id.radio_income) {
                filterType = "income";
            } else {
                filterType = "all";
            }

            // Get category filter
            int categoryPosition = categorySpinner.getSelectedItemPosition();
            if (categoryPosition > 0 && categoryPosition <= categoryList.size()) {
                filterCategoryId = categoryList.get(categoryPosition - 1).getId();
            } else {
                filterCategoryId = -1;
            }

            applyFilters();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void loadCategoriesForFilter(Spinner categorySpinner) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                categoryList = db.categoryDao().getAllCategories();

                // Create category names list with "All Categories" option
                List<String> categoryNames = new ArrayList<>();
                categoryNames.add(getString(R.string.all_categories));

                for (Category category : categoryList) {
                    categoryNames.add(category.getName());
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                requireContext(),
                                android.R.layout.simple_spinner_item,
                                categoryNames
                        );
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        categorySpinner.setAdapter(adapter);

                        // Set current selection
                        if (filterCategoryId != -1) {
                            for (int i = 0; i < categoryList.size(); i++) {
                                if (categoryList.get(i).getId() == filterCategoryId) {
                                    categorySpinner.setSelection(i + 1);
                                    break;
                                }
                            }
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("HistoryFragment", "Error loading categories", e);
            }
        }).start();
    }

    private void applyFilters() {
        new Thread(() -> {
            try {
                List<Transaction> filtered = new ArrayList<>();

                for (Transaction transaction : allTransactions) {
                    // Type filter
                    if (!filterType.equals("all")) {
                        if (!filterType.equalsIgnoreCase(transaction.getType())) {
                            continue;
                        }
                    }

                    // Category filter
                    if (filterCategoryId != -1) {
                        if (transaction.getCategoryId() != filterCategoryId) {
                            continue;
                        }
                    }

                    // Date range filter
                    if (filterStartDate > 0) {
                        if (transaction.getCreatedAt() < filterStartDate) {
                            continue;
                        }
                    }

                    if (filterEndDate > 0) {
                        if (transaction.getCreatedAt() > filterEndDate) {
                            continue;
                        }
                    }

                    filtered.add(transaction);
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> adapter.setTransactions(filtered));
                }
            } catch (Exception e) {
                android.util.Log.e("HistoryFragment", "Error applying filters", e);
            }
        }).start();
    }

    private void deleteTransaction(Transaction transaction) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());

                // Update wallet balance before deleting
                Wallet wallet = db.walletDao().getWalletById(transaction.getWalletId());
                if (wallet != null) {
                    double currentBalance = wallet.getBalance();
                    double newBalance;
                    if ("income".equals(transaction.getType())) {
                        // Reverse the income
                        newBalance = currentBalance - transaction.getAmount();
                    } else {
                        // Reverse the expense
                        newBalance = currentBalance + transaction.getAmount();
                    }
                    db.walletDao().updateBalance(wallet.getId(), newBalance, System.currentTimeMillis());
                }

                // Delete the transaction
                db.transactionDao().delete(transaction);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        android.widget.Toast.makeText(requireContext(),
                                "Transaction deleted", android.widget.Toast.LENGTH_SHORT).show();
                        loadTransactions();
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("HistoryFragment", "Error deleting transaction", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        android.widget.Toast.makeText(requireContext(),
                                "Error deleting transaction", android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

}