package com.example.mymoney.chatbot;

import android.content.Context;
import android.util.Log;

import com.example.mymoney.BuildConfig;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Category;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * LLM-powered query parser that extracts structured intent from natural language questions.
 * Supports both Vietnamese and English queries.
 */
public class QueryParser {
    private static final String TAG = "QueryParser";
    private static final String OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/";
    private static final String API_TOKEN = BuildConfig.OPENROUTER_API_TOKEN;
    private static final String MODEL = "mistralai/devstral-2512:free";

    private OpenRouterApiService apiService;
    private AppDatabase database;
    private Context context;
    private Gson gson;

    // Category names for the prompt
    private static final String CATEGORY_LIST = 
        "Food, Home, Transport, Relationship, Entertainment, Medical, Tax, " +
        "Gym & Fitness, Beauty, Clothing, Education, Childcare, Groceries, Others, " +
        "Salary, Bonus, Investment, Gift, Freelance, Refund, Rental, Interest";

    public QueryParser(Context context) {
        this.context = context;
        this.database = AppDatabase.getInstance(context);
        this.gson = new Gson();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(OPENROUTER_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiService = retrofit.create(OpenRouterApiService.class);
    }

    /**
     * Parse user query to extract intent (async with callback)
     */
    public void parseQuery(String userMessage, QueryParserCallback callback) {
        new Thread(() -> {
            try {
                QueryIntent intent = parseQuerySync(userMessage);
                callback.onSuccess(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error parsing query", e);
                // Return default intent on error
                QueryIntent defaultIntent = createDefaultIntent(userMessage);
                callback.onSuccess(defaultIntent);
            }
        }).start();
    }

    /**
     * Parse query synchronously (for use in background threads)
     */
    public QueryIntent parseQuerySync(String userMessage) {
        Log.d(TAG, "Parsing query: " + userMessage);

        // Get current date info for context
        Calendar now = Calendar.getInstance();
        int currentMonth = now.get(Calendar.MONTH) + 1;
        int currentYear = now.get(Calendar.YEAR);

        // Build the parsing prompt
        String systemPrompt = buildParsingPrompt(currentMonth, currentYear);
        
        OpenRouterRequest request = new OpenRouterRequest(MODEL);
        request.setTemperature(0.1); // Low temperature for consistent parsing
        request.setMax_tokens(200);
        request.addMessage("system", systemPrompt);
        request.addMessage("user", userMessage);

        // Use synchronous call with timeout
        AtomicReference<QueryIntent> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Call<OpenRouterResponse> call = apiService.generateResponse(
            "Bearer " + API_TOKEN,
            "https://github.com/notnbhd/mymoney",
            "MyMoney App",
            request
        );

        call.enqueue(new Callback<OpenRouterResponse>() {
            @Override
            public void onResponse(Call<OpenRouterResponse> call, Response<OpenRouterResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonResponse = response.body().getGeneratedText();
                    Log.d(TAG, "Parser response: " + jsonResponse);
                    QueryIntent intent = parseJsonResponse(jsonResponse, userMessage, currentMonth, currentYear);
                    resultRef.set(intent);
                } else {
                    Log.e(TAG, "Parser API error: " + response.code());
                    resultRef.set(createDefaultIntent(userMessage));
                }
                latch.countDown();
            }

            @Override
            public void onFailure(Call<OpenRouterResponse> call, Throwable t) {
                Log.e(TAG, "Parser API failure", t);
                resultRef.set(createDefaultIntent(userMessage));
                latch.countDown();
            }
        });

        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Parser timeout");
            return createDefaultIntent(userMessage);
        }

        QueryIntent result = resultRef.get();
        if (result == null) {
            result = createDefaultIntent(userMessage);
        }

        // Calculate actual timestamps from parsed data
        calculateTimestamps(result);
        
        // Resolve category name to ID if specified
        resolveCategoryId(result);

        Log.d(TAG, "Parsed intent: " + result);
        return result;
    }

    private String buildParsingPrompt(int currentMonth, int currentYear) {
        return "You are a financial query parser. Parse the user's question and extract structured information.\n" +
               "Current date context: Month " + currentMonth + ", Year " + currentYear + "\n\n" +
               "Output ONLY valid JSON (no markdown, no explanation) in this exact format:\n" +
               "{\n" +
               "  \"timeRange\": {\"type\": \"current_month|specific_month|year|last_n_days|all_time\", \"month\": 1-12, \"year\": 2020-2030, \"days\": number},\n" +
               "  \"category\": \"category_name or null\",\n" +
               "  \"queryType\": \"spending|income|comparison|trend|category_list|general\",\n" +
               "  \"needsClarification\": true/false,\n" +
               "  \"clarificationMessage\": \"message to ask user if clarification needed\"\n" +
               "}\n\n" +
               "Valid categories: " + CATEGORY_LIST + "\n\n" +
               "Time parsing rules:\n" +
               "- 'tháng 12' or 'December' = specific_month with month=12\n" +
               "- 'tháng trước' or 'last month' = specific_month with month=" + (currentMonth == 1 ? 12 : currentMonth - 1) + "\n" +
               "- 'năm nay' or 'this year' = year with year=" + currentYear + "\n" +
               "- 'năm ngoái' or 'last year' = year with year=" + (currentYear - 1) + "\n" +
               "- 'tuần qua' or 'last week' = last_n_days with days=7\n" +
               "- If no time specified = current_month\n\n" +
               "Category matching:\n" +
               "- 'clothes/quần áo' = Clothing\n" +
               "- 'food/ăn uống/đồ ăn' = Food\n" +
               "- 'transport/đi lại/xe' = Transport\n" +
               "- 'gym/tập thể dục' = Gym & Fitness\n\n" +
               "Examples:\n" +
               "- 'How much did I spend on clothes in December?' → {\"timeRange\":{\"type\":\"specific_month\",\"month\":12,\"year\":" + currentYear + "},\"category\":\"Clothing\",\"queryType\":\"spending\",\"needsClarification\":false}\n" +
               "- 'Tháng trước tôi chi bao nhiêu tiền ăn?' → {\"timeRange\":{\"type\":\"specific_month\",\"month\":" + (currentMonth == 1 ? 12 : currentMonth - 1) + ",\"year\":" + (currentMonth == 1 ? currentYear - 1 : currentYear) + "},\"category\":\"Food\",\"queryType\":\"spending\",\"needsClarification\":false}\n" +
               "- 'Show my spending' → {\"timeRange\":{\"type\":\"current_month\"},\"category\":null,\"queryType\":\"spending\",\"needsClarification\":true,\"clarificationMessage\":\"Bạn muốn xem chi tiêu trong khoảng thời gian nào? (tháng này, tháng trước, năm nay...)\"}";
    }

    private QueryIntent parseJsonResponse(String jsonResponse, String originalQuery, int currentMonth, int currentYear) {
        QueryIntent intent = new QueryIntent();
        intent.setOriginalQuery(originalQuery);

        try {
            // Clean up the response - remove markdown code blocks if present
            String cleanJson = jsonResponse.trim();
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            }

            JsonObject json = JsonParser.parseString(cleanJson).getAsJsonObject();

            // Parse time range
            if (json.has("timeRange") && !json.get("timeRange").isJsonNull()) {
                JsonObject timeRange = json.getAsJsonObject("timeRange");
                String type = timeRange.has("type") ? timeRange.get("type").getAsString() : "current_month";
                
                switch (type) {
                    case "specific_month":
                        intent.setTimeRangeType(QueryIntent.TimeRangeType.SPECIFIC_MONTH);
                        intent.setMonth(timeRange.has("month") ? timeRange.get("month").getAsInt() : currentMonth);
                        intent.setYear(timeRange.has("year") ? timeRange.get("year").getAsInt() : currentYear);
                        break;
                    case "year":
                        intent.setTimeRangeType(QueryIntent.TimeRangeType.YEAR);
                        intent.setYear(timeRange.has("year") ? timeRange.get("year").getAsInt() : currentYear);
                        break;
                    case "last_n_days":
                        intent.setTimeRangeType(QueryIntent.TimeRangeType.LAST_N_DAYS);
                        intent.setDays(timeRange.has("days") ? timeRange.get("days").getAsInt() : 7);
                        break;
                    case "all_time":
                        intent.setTimeRangeType(QueryIntent.TimeRangeType.ALL_TIME);
                        break;
                    default:
                        intent.setTimeRangeType(QueryIntent.TimeRangeType.CURRENT_MONTH);
                }
            }

            // Parse category
            if (json.has("category") && !json.get("category").isJsonNull()) {
                intent.setCategoryName(json.get("category").getAsString());
            }

            // Parse query type
            if (json.has("queryType") && !json.get("queryType").isJsonNull()) {
                String queryType = json.get("queryType").getAsString();
                switch (queryType) {
                    case "spending": intent.setQueryType(QueryIntent.QueryType.SPENDING); break;
                    case "income": intent.setQueryType(QueryIntent.QueryType.INCOME); break;
                    case "comparison": intent.setQueryType(QueryIntent.QueryType.COMPARISON); break;
                    case "trend": intent.setQueryType(QueryIntent.QueryType.TREND); break;
                    case "category_list": intent.setQueryType(QueryIntent.QueryType.CATEGORY_LIST); break;
                    default: intent.setQueryType(QueryIntent.QueryType.GENERAL);
                }
            }

            // Parse clarification
            if (json.has("needsClarification") && json.get("needsClarification").getAsBoolean()) {
                intent.setNeedsClarification(true);
                if (json.has("clarificationMessage") && !json.get("clarificationMessage").isJsonNull()) {
                    intent.setClarificationMessage(json.get("clarificationMessage").getAsString());
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON response: " + jsonResponse, e);
            return createDefaultIntent(originalQuery);
        }

        return intent;
    }

    private void calculateTimestamps(QueryIntent intent) {
        Calendar calendar = Calendar.getInstance();
        
        switch (intent.getTimeRangeType()) {
            case CURRENT_MONTH:
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                intent.setTimeRangeStart(calendar.getTimeInMillis());
                intent.setTimeRangeEnd(System.currentTimeMillis());
                break;
                
            case SPECIFIC_MONTH:
                calendar.set(Calendar.YEAR, intent.getYear());
                calendar.set(Calendar.MONTH, intent.getMonth() - 1); // Calendar months are 0-indexed
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                intent.setTimeRangeStart(calendar.getTimeInMillis());
                
                calendar.add(Calendar.MONTH, 1);
                calendar.add(Calendar.MILLISECOND, -1);
                intent.setTimeRangeEnd(calendar.getTimeInMillis());
                break;
                
            case YEAR:
                calendar.set(Calendar.YEAR, intent.getYear());
                calendar.set(Calendar.MONTH, Calendar.JANUARY);
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                intent.setTimeRangeStart(calendar.getTimeInMillis());
                
                calendar.add(Calendar.YEAR, 1);
                calendar.add(Calendar.MILLISECOND, -1);
                intent.setTimeRangeEnd(calendar.getTimeInMillis());
                break;
                
            case LAST_N_DAYS:
                calendar.add(Calendar.DAY_OF_YEAR, -intent.getDays());
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                intent.setTimeRangeStart(calendar.getTimeInMillis());
                intent.setTimeRangeEnd(System.currentTimeMillis());
                break;
                
            case ALL_TIME:
                intent.setTimeRangeStart(0);
                intent.setTimeRangeEnd(System.currentTimeMillis());
                break;
        }
    }

    private void resolveCategoryId(QueryIntent intent) {
        if (intent.getCategoryName() != null && !intent.getCategoryName().isEmpty()) {
            Log.d(TAG, "Resolving category: " + intent.getCategoryName());
            Category category = database.categoryDao().getCategoryByName(intent.getCategoryName());
            if (category != null) {
                intent.setCategoryId(category.getId());
                Log.d(TAG, "Category resolved to ID: " + category.getId());
            } else {
                // Try case-insensitive search by getting all categories
                List<Category> allCategories = database.categoryDao().getAllCategories();
                for (Category cat : allCategories) {
                    if (cat.getName().equalsIgnoreCase(intent.getCategoryName())) {
                        intent.setCategoryId(cat.getId());
                        intent.setCategoryName(cat.getName()); // Use exact name from DB
                        Log.d(TAG, "Category resolved (case-insensitive) to ID: " + cat.getId());
                        return;
                    }
                }
                Log.w(TAG, "Category not found: " + intent.getCategoryName());
            }
        }
    }

    private QueryIntent createDefaultIntent(String originalQuery) {
        QueryIntent intent = new QueryIntent();
        intent.setOriginalQuery(originalQuery);
        intent.setTimeRangeType(QueryIntent.TimeRangeType.CURRENT_MONTH);
        intent.setQueryType(QueryIntent.QueryType.GENERAL);
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        intent.setTimeRangeStart(calendar.getTimeInMillis());
        intent.setTimeRangeEnd(System.currentTimeMillis());
        
        return intent;
    }

    public interface QueryParserCallback {
        void onSuccess(QueryIntent intent);
    }
}
