package com.example.faceauth.utils;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class FaceRecognizer {
    Interpreter interpreter;

    public FaceRecognizer(AssetManager assetManager) throws IOException {
        interpreter = new Interpreter(loadModel(assetManager));
    }

    private MappedByteBuffer loadModel(AssetManager assetManager) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd("mobile_face_net.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();

        return fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.getStartOffset(),
                fileDescriptor.getDeclaredLength()
        );
    }

    public float[][] getEmbedding(Bitmap bitmap) {

        Bitmap resized = Bitmap.createScaledBitmap(
                bitmap,
                112,
                112,
                true
        );

        ByteBuffer byteBuffer =
                ByteBuffer.allocateDirect(
                        1 * 112 * 112 * 3 * 4
                );

        byteBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[112 * 112];

        resized.getPixels(pixels, 0, 112, 0, 0, 112, 112);

        for (int pixel : pixels) {

            float r = (((pixel >> 16) & 0xFF) - 127.5f) / 127.5f;

            float g = (((pixel >> 8) & 0xFF) - 127.5f) / 127.5f;

            float b = ((pixel & 0xFF) - 127.5f) / 127.5f;

            byteBuffer.putFloat(r);
            byteBuffer.putFloat(g);
            byteBuffer.putFloat(b);
        }

        float[][] embedding = new float[1][192];

        interpreter.run(byteBuffer, embedding);

        return embedding;
    }
}
