package com.example.mymoney;

import static com.example.mymoney.MainActivity.getCurrentUserId;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.BudgetDao;
import com.example.mymoney.database.dao.CategoryDao;
import com.example.mymoney.database.dao.TransactionDao;
import com.example.mymoney.database.entity.Budget;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.model.CategoryExpense;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class BudgetFrag extends Fragment {

    // ==== Views ====
    private LinearLayout layoutSavingSection;

    private EditText edtSavedMoney;
    private TextView tvResult, tvSavingPercent;

    private Button btnEndSaving, btnUpdateSaved;

    private ProgressBar progressSaving;
    private TextView tvWarning;


    // ==== Data ====
    private SharedPreferences prefs;
    private TransactionDao transactionDao;
    private BudgetDao budgetDao;
    private CategoryDao categoryDao;
    private String goalName = "";
    private List<Category> expenseCategories;

    private final DecimalFormat df = new DecimalFormat("#,###");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.frag_budget, container, false);

        // ==== MAP VIEW ====
        layoutSavingSection = view.findViewById(R.id.layout_saving_section);
        tvResult = view.findViewById(R.id.tv_budget_result);
        tvSavingPercent = view.findViewById(R.id.tvSavingPercent);

        btnEndSaving = view.findViewById(R.id.btn_end_saving);
        btnUpdateSaved = view.findViewById(R.id.btn_update_saved);
        edtSavedMoney = view.findViewById(R.id.edt_saved_money);
        progressSaving = view.findViewById(R.id.progressSaving);
        tvWarning = view.findViewById(R.id.tvWarning);


        // ==== INIT ====
        prefs = requireContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);
        AppDatabase db = AppDatabase.getInstance(requireContext());
        transactionDao = db.transactionDao();
        budgetDao = db.budgetDao();
        categoryDao = db.categoryDao();

        // Ẩn mặc định
        hideAll();

        // Lấy tham số
        Bundle args = getArguments();
        if (args != null) {
            goalName = args.getString("goalName", "");
        }

        // CHỈ KHỞI TẠO START_TIME NẾU CHƯA TỒN TẠI
        if (!prefs.contains(goalName + "_start")) {
            prefs.edit().putLong(goalName + "_start", System.currentTimeMillis()).apply();
        }

        // Xử lý auto mode (tính budget)
        if (args != null
                && args.containsKey("target_arg")
                && !prefs.getBoolean(goalName + "_isSaving", false)) {

            long target = args.getLong("target_arg");
            long months = args.getLong("months_arg");
            long income = args.getLong("income_arg");

            Executors.newSingleThreadExecutor().execute(() -> {
                calculateBudget(target, months, income);
                requireActivity().runOnUiThread(this::loadSavedPlan);
            });

        } else if (prefs.getBoolean(goalName + "_isSaving", false)) {
            loadSavedPlan();
        }


        // ==== BUTTON HANDLER ====
        btnUpdateSaved.setOnClickListener(v -> updateSavedMoney());
        btnEndSaving.setOnClickListener(v -> endSavingAction());
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (prefs.getBoolean(goalName + "_isSaving", false)) {
            loadSavedPlan(); // đã rebuild bên trong
        }
    }


    private void hideAll() {
        layoutSavingSection.setVisibility(View.GONE);
        btnEndSaving.setVisibility(View.GONE);
        btnUpdateSaved.setVisibility(View.GONE);
        edtSavedMoney.setVisibility(View.GONE);
        progressSaving.setVisibility(View.GONE);
    }

    // ============================================================
    // MAIN CALCULATE FUNCTION
    // ============================================================
    // DEPRECATED: Keep for backwards compatibility with prefs lookup
    private static final String[] CATEGORIES = {
            "Food",
            "Home",
            "Transport",
            "Relationship",
            "Entertainment"
    };

    private void calculateBudget(long target, long months, long income) {

        long targetVal = floorToThousand(target);
        long monthsVal = months;
        long incomeVal = floorToThousand(income);

        long savingPerMonth = floorToThousand((double) targetVal / monthsVal);
        long maxExpensePerMonth = floorToThousand(incomeVal - savingPerMonth);

        // =====================================================
        // 0️⃣ LOAD EXPENSE CATEGORIES FROM DATABASE
        // =====================================================
        expenseCategories = categoryDao.getAllExpenseCategories();
        if (expenseCategories == null || expenseCategories.isEmpty()) {
            android.util.Log.w("BudgetFrag", "No expense categories found in database");
            return;
        }

        // =====================================================
        // 1️⃣ XÁC ĐỊNH MỐC 3 THÁNG TRƯỚC
        // =====================================================
        long startTime = prefs.getLong(goalName + "_start", 0);

        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(startTime);
        startCal.set(Calendar.DAY_OF_MONTH, 1);
        long startMonthStart = startCal.getTimeInMillis();

        Calendar fromCal = Calendar.getInstance();
        fromCal.setTimeInMillis(startMonthStart);
        fromCal.add(Calendar.MONTH, -3);
        long fromDate = fromCal.getTimeInMillis();

        int userId = getCurrentUserId();
        int walletId = MainActivity.getSelectedWalletId();

        // =====================================================
        // 2️⃣ LẤY THÓI QUEN CHI TIÊU 3 THÁNG
        // =====================================================
        List<CategoryExpense> habitList =
                transactionDao.getExpensesByCategoryBetween(
                        fromDate,
                        startMonthStart,
                        userId
                );

        Map<String, Long> habitMap = new HashMap<>();
        double totalExpense3M = 0;

        for (CategoryExpense ce : habitList) {
            long v = floorToThousand(ce.total);
            habitMap.put(ce.category, v);
            totalExpense3M += v;
        }

        if (totalExpense3M <= 0) totalExpense3M = 1;

        // =====================================================
        // 3️⃣ LƯU THÔNG TIN KẾ HOẠCH
        // =====================================================
        SharedPreferences.Editor editor = prefs.edit();

        editor.putLong(goalName + "_target", targetVal);
        editor.putLong(goalName + "_months", monthsVal);
        editor.putLong(goalName + "_income", incomeVal);
        editor.putLong(goalName + "_savingPerMonth", savingPerMonth);
        editor.putLong(goalName + "_maxExpensePerMonth", maxExpensePerMonth);

        // Calculate start/end dates for budgets
        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(startTime);
        endCal.add(Calendar.MONTH, (int) monthsVal);
        String startDateStr = dateFormat.format(new Date(startTime));
        String endDateStr = dateFormat.format(endCal.getTime());

        // Map to store limits for each category (categoryId -> limit)
        Map<Integer, Long> categoryLimits = new HashMap<>();

        // =====================================================
        // 4️⃣ TÍNH LIMIT CHO TẤT CẢ DANH MỤC (QUAN TRỌNG)
        // =====================================================
        for (Category category : expenseCategories) {
            String categoryName = category.getName();
            int categoryId = category.getId();

            long habit = habitMap.getOrDefault(categoryName, 0L); // ⭐ DB không có → 0
            double ratio = habit / totalExpense3M;
            long limit = floorToThousand(ratio * maxExpensePerMonth);

            // Save to SharedPreferences (backwards compatibility)
            editor.putLong(goalName + "_limit_" + categoryName, limit);
            
            // Store for budget creation
            categoryLimits.put(categoryId, limit);
        }

        editor.commit(); // commit để đảm bảo dữ liệu đã ghi

        // =====================================================
        // 4.5️⃣ CREATE BUDGET ENTITIES IN DATABASE
        // =====================================================
        int budgetsCreated = createBudgetsFromLimits(userId, walletId, categoryLimits, startDateStr, endDateStr);
        android.util.Log.d("BudgetFrag", "Created " + budgetsCreated + " budget entries for goal: " + goalName);

        // =====================================================
        // 5️⃣ LẤY CHI TIÊU KỂ TỪ KHI BẮT ĐẦU TIẾT KIỆM
        // =====================================================
        List<CategoryExpense> spentList =
                transactionDao.getExpensesByCategorySince(startTime, userId);

        Map<String, Long> spentMap = new HashMap<>();
        for (CategoryExpense ce : spentList) {
            spentMap.put(ce.category, (long) ce.total);

        }

        // =====================================================
        // 6️⃣ BUILD SUMMARY (HIỂN THỊ ĐỦ CATEGORY)
        // =====================================================
        StringBuilder sb = new StringBuilder();
        sb.append("<b>🎯 Kế hoạch tiết kiệm</b><br><br>");
        sb.append("Mục tiêu: ").append(df.format(targetVal)).append(" VND<br>");
        sb.append("Thời gian: ").append(monthsVal).append(" tháng<br>");
        sb.append("Lương: ").append(df.format(incomeVal)).append(" VND<br><br>");

        sb.append("Cần tiết kiệm mỗi tháng: ")
                .append(df.format(savingPerMonth)).append(" VND<br>");
        sb.append("Được tiêu tối đa tháng này: ")
                .append(df.format(maxExpensePerMonth)).append(" VND<br><br>");

        sb.append("<b>🚀 Giới hạn theo thói quen (3 tháng trước):</b><br>");

        for (Category category : expenseCategories) {
            String categoryName = category.getName();
            long spent = spentMap.getOrDefault(categoryName, 0L);
            long limit = prefs.getLong(goalName + "_limit_" + categoryName, 0);

            sb.append("• ").append(categoryName).append(": ")
                    .append(df.format(spent))
                    .append(" / ")
                    .append(df.format(limit))
                    .append(" VND<br>");
        }

        prefs.edit()
                .putString(goalName + "_summary", sb.toString())
                .putBoolean(goalName + "_isSaving", true)
                .apply();
    }

    /**
     * Creates Budget entities in the database for each category limit
     */
    private int createBudgetsFromLimits(int userId, int walletId,
                                         Map<Integer, Long> categoryLimits,
                                         String startDate, String endDate) {
        int count = 0;
        
        for (Map.Entry<Integer, Long> entry : categoryLimits.entrySet()) {
            int categoryId = entry.getKey();
            long limitAmount = entry.getValue();
            
            // Skip categories with 0 limit
            if (limitAmount <= 0) continue;
            
            // Find category name
            String categoryName = "";
            for (Category cat : expenseCategories) {
                if (cat.getId() == categoryId) {
                    categoryName = cat.getName();
                    break;
                }
            }
            
            // Check if a budget already exists for this goal + category
            String budgetName = goalName + " - " + categoryName;
            Budget existingBudget = budgetDao.getBudgetByName(budgetName);
            
            if (existingBudget != null) {
                // Update existing budget
                existingBudget.setBudgetAmount(limitAmount);
                existingBudget.setStartDate(startDate);
                existingBudget.setEndDate(endDate);
                existingBudget.setUpdatedAt(System.currentTimeMillis());
                budgetDao.update(existingBudget);
            } else {
                // Create new budget
                Budget budget = new Budget();
                budget.setUserId(userId);
                budget.setWalletId(walletId);
                budget.setCategoryId(categoryId);
                budget.setName(budgetName);
                budget.setBudgetAmount(limitAmount);
                budget.setBudgetType("custom");
                budget.setPeriodUnit("month");
                budget.setStartDate(startDate);
                budget.setEndDate(endDate);
                budget.setAlertThreshold(0.8); // 80% warning
                budget.setCreatedAt(System.currentTimeMillis());
                budget.setUpdatedAt(System.currentTimeMillis());
                
                budgetDao.insert(budget);
            }
            count++;
        }
        
        return count;
    }




    // ============================================================
    // LOAD SAVED PLAN
    // ============================================================
    private void loadSavedPlan() {

        Executors.newSingleThreadExecutor().execute(() -> {

            rebuildSummary();
            checkSavingProgress(); // 🔥 BẮT BUỘC PHẢI CÓ

            requireActivity().runOnUiThread(() -> {

                String summary = prefs.getString(goalName + "_summary", "");

                layoutSavingSection.setVisibility(View.VISIBLE);
                btnEndSaving.setVisibility(View.VISIBLE);
                btnUpdateSaved.setVisibility(View.VISIBLE);
                edtSavedMoney.setVisibility(View.VISIBLE);
                progressSaving.setVisibility(View.VISIBLE);

                long saved = prefs.getLong(goalName + "_savedManual", 0);
                long startTime = prefs.getLong(goalName + "_start", 0);

                String startDate = dateFormat.format(new Date(startTime));

                String fullText = summary +
                        "<br><b>Bắt đầu tiết kiệm:</b> " + startDate +
                        "<br><b>Đã tiết kiệm:</b> " + df.format(saved) + " VND";

                tvResult.setText(android.text.Html.fromHtml(fullText));
                tvResult.setGravity(Gravity.START);

                long target = prefs.getLong(goalName + "_target", 0);
                int percent = target == 0 ? 0 : (int) ((saved * 100) / target);
                progressSaving.setProgress(Math.min(percent, 100));
                tvSavingPercent.setText(percent + "%");
            });
        });
    }



    // ============================================================
    // UPDATE SAVED MONEY
    // ============================================================
    private void updateSavedMoney() {

        String savedStr = edtSavedMoney.getText().toString().trim();
        if (savedStr.isEmpty()) return;

        long added = floorToThousand(Long.parseLong(savedStr));
        long current = prefs.getLong(goalName + "_savedManual", 0);
        long newTotal = current + added;

        // Lưu vào budget_prefs
        prefs.edit().putLong(goalName + "_savedManual", newTotal).apply();

        // ⭐⭐⭐ LƯU NGƯỢC LẠI VÀO DANH SÁCH NGOÀI ⭐⭐⭐
        SavingGoalFragment.updateSavedInGoalList(requireContext(), goalName, newTotal);

        edtSavedMoney.setText("");
        loadSavedPlan();
    }



    // ============================================================
    // CHECK PROGRESS
    // ============================================================
    private void checkSavingProgress() {

        Map<String, Long> spentMap = getExpenseByCategoryForWarning();

        // Ensure expense categories are loaded
        if (expenseCategories == null || expenseCategories.isEmpty()) {
            expenseCategories = categoryDao.getAllExpenseCategories();
        }
        
        if (expenseCategories == null || expenseCategories.isEmpty()) {
            return; // No categories to check
        }

        boolean hasExceeded = false;
        StringBuilder warningDetail = new StringBuilder();

        for (Category category : expenseCategories) {
            String categoryName = category.getName();

            long spent = spentMap.getOrDefault(categoryName, 0L);
            long limit = prefs.getLong(goalName + "_limit_" + categoryName, 0);

            // 🔴 TRƯỜNG HỢP 1: LIMIT = 0 → CẤM CHI
            if (limit == 0 && spent > 0) {
                hasExceeded = true;
                warningDetail.append("• ")
                        .append(categoryName)
                        .append(": ")
                        .append(df.format(spent))
                        .append(" / 0 VND\n");
                continue;
            }

            // 🔴 TRƯỜNG HỢP 2: LIMIT > 0 → so bình thường
            if (limit > 0 && spent > limit) {
                hasExceeded = true;
                warningDetail.append("• ")
                        .append(categoryName)
                        .append(": ")
                        .append(df.format(spent))
                        .append(" / ")
                        .append(df.format(limit))
                        .append(" VND\n");
            }
        }

        boolean finalHasExceeded = hasExceeded;

        requireActivity().runOnUiThread(() -> {
            if (finalHasExceeded) {

                tvWarning.setVisibility(View.VISIBLE);
                tvWarning.setText("⚠ Một số danh mục đã vượt hạn mức!");

                new AlertDialog.Builder(requireContext())
                        .setTitle("⚠ Vượt hạn mức")
                        .setMessage(
                                "Các danh mục sau đã vượt hạn mức:\n\n" +
                                        warningDetail +
                                        "\n\nBạn có muốn chỉnh sửa hạn mức không?"
                        )
                        .setNegativeButton("Để sau", null)
                        .setPositiveButton("Chỉnh sửa", (dialog, which) -> {
                            showEditAllLimitsDialog(spentMap);
                        })

                        .show();

            } else {
                tvWarning.setVisibility(View.GONE);
            }
        });

    }

    // ============================================================
    private Map<String, Long> getExpenseByCategoryForWarning() {

        long startTime = prefs.getLong(goalName + "_start", 0);
        int userId = getCurrentUserId();

        // ✅ CHỈ LẤY TỪ LÚC BẮT ĐẦU TIẾT KIỆM
        List<CategoryExpense> expenses =
                transactionDao.getExpensesByCategorySince(startTime, userId);

        Map<String, Long> map = new HashMap<>();
        for (CategoryExpense ce : expenses) {
            map.put(ce.category, (long) ce.total); // ❌ KHÔNG floor
        }

        return map;
    }

    private long floorToThousand(double v) {
        return (long) (Math.floor(v / 1000) * 1000);
    }


    // ============================================================
    // END SAVING GOAL
    // ============================================================
    private void endSavingAction() {

        SharedPreferences goalPrefs =
                requireContext().getSharedPreferences("SAVING_GOALS", Context.MODE_PRIVATE);

        Set<String> set = new HashSet<>(goalPrefs.getStringSet("goal_list", new HashSet<>()));
        Set<String> newSet = new HashSet<>();

        for (String item : set) {
            if (!item.startsWith(goalName + "|")) newSet.add(item);
        }

        goalPrefs.edit().putStringSet("goal_list", newSet).apply();

        // Lưu lịch sử
        SharedPreferences historyPref =
                requireContext().getSharedPreferences("SAVING_HISTORY", Context.MODE_PRIVATE);

        Set<String> history = historyPref.getStringSet("history_list", new HashSet<>());

        long target = prefs.getLong(goalName + "_target", 0);
        long saved = prefs.getLong(goalName + "_savedManual", 0);
        long start = prefs.getLong(goalName + "_start", 0);
        long end = System.currentTimeMillis();

        history.add(goalName + "|" + target + "|" + saved + "|" + start + "|" + end + "|auto");

        historyPref.edit().putStringSet("history_list", history).apply();

        // Xóa dữ liệu riêng
        SharedPreferences.Editor ed = prefs.edit();
        ed.remove(goalName + "_target");
        ed.remove(goalName + "_months");
        ed.remove(goalName + "_income");
        ed.remove(goalName + "_savingPerMonth");
        ed.remove(goalName + "_maxExpensePerMonth");
        ed.remove(goalName + "_savedManual");
        ed.remove(goalName + "_summary");
        ed.remove(goalName + "_isSaving");
        ed.remove(goalName + "_start");
        ed.apply();

        new AlertDialog.Builder(requireContext())
                .setTitle("Đã kết thúc mục tiêu")
                .setMessage("Mục tiêu \"" + goalName + "\" đã được lưu vào lịch sử.")
                .setPositiveButton("OK", (dialog, which) ->
                        requireActivity().getSupportFragmentManager().popBackStack()
                )
                .show();
    }


    // ============================================================
    public static BudgetFrag newInstance(String goalName, long target, long months, long income) {
        BudgetFrag fragment = new BudgetFrag();
        Bundle args = new Bundle();
        args.putString("goalName", goalName);
        args.putLong("target_arg", target);
        args.putLong("months_arg", months);
        args.putLong("income_arg", income);
        fragment.setArguments(args);
        return fragment;
    }
    private void rebuildSummary() {

        long startTime = prefs.getLong(goalName + "_start", 0);
        long target = prefs.getLong(goalName + "_target", 0);
        long months = prefs.getLong(goalName + "_months", 0);
        long income = prefs.getLong(goalName + "_income", 0);
        long maxExpense = prefs.getLong(goalName + "_maxExpensePerMonth", 0);

        int userId = getCurrentUserId(); // ⭐ BẮT BUỘC

        // =========================
        // 1️⃣ LẤY CHI TIÊU HIỆN TẠI
        // =========================
        List<CategoryExpense> spentList =
                transactionDao.getExpensesByCategorySince(startTime, userId);

        Map<String, Long> spentMap = new HashMap<>();
        for (CategoryExpense ce : spentList) {
            spentMap.put(ce.category, (long) ce.total);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<b>🎯 Kế hoạch tiết kiệm</b><br><br>");
        sb.append("Mục tiêu: ").append(df.format(target)).append(" VND<br>");
        sb.append("Thời gian: ").append(months).append(" tháng<br>");
        sb.append("Lương: ").append(df.format(income)).append(" VND<br><br>");
        sb.append("Được tiêu tối đa tháng này: ")
                .append(df.format(maxExpense)).append(" VND<br><br>");

        sb.append("<b>🚀 Giới hạn sau khi điều chỉnh:</b><br>");

        // Ensure expense categories are loaded
        if (expenseCategories == null || expenseCategories.isEmpty()) {
            expenseCategories = categoryDao.getAllExpenseCategories();
        }
        
        if (expenseCategories != null) {
            for (Category category : expenseCategories) {
                String categoryName = category.getName();

                long spent = spentMap.getOrDefault(categoryName, 0L);

                // ⭐ AUTO MODE: LUÔN LẤY LIMIT, KHÔNG CÓ = 0
                long limit = prefs.getLong(goalName + "_limit_" + categoryName, 0);

                sb.append("• ").append(categoryName).append(": ")
                        .append(df.format(spent))
                        .append(" / ")
                        .append(df.format(limit))
                        .append(" VND");

                if (spent > limit && limit > 0) {
                    sb.append(" ⚠");
                }

                sb.append("<br>");
            }
        }

        prefs.edit()
                .putString(goalName + "_summary", sb.toString())
                .apply();
    }

    private void showEditAllLimitsDialog(Map<String, Long> spentMap) {

        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(32, 24, 32, 8);

        Map<String, EditText> editMap = new HashMap<>();

        // Ensure expense categories are loaded
        if (expenseCategories == null || expenseCategories.isEmpty()) {
            Executors.newSingleThreadExecutor().execute(() -> {
                expenseCategories = categoryDao.getAllExpenseCategories();
                requireActivity().runOnUiThread(() -> showEditAllLimitsDialog(spentMap));
            });
            return;
        }

        for (Category category : expenseCategories) {
            String categoryName = category.getName();

            long spent = spentMap.getOrDefault(categoryName, 0L);
            long limit = prefs.getLong(goalName + "_limit_" + categoryName, 0);

            // ===== Label =====
            TextView tv = new TextView(requireContext());
            tv.setText(categoryName + " (đã chi: " + df.format(spent) + " VND)");
            tv.setPadding(0, 16, 0, 4);
            tv.setTextSize(14);

            // ===== Input =====
            EditText edt = new EditText(requireContext());
            edt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            edt.setHint("Limit (VND)");
            edt.setText(String.valueOf(limit));

            // Highlight nếu vượt
            if ((limit == 0 && spent > 0) || (limit > 0 && spent > limit)) {
                tv.setTextColor(0xFFFF4444); // đỏ
            }

            container.addView(tv);
            container.addView(edt);

            editMap.put(categoryName, edt);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("✏️ Chỉnh sửa hạn mức")
                .setView(container)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu tất cả", (dialog, which) -> {

                    SharedPreferences.Editor editor = prefs.edit();

                    for (Category category : expenseCategories) {
                        String categoryName = category.getName();

                        EditText edt = editMap.get(categoryName);
                        if (edt == null) continue;

                        String val = edt.getText().toString().trim();
                        if (val.isEmpty()) continue;

                        long newLimit = floorToThousand(Long.parseLong(val));
                        editor.putLong(goalName + "_limit_" + categoryName, newLimit);
                    }

                    editor.apply();

                    Executors.newSingleThreadExecutor().execute(() -> {
                        rebuildSummary();
                        requireActivity().runOnUiThread(this::loadSavedPlan);
                    });
                })
                .show();
    }

}
