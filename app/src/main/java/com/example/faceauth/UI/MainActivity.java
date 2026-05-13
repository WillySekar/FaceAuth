package com.example.faceauth.UI;


import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.faceauth.R;
import com.example.faceauth.UI.AttendanceActivity;
import com.example.faceauth.UI.RegisterActvity;

public class MainActivity extends AppCompatActivity {
    private AppCompatButton btnRegister, btnRecogination;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
         try {
             btnRegister = findViewById(R.id.btnRegister);
             btnRecogination = findViewById(R.id.btnAttendance);
             btnRegister.setOnClickListener(v ->
                     startActivity(new Intent(this, RegisterActvity.class)));

             btnRecogination.setOnClickListener(v ->
                     startActivity(new Intent(this, AttendanceActivity.class)));
         }catch (Exception Ignored){}
    }

}