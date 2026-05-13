package com.example.faceauth.UI;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.example.faceauth.R;
import com.example.faceauth.model.DBHelper;
import com.example.faceauth.utils.FaceDetectorHelper;
import com.example.faceauth.utils.FaceRecognizer;
import com.example.faceauth.utils.Utils;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class LiveAttendanceActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextView txtName;

    private FaceDetectorHelper detectorHelper;
    private FaceRecognizer recognizer;
    private DBHelper db;

    private long lastRecognitionTime = 0;
    private String lastName = "";
    private long lastShownTime = 0;

    private String candidateName = "";
    private int candidateCount = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_attendance);

        try{
            previewView = findViewById(R.id.previewView);
            txtName = findViewById(R.id.txtName);

            detectorHelper = new FaceDetectorHelper();
            db = new DBHelper(this);

            try {
                recognizer = new FaceRecognizer(getAssets());
            } catch (Exception e) {
                Log.e("MODEL", "Load failed", e);
            }
            startCamera();
        }catch(Exception Ignored){}
    }
    private void startCamera() {

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {

            try {

                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();

                CameraSelector selector =
                        CameraSelector.DEFAULT_FRONT_CAMERA;

                ImageAnalysis analysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();

                analysis.setAnalyzer(
                        ContextCompat.getMainExecutor(this),
                        image -> {

                            try {

                                long now = System.currentTimeMillis();

                                if (now - lastRecognitionTime < 1000) {
                                    image.close();
                                    return;
                                }

                                lastRecognitionTime = now;
                                Bitmap bitmap = imageProxyToBitmap(image);

                                if (bitmap == null) {
                                    image.close();
                                    return;
                                }


                                recognizeFrame(bitmap);

                            } catch (Exception e) {
                                Log.e("FRAME", "Analyzer error", e);
                            }

                            image.close();
                        });

                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider()
                );

                provider.unbindAll();

                provider.bindToLifecycle(
                        this,
                        selector,
                        preview,
                        analysis
                );

            } catch (Exception e) {
                Log.e("CAMERA", "Start failed", e);
            }

        }, ContextCompat.getMainExecutor(this));
    }

    private void recognizeFrame(Bitmap capturedBitmap) {

        detectorHelper.detect(capturedBitmap, faces -> {

            try {

                if (faces == null || faces.size() == 0) {
                    txtName.setText("Waiting...");
                    return;
                }
                Log.d("BITMAP_SIZE", capturedBitmap.getWidth() + " x " + capturedBitmap.getHeight());
                Rect rect = faces.get(0).getBoundingBox();

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
                Log.d("BITMAP_SIZE2", capturedBitmap.getWidth() + " x " + capturedBitmap.getHeight());
                int width = right - left;
                int height = bottom - top;

                if (width <= 0 || height <= 0) return;

                Bitmap cropped = Bitmap.createBitmap(
                        capturedBitmap,
                        left,
                        top,
                        width,
                        height
                );

                Bitmap face = Bitmap.createScaledBitmap(
                        cropped,
                        112,
                        112,
                        true
                );

                float[][] currentEmbedding =
                        recognizer.getEmbedding(face);

                float[] current =
                        Utils.normalize(currentEmbedding[0]);

                Cursor cursor = db.getAllUsers();

                String bestName = "Unknown";
                float bestDistance = Float.MAX_VALUE;
                float secondBest = Float.MAX_VALUE;

                while (cursor.moveToNext()) {

                    String name = cursor.getString(1);
                    String emb = cursor.getString(2);

                    float[] storedEmbedding = Utils.stringToEmbedding(emb);

                    float[] stored = Utils.normalize(storedEmbedding);
                    float distance = Utils.distance(current, stored);

                    Log.d("LIVE_MATCH", name + "=" + distance);

                    if (distance < bestDistance) {
                        secondBest = bestDistance;
                        bestDistance = distance;
                        bestName = name;

                    } else if (distance < secondBest) {
                        secondBest = distance;
                    }
                }

                cursor.close();
                Log.d("BEST", bestName +
                                " best=" + bestDistance +
                                " second=" + secondBest);

                if (bestDistance < 0.95f && (secondBest - bestDistance) > 0.05f) {

                    if (bestName.equals(candidateName)) {
                        candidateCount++;
                    } else {
                        candidateName = bestName;
                        candidateCount = 1;
                    }

                    if (candidateCount >= 3) {

                        long now = System.currentTimeMillis();

                        if (!bestName.equals(lastName) || now - lastShownTime > 3000) {

                            lastName = bestName;
                            lastShownTime = now;

                            txtName.setText(
                                    "Recognized: " + bestName
                            );
                        }
                    }

                } else {

                    candidateCount = 0;
                    txtName.setText("Unknown Face");
                }

            } catch (Exception e) {
                Log.e("LIVE", "Recognition error", e);
            }
        });
    }

    private Bitmap mirrorBitmap(Bitmap src) {

        Matrix matrix = new Matrix();
        matrix.preScale(-1, 1);

        return Bitmap.createBitmap(
                src,
                0,
                0,
                src.getWidth(),
                src.getHeight(),
                matrix,
                true
        );
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {

        try {

            ImageProxy.PlaneProxy[] planes = image.getPlanes();

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

            YuvImage yuvImage = new YuvImage(
                    nv21,
                    ImageFormat.NV21,
                    image.getWidth(),
                    image.getHeight(),
                    null
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()),
                    100,
                    out
            );

            byte[] imageBytes = out.toByteArray();

            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    imageBytes,
                    0,
                    imageBytes.length
            );

            Matrix matrix = new Matrix();

            matrix.preScale(-1, 1);

            return Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true
            );

        } catch (Exception e) {

            Log.e("CONVERT", "Bitmap conversion failed", e);
            return null;
        }
    }
}