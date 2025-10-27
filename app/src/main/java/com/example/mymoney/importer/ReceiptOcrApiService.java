package com.example.mymoney.importer;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

/**
 * Retrofit definition for the receipt OCR backend service.
 */
public interface ReceiptOcrApiService {

    @Multipart
    @POST("api/process-receipt")
    Call<ReceiptOcrResponse> processReceipt(@Part MultipartBody.Part imagePart);
}
