package com.example.mymoney.importer;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.R;
import com.example.mymoney.adapter.CategoryAdapter;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.view.ZoomableImageView;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Dialog for reviewing and editing batch-scanned receipts
 */
public class ReceiptReviewDialog extends DialogFragment {

    public interface ReviewListener {
        void onReceiptApproved(PendingReceipt receipt);
        void onReceiptDiscarded(PendingReceipt receipt);
        void onAllReviewsComplete(List<PendingReceipt> approvedReceipts);
        void onReviewCancelled();
    }

    private List<PendingReceipt> pendingReceipts;
    private List<PendingReceipt> approvedReceipts = new ArrayList<>();
    private int currentIndex = 0;
    private ReviewListener listener;

    // Views
    private TextView tvCounter;
    private ZoomableImageView imgReceipt;
    private FrameLayout processingOverlay;
    private LinearLayout errorOverlay;
    private TextView tvErrorMessage;
    private EditText edtAmount;
    private LinearLayout categorySelector;
    private ImageView imgCategoryIcon;
    private TextView tvCategory;
    private LinearLayout dateSelector;
    private TextView tvDate;
    private EditText edtNotes;
    private Button btnSkip;
    private Button btnApprove;
    private ImageButton btnClose;
    private ImageButton btnDiscard;

    private Category selectedCategory;
    private Calendar selectedDate;
    private List<Category> availableCategories;

    public static ReceiptReviewDialog newInstance(List<PendingReceipt> receipts) {
        ReceiptReviewDialog dialog = new ReceiptReviewDialog();
        dialog.pendingReceipts = new ArrayList<>(receipts);
        return dialog;
    }

    public void setReviewListener(ReviewListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.FullScreenDialog);
        selectedDate = Calendar.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_receipt_review, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupListeners();
        loadCategories();

        if (pendingReceipts != null && !pendingReceipts.isEmpty()) {
            displayReceipt(currentIndex);
        } else {
            dismiss();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void initViews(View view) {
        tvCounter = view.findViewById(R.id.tv_counter);
        imgReceipt = view.findViewById(R.id.img_receipt);
        processingOverlay = view.findViewById(R.id.processing_overlay);
        errorOverlay = view.findViewById(R.id.error_overlay);
        tvErrorMessage = view.findViewById(R.id.tv_error_message);
        edtAmount = view.findViewById(R.id.edt_amount);
        categorySelector = view.findViewById(R.id.category_selector);
        imgCategoryIcon = view.findViewById(R.id.img_category_icon);
        tvCategory = view.findViewById(R.id.tv_category);
        dateSelector = view.findViewById(R.id.date_selector);
        tvDate = view.findViewById(R.id.tv_date);
        edtNotes = view.findViewById(R.id.edt_notes);
        btnSkip = view.findViewById(R.id.btn_skip);
        btnApprove = view.findViewById(R.id.btn_approve);
        btnClose = view.findViewById(R.id.btn_close);
        btnDiscard = view.findViewById(R.id.btn_discard);
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReviewCancelled();
            }
            dismiss();
        });

        btnDiscard.setOnClickListener(v -> discardCurrentReceipt());

        btnSkip.setOnClickListener(v -> skipCurrentReceipt());

        btnApprove.setOnClickListener(v -> approveCurrentReceipt());

        categorySelector.setOnClickListener(v -> showCategoryDialog());

        dateSelector.setOnClickListener(v -> showDatePicker());
    }

    private void loadCategories() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                availableCategories = db.categoryDao().getAllExpenseCategories();

                if (!availableCategories.isEmpty() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (selectedCategory == null) {
                            selectedCategory = availableCategories.get(0);
                            updateCategoryDisplay();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void displayReceipt(int index) {
        if (index < 0 || index >= pendingReceipts.size()) {
            finishReview();
            return;
        }

        PendingReceipt receipt = pendingReceipts.get(index);

        // Update counter
        tvCounter.setText(String.format(Locale.getDefault(),
                "Receipt %d of %d", index + 1, pendingReceipts.size()));

        // Update button text
        if (index == pendingReceipts.size() - 1) {
            btnApprove.setText("Approve & Finish");
            btnSkip.setText("Skip & Finish");
        } else {
            btnApprove.setText("Approve & Next");
            btnSkip.setText("Skip");
        }

        // Display image with proper orientation
        File imageFile = receipt.getImageFile();
        if (imageFile != null && imageFile.exists()) {
            Bitmap bitmap = loadBitmapWithCorrectOrientation(imageFile.getAbsolutePath());
            if (bitmap != null) {
                imgReceipt.setImageBitmap(bitmap);
                imgReceipt.resetZoom();
            }
        }

        // Show processing state
        if (receipt.isProcessing()) {
            processingOverlay.setVisibility(View.VISIBLE);
            errorOverlay.setVisibility(View.GONE);
            return;
        }

        processingOverlay.setVisibility(View.GONE);

        // Show error state
        if (receipt.hasError()) {
            errorOverlay.setVisibility(View.VISIBLE);
            tvErrorMessage.setText(receipt.getErrorMessage());
        } else {
            errorOverlay.setVisibility(View.GONE);
        }

        // Populate form fields
        populateFormFields(receipt);
    }

    private void populateFormFields(PendingReceipt receipt) {
        // Amount
        Double amount = receipt.getEditedAmount();
        if (amount != null && amount > 0) {
            edtAmount.setText(String.format(Locale.US, "%.2f", amount));
        } else {
            edtAmount.setText("");
        }

        // Category
        String categoryName = receipt.getEditedCategory();
        if (categoryName != null && !categoryName.isEmpty() && availableCategories != null) {
            // Try to find matching category
            for (Category cat : availableCategories) {
                if (cat.getName().equalsIgnoreCase(categoryName)) {
                    selectedCategory = cat;
                    break;
                }
            }
        }
        updateCategoryDisplay();

        // Date
        String dateStr = receipt.getEditedDate();
        if (dateStr != null && !dateStr.isEmpty()) {
            parseAndSetDate(dateStr);
        } else {
            selectedDate = Calendar.getInstance();
        }
        updateDateDisplay();

        // Notes
        String merchant = receipt.getEditedMerchant();
        String notes = receipt.getEditedNotes();
        StringBuilder notesBuilder = new StringBuilder();
        if (merchant != null && !merchant.isEmpty()) {
            notesBuilder.append(merchant);
        }
        if (notes != null && !notes.isEmpty()) {
            if (notesBuilder.length() > 0) {
                notesBuilder.append("\n");
            }
            notesBuilder.append(notes);
        }
        edtNotes.setText(notesBuilder.toString());
    }

    private void parseAndSetDate(String dateStr) {
        // Try multiple date formats
        String[] formats = {
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "dd-MM-yyyy",
                "yyyy/MM/dd"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                Date date = sdf.parse(dateStr);
                if (date != null) {
                    selectedDate.setTime(date);
                    return;
                }
            } catch (ParseException ignored) {
            }
        }

        // If all formats fail, use current date
        selectedDate = Calendar.getInstance();
    }

    private void updateCategoryDisplay() {
        if (selectedCategory != null) {
            tvCategory.setText(selectedCategory.getName());
            // Try to set category icon
            try {
                int iconRes = requireContext().getResources().getIdentifier(
                        selectedCategory.getIcon(), "drawable", requireContext().getPackageName());
                if (iconRes != 0) {
                    imgCategoryIcon.setImageResource(iconRes);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            tvCategory.setText("Select Category");
        }
    }

    private void updateDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        tvDate.setText(sdf.format(selectedDate.getTime()));
    }

    private void showCategoryDialog() {
        if (availableCategories == null || availableCategories.isEmpty()) {
            Toast.makeText(requireContext(), "Loading categories...", Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_category_selection);

        RecyclerView recyclerView = dialog.findViewById(R.id.categories_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        CategoryAdapter adapter = new CategoryAdapter(category -> {
            selectedCategory = category;
            updateCategoryDisplay();
            dialog.dismiss();
        });

        adapter.setCategories(availableCategories);
        recyclerView.setAdapter(adapter);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
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

    private void saveEditedValuesToReceipt(PendingReceipt receipt) {
        // Amount
        String amountStr = edtAmount.getText().toString().trim();
        if (!TextUtils.isEmpty(amountStr)) {
            try {
                receipt.setEditedAmount(Double.parseDouble(amountStr));
            } catch (NumberFormatException e) {
                receipt.setEditedAmount(0.0);
            }
        }

        // Category
        if (selectedCategory != null) {
            receipt.setEditedCategory(selectedCategory.getName());
            receipt.setSelectedCategoryId(selectedCategory.getId());
        }

        // Date
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        receipt.setEditedDate(sdf.format(selectedDate.getTime()));

        // Notes
        receipt.setEditedNotes(edtNotes.getText().toString().trim());
    }

    private void approveCurrentReceipt() {
        if (currentIndex < 0 || currentIndex >= pendingReceipts.size()) {
            return;
        }

        // Validate amount
        String amountStr = edtAmount.getText().toString().trim();
        if (TextUtils.isEmpty(amountStr)) {
            Toast.makeText(requireContext(), "Please enter an amount", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                Toast.makeText(requireContext(), "Amount must be greater than zero", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedCategory == null) {
            Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }

        PendingReceipt receipt = pendingReceipts.get(currentIndex);
        saveEditedValuesToReceipt(receipt);
        approvedReceipts.add(receipt);

        if (listener != null) {
            listener.onReceiptApproved(receipt);
        }

        moveToNext();
    }

    private void skipCurrentReceipt() {
        moveToNext();
    }

    private void discardCurrentReceipt() {
        if (currentIndex < 0 || currentIndex >= pendingReceipts.size()) {
            return;
        }

        PendingReceipt receipt = pendingReceipts.get(currentIndex);

        if (listener != null) {
            listener.onReceiptDiscarded(receipt);
        }

        moveToNext();
    }

    private void moveToNext() {
        currentIndex++;
        if (currentIndex >= pendingReceipts.size()) {
            finishReview();
        } else {
            displayReceipt(currentIndex);
        }
    }

    private void finishReview() {
        if (listener != null) {
            listener.onAllReviewsComplete(approvedReceipts);
        }
        dismiss();
    }

    /**
     * Load bitmap and apply correct rotation based on EXIF orientation
     */
    private Bitmap loadBitmapWithCorrectOrientation(String imagePath) {
        try {
            // First decode the bitmap
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            if (bitmap == null) {
                return null;
            }

            // Read EXIF data to get orientation
            ExifInterface exif = new ExifInterface(imagePath);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );

            // Calculate rotation angle based on EXIF orientation
            int rotationAngle = 0;
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotationAngle = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotationAngle = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotationAngle = 270;
                    break;
                case ExifInterface.ORIENTATION_NORMAL:
                default:
                    rotationAngle = 0;
                    break;
            }

            // If no rotation needed, return original bitmap
            if (rotationAngle == 0) {
                return bitmap;
            }

            // Apply rotation
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationAngle);

            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(),
                    matrix, true
            );

            // Recycle original bitmap if different from rotated
            if (rotatedBitmap != bitmap) {
                bitmap.recycle();
            }

            return rotatedBitmap;
        } catch (IOException e) {
            android.util.Log.e("ReceiptReviewDialog", "Error reading EXIF data", e);
            // Fall back to just loading the bitmap without rotation
            return BitmapFactory.decodeFile(imagePath);
        }
    }
}