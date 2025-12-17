package com.example.mymoney.savingGoal;

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

import com.example.mymoney.MainActivity;
import com.example.mymoney.database.dao.CategoryDao;
import com.example.mymoney.savingGoal.SavingGoalFragment;
import com.example.mymoney.R;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.TransactionDao;
import com.example.mymoney.model.CategoryExpense;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.os.VibrationEffect;
import android.os.Vibrator;



import java.util.Arrays;
import java.util.List;


import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ManualSavingGoal extends Fragment {

    private String goalName;
    private long goalAmount;
    private long totalSaved = 0;

    private ProgressBar progressBar;
    private TextView txtTotalProgress;
    private LinearLayout categoryContainer;
    private EditText inputSavedMoney;
    private Button btnSaveProgress;
    private Button btnEndSavingProgress;
    private boolean warned = false;
    private boolean completedShown = false;
    private CategoryDao categoryDao;
    private List<com.example.mymoney.database.entity.Category> expenseCategories;


    private List<CategoryExpense> expensesSinceStart;

    // Format tiền
    private final DecimalFormat df = new DecimalFormat("#,###");

    public ManualSavingGoal() {}

    private static final String[] CATEGORIES = {
            "Food",
            "Home",
            "Transport",
            "Relationship",
            "Entertainment"
    };

    public static ManualSavingGoal newInstance(String name, long amount) {
        ManualSavingGoal f = new ManualSavingGoal();
        Bundle b = new Bundle();
        b.putString("goalName", name);
        b.putLong("goalAmount", amount);
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
        AppDatabase db = AppDatabase.getInstance(requireContext());
        categoryDao = db.categoryDao();


        // 3️⃣ Nếu CHƯA bắt đầu tiết kiệm → không tính chi tiêu
        loadExpenseCategories(() -> {

            if (savingStart <= 0) {
                expensesSinceStart = new ArrayList<>();
                setupUI();
                return;
            }

            loadExpensesSinceSavingStart(savingStart, this::setupUI);
        });


        // 4️⃣ Nếu ĐÃ bắt đầu → load chi tiêu kể từ startTime


        return v;
    }
    private void loadExpenseCategories(Runnable callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            expenseCategories = categoryDao.getAllExpenseCategories();
            requireActivity().runOnUiThread(callback);
        });
    }


    private void readArguments() {
        Bundle a = getArguments();
        if (a == null) return;

        goalName = a.getString("goalName", "");
        goalAmount = a.getLong("goalAmount", 0);
    }

    private void mapViews(View v) {
        progressBar = v.findViewById(R.id.progressGoal);
        txtTotalProgress = v.findViewById(R.id.txtTotalProgress);
        categoryContainer = v.findViewById(R.id.categoryContainer);
        inputSavedMoney = v.findViewById(R.id.inputSavedMoney);
        btnSaveProgress = v.findViewById(R.id.btnSaveProgress);
        btnEndSavingProgress = v.findViewById(R.id.btnEndSavingProgress);

    }

    // Load saved amount from database
    private void loadSavedAmount() {
        int userId = getCurrentUserId();
        int walletId = MainActivity.getSelectedWalletId();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                com.example.mymoney.database.entity.SavingGoal dbGoal =
                        AppDatabase.getInstance(requireContext())
                                .savingGoalDao()
                                .getSavingGoalByName(userId, walletId, goalName);

                if (dbGoal != null) {
                    totalSaved = (long) dbGoal.getCurrentAmount();
                }

                // Update UI on main thread will happen in setupUI
            } catch (Exception e) {
                android.util.Log.e("SavingProgressFragment", "Error loading saved amount", e);
            }
        });
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
        long remain = Math.max(goalAmount - totalSaved, 0);

        String progressText =
                "Mục tiêu: " + df.format(goalAmount) + " VND\n" +
                        "Đã tiết kiệm: " + df.format(totalSaved) + " VND\n" +
                        "Còn thiếu: " + df.format(remain) + " VND";

        txtTotalProgress.setText(progressText);

        progressBar.setMax((int) goalAmount);
        progressBar.setProgress((int) totalSaved);
        btnEndSavingProgress.setOnClickListener(v -> showConfirmEndDialog());


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
        for (com.example.mymoney.database.entity.Category category : expenseCategories) {

            String categoryName = category.getName();

            long spent = spentMap.getOrDefault(categoryName, 0L);

            long limit;
            if (budgetPrefs.contains(goalName + "_limit_" + categoryName)) {
                limit = budgetPrefs.getLong(goalName + "_limit_" + categoryName, 0);
            } else {
                limit = -1;
            }

            addCategory(categoryName, spent, limit);
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
            // 🎉 HOÀN THÀNH MỤC TIÊU
            if (totalSaved >= goalAmount && !completedShown) {
                completedShown = true;

                showCelebration();

                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("🎉 Chúc mừng!")
                        .setMessage(
                                "Bạn đã hoàn thành mục tiêu tiết kiệm:\n\n" +
                                        "🎯 " + df.format(goalAmount) + " VND\n\n" +
                                        "Bạn có muốn kết thúc mục tiêu ngay không?"
                        )
                        .setCancelable(false)
                        .setPositiveButton("Kết thúc", (d, w) -> {
                            showConfirmEndDialog(); // dialog xác nhận kết thúc
                        })
                        .setNegativeButton("Để sau", null)
                        .show();
            }


// ⭐⭐⭐ CẬP NHẬT SAVING + LAST UPDATED TIME ⭐⭐⭐
            SavingGoalFragment.updateSavedInGoalList(
                    requireContext(),
                    goalName,
                    totalSaved
            );

// (có thể bỏ saveUpdatedGoal nếu muốn)


            long newRemain = Math.max(goalAmount - totalSaved, 0);
            txtTotalProgress.setText(
                    "Mục tiêu: " + df.format(goalAmount) + " VND\n" +
                            "Đã tiết kiệm: " + df.format(totalSaved) + " VND\n" +
                            "Còn thiếu: " + df.format(newRemain) + " VND"
            );

            progressBar.setProgress((int) totalSaved);
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
    private void endSavingGoal() {

        // ⚠️ Lưu reference Activity trước
        if (!isAdded()) return;
        final androidx.fragment.app.FragmentActivity activity = getActivity();
        if (activity == null) return;

        // 1️⃣ XÓA LEGACY PREFS
        SharedPreferences prefs =
                requireContext().getSharedPreferences("SAVING_GOALS", Context.MODE_PRIVATE);

        Set<String> raw = prefs.getStringSet("goal_list", new HashSet<>());
        Set<String> newSet = new HashSet<>();

        for (String item : raw) {
            if (!item.startsWith(goalName + "|")) {
                newSet.add(item);
            }
        }
        prefs.edit().putStringSet("goal_list", newSet).apply();

        // 2️⃣ LẤY START TIME
        SharedPreferences prefsBudget =
                requireContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);

        long startTime = prefsBudget.getLong(goalName + "_start", 0);
        long endTime = System.currentTimeMillis();

        // 3️⃣ LƯU HISTORY
        SharedPreferences historyPref =
                requireContext().getSharedPreferences("SAVING_HISTORY", Context.MODE_PRIVATE);

        Set<String> history =
                new HashSet<>(historyPref.getStringSet("history_list", new HashSet<>()));

        history.add(
                goalName + "|" +
                        goalAmount + "|" +
                        totalSaved + "|" +
                        startTime + "|" +
                        endTime + "|completed"
        );

        historyPref.edit().putStringSet("history_list", history).apply();

        // 4️⃣ XÓA DB + PREFS (BACKGROUND)
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int userId = getCurrentUserId();
                int walletId = MainActivity.getSelectedWalletId();

                AppDatabase db = AppDatabase.getInstance(requireContext());

                db.budgetDao().deleteByNamePattern(goalName + " - %");

                com.example.mymoney.database.entity.SavingGoal dbGoal =
                        db.savingGoalDao().getSavingGoalByName(userId, walletId, goalName);

                if (dbGoal != null) {
                    db.savingGoalDao().deleteById(dbGoal.getId());
                }

                prefsBudget.edit()
                        .remove(goalName + "_start")
                        .apply();

                // ✅ UI PHẢI CHECK isAdded()
                activity.runOnUiThread(() -> {
                    if (!isAdded()) return;

                    activity.getSupportFragmentManager()
                            .popBackStack();
                });

            } catch (Exception e) {
                android.util.Log.e("ManualSavingGoal", "Error ending goal", e);
            }
        });
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
    private void showEditAllLimitsDialog() {

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 10);

        SharedPreferences prefs =
                requireContext().getSharedPreferences("budget_prefs", Context.MODE_PRIVATE);

        Map<String, EditText> inputs = new HashMap<>();

        for (com.example.mymoney.database.entity.Category category : expenseCategories) {
            String name = category.getName();

            EditText edt = new EditText(requireContext());
            edt.setHint(name + " limit");

            long oldLimit = prefs.getLong(goalName + "_limit_" + name, 0);
            if (oldLimit > 0) edt.setText(String.valueOf(oldLimit));

            layout.addView(edt);
            inputs.put(name, edt);
        }


        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Sửa toàn bộ giới hạn chi tiêu")
                .setView(layout)
                .setPositiveButton("Lưu", (d, w) -> {

                    SharedPreferences.Editor editor = prefs.edit();

                    for (String name : inputs.keySet()) {
                        String val = inputs.get(name).getText().toString().trim();
                        if (!TextUtils.isEmpty(val)) {
                            editor.putLong(goalName + "_limit_" + name, Long.parseLong(val));
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
    private void showConfirmEndDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Kết thúc mục tiêu tiết kiệm")
                .setMessage(
                        "Bạn có chắc muốn kết thúc mục tiêu \"" + goalName + "\"?\n\n" +
                                "Mục tiêu sẽ được lưu vào lịch sử và không thể chỉnh sửa lại."
                )
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Kết thúc", (dialog, which) -> {
                    endSavingGoal(); // ✅ CHỈ GỌI KHI OK
                })
                .show();
    }

    private void showCelebration() {
        if (!isAdded()) return;

        // ===== RUNG =====
        Vibrator vibrator =
                (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);

        if (vibrator != null) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(
                        VibrationEffect.createWaveform(
                                new long[]{0, 300, 150, 300},
                                -1
                        )
                );
            } else {
                vibrator.vibrate(500);
            }
        }

        // ===== ÂM THANH =====
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(
                    requireContext(),
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            );
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {}

        // ===== VIEW HỒNG =====
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 40);
        layout.setBackgroundColor(0xFFFFF1F6); // hồng nhạt MyMoney

        TextView title = new TextView(requireContext());
        title.setText("🎉 CHÚC MỪNG 🎉");
        title.setTextSize(22);
        title.setTextColor(0xFFE91E63); // hồng đậm
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, 24);

        TextView content = new TextView(requireContext());
        content.setText(
                "Bạn đã hoàn thành mục tiêu tiết kiệm!\n\n" +
                        "💰 " + df.format(goalAmount) + " VND 💰"
        );
        content.setTextSize(16);
        content.setTextColor(0xFF444444);
        content.setGravity(android.view.Gravity.CENTER);

        layout.addView(title);
        layout.addView(content);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("OK 🎯", null)
                .show();
    }

}