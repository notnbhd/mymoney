package com.example.mymoney;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.adapter.CategoryAdapter;
import com.example.mymoney.importer.BatchReceiptImportManager;
import com.example.mymoney.importer.PendingReceipt;
import com.example.mymoney.importer.ReceiptOcrResponse;
import com.example.mymoney.importer.ReceiptPhotoImportManager;
import com.example.mymoney.importer.ReceiptReviewDialog;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.database.entity.Transaction;
import com.example.mymoney.utils.BudgetExceedHelper;


import android.text.TextUtils;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.util.Calendar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ImportFragment extends Fragment {

    private LinearLayout expenseSelector, incomeSelector;
    private EditText amountInput, notesInput;
    private LinearLayout dateSelector, categorySelector;
    private TextView dateText, categoryText;
    private ImageView categoryIcon;
    private Button saveButton;

    // OCR related fields
    private LinearLayout btnCamera, btnScan, btnVoice;
    private ReceiptPhotoImportManager photoImportManager;
    private BatchReceiptImportManager batchReceiptManager;

    // Voice input constants
    private static final int REQUEST_RECORD_AUDIO = 200;
    private static final int REQUEST_SPEECH_INPUT = 201;

    private String selectedType = "expense"; // Default to expense
    private Calendar selectedDate;
    private int selectedCategoryId = -1; // Will be loaded from database
    private Category selectedCategory = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoImportManager = new ReceiptPhotoImportManager(this, new ReceiptPhotoImportManager.Listener() {
            @Override
            public void onProcessing() {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(), "Processing receipt...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSuccess(ReceiptOcrResponse.ReceiptData data) {
                if (!isAdded()) {
                    return;
                }
                if (data == null) {
                    Toast.makeText(requireContext(), "Empty response from receipt service", Toast.LENGTH_LONG).show();
                    return;
                }
                // Directly apply receipt data to form
                applyReceiptData(data);
                Toast.makeText(requireContext(), "Receipt data loaded. Please verify and save.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                Toast.makeText(requireContext(),
                        message != null ? message : "Failed to process receipt",
                        Toast.LENGTH_LONG).show();
            }
        });

        // Initialize batch receipt manager for multiple receipts
        batchReceiptManager = new BatchReceiptImportManager(this, new BatchReceiptImportManager.BatchListener() {
            @Override
            public void onProcessingStarted(int totalCount) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        "Processing " + totalCount + " receipt(s)...", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onReceiptProcessed(int index, PendingReceipt receipt) {
                // Individual receipt processed - could show progress
                if (!isAdded()) return;
                android.util.Log.d("ImportFragment", "Receipt " + (index + 1) + " processed");
            }

            @Override
            public void onAllProcessed(List<PendingReceipt> receipts) {
                if (!isAdded()) return;

                // Show review dialog for all receipts
                showBatchReviewDialog(receipts);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(),
                        message != null ? message : "Failed to process receipts",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_import, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        // Initialize views
        expenseSelector = view.findViewById(R.id.expense_selector);
        incomeSelector = view.findViewById(R.id.income_selector);
        amountInput = view.findViewById(R.id.amount_input);
        notesInput = view.findViewById(R.id.notes_input);
        dateSelector = view.findViewById(R.id.date_selector);
        dateText = view.findViewById(R.id.date_text);
        categorySelector = view.findViewById(R.id.category_selector);
        categoryText = view.findViewById(R.id.category_text);
        categoryIcon = view.findViewById(R.id.category_icon);
        saveButton = view.findViewById(R.id.save_button);
        btnCamera = view.findViewById(R.id.btnCamera);
        btnScan = view.findViewById(R.id.btnScan);
        btnVoice = view.findViewById(R.id.btnVoice);

        selectedDate = Calendar.getInstance();
        updateDateDisplay();

        setupListeners();

        loadDefaultCategory();
    }

    private void setupListeners() {
        // Transaction type selectors
        expenseSelector.setOnClickListener(v -> {
            selectTransactionType("expense");
            loadCategoriesForType("expense");
        });
        incomeSelector.setOnClickListener(v -> {
            selectTransactionType("income");
            loadCategoriesForType("income");
        });

        // Receipt photo import actions - using batch mode for multiple receipts
        btnCamera.setOnClickListener(v -> {
            if (batchReceiptManager != null) {
                // Use batch camera import - allows capturing multiple receipts
                batchReceiptManager.startCameraImport();
            }
        });
        if (btnScan != null) {
            btnScan.setOnClickListener(v -> {
                if (batchReceiptManager != null) {
                    // Use batch gallery import - allows selecting multiple images
                    batchReceiptManager.startGalleryImport();
                }
            });
        }

        // Voice input button
        if (btnVoice != null) {
            btnVoice.setOnClickListener(v -> startVoiceInput());
        }

        // Date selector
        dateSelector.setOnClickListener(v -> showDatePicker());

        // Category selector
        categorySelector.setOnClickListener(v -> showCategoryDialog());

        // Save button
        saveButton.setOnClickListener(v -> saveTransaction());
    }

    private void selectTransactionType(String type) {
        selectedType = type;

        if (type.equals("expense")) {
            expenseSelector.setBackgroundResource(R.drawable.selector_background);
            incomeSelector.setBackgroundResource(R.drawable.search_background);
            // Update text colors if needed
        } else {
            incomeSelector.setBackgroundResource(R.drawable.selector_background);
            expenseSelector.setBackgroundResource(R.drawable.search_background);
        }
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(Calendar.YEAR, year);
                    selectedDate.set(Calendar.MONTH, month);
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateDisplay();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void updateDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        dateText.setText(sdf.format(selectedDate.getTime()));
    }

    /**
     * Load the first available category from database as default
     */
    private void loadDefaultCategory() {
        loadCategoriesForType(selectedType);
    }

    /**
     * Load categories based on transaction type (expense or income)
     */
    private void loadCategoriesForType(String type) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());

                // Get categories by type
                List<Category> categories;
                if (type.equals("expense")) {
                    categories = db.categoryDao().getAllExpenseCategories();
                    android.util.Log.d("ImportFragment", "Loaded " + categories.size() + " expense categories");
                } else {
                    categories = db.categoryDao().getAllIncomeCategories();
                    android.util.Log.d("ImportFragment", "Loaded " + categories.size() + " income categories");
                }

                if (!categories.isEmpty() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Auto-select first category if none selected or type changed
                        if (selectedCategory == null || !selectedCategory.getType().equals(type)) {
                            selectedCategory = categories.get(0);
                            selectedCategoryId = selectedCategory.getId();
                            updateCategoryDisplay();
                            android.util.Log.d("ImportFragment", "Auto-selected category: " + selectedCategory.getName());
                        }
                    });
                } else {
                    android.util.Log.w("ImportFragment", "No categories found for type: " + type);
                }
            } catch (Exception e) {
                android.util.Log.e("ImportFragment", "Error loading categories", e);
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Show category selection dialog
     */
    private void showCategoryDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_category_selection);

        RecyclerView recyclerView = dialog.findViewById(R.id.categories_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        CategoryAdapter adapter = new CategoryAdapter(category -> {
            selectedCategory = category;
            selectedCategoryId = category.getId();
            updateCategoryDisplay();
            dialog.dismiss();
        });

        adapter.setSelectedCategoryId(selectedCategoryId);
        recyclerView.setAdapter(adapter);

        // Load categories based on current type
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                List<Category> categories;
                if (selectedType.equals("expense")) {
                    categories = db.categoryDao().getAllExpenseCategories();
                    android.util.Log.d("ImportFragment", "Dialog: Loaded " + categories.size() + " expense categories");
                } else {
                    categories = db.categoryDao().getAllIncomeCategories();
                    android.util.Log.d("ImportFragment", "Dialog: Loaded " + categories.size() + " income categories");
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.setCategories(categories);
                        android.util.Log.d("ImportFragment", "Dialog: Set " + categories.size() + " categories to adapter");
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("ImportFragment", "Dialog: Error loading categories", e);
                e.printStackTrace();
            }
        }).start();

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    /**
     * Update category display with selected category
     */
    private void updateCategoryDisplay() {
        if (selectedCategory != null) {
            categoryText.setText(selectedCategory.getName());

            // Set icon based on category name
            int iconRes;
            switch (selectedCategory.getName().toLowerCase()) {
                case "food":
                    iconRes = R.drawable.ic_food;
                    break;
                case "home":
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
            categoryIcon.setImageResource(iconRes);
            categoryIcon.setVisibility(View.VISIBLE);
        } else {
            categoryText.setText("Select Category");
            categoryIcon.setVisibility(View.GONE);
        }
    }

    private void applyReceiptData(ReceiptOcrResponse.ReceiptData data) {
        if (data == null || !isAdded()) {
            return;
        }

        Double totalAmount = data.getTotalAmount();
        if (totalAmount != null) {
            amountInput.setText(String.format(Locale.US, "%.2f", totalAmount));
        }

        String merchantName = data.getMerchantName();
        if (!TextUtils.isEmpty(merchantName)) {
            notesInput.setText(merchantName);
        }

        selectedType = "expense";
        selectTransactionType("expense");

        String mappedCategory = mapToLocalCategory(data.getExpenseCategory());
        if (!TextUtils.isEmpty(mappedCategory)) {
            assignCategoryByName(mappedCategory);
        }

        String receiptDate = data.getReceiptDate();
        if (!TextUtils.isEmpty(receiptDate)) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                selectedDate.setTime(sdf.parse(receiptDate));
                updateDateDisplay();
            } catch (Exception e) {
                android.util.Log.w("ImportFragment", "Unable to parse receipt date: " + receiptDate, e);
            }
        }
    }

    private String mapToLocalCategory(String remoteCategory) {
        if (TextUtils.isEmpty(remoteCategory)) {
            return "Others";
        }
        return remoteCategory.trim();
    }

    private void assignCategoryByName(String categoryName) {
        if (TextUtils.isEmpty(categoryName) || !isAdded()) {
            return;
        }

        Context appContext = requireContext().getApplicationContext();
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(appContext);
                List<Category> categories = db.categoryDao().getAllExpenseCategories();
                for (Category category : categories) {
                    if (category.getName().equalsIgnoreCase(categoryName)) {
                        selectedCategory = category;
                        selectedCategoryId = category.getId();
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(this::updateCategoryDisplay);
                        }
                        return;
                    }
                }
                android.util.Log.w("ImportFragment", "Category not found for receipt mapping: " + categoryName);
            } catch (Exception e) {
                android.util.Log.e("ImportFragment", "Error assigning category", e);
            }
        }).start();
    }

    /**
     * Show batch review dialog for multiple scanned receipts
     */
    private void showBatchReviewDialog(List<PendingReceipt> receipts) {
        if (receipts == null || receipts.isEmpty()) {
            Toast.makeText(requireContext(), "No receipts to review", Toast.LENGTH_SHORT).show();
            return;
        }

        ReceiptReviewDialog reviewDialog = ReceiptReviewDialog.newInstance(receipts);
        reviewDialog.setReviewListener(new ReceiptReviewDialog.ReviewListener() {
            @Override
            public void onReceiptApproved(PendingReceipt receipt) {
                android.util.Log.d("ImportFragment", "Receipt approved: " + receipt.getEditedAmount());
            }

            @Override
            public void onReceiptDiscarded(PendingReceipt receipt) {
                android.util.Log.d("ImportFragment", "Receipt discarded");
            }

            @Override
            public void onAllReviewsComplete(List<PendingReceipt> approvedReceipts) {
                if (approvedReceipts.isEmpty()) {
                    Toast.makeText(requireContext(), "No receipts approved", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Save all approved receipts as transactions
                saveBatchTransactions(approvedReceipts);
            }

            @Override
            public void onReviewCancelled() {
                Toast.makeText(requireContext(), "Review cancelled", Toast.LENGTH_SHORT).show();
            }
        });

        reviewDialog.show(getParentFragmentManager(), "receipt_review");
    }

    /**
     * Save multiple approved receipts as transactions
     */
    private void saveBatchTransactions(List<PendingReceipt> approvedReceipts) {
        int walletId = MainActivity.getSelectedWalletId();

        if (walletId == -1) {
            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                var wallets = db.walletDao().getActiveWalletsByUserId(MainActivity.getCurrentUserId());
                if (!wallets.isEmpty()) {
                    MainActivity.setSelectedWalletId(wallets.get(0).getId());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> saveBatchTransactionsWithWallet(approvedReceipts));
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "Please create a wallet first", Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            }).start();
            return;
        }

        saveBatchTransactionsWithWallet(approvedReceipts);
    }

    private void saveBatchTransactionsWithWallet(List<PendingReceipt> approvedReceipts) {
        // First, check all transactions for budget exceed in background
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                int walletId = MainActivity.getSelectedWalletId();
                int userId = MainActivity.getCurrentUserId();

                // Check which transactions exceed budget
                java.util.ArrayList<BudgetExceedHelper.BatchExceedItem> exceedItems = new java.util.ArrayList<>();
                java.util.ArrayList<Integer> exceedIndices = new java.util.ArrayList<>();

                for (int i = 0; i < approvedReceipts.size(); i++) {
                    PendingReceipt receipt = approvedReceipts.get(i);
                    Double amount = receipt.getEditedAmount();
                    int categoryId = receipt.getSelectedCategoryId();

                    if (amount != null && amount > 0 && categoryId != -1) {
                        // Check budget for this transaction
                        java.util.List<BudgetExceedHelper.BudgetExceedInfo> exceeded =
                                BudgetExceedHelper.checkBudgetsSync(requireContext(), categoryId, amount, walletId, userId);

                        if (!exceeded.isEmpty()) {
                            // Get category name
                            Category cat = db.categoryDao().getCategoryById(categoryId);
                            String catName = cat != null ? cat.getName() : "Unknown";

                            exceedItems.add(new BudgetExceedHelper.BatchExceedItem(i, catName, amount, exceeded));
                            exceedIndices.add(i);
                        }
                    }
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (exceedItems.isEmpty()) {
                            // No budgets exceeded, save all directly
                            proceedWithBatchSave(approvedReceipts, null);
                        } else {
                            // Show confirmation dialog for exceeded budgets
                            BudgetExceedHelper.showBatchExceedConfirmationDialog(requireContext(),
                                    exceedItems,
                                    new BudgetExceedHelper.BatchBudgetCheckCallback() {
                                        @Override
                                        public void onProceedAll() {
                                            // Save all transactions
                                            proceedWithBatchSave(approvedReceipts, null);
                                        }

                                        @Override
                                        public void onSkipExceeded() {
                                            // Skip transactions that exceed budget
                                            proceedWithBatchSave(approvedReceipts, exceedIndices);
                                        }

                                        @Override
                                        public void onDismissAll() {
                                            // Cancel all
                                            Toast.makeText(requireContext(), "Đã hủy tất cả khoản chi tiêu", Toast.LENGTH_SHORT).show();
                                            if (batchReceiptManager != null) {
                                                batchReceiptManager.cleanupTempFiles();
                                            }
                                        }
                                    });
                        }
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("ImportFragment", "Error checking batch budgets", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // If budget check fails, proceed with saving anyway
                        proceedWithBatchSave(approvedReceipts, null);
                    });
                }
            }
        }).start();
    }

    /**
     * Actually save the batch transactions after budget check
     * @param approvedReceipts All approved receipts
     * @param skipIndices Indices to skip (null to save all)
     */
    private void proceedWithBatchSave(List<PendingReceipt> approvedReceipts,
                                      java.util.ArrayList<Integer> skipIndices) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                int successCount = 0;
                int failCount = 0;
                int skippedCount = 0;

                for (int i = 0; i < approvedReceipts.size(); i++) {
                    // Check if this index should be skipped
                    if (skipIndices != null && skipIndices.contains(i)) {
                        skippedCount++;
                        continue;
                    }

                    PendingReceipt receipt = approvedReceipts.get(i);
                    try {
                        Transaction transaction = createTransactionFromReceipt(receipt);
                        if (transaction != null) {
                            db.transactionDao().insert(transaction);
                            updateWalletBalance(transaction);
                            successCount++;
                        } else {
                            failCount++;
                        }
                    } catch (Exception e) {
                        android.util.Log.e("ImportFragment", "Error saving receipt transaction", e);
                        failCount++;
                    }
                }

                final int finalSuccessCount = successCount;
                final int finalFailCount = failCount;
                final int finalSkippedCount = skippedCount;

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        String message;
                        if (finalFailCount == 0 && finalSkippedCount == 0) {
                            message = finalSuccessCount + " transaction(s) saved successfully!";
                        } else if (finalSkippedCount > 0) {
                            message = finalSuccessCount + " saved, " + finalSkippedCount + " skipped (over budget)";
                        } else {
                            message = finalSuccessCount + " saved, " + finalFailCount + " failed";
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();

                        // Cleanup temp files
                        if (batchReceiptManager != null) {
                            batchReceiptManager.cleanupTempFiles();
                        }

                        // Refresh other fragments
                        refreshHomeFragment();
                    });
                }
            } catch (Exception e) {
                android.util.Log.e("ImportFragment", "Error saving batch transactions", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Error saving transactions: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            }
        }).start();
    }

    private Transaction createTransactionFromReceipt(PendingReceipt receipt) {
        Double amount = receipt.getEditedAmount();
        if (amount == null || amount <= 0) {
            return null;
        }

        int categoryId = receipt.getSelectedCategoryId();
        if (categoryId == -1) {
            return null;
        }

        Transaction transaction = new Transaction();
        transaction.setWalletId(MainActivity.getSelectedWalletId());
        transaction.setCategoryId(categoryId);
        transaction.setUserId(MainActivity.getCurrentUserId());
        transaction.setAmount(amount);
        transaction.setType("expense"); // Receipts are always expenses
        transaction.setRecurring(false);

        // Set description from notes and merchant
        String notes = receipt.getEditedNotes();
        transaction.setDescription(notes != null ? notes : "");

        // Parse and set date
        String dateStr = receipt.getEditedDate();
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                transaction.setCreatedAt(sdf.parse(dateStr).getTime());
            } catch (Exception e) {
                transaction.setCreatedAt(System.currentTimeMillis());
            }
        } else {
            transaction.setCreatedAt(System.currentTimeMillis());
        }

        transaction.setUpdatedAt(System.currentTimeMillis());

        return transaction;
    }

    private void saveTransaction() {
        int walletId = MainActivity.getSelectedWalletId();

        if (walletId == -1) {
            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                var wallets = db.walletDao().getActiveWalletsByUserId(MainActivity.getCurrentUserId());
                if (!wallets.isEmpty()) {
                    MainActivity.setSelectedWalletId(wallets.get(0).getId());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(this::saveTransactionWithWallet);
                    }
                } else {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "Please create a wallet first", Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            }).start();
            return;
        }

        saveTransactionWithWallet();
    }

    private void saveTransactionWithWallet() {
        // Validate input
        String amountStr = amountInput.getText().toString().trim();
        if (amountStr.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if category is selected
        if (selectedCategoryId == -1 || selectedCategory == null) {
            Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                Toast.makeText(requireContext(), "Amount must be greater than 0", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        // Only check budget for expense type
        if (selectedType.equals("expense")) {
            int walletId = MainActivity.getSelectedWalletId();
            int userId = MainActivity.getCurrentUserId();

            // Check if expense will exceed any budget
            BudgetExceedHelper.checkAndConfirm(requireContext(), selectedCategoryId, amount,
                    walletId, userId, new BudgetExceedHelper.BudgetCheckCallback() {
                        @Override
                        public void onProceed() {
                            // User confirmed to proceed, save the transaction
                            proceedWithSaveTransaction(amount);
                        }

                        @Override
                        public void onDismiss() {
                            // User dismissed the expense
                            Toast.makeText(requireContext(), "Chi tiêu đã bị hủy", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            // For income, save directly without budget check
            proceedWithSaveTransaction(amount);
        }
    }

    /**
     * Actually save the transaction after budget check passed or confirmed
     */
    private void proceedWithSaveTransaction(double amount) {
        // Create transaction object
        Transaction transaction = new Transaction();
        transaction.setWalletId(MainActivity.getSelectedWalletId());
        transaction.setCategoryId(selectedCategoryId);
        transaction.setUserId(MainActivity.getCurrentUserId());
        transaction.setAmount(amount);
        transaction.setDescription(notesInput.getText().toString().trim());
        transaction.setType(selectedType);
        transaction.setCreatedAt(selectedDate.getTimeInMillis());
        transaction.setUpdatedAt(System.currentTimeMillis());

        // Set non-recurring by default
        transaction.setRecurring(false);
        transaction.setRecurringInterval(null);

        // Save to database in background thread
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                long transactionId = db.transactionDao().insert(transaction);

                // Update wallet balance
                updateWalletBalance(transaction);

                // Show success message on UI thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Transaction saved successfully!", Toast.LENGTH_SHORT).show();
                        clearForm();

                        // Refresh HomeFragment if it exists
                        refreshHomeFragment();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Error saving transaction: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    private void updateWalletBalance(Transaction transaction) {
        try {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            com.example.mymoney.database.entity.Wallet wallet =
                    db.walletDao().getWalletById(transaction.getWalletId());

            if (wallet != null) {
                double currentBalance = wallet.getBalance();
                double newBalance;

                if (transaction.getType().equals("income")) {
                    newBalance = currentBalance + transaction.getAmount();
                } else {
                    newBalance = currentBalance - transaction.getAmount();
                }

                wallet.setBalance(newBalance);
                db.walletDao().update(wallet);

                android.util.Log.d("ImportFragment", "Wallet balance updated: " + currentBalance + " -> " + newBalance);
            }
        } catch (Exception e) {
            android.util.Log.e("ImportFragment", "Error updating wallet balance", e);
        }
    }

    private void refreshHomeFragment() {
        // Refresh HomeFragment and HistoryFragment after saving transaction
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            androidx.fragment.app.FragmentManager fragmentManager = mainActivity.getSupportFragmentManager();

            // Find and refresh HomeFragment if it exists
            for (androidx.fragment.app.Fragment fragment : fragmentManager.getFragments()) {
                if (fragment instanceof HomeFragment && fragment.isAdded()) {
                    ((HomeFragment) fragment).refreshData();
                    android.util.Log.d("ImportFragment", "HomeFragment refreshed");
                }
                if (fragment instanceof HistoryFragment && fragment.isAdded()) {
                    ((HistoryFragment) fragment).refreshData();
                    android.util.Log.d("ImportFragment", "HistoryFragment refreshed");
                }
            }
        }
    }

    private void clearForm() {
        amountInput.setText("");
        notesInput.setText("");
        selectedDate = Calendar.getInstance();
        updateDateDisplay();
        selectedType = "expense";
        selectTransactionType("expense");
    }

    // ==================== VOICE INPUT METHODS ====================

    /**
     * Start voice recognition intent
     */
    private void startVoiceInput() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói thông tin chi tiêu...");

        try {
            startActivityForResult(intent, REQUEST_SPEECH_INPUT);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Không thể khởi động trình nhận giọng nói!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handle activity results for voice input
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SPEECH_INPUT && resultCode == requireActivity().RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0);
                processVoiceInput(spokenText);
            }
        }
    }

    /**
     * Handle permission results for voice input
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceInput();
            } else {
                Toast.makeText(requireContext(), "Bạn cần cấp quyền Microphone để sử dụng tính năng này!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Process voice input text and extract transaction data
     */
    private void processVoiceInput(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();

        // ========== 1️⃣ Detect transaction type ==========
        String type = "expense"; // Default to expense
        if (normalized.contains("thu nhập") || normalized.contains("nhận") ||
                normalized.contains("lương") || normalized.contains("được") ||
                normalized.contains("income")) {
            type = "income";
        } else if (normalized.contains("chi") || normalized.contains("mua") ||
                normalized.contains("trả") || normalized.contains("expense")) {
            type = "expense";
        }

        // ========== 2️⃣ Detect amount ==========
        long amount = 0L;

        Matcher mix = Pattern.compile("(\\d+)\\s*triệu.*?(\\d+)\\s*(nghìn|ngàn)").matcher(normalized);
        if (mix.find()) {
            long trieu = Long.parseLong(mix.group(1));
            long nghin = Long.parseLong(mix.group(2));
            amount = trieu * 1_000_000 + nghin * 1_000;
        } else if (normalized.contains("triệu")) {
            Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*triệu").matcher(normalized);
            if (m.find()) {
                double num = Double.parseDouble(m.group(1).replace(",", "."));
                amount = (long) (num * 1_000_000);
            }
        } else if (normalized.contains("nghìn") || normalized.contains("ngàn")) {
            Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(nghìn|ngàn)").matcher(normalized);
            if (m.find()) {
                double num = Double.parseDouble(m.group(1).replace(",", "."));
                amount = (long) (num * 1_000);
            }
        } else {
            Matcher m = Pattern.compile("(\\d{1,3}(?:[.,]\\d{3})+|\\d+)").matcher(normalized);
            if (m.find()) {
                String numStr = m.group(1).replaceAll("[^\\d]", "");
                try {
                    amount = Long.parseLong(numStr);
                } catch (NumberFormatException ignored) {}
            }
        }

        // ========== 3️⃣ Detect category ==========
        String category = "Others";
        if (type.equals("expense")) {
            if (normalized.contains("ăn") || normalized.contains("uống"))
                category = "Food";
            else if (normalized.contains("xăng") || normalized.contains("đi lại") || normalized.contains("xe"))
                category = "Transport";
            else if (normalized.contains("nhà"))
                category = "Home";
            else if (normalized.contains("chơi") || normalized.contains("phim") || normalized.contains("giải trí"))
                category = "Entertainment";
            else if (normalized.contains("quà") || normalized.contains("hoa") || normalized.contains("yêu"))
                category = "Relationship";
            else if (normalized.contains("thuốc") || normalized.contains("bệnh viện") || normalized.contains("khám"))
                category = "Medical";
            else if (normalized.contains("quần áo") || normalized.contains("mặc"))
                category = "Clothing";
            else if (normalized.contains("học") || normalized.contains("sách"))
                category = "Education";
            else if (normalized.contains("gym") || normalized.contains("tập"))
                category = "Gym & Fitness";
            else if (normalized.contains("đẹp") || normalized.contains("spa"))
                category = "Beauty";
        } else { // income
            if (normalized.contains("lương") || normalized.contains("salary"))
                category = "Salary";
            else if (normalized.contains("kinh doanh") || normalized.contains("bán"))
                category = "Business";
            else if (normalized.contains("thưởng") || normalized.contains("tặng"))
                category = "Gifts";
        }

        // ========== 4️⃣ Detect date ==========
        String date = "";
        Matcher d1 = Pattern.compile("(\\d{1,2}[\\-/]\\d{1,2}[\\-/]\\d{2,4})").matcher(normalized);
        if (d1.find()) {
            date = d1.group(1);
        } else {
            Matcher d2 = Pattern.compile("(\\d{1,2})\\s*tháng\\s*(\\d{1,2})(?:\\s*năm\\s*(\\d{4}))?").matcher(normalized);
            if (d2.find()) {
                String day = d2.group(1);
                String month = d2.group(2);
                String year = (d2.group(3) != null)
                        ? d2.group(3)
                        : String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
                date = day + "/" + month + "/" + year;
            }
        }

        // ========== 5️⃣ Show confirmation dialog ==========
        final String amountStr = (amount > 0) ? String.valueOf(amount) : "";
        final String finalCategory = category;
        final String finalDate = date;
        final String finalType = type;

        requireActivity().runOnUiThread(() -> {
            showVoiceConfirmDialog(finalType, amountStr, finalCategory, finalDate);
        });
    }

    /**
     * Show confirmation dialog for voice input data
     */
    private void showVoiceConfirmDialog(String type, String amount, String categoryName, String date) {
        if (getActivity() == null) return;

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ocr_confirm);

        TextView tvType = dialog.findViewById(R.id.tvType);
        TextView tvAmount = dialog.findViewById(R.id.tvAmount);
        TextView tvCategory = dialog.findViewById(R.id.tvCategory);
        TextView tvDate = dialog.findViewById(R.id.tvDate);
        Button btnConfirm = dialog.findViewById(R.id.btnConfirm);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        // Format amount nicely
        String formattedAmount = "Chưa nhận diện được";
        if (!amount.isEmpty()) {
            try {
                long amt = Long.parseLong(amount);
                formattedAmount = String.format("%,d VND", amt);
            } catch (Exception e) {
                formattedAmount = amount + " VND";
            }
        }

        // Display data
        tvType.setText(type.equals("income") ? "Income" : "Expense");
        tvAmount.setText(formattedAmount);
        tvCategory.setText(categoryName);
        tvDate.setText(date.isEmpty() ? "Hôm nay" : date);

        // Cancel button
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Confirm button
        final String finalAmount = amount;
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();

            // Save transaction directly to database
            new Thread(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(requireContext());

                    // Get current wallet
                    int walletId = MainActivity.getSelectedWalletId();
                    if (walletId == -1) {
                        var wallets = db.walletDao().getActiveWalletsByUserId(MainActivity.getCurrentUserId());
                        if (!wallets.isEmpty()) walletId = wallets.get(0).getId();
                        else {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(), "Bạn cần tạo ví trước!", Toast.LENGTH_SHORT).show());
                            }
                            return;
                        }
                    }

                    // Parse amount and date
                    double money = 0;
                    try { money = Double.parseDouble(finalAmount); } catch (Exception ignored) {}
                    long timeMillis = System.currentTimeMillis();
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        if (!date.isEmpty()) {
                            timeMillis = sdf.parse(date).getTime();
                        }
                    } catch (Exception ignored) {}

                    // Find category
                    Category matched = null;
                    List<Category> list = type.equals("income")
                            ? db.categoryDao().getAllIncomeCategories()
                            : db.categoryDao().getAllExpenseCategories();

                    for (Category c : list) {
                        if (c.getName().equalsIgnoreCase(categoryName)) {
                            matched = c;
                            break;
                        }
                    }

                    if (matched == null && !list.isEmpty()) matched = list.get(0);

                    // Create Transaction
                    Transaction t = new Transaction();
                    t.setWalletId(walletId);
                    t.setUserId(MainActivity.getCurrentUserId());
                    t.setType(type);
                    t.setAmount(money);
                    t.setCategoryId(matched != null ? matched.getId() : -1);
                    t.setDescription("");
                    t.setCreatedAt(timeMillis);
                    t.setUpdatedAt(System.currentTimeMillis());
                    t.setRecurring(false);

                    long id = db.transactionDao().insert(t);

                    // Update wallet balance
                    com.example.mymoney.database.entity.Wallet wallet =
                            db.walletDao().getWalletById(walletId);
                    if (wallet != null) {
                        double newBalance = wallet.getBalance() +
                                (type.equals("income") ? money : -money);
                        wallet.setBalance(newBalance);
                        db.walletDao().update(wallet);
                    }

                    // Show success message
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                    "Đã lưu giao dịch " + (type.equals("income") ? "thu nhập" : "chi tiêu") + " thành công!",
                                    Toast.LENGTH_SHORT).show();

                            // Refresh Home / History
                            refreshHomeFragment();
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "Lỗi khi lưu dữ liệu: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }
            }).start();
        });

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

}