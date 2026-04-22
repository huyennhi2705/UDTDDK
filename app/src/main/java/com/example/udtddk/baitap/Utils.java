package com.example.udtddk.baitap;

/**
 * Tiện ích dùng chung cho toàn bộ module sức khoẻ.
 * Tránh trùng lặp parseFloat() và formatHours() ở nhiều Activity.
 */
public final class Utils {

    private Utils() {}

    /**
     * Parse float an toàn, trả fallback nếu chuỗi rỗng hoặc không hợp lệ.
     */
    public static float parseFloat(String s, float fallback) {
        if (s == null || s.trim().isEmpty()) return fallback;
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Hiển thị số tiếng: "30 phút" cho 0.5, "1 tiếng", "1.5 tiếng",...
     */
    public static String formatHours(float hours) {
        if (hours == 0.5f) return "30 phút";
        if (hours == (int) hours) return (int) hours + " tiếng";
        return hours + " tiếng";
    }

    /**
     * Format khoảng cách nhắc (alias của formatHours để code tự giải thích).
     */
    public static String formatInterval(float hours) {
        return formatHours(hours);
    }

    /**
     * Round float về 1 chữ số thập phân, trả float (nhất quán khi lưu Firebase).
     */
    public static float round1(float value) {
        return Math.round(value * 10f) / 10f;
    }
}