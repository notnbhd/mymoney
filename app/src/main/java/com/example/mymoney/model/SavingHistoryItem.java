package com.example.mymoney.model;

public class SavingHistoryItem {
    public String name;
    public long target;
    public long saved;
    public long start;
    public long end;
    public String type;

    public SavingHistoryItem(String name, long target, long saved, long start, long end, String type) {
        this.name = name;
        this.target = target;
        this.saved = saved;
        this.start = start;
        this.end = end;
        this.type = type;
    }
}
