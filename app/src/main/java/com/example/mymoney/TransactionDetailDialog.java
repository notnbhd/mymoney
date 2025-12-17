package com.example.mymoney;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.database.entity.Transaction;
import com.example.mymoney.database.entity.Wallet;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TransactionDetailDialog extends Dialog {

    private Transaction transaction;
    private AppDatabase database;
    private OnTransactionActionListener listener;
    private Context context;

    // Views
    private ImageButton btnClose;
    private ImageButton btnDelete;
    private ImageButton btnEdit;
    private ImageView ivCategoryIcon;
    private TextView tvCategoryName;
    private TextView tvAmount;
    private TextView tvDate;
    private TextView tvAccount;
    private TextView tvNote;

    public interface OnTransactionActionListener {
        void onDelete(Transaction transaction);
        void onEdit(Transaction transaction);
    }

    public TransactionDetailDialog(@NonNull Context context, Transaction transaction, OnTransactionActionListener listener) {
        super(context);
        this.context = context;
        this.transaction = transaction;
        this.database = AppDatabase.getInstance(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_transaction_detail);

        // Make dialog background transparent to show rounded corners
        if (getWindow() != null) {
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);

            // Add fade-in animation
            View decorView = getWindow().getDecorView();
            decorView.setAlpha(0f);
            decorView.setScaleX(0.9f);
            decorView.setScaleY(0.9f);
            decorView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .start();
        }

        initViews();
        loadTransactionDetails();
        setupListeners();
    }

    private void initViews() {
        btnClose = findViewById(R.id.btn_close);
        btnDelete = findViewById(R.id.btn_delete);
        btnEdit = findViewById(R.id.btn_edit);
        ivCategoryIcon = findViewById(R.id.iv_category_icon);
        tvCategoryName = findViewById(R.id.tv_category_name);
        tvAmount = findViewById(R.id.tv_amount);
        tvDate = findViewById(R.id.tv_date);
        tvAccount = findViewById(R.id.tv_account);
        tvNote = findViewById(R.id.tv_note);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private void loadTransactionDetails() {
        new Thread(() -> {
            try {
                Category category = database.categoryDao().getCategoryById(transaction.getCategoryId());

                Wallet wallet = database.walletDao().getWalletById(transaction.getWalletId());

                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                String formattedDate = sdf.format(new Date(transaction.getCreatedAt()));

                // Update UI on main thread - use a Handler or post to any view
                if (tvCategoryName != null) {
                    tvCategoryName.post(() -> {
                        try {
                            // Category name and icon
                            if (category != null) {
                                tvCategoryName.setText(category.getName());

                                // Set category icon based on icon resource name
                                if (category.getIcon() != null && !category.getIcon().isEmpty()) {
                                    int iconResId = getContext().getResources().getIdentifier(
                                            category.getIcon(),
                                            "drawable",
                                            getContext().getPackageName()
                                    );
                                    if (iconResId != 0) {
                                        ivCategoryIcon.setImageDrawable(getContext().getDrawable(iconResId));
                                    }
                                }
                            }

                            String amountText = String.format(Locale.getDefault(),
                                    "%s%,.2f %s",
                                    "income".equals(transaction.getType()) ? "+" : "-",
                                    transaction.getAmount(),
                                    wallet != null ? wallet.getCurrency() : "VND");
                            tvAmount.setText(amountText);

                            // Set color based on type
                            if ("income".equals(transaction.getType())) {
                                tvAmount.setTextColor(getContext().getResources().getColor(R.color.green, null));
                            } else {
                                tvAmount.setTextColor(getContext().getResources().getColor(R.color.red, null));
                            }

                            // Date
                            tvDate.setText(formattedDate);

                            // Wallet name
                            tvAccount.setText(wallet != null ? wallet.getName() : "Default Account");

                            // Note (description)
                            String note = transaction.getDescription();
                            if (note != null && !note.trim().isEmpty()) {
                                tvNote.setText(note);
                            } else {
                                tvNote.setText("No note");
                            }
                        } catch (Exception e) {
                            android.util.Log.e("TransactionDialog", "Error updating UI", e);
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("TransactionDialog", "Error loading transaction details", e);
                e.printStackTrace();
            }
        }).start();
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> dismiss());

        btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(transaction);
            }
            dismiss();
        });

        btnEdit.setOnClickListener(v -> {
            dismiss();
            showEditDialog();
        });
    }

    private void showEditDialog() {
        BottomSheetDialog editDialog = new BottomSheetDialog(context);
        View dialogView = View.inflate(context, R.layout.dialog_edit_transaction, null);
        editDialog.setContentView(dialogView);

        // Get views
        ImageButton btnCloseEdit = dialogView.findViewById(R.id.btn_close);
        ImageView ivCatIcon = dialogView.findViewById(R.id.iv_category_icon);
        TextView tvCatName = dialogView.findViewById(R.id.tv_category_name);
        EditText edtAmount = dialogView.findViewById(R.id.edt_amount);
        LinearLayout layoutDate = dialogView.findViewById(R.id.layout_date);
        TextView tvDateEdit = dialogView.findViewById(R.id.tv_date);
        LinearLayout layoutTime = dialogView.findViewById(R.id.layout_time);
        TextView tvTime = dialogView.findViewById(R.id.tv_time);
        EditText edtNote = dialogView.findViewById(R.id.edt_note);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_save);

        // Calendar for date/time selection
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(transaction.getCreatedAt());

        // Date formatters
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        // Pre-fill current values
        edtAmount.setText(String.valueOf(transaction.getAmount()));
        tvDateEdit.setText(dateFormat.format(new Date(transaction.getCreatedAt())));
        tvTime.setText(timeFormat.format(new Date(transaction.getCreatedAt())));
        if (transaction.getDescription() != null) {
            edtNote.setText(transaction.getDescription());
        }

        // Load category info
        new Thread(() -> {
            Category category = database.categoryDao().getCategoryById(transaction.getCategoryId());
            if (category != null && tvCatName != null) {
                tvCatName.post(() -> {
                    tvCatName.setText(category.getName());
                    if (category.getIcon() != null && !category.getIcon().isEmpty()) {
                        int iconResId = context.getResources().getIdentifier(
                                category.getIcon(), "drawable", context.getPackageName());
                        if (iconResId != 0) {
                            ivCatIcon.setImageResource(iconResId);
                        }
                    }
                });
            }
        }).start();

        // Date picker
        layoutDate.setOnClickListener(v -> {
            DatePickerDialog datePicker = new DatePickerDialog(
                    context,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(Calendar.YEAR, year);
                        calendar.set(Calendar.MONTH, month);
                        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        tvDateEdit.setText(dateFormat.format(calendar.getTime()));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.show();
        });

        // Time picker
        layoutTime.setOnClickListener(v -> {
            TimePickerDialog timePicker = new TimePickerDialog(
                    context,
                    (view, hourOfDay, minute) -> {
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        calendar.set(Calendar.MINUTE, minute);
                        tvTime.setText(timeFormat.format(calendar.getTime()));
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
            );
            timePicker.show();
        });

        // Close button
        btnCloseEdit.setOnClickListener(v -> editDialog.dismiss());
        btnCancel.setOnClickListener(v -> editDialog.dismiss());

        // Save button
        btnSave.setOnClickListener(v -> {
            String amountStr = edtAmount.getText().toString().trim();
            if (amountStr.isEmpty()) {
                Toast.makeText(context, "Please enter amount", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double newAmount = Double.parseDouble(amountStr);
                String newNote = edtNote.getText().toString().trim();
                long newTimestamp = calendar.getTimeInMillis();

                // Calculate wallet balance adjustment
                double amountDifference = newAmount - transaction.getAmount();

                // Update transaction
                transaction.setAmount(newAmount);
                transaction.setDescription(newNote);
                transaction.setCreatedAt(newTimestamp);
                transaction.setUpdatedAt(System.currentTimeMillis());

                // Save to database and update wallet balance
                new Thread(() -> {
                    try {
                        database.transactionDao().update(transaction);

                        // Update wallet balance if amount changed
                        if (amountDifference != 0) {
                            Wallet wallet = database.walletDao().getWalletById(transaction.getWalletId());
                            if (wallet != null) {
                                double currentBalance = wallet.getBalance();
                                double newBalance;
                                if ("income".equals(transaction.getType())) {
                                    newBalance = currentBalance + amountDifference;
                                } else {
                                    newBalance = currentBalance - amountDifference;
                                }
                                database.walletDao().updateBalance(
                                        wallet.getId(), newBalance, System.currentTimeMillis());
                            }
                        }

                        // Notify listener
                        if (listener != null) {
                            tvCatName.post(() -> listener.onEdit(transaction));
                        }

                        tvCatName.post(() -> {
                            Toast.makeText(context, "Transaction updated", Toast.LENGTH_SHORT).show();
                            editDialog.dismiss();
                        });

                    } catch (Exception e) {
                        android.util.Log.e("EditTransaction", "Error saving", e);
                        tvCatName.post(() ->
                                Toast.makeText(context, "Error saving: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }).start();

            } catch (NumberFormatException e) {
                Toast.makeText(context, "Invalid amount", Toast.LENGTH_SHORT).show();
            }
        });

        editDialog.show();
    }
}