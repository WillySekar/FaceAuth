package com.example.faceauth.UI;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.example.faceauth.R;
import com.example.faceauth.model.DBHelper;
import com.example.faceauth.utils.FaceDetectorHelper;
import com.example.faceauth.utils.FaceRecognizer;
import com.example.faceauth.utils.Utils;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class activity_live_attendances extends AppCompatActivity {

    private PreviewView previewView;
    private TextView tvStatus;

    private FaceDetectorHelper detectorHelper;
    private FaceRecognizer recognizer;
    private DBHelper db;
    private ExecutorService cameraExecutor;

    private boolean isProcessing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_attendances);

        try {
            previewView = findViewById(R.id.previewView);
            tvStatus = findViewById(R.id.tvStatus);

            detectorHelper = new FaceDetectorHelper();
            db = new DBHelper(this);
            try {
                recognizer = new FaceRecognizer(getAssets());
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Model load failed", Toast.LENGTH_SHORT).show();
            }

            cameraExecutor = Executors.newSingleThreadExecutor();
            startCamera();

        }catch (Exception Ignored){}

    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::processImageProxy);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("LIVE_CAMERA", "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void processImageProxy(ImageProxy imageProxy) {
        if (isProcessing) {
            imageProxy.close();
            return;
        }
        isProcessing = true;

        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
            InputImage image = InputImage.fromMediaImage(mediaImage, rotationDegrees);

            // Call the helper with BOTH success and failure listeners
            detectorHelper.detect(image, faces -> {

                // SUCCESS LISTENER LOGIC
                if (faces.isEmpty()) {
                    tvStatus.setText("No face detected");
                    isProcessing = false;
                    imageProxy.close();
                    return;
                }

                // Convert YUV to Bitmap and rotate it correctly for cropping
                Bitmap bitmap = toBitmap(mediaImage);
                Bitmap rotatedBitmap = rotateBitmap(bitmap, rotationDegrees);

                // If using front camera, you might need to flip the bitmap horizontally to avoid mirror issues
                // rotatedBitmap = flipBitmap(rotatedBitmap);

                recognizeFace(rotatedBitmap, faces.get(0).getBoundingBox());
                imageProxy.close();

            }, e -> {
                Log.e("LIVE_CAMERA", "Detection failed", e);
                isProcessing = false;
                imageProxy.close();
            });

        } else {
            isProcessing = false;
            imageProxy.close();
        }
    }

    private void recognizeFace(Bitmap capturedBitmap, Rect rect) {
        try {
            int paddingX = rect.width() / 5;
            int paddingY = rect.height() / 5;

            int left = Math.max(0, rect.left - paddingX);
            int top = Math.max(0, rect.top - paddingY);

            int right = Math.min(
                    capturedBitmap.getWidth(),
                    rect.right + paddingX
            );

            int bottom = Math.min(
                    capturedBitmap.getHeight(),
                    rect.bottom + paddingY
            );

            int width = right - left;
            int height = bottom - top;
            Bitmap cropped = Bitmap.createBitmap(capturedBitmap, left, top, width, height);
            Bitmap face = Bitmap.createScaledBitmap(cropped, 112, 112, true);

            float[][] currentEmbedding = recognizer.getEmbedding(face);
            float[] current = Utils.normalize(currentEmbedding[0]);

            Cursor cursor = db.getAllUsers();
            String bestName = "Unknown";
            float bestDistance = Float.MAX_VALUE;

            while (cursor.moveToNext()) {
                String name = cursor.getString(1);
                String emb = cursor.getString(2);
                Log.d("DBUSER", "name=" + name);

                float[] storedEmbedding = Utils.stringToEmbedding(emb);
                float[] stored = Utils.normalize(storedEmbedding);

                float distance = Utils.distance(current, stored);
                Log.d("LIVE_MATCH", name + "=" + distance);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestName = name;
                }
            }
            cursor.close();

            String finalBestName = bestName;
            float finalBestDistance = bestDistance;
            Log.d("BEST MATCH", "best Match=" + finalBestDistance);
            // Update UI on Main Thread
            runOnUiThread(() -> {
                if (finalBestDistance < 1.1f) {
                    tvStatus.setText("Recognized: " + finalBestName);
                    tvStatus.setTextColor(0xFF00FF00); // Green
                } else {
                    tvStatus.setText("Unknown Face");
                    tvStatus.setTextColor(0xFFFF0000); // Red
                }

                // Add a small delay before processing the next frame so the UI isn't chaotic
                tvStatus.postDelayed(() -> isProcessing = false, 1000);
            });

        } catch (Exception e) {
            Log.e("LIVE_RECOGNITION", "Recognition error", e);
            isProcessing = false;
        }
    }

    // Utility to convert CameraX Image to Bitmap
    private Bitmap toBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        if (rotationDegrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        // Uncomment the line below if using the front camera to fix mirroring issues
        // matrix.preScale(-1.0f, 1.0f);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}