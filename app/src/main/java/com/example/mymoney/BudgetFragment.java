package com.example.mymoney;

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
import com.example.mymoney.database.dao.TransactionDao;
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

public class BudgetFragment extends Fragment {

    // ==== Views ====
    private LinearLayout layoutSavingSection;

    private EditText edtSavedMoney;
    private TextView tvResult, tvSavingPercent;

    private Button btnEndSaving, btnUpdateSaved, btnRecalc;

    private ProgressBar progressSaving;
    private TextView tvWarning;


    // ==== Data ====
    private SharedPreferences prefs;
    private TransactionDao transactionDao;
    private String goalName = "";

    private final DecimalFormat df = new DecimalFormat("#,###");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_budget, container, false);

        // ==== MAP VIEW ====
        layoutSavingSection = view.findViewById(R.id.layout_saving_section);
        tvResult = view.findViewById(R.id.tv_budget_result);
        tvSavingPercent = view.findViewById(R.id.tvSavingPercent);

        btnEndSaving = view.findViewById(R.id.btn_end_saving);
        btnUpdateSaved = view.findViewById(R.id.btn_update_saved);
        btnRecalc = view.findViewById(R.id.btn_recalc_budget);

        edtSavedMoney = view.findViewById(R.id.edt_saved_money);
        progressSaving = view.findViewById(R.id.progressSaving);
        tvWarning = view.findViewById(R.id.tvWarning);


        // ==== INIT ====
        prefs = requireContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);
        transactionDao = AppDatabase.getInstance(requireContext()).transactionDao();

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
        if (args != null && args.containsKey("target_arg")) {

            long target = args.getLong("target_arg");
            long months = args.getLong("months_arg");
            long income = args.getLong("income_arg");

            Executors.newSingleThreadExecutor().execute(() -> {
                calculateBudget(target, months, income);

                requireActivity().runOnUiThread(this::loadSavedPlan);
            });

        } else {
            if (prefs.getBoolean(goalName + "_isSaving", false)) {
                loadSavedPlan();
            }
        }

        // ==== BUTTON HANDLER ====
        btnUpdateSaved.setOnClickListener(v -> updateSavedMoney());
        btnEndSaving.setOnClickListener(v -> endSavingAction());
        btnRecalc.setOnClickListener(v -> recalcBudgetAutomatically());
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (prefs.getBoolean(goalName + "_isSaving", false)) {
            loadSavedPlan();
        }
    }

    private void hideAll() {
        layoutSavingSection.setVisibility(View.GONE);
        btnEndSaving.setVisibility(View.GONE);
        btnUpdateSaved.setVisibility(View.GONE);
        edtSavedMoney.setVisibility(View.GONE);
        progressSaving.setVisibility(View.GONE);
        btnRecalc.setVisibility(View.GONE);
    }

    // ============================================================
    // MAIN CALCULATE FUNCTION
    // ============================================================
    private void calculateBudget(long target, long months, long income) {

        long targetVal = floorToThousand(target);
        long monthsVal = months;
        long incomeVal = floorToThousand(income);

        long savingPerMonth = floorToThousand((double) targetVal / monthsVal);
        long maxExpensePerMonth = floorToThousand(incomeVal - savingPerMonth);

        // Lấy chi tiêu 3 tháng gần nhất
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -3);
        long fromDate = cal.getTimeInMillis();

        List<CategoryExpense> expenses = transactionDao.getExpensesByCategorySince(fromDate);

        double totalExpense3M = 0;
        for (CategoryExpense ce : expenses) totalExpense3M += ce.total;

        long totalSpent = floorToThousand(totalExpense3M);
        if (totalSpent <= 0) totalSpent = 1;

        // Lưu dữ liệu
        SharedPreferences.Editor editor = prefs.edit();

        editor.putLong(goalName + "_target", targetVal);
        editor.putLong(goalName + "_months", monthsVal);
        editor.putLong(goalName + "_income", incomeVal);
        editor.putLong(goalName + "_savingPerMonth", savingPerMonth);
        editor.putLong(goalName + "_maxExpensePerMonth", maxExpensePerMonth);

        // Tính limit từng category
        for (CategoryExpense ce : expenses) {
            double ratio = ce.total / totalExpense3M;
            long limit = floorToThousand(ratio * maxExpensePerMonth);
            editor.putLong(goalName + "_limit_" + ce.category, limit);
        }

        editor.apply();

        // Tạo summary
        long startTime = prefs.getLong(goalName + "_start", 0);
        List<CategoryExpense> spentSinceStart =
                transactionDao.getExpensesByCategorySince(startTime);

        Map<String, Long> spentMap = new HashMap<>();
        for (CategoryExpense ce : spentSinceStart) {
            spentMap.put(ce.category, floorToThousand(ce.total));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<b>🎯 Kế hoạch tiết kiệm</b><br><br>");
        sb.append("Mục tiêu: ").append(df.format(targetVal)).append(" VND<br>");
        sb.append("Thời gian: ").append(monthsVal).append(" tháng<br>");
        sb.append("Lương: ").append(df.format(incomeVal)).append(" VND<br><br>");

        sb.append("Cần tiết kiệm mỗi tháng: ").append(df.format(savingPerMonth)).append(" VND<br>");
        sb.append("Được tiêu tối đa tháng này: ").append(df.format(maxExpensePerMonth)).append(" VND<br><br>");

        sb.append("<b>🚀 Giới hạn theo thói quen:</b><br>");

        for (CategoryExpense ce : expenses) {
            long spent = spentMap.getOrDefault(ce.category, 0L);
            long limit = prefs.getLong(goalName + "_limit_" + ce.category, 0);

            sb.append("• ").append(ce.category).append(": ")
                    .append(df.format(spent)).append(" / ").append(df.format(limit))
                    .append(" VND<br>");
        }

        prefs.edit()
                .putString(goalName + "_summary", sb.toString())
                .putBoolean(goalName + "_isSaving", true)
                .apply();
    }


    // ============================================================
    // LOAD SAVED PLAN
    // ============================================================
    private void loadSavedPlan() {

        String summary = prefs.getString(goalName + "_summary", "");

        if (summary.isEmpty()) {
            layoutSavingSection.setVisibility(View.VISIBLE);
            return;
        }

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
        if (percent > 100) percent = 100;

        progressSaving.setProgress(percent);
        tvSavingPercent.setText(percent + "%");

        Executors.newSingleThreadExecutor().execute(this::checkSavingProgress);
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

        long maxExpense = prefs.getLong(goalName + "_maxExpensePerMonth", 0);
        long spentThisMonth = getExpenseThisMonth();

        boolean exceed = spentThisMonth > maxExpense;

        requireActivity().runOnUiThread(() -> {

            btnRecalc.setVisibility(exceed ? View.VISIBLE : View.GONE);

            if (exceed) {
                tvWarning.setVisibility(View.VISIBLE);
                tvWarning.setText("⚠ Chi tiêu vượt hạn mức! Đã tiêu: "
                        + df.format(spentThisMonth) + " / "
                        + df.format(maxExpense) + " VND");


                // 🔥 POPUP
                new AlertDialog.Builder(requireContext())
                        .setTitle("⚠ Chi tiêu vượt giới hạn!")
                        .setMessage(
                                "Bạn đã tiêu vượt mức giới hạn tháng này.\n\n" +
                                        "• Đã tiêu: " + df.format(spentThisMonth) + " VND\n" +
                                        "• Giới hạn: " + df.format(maxExpense) + " VND\n\n" +
                                        "Hãy điều chỉnh chi tiêu hoặc tính lại ngân sách."
                        )
                        .setPositiveButton("OK", null)
                        .show();
            } else {
                tvWarning.setVisibility(View.GONE);
            }
        });
    }

    // ============================================================
    private long getExpenseThisMonth() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);

        long from = c.getTimeInMillis();

        double totalDouble = transactionDao.getTotalExpenseSince(from);

        return floorToThousand(totalDouble);
    }



    // ============================================================
    private void recalcBudgetAutomatically() {

        long income = prefs.getLong(goalName + "_income", 0);
        long target = prefs.getLong(goalName + "_target", 0);
        long months = prefs.getLong(goalName + "_months", 0);

        if (income == 0 || target == 0 || months == 0) return;

        calculateBudget(target, months, income);

        new AlertDialog.Builder(requireContext())
                .setTitle("Đã tính lại ngân sách")
                .setMessage("Ngân sách đã được cập nhật theo chi tiêu thực tế.")
                .setPositiveButton("OK", (d, w) -> loadSavedPlan())
                .show();
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
    public static BudgetFragment newInstance(String goalName, long target, long months, long income) {
        BudgetFragment fragment = new BudgetFragment();
        Bundle args = new Bundle();
        args.putString("goalName", goalName);
        args.putLong("target_arg", target);
        args.putLong("months_arg", months);
        args.putLong("income_arg", income);
        fragment.setArguments(args);
        return fragment;
    }


}
