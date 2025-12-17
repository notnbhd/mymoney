package com.example.mymoney.savingGoal;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.MainActivity;
import com.example.mymoney.R;
import com.example.mymoney.adapter.SavingGoalAdapter;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.BudgetDao;
import com.example.mymoney.database.dao.CategoryDao;
import com.example.mymoney.database.dao.SavingGoalDao;
import com.example.mymoney.database.entity.Budget;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.model.SavingGoal;
import com.example.mymoney.savingGoal.AutoSavingGoal;
import com.example.mymoney.savingGoal.ManualSavingGoal;
import com.example.mymoney.savingGoal.SavingHistoryFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class SavingGoalFragment extends Fragment {

    private static final String TAG = "SavingGoalFragment";

    private RecyclerView recyclerSavingGoals;
    private SavingGoalAdapter adapter;
    private List<SavingGoal> goalList = new ArrayList<>();
    private ImageView btnAddGoal;
    private Button btnSavingHistory;

    // Database
    private AppDatabase database;
    private SavingGoalDao savingGoalDao;
    private CategoryDao categoryDao;
    private BudgetDao budgetDao;

    // Budget preferences for limit storage
    private SharedPreferences budgetPrefs;

    // ============================
    // Wizard temp variables
    // ============================
    private String tempGoalName;
    private long tempGoalAmount;
    private int tempMonths;
    private long tempIncome;

    // Dynamic category limits (categoryId -> limit amount)
    private Map<Integer, Long> tempCategoryLimits = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_saving_goal, container, false);

        // Initialize database
        database = AppDatabase.getInstance(requireContext());
        savingGoalDao = database.savingGoalDao();
        categoryDao = database.categoryDao();
        budgetDao = database.budgetDao();
        budgetPrefs = requireContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);

        btnSavingHistory = view.findViewById(R.id.btnSavingHistory);
        btnSavingHistory.setOnClickListener(v -> openSavingHistory());

        recyclerSavingGoals = view.findViewById(R.id.recyclerSavingGoals);
        btnAddGoal = view.findViewById(R.id.btnAddGoal);

        adapter = new SavingGoalAdapter(goalList, goal -> {
            if (goal.getType().equals("manual")) {
                openProgressScreen(goal);
            } else {
                // Open BudgetFragment for auto mode
                budgetPrefs.edit().putString("current_goal_name", goal.getName()).apply();
                openBudgetFragmentFromList(goal);
            }
        });

        recyclerSavingGoals.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerSavingGoals.setAdapter(adapter);

        loadGoalsFromDatabase();
        btnAddGoal.setOnClickListener(v -> showAddGoalDialog());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadGoalsFromDatabase();
    }

    // ============================================================
    // DATABASE OPERATIONS
    // ============================================================

    private void loadGoalsFromDatabase() {
        int userId = MainActivity.getCurrentUserId();
        int walletId = MainActivity.getSelectedWalletId();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<com.example.mymoney.database.entity.SavingGoal> dbGoals;

                if (walletId > 0) {
                    dbGoals = savingGoalDao.getSavingGoalsByUserAndWallet(userId, walletId);
                } else {
                    dbGoals = savingGoalDao.getSavingGoalsByUserId(userId);
                }

                List<SavingGoal> uiGoals = new ArrayList<>();
                for (com.example.mymoney.database.entity.SavingGoal dbGoal : dbGoals) {
                    uiGoals.add(new SavingGoal(
                            dbGoal.getId(),
                            dbGoal.getName(),
                            (long) dbGoal.getTarget(),
                            (long) dbGoal.getCurrentAmount(),
                            dbGoal.getDescription() != null && dbGoal.getDescription().equals("auto") ? "auto" : "manual",
                            dbGoal.getUpdatedAt(),
                            dbGoal.getUserId(),
                            dbGoal.getWalletId(),
                            dbGoal.getStatus()
                    ));
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        goalList.clear();
                        goalList.addAll(uiGoals);
                        adapter.notifyDataSetChanged();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading goals", e);
            }
        });
    }

    /**
     * Show confirmation dialog before deleting a saving goal
     */
    private void showDeleteGoalConfirmDialog(SavingGoal goal) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa mục tiêu tiết kiệm")
                .setMessage("Bạn có chắc muốn xóa mục tiêu \"" + goal.getName() + "\"?\n\nThao tác này không thể hoàn tác.")
                .setPositiveButton("Xóa", (dialog, which) -> deleteGoal(goal))
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Delete a saving goal from database and related data
     */
    private void deleteGoal(SavingGoal goal) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String goalName = goal.getName();

                // Delete related budgets (budgets named "goalName - categoryName")
                budgetDao.deleteByNamePattern(goalName + " - %");
                Log.d(TAG, "Deleted budgets for goal: " + goalName);

                // Delete from database
                savingGoalDao.deleteById(goal.getId());

                // Clear related SharedPreferences data
                SharedPreferences.Editor editor = budgetPrefs.edit();

                // Remove all prefs related to this goal
                editor.remove(goalName + "_start");
                editor.remove(goalName + "_target");
                editor.remove(goalName + "_months");
                editor.remove(goalName + "_income");
                editor.remove(goalName + "_savingPerMonth");
                editor.remove(goalName + "_maxExpensePerMonth");
                editor.remove(goalName + "_savedManual");
                editor.remove(goalName + "_summary");
                editor.remove(goalName + "_isSaving");

                // Remove category limits
                List<Category> categories = categoryDao.getAllExpenseCategories();
                for (Category cat : categories) {
                    editor.remove(goalName + "_limit_" + cat.getName());
                }

                editor.apply();

                Log.d(TAG, "Deleted goal: " + goalName);

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Đã xóa mục tiêu \"" + goalName + "\"", Toast.LENGTH_SHORT).show();
                        loadGoalsFromDatabase();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting goal", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Lỗi xóa mục tiêu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void saveGoalToDatabase(String name, long targetAmount, String type,
                                    Map<Integer, Long> categoryLimits, Runnable onComplete) {
        int userId = MainActivity.getCurrentUserId();
        int walletId = MainActivity.getSelectedWalletId();

        if (walletId <= 0) {
            Toast.makeText(getContext(), "Vui lòng chọn ví trước khi tạo mục tiêu tiết kiệm", Toast.LENGTH_SHORT).show();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Create SavingGoal
                com.example.mymoney.database.entity.SavingGoal dbGoal = new com.example.mymoney.database.entity.SavingGoal();
                dbGoal.setName(name);
                dbGoal.setTarget(targetAmount);
                dbGoal.setCurrentAmount(0);
                dbGoal.setUserId(userId);
                dbGoal.setWalletId(walletId);
                dbGoal.setCategoryId(null);  // Saving goals don't require a category
                dbGoal.setDescription(type); // Store type in description field
                dbGoal.setStatus("active");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                String today = sdf.format(new Date());
                dbGoal.setStartDate(today);

                // Calculate end date based on tempMonths
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.add(java.util.Calendar.MONTH, tempMonths);
                String endDate = sdf.format(cal.getTime());
                dbGoal.setEndDate(endDate);

                long goalId = savingGoalDao.insert(dbGoal);
                Log.d(TAG, "Saved goal with ID: " + goalId);

                // Create Budget entries for each category limit
                if (categoryLimits != null && !categoryLimits.isEmpty()) {
                    int budgetCount = createBudgetsForGoal(userId, walletId, name, categoryLimits, today, endDate);
                    Log.d(TAG, "Created " + budgetCount + " budgets for goal: " + name);
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        loadGoalsFromDatabase();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error saving goal", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Lỗi lưu mục tiêu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    /**
     * Create Budget entries for each category based on the limits set by user or recommendation engine
     * Only creates budgets for categories with limit > 0
     */
    private int createBudgetsForGoal(int userId, int walletId, String goalName,
                                     Map<Integer, Long> categoryLimits,
                                     String startDate, String endDate) {
        int count = 0;

        for (Map.Entry<Integer, Long> entry : categoryLimits.entrySet()) {
            int categoryId = entry.getKey();
            long limitAmount = entry.getValue();

            // Skip categories with 0 or negative limit
            if (limitAmount <= 0) {
                continue;
            }

            // Get category name for budget naming
            Category category = categoryDao.getCategoryById(categoryId);
            String categoryName = category != null ? category.getName() : "Category " + categoryId;

            Budget budget = new Budget();
            budget.setUserId(userId);
            budget.setWalletId(walletId);
            budget.setCategoryId(categoryId);
            budget.setName(goalName + " - " + categoryName);  // Link budget name to goal
            budget.setBudgetAmount(limitAmount);
            budget.setBudgetType("monthly");
            budget.setPeriodUnit("monthly");
            budget.setStartDate(startDate);
            budget.setEndDate(endDate);
            budget.setAlertThreshold(80.0);  // Default 80% alert threshold

            budgetDao.insert(budget);
            count++;
        }

        return count;
    }

    // ============================================================
    // STEP 1 — Enter goal name
    // ============================================================
    private void showAddGoalDialog() {
        View view = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_goal_step1, null);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(view)
                .create();

        EditText editGoalName = view.findViewById(R.id.editGoalName);
        Button btnNext = view.findViewById(R.id.btnNextStep);

        btnNext.setOnClickListener(v -> {
            String name = editGoalName.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(getContext(), "Bạn chưa nhập tên mục tiết kiệm", Toast.LENGTH_SHORT).show();
                return;
            }

            tempGoalName = name;
            dialog.dismiss();
            showBasicSavingInfoDialog();
        });

        dialog.show();
    }

    // ============================================================
    // STEP 2 — Enter target amount, months, income
    // ============================================================
    private void showBasicSavingInfoDialog() {
        View view = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_saving_basic, null);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(view)
                .create();

        EditText inputGoalAmount = view.findViewById(R.id.inputGoalAmount);
        EditText inputMonths = view.findViewById(R.id.inputMonths);
        EditText inputIncome = view.findViewById(R.id.inputSalary);

        Button btnNext = view.findViewById(R.id.btnBasicNext);

        btnNext.setOnClickListener(v -> {
            if (inputGoalAmount.getText().toString().isEmpty()
                    || inputMonths.getText().toString().isEmpty()
                    || inputIncome.getText().toString().isEmpty()) {

                Toast.makeText(getContext(), "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
                return;
            }

            tempGoalAmount = Long.parseLong(inputGoalAmount.getText().toString());
            tempMonths = Integer.parseInt(inputMonths.getText().toString());
            tempIncome = Long.parseLong(inputIncome.getText().toString());

            dialog.dismiss();
            showChooseMethodDialog();
        });

        dialog.show();
    }

    // ============================================================
    // STEP 3 — Choose method (manual/auto)
    // ============================================================
    private void showChooseMethodDialog() {
        View view = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_choose_method, null);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(view)
                .create();

        LinearLayout optionManual = view.findViewById(R.id.optionManual);
        LinearLayout optionAuto = view.findViewById(R.id.optionAuto);

        optionManual.setOnClickListener(v -> {
            dialog.dismiss();
            showDynamicLimitDialog();
        });

        optionAuto.setOnClickListener(v -> {
            dialog.dismiss();
            openBudgetFragment();
        });

        dialog.show();
    }

    // ============================================================
    // STEP 4 — Dynamic category limits from database
    // ============================================================
    private void showDynamicLimitDialog() {
        // Load categories from database
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<Category> expenseCategories = categoryDao.getAllExpenseCategories();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        showDynamicLimitDialogWithCategories(expenseCategories);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading categories", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), "Lỗi tải danh mục: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void showDynamicLimitDialogWithCategories(List<Category> categories) {
        // Create dialog programmatically with all categories
        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(40, 40, 40, 40);

        // Title
        TextView title = new TextView(getContext());
        title.setText("Nhập giới hạn chi tiêu");
        title.setTextSize(18);
        title.setTextColor(0xFFFF4081);
        title.setPadding(0, 0, 0, 40);
        container.addView(title);

        // Map to store EditText references by category ID
        Map<Integer, EditText> editTextMap = new HashMap<>();

        // Create input for each expense category
        for (Category category : categories) {
            LinearLayout itemLayout = new LinearLayout(getContext());
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(20, 20, 20, 20);

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            itemParams.setMargins(0, 0, 0, 24);
            itemLayout.setLayoutParams(itemParams);
            itemLayout.setBackgroundResource(R.drawable.rounded_option);

            // Category name label
            TextView label = new TextView(getContext());
            label.setText(getCategoryDisplayName(category.getName()));
            label.setTextColor(0xFFFF4081);
            label.setTextSize(14);
            itemLayout.addView(label);

            // EditText for limit
            EditText editLimit = new EditText(getContext());
            editLimit.setHint("Nhập giới hạn...");
            editLimit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            editLimit.setBackgroundResource(R.drawable.rounded_edittext);
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            editParams.setMargins(0, 16, 0, 0);
            editLimit.setLayoutParams(editParams);
            itemLayout.addView(editLimit);

            editTextMap.put(category.getId(), editLimit);
            container.addView(itemLayout);
        }

        // Start saving button
        Button btnStart = new Button(getContext());
        btnStart.setText("Bắt đầu tiết kiệm");
        btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF80AB));
        btnStart.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(0, 40, 0, 0);
        btnStart.setLayoutParams(btnParams);
        container.addView(btnStart);

        // Wrap in ScrollView
        android.widget.ScrollView scrollView = new android.widget.ScrollView(getContext());
        scrollView.addView(container);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(scrollView)
                .create();

        btnStart.setOnClickListener(v -> {
            // Collect only the limits that user entered (not all required)
            tempCategoryLimits.clear();

            for (Map.Entry<Integer, EditText> entry : editTextMap.entrySet()) {
                String text = entry.getValue().getText().toString().trim();
                if (!text.isEmpty()) {
                    try {
                        long limit = Long.parseLong(text);
                        if (limit > 0) {
                            tempCategoryLimits.put(entry.getKey(), limit);
                        }
                    } catch (NumberFormatException e) {
                        // Skip invalid input
                    }
                }
            }

            // Save limits to SharedPreferences (only for categories that have limits)
            SharedPreferences.Editor editor = budgetPrefs.edit();
            long now = System.currentTimeMillis();

            for (Category category : categories) {
                Long limit = tempCategoryLimits.get(category.getId());
                if (limit != null && limit > 0) {
                    editor.putLong(tempGoalName + "_limit_" + category.getName(), limit);
                }
            }
            editor.putLong(tempGoalName + "_start", now);
            editor.apply();

            dialog.dismiss();

            // Save goal to database with category budgets (only non-empty ones), then open progress screen
            saveGoalToDatabase(tempGoalName, tempGoalAmount, "manual", tempCategoryLimits, () -> {
                // Find the newly created goal from the list
                for (SavingGoal goal : goalList) {
                    if (goal.getName().equals(tempGoalName)) {
                        openProgressScreen(goal);
                        break;
                    }
                }
            });
        });

        dialog.show();
    }

    // Helper to get display name for categories
    private String getCategoryDisplayName(String name) {
        switch (name.toLowerCase()) {
            case "food": return "Ăn uống";
            case "home": return "Nhà cửa";
            case "transport": return "Đi lại";
            case "relationship": return "Tình yêu / Quan hệ";
            case "entertainment": return "Giải trí";
            case "medical": return "Y tế";
            case "tax": return "Thuế";
            case "gym & fitness": return "Thể dục & Gym";
            case "beauty": return "Làm đẹp";
            case "clothing": return "Quần áo";
            case "education": return "Giáo dục";
            case "childcare": return "Chăm sóc con cái";
            case "groceries": return "Mua sắm";
            case "others": return "Khác";
            default: return name;
        }
    }

    // ============================================================
    // Open Progress Screen
    // ============================================================
    private void openProgressScreen(SavingGoal goal) {
        Fragment fragment = ManualSavingGoal.newInstance(goal.getName(), goal.getTargetAmount());
        budgetPrefs.edit().putString("current_goal_name", goal.getName()).apply();

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    // ============================================================
    // Open Budget Fragment (Auto Mode)
    // ============================================================
    private void openBudgetFragment() {
        // Set start time if not exists
        if (!budgetPrefs.contains(tempGoalName + "_start")) {
            budgetPrefs.edit()
                    .putLong(tempGoalName + "_start", System.currentTimeMillis())
                    .apply();
        }

        // For auto mode, budgets will be created by the recommendation engine in BudgetFrag
        // Pass null for categoryLimits - the BudgetFrag will handle budget creation
        saveGoalToDatabase(tempGoalName, tempGoalAmount, "auto", null, () -> {
            AutoSavingGoal fragment = AutoSavingGoal.newInstance(
                    tempGoalName,
                    tempGoalAmount,
                    tempMonths,
                    tempIncome
            );

            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        });
    }

    private void openBudgetFragmentFromList(SavingGoal goal) {
        long target = budgetPrefs.getLong(goal.getName() + "_target", goal.getTargetAmount());
        long months = budgetPrefs.getLong(goal.getName() + "_months", 1);
        long income = budgetPrefs.getLong(goal.getName() + "_income", 0);

        AutoSavingGoal fragment = AutoSavingGoal.newInstance(
                goal.getName(),
                target,
                months,
                income
        );

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void openSavingHistory() {
        Fragment fragment = new SavingHistoryFragment();
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    // ============================================================
    // Static method to update saved amount
    // ============================================================
    public static void updateSavedInGoalList(Context context, String goalName, long newSaved) {
        AppDatabase db = AppDatabase.getInstance(context);
        int userId = MainActivity.getCurrentUserId();
        int walletId = MainActivity.getSelectedWalletId();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                com.example.mymoney.database.entity.SavingGoal goal =
                        db.savingGoalDao().getSavingGoalByName(userId, walletId, goalName);

                if (goal != null) {
                    goal.setCurrentAmount(newSaved);
                    goal.setUpdatedAt(System.currentTimeMillis());
                    db.savingGoalDao().update(goal);
                    Log.d("SavingGoalFragment", "Updated goal '" + goalName + "' saved amount to: " + newSaved);
                }
            } catch (Exception e) {
                Log.e("SavingGoalFragment", "Error updating goal saved amount", e);
            }
        });
    }
}