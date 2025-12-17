package com.example.mymoney;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.mymoney.account.AccountActivity;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Wallet;
import com.example.mymoney.notification.NotificationSettingFragment;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "MyMoneyPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";

    private static int selectedWalletId = -1; //-1 la chua chon vi nao
    private static String selectedWalletCurrency = "VND"; // Default currency
    private static int currentUserId = 1; // acc default la 1

    private FragmentManager fragmentManager;
    private TextView headerTitle;
    private CardView walletPanel;
    private CardView settingsPanel;
    private ImageView btnWallet;
    private ImageView btnSettings;
    private LinearLayout walletListContainer;

    private LinearLayout settingsLogin;
    private LinearLayout settingsLogout;

    // Header and footer views
    private LinearLayout headerLayout;
    private View headerDivider;
    private View bottomNavigation;
    private LinearLayout settingsNotification;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Switch from splash theme to main theme
        setTheme(R.style.Theme_MyMoney);
        LocaleHelper.onAttach(this); // ✅ Language support
        ThemeUtils.applyTheme(this); // ✅ Apply current theme
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main_container);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        1001
                );
            }
        }


        View mainLayout = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        fragmentManager = getSupportFragmentManager();
        headerTitle = findViewById(R.id.header_title);
        walletPanel = findViewById(R.id.wallet_panel);
        settingsPanel = findViewById(R.id.settings_panel);
        btnWallet = findViewById(R.id.btn_wallet);
        btnSettings = findViewById(R.id.btn_settings);
        settingsNotification = findViewById(R.id.settings_notification);
        settingsLogin = findViewById(R.id.settings_login);
        settingsLogout = findViewById(R.id.settings_logout);
        headerLayout = findViewById(R.id.header_layout);
        headerDivider = findViewById(R.id.header_divider);
        bottomNavigation = findViewById(R.id.bottom_navigation);

        // Update current user ID from preferences
        updateCurrentUserId();

        setupWalletButton();
        setupSettingsButton();
        updateSettingsButtonsVisibility();

        setupNavigationBar();

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment(), "Home");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCurrentUserId();
        updateSettingsButtonsVisibility();
        loadWalletsFromDatabase();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::refreshCurrentFragment, 300);
    }

    /**
     * Update the static currentUserId from SharedPreferences
     */
    private void updateCurrentUserId() {
        currentUserId = getLoggedInUserId();
        android.util.Log.d("MainActivity", "Current user ID updated to: " + currentUserId);
    }

    // ================= Wallet Setup =================
    private void setupWalletButton() {
        btnWallet.setOnClickListener(v -> {
            loadWalletsFromDatabase();
            hideSettingsPanel(); // Hide settings when opening wallet
            toggleWalletPanel();
        });

        LinearLayout addWalletItem = findViewById(R.id.add_wallet_item);

        addWalletItem.setOnClickListener(v -> {
            hideWalletPanel();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.setCustomAnimations(
                    R.anim.fade_in_up,
                    R.anim.fade_out_down,
                    R.anim.fade_in_up,
                    R.anim.fade_out_down
            );
            transaction.replace(R.id.fragment_container, new NewWalletFragment());
            transaction.addToBackStack(null);
            transaction.commit();
        });
    }

    // ================= Settings Setup =================
    private void setupSettingsButton() {
        btnSettings.setOnClickListener(v -> {
            hideWalletPanel(); // Hide wallet when opening settings
            updateSettingsButtonsVisibility(); // Update visibility before showing panel
            toggleSettingsPanel();
        });

        // 🔹 Account settings
        LinearLayout settingsAccount = findViewById(R.id.settings_account);
        settingsAccount.setOnClickListener(v -> {
            hideSettingsPanel();
            Intent intent = new Intent(MainActivity.this, AccountActivity.class);
            startActivity(intent);
        });

        // 🌗 Theme switch
        LinearLayout themesLayout = findViewById(R.id.settings_themes);
        ImageView imgThemeIcon = findViewById(R.id.imgThemeIcon); // Moon/Sun icon

        // Display correct icon when app opens
        if (ThemeUtils.isDarkMode(this)) {
            imgThemeIcon.setImageResource(R.drawable.ic_moon);
        } else {
            imgThemeIcon.setImageResource(R.drawable.ic_sun);
        }

        // When user clicks "Themes"
        themesLayout.setOnClickListener(v -> {
            boolean isDark = ThemeUtils.isDarkMode(this);
            ThemeUtils.toggleTheme(this);
            imgThemeIcon.setImageResource(isDark ? R.drawable.ic_sun : R.drawable.ic_moon);
            recreate();
        });

        // 🌍 Language settings
        LinearLayout settingsLanguage = findViewById(R.id.settings_language);
        settingsLanguage.setOnClickListener(v -> {
            hideSettingsPanel();
            String[] languages = {"English", "Tiếng Việt"};
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Choose Language")
                    .setItems(languages, (dialog, which) -> {
                        String langCode = (which == 0) ? "en" : "vi";
                        LocaleHelper.setLocale(this, langCode);
                        recreate();
                    })
                    .show();
        });
        settingsNotification.setOnClickListener(v -> {
            hideSettingsPanel();

            getSupportFragmentManager()
                    .beginTransaction()
                    .setCustomAnimations(
                            R.anim.fade_in_up,
                            R.anim.fade_out_down,
                            R.anim.fade_in_up,
                            R.anim.fade_out_down
                    )
                    .replace(R.id.fragment_container, new NotificationSettingFragment())
                    .addToBackStack(null)
                    .commit();
        });


        // 🔐 Login button
        settingsLogin.setOnClickListener(v -> {
            hideSettingsPanel();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // 🔓 Logout button
        settingsLogout.setOnClickListener(v -> {
            hideSettingsPanel();
            performLogout();
        });
    }

    /**
     * Update visibility of login/logout buttons based on authentication state
     */
    private void updateSettingsButtonsVisibility() {
        if (isLoggedIn()) {
            // User is logged in - show logout, hide login
            settingsLogin.setVisibility(View.GONE);
            settingsLogout.setVisibility(View.VISIBLE);
        } else {
            // User is not logged in - show login, hide logout
            settingsLogin.setVisibility(View.VISIBLE);
            settingsLogout.setVisibility(View.GONE);
        }
    }

    /**
     * Check if a user is currently logged in
     */
    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /**
     * Get the currently logged-in user ID
     */
    private int getLoggedInUserId() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(KEY_USER_ID, 1);
    }

    /**
     * Perform logout: clear session and refresh UI
     */
    private void performLogout() {
        android.util.Log.d("MainActivity", "Performing logout...");

        // Clear session
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .putBoolean(KEY_IS_LOGGED_IN, false)
                .apply();

        // Reset to default user ID
        currentUserId = 1;
        android.util.Log.d("MainActivity", "User ID reset to: " + currentUserId);

        // Reset selected wallet
        selectedWalletId = -1;
        android.util.Log.d("MainActivity", "Selected wallet ID reset to: " + selectedWalletId);

        // Update UI
        updateSettingsButtonsVisibility();

        // Clear wallet panel
        if (walletListContainer != null) {
            walletListContainer.removeAllViews();
        }

        // Reload wallets for default user
        loadWalletsFromDatabase();

        // Refresh current fragment to show empty/default data with delay to ensure state is updated
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.util.Log.d("MainActivity", "Refreshing fragment after logout");
            refreshCurrentFragment();
        }, 200);

        Toast.makeText(this, "Đăng xuất thành công", Toast.LENGTH_SHORT).show();
    }

    // ================= Wallet Loading =================

    /**
     * Load wallets from database and populate the wallet panel
     */
    private void loadWalletsFromDatabase() {
        walletListContainer = findViewById(R.id.wallet_list_container);

        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            int userId = getLoggedInUserId();
            List<Wallet> wallets = db.walletDao().getActiveWalletsByUserId(userId);

            android.util.Log.d("MainActivity", "Loaded " + wallets.size() + " wallets for user ID: " + userId);
            for (Wallet w : wallets) {
                android.util.Log.d("MainActivity", "  - Wallet: " + w.getName() + " (ID: " + w.getId() + ")");
            }

            // Auto-select first wallet if none is selected or if wallet doesn't belong to current user
            boolean needToSelectWallet = false;
            int newSelectedWalletId = selectedWalletId;
            String newCurrency = selectedWalletCurrency;

            if (!wallets.isEmpty()) {
                // Check if current selected wallet belongs to this user
                needToSelectWallet = (selectedWalletId == -1);

                if (selectedWalletId != -1) {
                    // Check if the selected wallet belongs to the current user
                    Wallet selectedWallet = db.walletDao().getWalletById(selectedWalletId);
                    if (selectedWallet == null || selectedWallet.getUserId() != userId) {
                        android.util.Log.d("MainActivity", "Selected wallet doesn't belong to user " + userId + ", resetting");
                        needToSelectWallet = true;
                    } else {
                        // Update currency from current selected wallet
                        newCurrency = selectedWallet.getCurrency() != null ? selectedWallet.getCurrency() : "VND";
                    }
                }

                if (needToSelectWallet) {
                    newSelectedWalletId = wallets.get(0).getId();
                    newCurrency = wallets.get(0).getCurrency() != null ? wallets.get(0).getCurrency() : "VND";
                    android.util.Log.d("MainActivity", "Auto-selected first wallet: ID " + newSelectedWalletId + " (" + wallets.get(0).getName() + "), Currency: " + newCurrency);
                }
            } else {
                android.util.Log.d("MainActivity", "No wallets available for user " + userId);
                newSelectedWalletId = -1;
                newCurrency = "VND";
            }

            final int finalWalletId = newSelectedWalletId;
            final String finalCurrency = newCurrency;
            final boolean walletChanged = (selectedWalletId != newSelectedWalletId);

            runOnUiThread(() -> {
                walletListContainer.removeAllViews();

                for (Wallet wallet : wallets) {
                    addWalletItemToPanel(wallet);
                }

                selectedWalletId = finalWalletId;
                selectedWalletCurrency = finalCurrency;

                android.util.Log.d("MainActivity", "Wallet items added to panel, selected wallet ID: " + selectedWalletId + ", Currency: " + selectedWalletCurrency);

                if (walletChanged) {
                    android.util.Log.d("MainActivity", "Wallet selection changed, triggering fragment refresh");
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        refreshCurrentFragment();
                    }, 100);
                }
            });
        }).start();
    }

    /**
     * Add a wallet item to the panel
     */
    private void addWalletItemToPanel(Wallet wallet) {
        View walletItemView = getLayoutInflater()
                .inflate(R.layout.wallet_item, walletListContainer, false);

        LinearLayout clickableArea = walletItemView.findViewById(R.id.wallet_clickable_area);
        if (clickableArea == null) {
            if (walletItemView instanceof LinearLayout) {
                LinearLayout parent = (LinearLayout) walletItemView;
                if (parent.getChildCount() > 0 && parent.getChildAt(0) instanceof LinearLayout) {
                    clickableArea = (LinearLayout) parent.getChildAt(0);
                }
            }
        }

        ImageView icon = walletItemView.findViewById(R.id.wallet_icon);
        TextView name = walletItemView.findViewById(R.id.wallet_name);
        TextView balance = walletItemView.findViewById(R.id.wallet_balance);

        name.setText(wallet.getName());
        balance.setText(String.format("%,.2f %s", wallet.getBalance(), wallet.getCurrency()));

        // Set icon based on wallet type
        switch (wallet.getType()) {
            case "cash":
                icon.setImageResource(R.drawable.ic_money_bundle);
                break;
            case "bank":
                icon.setImageResource(R.drawable.ic_bankcard);
                break;
            case "virtual":
                icon.setImageResource(R.drawable.ic_virtual_account);
                break;
            default:
                icon.setImageResource(R.drawable.ic_wallet1);
                break;
        }

        // Set click listener on the clickable area, not the root
        View finalClickableArea = clickableArea != null ? clickableArea : walletItemView;
        finalClickableArea.setOnClickListener(v -> {
            android.util.Log.d("MainActivity", "Wallet item clicked!");
            hideWalletPanel();
            int oldWalletId = selectedWalletId;
            selectedWalletId = wallet.getId();
            selectedWalletCurrency = wallet.getCurrency() != null ? wallet.getCurrency() : "VND";

            android.util.Log.d("MainActivity", "Wallet switched: " + oldWalletId + " -> " + selectedWalletId);
            android.util.Log.d("MainActivity", "Selected wallet: " + wallet.getName() + ", Currency: " + selectedWalletCurrency);

            Toast.makeText(this, "Chọn ví: " + wallet.getName(), Toast.LENGTH_SHORT).show();

            // Refresh current fragment with new wallet data
            refreshCurrentFragment();
        });

        walletListContainer.addView(walletItemView);
    }

    /**
     * Refresh the current fragment to reflect wallet changes
     */
    private void refreshCurrentFragment() {
        Fragment currentFragment = fragmentManager.findFragmentById(R.id.fragment_container);

        android.util.Log.d("MainActivity", "Refreshing fragment: " +
                (currentFragment != null ? currentFragment.getClass().getSimpleName() : "null"));
        android.util.Log.d("MainActivity", "Current user ID: " + currentUserId + ", Selected wallet ID: " + selectedWalletId);

        if (currentFragment instanceof HomeFragment) {
            android.util.Log.d("MainActivity", "Calling HomeFragment.refreshData()");
            ((HomeFragment) currentFragment).refreshData();
        } else if (currentFragment instanceof HistoryFragment) {
            android.util.Log.d("MainActivity", "Calling HistoryFragment.refreshData()");
            ((HistoryFragment) currentFragment).refreshData();
        } else if (currentFragment instanceof StatisticsFragment) {
            android.util.Log.d("MainActivity", "Calling StatisticsFragment.refreshData()");
            ((StatisticsFragment) currentFragment).refreshData();
        } else if (currentFragment instanceof BudgetFragment) {
            android.util.Log.d("MainActivity", "Calling BudgetFragment.refreshData()");
            ((BudgetFragment) currentFragment).refreshData();
        } else if (currentFragment != null) {
            // For other fragments, use detach/attach
            android.util.Log.d("MainActivity", "Using detach/attach for fragment refresh");
            fragmentManager.beginTransaction()
                    .detach(currentFragment)
                    .attach(currentFragment)
                    .commitAllowingStateLoss();
        }
    }

    // ================= Panel Management =================

    private void toggleWalletPanel() {
        if (walletPanel.getVisibility() == View.VISIBLE) {
            hideWalletPanel();
        } else {
            showWalletPanel();
        }
    }

    private void showWalletPanel() {
        walletPanel.setVisibility(View.VISIBLE);
    }

    private void hideWalletPanel() {
        walletPanel.setVisibility(View.GONE);
    }

    private void toggleSettingsPanel() {
        if (settingsPanel.getVisibility() == View.VISIBLE) {
            hideSettingsPanel();
        } else {
            showSettingsPanel();
        }
    }

    private void showSettingsPanel() {
        settingsPanel.setVisibility(View.VISIBLE);
    }

    private void hideSettingsPanel() {
        settingsPanel.setVisibility(View.GONE);
    }

    // ================= Navigation =================

    private void setupNavigationBar() {
        LinearLayout navHome = findViewById(R.id.nav_home);
        LinearLayout navAIChat = findViewById(R.id.nav_ai_chat);
        LinearLayout navStatistics = findViewById(R.id.nav_statistics);
        LinearLayout navMore = findViewById(R.id.nav_more);

        navHome.setOnClickListener(v -> loadFragment(new HomeFragment(), "Home"));
        navAIChat.setOnClickListener(v -> loadFragment(new AIChatFragment(), "AI Chat"));
        navStatistics.setOnClickListener(v -> loadFragment(new StatisticsFragment(), "Statistics"));
        navMore.setOnClickListener(v -> loadFragment(new MoreFragment(), "More"));
    }

    /**
     * Load fragment from MoreFragment (with back stack)
     */
    public void loadFragmentFromMore(Fragment fragment, String title) {
        showHeaderAndFooter();
        headerTitle.setText(title);

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(
                R.anim.fade_in_up,
                R.anim.fade_out_down,
                R.anim.fade_in_up,
                R.anim.fade_out_down
        );
        transaction.replace(R.id.fragment_container, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void loadFragment(Fragment fragment, String title) {
        // Show header and footer for normal fragments
        showHeaderAndFooter();

        headerTitle.setText(title);

        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.setCustomAnimations(
                R.anim.fade_in_up,
                R.anim.fade_out_down,
                R.anim.fade_in_up,
                R.anim.fade_out_down
        );
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

// ================= Header & Footer Management =================

    private void hideHeaderAndFooter() {
        if (headerLayout != null) {
            headerLayout.setVisibility(View.GONE);
        }
        if (headerDivider != null) {
            headerDivider.setVisibility(View.GONE);
        }
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(View.GONE);
        }
    }

    private void showHeaderAndFooter() {
        if (headerLayout != null) {
            headerLayout.setVisibility(View.VISIBLE);
        }
        if (headerDivider != null) {
            headerDivider.setVisibility(View.VISIBLE);
        }
        if (bottomNavigation != null) {
            bottomNavigation.setVisibility(View.VISIBLE);
        }
    }

// ================= Public Accessors & Helpers =================

    /**
     * Get the currently selected wallet ID
     */
    public static int getSelectedWalletId() {
        return selectedWalletId;
    }

    /**
     * Set the selected wallet ID
     */
    public static void setSelectedWalletId(int walletId) {
        selectedWalletId = walletId;
        android.util.Log.d("MainActivity", "Wallet ID set to: " + walletId);
    }

    /**
     * Get the current logged-in user ID (for use in fragments)
     */
    public static int getCurrentUserId() {
        return currentUserId;
    }

    /**
     * Get the selected wallet's currency (for use in fragments)
     */
    public static String getSelectedWalletCurrency() {
        return selectedWalletCurrency != null ? selectedWalletCurrency : "VND";
    }

    /**
     * Public method to hide header and footer (for use by fragments)
     */
    public void hideMainHeaderAndFooter() {
        hideHeaderAndFooter();
    }

    /**
     * Public method to show header and footer (for use by fragments)
     */
    public void showMainHeaderAndFooter() {
        showHeaderAndFooter();
    }


}