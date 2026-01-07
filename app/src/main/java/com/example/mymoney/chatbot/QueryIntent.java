package com.example.mymoney.chatbot;

/**
 * Represents the parsed intent from a user's natural language query.
 * Used to extract structured information for database queries.
 */
public class QueryIntent {
    
    public enum QueryType {
        SPENDING,       // "How much did I spend on..."
        INCOME,         // "How much did I earn..."
        COMPARISON,     // "Compare spending in X vs Y"
        TREND,          // "What's my average monthly spending..."
        CATEGORY_LIST,  // "What did I spend on Food?"
        GENERAL         // General advice questions
    }
    
    public enum TimeRangeType {
        CURRENT_MONTH,
        SPECIFIC_MONTH,
        YEAR,
        LAST_N_DAYS,
        DATE_RANGE,
        ALL_TIME
    }
    
    // Time range
    private TimeRangeType timeRangeType;
    private long timeRangeStart;
    private long timeRangeEnd;
    private int month;  // 1-12
    private int year;   // 2025, 2026, etc.
    private int days;   // For "last N days" queries
    
    // Category filter
    private String categoryName;
    private Integer categoryId;
    
    // Query type
    private QueryType queryType;
    
    // Original query for context
    private String originalQuery;
    
    // Whether we need to ask user for clarification
    private boolean needsClarification;
    private String clarificationMessage;

    public QueryIntent() {
        this.timeRangeType = TimeRangeType.CURRENT_MONTH;
        this.queryType = QueryType.GENERAL;
        this.needsClarification = false;
    }

    // Getters and Setters
    public TimeRangeType getTimeRangeType() {
        return timeRangeType;
    }

    public void setTimeRangeType(TimeRangeType timeRangeType) {
        this.timeRangeType = timeRangeType;
    }

    public long getTimeRangeStart() {
        return timeRangeStart;
    }

    public void setTimeRangeStart(long timeRangeStart) {
        this.timeRangeStart = timeRangeStart;
    }

    public long getTimeRangeEnd() {
        return timeRangeEnd;
    }

    public void setTimeRangeEnd(long timeRangeEnd) {
        this.timeRangeEnd = timeRangeEnd;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public void setQueryType(QueryType queryType) {
        this.queryType = queryType;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery;
    }

    public boolean needsClarification() {
        return needsClarification;
    }

    public void setNeedsClarification(boolean needsClarification) {
        this.needsClarification = needsClarification;
    }

    public String getClarificationMessage() {
        return clarificationMessage;
    }

    public void setClarificationMessage(String clarificationMessage) {
        this.clarificationMessage = clarificationMessage;
    }

    @Override
    public String toString() {
        return "QueryIntent{" +
                "timeRangeType=" + timeRangeType +
                ", month=" + month +
                ", year=" + year +
                ", categoryName='" + categoryName + '\'' +
                ", queryType=" + queryType +
                ", needsClarification=" + needsClarification +
                '}';
    }
}
