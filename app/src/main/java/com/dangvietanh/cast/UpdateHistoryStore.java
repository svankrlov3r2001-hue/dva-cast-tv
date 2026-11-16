package com.dangvietanh.cast;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UpdateHistoryStore {

    public static final class HistoryEntry {
        public final long versionCode;
        public final String versionName;
        public final String date;
        public final String title;
        public final List<String> changes;

        public HistoryEntry(long versionCode, String versionName, String date, String title, List<String> changes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.date = date;
            this.title = title;
            this.changes = changes;
        }
    }

    private UpdateHistoryStore() {
    }

    public static void saveServerPayload(Context context, String rawJson) {
        if (context == null || rawJson == null || rawJson.trim().isEmpty()) return;
        try {
            JSONObject obj = new JSONObject(rawJson);
            if (obj.has("history") || obj.has("versionCode")) {
                Prefs.setCachedUpdateHistoryJson(context, rawJson);
            }
        } catch (Exception ignored) {
        }
    }

    public static List<HistoryEntry> getHistory(Context context) {
        Map<Long, HistoryEntry> byVersion = new LinkedHashMap<>();

        // 1. Built-in history always available offline
        for (HistoryEntry entry : getBuiltInEntries()) {
            byVersion.put(entry.versionCode, entry);
        }

        // 2. Merge cached server history (newer versions or updated notes override built-in)
        if (context != null) {
            String cached = Prefs.getCachedUpdateHistoryJson(context);
            if (cached != null && !cached.trim().isEmpty()) {
                for (HistoryEntry entry : parseServerJson(cached)) {
                    byVersion.put(entry.versionCode, entry);
                }
            }
        }

        List<HistoryEntry> list = new ArrayList<>(byVersion.values());
        Collections.sort(list, new Comparator<HistoryEntry>() {
            @Override
            public int compare(HistoryEntry a, HistoryEntry b) {
                return Long.compare(b.versionCode, a.versionCode);
            }
        });
        return list;
    }

    public static String getCurrentReleaseDate(Context context, long localVersionCode) {
        for (HistoryEntry entry : getHistory(context)) {
            if (entry.versionCode == localVersionCode && entry.date != null && !entry.date.isEmpty()) {
                return entry.date;
            }
        }
        return "16/11/2026";
    }

    private static List<HistoryEntry> parseServerJson(String rawJson) {
        List<HistoryEntry> result = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(rawJson);
            JSONArray arr = root.optJSONArray("history");
            if (arr != null && arr.length() > 0) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.optJSONObject(i);
                    if (item == null) continue;
                    long code = item.optLong("versionCode", 0);
                    String name = item.optString("versionName", code > 0 ? String.valueOf(code) : "");
                    String date = item.optString("date", "");
                    String title = item.optString("title", "Cập nhật phiên bản v" + name);
                    List<String> changes = new ArrayList<>();
                    JSONArray chArr = item.optJSONArray("changes");
                    if (chArr != null) {
                        for (int j = 0; j < chArr.length(); j++) {
                            String line = chArr.optString(j, "").trim();
                            if (!line.isEmpty()) changes.add(line);
                        }
                    }
                    if (code > 0) {
                        result.add(new HistoryEntry(code, name, date, title, changes));
                    }
                }
            } else if (root.has("versionCode")) {
                long code = root.optLong("versionCode", 0);
                String name = root.optString("versionName", String.valueOf(code));
                String date = root.optString("releaseDate", "");
                String note = root.optString("changelog", "");
                List<String> changes = new ArrayList<>();
                if (!note.isEmpty()) changes.add(note);
                if (code > 0) {
                    result.add(new HistoryEntry(code, name, date, "Phiên bản v" + name, changes));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static List<HistoryEntry> getBuiltInEntries() {
        List<HistoryEntry> list = new ArrayList<>();

        List<String> v6 = new ArrayList<>();
        v6.add("Bỏ toàn bộ viền (border) các nút/khung chức năng bên trong Cài đặt, chỉ giữ viền khung ngoài cùng");
        v6.add("Khắc phục triệt để khung vuông màu cam mặc định trên máy chiếu khi điều khiển bằng remote");
        v6.add("Nâng cấp hiệu ứng hover & focus cho các nút điều khiển trên màn hình chiếu chuyên nghiệp hơn");
        v6.add("Sửa lỗi logo đỏ và chữ DVA Cast bị dính sát nhau trên máy chiếu / TV Android");
        list.add(new HistoryEntry(6L, "3.3", "16/11/2026", "Tối ưu Giao diện Cài đặt & Điều khiển Máy chiếu", v6));

        List<String> v5 = new ArrayList<>();
        v5.add("Tích hợp trình phát Native ExoPlayer (Media3) & SurfaceView giải mã phần cứng 4K 60fps HDR");
        v5.add("Tự động phát hiện lỗi giải mã / màn hình đen và hiển thị popup thông báo rõ ràng");
        v5.add("Tắt hiệu ứng blur khi phát video, tối ưu giảm tải GPU và hạn chế render HUD dư thừa");
        v5.add("Ẩn nút toàn màn hình khi chạy trên App TV; tối giản nút Ngắt kết nối thành icon");
        v5.add("Bổ sung hiệu ứng chuyển cảnh mượt mà từ màn hình QR sang trình phát phim");
        v5.add("Đổi nút Quay lại & Đóng trong Cài đặt thành nút icon tối giản chuyên nghiệp");
        list.add(new HistoryEntry(5L, "3.2", "16/11/2026", "Trình phát Native 4K HDR & Tối ưu Hiệu năng TV", v5));

        List<String> v4 = new ArrayList<>();
        v4.add("Sửa lỗi đồ họa vòng xoay Spinner trên màn hình khởi động");
        v4.add("Ẩn nút Quay lại trang chủ khi chạy trực tiếp trên App DVA Cast TV");
        v4.add("Hiển thị ảnh poster của phim thay vì icon mặc định khi tải bộ nhớ đệm");
        v4.add("Đổi tên Tiện ích thành Cài đặt, tối ưu ngôn ngữ chuẩn app xem phim");
        v4.add("Đồng bộ kính trong suốt rgba(0, 0, 0, 0.25) và viền sáng mảnh giữa App và Web");
        v4.add("Bổ sung Nhật ký cập nhật chi tiết theo từng phiên bản trong Cài đặt");
        list.add(new HistoryEntry(4L, "3.1", "15/11/2026", "Nâng cấp Giao diện Rạp phim & Cài đặt Apple Glass", v4));

        List<String> v3 = new ArrayList<>();
        v3.add("Thiết kế lại giao diện trình phát với Top Bar, Dock trái & Timeline đáy");
        v3.add("Mở rộng khung trạng thái Đang kết nối... trên màn hình khởi động");
        v3.add("Kết nối nút Cài đặt trên trình phát để mở nhanh menu điều khiển trên App");
        list.add(new HistoryEntry(3L, "3.0", "15/11/2026", "HUD Trình phát Mới & Menu Cài đặt Apple TV", v3));

        List<String> v2 = new ArrayList<>();
        v2.add("Bổ sung màn hình khởi động UX, menu điều khiển & bảo trì");
        v2.add("Hỗ trợ tự khởi động cùng máy chiếu, tối ưu MediaSession / AudioFocus");
        v2.add("Tích hợp hệ thống tự cập nhật APK qua Wi-Fi");
        list.add(new HistoryEntry(2L, "2.0", "14/11/2026", "UX Khởi động & Tự cập nhật OTA", v2));

        List<String> v1 = new ArrayList<>();
        v1.add("Ra mắt ứng dụng nhận trình chiếu DVA Cast dành cho Android TV & Máy chiếu");
        v1.add("Hỗ trợ ghép nối nhanh bằng mã QR và mã 6 ký tự, đồng bộ thời gian thực");
        list.add(new HistoryEntry(1L, "1.0", "10/11/2026", "Phiên bản DVA Cast TV đầu tiên", v1));

        return list;
    }
}
