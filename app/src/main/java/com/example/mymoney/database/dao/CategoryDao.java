package com.example.mymoney.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mymoney.database.entity.Category;

import java.util.List;

@Dao
public interface CategoryDao {
    
    @Insert
    long insert(Category category);
    
    @Update
    void update(Category category);
    
    @Delete
    void delete(Category category);
    
    @Query("SELECT * FROM category WHERE id = :categoryId")
    Category getCategoryById(int categoryId);

    @Query("SELECT * FROM category WHERE type = 'expense'")
    List<Category> getAllExpenseCategories();
    
    @Query("SELECT * FROM category WHERE type = 'income'")
    List<Category> getAllIncomeCategories();
    
    @Query("SELECT * FROM category")
    List<Category> getAllCategories();
    
    @Query("SELECT * FROM category WHERE name = :name LIMIT 1")
    Category getCategoryByName(String name);
    
    @Query("DELETE FROM category WHERE id = :categoryId")
    void deleteById(int categoryId);
}
