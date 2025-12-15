package com.example.mymoney.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.R;
import com.example.mymoney.model.SavingGoal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SavingGoalAdapter extends RecyclerView.Adapter<SavingGoalAdapter.ViewHolder> {

    // =======================
    // CLICK LISTENER
    // =======================
    public interface OnGoalClickListener {
        void onGoalClick(SavingGoal goal);
    }

    private final List<SavingGoal> goals;
    private final OnGoalClickListener listener;

    public SavingGoalAdapter(List<SavingGoal> goals, OnGoalClickListener listener) {
        this.goals = goals;
        this.listener = listener;
    }

    // =======================
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_saving_goal, parent, false);
        return new ViewHolder(v);
    }

    // =======================
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        SavingGoal goal = goals.get(position); // ✅ ĐÚNG

        // ===== TÊN GOAL =====
        holder.tvGoalName.setText(goal.getName());

        // ===== NGÀY CẬP NHẬT =====
        if (goal.getLastUpdatedTime() > 0) {
            SimpleDateFormat sdf =
                    new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            holder.tvLastUpdate.setText(
                    "Cập nhật: " + sdf.format(new Date(goal.getLastUpdatedTime()))
            );
        } else {
            holder.tvLastUpdate.setText("Chưa cập nhật");
        }

        // ===== % HOÀN THÀNH =====
        int percent = goal.getTargetAmount() == 0 ? 0 :
                (int) (goal.getCurrentSaved() * 100 / goal.getTargetAmount());

        percent = Math.min(percent, 100);

        holder.progressCircle.setProgress(percent);
        holder.tvPercent.setText(percent + "%");

        // ===== CLICK ITEM =====
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onGoalClick(goal);
            }
        });
    }

    // =======================
    @Override
    public int getItemCount() {
        return goals.size();
    }

    // =======================
    public static class ViewHolder extends RecyclerView.ViewHolder {

        TextView tvGoalName;
        TextView tvLastUpdate;
        TextView tvPercent;
        ProgressBar progressCircle;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            // ⚠️ ID PHẢI KHỚP item_saving_goal.xml
            tvGoalName = itemView.findViewById(R.id.tvGoalName);
            tvLastUpdate = itemView.findViewById(R.id.tvLastUpdate);
            tvPercent = itemView.findViewById(R.id.tvPercent);
            progressCircle = itemView.findViewById(R.id.progressCircle);
        }
    }
}
