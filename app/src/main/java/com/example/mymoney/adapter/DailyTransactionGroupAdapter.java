package com.example.mymoney.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.R;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Transaction;
import com.example.mymoney.model.DailyTransactionGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DailyTransactionGroupAdapter extends RecyclerView.Adapter<DailyTransactionGroupAdapter.DailyGroupViewHolder> {

    private List<DailyTransactionGroup> dailyGroups = new ArrayList<>();
    private AppDatabase database;
    private TransactionAdapter.OnTransactionClickListener listener;

    public DailyTransactionGroupAdapter(AppDatabase database, TransactionAdapter.OnTransactionClickListener listener) {
        this.database = database;
        this.listener = listener;
    }

    public void setDailyGroups(List<DailyTransactionGroup> dailyGroups) {
        this.dailyGroups = dailyGroups;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DailyGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_daily_transaction_group, parent, false);
        return new DailyGroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DailyGroupViewHolder holder, int position) {
        DailyTransactionGroup group = dailyGroups.get(position);
        holder.bind(group, position);

        // entrance anim
        holder.itemView.setAlpha(0f);
        holder.itemView.setTranslationY(50f);
        holder.itemView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay(position * 50L)
                .start();
    }

    @Override
    public int getItemCount() {
        return dailyGroups.size();
    }

    class DailyGroupViewHolder extends RecyclerView.ViewHolder {
        private TextView tvDate;
        private TextView tvSummary;
        private RecyclerView rvTransactions;
        private TransactionAdapter transactionAdapter;
        private View headerLayout;

        public DailyGroupViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvSummary = itemView.findViewById(R.id.tv_summary);
            rvTransactions = itemView.findViewById(R.id.rv_transactions);
            headerLayout = itemView.findViewById(R.id.header_layout);

            // Setup nested RecyclerView for transactions
            rvTransactions.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            transactionAdapter = new TransactionAdapter(database, listener);
            rvTransactions.setAdapter(transactionAdapter);
        }

        public void bind(DailyTransactionGroup group, int position) {
            // Set date
            tvDate.setText(group.getDate());

            // Get currency from MainActivity
            String currency = com.example.mymoney.MainActivity.getSelectedWalletCurrency();

            // Set summary with 2 decimal places using wallet currency
            String summary = String.format(Locale.getDefault(),
                    "Income: %,.2f %s | Expense: %,.2f %s",
                    group.getTotalIncome(), currency,
                    group.getTotalExpense(), currency);
            tvSummary.setText(summary);

            // Show/hide transactions based on expanded state
            rvTransactions.setVisibility(group.isExpanded() ? View.VISIBLE : View.GONE);

            // Set transactions
            transactionAdapter.setTransactions(group.getTransactions());

            // Handle header click to toggle expand/collapse
            headerLayout.setOnClickListener(v -> {
                group.toggleExpanded();
                notifyItemChanged(position);
            });
        }
    }
}