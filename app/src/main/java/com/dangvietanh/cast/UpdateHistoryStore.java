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
        return "23/11/2026";
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

        List<String> v10 = new ArrayList<>();
        v10.add("Thiết kế lại giao diện Trang chủ chuẩn Android TV với thanh menu cố định gọn gàng bên trái, không che khuất nội dung phim");
        v10.add("Hiển thị banner phim nổi bật toàn màn hình cùng thanh tiện ích Tìm kiếm, Danh sách, Cài đặt, Đồng hồ và Wi-Fi ở góc trên");
        v10.add("Tinh chỉnh hàng Tiếp tục xem và danh sách phim nhỏ gọn, sắc nét kèm biểu tượng phát nhanh và khung sáng rõ nét khi di chuyển bằng remote");
        v10.add("Nâng cấp bề mặt kính mờ tối dịu mắt, giảm độ bo góc và loại bỏ phần nền đục cồng kềnh trên toàn bộ các màn hình và bảng cài đặt");
        list.add(new HistoryEntry(10L, "4.1", "23/11/2026", "Tinh Gọn Giao Diện Trang Chủ & Nâng Cấp Trải Nghiệm Android TV Chuẩn Rạp Phim", v10));

        List<String> v9 = new ArrayList<>();
        v9.add("Giao diện rạp phim màn hình ngang thế hệ mới dành riêng cho Android TV và Máy chiếu với hiệu ứng kính mờ không viền sang trọng");
        v9.add("Đăng nhập nhanh chóng qua mã QR hoặc mã thiết bị 9 số trực tiếp trên màn hình TV");
        v9.add("Kho phim trang chủ, tìm kiếm, khám phá theo thể loại/quốc gia, tủ phim cá nhân và trang chi tiết phim đầy đủ thông tin, diễn viên, phim liên quan");
        v9.add("Trình phát phim siêu mượt hỗ trợ chọn máy chủ phát, danh sách tập, tự động chuyển tập mượt mà, tràn viền màn hình, phụ đề song ngữ, dịch tự động và thuyết minh thông minh");
        v9.add("Giữ nguyên quy trình kiểm tra và tự động cập nhật phiên bản mới qua mạng Wi-Fi cho mọi dòng thiết bị");
        list.add(new HistoryEntry(9L, "4.0", "23/11/2026", "Giao Diện Rạp Phim Toàn Diện Thế Hệ Mới Cho Android TV & Máy Chiếu", v9));

        List<String> v8 = new ArrayList<>();
        v8.add("Tối ưu tốc độ ghép nối mã QR giữa điện thoại, máy tính và màn hình TV / máy chiếu");
        v8.add("Hỗ trợ kết nối tương thích liền mạch cho cả các thiết bị TV đang dùng phiên bản trước");
        v8.add("Cải thiện tốc độ kiểm tra và tự động tải bản cập nhật ứng dụng mới qua mạng Wi-Fi");
        v8.add("Đồng bộ thông tin phiên bản với trang Tải Ứng Dụng dành cho thành viên trên dangvietanh.com");
        list.add(new HistoryEntry(8L, "3.5", "19/11/2026", "Tăng Tốc Kết Nối Mã QR & Nâng Cấp Tự Động Cập Nhật Trên TV", v8));

        List<String> v7 = new ArrayList<>();
        v7.add("Bổ sung bảng Tùy chỉnh phụ đề đầy đủ: Phông chữ, Màu sắc, Cỡ chữ, Vị trí, Viền chữ, Độ trễ, Dịch tự động 108 ngôn ngữ và Thuyết minh thông minh");
        v7.add("Nâng cấp hiển thị phụ đề song ngữ Việt - Anh sắc nét: hỗ trợ đảo vị trí 2 dòng, thanh phân cách và tự ngắt dòng cân đối dễ đọc từ xa");
        v7.add("Đồng bộ cài đặt phụ đề tức thì hai chiều giữa điện thoại điều khiển và màn hình TV");
        v7.add("Hỗ trợ dùng phím điều hướng và nút Quay lại trên remote TV để đóng mở nhanh bảng tùy chỉnh phụ đề");
        list.add(new HistoryEntry(7L, "3.4", "17/11/2026", "Tùy Chỉnh Phụ Đề Toàn Diện, Phụ Đề Song Ngữ & Thuyết Minh Đa Ngôn Ngữ", v7));

        List<String> v6 = new ArrayList<>();
        v6.add("Làm phẳng các thẻ chức năng bên trong bảng Cài đặt giúp giao diện thanh thoát và hiện đại hơn");
        v6.add("Loại bỏ khung viền màu cam mặc định khi di chuyển chọn mục bằng remote trên máy chiếu và TV");
        v6.add("Nâng cấp hiệu ứng sáng nổi bật cho nút đang được chọn trên màn hình trình chiếu");
        v6.add("Căn chỉnh lại khoảng cách giữa biểu trưng và tên ứng dụng cân đối trên mọi dòng TV và máy chiếu");
        list.add(new HistoryEntry(6L, "3.3", "16/11/2026", "Tối Ưu Giao Diện Cài Đặt & Điều Khiển Bằng Remote Trên Máy Chiếu", v6));

        List<String> v5 = new ArrayList<>();
        v5.add("Nâng cấp trình phát phim 4K 60fps HDR cho hình ảnh sắc nét và chuyển động mượt mà trên màn hình lớn");
        v5.add("Tự động nhận biết sự cố luồng phát và hiển thị bảng thông báo hướng dẫn rõ ràng trên màn hình");
        v5.add("Tối ưu hiệu năng hiển thị trong lúc đang phát phim giúp thiết bị hoạt động mát và ổn định hơn");
        v5.add("Ẩn các nút không cần thiết trên màn hình TV và thu gọn nút Ngắt kết nối thành biểu tượng tinh tế");
        v5.add("Bổ sung hiệu ứng chuyển cảnh êm ái từ màn hình chờ quét mã QR sang màn hình chiếu phim");
        v5.add("Thiết kế lại nút Quay lại và nút Đóng trong bảng Cài đặt theo phong cách biểu tượng tối giản");
        list.add(new HistoryEntry(5L, "3.2", "16/11/2026", "Phát Phim 4K HDR 60fps Siêu Mượt & Tối Ưu Hiệu Năng TV", v5));

        List<String> v4 = new ArrayList<>();
        v4.add("Làm mượt hiệu ứng vòng xoay kết nối trên màn hình khởi động ứng dụng");
        v4.add("Tối giản giao diện màn hình chờ khi sử dụng trực tiếp trên ứng dụng TV");
        v4.add("Hiển thị ảnh áp phích của bộ phim đang xem trong lúc nạp dữ liệu phát");
        v4.add("Chuẩn hóa tên gọi mục Cài đặt và câu từ thân thiện với trải nghiệm xem phim gia đình");
        v4.add("Đồng bộ hiệu ứng nền kính mờ trong suốt sang trọng trên toàn bộ các bảng điều khiển");
        v4.add("Bổ sung mục xem thông tin các tính năng mới ngay trong phần Cài đặt của TV");
        list.add(new HistoryEntry(4L, "3.1", "15/11/2026", "Nâng Cấp Giao Diện Rạp Phim & Bảng Cài Đặt Kính Mờ", v4));

        List<String> v3 = new ArrayList<>();
        v3.add("Thiết kế lại giao diện điều khiển khi xem phim với thanh thông tin phía trên, cụm nút nhanh bên trái và thanh thời gian phía dưới");
        v3.add("Mở rộng khung hiển thị trạng thái kết nối rõ ràng hơn trên màn hình khởi động");
        v3.add("Kết nối nút Cài đặt ngay trên màn hình phát phim để mở nhanh bảng tùy chỉnh bằng remote TV");
        list.add(new HistoryEntry(3L, "3.0", "15/11/2026", "Giao Diện Điều Khiển Trình Phát Mới & Menu Cài Đặt Nhanh", v3));

        List<String> v2 = new ArrayList<>();
        v2.add("Bổ sung màn hình khởi động rạp phim cùng bảng điều khiển và dọn bộ nhớ đệm tiện lợi");
        v2.add("Hỗ trợ tùy chọn tự động mở ứng dụng khi bật TV hoặc máy chiếu và tối ưu âm thanh");
        v2.add("Tích hợp tính năng tự động kiểm tra và cập nhật phiên bản mới trực tiếp qua mạng Wi-Fi");
        list.add(new HistoryEntry(2L, "2.0", "14/11/2026", "Màn Hình Khởi Động Mới & Tự Động Cập Nhật Qua Wi-Fi", v2));

        List<String> v1 = new ArrayList<>();
        v1.add("Ra mắt ứng dụng trình chiếu phim màn hình lớn dành riêng cho Android TV, Google TV và Máy chiếu");
        v1.add("Hỗ trợ kết nối nhanh bằng mã QR hoặc mã 6 ký tự, đồng bộ phát phim và phụ đề theo thời gian thực");
        list.add(new HistoryEntry(1L, "1.0", "10/11/2026", "Phiên Bản Chính Thức Đầu Tiên Cho Android TV & Máy Chiếu", v1));

        return list;
    }
}
