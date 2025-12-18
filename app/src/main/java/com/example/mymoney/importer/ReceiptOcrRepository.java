package com.example.mymoney.importer;

import android.util.Log;

import com.example.mymoney.BuildConfig;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ReceiptOcrRepository {

    private static final String TAG = "ReceiptOcrRepository";
    // Use 10.0.2.2 for Android emulator to reach host machine's localhost
    // For physical device, use your computer's actual IP address
    private static final String DEFAULT_BASE_URL = "http://172.20.10.2:5000/";

    private final ReceiptOcrApiService apiService;

    public ReceiptOcrRepository() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(resolveBaseUrl())
                .client(buildClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(ReceiptOcrApiService.class);
    }

    private OkHttpClient buildClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private String resolveBaseUrl() {
        try {
            Field baseUrlField = BuildConfig.class.getField("RECEIPT_OCR_BASE_URL");
            Object value = baseUrlField.get(null);
            if (value instanceof String) {
                String result = ((String) value).trim();
                if (!result.isEmpty()) {
                    if (!result.endsWith("/")) {
                        result = result + "/";
                    }
                    Log.d(TAG, "Using base URL from BuildConfig: " + result);
                    return result;
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // Fall back to default URL below
        }
        Log.w(TAG, "Using default base URL for receipt OCR: " + DEFAULT_BASE_URL);
        return DEFAULT_BASE_URL;
    }

    public void processReceipt(File imageFile, ReceiptOcrCallback callback) {
        if (imageFile == null || !imageFile.exists()) {
            callback.onError("Receipt image not found");
            return;
        }

        String mimeType = URLConnection.guessContentTypeFromName(imageFile.getName());
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "image/jpeg";
        }

        RequestBody requestBody = RequestBody.create(MediaType.parse(mimeType), imageFile);
        MultipartBody.Part imagePart = MultipartBody.Part.createFormData(
                "image",
                imageFile.getName(),
                requestBody
        );

        apiService.processReceipt(imagePart).enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ReceiptOcrResponse> call, Response<ReceiptOcrResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getReceiptData() != null) {
                    callback.onSuccess(response.body().getReceiptData());
                } else {
                    String errorMessage = "Failed to parse receipt";
                    if (response.errorBody() != null) {
                        try {
                            errorMessage = response.errorBody().string();
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading error body", e);
                        }
                    }
                    callback.onError(errorMessage);
                }
            }

            @Override
            public void onFailure(Call<ReceiptOcrResponse> call, Throwable t) {
                Log.e(TAG, "Network error when processing receipt", t);
                callback.onError(t.getMessage() != null ? t.getMessage() : "Unknown network error");
            }
        });
    }

    public interface ReceiptOcrCallback {
        void onSuccess(ReceiptOcrResponse.ReceiptData data);
        void onError(String message);
    }
}