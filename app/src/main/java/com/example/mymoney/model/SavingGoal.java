package com.example.mymoney.model;

/**
 * Model class for displaying SavingGoal in UI.
 * Links to user and wallet for proper data isolation.
 */
public class SavingGoal {

    private int id;
    private String name;
    private long targetAmount;
    private long currentSaved;
    private String type; // "manual" hoặc "auto"
    private long lastUpdatedTime;
    private int userId;
    private int walletId;
    private String status; // "active", "completed", "cancelled"

    // Full constructor
    public SavingGoal(int id, String name, long targetAmount, long currentSaved,
                      String type, long lastUpdatedTime, int userId, int walletId, String status) {
        this.id = id;
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentSaved = currentSaved;
        this.type = type;
        this.lastUpdatedTime = lastUpdatedTime;
        this.userId = userId;
        this.walletId = walletId;
        this.status = status;
    }

    // Legacy constructor for backward compatibility
    public SavingGoal(String name, int targetAmount, int currentSaved, String type, long lastUpdatedTime) {
        this.id = 0;
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentSaved = currentSaved;
        this.type = type;
        this.lastUpdatedTime = lastUpdatedTime;
        this.userId = 0;
        this.walletId = 0;
        this.status = "active";
    }

    // ===== Getter/Setter =====

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

    public long getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(long targetAmount) {
        this.targetAmount = targetAmount;
    }

    public long getCurrentSaved() {
        return currentSaved;
    }

    public void setCurrentSaved(long currentSaved) {
        this.currentSaved = currentSaved;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getLastUpdatedTime() {
        return lastUpdatedTime;
    }

    public void setLastUpdatedTime(long lastUpdatedTime) {
        this.lastUpdatedTime = lastUpdatedTime;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getWalletId() {
        return walletId;
    }

    public void setWalletId(int walletId) {
        this.walletId = walletId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}