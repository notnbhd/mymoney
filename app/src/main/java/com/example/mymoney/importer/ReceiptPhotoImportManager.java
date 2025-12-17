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
import java.util.Date;
import java.util.Locale;

public class ReceiptPhotoImportManager {

    private static final String TAG = "ReceiptPhotoImporter";

    public interface Listener {
        void onProcessing();
        void onSuccess(ReceiptOcrResponse.ReceiptData data);
        void onError(String message);
    }

    private final Context context;
    private final Listener listener;
    private final ReceiptOcrRepository repository;

    private final ActivityResultLauncher<String> cameraPermissionLauncher;
    private final ActivityResultLauncher<String> storagePermissionLauncher;
    private final ActivityResultLauncher<Uri> cameraLauncher;
    private final ActivityResultLauncher<String> galleryLauncher;

    private boolean pendingGalleryAfterPermission = false;
    private File pendingCameraFile;
    private Uri pendingCameraUri;

    public ReceiptPhotoImportManager(@NonNull Fragment fragment, @NonNull Listener listener) {
        this.context = fragment.requireContext();
        this.listener = listener;
        this.repository = new ReceiptOcrRepository();

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
                        openGallery();
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
                        sendImageToServer(pendingCameraFile);
                    } else {
                        cleanupPendingCameraFile();
                    }
                }
        );

        this.galleryLauncher = fragment.registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) {
                        listener.onError("No image selected");
                        return;
                    }
                    try {
                        File tempCopy = copyUriToCache(uri);
                        sendImageToServer(tempCopy);
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to copy gallery image", e);
                        listener.onError("Unable to read selected image");
                    }
                }
        );
    }

    public void startCameraImport() {
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
            openGallery();
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

    private void openGallery() {
        galleryLauncher.launch("image/*");
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

    private void sendImageToServer(File imageFile) {
        listener.onProcessing();
        repository.processReceipt(imageFile, new ReceiptOcrRepository.ReceiptOcrCallback() {
            @Override
            public void onSuccess(ReceiptOcrResponse.ReceiptData data) {
                cleanupTempFile(imageFile);
                listener.onSuccess(data);
            }

            @Override
            public void onError(String message) {
                cleanupTempFile(imageFile);
                listener.onError(message != null ? message : "Failed to process receipt");
            }
        });
    }

    private void cleanupPendingCameraFile() {
        if (pendingCameraFile != null && pendingCameraFile.exists() && !pendingCameraFile.delete()) {
            Log.w(TAG, "Unable to delete pending camera file");
        }
        pendingCameraFile = null;
        pendingCameraUri = null;
    }

    private void cleanupTempFile(File file) {
        if (file == null) {
            return;
        }
        if (file.equals(pendingCameraFile)) {
            cleanupPendingCameraFile();
            return;
        }
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Unable to delete temp file: " + file.getAbsolutePath());
        }
    }

    private String resolveGalleryPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }
}