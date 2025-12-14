package com.you.weplayauto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

public class FloatingButtonService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    private boolean isScanning = false;
    private ImageView playPauseButton;

    private static final String CHANNEL_ID = "FloatingControlChannel";
    private static final String PREFS_NAME = "WePlayPrefs";
    private static final String EXPIRES_AT = "expiryTime";
    private static final long CHECK_INTERVAL = 2000; // Kiểm tra mỗi 2 giây

    private Handler handler = new Handler();
    private Runnable keyCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isKeyValid()) {
                handleKeyExpired();
                return;
            }
            handler.postDelayed(this, CHECK_INTERVAL);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isKeyValid()) {
            Toast.makeText(this, "❌ Key đã hết hạn. Không thể bật điều khiển.", Toast.LENGTH_SHORT).show();
            stopSelf();
            return START_NOT_STICKY;
        }

        showNotification();
        createFloatingWidget();

        // Bắt đầu kiểm tra key định kỳ
        handler.postDelayed(keyCheckRunnable, CHECK_INTERVAL);

        return START_STICKY;
    }

    private boolean isKeyValid() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long expiryTime = prefs.getLong(EXPIRES_AT, 0);
        long currentTime = System.currentTimeMillis();
        return expiryTime == 0 || currentTime < expiryTime;
    }

    private void handleKeyExpired() {
        Toast.makeText(this, "⏰ Key đã hết hạn! Dừng mọi hoạt động.", Toast.LENGTH_LONG).show();

        // Dừng service
        stopSelf();

        // Xóa key để bắt buộc nhập lại
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.remove("saved_key");
        editor.remove(EXPIRES_AT);
        editor.apply();

        // Mở lại LoginActivity
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void createFloatingWidget() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button_layout, null);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 200;

        windowManager.addView(floatingView, params);

        playPauseButton = floatingView.findViewById(R.id.button_play_pause);
        View closeButton = floatingView.findViewById(R.id.button_close);
        View moveButton = floatingView.findViewById(R.id.button_move);

        playPauseButton.setOnClickListener(v -> toggleScanning());
        closeButton.setOnClickListener(v -> stopSelf());
        setupDragHandle(moveButton);
    }

    private void toggleScanning() {
        isScanning = !isScanning;

        if (!isKeyValid()) {
            Toast.makeText(this, "⚠️ Key đã hết hạn. Dừng hoạt động.", Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        if (isScanning) {
            playPauseButton.setImageResource(R.drawable.ic_floating_pause);
        } else {
            playPauseButton.setImageResource(R.drawable.ic_floating_play);
        }

        Intent intent = new Intent("com.you.weplayauto.TOGGLE_SERVICE");
        intent.putExtra("running", isScanning);
        sendBroadcast(intent);
    }

    private void setupDragHandle(View dragHandle) {
        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Điều khiển WePlay",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WePlay Auto")
                .setContentText("Bảng điều khiển đang chạy.")
                .setSmallIcon(R.drawable.ic_floating_play)
                .setOngoing(true)
                .build();

        startForeground(2, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(keyCheckRunnable);

        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }

        if (isScanning) {
            toggleScanning();
        }
    }
}