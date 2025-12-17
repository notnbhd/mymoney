package com.example.mymoney;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.budget.BudgetNotificationService;
import com.example.mymoney.budget.BudgetRuleEngine;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.BudgetDao;
import com.example.mymoney.database.dao.CategoryDao;
import com.example.mymoney.database.entity.Budget;
import com.example.mymoney.database.entity.Category;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class BudgetFragment extends Fragment {

    private RecyclerView rvBudgets;
    private LinearLayout layoutEmptyState;
    private TextView btnAddBudget;
    private ImageButton btnRefresh;

    private BudgetDao budgetDao;
    private CategoryDao categoryDao;
    private BudgetAdapter adapter;
    private List<Budget> budgetList = new ArrayList<>();
    private Map<Integer, Double> spentAmountsMap = new HashMap<>();
    private int lastWalletId = -1;
    private BudgetNotificationService notificationService;

    // For category spinner in dialog
    private List<Category> expenseCategories = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_budget, container, false);

        // Initialize views
        rvBudgets = view.findViewById(R.id.rv_budgets);
        layoutEmptyState = view.findViewById(R.id.layout_empty_state);
        btnAddBudget = view.findViewById(R.id.btn_add_budget);
        btnRefresh = view.findViewById(R.id.btn_refresh);

        // Initialize database
        budgetDao = AppDatabase.getInstance(requireContext()).budgetDao();
        categoryDao = AppDatabase.getInstance(requireContext()).categoryDao();

        // Initialize notification service
        notificationService = new BudgetNotificationService(requireContext());

        // Setup RecyclerView
        setupRecyclerView();

        // Load budgets
        loadBudgets();

        // Button listeners
        btnAddBudget.setOnClickListener(v -> showCreateBudgetDialog());
        btnRefresh.setOnClickListener(v -> loadBudgets());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh budgets when fragment becomes visible
        int currentWalletId = MainActivity.getSelectedWalletId();
        if (lastWalletId != currentWalletId) {
            lastWalletId = currentWalletId;
            loadBudgets();
        }
    }

    /**
     * Public method to refresh data (called by MainActivity when wallet changes)
     */
    public void refreshData() {
        lastWalletId = MainActivity.getSelectedWalletId();
        loadBudgets();
    }

    private void setupRecyclerView() {
        adapter = new BudgetAdapter(
                requireContext(),
                budgetList,
                budget -> {
                    // Budget clicked - detail screen removed for now
                    Toast.makeText(requireContext(), budget.getName() + ": $" +
                                    new java.text.DecimalFormat("#,###").format(spentAmountsMap.getOrDefault(budget.getId(), 0.0)) +
                                    " / $" + new java.text.DecimalFormat("#,###").format(budget.getBudgetAmount()),
                            Toast.LENGTH_SHORT).show();
                },
                budget -> {
                    // Delete budget
                    showDeleteConfirmDialog(budget);
                },
                spentAmountsMap
        );

        rvBudgets.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvBudgets.setAdapter(adapter);
    }

    private void loadBudgets() {
        Executors.newSingleThreadExecutor().execute(() -> {
            // Load budgets for current wallet
            int currentWalletId = MainActivity.getSelectedWalletId();
            List<Budget> budgets;
            if (currentWalletId > 0) {
                budgets = budgetDao.getBudgetsByWalletId(currentWalletId);
            } else {
                budgets = budgetDao.getAllBudgets();
            }

            // Pre-calculate expenses for each budget (filtered by wallet and category)
            Map<Integer, Double> expensesMap = new HashMap<>();
            AppDatabase db = AppDatabase.getInstance(requireContext());
            for (Budget budget : budgets) {
                long[] periodRange = BudgetAdapter.calculatePeriodRange(budget);
                int walletId = budget.getWalletId();
                Integer categoryId = budget.getCategoryId();

                double spent;
                if (categoryId != null && categoryId > 0) {
                    // Category-specific budget - only count expenses for this category
                    spent = db.transactionDao().getTotalExpenseBetweenForWalletAndCategory(
                            periodRange[0], periodRange[1], walletId, categoryId);
                } else {
                    // Global budget - count all expenses
                    spent = db.transactionDao().getTotalExpenseBetweenForWallet(
                            periodRange[0], periodRange[1], walletId);
                }
                expensesMap.put(budget.getId(), spent);

                // Debug logging
                String categoryInfo = (categoryId != null && categoryId > 0) ?
                        " CategoryID: " + categoryId : " (Global)";
                android.util.Log.d("BudgetFragment", "Budget: " + budget.getName() +
                        " (ID: " + budget.getId() + ", Type: " + budget.getBudgetType() +
                        ", WalletID: " + walletId + categoryInfo + ")" +
                        " | Period: " + new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(periodRange[0])) +
                        " to " + new java.text.SimpleDateFormat("MM/dd/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(periodRange[1])) +
                        " | Spent: $" + spent);
            }

            // Run rule-based analysis and check for notifications
            if (!budgets.isEmpty()) {
                BudgetRuleEngine.BudgetAnalysisResult analysisResult =
                        BudgetRuleEngine.analyzeBudgets(budgets, expensesMap);
                notificationService.checkAndNotify(analysisResult);
            }

            // Build category names map for display
            Map<Integer, String> categoryNamesMap = new HashMap<>();
            List<Category> allCategories = categoryDao.getAllCategories();
            for (Category category : allCategories) {
                categoryNamesMap.put(category.getId(), category.getName());
            }

            final Map<Integer, String> finalCategoryNamesMap = categoryNamesMap;
            requireActivity().runOnUiThread(() -> {
                budgetList.clear();
                budgetList.addAll(budgets);
                spentAmountsMap.clear();
                spentAmountsMap.putAll(expensesMap);
                adapter.setCategoryNamesMap(finalCategoryNamesMap);
                adapter.notifyDataSetChanged();

                if (budgets.isEmpty()) {
                    layoutEmptyState.setVisibility(View.VISIBLE);
                    rvBudgets.setVisibility(View.GONE);
                } else {
                    layoutEmptyState.setVisibility(View.GONE);
                    rvBudgets.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void showCreateBudgetDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_budget, null);
        dialog.setContentView(dialogView);

        // Get views from dialog
        android.widget.EditText edtBudgetName = dialogView.findViewById(R.id.edt_budget_name);
        android.widget.EditText edtBudgetAmount = dialogView.findViewById(R.id.edt_budget_amount);
        android.widget.EditText edtAlertThreshold = dialogView.findViewById(R.id.edt_alert_threshold);
        android.widget.RadioGroup rgPeriodType = dialogView.findViewById(R.id.rg_period_type);
        LinearLayout layoutCustomPeriod = dialogView.findViewById(R.id.layout_custom_period);
        TextView tvStartDate = dialogView.findViewById(R.id.tv_start_date);
        TextView tvEndDate = dialogView.findViewById(R.id.tv_end_date);
        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnCreate = dialogView.findViewById(R.id.btn_create);
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_category);

        final Calendar startCalendar = Calendar.getInstance();
        final Calendar endCalendar = Calendar.getInstance();

        // Load categories for spinner
        loadCategoriesForSpinner(spinnerCategory);

        // Show/hide custom period section
        rgPeriodType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_custom) {
                layoutCustomPeriod.setVisibility(View.VISIBLE);
            } else {
                layoutCustomPeriod.setVisibility(View.GONE);
            }
        });

        // Date pickers
        tvStartDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        startCalendar.set(year, month, dayOfMonth);
                        tvStartDate.setText(String.format("%02d/%02d/%d", month + 1, dayOfMonth, year));
                    },
                    startCalendar.get(Calendar.YEAR),
                    startCalendar.get(Calendar.MONTH),
                    startCalendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        tvEndDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        endCalendar.set(year, month, dayOfMonth);
                        tvEndDate.setText(String.format("%02d/%02d/%d", month + 1, dayOfMonth, year));
                    },
                    endCalendar.get(Calendar.YEAR),
                    endCalendar.get(Calendar.MONTH),
                    endCalendar.get(Calendar.DAY_OF_MONTH)
            );
            datePickerDialog.show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnCreate.setOnClickListener(v -> {
            try {
                String name = edtBudgetName.getText().toString().trim();
                String amountStr = edtBudgetAmount.getText().toString().trim();
                String thresholdStr = edtAlertThreshold.getText().toString().trim();

                if (name.isEmpty() || amountStr.isEmpty()) {
                    Toast.makeText(requireContext(), "Please fill in required fields", Toast.LENGTH_SHORT).show();
                    return;
                }

                double amount;
                try {
                    amount = Double.parseDouble(amountStr);
                    if (amount <= 0) {
                        Toast.makeText(requireContext(), "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Invalid amount format", Toast.LENGTH_SHORT).show();
                    return;
                }

                double threshold = 80.0;
                if (!thresholdStr.isEmpty()) {
                    try {
                        threshold = Double.parseDouble(thresholdStr);
                        if (threshold < 0 || threshold > 100) {
                            Toast.makeText(requireContext(), "Threshold must be between 0 and 100", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "Invalid threshold format", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                String periodType = "monthly"; // default
                int checkedId = rgPeriodType.getCheckedRadioButtonId();
                if (checkedId == R.id.rb_daily) periodType = "daily";
                else if (checkedId == R.id.rb_weekly) periodType = "weekly";
                else if (checkedId == R.id.rb_monthly) periodType = "monthly";
                else if (checkedId == R.id.rb_yearly) periodType = "yearly";
                else if (checkedId == R.id.rb_custom) periodType = "custom";

                // Validate custom period dates
                if (periodType.equals("custom")) {
                    String startDateText = tvStartDate.getText().toString();
                    String endDateText = tvEndDate.getText().toString();
                    if (startDateText.isEmpty() || endDateText.isEmpty() ||
                            startDateText.equals("Select start date") || endDateText.equals("Select end date")) {
                        Toast.makeText(requireContext(), "Please select start and end dates", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                Budget budget = new Budget();
                budget.setName(name);
                budget.setBudgetAmount(amount);
                budget.setBudgetType(periodType);
                budget.setAlertThreshold(threshold);

                if (periodType.equals("custom")) {
                    budget.setStartDate(tvStartDate.getText().toString());
                    budget.setEndDate(tvEndDate.getText().toString());
                } else {
                    // Set start date to today
                    Calendar today = Calendar.getInstance();
                    budget.setStartDate(String.format("%02d/%02d/%d",
                            today.get(Calendar.MONTH) + 1,
                            today.get(Calendar.DAY_OF_MONTH),
                            today.get(Calendar.YEAR)));
                }

                // Get current user and wallet IDs from MainActivity
                int userId = MainActivity.getCurrentUserId();
                int walletId = MainActivity.getSelectedWalletId();

                // Fallback to default if not set
                if (userId <= 0) userId = 1;
                if (walletId <= 0) walletId = 1;

                budget.setUserId(userId);
                budget.setWalletId(walletId);

                // Set category if selected (not "All Categories")
                int selectedCategoryPosition = spinnerCategory.getSelectedItemPosition();
                if (selectedCategoryPosition > 0 && selectedCategoryPosition <= expenseCategories.size()) {
                    Category selectedCategory = expenseCategories.get(selectedCategoryPosition - 1);
                    budget.setCategoryId(selectedCategory.getId());
                } else {
                    budget.setCategoryId(null); // Global budget (no specific category)
                }

                Executors.newSingleThreadExecutor().execute(() -> {
                    try {
                        budgetDao.insert(budget);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Budget created successfully", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadBudgets();
                        });
                    } catch (Exception e) {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Error creating budget: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void showDeleteConfirmDialog(Budget budget) {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Budget")
                .setMessage("Are you sure you want to delete \"" + budget.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        budgetDao.delete(budget);
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Budget deleted", Toast.LENGTH_SHORT).show();
                            loadBudgets();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Load expense categories for the spinner
     */
    private void loadCategoriesForSpinner(Spinner spinner) {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Category> categories = categoryDao.getAllExpenseCategories();

            requireActivity().runOnUiThread(() -> {
                expenseCategories.clear();
                expenseCategories.addAll(categories);

                // Create list with "All Categories" option first
                List<String> categoryNames = new ArrayList<>();
                categoryNames.add("All Categories (Global)");
                for (Category cat : categories) {
                    categoryNames.add(cat.getName());
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout.simple_spinner_item,
                        categoryNames
                );
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
            });
        });
    }
}