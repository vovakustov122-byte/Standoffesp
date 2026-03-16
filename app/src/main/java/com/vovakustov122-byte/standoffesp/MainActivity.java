package com.vovakustov122-byte.standoffesp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    
    private static final int REQUEST_MEDIA_PROJECTION = 100;
    private static final int REQUEST_OVERLAY_PERMISSION = 101;
    
    private MediaProjectionManager projectionManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        projectionManager = (MediaProjectionManager) 
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        Button startBtn = findViewById(R.id.startBtn);
        startBtn.setOnClickListener(v -> checkPermissions());
    }
    
    private void checkPermissions() {
        // Проверяем разрешение на оверлей
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
                return;
            }
        }
        
        // Запрашиваем захват экрана
        startMediaProjection();
    }
    
    private void startMediaProjection() {
        Intent intent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK) {
                // Запускаем сервис с ESP
                Intent serviceIntent = new Intent(this, ESPOverlayService.class);
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                
                Toast.makeText(this, "ESP запущен!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Нужен доступ к экрану", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
