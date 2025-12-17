package com.example.mymoney.savingGoal;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.mymoney.R;
import com.example.mymoney.adapter.SavingHistoryAdapter;
import com.example.mymoney.model.SavingHistoryItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SavingHistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private SavingHistoryAdapter adapter;
    private List<SavingHistoryItem> historyList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_saving_history, container, false);

        recyclerView = view.findViewById(R.id.recyclerSavingHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new SavingHistoryAdapter(historyList);
        recyclerView.setAdapter(adapter);

        loadHistory();

        return view;
    }

    private void loadHistory() {
        SharedPreferences prefs = requireContext().getSharedPreferences("SAVING_HISTORY", 0);
        Set<String> set = prefs.getStringSet("history_list", new HashSet<>());

        historyList.clear();

        for (String s : set) {
            String[] a = s.split("\\|");

            String name = a.length > 0 ? a[0] : "";
            long target = a.length > 1 ? safeLong(a[1]) : 0;
            long saved = a.length > 2 ? safeLong(a[2]) : 0;
            long start = a.length > 3 ? safeLong(a[3]) : 0;
            long end = a.length > 4 ? safeLong(a[4]) : 0;
            String type = a.length > 5 ? a[5] : "manual";

            historyList.add(new SavingHistoryItem(name, target, saved, start, end, type));
        }

        adapter.notifyDataSetChanged();
    }

    private long safeLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }
}