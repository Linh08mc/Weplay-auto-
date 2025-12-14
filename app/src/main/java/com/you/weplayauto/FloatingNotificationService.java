package com.you.weplayauto;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingNotificationService extends Service {
    private WindowManager windowManager;
    private View floatingView;
    private Handler handler;
    private TextView notificationText;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("message")) {
            String message = intent.getStringExtra("message");
            showFloatingNotification(message);
        }
        return START_NOT_STICKY;
    }

    private void showFloatingNotification(final String message) {
        handler.post(new Runnable() {
            public void run() {
                createFloatingView(message);
                
                // Tự động ẩn sau 3 giây
                handler.postDelayed(new Runnable() {
                    public void run() {
                        removeFloatingView();
                    }
                }, 3000);
            }
        });
    }

    private void createFloatingView(String message) {
        removeFloatingView(); // Xóa view cũ nếu có

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // Tạo layout đơn giản không cần XML
        notificationText = new TextView(this);
        notificationText.setText(message);
        notificationText.setBackgroundColor(0xFF4CAF50); // Màu xanh
        notificationText.setTextColor(0xFFFFFFFF); // Màu trắng
        notificationText.setPadding(20, 10, 20, 10);
        notificationText.setTextSize(14);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.x = 0;
        params.y = 100;

        floatingView = notificationText;
        windowManager.addView(floatingView, params);
    }

    private void removeFloatingView() {
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
            floatingView = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeFloatingView();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}