package com.example.mymoney;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.adapter.CategoryAdapter;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.database.entity.Transaction;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.Manifest;
import android.speech.RecognizerIntent;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;


public class ImportFragment extends Fragment {

    private LinearLayout expenseSelector, incomeSelector;
    private EditText amountInput, notesInput;
    private LinearLayout dateSelector, categorySelector;
    private TextView dateText, categoryText;
    private ImageView categoryIcon;
    private RadioGroup repeatRadioGroup;
    private RadioButton repeatYes, repeatNo;
    private LinearLayout recurringSection;
    private Spinner recurringSpinner;
    private Button saveButton;

    // OCR related fields
    private LinearLayout btnCamera;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private Uri imageUri;
    private static final int CAMERA_PERMISSION_CODE = 100;

    private String selectedType = "expense"; // Default to expense
    private Calendar selectedDate;
    private int selectedCategoryId = -1; // Will be loaded from database
    private Category selectedCategory = null;
    // 🎤 Voice input fields
    private LinearLayout btnVoice;
    private static final int REQUEST_RECORD_AUDIO = 200;
    private static final int REQUEST_SPEECH_INPUT = 201;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_import, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        btnVoice = view.findViewById(R.id.btnVoice);
        setupVoiceButton();


        // Initialize OCR launchers first
        setupOCRLaunchers();

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
        repeatRadioGroup = view.findViewById(R.id.repeat_radio_group);
        repeatYes = view.findViewById(R.id.repeat_yes);
        repeatNo = view.findViewById(R.id.repeat_no);
        recurringSection = view.findViewById(R.id.recurring_section);
        recurringSpinner = view.findViewById(R.id.recurring_spinner);
        saveButton = view.findViewById(R.id.save_button);
        btnCamera = view.findViewById(R.id.btnCamera);

        // Initialize selected date to today
        selectedDate = Calendar.getInstance();
        updateDateDisplay();

        // Set up recurring spinner
        setupRecurringSpinner();

        // Set up click listeners
        setupListeners();

        // Set up OCR button
        setupOCRButton();

        // Load default category from database
        loadDefaultCategory();
    }

    private void setupRecurringSpinner() {
        String[] recurringOptions = {
                getString(R.string.daily),
                getString(R.string.weekly),
                getString(R.string.monthly),
                getString(R.string.yearly)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                recurringOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        recurringSpinner.setAdapter(adapter);
        recurringSpinner.setSelection(2); // Default to Monthly
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

        // Date selector
        dateSelector.setOnClickListener(v -> showDatePicker());

        // Category selector
        categorySelector.setOnClickListener(v -> showCategoryDialog());

        // Repeat radio group
        repeatRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.repeat_yes) {
                recurringSection.setVisibility(View.VISIBLE);
            } else {
                recurringSection.setVisibility(View.GONE);
            }
        });

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

    private void saveTransaction() {
        // Get selected wallet from MainActivity
        int walletId = MainActivity.getSelectedWalletId();

        // If no wallet selected, try to get the first available wallet
        if (walletId == -1) {
            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                var wallets = db.walletDao().getActiveWalletsByUserId(MainActivity.getCurrentUserId());
                if (!wallets.isEmpty()) {
                    MainActivity.setSelectedWalletId(wallets.get(0).getId());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> saveTransactionWithWallet());
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

        // Handle recurring
        boolean isRecurring = repeatYes.isChecked();
        transaction.setRecurring(isRecurring);

        if (isRecurring) {
            String[] intervals = {"daily", "weekly", "monthly", "yearly"};
            int selectedPosition = recurringSpinner.getSelectedItemPosition();
            transaction.setRecurringInterval(intervals[selectedPosition]);
        } else {
            transaction.setRecurringInterval(null);
        }

        // Save to database in background thread
        // Save to database in background thread
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                long transactionId = db.transactionDao().insert(transaction);

                // Update wallet balance
                updateWalletBalance(transaction);

                // =========================
                // CHECK LIMIT — chạy ở background, KHÔNG CRASH
                // =========================
                BudgetChecker.checkAllGoalsBackground(
                        requireContext(),
                        selectedCategory.getName(),
                        amount
                );

                // Show success message on UI thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                                "Transaction saved successfully!", Toast.LENGTH_SHORT).show();
                        clearForm();
                        refreshHomeFragment();
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(),
                                    "Error saving transaction: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show()
                    );
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
        repeatNo.setChecked(true);
        recurringSection.setVisibility(View.GONE);
        selectedDate = Calendar.getInstance();
        updateDateDisplay();
        selectedType = "expense";
        selectTransactionType("expense");
    }

    // ==================== OCR Methods ====================

    /**
     * Setup OCR activity result launchers
     */
    private void setupOCRLaunchers() {
        // Launcher for Camera
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == requireActivity().RESULT_OK) {
                        if (imageUri != null) {
                            Toast.makeText(requireContext(), "Ảnh đã chụp xong!", Toast.LENGTH_SHORT).show();
                            processImage(imageUri);
                        }
                    }
                }
        );

        // Launcher for Gallery
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == requireActivity().RESULT_OK && result.getData() != null) {
                        Uri selectedImage = result.getData().getData();
                        Toast.makeText(requireContext(), "Đã chọn ảnh từ thư viện!", Toast.LENGTH_SHORT).show();
                        processImage(selectedImage);
                    }
                }
        );
    }

    /**
     * Setup OCR button click listener
     */
    private void setupOCRButton() {
        if (btnCamera != null) {
            btnCamera.setOnClickListener(v -> {
                String[] options = {"Chụp ảnh", "Chọn từ thư viện"};
                new AlertDialog.Builder(requireContext())
                        .setTitle("Nhập dữ liệu bằng ảnh")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                checkCameraPermissionAndOpen();
                            } else {
                                openGallery();
                            }
                        })
                        .show();
            });
        }
    }

    /**
     * Check camera permission and open camera
     */
    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            openCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(requireContext(), "Bạn cần cấp quyền Camera để chụp ảnh!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Open camera to capture image
     */
    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = new File(requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "temp.jpg");
        imageUri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", photoFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        cameraLauncher.launch(intent);
    }

    /**
     * Open gallery to select image
     */
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    /**
     * Process image using ML Kit OCR
     */
    private void processImage(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(requireContext(), uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String rawText = visionText.getText();
                        Toast.makeText(requireContext(), "OCR thành công!", Toast.LENGTH_SHORT).show();
                        handleExtractedText(rawText);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(requireContext(), "Lỗi OCR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Lỗi xử lý ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Extract transaction data from OCR text
     */
    private void handleExtractedText(String text) {
        String type = "";
        String category = "";
        String date = "";
        String amount = "";

        // Làm sạch text OCR
        String cleanedText = text.replaceAll("[^\\dA-Za-zÀ-ỹ\\s/\\-.,]", " ").toLowerCase(Locale.ROOT);
        android.util.Log.d("OCR_TEXT", "Raw: " + text);
        android.util.Log.d("OCR_TEXT", "Cleaned: " + cleanedText);

        // 1️⃣ Nhận diện loại giao dịch
        if (cleanedText.contains("chi") || cleanedText.contains("expense")) {
            type = "expense";
        } else if (cleanedText.contains("thu") || cleanedText.contains("income")) {
            type = "income";
        }

        // 2️⃣ Nhận diện số tiền (ưu tiên số lớn nhất)
        // 2️⃣ Nhận diện số tiền chính xác hơn (tìm số dài nhất, bỏ dấu và VND)
        Pattern amountPattern = Pattern.compile("(\\d{3,}(?:[.,]\\d{3,})*)");
        Matcher matcher = amountPattern.matcher(text.replaceAll("[^0-9.,]", " "));
        String longestNum = "";
        while (matcher.find()) {
            String numStr = matcher.group(1);
            if (numStr.length() > longestNum.length()) {
                longestNum = numStr;
            }
        }

        if (!longestNum.isEmpty()) {
            // Xóa dấu chấm, dấu phẩy và ký tự tiền tệ
            String cleaned = longestNum.replaceAll("[.,]", "").replaceAll("[^0-9]", "");
            try {
                long value = Long.parseLong(cleaned);
                amount = String.valueOf(value);
            } catch (NumberFormatException ignored) {}
        }


        // 3️⃣ Nhận diện ngày
        Pattern datePattern = Pattern.compile("(\\d{1,2}[\\-/]\\d{1,2}[\\-/]\\d{2,4})");
        Matcher dateMatcher = datePattern.matcher(text);
        if (dateMatcher.find()) {
            date = dateMatcher.group(1);
            parseAndSetDate(date);
        }

        // 4️⃣ Nhận diện danh mục
        if (cleanedText.contains("ăn") || cleanedText.contains("food")) {
            category = "Food";
        } else if (cleanedText.contains("xe") || cleanedText.contains("transport")) {
            category = "Transport";
        } else if (cleanedText.contains("nhà") || cleanedText.contains("home")) {
            category = "Home";
        } else if (cleanedText.contains("giải trí") || cleanedText.contains("entertainment")) {
            category = "Entertainment";
        } else if (cleanedText.contains("lương") || cleanedText.contains("salary")) {
            category = "Salary";
        } else if (cleanedText.contains("kinh doanh") || cleanedText.contains("business")) {
            category = "Business";
        } else {
            category = "Others";
        }

        // ✅ Gọi hàm hiển thị confirm dialog
        fillFormFromOCR(type, amount, category, date);
    }


    /**
     * Parse date string and set selectedDate
     */
    private void parseAndSetDate(String dateString) {
        try {
            SimpleDateFormat sdf;
            if (dateString.contains("/")) {
                sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            } else {
                sdf = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            }

            selectedDate.setTime(sdf.parse(dateString));
            updateDateDisplay();
        } catch (Exception e) {
            android.util.Log.e("ImportFragment", "Error parsing date: " + dateString, e);
        }
    }

    /**
     * Fill form with OCR extracted data
     */
    private void fillFormFromOCR(String type, String amount, String categoryName, String date) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                showOCRConfirmDialog(type, amount, categoryName, date);
            });
        }
    }

    /**
     * Find and set category by name
     */
    private void findAndSetCategory(String categoryName) {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                List<Category> categories;

                if (selectedType.equals("expense")) {
                    categories = db.categoryDao().getAllExpenseCategories();
                } else {
                    categories = db.categoryDao().getAllIncomeCategories();
                }

                // Find matching category
                Category matchedCategory = null;
                for (Category cat : categories) {
                    if (cat.getName().equalsIgnoreCase(categoryName)) {
                        matchedCategory = cat;
                        break;
                    }
                }

                if (matchedCategory != null) {
                    Category finalCategory = matchedCategory;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            selectedCategory = finalCategory;
                            selectedCategoryId = finalCategory.getId();
                            updateCategoryDisplay();
                        });
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("ImportFragment", "Error finding category: " + categoryName, e);
            }
        }).start();
    }
    /**
     * Hiển thị hộp xác nhận trước khi lưu dữ liệu OCR vào CSDL
     */
    private void showOCRConfirmDialog(String type, String amount, String categoryName, String date) {
        if (getActivity() == null) return;

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ocr_confirm);

        TextView tvType = dialog.findViewById(R.id.tvType);
        TextView tvAmount = dialog.findViewById(R.id.tvAmount);
        TextView tvCategory = dialog.findViewById(R.id.tvCategory);
        TextView tvDate = dialog.findViewById(R.id.tvDate);
        EditText etNote = dialog.findViewById(R.id.etNote);
        Button btnConfirm = dialog.findViewById(R.id.btnConfirm);
        Button btnCancel = dialog.findViewById(R.id.btnCancel);

        // Gán dữ liệu OCR vào giao diện
        tvType.setText(type.equals("expense") ? "Expense" : "Income");
        tvAmount.setText(amount + " VND");
        tvCategory.setText(categoryName);
        tvDate.setText(date);

        // Nút hủy
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // Nút xác nhận
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();

            // Gán dữ liệu vào form chính
            amountInput.setText(amount);
            notesInput.setText(etNote.getText().toString());
            selectedType = type;
            selectTransactionType(type);
            loadCategoriesForType(type);
            findAndSetCategory(categoryName);
            parseAndSetDate(date);

            // Lưu vào database
            saveTransactionWithWallet();
        });

        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
    // ==================== VOICE INPUT METHODS ====================

    /**
     * Setup Voice button
     */
    private void setupVoiceButton() {
        if (btnVoice != null) {
            btnVoice.setOnClickListener(v -> startVoiceInput());
        }
    }

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
     * Nhận kết quả giọng nói
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
     * Phân tích text nhận được từ giọng nói
     */
    /**
     * Phân tích text nhận được từ giọng nói và mở dialog xác nhận
     */
    /**
     * Phân tích text nhận được từ giọng nói và mở dialog xác nhận
     */
    /**
     * Phân tích text nhận được từ giọng nói
     */
    private void processVoiceInput(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();

        // ========== 1️⃣ Nhận dạng loại giao dịch ==========
        String type = "expense"; // Mặc định là chi tiêu
        if (normalized.contains("thu nhập") || normalized.contains("nhận") ||
                normalized.contains("lương") || normalized.contains("được") ||
                normalized.contains("income")) {
            type = "income";
        } else if (normalized.contains("chi") || normalized.contains("mua") ||
                normalized.contains("trả") || normalized.contains("expense")) {
            type = "expense";
        }

        // ========== 2️⃣ Nhận dạng số tiền ==========
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

        // ========== 3️⃣ Nhận dạng danh mục ==========
        String category = "Others";
        if (type.equals("expense")) {
            if (normalized.contains("ăn") || normalized.contains("uống"))
                category = "Food";
            else if (normalized.contains("xăng") || normalized.contains("đi lại"))
                category = "Transport";
            else if (normalized.contains("nhà"))
                category = "Home";
            else if (normalized.contains("chơi") || normalized.contains("phim"))
                category = "Entertainment";
            else if (normalized.contains("quà") || normalized.contains("hoa"))
                category = "Relationship";
        } else { // income
            if (normalized.contains("lương") || normalized.contains("salary"))
                category = "Salary";
            else if (normalized.contains("kinh doanh") || normalized.contains("bán"))
                category = "Business";
            else if (normalized.contains("thưởng") || normalized.contains("tặng"))
                category = "Gifts";
            else if (normalized.contains("khác"))
                category="Others";
        }

        // ========== 4️⃣ Nhận dạng ngày ==========
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

        // ========== 5️⃣ Hiển thị dialog xác nhận ==========
        final String amountStr = (amount > 0) ? String.valueOf(amount) : "";
        final String finalCategory = category;
        final String finalDate = date;
        final String finalType = type;

        requireActivity().runOnUiThread(() -> {
            showVoiceConfirmDialog(finalType, amountStr, finalCategory, finalDate);
        });
    }


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

        // 🔹 Format số tiền đẹp
        String formattedAmount = "Chưa nhận diện được";
        if (!amount.isEmpty()) {
            try {
                long amt = Long.parseLong(amount);
                formattedAmount = String.format("%,d VND", amt);
            } catch (Exception e) {
                formattedAmount = amount + " VND";
            }
        }

        // 🔹 Hiển thị dữ liệu
        tvType.setText(type.equals("income") ? "Income" : "Expense");
        tvAmount.setText(formattedAmount);
        tvCategory.setText(categoryName);
        tvDate.setText(date.isEmpty() ? "Không có ngày" : date);

        // ❌ Nút Cancel
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        // ✅ Nút Confirm
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();

            // 1️⃣ Gán dữ liệu vào các biến chính
            selectedType = type;
            selectTransactionType(type);
            loadCategoriesForType(type);
            findAndSetCategory(categoryName);
            parseAndSetDate(date);

            // 2️⃣ Tạo transaction và lưu luôn vào database
            new Thread(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(requireContext());

                    // Lấy ví hiện tại (hoặc ví đầu tiên)
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

                    // 3️⃣ Parse ngày và số tiền
                    double money = 0;
                    try { money = Double.parseDouble(amount); } catch (Exception ignored) {}
                    long timeMillis = System.currentTimeMillis();
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                        timeMillis = sdf.parse(date).getTime();
                    } catch (Exception ignored) {}

                    // 4️⃣ Tìm danh mục
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

                    // 5️⃣ Tạo Transaction mới
                    Transaction t = new Transaction();
                    t.setWalletId(walletId);
                    t.setUserId(MainActivity.getCurrentUserId());
                    t.setType(type);
                    t.setAmount(money);
                    t.setCategoryId(matched != null ? matched.getId() : -1);
                    t.setDescription(""); // không cần note
                    t.setCreatedAt(timeMillis);
                    t.setUpdatedAt(System.currentTimeMillis());
                    t.setRecurring(false);

                    long id = db.transactionDao().insert(t);

                    // 6️⃣ Cập nhật số dư ví
                    com.example.mymoney.database.entity.Wallet wallet =
                            db.walletDao().getWalletById(walletId);
                    if (wallet != null) {
                        double newBalance = wallet.getBalance() +
                                (type.equals("income") ? money : -money);
                        wallet.setBalance(newBalance);
                        db.walletDao().update(wallet);
                    }

                    // 7️⃣ Thông báo thành công
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(),
                                    "Đã lưu giao dịch " + (type.equals("income") ? "thu nhập" : "chi tiêu") + " thành công!",
                                    Toast.LENGTH_SHORT).show();

                            // Làm mới dữ liệu ở Home / History
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


    /**
     * Hàm tách số từ chuỗi (dùng cho nhận diện giọng nói)
     */
    private int extractNumber(String text) {
        text = text.replaceAll("[^0-9]", " ");
        String[] parts = text.trim().split("\\s+");
        if (parts.length > 0) {
            try {
                return Integer.parseInt(parts[0]);
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }




}