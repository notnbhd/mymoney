package com.example.mymoney;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Wallet;

public class AddWalletFragment extends Fragment {

    private static final String ARG_WALLET_TYPE = "wallet_type";

    private ImageView btnBack;
    private ImageView btnSave;
    private EditText etWalletName;
    private EditText etBalance;
    private TextView tvCurrency;
    private LinearLayout layoutCurrency;
    private EditText etNote;

    private String walletType;
    private String selectedCurrency = "VND"; // Default currency

    public static AddWalletFragment newInstance(String walletType) {
        AddWalletFragment fragment = new AddWalletFragment();
        Bundle args = new Bundle();
        args.putString(ARG_WALLET_TYPE, walletType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            walletType = getArguments().getString(ARG_WALLET_TYPE);
        }
        if (walletType == null) {
            walletType = NewWalletFragment.WALLET_TYPE_CASH;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_add_wallet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).hideMainHeaderAndFooter();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (getActivity() != null && isRemoving()) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showMainHeaderAndFooter();
            }
        }
    }

    private void initializeViews(View view) {
        btnBack = view.findViewById(R.id.btn_back);
        btnSave = view.findViewById(R.id.btn_save);
        etWalletName = view.findViewById(R.id.et_wallet_name);
        etBalance = view.findViewById(R.id.et_balance);
        tvCurrency = view.findViewById(R.id.tv_currency);
        layoutCurrency = view.findViewById(R.id.layout_currency);
        etNote = view.findViewById(R.id.et_note);

        // Set default currency display
        tvCurrency.setText(selectedCurrency);
        if (getContext() != null) {
            tvCurrency.setTextColor(getResources().getColor(R.color.black, null));
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().getSupportFragmentManager().popBackStack();
            }
        });

        btnSave.setOnClickListener(v -> saveWallet());

        layoutCurrency.setOnClickListener(v -> showCurrencyPicker());
    }

    private void showCurrencyPicker() {
        String[] currencies = {"VND", "USD", "EUR", "GBP", "JPY"};
        int currentIndex = -1;

        for (int i = 0; i < currencies.length; i++) {
            if (currencies[i].equals(selectedCurrency)) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = (currentIndex + 1) % currencies.length;
        selectedCurrency = currencies[nextIndex];
        tvCurrency.setText(selectedCurrency);
    }

    private void saveWallet() {
        String walletName = etWalletName.getText().toString().trim();
        String balanceStr = etBalance.getText().toString().trim();
        String note = etNote.getText().toString().trim();

        if (walletName.isEmpty()) {
            Toast.makeText(getContext(), "Please enter wallet name", Toast.LENGTH_SHORT).show();
            etWalletName.requestFocus();
            return;
        }

        if (balanceStr.isEmpty()) {
            Toast.makeText(getContext(), "Please enter balance", Toast.LENGTH_SHORT).show();
            etBalance.requestFocus();
            return;
        }

        double balance;
        try {
            balance = Double.parseDouble(balanceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Please enter a valid balance amount", Toast.LENGTH_SHORT).show();
            etBalance.requestFocus();
            return;
        }

        // Save wallet to database
        if (getContext() != null) {
            // Disable save button to prevent double submission
            btnSave.setEnabled(false);

            // Create wallet object
            Wallet wallet = new Wallet();
            wallet.setName(walletName);
            wallet.setType(walletType);
            wallet.setCurrency(selectedCurrency);
            wallet.setBalance(balance);
            wallet.setDescription(note);
            wallet.setActive(true);
            wallet.setUserId(MainActivity.getCurrentUserId());
            
            // Insert wallet in background thread
            AppDatabase db = AppDatabase.getInstance(getContext());
            new Thread(() -> {
                try {
                    long walletId = db.walletDao().insert(wallet);

                    // Show success message on UI thread
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Wallet saved successfully!", Toast.LENGTH_SHORT).show();
                            
                            if (getActivity() != null) {
                                getActivity().getSupportFragmentManager().popBackStack(null,
                                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
                            }
                        });
                    }
                } catch (Exception e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(getContext(), "Error saving wallet: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            btnSave.setEnabled(true);
                        });
                    }
                }
            }).start();
        }
    }
}
