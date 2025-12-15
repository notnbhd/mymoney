package com.example.mymoney.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.R;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.database.entity.Transaction;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder> {

    private List<Transaction> transactions = new ArrayList<>();
    private AppDatabase database;
    private OnTransactionClickListener listener;

    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    public TransactionAdapter(AppDatabase database, OnTransactionClickListener listener) {
        this.database = database;
        this.listener = listener;
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TransactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        Transaction transaction = transactions.get(position);
        holder.bind(transaction);
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    class TransactionViewHolder extends RecyclerView.ViewHolder {
        private ImageView transactionIcon;
        private TextView transactionCategory;
        private TextView transactionDetails;
        private TextView transactionAmount;

        public TransactionViewHolder(@NonNull View itemView) {
            super(itemView);
            transactionIcon = itemView.findViewById(R.id.transaction_icon);
            transactionCategory = itemView.findViewById(R.id.transaction_category);
            transactionDetails = itemView.findViewById(R.id.transaction_details);
            transactionAmount = itemView.findViewById(R.id.transaction_amount);
        }

        public void bind(Transaction transaction) {
            // Get category name from database in background
            new Thread(() -> {
                try {
                    Category category = database.categoryDao().getCategoryById(transaction.getCategoryId());
                    
                    // Update UI on main thread
                    itemView.post(() -> {
                        if (category != null) {
                            transactionCategory.setText(category.getName());
                            setTransactionIcon(category.getName());
                        } else {
                            transactionCategory.setText("Unknown");
                            transactionIcon.setImageResource(R.drawable.ic_more_apps);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            
            // Format date and description - show full date for history
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            String dateStr = sdf.format(new Date(transaction.getCreatedAt()));
            transactionDetails.setText(dateStr);
            
            // Get currency from MainActivity
            String currency = com.example.mymoney.MainActivity.getSelectedWalletCurrency();
            
            // Format amount with 2 decimal places
            String amountStr;
            int textColor;
            if (transaction.getType().equals("expense")) {
                amountStr = String.format(Locale.getDefault(), "-%,.2f %s", transaction.getAmount(), currency);
                textColor = itemView.getContext().getColor(R.color.expense_red);
            } else {
                amountStr = String.format(Locale.getDefault(), "+%,.2f %s", transaction.getAmount(), currency);
                textColor = itemView.getContext().getColor(R.color.primary_green);
            }
            transactionAmount.setText(amountStr);
            transactionAmount.setTextColor(textColor);
            
            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTransactionClick(transaction);
                }
            });
        }

        private void setTransactionIcon(String categoryName) {
            int iconRes;
            switch (categoryName.toLowerCase()) {
                case "food":
                    iconRes = R.drawable.ic_food;
                    break;
                case "home":
                case "housing":
                    iconRes = R.drawable.ic_home;
                    break;
                case "transport":
                    iconRes = R.drawable.ic_taxi;
                    break;
                case "relationship":
                    iconRes = R.drawable.ic_love;
                    break;
                case "entertainment":
                    iconRes = R.drawable.ic_entertainment;
                    break;
                case "medical":
                    iconRes = R.drawable.ic_medical;
                    break;
                case "tax":
                    iconRes = R.drawable.tax_accountant_fee_svgrepo_com;
                    break;
                case "gym & fitness":
                    iconRes = R.drawable.ic_gym;
                    break;
                case "beauty":
                    iconRes = R.drawable.ic_beauty;
                    break;
                case "clothing":
                    iconRes = R.drawable.ic_clothing;
                    break;
                case "education":
                case "study":
                    iconRes = R.drawable.ic_education;
                    break;
                case "childcare":
                    iconRes = R.drawable.ic_childcare;
                    break;
                case "salary":
                    iconRes = R.drawable.ic_salary;
                    break;
                case "business":
                    iconRes = R.drawable.ic_work;
                    break;
                case "gifts":
                    iconRes = R.drawable.ic_gift;
                    break;
                case "others":
                default:
                    iconRes = R.drawable.ic_more_apps;
                    break;
            }
            transactionIcon.setImageResource(iconRes);
        }
    }
}
