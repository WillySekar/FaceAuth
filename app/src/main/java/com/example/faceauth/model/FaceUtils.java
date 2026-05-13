package com.example.faceauth.model;

import android.graphics.Bitmap;
import android.util.Log;

public class FaceUtils {
    public static String createEmbedding(Bitmap bitmap){

        Bitmap small= Bitmap.createScaledBitmap(bitmap,64,64,false);

        StringBuilder sb=new StringBuilder();

        for(int x=0;x<64;x++){
            for(int y=0;y<64;y++){

                int pixel=small.getPixel(x,y);

                int r=(pixel>>16)&0xff;
                int g=(pixel>>8)&0xff;
                int b=(pixel)&0xff;

                int gray=(r+g+b)/3;

                sb.append(gray).append(",");
            }
        }

        return sb.toString();
    }

    public static boolean compare(String e1,String e2){

        try{

            String[] a=e1.split(",");
            String[] b=e2.split(",");

            long diff=0;

            for(int i=0;i<a.length;i++){

                diff += Math.abs(
                        Integer.parseInt(a[i])-
                                Integer.parseInt(b[i]));
            }

            Log.d("FACE_DIFF","diff="+diff);

            return diff<18000000;

        }catch(Exception e){

            Log.e("COMPARE",e.toString());
            return false;
        }
    }
}
