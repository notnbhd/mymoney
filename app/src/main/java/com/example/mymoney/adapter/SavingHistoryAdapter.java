package com.example.mymoney.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.R;
import com.example.mymoney.model.SavingHistoryItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class SavingHistoryAdapter extends RecyclerView.Adapter<SavingHistoryAdapter.ViewHolder> {

    private List<SavingHistoryItem> list;
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy");

    public SavingHistoryAdapter(List<SavingHistoryItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SavingHistoryItem item = list.get(position);

        holder.txtName.setText(item.name);

        holder.txtInfo.setText(
                "Mục tiêu: " + item.target +
                        "\nĐã tiết kiệm: " + item.saved +
                        "\nBắt đầu: " + df.format(new Date(item.start)) +
                        "\nKết thúc: " + df.format(new Date(item.end)) +
                        "\nLoại: " + (item.type.equals("auto") ? "Tự động" : "Thủ công")
        );
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtInfo;

        public ViewHolder(View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtHistoryName);
            txtInfo = itemView.findViewById(R.id.txtHistoryInfo);
        }
    }
}
