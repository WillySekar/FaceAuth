package com.example.faceauth.utils;

public class Utils {
    public static String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder();

        for (float f : embedding) {
            sb.append(f).append(",");
        }

        return sb.toString();
    }

    public static float[] stringToEmbedding(String s) {
        String[] arr = s.split(",");
        float[] result = new float[arr.length];

        for (int i = 0; i < arr.length; i++) {
            result[i] = Float.parseFloat(arr[i]);
        }

        return result;
    }

    public static float distance(float[] a, float[] b) {
        float sum = 0;

        for (int i = 0; i < a.length; i++) {
            float diff = a[i] - b[i];
            sum += diff * diff;
        }

        return (float)Math.sqrt(sum);
    }

    public static float[] normalize(float[] emb) {

        float sum = 0f;

        for (float v : emb) {
            sum += v * v;
        }

        float norm = (float) Math.sqrt(sum);

        float[] out = new float[emb.length];

        for (int i = 0; i < emb.length; i++) {
            out[i] = emb[i] / norm;
        }

        return out;
    }

}
