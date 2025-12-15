package com.example.mymoney.model;

public class SavingGoal {

    private String name;
    private int targetAmount;
    private int currentSaved;
    private String type; // "manual" hoặc "auto"

    private long lastUpdatedTime;

    public SavingGoal(String name, int targetAmount, int currentSaved, String type, long lastUpdatedTime) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentSaved = currentSaved;
        this.type = type;
        this.lastUpdatedTime = lastUpdatedTime;
    }

    // getter
    public long getLastUpdatedTime() {
        return lastUpdatedTime;
    }
    // ===== Constructor =====

    // ===== Getter/Setter =====
    public String getName() {
        return name;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    public int getCurrentSaved() {
        return currentSaved;
    }

    public void setCurrentSaved(int currentSaved) {
        this.currentSaved = currentSaved;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
