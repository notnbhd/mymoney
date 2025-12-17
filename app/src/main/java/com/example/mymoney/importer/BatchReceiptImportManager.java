package com.example.mymoney.importer;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manager for importing multiple receipts at once via camera or gallery
 */
public class BatchReceiptImportManager {

    private static final String TAG = "BatchReceiptImporter";
    
    public interface BatchListener {
        void onProcessingStarted(int totalCount);
        void onReceiptProcessed(int index, PendingReceipt receipt);
        void onAllProcessed(List<PendingReceipt> receipts);
        void onError(String message);
    }

    private final Context context;
    private final BatchListener listener;
    private final ReceiptOcrRepository repository;
    private final ExecutorService executor;

    private final ActivityResultLauncher<String> cameraPermissionLauncher;
    private final ActivityResultLauncher<String> storagePermissionLauncher;
    private final ActivityResultLauncher<Uri> cameraLauncher;
    private final ActivityResultLauncher<String[]> multipleGalleryLauncher;

    private boolean pendingGalleryAfterPermission = false;
    private File pendingCameraFile;
    private Uri pendingCameraUri;
    
    private List<PendingReceipt> pendingReceipts = new ArrayList<>();
    private List<File> cameraFiles = new ArrayList<>();
    private boolean isCapturingMultiple = false;

    public BatchReceiptImportManager(@NonNull Fragment fragment, @NonNull BatchListener listener) {
        this.context = fragment.requireContext();
        this.listener = listener;
        this.repository = new ReceiptOcrRepository();
        this.executor = Executors.newFixedThreadPool(2);

        this.cameraPermissionLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        openCamera();
                    } else {
                        listener.onError("Camera permission denied");
                    }
                }
        );

        this.storagePermissionLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted && pendingGalleryAfterPermission) {
                        pendingGalleryAfterPermission = false;
                        openMultipleGallery();
                    } else if (!granted) {
                        pendingGalleryAfterPermission = false;
                        listener.onError("Media permission denied");
                    }
                }
        );

        this.cameraLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (Boolean.TRUE.equals(success) && pendingCameraFile != null) {
                        cameraFiles.add(pendingCameraFile);
                        pendingCameraFile = null;
                        
                        if (isCapturingMultiple) {
                            // Ask user if they want to capture another
                            showContinueCaptureDialog(fragment);
                        } else {
                            processAllFiles();
                        }
                    } else {
                        cleanupPendingCameraFile();
                        if (!cameraFiles.isEmpty()) {
                            processAllFiles();
                        }
                    }
                }
        );

        this.multipleGalleryLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris == null || uris.isEmpty()) {
                        listener.onError("No images selected");
                        return;
                    }
                    
                    List<File> files = new ArrayList<>();
                    for (Uri uri : uris) {
                        try {
                            File tempCopy = copyUriToCache(uri);
                            files.add(tempCopy);
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to copy gallery image: " + uri, e);
                        }
                    }
                    
                    if (files.isEmpty()) {
                        listener.onError("Unable to read selected images");
                        return;
                    }
                    
                    processMultipleFiles(files);
                }
        );
    }
    
    private void showContinueCaptureDialog(Fragment fragment) {
        new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Add More Receipts?")
                .setMessage("You have captured " + cameraFiles.size() + " receipt(s). Do you want to capture another?")
                .setPositiveButton("Capture More", (d, w) -> openCamera())
                .setNegativeButton("Done", (d, w) -> processAllFiles())
                .setCancelable(false)
                .show();
    }

    public void startCameraImport() {
        isCapturingMultiple = true;
        cameraFiles.clear();
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }
    
    public void startSingleCameraImport() {
        isCapturingMultiple = false;
        cameraFiles.clear();
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    public void startGalleryImport() {
        String permission = resolveGalleryPermission();
        if (permission == null || ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED) {
            openMultipleGallery();
        } else {
            pendingGalleryAfterPermission = true;
            storagePermissionLauncher.launch(permission);
        }
    }

    private void openCamera() {
        try {
            File imageFile = createTempImageFile();
            Uri photoUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    imageFile
            );
            pendingCameraFile = imageFile;
            pendingCameraUri = photoUri;
            cameraLauncher.launch(photoUri);
        } catch (IOException e) {
            Log.e(TAG, "Unable to create temp file for camera", e);
            listener.onError("Unable to create image file");
        }
    }

    private void openMultipleGallery() {
        multipleGalleryLauncher.launch(new String[]{"image/*"});
    }
    
    private void processAllFiles() {
        if (cameraFiles.isEmpty()) {
            listener.onError("No receipts captured");
            return;
        }
        processMultipleFiles(new ArrayList<>(cameraFiles));
        cameraFiles.clear();
    }
    
    private void processMultipleFiles(List<File> files) {
        pendingReceipts.clear();
        
        for (File file : files) {
            pendingReceipts.add(new PendingReceipt(file));
        }
        
        listener.onProcessingStarted(files.size());
        
        AtomicInteger processedCount = new AtomicInteger(0);
        int total = files.size();
        
        for (int i = 0; i < pendingReceipts.size(); i++) {
            final int index = i;
            final PendingReceipt receipt = pendingReceipts.get(i);
            receipt.setProcessing(true);
            
            executor.execute(() -> {
                repository.processReceipt(receipt.getImageFile(), new ReceiptOcrRepository.ReceiptOcrCallback() {
                    @Override
                    public void onSuccess(ReceiptOcrResponse.ReceiptData data) {
                        receipt.setData(data);
                        receipt.setProcessing(false);
                        receipt.setProcessed(true);
                        
                        int completed = processedCount.incrementAndGet();
                        listener.onReceiptProcessed(index, receipt);
                        
                        if (completed == total) {
                            listener.onAllProcessed(new ArrayList<>(pendingReceipts));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        receipt.setErrorMessage(message != null ? message : "Failed to process receipt");
                        receipt.setProcessing(false);
                        receipt.setProcessed(true);
                        
                        int completed = processedCount.incrementAndGet();
                        listener.onReceiptProcessed(index, receipt);
                        
                        if (completed == total) {
                            listener.onAllProcessed(new ArrayList<>(pendingReceipts));
                        }
                    }
                });
            });
        }
    }

    private File createTempImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            storageDir = context.getCacheDir();
        }
        return File.createTempFile("receipt_" + timeStamp + "_", ".jpg", storageDir);
    }

    private File copyUriToCache(Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String mimeType = resolver.getType(uri);
        String extension = ".jpg";
        if (mimeType != null) {
            String derived = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (derived != null && !derived.isEmpty()) {
                extension = "." + derived;
            }
        }

        File tempFile = File.createTempFile("receipt_import_", extension, context.getCacheDir());
        try (InputStream inputStream = resolver.openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile)) {
            if (inputStream == null) {
                throw new IOException("Unable to open input stream");
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
    }

    private void cleanupPendingCameraFile() {
        if (pendingCameraFile != null && pendingCameraFile.exists() && !pendingCameraFile.delete()) {
            Log.w(TAG, "Unable to delete pending camera file");
        }
        pendingCameraFile = null;
        pendingCameraUri = null;
    }

    public void cleanupTempFiles() {
        for (PendingReceipt receipt : pendingReceipts) {
            File file = receipt.getImageFile();
            if (file != null && file.exists() && !file.delete()) {
                Log.w(TAG, "Unable to delete temp file: " + file.getAbsolutePath());
            }
        }
        pendingReceipts.clear();
        
        for (File file : cameraFiles) {
            if (file != null && file.exists() && !file.delete()) {
                Log.w(TAG, "Unable to delete camera file: " + file.getAbsolutePath());
            }
        }
        cameraFiles.clear();
    }

    private String resolveGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
