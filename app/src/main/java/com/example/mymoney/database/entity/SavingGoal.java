package com.example.mymoney.database.entity;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "saving_goals",
        foreignKeys = {
                @ForeignKey(
                        entity = Category.class,
                        parentColumns = "id",
                        childColumns = "category_id",
                        onDelete = ForeignKey.SET_NULL
                ),
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
public class SavingGoal {
    
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int id;
    
    @ColumnInfo(name = "name")
    private String name;
    
    @ColumnInfo(name = "target")
    private double target;
    
    @ColumnInfo(name = "current_amount")
    private double currentAmount;
    
    @ColumnInfo(name = "start_date")
    private String startDate;
    
    @ColumnInfo(name = "end_date")
    private String endDate;
    
    @ColumnInfo(name = "description")
    private String description;
    
    @ColumnInfo(name = "status")
    private String status; // "active", "completed", "cancelled"
    
    @ColumnInfo(name = "created_at")
    private long createdAt;
    
    @ColumnInfo(name = "updated_at")
    private long updatedAt;
    
    @Nullable
    @ColumnInfo(name = "category_id")
    private Integer categoryId;  // Nullable - saving goals don't require a category
    
    @ColumnInfo(name = "wallet_id")
    private int walletId;
    
    @ColumnInfo(name = "user_id")
    private int userId;

    // Constructors
    public SavingGoal() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.currentAmount = 0.0;
        this.status = "active";
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

    public double getTarget() {
        return target;
    }

    public void setTarget(double target) {
        this.target = target;
    }

    public double getCurrentAmount() {
        return currentAmount;
    }

    public void setCurrentAmount(double currentAmount) {
        this.currentAmount = currentAmount;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    @Nullable
    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(@Nullable Integer categoryId) {
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
