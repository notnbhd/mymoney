package com.example.mymoney;

import static com.example.mymoney.MainActivity.getCurrentUserId;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.TransactionDao;
import com.example.mymoney.model.CategoryExpense;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class SavingProgressFragment extends Fragment {

    private String goalName;
    private int goalAmount;
    private int totalSaved = 0;

    private ProgressBar progressBar;
    private TextView txtTotalProgress;
    private LinearLayout categoryContainer;
    private EditText inputSavedMoney;
    private Button btnSaveProgress;
    private Button btnEndSavingProgress;
    private boolean warned = false;


    private List<CategoryExpense> expensesSinceStart;

    // Format tiền
    private final DecimalFormat df = new DecimalFormat("#,###");

    public SavingProgressFragment() {}

    private static final String[] CATEGORIES = {
            "Food",
            "Home",
            "Transport",
            "Relationship",
            "Entertainment"
    };

    public static SavingProgressFragment newInstance(String name, int amount) {
        SavingProgressFragment f = new SavingProgressFragment();
        Bundle b = new Bundle();
        b.putString("goalName", name);
        b.putInt("goalAmount", amount);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_saving_goal_progress, container, false);

        readArguments();
        mapViews(v);

        // 1️⃣ Load số tiền đã tiết kiệm
        loadSavedAmount();

        // 2️⃣ Lấy startTime RIÊNG cho goal này
        SharedPreferences prefsBudget =
                requireContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);

        long savingStart = prefsBudget.getLong(goalName + "_start", -1);

        // 3️⃣ Nếu CHƯA bắt đầu tiết kiệm → không tính chi tiêu
        if (savingStart <= 0) {
            expensesSinceStart = new ArrayList<>(); // dùng list rỗng cho an toàn
            setupUI();
            return v;
        }

        // 4️⃣ Nếu ĐÃ bắt đầu → load chi tiêu kể từ startTime
        loadExpensesSinceSavingStart(savingStart, this::setupUI);

        return v;
    }


    private void readArguments() {
        Bundle a = getArguments();
        if (a == null) return;

        goalName = a.getString("goalName", "");
        goalAmount = a.getInt("goalAmount", 0);
    }

    private void mapViews(View v) {
        progressBar = v.findViewById(R.id.progressGoal);
        txtTotalProgress = v.findViewById(R.id.txtTotalProgress);
        categoryContainer = v.findViewById(R.id.categoryContainer);
        inputSavedMoney = v.findViewById(R.id.inputSavedMoney);
        btnSaveProgress = v.findViewById(R.id.btnSaveProgress);
        btnEndSavingProgress = v.findViewById(R.id.btnEndSavingProgress);

    }

    // Đọc tổng tiền đã tiết kiệm cho goal này từ SAVING_GOALS
    private void loadSavedAmount() {
        SharedPreferences prefs =
                requireContext().getSharedPreferences("SAVING_GOALS", Context.MODE_PRIVATE);

        Set<String> rawSet = prefs.getStringSet("goal_list", new HashSet<>());

        for (String item : rawSet) {
            String[] arr = item.split("\\|");
            if (arr.length >= 3 && arr[0].equals(goalName)) {
                totalSaved = Integer.parseInt(arr[2]);
                break;
            }
        }
    }

    // 🔥 LẤY DỮ LIỆU GIỐNG BUDGETFRAGMENT
    private void loadExpensesSinceSavingStart(long savingStart, Runnable callback) {
        Executors.newSingleThreadExecutor().execute(() -> {

            TransactionDao dao =
                    AppDatabase.getInstance(requireContext()).transactionDao();

            int userId = getCurrentUserId(); // giống BudgetFragment

            expensesSinceStart =
                    dao.getExpensesByCategorySince(savingStart, userId);

            requireActivity().runOnUiThread(callback);
        });
    }


    private void setupUI() {

        // ====== 1. Hiển thị tiến độ tiết kiệm ======
        int remain = Math.max(goalAmount - totalSaved, 0);

        String progressText =
                "Mục tiêu: " + df.format(goalAmount) + " VND\n" +
                        "Đã tiết kiệm: " + df.format(totalSaved) + " VND\n" +
                        "Còn thiếu: " + df.format(remain) + " VND";

        txtTotalProgress.setText(progressText);

        progressBar.setMax(goalAmount);
        progressBar.setProgress(totalSaved);
        btnEndSavingProgress.setOnClickListener(v -> endSavingGoal());

        // ====== 2. Hiển thị chi tiêu / limit ======
        categoryContainer.removeAllViews();

        // 🔥 TITLE (CHỈ ADD 1 LẦN)
        TextView title = new TextView(requireContext());
        title.setText("📌 Chi tiêu theo danh mục:");
        title.setTextSize(18);
        title.setPadding(0, 0, 0, 20);
        categoryContainer.addView(title);

        // prefs
        SharedPreferences budgetPrefs =
                requireContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);

        // map chi tiêu từ DB
        Map<String, Long> spentMap = new HashMap<>();
        if (expensesSinceStart != null) {
            for (CategoryExpense ce : expensesSinceStart) {
                spentMap.put(ce.category, (long) ce.total);
            }
        }

        // 🔥 HIỂN THỊ THEO CATEGORY CỐ ĐỊNH
        for (String category : CATEGORIES) {
            long spent = spentMap.getOrDefault(category, 0L);
            long limit;
            if (budgetPrefs.contains(goalName + "_limit_" + category)) {
                limit = budgetPrefs.getLong(goalName + "_limit_" + category, 0);
            } else {
                limit = -1; // chưa set limit
            }

            addCategory(category, spent, limit);
        }

        // ====== 3. Cập nhật số tiền tiết kiệm ======
        btnSaveProgress.setOnClickListener(b -> {
            String val = inputSavedMoney.getText().toString().trim();
            if (TextUtils.isEmpty(val)) return;

            int add;
            try {
                add = Integer.parseInt(val);
            } catch (NumberFormatException e) {
                inputSavedMoney.setError("Số tiền không hợp lệ");
                return;
            }

            totalSaved += add;

// ⭐⭐⭐ CẬP NHẬT SAVING + LAST UPDATED TIME ⭐⭐⭐
            SavingGoalFragment.updateSavedInGoalList(
                    requireContext(),
                    goalName,
                    totalSaved
            );

// (có thể bỏ saveUpdatedGoal nếu muốn)


            int newRemain = Math.max(goalAmount - totalSaved, 0);
            txtTotalProgress.setText(
                    "Mục tiêu: " + df.format(goalAmount) + " VND\n" +
                            "Đã tiết kiệm: " + df.format(totalSaved) + " VND\n" +
                            "Còn thiếu: " + df.format(newRemain) + " VND"
            );

            progressBar.setProgress(totalSaved);
            inputSavedMoney.setText("");
        });
    }


    private void addCategory(String name, long spent, long limit) {
        TextView tv = new TextView(getContext());

        boolean isOver = limit > 0 && spent > limit;

        String line;
        if (limit > 0) {
            line = "• " + name + ": " +
                    df.format(spent) + " / " +
                    df.format(limit) + " VND";
        } else {
            line = "• " + name + ": " +
                    df.format(spent) + " VND (chưa đặt giới hạn)";
        }

        if (isOver) {
            line += "  ⚠ VƯỢT GIỚI HẠN";
            tv.setTextColor(0xFFFF4444);
        }

        tv.setText(line);
        tv.setTextSize(16);
        tv.setPadding(0, 12, 0, 12);

        // 🔥 CLICK LOGIC CHUẨN
        tv.setOnClickListener(v -> {
            if (isOver) {
                // ⚠️ VƯỢT → SỬA TẤT CẢ
                showEditAllLimitsDialog();
            } else {
                // ✅ CHƯA VƯỢT → SỬA RIÊNG
                showEditLimitDialog(name, limit);
            }
        });

        // 🔔 CẢNH BÁO CHỈ SHOW 1 LẦN
        if (isOver && !warned) {
            warned = true;
            tv.post(() -> showOverLimitAllDialog());
        }

        categoryContainer.addView(tv);
    }




    // lưu lại tổng tiền đã tiết kiệm của goal hiện tại
    private void saveUpdatedGoal(int newValue) {
        SharedPreferences prefs =
                requireContext().getSharedPreferences("SAVING_GOALS", Context.MODE_PRIVATE);

        Set<String> raw = prefs.getStringSet("goal_list", new HashSet<>());
        Set<String> newSet = new HashSet<>();

        for (String item : raw) {
            String[] arr = item.split("\\|");
            if (arr.length >= 3 && arr[0].equals(goalName)) {
                newSet.add(arr[0] + "|" + arr[1] + "|" + newValue);
            } else {
                newSet.add(item);
            }
        }

        prefs.edit().putStringSet("goal_list", newSet).apply();
    }
    private void endSavingGoal() {

        // 1) XÓA KHỎI DANH SÁCH GOAL
        SharedPreferences prefs =
                requireContext().getSharedPreferences("SAVING_GOALS", Context.MODE_PRIVATE);

        Set<String> raw = prefs.getStringSet("goal_list", new HashSet<>());
        Set<String> newSet = new HashSet<>();

        for (String item : raw) {
            String[] arr = item.split("\\|");
            if (!arr[0].equals(goalName)) {
                newSet.add(item); // giữ lại những mục khác
            }
        }

        prefs.edit().putStringSet("goal_list", newSet).apply();



        // 2) LẤY START TIME đã lưu khi bắt đầu tiết kiệm
        SharedPreferences prefsBudget =
                requireContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);

        long startTime = prefsBudget.getLong(goalName + "_start", 0);

        // 3) END TIME = thời điểm hoàn thành
        long endTime = System.currentTimeMillis();



        // 4) LƯU VÀO LỊCH SỬ HOÀN THÀNH — ĐÚNG THỨ TỰ:
        // name | target | saved | start | end | type
        SharedPreferences historyPref =
                requireContext().getSharedPreferences("SAVING_HISTORY", Context.MODE_PRIVATE);

        Set<String> history = historyPref.getStringSet("history_list", new HashSet<>());

        history.add(
                goalName + "|" +
                        goalAmount + "|" +
                        totalSaved + "|" +
                        startTime + "|" +
                        endTime + "|" +
                        "completed"
        );

        historyPref.edit().putStringSet("history_list", history).apply();



        // 5) Quay lại màn danh sách
        requireActivity().getSupportFragmentManager()
                .popBackStack();
    }

    private void showEditLimitDialog(String category, long oldLimit) {
        EditText edt = new EditText(getContext());
        edt.setHint("Nhập giới hạn mới");

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Sửa giới hạn chi tiêu")
                .setView(edt)
                .setPositiveButton("Lưu", (d, w) -> {
                    String val = edt.getText().toString().trim();
                    if (TextUtils.isEmpty(val)) return;

                    long newLimit = Long.parseLong(val);

                    SharedPreferences prefs =
                            requireContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);

                    prefs.edit()
                            .putLong(goalName + "_limit_" + category, newLimit)
                            .apply();

                    setupUI(); // refresh
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void showOverLimitWarningDialog(String category, long spent, long limit) {

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("⚠ Vượt giới hạn chi tiêu")
                .setMessage(
                        "Danh mục: " + category +
                                "\nĐã chi: " + df.format(spent) + " VND" +
                                "\nGiới hạn: " + df.format(limit) + " VND" +
                                "\n\nBạn có muốn sửa lại giới hạn không?"
                )
                .setPositiveButton("Sửa giới hạn", (d, w) ->
                        showEditLimitDialog(category, limit)
                )
                .setNegativeButton("Để sau", null)
                .show();
    }
    private void showEditAllLimitsDialog() {

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        SharedPreferences prefs =
                requireContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);

        Map<String, EditText> inputs = new HashMap<>();

        for (String category : CATEGORIES) {
            EditText edt = new EditText(requireContext());
            edt.setHint(category + " limit");

            long oldLimit = prefs.getLong(goalName + "_limit_" + category, 0);
            if (oldLimit > 0) {
                edt.setText(String.valueOf(oldLimit));
            }

            layout.addView(edt);
            inputs.put(category, edt);
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Sửa toàn bộ giới hạn chi tiêu")
                .setView(layout)
                .setPositiveButton("Lưu", (d, w) -> {

                    SharedPreferences.Editor editor = prefs.edit();

                    for (String category : CATEGORIES) {
                        String val = inputs.get(category).getText().toString().trim();
                        if (!TextUtils.isEmpty(val)) {
                            editor.putLong(
                                    goalName + "_limit_" + category,
                                    Long.parseLong(val)
                            );
                        }
                    }

                    editor.apply();
                    setupUI(); // refresh lại màn hình
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }
    private void showOverLimitAllDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("⚠ Vượt giới hạn chi tiêu")
                .setMessage(
                        "Chi tiêu của bạn đã vượt giới hạn cho mục tiêu này.\n\n" +
                                "Bạn có muốn chỉnh sửa lại toàn bộ giới hạn không?"
                )
                .setPositiveButton("Sửa toàn bộ", (d, w) ->
                        showEditAllLimitsDialog()
                )
                .setNegativeButton("Để sau", null)
                .show();
    }


}