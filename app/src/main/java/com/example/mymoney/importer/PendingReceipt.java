package com.example.mymoney.importer;

import java.io.File;

/**
 * Represents a pending receipt to be reviewed by the user
 */
public class PendingReceipt {
    private File imageFile;
    private ReceiptOcrResponse.ReceiptData data;
    private boolean isProcessing;
    private boolean isProcessed;
    private String errorMessage;
    
    // Editable fields
    private Double editedAmount;
    private String editedCategory;
    private int selectedCategoryId = -1;
    private String editedDate;
    private String editedMerchant;
    private String editedNotes;
    
    public PendingReceipt(File imageFile) {
        this.imageFile = imageFile;
        this.isProcessing = false;
        this.isProcessed = false;
    }
    
    public File getImageFile() {
        return imageFile;
    }
    
    public void setImageFile(File imageFile) {
        this.imageFile = imageFile;
    }
    
    public ReceiptOcrResponse.ReceiptData getData() {
        return data;
    }
    
    public void setData(ReceiptOcrResponse.ReceiptData data) {
        this.data = data;
        if (data != null) {
            // Initialize editable fields from OCR data
            this.editedAmount = data.getTotalAmount();
            this.editedCategory = data.getExpenseCategory();
            this.editedDate = data.getReceiptDate();
            this.editedMerchant = data.getMerchantName();
        }
    }
    
    public boolean isProcessing() {
        return isProcessing;
    }
    
    public void setProcessing(boolean processing) {
        isProcessing = processing;
    }
    
    public boolean isProcessed() {
        return isProcessed;
    }
    
    public void setProcessed(boolean processed) {
        isProcessed = processed;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public Double getEditedAmount() {
        return editedAmount;
    }
    
    public void setEditedAmount(Double editedAmount) {
        this.editedAmount = editedAmount;
    }
    
    public String getEditedCategory() {
        return editedCategory;
    }
    
    public void setEditedCategory(String editedCategory) {
        this.editedCategory = editedCategory;
    }
    
    public int getSelectedCategoryId() {
        return selectedCategoryId;
    }
    
    public void setSelectedCategoryId(int selectedCategoryId) {
        this.selectedCategoryId = selectedCategoryId;
    }

    public String getEditedDate() {
        return editedDate;
    }
    
    public void setEditedDate(String editedDate) {
        this.editedDate = editedDate;
    }
    
    public String getEditedMerchant() {
        return editedMerchant;
    }
    
    public void setEditedMerchant(String editedMerchant) {
        this.editedMerchant = editedMerchant;
    }
    
    public String getEditedNotes() {
        return editedNotes;
    }
    
    public void setEditedNotes(String editedNotes) {
        this.editedNotes = editedNotes;
    }
    
    public boolean hasError() {
        return errorMessage != null && !errorMessage.isEmpty();
    }
}
