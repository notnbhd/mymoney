package com.example.mymoney;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class MoreFragment extends Fragment {

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(LocaleHelper.onAttach(context));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_more, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        LinearLayout historyItem = view.findViewById(R.id.more_history);
        LinearLayout budgetItem = view.findViewById(R.id.more_budget);
        LinearLayout savingGoalItem = view.findViewById(R.id.more_saving_goal);

        historyItem.setOnClickListener(v -> navigateToFragment(new HistoryFragment(), "History"));
        budgetItem.setOnClickListener(v -> navigateToFragment(new BudgetFragment(), "Budgets"));
        savingGoalItem.setOnClickListener(v -> navigateToFragment(new SavingGoalFragment(), "Saving Goal"));
    }

    private void navigateToFragment(Fragment fragment, String title) {
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            mainActivity.loadFragmentFromMore(fragment, title);
        }
    }
}
