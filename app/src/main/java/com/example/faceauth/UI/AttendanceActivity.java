package com.example.faceauth.UI;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

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
import android.widget.ImageView;
import android.widget.Toast;

import com.example.faceauth.R;
import com.example.faceauth.model.DBHelper;
import com.example.faceauth.utils.FaceDetectorHelper;
import com.example.faceauth.utils.FaceRecognizer;
import com.example.faceauth.utils.Utils;

import java.io.FileDescriptor;
import java.io.IOException;

public class AttendanceActivity extends AppCompatActivity {

    private AppCompatButton btnRecognize, btnFingerAttendance ,btnLiveAttendance;
    private ImageView imageView;
    private static final int CAMERA_REQUEST = 200;

    private Bitmap capturedBitmap;

    FaceDetectorHelper detectorHelper;
    FaceRecognizer recognizer;
    DBHelper db;
    private Uri image_uri;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance);

        imageView = findViewById(R.id.imageView);
        btnRecognize = findViewById(R.id.btnFaceAttendance);
        btnFingerAttendance = findViewById(R.id.btnFingerAttendance);
        btnLiveAttendance = findViewById(R.id.btnLiveAttendance);

        detectorHelper = new FaceDetectorHelper();
        db = new DBHelper(this);
        try {
            recognizer = new FaceRecognizer(getAssets());
        } catch (IOException e) {
            e.printStackTrace();
        }

        btnRecognize.setOnClickListener(view -> {
            openCamera();
        });
        btnLiveAttendance.setOnClickListener(view -> {
            startActivity(new Intent(AttendanceActivity.this, activity_live_attendances.class));
        });

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
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
                capturedBitmap = uriToBitmap(image_uri);
                Log.d("CAMERA", "Full bitmap loaded");
                capturedBitmap = rotateBitmap(capturedBitmap);
                Log.d("CAMERA", "Bitmap rotated");
                capturedBitmap = Bitmap.createScaledBitmap(capturedBitmap, 800, 600, true);

                Log.d("BITMAP", "Width=" + capturedBitmap.getWidth() +
                        " Height=" + capturedBitmap.getHeight());
                recognizeFace();
            }

        } catch (Exception e) {
            Log.e("CAMERA", "Capture failed", e);
        }
    }

    private void recognizeFace() {

        detectorHelper.detect(capturedBitmap, faces -> {

            try {

                if (faces == null || faces.size() == 0) {
                    Log.d("FACE", "No face detected");
                    Toast.makeText(this, "No face detected", Toast.LENGTH_SHORT).show();
                    return;
                }

                Rect rect = faces.get(0).getBoundingBox();

                int left = Math.max(0, rect.left);
                int top = Math.max(0, rect.top);
                int width = Math.min(rect.width(), capturedBitmap.getWidth() - left);
                int height = Math.min(rect.height(), capturedBitmap.getHeight() - top);

                Bitmap cropped = Bitmap.createBitmap(capturedBitmap, left, top, width, height);
                Bitmap face = Bitmap.createScaledBitmap(cropped, 112, 112, true);
                imageView.setImageBitmap(face);

                Log.d("CROP", "Face cropped");

                float[][] currentEmbedding = recognizer.getEmbedding(face);
                float[] current = Utils.normalize(currentEmbedding[0]);

                Cursor cursor = db.getAllUsers();
                Log.d("cursor", cursor.getExtras().toString());

                String bestName = "Unknown";
                float bestDistance = Float.MAX_VALUE;
                while (cursor.moveToNext()) {

                    String name = cursor.getString(1);
                    String emb = cursor.getString(2);

                    Log.d("DBUSER", "name=" + name );

                    float[] storedEmbedding = Utils.stringToEmbedding(emb);
                    float[] stored = Utils.normalize(storedEmbedding);

                    float distance = Utils.distance(current, stored);

                    Log.d("MATCHD", name + " distance=" + distance);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestName = name;
                    }

                }
                Log.d("BEST", bestName + " best distance=" + bestDistance);

                if (bestDistance < 1.2f) {
                    Toast.makeText(this, "Recognized: " + bestName, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Unknown Face", Toast.LENGTH_LONG).show();
                }

                cursor.close();

            } catch (Exception e) {
                Log.e("RECOGNITION", "Recognition error", e);
            }
        });
    }

    @SuppressLint("Range")
    public Bitmap rotateBitmap(Bitmap input) {
         try {
             String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
             Cursor cur = getContentResolver().query(image_uri, orientationColumn, null, null, null);

             int orientation = 0;
             if (cur != null && cur.moveToFirst()) {
                 orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
                 cur.close();
             }
             Log.d("ROTATE", "orientation=" + orientation);
             Matrix matrix = new Matrix();
             if (orientation == 90 || orientation == 180 || orientation == 270) {
                 matrix.postRotate(orientation);
             }
             return Bitmap.createBitmap(input, 0, 0, input.getWidth(), input.getHeight(), matrix, true);
         }catch (Exception Ignored){}
        return  null;
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
}