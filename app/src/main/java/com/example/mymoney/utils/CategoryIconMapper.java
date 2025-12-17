package com.example.mymoney.utils;

import com.example.mymoney.R;
import java.util.Locale;

public class CategoryIconMapper {

    public static int getIcon(String categoryName) {
        if (categoryName == null) return R.drawable.ic_more_apps;

        switch (categoryName.toLowerCase(Locale.ROOT).trim()) {

            case "food":
                return R.drawable.ic_food;

            case "home":
                return R.drawable.ic_home;

            case "transport":
                return R.drawable.ic_taxi;

            case "relationship":
                return R.drawable.ic_love;

            case "entertainment":
                return R.drawable.ic_entertainment;

            case "medical":
                return R.drawable.ic_medical;

            case "tax":
                return R.drawable.tax_accountant_fee_svgrepo_com;

            case "gym & fitness":
                return R.drawable.ic_gym;

            case "beauty":
                return R.drawable.ic_beauty;

            case "clothing":
                return R.drawable.ic_clothing;

            case "education":
                return R.drawable.ic_education;

            case "childcare":
                return R.drawable.ic_childcare;

            case "salary":
                return R.drawable.ic_salary;

            case "business":
                return R.drawable.ic_work;

            case "gifts":
                return R.drawable.ic_gift;

            case "others":
            default:
                return R.drawable.ic_more_apps;
        }
    }
}
