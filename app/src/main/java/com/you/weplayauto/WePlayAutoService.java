package com.you.weplayauto;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.util.List;

public class WePlayAutoService extends AccessibilityService {

    private static final String TAG = "WePlayAuto";
    private static final String WEPLAY_PACKAGE = "com.wejoy.weplay";
    private static final long COMMAND_COOLDOWN = 30000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable continuousChecker;
    private long lastCommandTime = 0;
    private boolean isAttemptingToStartGame = false;
    private boolean isScanning = false;

    private final BroadcastReceiver toggleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            isScanning = intent.getBooleanExtra("running", false);
            showToast(isScanning ? "✓ Bắt đầu quét" : "✗ Dừng quét");
        }
    };

    private void showToast(final String message) {
        handler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        IntentFilter filter = new IntentFilter("com.you.weplayauto.TOGGLE_SERVICE");
        registerReceiver(toggleReceiver, filter);

        showNotification();
        showToast("Service WePlayAuto đã kết nối!");
        startContinuousChecking();
    }

    private void startContinuousChecking() {
        continuousChecker = new Runnable() {
            @Override
            public void run() {
                if (!isScanning) {
                    handler.postDelayed(this, 1500);
                    return;
                }

                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode == null) {
                    handler.postDelayed(this, 1500);
                    return;
                }

                if (!WEPLAY_PACKAGE.equals(rootNode.getPackageName())) {
                    isAttemptingToStartGame = false;
                    rootNode.recycle();
                    handler.postDelayed(this, 1500);
                    return;
                }

                if (!handleResultScreen(rootNode)) {
                     if (isInLobby(rootNode)) {
                        handleCommandLogic(rootNode);
                        handleStartButton(rootNode);
                    }
                }

                rootNode.recycle();
                handler.postDelayed(this, 1500);
            }
        };
        handler.post(continuousChecker);
    }

    private boolean handleResultScreen(AccessibilityNodeInfo root) {
        if (isAttemptingToStartGame) return false;

        AccessibilityNodeInfo resultNode = findNodeByText(root, "Kết quả");
        AccessibilityNodeInfo playAgainNode = findNodeByText(root, "Chơi tiếp");
        AccessibilityNodeInfo winNode = findNodeByText(root, "WIN");

        boolean isResultScreen = resultNode != null || winNode != null || playAgainNode != null;

        if (resultNode != null) resultNode.recycle();
        if (playAgainNode != null) playAgainNode.recycle();
        if (winNode != null) winNode.recycle();

        if (isResultScreen) {
            handler.postDelayed(() -> {
                if (!clickByText("Chơi tiếp")) {
                     clickByIdOrText("com.wejoy.weplay:id/tv_start_game", "Bắt đầu trò chơi");
                }
                showToast("✅ Tự động vào trận mới!");
                isAttemptingToStartGame = true;
                handler.postDelayed(() -> isAttemptingToStartGame = false, 3000);
            }, 2000);
            return true;
        }

        return false;
    }

    private boolean isInLobby(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo startNode = findNodeByIdOrText(root, "com.wejoy.weplay:id/tv_start_game", "Bắt đầu trò chơi");
        AccessibilityNodeInfo resultNode = findNodeByText(root, "Kết quả");

        boolean inLobby = (startNode != null && resultNode == null);

        if (startNode != null) startNode.recycle();
        if (resultNode != null) resultNode.recycle();

        return inLobby;
    }

    private void handleStartButton(AccessibilityNodeInfo root) {
        if (isAttemptingToStartGame) return;

        AccessibilityNodeInfo startButton = findNodeByIdOrText(root, "com.wejoy.weplay:id/tv_start_game", "Bắt đầu trò chơi");
        if (startButton != null) {
            if (clickNodeAndParents(startButton)) {
                isAttemptingToStartGame = true;
                handler.postDelayed(() -> isAttemptingToStartGame = false, 3000);
            }
            startButton.recycle();
        }
    }

    private void handleCommandLogic(AccessibilityNodeInfo root) {
        if (System.currentTimeMillis() - lastCommandTime < COMMAND_COOLDOWN) return;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("/chedo");
        if (nodes != null && !nodes.isEmpty()) {
            for (AccessibilityNodeInfo node : nodes) {
                String nodeText = getNodeText(node);
                if (nodeText != null && !isChatInputField(node)) {
                    processCommand(nodeText);
                    lastCommandTime = System.currentTimeMillis();
                    break;
                }
            }
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null) node.recycle();
            }
        }
    }

    private void processCommand(String text) {
        String msg = text.toLowerCase();
        if (msg.contains("hotkho")) executeModeChange("Hot nhất", "Khó");
        else if (msg.contains("hotde")) executeModeChange("Hot nhất", "Dễ");
        else if (msg.contains("tinhcakho")) executeModeChange("Tình ca", "Khó");
        else if (msg.contains("tinhcade")) executeModeChange("Tình ca", "Dễ");
        else if (msg.contains("moikho")) executeModeChange("Mới nhất", "Khó");
        else if (msg.contains("moide")) executeModeChange("Mới nhất", "Dễ");
    }

    private void executeModeChange(final String songType, final String difficulty) {
        clickByText("Đổi kho bài hát");
        handler.postDelayed(() -> {
            clickByText(songType);
            handler.postDelayed(() -> {
                clickByText(difficulty);
                handler.postDelayed(() -> {
                    if (!clickByText("Xác nhận")) {
                        clickByText("OK");
                    }
                    showToast("Đổi chế độ thành công!");
                }, 1200);
            }, 1200);
        }, 1500);
    }

    private boolean clickNodeAndParents(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable()) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            boolean success = clickNodeAndParents(parent);
            parent.recycle();
            return success;
        }
        return false;
    }

    private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo root, String text) {
        if (root == null || text == null) return null;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
        AccessibilityNodeInfo result = null;
        if (nodes != null && !nodes.isEmpty()) {
            result = AccessibilityNodeInfo.obtain(nodes.get(0));
            for (int i = 1; i < nodes.size(); i++) {
                if (nodes.get(i) != null) nodes.get(i).recycle();
            }
        }
        return result;
    }

    private boolean clickByIdOrText(String id, String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo nodeToClick = findNodeByIdOrText(root, id, text);
        boolean clicked = false;
        if (nodeToClick != null) {
            clicked = clickNodeAndParents(nodeToClick);
            nodeToClick.recycle();
        }
        root.recycle();
        return clicked;
    }

    private boolean clickByText(String text) {
        return clickByIdOrText(null, text);
    }

    private AccessibilityNodeInfo findNodeByIdOrText(AccessibilityNodeInfo root, String id, String text) {
        if (root == null) return null;

        if (id != null) {
            List<AccessibilityNodeInfo> nodesById = root.findAccessibilityNodeInfosByViewId(id);
            if (nodesById != null && !nodesById.isEmpty()) {
                AccessibilityNodeInfo result = AccessibilityNodeInfo.obtain(nodesById.get(0));
                 for (int i = 1; i < nodesById.size(); i++) {
                     if(nodesById.get(i) != null) nodesById.get(i).recycle();
                 }
                return result;
            }
        }
        return findNodeByText(root, text);
    }

    private String getNodeText(AccessibilityNodeInfo node) {
        if (node == null || node.getText() == null) return null;
        return node.getText().toString().trim();
    }

    private boolean isChatInputField(AccessibilityNodeInfo node) {
        String text = getNodeText(node);
        return text != null && text.contains("Nói gì đó...");
    }

    @Override
    public void onInterrupt() {
        if (handler != null && continuousChecker != null) {
            handler.removeCallbacks(continuousChecker);
        }
        try {
            unregisterReceiver(toggleReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        onInterrupt();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    private void showNotification() {
        String channelId = "WePlayAutoServiceChannel";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(channelId, "WePlay Auto Service", NotificationManager.IMPORTANCE_LOW));
        }
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("WePlay Auto Service")
                .setContentText("Auto play - By NhatDuckk")
                .setSmallIcon(android.R.drawable.star_on)
                .setOngoing(true)
                .build();
        startForeground(1, notification);
    }
}