package com.example.faceauth.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.widget.Toast;

public class AttendanceDB extends SQLiteOpenHelper {

    public AttendanceDB(Context context) {
        super(context,"attendance.db",null,1);
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE attendance(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "time DATETIME DEFAULT CURRENT_TIMESTAMP)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) { }

    public static void markAttendance(Context context,String name){

        AttendanceDB dbHelper = new AttendanceDB(context);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("name",name);

        db.insert("attendance",null,values);

        Toast.makeText(context,
                "Attendance Marked for "+name,
                Toast.LENGTH_LONG).show();
    }
}
