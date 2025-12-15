package com.example.mymoney.utils;

import android.content.Context;
import android.util.Log;

import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.dao.BudgetDao;
import com.example.mymoney.database.dao.CategoryDao;
import com.example.mymoney.database.dao.TransactionDao;
import com.example.mymoney.database.dao.WalletDao;
import com.example.mymoney.database.entity.Budget;
import com.example.mymoney.database.entity.Category;
import com.example.mymoney.database.entity.Transaction;
import com.example.mymoney.database.entity.Wallet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;

/**
 * Generates test data for budgets, expenses, and income to test the spending pattern analyzer
 * and budget recommendation features.
 * 
 * ===== DEMO FEATURES FOR RULE-BASED SYSTEMS =====
 * 
 * 1. SpendingPatternAnalyzer Features:
 *    - Regular Habits: Food, Transport, Clothing appear in 60%+ of months
 *    - Missing Purchases: Some categories (Beauty) intentionally skip current month
 *    - Unusual Spending (Spike): Entertainment this month is 2x higher than average
 *    - Unusual Spending (Drop): Some categories show reduced spending
 *    - Saving Opportunities: Increasing spending trends in non-essential categories
 *    - Monthly Comparison: Varied spending to show trends
 * 
 * 2. BudgetRuleEngine Features:
 *    - Budget Exceeded: Food budget will be exceeded (set low limit)
 *    - Critical Velocity: Entertainment spending velocity > 1.5x
 *    - Warning/Caution levels: Different budgets at different thresholds
 *    - On Track: Transport budget stays within healthy range
 *    - Period Ending: Weekly budgets near end of period
 *    - Multiple Budgets at Risk: Create 2+ budgets with issues
 */
public class TestDataGenerator {
    
    private static final String TAG = "TestDataGenerator";
    
    private final Context context;
    private final AppDatabase database;
    private final TransactionDao transactionDao;
    private final BudgetDao budgetDao;
    private final CategoryDao categoryDao;
    private final WalletDao walletDao;
    private final Random random = new Random();
    
    // Category IDs - will be loaded from existing categories
    private int catFood = -1;
    private int catTransport = -1;
    private int catEntertainment = -1;
    private int catMedical = -1;
    private int catClothing = -1;
    private int catEducation = -1;
    private int catGroceries = -1;
    private int catBeauty = -1;
    private int catSalary = -1;
    private int catBusiness = -1;
    private int catGifts = -1;
    
    public interface GeneratorCallback {
        void onComplete(String message);
        void onError(String error);
    }
    
    public TestDataGenerator(Context context) {
        this.context = context;
        this.database = AppDatabase.getInstance(context);
        this.transactionDao = database.transactionDao();
        this.budgetDao = database.budgetDao();
        this.categoryDao = database.categoryDao();
        this.walletDao = database.walletDao();
    }
    
    /**
     * Generate 6 months of realistic test data
     */
    public void generateTestData(int userId, int walletId, GeneratorCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Log.d(TAG, "Starting test data generation for user: " + userId + ", wallet: " + walletId);
                
                // Validate wallet exists
                if (walletId <= 0) {
                    callback.onError("Vui lòng chọn ví trước khi tạo dữ liệu test.");
                    return;
                }
                
                Wallet wallet = walletDao.getWalletById(walletId);
                if (wallet == null) {
                    callback.onError("Không tìm thấy ví. Vui lòng chọn ví khác.");
                    return;
                }
                
                // Step 1: Load existing categories
                if (!loadExistingCategories()) {
                    callback.onError("Không tìm thấy danh mục. Vui lòng tạo danh mục trước.");
                    return;
                }
                
                // Step 2: Generate 6 months of transactions
                int transactionCount = generateTransactions(userId, walletId);
                
                // Step 3: Generate budgets
                int budgetCount = generateBudgets(userId, walletId);
                
                // Step 4: Recalculate and update wallet balance
                double newBalance = recalculateWalletBalance(walletId);
                Log.d(TAG, "Wallet balance updated to: " + newBalance);
                
                // Get wallet currency
                String currency = wallet.getCurrency() != null ? wallet.getCurrency() : "VND";
                
                String message = String.format(Locale.getDefault(),
                    "✅ Đã tạo dữ liệu test thành công!\n\n" +
                    "📊 Giao dịch: %d\n" +
                    "💰 Ngân sách: %d\n" +
                    "💵 Số dư mới: %,.0f %s\n\n" +
                    "📋 Dữ liệu từ đầu năm %d đến nay:\n\n" +
                    "🎯 Demo SpendingPatternAnalyzer:\n" +
                    "• Regular Habits: Food, Transport (100%%)\n" +
                    "• Missing Purchase: Beauty (không có tháng này)\n" +
                    "• Unusual Spike: Entertainment (2x)\n" +
                    "• Unusual Drop: Education (giảm mạnh)\n" +
                    "• Increasing Trend: Clothing (tăng mỗi tháng)\n\n" +
                    "🎯 Demo BudgetRuleEngine:\n" +
                    "• Exceeded: Food Budget (vượt ngân sách)\n" +
                    "• Critical Velocity: Entertainment (chi quá nhanh)\n" +
                    "• Warning: Clothing (75-90%%)\n" +
                    "• On Track: Transport (tốt)\n" +
                    "• Period Ending: Weekly Budget\n\n" +
                    "⚠️ Hãy vào Chatbot hoặc Statistics để xem kết quả phân tích!",
                    transactionCount, budgetCount, newBalance, currency, Calendar.getInstance().get(Calendar.YEAR)
                );
                
                callback.onComplete(message);
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating test data", e);
                callback.onError("Lỗi tạo dữ liệu: " + e.getMessage());
            }
        });
    }
    
    /**
     * Load existing categories from database
     */
    private boolean loadExistingCategories() {
        List<Category> allCategories = categoryDao.getAllCategories();
        
        if (allCategories.isEmpty()) {
            Log.e(TAG, "No categories found in database");
            return false;
        }
        
        for (Category cat : allCategories) {
            String name = cat.getName().toLowerCase();
            int id = cat.getId();
            
            // Expense categories
            if (name.equals("food")) catFood = id;
            else if (name.equals("transport")) catTransport = id;
            else if (name.equals("entertainment")) catEntertainment = id;
            else if (name.equals("medical")) catMedical = id;
            else if (name.equals("clothing")) catClothing = id;
            else if (name.equals("education")) catEducation = id;
            else if (name.equals("groceries")) catGroceries = id;
            else if (name.equals("beauty")) catBeauty = id;
            // Income categories
            else if (name.equals("salary")) catSalary = id;
            else if (name.equals("business")) catBusiness = id;
            else if (name.equals("gifts")) catGifts = id;
        }
        
        Log.d(TAG, String.format("Loaded categories - Food: %d, Transport: %d, Clothing: %d, Salary: %d",
            catFood, catTransport, catClothing, catSalary));
        
        // Check if we have minimum required categories
        return catFood > 0 && catSalary > 0;
    }
    
    /**
     * Generate transactions from beginning of this year until now
     * Designed to demonstrate all SpendingPatternAnalyzer features:
     * 1. Regular Habits (60%+ occurrence): Food, Transport, Clothing
     * 2. Missing Purchases: Beauty skipped current month (but present in history)
     * 3. Unusual Spending Spikes: Entertainment 2x higher this month
     * 4. Unusual Spending Drops: Education significantly lower
     * 5. Saving Opportunities: Clothing shows increasing trend over months
     * 6. Monthly Comparison: Varied totals to show trends
     */
    private int generateTransactions(int userId, int walletId) {
        List<Transaction> transactions = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        
        // Get current date info
        Calendar now = Calendar.getInstance();
        int currentYear = now.get(Calendar.YEAR);
        int currentMonth = now.get(Calendar.MONTH);
        int currentDayOfMonth = now.get(Calendar.DAY_OF_MONTH);
        
        // Track spending for pattern demo purposes
        int monthsGenerated = 0;
        
        // Generate from January (month 0) of this year until current month
        for (int month = 0; month <= currentMonth; month++) {
            cal.set(currentYear, month, 1, 0, 0, 0);
            
            // For current month, only generate up to current day
            int daysInMonth;
            if (month == currentMonth) {
                daysInMonth = currentDayOfMonth;
            } else {
                daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            }
            
            int genMonth = month;
            int genYear = currentYear;
            boolean isCurrentMonth = (month == currentMonth);
            
            Log.d(TAG, "Generating transactions for month: " + (genMonth + 1) + "/" + genYear);
            monthsGenerated++;
            
            // === INCOME TRANSACTIONS ===
            
            // Salary (monthly, on 5th) - using existing Salary category
            if (catSalary > 0 && daysInMonth >= 5) {
                cal.set(genYear, genMonth, 5, 9, 0, 0);
                transactions.add(createTransaction(
                    userId, walletId, catSalary, "income",
                    randomAmount(15000000, 20000000), // 15-20 million VND
                    "Salary month " + (genMonth + 1),
                    cal.getTimeInMillis()
                ));
            }
            
            // Occasional business income (every 2-3 months) - using existing Business category
            if (catBusiness > 0 && month % 3 == 0 && daysInMonth >= 15) {
                cal.set(genYear, genMonth, 15, 10, 0, 0);
                transactions.add(createTransaction(
                    userId, walletId, catBusiness, "income",
                    randomAmount(2000000, 5000000),
                    "Business income",
                    cal.getTimeInMillis()
                ));
            }
            
            // Occasional gifts (every other month) - using existing Gifts category
            if (catGifts > 0 && month % 2 == 0 && daysInMonth >= 20) {
                cal.set(genYear, genMonth, 20, 14, 0, 0);
                transactions.add(createTransaction(
                    userId, walletId, catGifts, "income",
                    randomAmount(500000, 2000000),
                    "Gift received",
                    cal.getTimeInMillis()
                ));
            }
            
            // === EXPENSE TRANSACTIONS ===
            // Designed to trigger SpendingPatternAnalyzer rules
            
            // ========================================
            // RULE 1: REGULAR HABITS (60%+ occurrence)
            // Food, Transport appear EVERY month = 100% frequency
            // ========================================
            
            // Daily food expenses (20-25 times per month) - REGULAR HABIT
            if (catFood > 0) {
                int foodDays = Math.min(random.nextInt(6) + 20, daysInMonth);
                for (int i = 0; i < foodDays; i++) {
                    int day = random.nextInt(daysInMonth) + 1;
                    cal.set(genYear, genMonth, day, 12, 30, 0);
                    transactions.add(createTransaction(
                        userId, walletId, catFood, "expense",
                        randomAmount(50000, 200000), // 50k-200k per meal/day
                        getRandomFoodDescription(),
                        cal.getTimeInMillis()
                    ));
                }
            }
            
            // Transport (15-20 times per month) - REGULAR HABIT
            if (catTransport > 0) {
                int transportDays = Math.min(random.nextInt(6) + 15, daysInMonth);
                for (int i = 0; i < transportDays; i++) {
                    int day = random.nextInt(daysInMonth) + 1;
                    cal.set(genYear, genMonth, day, 8, 0, 0);
                    transactions.add(createTransaction(
                        userId, walletId, catTransport, "expense",
                        randomAmount(20000, 100000),
                        getRandomTransportDescription(),
                        cal.getTimeInMillis()
                    ));
                }
            }
            
            // Groceries (monthly) - REGULAR HABIT
            if (catGroceries > 0 && daysInMonth >= 10) {
                cal.set(genYear, genMonth, 10, 9, 0, 0);
                transactions.add(createTransaction(
                    userId, walletId, catGroceries, "expense",
                    randomAmount(1000000, 2000000),
                    "Monthly groceries",
                    cal.getTimeInMillis()
                ));
            }
            
            // ========================================
            // RULE 2: CLOTHING - INCREASING TREND (Saving Opportunity)
            // Spending increases each month to trigger "increasing trend" detection
            // ========================================
            if (catClothing > 0 && daysInMonth >= 10) {
                int clothingDay = Math.min(random.nextInt(15) + 10, daysInMonth);
                cal.set(genYear, genMonth, clothingDay, 15, 0, 0);
                
                // Intentionally increase clothing spending each month
                // Start at 300k and increase by 100k each month
                double baseClothing = 300000 + (monthsGenerated * 100000);
                double clothingAmount = baseClothing + randomAmount(-50000, 50000);
                
                transactions.add(createTransaction(
                    userId, walletId, catClothing, "expense",
                    clothingAmount,
                    getRandomClothingDescription(),
                    cal.getTimeInMillis()
                ));
            }
            
            // ========================================
            // RULE 3: ENTERTAINMENT - UNUSUAL SPIKE THIS MONTH
            // Current month has 2x spending compared to average
            // ========================================
            if (catEntertainment > 0) {
                int entertainmentTimes;
                double entertainmentMultiplier;
                
                if (isCurrentMonth) {
                    // SPIKE: Current month - many more entertainment expenses (2x)
                    entertainmentTimes = Math.min(random.nextInt(3) + 6, daysInMonth); // 6-8 times
                    entertainmentMultiplier = 2.0; // Double the amount
                } else {
                    // Normal months
                    entertainmentTimes = Math.min(random.nextInt(3) + 2, daysInMonth); // 2-4 times
                    entertainmentMultiplier = 1.0;
                }
                
                for (int i = 0; i < entertainmentTimes; i++) {
                    int day = random.nextInt(daysInMonth) + 1;
                    cal.set(genYear, genMonth, day, 19, 0, 0);
                    transactions.add(createTransaction(
                        userId, walletId, catEntertainment, "expense",
                        randomAmount(100000, 500000) * entertainmentMultiplier,
                        getRandomEntertainmentDescription(),
                        cal.getTimeInMillis()
                    ));
                }
            }
            
            // ========================================
            // RULE 4: BEAUTY - MISSING PURCHASE THIS MONTH
            // Appears in history but NOT in current month
            // ========================================
            if (catBeauty > 0 && !isCurrentMonth) {
                // Only generate for past months (60%+ of them)
                if (random.nextDouble() < 0.7) {
                    int day = random.nextInt(daysInMonth) + 1;
                    cal.set(genYear, genMonth, day, 14, 0, 0);
                    transactions.add(createTransaction(
                        userId, walletId, catBeauty, "expense",
                        randomAmount(300000, 800000),
                        "Beauty & skincare",
                        cal.getTimeInMillis()
                    ));
                }
            }
            // Note: No Beauty spending in current month to trigger "Missing Purchase"
            
            // ========================================
            // RULE 5: EDUCATION - UNUSUAL DROP (if exists in history)
            // Regular in past but significantly reduced this month
            // ========================================
            if (catEducation > 0) {
                if (isCurrentMonth) {
                    // DROP: Very small spending this month (30% of normal)
                    if (daysInMonth >= 5) {
                        int day = random.nextInt(daysInMonth) + 1;
                        cal.set(genYear, genMonth, day, 9, 0, 0);
                        transactions.add(createTransaction(
                            userId, walletId, catEducation, "expense",
                            randomAmount(100000, 200000), // Much lower than usual 500k-2M
                            "Small book purchase",
                            cal.getTimeInMillis()
                        ));
                    }
                } else {
                    // Regular spending in past months (70%+ occurrence)
                    if (random.nextDouble() < 0.75) {
                        int day = random.nextInt(daysInMonth) + 1;
                        cal.set(genYear, genMonth, day, 9, 0, 0);
                        transactions.add(createTransaction(
                            userId, walletId, catEducation, "expense",
                            randomAmount(500000, 2000000),
                            "Course / Books",
                            cal.getTimeInMillis()
                        ));
                    }
                }
            }
            
            // Medical (occasional, every 2-3 months) - using existing Medical category
            if (catMedical > 0 && random.nextDouble() < 0.4) {
                int day = random.nextInt(daysInMonth) + 1;
                cal.set(genYear, genMonth, day, 10, 0, 0);
                transactions.add(createTransaction(
                    userId, walletId, catMedical, "expense",
                    randomAmount(200000, 1500000),
                    "Medical checkup",
                    cal.getTimeInMillis()
                ));
            }
        }
        
        // Insert all transactions
        for (Transaction t : transactions) {
            transactionDao.insert(t);
        }
        
        Log.d(TAG, "Generated " + transactions.size() + " transactions");
        return transactions.size();
    }
    
    /**
     * Generate budgets for the current month
     * Designed to demonstrate all BudgetRuleEngine features:
     * 1. Budget Exceeded: Food budget intentionally low
     * 2. Critical Velocity (>1.5x): Entertainment budget 
     * 3. Warning Level (75-90%): Clothing budget at warning threshold
     * 4. On Track: Transport budget stays healthy
     * 5. Period Ending: Weekly budget near end of week
     * 6. Multiple Budgets at Risk: 2+ budgets with issues
     */
    private int generateBudgets(int userId, int walletId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        
        // Get start and end of current month
        cal.set(Calendar.DAY_OF_MONTH, 1);
        String startDate = sdf.format(cal.getTime());
        
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        String endDate = sdf.format(cal.getTime());
        
        // Calculate day progress in month for budget calculations
        Calendar now = Calendar.getInstance();
        int currentDay = now.get(Calendar.DAY_OF_MONTH);
        int totalDays = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        double monthProgress = (double) currentDay / totalDays;
        
        List<Budget> budgets = new ArrayList<>();
        
        // ========================================
        // RULE 1: BUDGET EXCEEDED - Food budget intentionally too low
        // Set budget lower than typical spending to demonstrate exceeded state
        // ========================================
        if (catFood > 0) {
            // Food typically spends 2.5-5M, set budget at 2M to ensure exceeded
            budgets.add(createBudget(
                userId, walletId, catFood,
                "Food Budget",
                2000000, // Intentionally low - will be exceeded
                "monthly", startDate, endDate, 80
            ));
        }
        
        // ========================================
        // RULE 2: ON TRACK - Transport budget healthy range
        // Set budget appropriately to show good financial health
        // ========================================
        if (catTransport > 0) {
            // Transport typically spends 1-2M, budget at 3M = safe
            budgets.add(createBudget(
                userId, walletId, catTransport,
                "Transport Budget",
                3000000, // Generous budget - will be on track
                "monthly", startDate, endDate, 75
            ));
        }
        
        // ========================================
        // RULE 3: WARNING LEVEL (75-90%) - Clothing budget near limit
        // Set budget to hit warning threshold
        // ========================================
        if (catClothing > 0) {
            // Clothing spending increases each month, set budget to be at warning
            double estimatedClothingSpend = 300000 + (currentDay / 30.0 * 700000); // ~1M
            budgets.add(createBudget(
                userId, walletId, catClothing,
                "Clothing Budget",
                1200000, // Will hit 75-90% range
                "monthly", startDate, endDate, 70
            ));
        }
        
        // ========================================
        // RULE 4: CRITICAL VELOCITY - Entertainment with fast spending
        // Entertainment this month is 2x normal, so velocity will be high
        // ========================================
        if (catEntertainment > 0) {
            // Entertainment is spiking, set budget to show critical velocity
            budgets.add(createBudget(
                userId, walletId, catEntertainment,
                "Entertainment Budget",
                1500000, // Normal budget but spending is 2x = critical velocity
                "monthly", startDate, endDate, 80
            ));
        }
        
        // ========================================
        // RULE 5: OVERALL MONTHLY BUDGET
        // Total budget to show combined health
        // ========================================
        budgets.add(createBudget(
            userId, walletId, null,
            "Total Monthly Budget",
            12000000, // Total monthly limit - will be near limit
            "monthly", startDate, endDate, 85
        ));
        
        // ========================================
        // RULE 6: WEEKLY BUDGET - Period Ending demonstration
        // Weekly budget to show end-of-period rules
        // ========================================
        cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        String weekStart = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_WEEK, 6);
        String weekEnd = sdf.format(cal.getTime());
        
        if (catFood > 0) {
            budgets.add(createBudget(
                userId, walletId, catFood,
                "Weekly Food Budget",
                500000, // Low weekly budget to demonstrate period ending
                "weekly", weekStart, weekEnd, 80
            ));
        }
        
        // ========================================
        // RULE 7: CAUTION LEVEL (50-75%) - Groceries budget
        // ========================================
        if (catGroceries > 0) {
            budgets.add(createBudget(
                userId, walletId, catGroceries,
                "Groceries Budget",
                2500000, // Will be at caution level (50-75%)
                "monthly", startDate, endDate, 70
            ));
        }
        
        // ========================================
        // RULE 8: DAILY BUDGET - Shows daily limit tracking
        // ========================================
        Calendar today = Calendar.getInstance();
        String todayStr = sdf.format(today.getTime());
        
        budgets.add(createBudget(
            userId, walletId, null,
            "Daily Spending Limit",
            500000, // Daily limit
            "daily", todayStr, todayStr, 90
        ));
        
        // Insert all budgets
        for (Budget b : budgets) {
            budgetDao.insert(b);
        }
        
        Log.d(TAG, "Generated " + budgets.size() + " budgets for rule engine demo");
        return budgets.size();
    }
    
    // === HELPER METHODS ===
    
    private Transaction createTransaction(int userId, int walletId, int categoryId,
                                         String type, double amount, String description,
                                         long timestamp) {
        Transaction t = new Transaction();
        t.setUserId(userId);
        t.setWalletId(walletId);
        t.setCategoryId(categoryId);
        t.setType(type);
        t.setAmount(amount);
        t.setDescription(description);
        t.setCreatedAt(timestamp);
        t.setUpdatedAt(timestamp);
        t.setRecurring(false);
        return t;
    }
    
    private Budget createBudget(int userId, int walletId, Integer categoryId,
                               String name, double amount, String type,
                               String startDate, String endDate, double alertThreshold) {
        Budget b = new Budget();
        b.setUserId(userId);
        b.setWalletId(walletId);
        b.setCategoryId(categoryId);
        b.setName(name);
        b.setBudgetAmount(amount);
        b.setBudgetType(type);
        b.setPeriodUnit(type);
        b.setStartDate(startDate);
        b.setEndDate(endDate);
        b.setAlertThreshold(alertThreshold);
        return b;
    }
    
    private double randomAmount(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }
    
    private String getRandomFoodDescription() {
        String[] foods = {
            "Lunch", "Breakfast pho", "Bun bo", "Coffee", "Milk tea",
            "Banh mi", "Office lunch", "Snacks", "Grab Food",
            "Dinner", "Breakfast", "Drinks", "Afternoon snack"
        };
        return foods[random.nextInt(foods.length)];
    }
    
    private String getRandomTransportDescription() {
        String[] transports = {
            "Grab to work", "Bus", "Gas", "Grab home",
            "Parking", "Taxi", "Motorbike taxi", "Grab bike"
        };
        return transports[random.nextInt(transports.length)];
    }
    
    private String getRandomClothingDescription() {
        String[] clothing = {
            "New T-shirt", "Jeans", "Sneakers", "Jacket",
            "Dress", "Shirt", "Fashion accessories", "Sportswear"
        };
        return clothing[random.nextInt(clothing.length)];
    }
    
    private String getRandomEntertainmentDescription() {
        String[] entertainment = {
            "Movie CGV", "Karaoke", "Cafe with friends", "Game",
            "Netflix/Spotify", "Bar", "Bowling", "Escape room"
        };
        return entertainment[random.nextInt(entertainment.length)];
    }
    
    /**
     * Recalculate wallet balance based on all transactions
     */
    private double recalculateWalletBalance(int walletId) {
        Wallet wallet = walletDao.getWalletById(walletId);
        if (wallet == null) {
            Log.e(TAG, "Wallet not found: " + walletId);
            return 0;
        }
        
        // Get all transactions for this wallet
        List<Transaction> transactions = transactionDao.getTransactionsByWalletId(walletId);
        
        double totalIncome = 0;
        double totalExpense = 0;
        
        for (Transaction t : transactions) {
            if ("income".equals(t.getType())) {
                totalIncome += t.getAmount();
            } else if ("expense".equals(t.getType())) {
                totalExpense += t.getAmount();
            }
        }
        
        double newBalance = totalIncome - totalExpense;
        
        // Update wallet balance using entity update for better reliability
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(System.currentTimeMillis());
        walletDao.update(wallet);
        
        Log.d(TAG, String.format("Wallet %d balance recalculated: Income=%.0f, Expense=%.0f, Balance=%.0f",
            walletId, totalIncome, totalExpense, newBalance));
        
        return newBalance;
    }
    
    /**
     * Clear all test data for a wallet
     */
    public void clearTestData(int walletId, GeneratorCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Delete all transactions for this wallet
                List<Transaction> transactions = transactionDao.getTransactionsByWalletId(walletId);
                for (Transaction t : transactions) {
                    transactionDao.delete(t);
                }
                
                // Delete all budgets for this wallet
                List<Budget> budgets = budgetDao.getBudgetsByWalletId(walletId);
                for (Budget b : budgets) {
                    budgetDao.delete(b);
                }
                
                // Reset wallet balance to 0 using entity update
                Wallet wallet = walletDao.getWalletById(walletId);
                if (wallet != null) {
                    wallet.setBalance(0);
                    wallet.setUpdatedAt(System.currentTimeMillis());
                    walletDao.update(wallet);
                }
                
                callback.onComplete("✅ Đã xóa tất cả dữ liệu test cho ví này.\n\n⚠️ Hãy quay lại Home để xem số dư cập nhật.");
                
            } catch (Exception e) {
                callback.onError("Lỗi xóa dữ liệu: " + e.getMessage());
            }
        });
    }
}
