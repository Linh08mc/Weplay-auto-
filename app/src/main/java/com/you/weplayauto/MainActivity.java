package com.you.weplayauto;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // ❗️ SỬA LỖI 1 & 2: Thống nhất tên
    private static final String PREFS_NAME = "WePlayAutoPrefs"; // Giống LoginActivity
    private static final String SAVED_KEY = "saved_key";
    private static final String EXPIRES_AT = "expires_at"; // Giống LoginActivity

    private TextView keyNameText;
    private TextView timeRemainingText;
    private Switch serviceSwitch;
    private Button settingsButton;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        keyNameText = findViewById(R.id.keyNameText);
        timeRemainingText = findViewById(R.id.timeRemainingText);
        serviceSwitch = findViewById(R.id.serviceSwitch);
        settingsButton = findViewById(R.id.settingsButton);

        showKeyInfo();

        serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                try {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Toast.makeText(this, "Bật 'WePlay Auto' trong danh sách trợ năng", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Không thể mở cài đặt Trợ năng!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        settingsButton.setOnClickListener(v -> {
            if (!isKeyValid()) {
                Toast.makeText(this, "Key đã hết hạn. Không thể bật điều khiển.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (Settings.canDrawOverlays(this)) {
                startOverlayService();
            } else {
                requestOverlayPermission();
            }
        });
    }

    private void showKeyInfo() {
        // Code ở đây giờ sẽ đọc đúng file "WePlayAutoPrefs"
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String key = prefs.getString(SAVED_KEY, "Chưa có key");
        
        // Code ở đây giờ sẽ đọc đúng key "expires_at" và nhận giá trị Long
        long expiryTime = prefs.getLong(EXPIRES_AT, 0);

        keyNameText.setText("Key: " + key);

        if (expiryTime == 0) {
            timeRemainingText.setText("Thời gian còn lại: Vĩnh viễn");
            return;
        }

        long remaining = expiryTime - System.currentTimeMillis();
        if (remaining <= 0) {
            handleKeyExpired();
            return;
        }

        startCountdown(remaining);
    }

    private void startCountdown(long remainingMillis) {
        if (countDownTimer != null) countDownTimer.cancel();

        countDownTimer = new CountDownTimer(remainingMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long hours = millisUntilFinished / (1000 * 60 * 60);
                long minutes = (millisUntilFinished / (1000 * 60)) % 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                String timeLeft = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
                timeRemainingText.setText("Thời gian còn lại: " + timeLeft);
            }

            @Override
            public void onFinish() {
                handleKeyExpired();
            }
        }.start();
    }

    private void handleKeyExpired() {
        Toast.makeText(this, "Key đã hết hạn!", Toast.LENGTH_LONG).show();
        stopService(new Intent(this, FloatingButtonService.class));

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.remove(SAVED_KEY);
        editor.remove(EXPIRES_AT);
        // ❗️ BỔ SUNG: Xóa luôn cờ "đã xác thực"
        editor.remove("key_validated"); 
        editor.apply();

        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private boolean isKeyValid() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        // Đọc đúng key "expires_at"
        long expiryTime = prefs.getLong(EXPIRES_AT, 0);
        return expiryTime == 0 || System.currentTimeMillis() < expiryTime;
    }

    private void requestOverlayPermission() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Cấp quyền 'Vẽ trên ứng dụng khác' để hiển thị nút", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Không thể mở cài đặt quyền vẽ nổi!", Toast.LENGTH_SHORT).show();
        }
    }

    private void startOverlayService() {
        try {
            Intent serviceIntent = new Intent(this, FloatingButtonService.class);
            startService(serviceIntent);
            Toast.makeText(this, "Nút điều khiển đã bật!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Không thể khởi động nút nổi!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
