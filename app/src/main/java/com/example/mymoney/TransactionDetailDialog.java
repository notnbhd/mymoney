package com.example.mymoney;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.database.entity.Transaction;
import com.example.mymoney.database.entity.Wallet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TransactionDetailDialog extends Dialog {

    private Transaction transaction;
    private AppDatabase database;
    private OnTransactionActionListener listener;

    // Views
    private ImageButton btnClose;
    private ImageButton btnDelete;
    private ImageView ivCategoryIcon;
    private TextView tvCategoryName;
    private TextView tvAmount;
    private TextView tvDate;
    private TextView tvAccount;
    private TextView tvNote;

    public interface OnTransactionActionListener {
        void onDelete(Transaction transaction);
    }

    public TransactionDetailDialog(@NonNull Context context, Transaction transaction, OnTransactionActionListener listener) {
        super(context);
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
    }
}
