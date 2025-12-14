package com.you.weplayauto;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    // ❗️ SỬA LỖI 1 & 2: Thống nhất tên
    private static final String PREFS_NAME = "WePlayAutoPrefs"; // Giống MainActivity
    private static final String KEY_VALIDATED = "key_validated";
    private static final String SAVED_KEY = "saved_key";
    private static final String EXPIRES_AT = "expires_at"; // Giống MainActivity

    // 🔗 Thay bằng IP hoặc domain của server bạn
    private static final String API_URL = "http://46.247.108.191:30105/validate";

    private EditText edtKey;
    private Button btnValidate;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvInfo;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // 🔹 Nếu key đã hợp lệ và chưa hết hạn => vào MainActivity luôn
        if (isKeyValidated()) {
            if (isKeyExpired()) {
                clearKeyData();
                Toast.makeText(this, "⏰ Key đã hết hạn. Vui lòng nhập key mới!", Toast.LENGTH_LONG).show();
            } else {
                goToMainActivity();
                return;
            }
        }

        initViews();
        setupListeners();
    }

    private void initViews() {
        edtKey = findViewById(R.id.edtKey);
        btnValidate = findViewById(R.id.btnValidate);
        progressBar = findViewById(R.id.progressBar);
        tvStatus = findViewById(R.id.tvStatus);
        tvInfo = findViewById(R.id.tvInfo);
        progressBar.setVisibility(View.GONE);
    }

    private void setupListeners() {
        btnValidate.setOnClickListener(v -> validateKeyOnline());
    }

    private void validateKeyOnline() {
        final String key = edtKey.getText().toString().trim();

        if (TextUtils.isEmpty(key)) {
            Toast.makeText(this, "⚠️ Vui lòng nhập key", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(this, "📴 Không có kết nối Internet!", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnValidate.setEnabled(false);
        tvStatus.setText("🔍 Đang xác thực key...");
        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));

        new Thread(() -> {
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                String deviceInfo = Build.MANUFACTURER + " " + Build.MODEL;
                String jsonPayload = String.format(
                        "{\"key\":\"%s\",\"device_info\":\"%s\"}",
                        key, deviceInfo
                );

                OutputStream os = conn.getOutputStream();
                os.write(jsonPayload.getBytes("UTF-8"));
                os.flush();
                os.close();

                int responseCode = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        responseCode >= 200 && responseCode < 300
                                ? conn.getInputStream()
                                : conn.getErrorStream()
                ));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) response.append(line);
                br.close();

                JSONObject json = new JSONObject(response.toString());
                final boolean isValid = json.optBoolean("valid", false);
                final String message = json.optString("message", "Lỗi không xác định");
                final String expiresAt = json.optString("expires_at", null);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnValidate.setEnabled(true);

                    if (isValid) {
                        tvStatus.setText("✅ Key hợp lệ!");
                        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));

                        saveKeyValidation(key, expiresAt); // Truyền chuỗi ngày giờ

                        Toast.makeText(LoginActivity.this,
                                "✅ Xác thực thành công!", Toast.LENGTH_SHORT).show();

                        new Handler().postDelayed(this::goToMainActivity, 800);
                    } else {
                        tvStatus.setText("❌ " + message);
                        tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        Toast.makeText(LoginActivity.this,
                                "❌ " + message, Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnValidate.setEnabled(true);
                    tvStatus.setText("❌ Lỗi kết nối!");
                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    Toast.makeText(LoginActivity.this,
                            "❌ Không thể kết nối đến server: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    // ❗️ SỬA LỖI 3: Chuyển String sang Long trước khi lưu
    private void saveKeyValidation(String key, String expiresAtStr) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_VALIDATED, true);
        editor.putString(SAVED_KEY, key);

        // Chuyển đổi String (ví dụ: "2025-10-25T13:08:00Z") sang Long (mili giây)
        long expiresAtMillis = 0; // Mặc định là 0 (vĩnh viễn)

        if (expiresAtStr != null && !expiresAtStr.equals("null") && !expiresAtStr.isEmpty()) {
            try {
                // Thử parse theo chuẩn ISO 8601 (nên dùng)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    expiresAtMillis = java.time.Instant.parse(expiresAtStr.replace(" ", "T")).toEpochMilli();
                } else {
                    // Cách cũ cho Android < 8
                    String clean = expiresAtStr.replace("Z", "+00:00");
                    // Thử bắt các định dạng phổ biến
                    if (!clean.contains("T")) { // Dạng "2025-10-25 13:08:00"
                        clean = clean.replace(" ", "T");
                    }
                    if (!clean.contains("+")) { // Thêm Z nếu không có múi giờ
                         clean = clean + "Z";
                    }
                    // Format này phải khớp chính xác với output của server
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.UK);
                    Date date = sdf.parse(clean);
                    expiresAtMillis = date.getTime();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Lỗi định dạng ngày giờ từ server!", Toast.LENGTH_SHORT).show();
                expiresAtMillis = 1; // Coi như hết hạn nếu lỗi parse
            }
        }
        
        // Lưu là LONG
        editor.putLong(EXPIRES_AT, expiresAtMillis);
        editor.apply();
    }

    private boolean isKeyValidated() {
        return prefs.getBoolean(KEY_VALIDATED, false);
    }

    // ❗️ SỬA LỖI 3: Đọc trực tiếp Long
    private boolean isKeyExpired() {
        long expiresAtMillis = prefs.getLong(EXPIRES_AT, 0);

        if (expiresAtMillis == 0) {
            return false; // Key vĩnh viễn
        }
        
        // So sánh thời gian hiện tại với thời gian hết hạn
        return System.currentTimeMillis() >= expiresAtMillis;
    }

    private void clearKeyData() {
        prefs.edit()
                .remove(KEY_VALIDATED)
                .remove(SAVED_KEY)
                .remove(EXPIRES_AT)
                .apply();
    }

    private void goToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        Toast.makeText(this, "⚠️ Vui lòng nhập key để tiếp tục", Toast.LENGTH_SHORT).show();
    }
}
