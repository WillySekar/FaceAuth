package com.example.faceauth.utils;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;

public class FaceDetectorHelper {

    FaceDetector detector;

    public FaceDetectorHelper() {
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .setMinFaceSize(0.10f)
                        .enableTracking()
                        .build();

        detector = FaceDetection.getClient(options);
    }

    public void detect(Bitmap bitmap, OnSuccessListener<List<Face>> success) {

        InputImage image = InputImage.fromBitmap(bitmap, 0);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    Log.d("FACE", "Detected count = " + faces.size());
                    success.onSuccess(faces);
                })
                .addOnFailureListener(e -> {
                    Log.e("FACE", "Detection failed", e);
                });
    }

    public void detect(InputImage image, OnSuccessListener<List<Face>> success, OnFailureListener failure) {
        detector.process(image)
                .addOnSuccessListener(faces -> {
                    Log.d("FACE", "Detected count = " + faces.size());
                    success.onSuccess(faces);
                })
                .addOnFailureListener(e -> {
                    Log.e("FACE", "Live Detection failed", e);
                    failure.onFailure(e); // Pass the failure back to the Activity
                });
    }
}
