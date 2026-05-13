package com.example.faceauth.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBHelper extends SQLiteOpenHelper {

    public DBHelper(Context c){
        super(c,"attendance.db",null,1);
    }

    @Override
    public void onCreate(SQLiteDatabase db){

        db.execSQL("CREATE TABLE users(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "embedding TEXT)");

        db.execSQL("CREATE TABLE attendance(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db,int o,int n){

        db.execSQL("DROP TABLE IF EXISTS users");
        db.execSQL("DROP TABLE IF EXISTS attendance");
        onCreate(db);
    }

    public void insertUser(String name,String embedding){

        try{

            SQLiteDatabase db=getWritableDatabase();

            ContentValues cv=new ContentValues();
            cv.put("name",name);
            cv.put("embedding",embedding);
            long r=db.insert("users",null,cv);
            Log.d("DB_INSERT","result="+r);

        }catch(Exception e){
            Log.e("DB_INSERT",e.toString());
        }
    }

    public Cursor getAllUsers(){
        return getReadableDatabase().rawQuery("SELECT * FROM users", null);
    }

    public void markAttendance(String name){

        try{

            SQLiteDatabase db=getWritableDatabase();

            ContentValues cv=new ContentValues();
            cv.put("name",name);
            db.insert("attendance",null,cv);

        }catch(Exception e){
            Log.e("ATTENDANCE",e.toString());
        }
    }
}
