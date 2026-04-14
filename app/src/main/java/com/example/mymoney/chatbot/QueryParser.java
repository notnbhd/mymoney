package com.example.mymoney.chatbot;

import android.content.Context;
import android.util.Log;

import com.example.mymoney.BuildConfig;
import com.example.mymoney.database.AppDatabase;
import com.example.mymoney.database.entity.Category;

import java.lang.reflect.Field;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Query parser that calls the backend /parse endpoint to extract structured intent
 * from natural language questions. Supports both Vietnamese and English queries.
 *
 * All LLM calls are now server-side — the Android app never needs the OpenRouter API token.
 */
public class QueryParser {
    private static final String TAG = "QueryParser";
    private static final String DEFAULT_BACKEND_BASE_URL = "http://192.168.1.16:8010/";
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 30;

    private BackendApiService apiService;
    private AppDatabase database;
    private Context context;

    public QueryParser(Context context) {
        this.context = context;
        this.database = AppDatabase.getInstance(context);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(resolveBackendBaseUrl())
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        this.apiService = retrofit.create(BackendApiService.class);
    }

    private String resolveBackendBaseUrl() {
        try {
            Field baseUrlField = BuildConfig.class.getField("CHATBOT_BACKEND_BASE_URL");
            Object value = baseUrlField.get(null);
            if (value instanceof String) {
                String result = ((String) value).trim();
                if (!result.isEmpty()) {
                    if (!result.startsWith("http://") && !result.startsWith("https://")) {
                        result = "http://" + result;
                    }
                    if (!result.endsWith("/")) {
                        result = result + "/";
                    }
                    Log.d(TAG, "Using backend URL from BuildConfig: " + result);
                    return result;
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}

        Log.w(TAG, "Using default backend URL: " + DEFAULT_BACKEND_BASE_URL);
        return DEFAULT_BACKEND_BASE_URL;
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
                QueryIntent defaultIntent = createDefaultIntent(userMessage);
                callback.onSuccess(defaultIntent);
            }
        }).start();
    }

    /**
     * Parse query synchronously by calling the backend /parse endpoint.
     */
    public QueryIntent parseQuerySync(String userMessage) {
        Log.d(TAG, "Parsing query via backend: " + userMessage);

        Calendar now = Calendar.getInstance();
        int currentMonth = now.get(Calendar.MONTH) + 1;
        int currentYear = now.get(Calendar.YEAR);

        // Build request for backend /parse endpoint
        BackendApiService.BackendParseRequest request =
                new BackendApiService.BackendParseRequest(userMessage, currentMonth, currentYear);

        AtomicReference<QueryIntent> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Call<BackendApiService.BackendParseResponse> call = apiService.parse(request);
        call.enqueue(new Callback<BackendApiService.BackendParseResponse>() {
            @Override
            public void onResponse(Call<BackendApiService.BackendParseResponse> call,
                                   Response<BackendApiService.BackendParseResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    BackendApiService.BackendParseResponse parseResponse = response.body();
                    Log.d(TAG, "Backend parse response: queryType=" + parseResponse.getQueryType()
                            + ", category=" + parseResponse.getCategory());
                    QueryIntent intent = convertToQueryIntent(parseResponse, userMessage, currentMonth, currentYear);
                    resultRef.set(intent);
                } else {
                    Log.e(TAG, "Backend /parse error: " + response.code());
                    resultRef.set(createDefaultIntent(userMessage));
                }
                latch.countDown();
            }

            @Override
            public void onFailure(Call<BackendApiService.BackendParseResponse> call, Throwable t) {
                Log.e(TAG, "Backend /parse failure", t);
                resultRef.set(createDefaultIntent(userMessage));
                latch.countDown();
            }
        });

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Parse timeout");
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

    /**
     * Convert backend parse response to QueryIntent.
     */
    private QueryIntent convertToQueryIntent(BackendApiService.BackendParseResponse response,
                                              String originalQuery, int currentMonth, int currentYear) {
        QueryIntent intent = new QueryIntent();
        intent.setOriginalQuery(originalQuery);

        // Parse time range
        BackendApiService.ParsedTimeRange timeRange = response.getTimeRange();
        if (timeRange != null) {
            switch (timeRange.getType()) {
                case "specific_month":
                    intent.setTimeRangeType(QueryIntent.TimeRangeType.SPECIFIC_MONTH);
                    intent.setMonth(timeRange.getMonth() != null ? timeRange.getMonth() : currentMonth);
                    intent.setYear(timeRange.getYear() != null ? timeRange.getYear() : currentYear);
                    break;
                case "year":
                    intent.setTimeRangeType(QueryIntent.TimeRangeType.YEAR);
                    intent.setYear(timeRange.getYear() != null ? timeRange.getYear() : currentYear);
                    break;
                case "last_n_days":
                    intent.setTimeRangeType(QueryIntent.TimeRangeType.LAST_N_DAYS);
                    intent.setDays(timeRange.getDays() != null ? timeRange.getDays() : 7);
                    break;
                case "all_time":
                    intent.setTimeRangeType(QueryIntent.TimeRangeType.ALL_TIME);
                    break;
                default:
                    intent.setTimeRangeType(QueryIntent.TimeRangeType.CURRENT_MONTH);
            }
        }

        // Parse category
        if (response.getCategory() != null && !response.getCategory().isEmpty()) {
            intent.setCategoryName(response.getCategory());
        }

        // Parse query type
        String queryType = response.getQueryType();
        if (queryType != null) {
            switch (queryType) {
                case "spending": intent.setQueryType(QueryIntent.QueryType.SPENDING); break;
                case "income": intent.setQueryType(QueryIntent.QueryType.INCOME); break;
                case "comparison": intent.setQueryType(QueryIntent.QueryType.COMPARISON); break;
                case "trend": intent.setQueryType(QueryIntent.QueryType.TREND); break;
                case "category_list": intent.setQueryType(QueryIntent.QueryType.CATEGORY_LIST); break;
                default: intent.setQueryType(QueryIntent.QueryType.GENERAL);
            }
        }

        intent.setNeedsClarification(false);
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
                calendar.set(Calendar.MONTH, intent.getMonth() - 1);
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
                List<Category> allCategories = database.categoryDao().getAllCategories();
                for (Category cat : allCategories) {
                    if (cat.getName().equalsIgnoreCase(intent.getCategoryName())) {
                        intent.setCategoryId(cat.getId());
                        intent.setCategoryName(cat.getName());
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
