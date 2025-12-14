package com.you.weplayauto;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class TickDetector {
    
    private static final String TAG = "TickDetector";
    
    // Màu tick xanh WePlay: #4CD964 (RGB: 76, 217, 100)
    private static final int TARGET_RED = 76;
    private static final int TARGET_GREEN = 217;
    private static final int TARGET_BLUE = 100;
    private static final int COLOR_TOLERANCE = 60; // Cho phép sai lệch
    
    private static final double GREEN_PIXEL_THRESHOLD = 0.3; // 0.3% pixel = có tick
    
    public boolean detectGreenTick(Bitmap area) {
        if (area == null || area.getWidth() == 0 || area.getHeight() == 0) {
            return false;
        }
        
        int matchingPixels = 0;
        int totalPixels = area.getWidth() * area.getHeight();
        
        for (int x = 0; x < area.getWidth(); x++) {
            for (int y = 0; y < area.getHeight(); y++) {
                int pixel = area.getPixel(x, y);
                if (isTickGreen(pixel)) {
                    matchingPixels++;
                }
            }
        }
        
        double matchPercentage = (matchingPixels * 100.0) / totalPixels;
        
        Log.d(TAG, String.format("Matching pixels: %d/%d (%.2f%%)", 
            matchingPixels, totalPixels, matchPercentage));
        
        return matchPercentage > GREEN_PIXEL_THRESHOLD;
    }
    
    private boolean isTickGreen(int pixel) {
        int red = Color.red(pixel);
        int green = Color.green(pixel);
        int blue = Color.blue(pixel);
        
        // So khớp với màu #4CD964
        boolean redMatch = Math.abs(red - TARGET_RED) < COLOR_TOLERANCE;
        boolean greenMatch = Math.abs(green - TARGET_GREEN) < COLOR_TOLERANCE;
        boolean blueMatch = Math.abs(blue - TARGET_BLUE) < COLOR_TOLERANCE;
        
        // Điều kiện thứ 2: Green phải cao nhất
        boolean greenDominant = green > red && green > blue && green > 150;
        
        return (redMatch && greenMatch && blueMatch) || greenDominant;
    }
}