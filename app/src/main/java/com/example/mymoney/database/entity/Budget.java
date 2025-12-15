package com.example.mymoney.database.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "budget",
        foreignKeys = {
                @ForeignKey(
                        entity = Wallet.class,
                        parentColumns = "id",
                        childColumns = "wallet_id",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = User.class,
                        parentColumns = "id",
                        childColumns = "user_id",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {@Index("category_id"), @Index("wallet_id"), @Index("user_id")})
public class Budget {
    
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;
    
    @ColumnInfo(name = "name")
    private String name;
    
    @ColumnInfo(name = "budget_amount")
    private double budgetAmount;
    
    @ColumnInfo(name = "budget_type")
    private String budgetType; // "monthly", "yearly", "custom"
    
    @ColumnInfo(name = "period_unit")
    private String periodUnit;
    
    @ColumnInfo(name = "start_date")
    private String startDate;
    
    @ColumnInfo(name = "end_date")
    private String endDate;
    
    @ColumnInfo(name = "alert_threshold")
    private double alertThreshold;
    
    @ColumnInfo(name = "created_at")
    private long createdAt;
    
    @ColumnInfo(name = "updated_at")
    private long updatedAt;
    
    @ColumnInfo(name = "category_id")
    private Integer categoryId; // Nullable - 0 or null means global budget
    
    @ColumnInfo(name = "wallet_id")
    private int walletId;
    
    @ColumnInfo(name = "user_id")
    private int userId;

    // Constructors
    public Budget() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(double budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public String getBudgetType() {
        return budgetType;
    }

    public void setBudgetType(String budgetType) {
        this.budgetType = budgetType;
    }

    public String getPeriodUnit() {
        return periodUnit;
    }

    public void setPeriodUnit(String periodUnit) {
        this.periodUnit = periodUnit;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public double getAlertThreshold() {
        return alertThreshold;
    }

    public void setAlertThreshold(double alertThreshold) {
        this.alertThreshold = alertThreshold;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public int getWalletId() {
        return walletId;
    }

    public void setWalletId(int walletId) {
        this.walletId = walletId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
}
