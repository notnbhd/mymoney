package com.example.mymoney.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mymoney.CategoryTotal;
import com.example.mymoney.MonthTotal;
import com.example.mymoney.CategoryTotal;
import com.example.mymoney.MonthTotal;
import com.example.mymoney.database.entity.Transaction;
import com.example.mymoney.model.CategoryExpense;

import java.util.List;

@Dao
public interface TransactionDao {

    @Insert
    long insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

    @Query("SELECT * FROM `transaction` WHERE id = :transactionId")
    Transaction getTransactionById(int transactionId);

    @Query("SELECT * FROM `transaction` WHERE wallet_id = :walletId ORDER BY created_at DESC")
    List<Transaction> getTransactionsByWalletId(int walletId);

    @Query("SELECT * FROM `transaction` WHERE user_id = :userId ORDER BY created_at DESC")
    List<Transaction> getTransactionsByUserId(int userId);

    @Query("SELECT * FROM `transaction` WHERE category_id = :categoryId ORDER BY created_at DESC")
    List<Transaction> getTransactionsByCategoryId(int categoryId);

    @Query("SELECT * FROM `transaction` WHERE wallet_id = :walletId AND type = :type ORDER BY created_at DESC")
    List<Transaction> getTransactionsByWalletAndType(int walletId, String type);

    @Query("SELECT * FROM `transaction` WHERE user_id = :userId AND type = :type ORDER BY created_at DESC")
    List<Transaction> getTransactionsByUserAndType(int userId, String type);

    @Query("SELECT * FROM `transaction` WHERE created_at BETWEEN :startDate AND :endDate ORDER BY created_at DESC")
    List<Transaction> getTransactionsByDateRange(long startDate, long endDate);

    @Query("SELECT * FROM `transaction` WHERE user_id = :userId AND created_at BETWEEN :startDate AND :endDate ORDER BY created_at DESC")
    List<Transaction> getTransactionsByDateRange(int userId, long startDate, long endDate);

    @Query("SELECT * FROM `transaction` WHERE wallet_id = :walletId AND created_at BETWEEN :startDate AND :endDate ORDER BY created_at DESC")
    List<Transaction> getTransactionsByWalletAndDateRange(int walletId, long startDate, long endDate);

    @Query("SELECT SUM(amount) FROM `transaction` WHERE user_id = :userId AND type = 'expense' AND created_at BETWEEN :startDate AND :endDate")
    Double getTotalExpensesByDateRange(int userId, long startDate, long endDate);

    @Query("SELECT SUM(amount) FROM `transaction` WHERE user_id = :userId AND type = 'income' AND created_at BETWEEN :startDate AND :endDate")
    Double getTotalIncomeByDateRange(int userId, long startDate, long endDate);

    @Query("SELECT SUM(amount) FROM `transaction` WHERE wallet_id = :walletId AND type = 'expense'")
    double getTotalExpensesByWallet(int walletId);

    @Query("SELECT SUM(amount) FROM `transaction` WHERE wallet_id = :walletId AND type = 'income'")
    double getTotalIncomeByWallet(int walletId);

    @Query("SELECT SUM(amount) FROM `transaction` WHERE user_id = :userId AND type = 'expense'")
    double getTotalExpensesByUser(int userId);

    @Query("SELECT SUM(amount) FROM `transaction` WHERE user_id = :userId AND type = 'income'")
    double getTotalIncomeByUser(int userId);

    @Query("SELECT * FROM `transaction` WHERE is_recurring = 1")
    List<Transaction> getRecurringTransactions();

    @Query("SELECT * FROM `transaction` ORDER BY created_at DESC")
    List<Transaction> getAllTransactions();

    @Query("DELETE FROM `transaction` WHERE id = :transactionId")
    void deleteById(int transactionId);

    @Query("SELECT * FROM `transaction` ORDER BY created_at DESC LIMIT :limit")
    List<Transaction> getRecentTransactions(int limit);


    // 🟢 Thêm phương thức thống kê top chi tiêu (wallet + user specific)
    @Query("SELECT c.name AS category, SUM(t.amount) AS total " +
            "FROM `transaction` t " +
            "JOIN category c ON t.category_id = c.id " +
            "WHERE t.type = 'expense' AND t.user_id = :userId AND t.wallet_id = :walletId AND t.created_at BETWEEN :startDate AND :endDate " +
            "GROUP BY c.name " +
            "ORDER BY total DESC " +
            "LIMIT 5")
    List<CategoryTotal> getTopExpensesByYear(int userId, int walletId, long startDate, long endDate);

    @Query("SELECT IFNULL(SUM(t.amount), 0) FROM `transaction` t " +
            "JOIN category c ON t.category_id = c.id " +
            "WHERE t.type = 'expense' AND c.name = :categoryName AND t.created_at >= :fromDate")
    double getTotalExpenseByCategorySince(String categoryName, long fromDate);

    @Query(
            "SELECT IFNULL(SUM(t.amount), 0) " +
                    "FROM `transaction` t " +
                    "JOIN category c ON t.category_id = c.id " +
                    "WHERE t.type = 'expense' " +
                    "AND c.name = :categoryName " +
                    "AND t.created_at >= :fromDate " +
                    "AND t.user_id = :userId"
    )
    double getTotalExpenseByCategorySinceForUser(
            String categoryName,
            long fromDate,
            int userId
    );

    @Query(
            "SELECT c.name AS category, IFNULL(SUM(t.amount), 0) AS total " +
                    "FROM category c " +
                    "LEFT JOIN `transaction` t " +
                    "  ON t.category_id = c.id " +
                    "  AND t.type = 'expense' " +
                    "  AND t.user_id = :userId " +
                    "  AND t.created_at >= :fromDate " +
                    "  AND t.created_at < :toDate " +
                    "WHERE c.name != 'Saving' " +
                    "GROUP BY c.name"
    )
    List<CategoryExpense> getExpensesByCategoryBetween(
            long fromDate,
            long toDate,
            int userId
    );

    @Query(
            "SELECT c.name AS category, IFNULL(SUM(t.amount), 0) AS total " +
                    "FROM category c " +
                    "LEFT JOIN `transaction` t " +
                    "  ON t.category_id = c.id " +
                    "  AND t.type = 'expense' " +
                    "  AND t.user_id = :userId " +
                    "  AND t.created_at >= :from " +
                    "WHERE c.name != 'Saving' " +
                    "GROUP BY c.name"
    )
    List<CategoryExpense> getExpensesByCategorySince(long from, int userId);
    @Query("SELECT strftime('%m', datetime(created_at / 1000, 'unixepoch')) AS month, " +
            "SUM(amount) AS total " +
            "FROM `transaction` " +
            "WHERE type = 'expense' AND user_id = :userId AND wallet_id = :walletId AND created_at BETWEEN :startDate AND :endDate " +
            "GROUP BY month " +
            "ORDER BY month")
    List<MonthTotal> getMonthlyExpensesByYear(int userId, int walletId, long startDate, long endDate);

    @Query("SELECT c.name AS category, SUM(t.amount) AS total " +
            "FROM `transaction` t " +
            "JOIN category c ON t.category_id = c.id " +
            "WHERE t.type = 'expense' AND t.user_id = :userId AND t.wallet_id = :walletId AND t.created_at BETWEEN :startDate AND :endDate " +
            "GROUP BY c.name " +
            "ORDER BY total DESC")
    List<CategoryTotal> getExpensesByDateRange(int userId, int walletId, long startDate, long endDate);
    // 🟢 ===== HÀM CHO MỤC SAVING GOAL (dùng trong SavingGoalFragment) =====
    @Query("SELECT c.name AS category, SUM(t.amount) AS total " +
            "FROM `transaction` t " +
            "JOIN category c ON t.category_id = c.id " +
            "WHERE t.type = 'expense' AND t.created_at >= :startDate " +
            "GROUP BY c.name " +
            "HAVING total > 0 " +
            "ORDER BY total DESC")
    List<CategoryExpense> getExpensesByCategorySince(long startDate);

    @Query("SELECT IFNULL(SUM(amount), 0) FROM `transaction` " +
            "WHERE type = 'income' AND created_at >= :startDate")
    double getTotalIncomeSince(long startDate);

    // Tổng chi tiêu kể từ một thời điểm
    @Query("SELECT IFNULL(SUM(amount), 0) FROM `transaction` " +
            "WHERE type = 'expense' AND created_at >= :startDate")
    double getTotalExpenseSince(long startDate);

    // Tổng chi tiêu trong khoảng thời gian cụ thể (for Budget tracking)
    @Query("SELECT IFNULL(SUM(amount), 0) FROM `transaction` " +
            "WHERE type = 'expense' AND created_at BETWEEN :startDate AND :endDate")
    double getTotalExpenseBetween(long startDate, long endDate);

    // Tổng chi tiêu trong khoảng thời gian cụ thể cho wallet cụ thể (for Budget tracking by wallet)
    @Query("SELECT IFNULL(SUM(amount), 0) FROM `transaction` " +
            "WHERE type = 'expense' AND created_at BETWEEN :startDate AND :endDate AND wallet_id = :walletId")
    double getTotalExpenseBetweenForWallet(long startDate, long endDate, int walletId);

    // Tổng chi tiêu trong khoảng thời gian cụ thể cho wallet và category cụ thể (for category-specific Budget)
    @Query("SELECT IFNULL(SUM(amount), 0) FROM `transaction` " +
            "WHERE type = 'expense' AND created_at BETWEEN :startDate AND :endDate AND wallet_id = :walletId AND category_id = :categoryId")
    double getTotalExpenseBetweenForWalletAndCategory(long startDate, long endDate, int walletId, int categoryId);


}