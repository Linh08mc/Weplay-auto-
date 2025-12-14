package com.you.weplayauto;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {

    private static final String CHANNEL_ID = "ScreenCaptureChannel";
    private static final String PREFS_NAME = "WePlayAutoPrefs";
    private static final String WEBHOOK_URL_KEY = "webhook_url";

    private boolean webhookSent = false;
    private MediaProjection mediaProjection;
    private MediaProjectionManager projectionManager;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDensity;
    private final Handler handler = new Handler();
    private Runnable screenAnalysisRunnable;

    private long unreadyStartTime = 0;
    private static final long DELAY_BEFORE_WEBHOOK = 30000;

    private String webhookUrl = "";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WePlay Auto - Team Cam")
                .setContentText("Đang giám sát team CAM...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();

        startForeground(1, notification);

        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        Log.d("ScreenCaptureService", "═══════════════════════════════════════");
        Log.d("ScreenCaptureService", "🚀 SERVICE KHỞI ĐỘNG - CHẾ ĐỘ TEAM CAM");
        Log.d("ScreenCaptureService", "📱 Màn hình: " + screenWidth + "x" + screenHeight + " @ " + screenDensity + "dpi");

        PackageManager pm = getPackageManager();
        int hasInternet = pm.checkPermission(android.Manifest.permission.INTERNET, getPackageName());
        if (hasInternet == PackageManager.PERMISSION_GRANTED) {
            Log.d("ScreenCaptureService", "✅ Có quyền INTERNET");
        } else {
            Log.e("ScreenCaptureService", "❌ KHÔNG CÓ QUYỀN INTERNET!");
        }

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        if (isConnected) {
            Log.d("ScreenCaptureService", "✅ Có kết nối mạng");
        } else {
            Log.e("ScreenCaptureService", "❌ KHÔNG CÓ KẾT NỐI MẠNG!");
        }

        Log.d("ScreenCaptureService", "═══════════════════════════════════════");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("ScreenCaptureService", "onStartCommand called");

        if (intent == null) {
            Log.e("ScreenCaptureService", "Intent is null, stopping service.");
            stopSelf();
            return START_NOT_STICKY;
        }

        final int resultCode = intent.getIntExtra("resultCode", -1);
        final Intent data = intent.getParcelableExtra("data");
        final String webhook = intent.getStringExtra("webhook_url");

        webhookUrl = webhook;
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            webhookUrl = prefs.getString(WEBHOOK_URL_KEY, 
                "https://discord.com/api/webhooks/1424209291635720296/-2eAc5x1vOYLHidaWdW_q6Ov7Ots25wdbRqnmqURCjoHRPwXon6ee_1P8m7obox-FuRF");
        }

        Log.d("ScreenCaptureService", "🔗 Webhook URL: " + webhookUrl);

        // ✅ FIX: Kiểm tra resultCode đúng cách
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e("ScreenCaptureService", "❌ Không có quyền ghi màn hình! ResultCode: " + resultCode);
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            Log.d("ScreenCaptureService", "⏳ Bắt đầu khởi tạo MediaProjection...");
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);

            if (mediaProjection == null) {
                Log.e("ScreenCaptureService", "❌ mediaProjection is null! Dừng service.");
                stopSelf();
                return START_NOT_STICKY;
            }

            Log.d("ScreenCaptureService", "⏳ Tạo ImageReader...");
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);

            Log.d("ScreenCaptureService", "⏳ Tạo VirtualDisplay...");
            mediaProjection.createVirtualDisplay(
                    "ScreenCaptureDisplay",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    0,
                    imageReader.getSurface(),
                    null,
                    null
            );

            Log.d("ScreenCaptureService", "✅ MediaProjection started successfully");

            webhookSent = false;
            unreadyStartTime = 0;

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startPeriodicAnalysis();
                }
            }, 2000);

        } catch (Exception e) {
            Log.e("ScreenCaptureService", "❌ Lỗi nghiêm trọng khi khởi tạo: " + e.getMessage());
            e.printStackTrace();
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WePlay Auto - Team Cam",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Giám sát trạng thái ready của team cam");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startPeriodicAnalysis() {
        Log.d("PeriodicScan", "🔄 Bắt đầu vòng lặp quét TEAM CAM (mỗi 3s)");
        screenAnalysisRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    scanOrangeTeamOnly();
                    handler.postDelayed(this, 3000);
                } catch (Exception e) {
                    Log.e("PeriodicScan", "Lỗi trong vòng lặp quét: " + e.getMessage());
                    // Vẫn tiếp tục chạy dù có lỗi
                    handler.postDelayed(this, 3000);
                }
            }
        };
        handler.post(screenAnalysisRunnable);
    }

    private void scanOrangeTeamOnly() {
        Bitmap screenshot = captureScreen();
        if (screenshot == null) {
            Log.e("OrangeScan", "❌ Không chụp được màn hình");
            return;
        }

        try {
            Log.d("OrangeScan", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Log.d("OrangeScan", "🟠 BẮT ĐẦU QUÉT CHỈ TEAM CAM");

            int startX = screenWidth / 2;
            int endX = screenWidth;
            int startY = (int) (screenHeight * 0.15);
            int endY = (int) (screenHeight * 0.85);
            int scanStep = 18;

            int orangePlayersReady = 0;
            int orangePlayersTotal = 0;
            int orangePlusSigns = 0;
            boolean foundOrangeBackground = false;

            for (int y = startY; y < endY; y += scanStep) {
                for (int x = startX; x < endX; x += scanStep) {
                    if (isOrangeBackground(screenshot, x, y)) {
                        foundOrangeBackground = true;

                        if (isPlusSign(screenshot, x, y)) {
                            orangePlusSigns++;
                            Log.d("OrangeScan", "➕ Vị trí trống tại (" + x + "," + y + ")");
                        } else {
                            orangePlayersTotal++;
                            if (checkForGreenCheckmark(screenshot, x, y)) {
                                orangePlayersReady++;
                                Log.d("OrangeScan", "✅ Người cam #" + orangePlayersTotal + " ĐÃ READY tại (" + x + "," + y + ")");
                            } else {
                                Log.d("OrangeScan", "❌ Người cam #" + orangePlayersTotal + " CHƯA READY tại (" + x + "," + y + ")");
                            }
                            x += 60;
                        }
                    }
                }
            }

            Log.d("OrangeScan", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Log.d("OrangeScan", "📊 KẾT QUẢ: Tổng: " + orangePlayersTotal + ", Ready: " + orangePlayersReady + ", Trống: " + orangePlusSigns);
            Log.d("OrangeScan", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            if (foundOrangeBackground) {
                if (orangePlayersTotal > 0) {
                    boolean hasUnreadyPlayers = (orangePlayersReady < orangePlayersTotal);
                    long currentTime = System.currentTimeMillis();

                    if (hasUnreadyPlayers) {
                        if (unreadyStartTime == 0) {
                            unreadyStartTime = currentTime;
                            Log.w("TimeCheck", "⏰ BẮT ĐẦU ĐẾM THỜI GIAN: " + orangePlayersReady + "/" + orangePlayersTotal + " ready");
                        }

                        long timeInUnreadyState = currentTime - unreadyStartTime;
                        long secondsElapsed = timeInUnreadyState / 1000;
                        long secondsRemaining = Math.max(0, (DELAY_BEFORE_WEBHOOK - timeInUnreadyState) / 1000);
                        
                        Log.i("TimeCheck", "⏳ Đã chờ: " + secondsElapsed + "s, còn: " + secondsRemaining + "s");

                        if (timeInUnreadyState >= DELAY_BEFORE_WEBHOOK && !webhookSent) {
                            Log.e("Webhook", "🚨🚨🚨 HẾT THỜI GIAN CHỜ! GỬI CẢNH BÁO! 🚨🚨🚨");
                            
                            int unreadyCount = orangePlayersTotal - orangePlayersReady;
                            
                            StringBuilder sb = new StringBuilder();
                            sb.append("🚨 CẢNH BÁO: TEAM CAM CHƯA SẴN SÀNG!\n");
                            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
                            sb.append("🟠 TEAM CAM:\n");
                            sb.append("   👥 Tổng: ").append(orangePlayersTotal).append(" người\n");
                            sb.append("   ✅ Sẵn sàng: ").append(orangePlayersReady).append(" người\n");
                            sb.append("   ❌ Chưa sẵn sàng: ").append(unreadyCount).append(" người\n");
                            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n");
                            sb.append("⏰ Đã chờ ").append(DELAY_BEFORE_WEBHOOK / 1000).append(" giây\n");
                            sb.append("🎮 VÀO GAME KIỂM TRA NGAY!");
                            
                            sendWebhook(sb.toString());
                            webhookSent = true;
                        }
                    } else {
                        if (unreadyStartTime != 0) {
                            long waitedTime = (currentTime - unreadyStartTime) / 1000;
                            Log.d("TimeCheck", "✅ TẤT CẢ ĐÃ READY! Đã chờ: " + waitedTime + "s - Reset bộ đếm");
                            unreadyStartTime = 0;
                        }
                        if (webhookSent) {
                            Log.d("Webhook", "🔄 Reset trạng thái webhook");
                            webhookSent = false;
                        }
                    }
                } else {
                    Log.d("OrangeScan", "⚠️ Team cam trống");
                    resetState();
                }
            } else {
                Log.d("OrangeScan", "⚠️ Không ở màn hình chờ team cam");
                resetState();
            }
        } catch (Exception e) {
            Log.e("OrangeScan", "❌ Lỗi trong quá trình quét: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (screenshot != null) {
                screenshot.recycle();
            }
        }
    }

    private void resetState() {
        if (unreadyStartTime != 0 || webhookSent) {
            Log.d("StateReset", "🔄 Resetting state...");
            unreadyStartTime = 0;
            webhookSent = false;
        }
    }

    // ✅ FIX: Sửa captureScreen() để tránh crash khi image null
    private Bitmap captureScreen() {
        if (imageReader == null) {
            Log.e("CaptureScreen", "ImageReader is null");
            return null;
        }
        
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                Log.d("CaptureScreen", "Image is null - chưa có frame mới");
                return null;
            }

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            if (rowPadding > 0) {
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
                bitmap.recycle();
                return croppedBitmap;
            }
            return bitmap;
            
        } catch (Exception e) {
            Log.e("CaptureScreen", "Lỗi khi chụp màn hình: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private boolean isOrangeBackground(Bitmap bmp, int x, int y) {
        if (x < 0 || x >= bmp.getWidth() || y < 0 || y >= bmp.getHeight()) return false;
        int color = bmp.getPixel(x, y);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return (red > 200 && green > 100 && green < 200 && blue < 120 && red > (green + 40));
    }

    private boolean checkForGreenCheckmark(Bitmap bmp, int centerX, int centerY) {
        int scanRadius = 18;
        int greenPixelCount = 0;
        int totalPixelsChecked = 0;

        for (int dy = -scanRadius; dy <= scanRadius; dy++) {
            for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                int checkX = centerX + dx;
                int checkY = centerY + dy;

                if (checkX >= 0 && checkX < bmp.getWidth() && checkY >= 0 && checkY < bmp.getHeight()) {
                    totalPixelsChecked++;
                    int color = bmp.getPixel(checkX, checkY);
                    int red = Color.red(color);
                    int green = Color.green(color);
                    int blue = Color.blue(color);

                    boolean isGreenish = (green > 150 && green > red * 1.5 && green > blue * 1.4 && red < 120);
                    if (isGreenish) {
                        greenPixelCount++;
                    }
                }
            }
        }

        double greenPercentage = (totalPixelsChecked > 0) ? (greenPixelCount * 100.0) / totalPixelsChecked : 0;
        return greenPercentage > 8.0;
    }

    private boolean isPlusSign(Bitmap bmp, int centerX, int centerY) {
        int plusRadius = 15;
        int whiteCount = 0;
        int totalChecked = 0;

        for (int offset = -plusRadius; offset <= plusRadius; offset++) {
            if (isWhiteOrLightGray(bmp, centerX + offset, centerY)) whiteCount++;
            totalChecked++;
            if (offset != 0) {
                if (isWhiteOrLightGray(bmp, centerX, centerY + offset)) whiteCount++;
                totalChecked++;
            }
        }
        return (totalChecked > 0) && (whiteCount > (totalChecked * 0.5));
    }

    private boolean isWhiteOrLightGray(Bitmap bmp, int x, int y) {
        if (x < 0 || x >= bmp.getWidth() || y < 0 || y >= bmp.getHeight()) return false;
        int color = bmp.getPixel(x, y);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return (red > 190 && green > 190 && blue > 190 && Math.abs(red - green) < 30 && Math.abs(green - blue) < 30);
    }

    // ✅ FIX: Escape message đúng cách
    private void sendWebhook(final String message) {
        if (webhookUrl == null || !webhookUrl.startsWith("http")) {
            Log.e("Webhook", "❌ URL Webhook không hợp lệ");
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    Log.d("Webhook", "🚀 Bắt đầu gửi webhook...");
                    
                    URL url = new URL(webhookUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("User-Agent", "WePlayAuto/1.0");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);

                    // ✅ FIX: Escape đúng cách cho JSON
                    String escapedMessage = message
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t");
                    
                    String jsonPayload = "{\"content\":\"" + escapedMessage + "\"}";

                    Log.d("Webhook", "📦 Payload length: " + jsonPayload.length());

                    OutputStream os = conn.getOutputStream();
                    os.write(jsonPayload.getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int responseCode = conn.getResponseCode();
                    Log.d("Webhook", "📡 Response: " + responseCode + " " + conn.getResponseMessage());
                    
                    if (responseCode >= 200 && responseCode < 300) {
                        Log.d("Webhook", "✅✅✅ WEBHOOK GỬI THÀNH CÔNG!");
                    } else {
                        Log.e("Webhook", "❌ Webhook THẤT BẠI với code: " + responseCode);
                    }
                } catch (Exception e) {
                    Log.e("Webhook", "❌❌❌ EXCEPTION: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("ScreenCaptureService", "🛑 Đang dừng service...");
        if (handler != null && screenAnalysisRunnable != null) {
            handler.removeCallbacks(screenAnalysisRunnable);
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        Log.d("ScreenCaptureService", "✅ Service đã dừng");
    }
}