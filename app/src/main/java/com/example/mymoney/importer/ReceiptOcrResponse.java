package com.example.mymoney.importer;

import com.google.gson.annotations.SerializedName;

public class ReceiptOcrResponse {

    @SerializedName("receiptData")
    private ReceiptData receiptData;

    public ReceiptData getReceiptData() {
        return receiptData;
    }

    public static class ReceiptData {

        @SerializedName("totalAmount")
        private Double totalAmount;

        @SerializedName("expenseCategory")
        private String expenseCategory;

        @SerializedName("receiptDate")
        private String receiptDate;

        @SerializedName("merchantName")
        private String merchantName;

        @SerializedName("timestamp")
        private String timestamp;

        public Double getTotalAmount() {
            return totalAmount;
        }

        public String getExpenseCategory() {
            return expenseCategory;
        }

        public String getReceiptDate() {
            return receiptDate;
        }

        public String getMerchantName() {
            return merchantName;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }
}