package com.example.faceauth.UI;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.biometric.BiometricPrompt;
import com.example.faceauth.R;
import com.example.faceauth.model.DBHelper;
import com.example.faceauth.model.FaceUtils;
import com.example.faceauth.utils.FaceDetectorHelper;
import com.example.faceauth.utils.FaceRecognizer;
import com.example.faceauth.utils.Utils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RegisterActvity extends AppCompatActivity {
    private TextInputEditText etName;
    private Button btnFaceRegister, btnFingerRegister;
    private Bitmap bitmap;
    private ImageView imageView;
    FaceDetectorHelper detectorHelper;
    FaceRecognizer recognizer;
    DBHelper db;
    private static final int CAMERA_REQUEST = 100;
    private Bitmap capturedBitmap;
    private Uri image_uri;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_actvity);

        try {
            //etName = findViewById(R.id.edt_name);
            btnFaceRegister = findViewById(R.id.btnFaceRegister);
            btnFingerRegister = findViewById(R.id.btnFingerRegister);
            imageView = findViewById(R.id.imageView);

            detectorHelper = new FaceDetectorHelper();
            db = new DBHelper(this);

            try {
                recognizer = new FaceRecognizer(getAssets());
            } catch (Exception e) {
                Log.e("MODEL", "Load failed", e);
            }
            btnFaceRegister.setOnClickListener(view -> {
                openCamera();
            });


        }catch (Exception Ignored){}

    }
    private void openCamera() {
           try {
               ContentValues values = new ContentValues();
               values.put(MediaStore.Images.Media.TITLE, "face");
               image_uri = getContentResolver().insert(
                       MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                       values
               );

               Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
               intent.putExtra(MediaStore.EXTRA_OUTPUT, image_uri);
               startActivityForResult(intent, CAMERA_REQUEST);
           }catch (Exception Ignored){}
    }

    private void registerFace() {

        if (capturedBitmap == null) {
            Toast.makeText(this, "Take image first", Toast.LENGTH_SHORT).show();
            return;
        }

        detectorHelper.detect(capturedBitmap, faces -> {

            try {

                if (faces == null || faces.size() == 0) {
                    Log.d("FACE", "No face detected");
                    Toast.makeText(this, "No face detected", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (faces.size() > 1) {
                    Toast.makeText(this, "Multiple faces detected! Please take a photo alone.", Toast.LENGTH_LONG).show();
                    return;
                }
                Rect rect = faces.get(0).getBoundingBox();
                Log.d("FACE", "Bounds: " + rect.toString());

                int left = Math.max(0, rect.left);
                int top = Math.max(0, rect.top);
                int width = Math.min(rect.width(), capturedBitmap.getWidth() - left);
                int height = Math.min(rect.height(), capturedBitmap.getHeight() - top);

                Bitmap cropped = Bitmap.createBitmap(capturedBitmap, left, top, width, height);

                Log.d("CROP", "W=" + cropped.getWidth() + " H=" + cropped.getHeight());

                Bitmap face = Bitmap.createScaledBitmap(cropped, 112, 112, true);
                //imageView.setImageBitmap(face);

                float[][] embedding = recognizer.getEmbedding(face);
                Log.d("EMBEDDING", "Generated");

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Enter Name");

                EditText input = new EditText(this);
                builder.setView(input);

                builder.setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    db.insertUser(name, Utils.embeddingToString(embedding[0]));

                    Log.d("DB", "Saved " + name);
                    Toast.makeText(this, "Face Registered", Toast.LENGTH_SHORT).show();

                });
                builder.show();

            } catch (Exception e) {
                Log.e("REGISTER", "Registration error", e);
            }

        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        try {
            if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
                Bitmap rawBitmap = uriToBitmap(image_uri);
                if (rawBitmap != null) {
                    rawBitmap = rotateBitmap(rawBitmap);
                    // NEW: Scale PROPORTIONALLY instead of forcing 800x600
                    capturedBitmap = scaleBitmapProportionally(rawBitmap, 800);
                    imageView.setImageBitmap(capturedBitmap);
                    Log.d("BITMAP", "Width=" + capturedBitmap.getWidth() + " Height=" + capturedBitmap.getHeight());
                    registerFace();
                }
            }

        } catch (Exception e) {
            Log.e("CAMERA", "Capture failed", e);
        }
    }
    @SuppressLint("Range")
    public Bitmap rotateBitmap(Bitmap input) {

        String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};

        Cursor cur = getContentResolver().query(image_uri, orientationColumn, null, null, null);
        int orientation = 0;

        if (cur != null && cur.moveToFirst()) {
            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
            cur.close();
        }
        if (orientation == 0) return input;
        Log.d("ROTATE", "orientation=" + orientation);
        Matrix matrix = new Matrix();

        if (orientation == 90 || orientation == 180 || orientation == 270) {
            matrix.postRotate(orientation);
        }
        return Bitmap.createBitmap(input, 0, 0, input.getWidth(), input.getHeight(), matrix, true);
    }

    private Bitmap uriToBitmap(Uri selectedFileUri) {
        try {
            ParcelFileDescriptor parcelFileDescriptor =
                    getContentResolver().openFileDescriptor(selectedFileUri, "r");
            FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
            parcelFileDescriptor.close();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return  null;
    }

    private Bitmap scaleBitmapProportionally(Bitmap bitmap, int maxDimension) {
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();

        float aspectRatio = (float) originalWidth / (float) originalHeight;

        int newWidth;
        int newHeight;

        if (originalWidth > originalHeight) {
            newWidth = maxDimension;
            newHeight = Math.round(maxDimension / aspectRatio);
        } else {
            newHeight = maxDimension;
            newWidth = Math.round(maxDimension * aspectRatio);
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }
}