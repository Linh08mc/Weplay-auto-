package com.you.weplayauto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class KeyUtils {

    private static final String PREFS_NAME = "WePlayPrefs";
    private static final String SAVED_KEY = "saved_key";
    private static final String EXPIRES_AT = "expiryTime";

    // =============================
    // 🔑 Lưu key và thời hạn
    // =============================
    public static void saveKey(Context context, String key, long expiryTimeMillis) {
        try {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putString(SAVED_KEY, key);
            editor.putLong(EXPIRES_AT, expiryTimeMillis);
            editor.apply();
            Log.d("KeyUtils", "✅ Key đã lưu: " + key + ", hết hạn: " + expiryTimeMillis);
        } catch (Exception e) {
            Log.e("KeyUtils", "❌ Lỗi khi lưu key: " + e.getMessage());
        }
    }

    // =============================
    // 📤 Lấy key hiện tại
    // =============================
    public static String getSavedKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(SAVED_KEY, null);
    }

    // =============================
    // ⏰ Lấy thời gian hết hạn
    // =============================
    public static long getExpiryTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(EXPIRES_AT, 0);
    }

    // =============================
    // ✅ Kiểm tra key còn hạn hay không
    // =============================
    public static boolean isKeyValid(Context context) {
        long expiry = getExpiryTime(context);
        return expiry == 0 || System.currentTimeMillis() < expiry;
    }

    // =============================
    // 🧹 Xóa key (dùng khi hết hạn)
    // =============================
    public static void clearKey(Context context) {
        try {
            SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.remove(SAVED_KEY);
            editor.remove(EXPIRES_AT);
            editor.apply();
            Log.d("KeyUtils", "🧹 Key đã bị xóa khỏi bộ nhớ.");
        } catch (Exception e) {
            Log.e("KeyUtils", "❌ Lỗi khi xóa key: " + e.getMessage());
        }
    }
}