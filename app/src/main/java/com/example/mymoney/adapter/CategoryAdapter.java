package com.example.mymoney.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mymoney.R;
import com.example.mymoney.database.entity.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private List<Category> categories = new ArrayList<>();
    private int selectedCategoryId = -1;
    private OnCategoryClickListener listener;

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    public CategoryAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        notifyDataSetChanged();
    }

    public void setSelectedCategoryId(int categoryId) {
        this.selectedCategoryId = categoryId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.bind(category);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    class CategoryViewHolder extends RecyclerView.ViewHolder {
        private ImageView categoryIcon;
        private TextView categoryName;
        private ImageView checkIcon;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryIcon = itemView.findViewById(R.id.category_icon);
            categoryName = itemView.findViewById(R.id.category_name);
            checkIcon = itemView.findViewById(R.id.check_icon);
        }

        public void bind(Category category) {
            categoryName.setText(category.getName());

            // Set icon based on category name
            setCategoryIcon(category.getName());

            // Show check icon if this category is selected
            if (category.getId() == selectedCategoryId) {
                checkIcon.setVisibility(View.VISIBLE);
            } else {
                checkIcon.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            });
        }

        private void setCategoryIcon(String categoryName) {
            int iconRes;
            switch (categoryName.toLowerCase()) {
                case "food":
                    iconRes = R.drawable.ic_food;
                    break;
                case "home":
                case "housing":
                    iconRes = R.drawable.ic_home;
                    break;
                case "transport":
                    iconRes = R.drawable.ic_taxi;
                    break;
                case "relationship":
                    iconRes = R.drawable.ic_love;
                    break;
                case "entertainment":
                    iconRes = R.drawable.ic_entertainment;
                    break;
                case "medical":
                    iconRes = R.drawable.ic_medical;
                    break;
                case "tax":
                    iconRes = R.drawable.tax_accountant_fee_svgrepo_com;
                    break;
                case "gym & fitness":
                    iconRes = R.drawable.ic_gym;
                    break;
                case "beauty":
                    iconRes = R.drawable.ic_beauty;
                    break;
                case "clothing":
                    iconRes = R.drawable.ic_clothing;
                    break;
                case "education":
                    iconRes = R.drawable.ic_education;
                    break;
                case "childcare":
                    iconRes = R.drawable.ic_childcare;
                    break;
                case "salary":
                    iconRes = R.drawable.ic_salary;
                    break;
                case "business":
                    iconRes = R.drawable.ic_work;
                    break;
                case "gifts":
                    iconRes = R.drawable.ic_gift;
                    break;
                case "groceries":
                    iconRes = R.drawable.ic_groceries;
                    break;
                case "others":
                default:
                    iconRes = R.drawable.ic_more_apps;
                    break;
            }
            categoryIcon.setImageResource(iconRes);
        }
    }
}