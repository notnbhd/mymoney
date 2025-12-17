package com.example.mymoney.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mymoney.database.entity.SavingGoal;

import java.util.List;

@Dao
public interface SavingGoalDao {
    
    @Insert
    long insert(SavingGoal savingGoal);
    
    @Update
    void update(SavingGoal savingGoal);
    
    @Delete
    void delete(SavingGoal savingGoal);
    
    @Query("SELECT * FROM saving_goals WHERE id = :goalId")
    SavingGoal getSavingGoalById(int goalId);
    
    @Query("SELECT * FROM saving_goals WHERE user_id = :userId")
    List<SavingGoal> getSavingGoalsByUserId(int userId);
    
    @Query("SELECT * FROM saving_goals WHERE wallet_id = :walletId")
    List<SavingGoal> getSavingGoalsByWalletId(int walletId);
    
    @Query("SELECT * FROM saving_goals WHERE user_id = :userId AND wallet_id = :walletId")
    List<SavingGoal> getSavingGoalsByUserAndWallet(int userId, int walletId);
    
    @Query("SELECT * FROM saving_goals WHERE user_id = :userId AND wallet_id = :walletId AND status = :status")
    List<SavingGoal> getSavingGoalsByUserWalletAndStatus(int userId, int walletId, String status);
    
    @Query("SELECT * FROM saving_goals WHERE user_id = :userId AND status = :status")
    List<SavingGoal> getSavingGoalsByUserAndStatus(int userId, String status);
    
    @Query("SELECT * FROM saving_goals WHERE status = 'active'")
    List<SavingGoal> getActiveSavingGoals();
    
    @Query("SELECT * FROM saving_goals")
    List<SavingGoal> getAllSavingGoals();
    
    @Query("UPDATE saving_goals SET current_amount = :amount, updated_at = :timestamp WHERE id = :goalId")
    void updateCurrentAmount(int goalId, double amount, long timestamp);
    
    @Query("SELECT * FROM saving_goals WHERE user_id = :userId AND wallet_id = :walletId AND name = :name LIMIT 1")
    SavingGoal getSavingGoalByName(int userId, int walletId, String name);
    
    @Query("DELETE FROM saving_goals WHERE id = :goalId")
    void deleteById(int goalId);
}
