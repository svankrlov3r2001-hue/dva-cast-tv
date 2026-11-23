package com.dangvietanh.cast.data;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.dangvietanh.cast.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiClient {

    public interface Callback<T> {
        void onSuccess(T data);
        void onError(String message, int httpCode);
    }

    public static final String IOS_API_BASE = "https://dangvietanh.com/dva_ios/api";
    public static final String TV_AUTH_URL = "https://dangvietanh.com/dva_cast/tv_auth.php";
    public static final String EPISODE_LIST_API = "https://api.dangvietanh.com/api/get_episode_list_api";
    private static final String USER_AGENT = "DVACastTV/4.0";

    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile String authToken;

    public ApiClient(Context context) {
        this.context = context.getApplicationContext();
        this.authToken = Prefs.getAuthToken(this.context);
    }

    public void setAuthToken(String token) {
        this.authToken = token != null ? token.trim() : "";
        Prefs.setAuthToken(context, this.authToken);
    }

    public String getAuthToken() {
        return authToken != null ? authToken : "";
    }

    public void saveUser(Models.User user) {
        if (user == null) {
            Prefs.setUserJson(context, "");
        } else {
            Prefs.setUserJson(context, user.toJson().toString());
        }
    }

    public Models.User getCachedUser() {
        String raw = Prefs.getUserJson(context);
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            return Models.User.fromJson(new JSONObject(raw));
        } catch (Exception e) {
            return null;
        }
    }

    public void clearSession() {
        this.authToken = "";
        Prefs.clearAuth(context);
    }

    public void pingTvAuth(final Callback<Boolean> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpResult res = requestJson("GET", TV_AUTH_URL + "?action=ping&_t=" + System.currentTimeMillis(), null, 7000);
                    boolean ok = res.code >= 200 && res.code < 300 && res.json != null && res.json.optBoolean("success", false);
                    postSuccess(cb, ok);
                } catch (Exception e) {
                    postError(cb, "Không thể kết nối máy chủ", 0);
                }
            }
        });
    }

    public void pollQrLogin(final String qrKey, final Callback<Models.QrPollResponse> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("qr_login_key", qrKey);
                    body.put("device_name", buildDeviceName());
                    HttpResult res = requestJson("POST", TV_AUTH_URL + "?action=qr_poll", body.toString(), 8000);
                    if (res.json == null) {
                        postError(cb, "Phản hồi máy chủ không hợp lệ", res.code);
                        return;
                    }
                    Models.QrPollResponse r = new Models.QrPollResponse();
                    r.success = res.json.optBoolean("success", false);
                    r.state = res.json.optString("state", "waiting");
                    r.key = res.json.optString("key", qrKey);
                    r.formattedKey = res.json.optString("formatted_key", "");
                    r.token = res.json.optString("token", "");
                    r.user = Models.User.fromJson(res.json.optJSONObject("user"));
                    r.claimedUserId = res.json.optInt("user_id", 0);
                    r.claimedName = res.json.optString("name", "");
                    r.claimedEmail = res.json.optString("email", "");
                    r.claimedAvatar = res.json.optString("avatar_url", "");
                    r.error = res.json.optString("error", "");
                    postSuccess(cb, r);
                } catch (Exception e) {
                    postError(cb, "Mất kết nối mạng", 0);
                }
            }
        });
    }

    public void cancelQrLogin(final String qrKey) {
        if (qrKey == null || qrKey.trim().isEmpty()) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("qr_login_key", qrKey);
                    requestJson("POST", TV_AUTH_URL + "?action=qr_cancel", body.toString(), 4000);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void checkAuthStatus(final Callback<Models.User> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpResult res = requestJson("GET", IOS_API_BASE + "/auth.php?action=status&_t=" + System.currentTimeMillis(), null, 8000);
                    if (res.code < 200 || res.code >= 300 || res.json == null) {
                        postError(cb, "Lỗi xác thực phiên", res.code);
                        return;
                    }
                    boolean auth = res.json.optBoolean("authenticated", false);
                    Models.User u = auth ? Models.User.fromJson(res.json.optJSONObject("user")) : null;
                    postSuccess(cb, u);
                } catch (Exception e) {
                    postError(cb, "Không thể kết nối máy chủ", 0);
                }
            }
        });
    }

    public void logout(final Runnable onDone) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    requestJson("POST", IOS_API_BASE + "/auth.php?action=logout", "{}", 4500);
                } catch (Exception ignored) {
                }
                clearSession();
                if (onDone != null) {
                    mainHandler.post(onDone);
                }
            }
        });
    }

    public void fetchHomeHub(final String mode, final String genreId, final Callback<Models.HomeHubResponse> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuilder url = new StringBuilder(IOS_API_BASE).append("/home_hub.php?mode=");
                    url.append(URLEncoder.encode(mode != null && !mode.isEmpty() ? mode : "home", "UTF-8"));
                    if (genreId != null && !genreId.trim().isEmpty()) {
                        url.append("&genre_id=").append(URLEncoder.encode(genreId.trim(), "UTF-8"));
                    }
                    url.append("&_t=").append(System.currentTimeMillis());

                    HttpResult res = requestJson("GET", url.toString(), null, 15000);
                    if (res.code < 200 || res.code >= 300 || res.json == null) {
                        postError(cb, extractError(res, "Không thể tải trang chủ."), res.code);
                        return;
                    }
                    Models.HomeHubResponse out = new Models.HomeHubResponse();
                    out.mode = res.json.optString("mode", mode);
                    JSONArray gArr = res.json.optJSONArray("genres");
                    if (gArr != null) {
                        for (int i = 0; i < gArr.length(); i++) {
                            out.genres.add(Models.FilterOption.fromJson(gArr.optJSONObject(i)));
                        }
                    }
                    JSONArray hArr = res.json.optJSONArray("hero");
                    if (hArr != null) {
                        for (int i = 0; i < hArr.length(); i++) {
                            JSONObject o = hArr.optJSONObject(i);
                            if (o != null) out.hero.add(Models.MovieItem.fromJson(o));
                        }
                    }
                    JSONArray sArr = res.json.optJSONArray("sections");
                    if (sArr != null) {
                        for (int i = 0; i < sArr.length(); i++) {
                            JSONObject o = sArr.optJSONObject(i);
                            if (o != null) out.sections.add(Models.HomeSection.fromJson(o));
                        }
                    }
                    postSuccess(cb, out);
                } catch (Exception e) {
                    postError(cb, "Không thể kết nối máy chủ.", 0);
                }
            }
        });
    }

    public void deleteWatchedMovie(final int videosId, final Callback<Boolean> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("videos_id", videosId);
                    HttpResult res = requestJson("POST", IOS_API_BASE + "/home_hub.php?action=delete_watched", body.toString(), 8000);
                    boolean ok = res.code >= 200 && res.code < 300 && res.json != null && res.json.optBoolean("success", false);
                    if (ok) postSuccess(cb, true);
                    else postError(cb, extractError(res, "Không thể xóa phim."), res.code);
                } catch (Exception e) {
                    postError(cb, "Lỗi kết nối.", 0);
                }
            }
        });
    }

    public void fetchMoviesCatalog(
            final int page,
            final int limit,
            final String type,
            final String genre,
            final String country,
            final String query,
            final String sort,
            final Callback<Models.CatalogResponse> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuilder url = new StringBuilder(IOS_API_BASE).append("/movies.php?page=").append(page)
                            .append("&limit=").append(limit);
                    if (type != null && !type.isEmpty()) url.append("&type=").append(URLEncoder.encode(type, "UTF-8"));
                    if (genre != null && !genre.isEmpty()) url.append("&genre=").append(URLEncoder.encode(genre, "UTF-8"));
                    if (country != null && !country.isEmpty()) url.append("&country=").append(URLEncoder.encode(country, "UTF-8"));
                    if (query != null && !query.isEmpty()) url.append("&q=").append(URLEncoder.encode(query, "UTF-8"));
                    if (sort != null && !sort.isEmpty()) url.append("&sort=").append(URLEncoder.encode(sort, "UTF-8"));

                    HttpResult res = requestJson("GET", url.toString(), null, 12000);
                    if (res.code < 200 || res.code >= 300 || res.json == null) {
                        postError(cb, extractError(res, "Không thể tải danh sách phim."), res.code);
                        return;
                    }
                    Models.CatalogResponse out = new Models.CatalogResponse();
                    out.page = res.json.optInt("page", page);
                    out.total = res.json.optInt("total", 0);
                    out.totalPages = Math.max(1, res.json.optInt("total_pages", 1));
                    JSONArray dArr = res.json.optJSONArray("data");
                    if (dArr != null) {
                        for (int i = 0; i < dArr.length(); i++) {
                            JSONObject o = dArr.optJSONObject(i);
                            if (o != null) out.data.add(Models.MovieItem.fromJson(o));
                        }
                    }
                    JSONObject filters = res.json.optJSONObject("filters");
                    if (filters != null) {
                        JSONArray gArr = filters.optJSONArray("genres");
                        if (gArr != null) {
                            for (int i = 0; i < gArr.length(); i++) out.genres.add(Models.FilterOption.fromJson(gArr.optJSONObject(i)));
                        }
                        JSONArray cArr = filters.optJSONArray("countries");
                        if (cArr != null) {
                            for (int i = 0; i < cArr.length(); i++) out.countries.add(Models.FilterOption.fromJson(cArr.optJSONObject(i)));
                        }
                    }
                    postSuccess(cb, out);
                } catch (Exception e) {
                    postError(cb, "Lỗi kết nối mạng.", 0);
                }
            }
        });
    }

    public void searchSuggest(final String q, final Callback<List<Models.MovieItem>> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = IOS_API_BASE + "/user_actions.php?action=search_suggest&q="
                            + URLEncoder.encode(q != null ? q : "", "UTF-8");
                    HttpResult res = requestJson("GET", url, null, 7000);
                    List<Models.MovieItem> items = new ArrayList<>();
                    if (res.json != null) {
                        JSONArray arr = res.json.optJSONArray("suggestions");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject o = arr.optJSONObject(i);
                                if (o != null) items.add(Models.MovieItem.fromJson(o));
                            }
                        }
                    }
                    postSuccess(cb, items);
                } catch (Exception e) {
                    postError(cb, "Lỗi tìm kiếm", 0);
                }
            }
        });
    }

    public void fetchMovieDetails(final int videosId, final Callback<Models.MovieDetailResponse> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = IOS_API_BASE + "/movie_details.php?id=" + videosId + "&_t=" + System.currentTimeMillis();
                    HttpResult res = requestJson("GET", url, null, 12000);
                    if (res.code < 200 || res.code >= 300 || res.json == null) {
                        postError(cb, extractError(res, "Không thể tải thông tin phim."), res.code);
                        return;
                    }
                    Models.MovieDetailResponse out = new Models.MovieDetailResponse();
                    out.movie = Models.MovieDetail.fromJson(res.json.optJSONObject("movie"));
                    JSONArray relArr = res.json.optJSONArray("related");
                    if (relArr != null) {
                        for (int i = 0; i < relArr.length(); i++) {
                            JSONObject o = relArr.optJSONObject(i);
                            if (o != null) out.related.add(Models.MovieItem.fromJson(o));
                        }
                    }
                    postSuccess(cb, out);
                } catch (Exception e) {
                    postError(cb, "Lỗi tải thông tin phim.", 0);
                }
            }
        });
    }

    public void toggleWishlist(final int videosId, final Callback<Boolean> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("videos_id", videosId);
                    HttpResult res = requestJson("POST", IOS_API_BASE + "/user_actions.php?action=toggle_wishlist", body.toString(), 8000);
                    if (res.code >= 200 && res.code < 300 && res.json != null && res.json.optBoolean("success", false)) {
                        postSuccess(cb, res.json.optBoolean("in_wishlist", false));
                    } else {
                        postError(cb, extractError(res, "Không thể cập nhật danh sách."), res.code);
                    }
                } catch (Exception e) {
                    postError(cb, "Lỗi kết nối.", 0);
                }
            }
        });
    }

    public void fetchLibrary(final Callback<Models.LibraryResponse> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpResult res = requestJson("GET", IOS_API_BASE + "/user_actions.php?action=library&_t=" + System.currentTimeMillis(), null, 12000);
                    if (res.code < 200 || res.code >= 300 || res.json == null) {
                        postError(cb, extractError(res, "Không thể tải tủ phim."), res.code);
                        return;
                    }
                    Models.LibraryResponse out = new Models.LibraryResponse();
                    JSONArray wArr = res.json.optJSONArray("wishlist");
                    if (wArr != null) {
                        for (int i = 0; i < wArr.length(); i++) out.wishlist.add(Models.MovieItem.fromJson(wArr.optJSONObject(i)));
                    }
                    JSONArray pArr = res.json.optJSONArray("watched");
                    if (pArr != null) {
                        for (int i = 0; i < pArr.length(); i++) out.watched.add(Models.MovieItem.fromJson(pArr.optJSONObject(i)));
                    }
                    postSuccess(cb, out);
                } catch (Exception e) {
                    postError(cb, "Lỗi tải tủ phim.", 0);
                }
            }
        });
    }

    public void resolveStream(final String streamCode, final int videosId, final Callback<Models.StreamResolvedData> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    StringBuilder url = new StringBuilder(IOS_API_BASE)
                            .append("/stream_resolve.php?stream_code=")
                            .append(URLEncoder.encode(streamCode != null ? streamCode : "", "UTF-8"));
                    if (videosId > 0) {
                        url.append("&videos_id=").append(videosId);
                    }
                    url.append("&_t=").append(System.currentTimeMillis());
                    HttpResult res = requestJson("GET", url.toString(), null, 12000);
                    if (res.code < 200 || res.code >= 300 || res.json == null) {
                        postError(cb, extractError(res, "Không thể khởi tạo luồng phát phim."), res.code);
                        return;
                    }
                    postSuccess(cb, Models.StreamResolvedData.fromJson(res.json));
                } catch (Exception e) {
                    postError(cb, "Lỗi kết nối máy chủ phát phim.", 0);
                }
            }
        });
    }

    public void saveWatchProgress(
            final int videosId,
            final String streamCode,
            final double posSec,
            final double durSec,
            final int episodeNumber,
            final String seasonId,
            final String title) {
        if (videosId <= 0 || streamCode == null || streamCode.trim().isEmpty()) return;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("videos_id", videosId);
                    body.put("stream_code", streamCode);
                    body.put("position_sec", Math.max(0, Math.round(posSec)));
                    body.put("duration_sec", Math.max(0, Math.round(durSec)));
                    if (episodeNumber > 0) body.put("episode_number", episodeNumber);
                    if (seasonId != null && !seasonId.isEmpty()) body.put("season_id", seasonId);
                    if (title != null && !title.isEmpty()) body.put("title", title);
                    requestJson("POST", IOS_API_BASE + "/stream_resolve.php?action=save_progress", body.toString(), 6000);
                } catch (Exception ignored) {
                }
            }
        });
    }

    public void fetchRichEpisodeList(
            final int userId,
            final String movieTitle,
            final String streamCode,
            final String serverMovieTitle,
            final String serverTapPhim,
            final String seasonId,
            final Callback<Models.EpisodeListApiResponse> cb) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    StringBuilder form = new StringBuilder();
                    appendForm(form, "user_id", String.valueOf(Math.max(1, userId)));
                    appendForm(form, "episode_list_name_D8c4Mb5Xw0Cc1Wh", movieTitle != null ? movieTitle : "");
                    appendForm(form, "episode_list_slug_D8c4Mb5Xw0Cc1Wh", streamCode != null ? streamCode : "");
                    appendForm(form, "episode_list_name_o_D8c4Mb5Xw0Cc1Wh",
                            serverMovieTitle != null && !serverMovieTitle.isEmpty() ? serverMovieTitle : (movieTitle != null ? movieTitle : ""));
                    appendForm(form, "episode_list_name_tap_D8c4Mb5Xw0Cc1Wh",
                            serverTapPhim != null && !serverTapPhim.isEmpty() ? serverTapPhim : "01");
                    if (seasonId != null && !seasonId.isEmpty()) {
                        appendForm(form, "seasons_id", seasonId);
                    }

                    URL url = new URL(EPISODE_LIST_API);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(7500);
                    conn.setReadTimeout(9000);
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    conn.setRequestProperty("User-Agent", USER_AGENT);
                    byte[] bytes = form.toString().getBytes(StandardCharsets.UTF_8);
                    OutputStream os = conn.getOutputStream();
                    os.write(bytes);
                    os.flush();
                    os.close();

                    int code = conn.getResponseCode();
                    String text = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
                    JSONObject json = new JSONObject(text);
                    if (!"success".equalsIgnoreCase(json.optString("status"))) {
                        postError(cb, json.optString("message", "Không có danh sách tập"), code);
                        return;
                    }
                    Models.EpisodeListApiResponse out = new Models.EpisodeListApiResponse();
                    out.currentSeasonId = json.isNull("current_season_id") ? "" : json.optString("current_season_id", "");
                    out.watchingSeasonId = json.isNull("watching_season_id") ? "" : json.optString("watching_season_id", "");
                    JSONArray dataArr = json.optJSONArray("data");
                    if (dataArr != null) {
                        for (int i = 0; i < dataArr.length(); i++) {
                            out.data.add(Models.ApiEpisodeItem.fromJson(dataArr.optJSONObject(i)));
                        }
                    }
                    JSONArray sArr = json.optJSONArray("seasons");
                    if (sArr != null) {
                        for (int i = 0; i < sArr.length(); i++) {
                            out.seasons.add(Models.ApiSeasonItem.fromJson(sArr.optJSONObject(i)));
                        }
                    }
                    postSuccess(cb, out);
                } catch (Exception e) {
                    postError(cb, "Không thể tải danh sách tập", 0);
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        });
    }

    private static void appendForm(StringBuilder sb, String k, String v) throws Exception {
        if (sb.length() > 0) sb.append('&');
        sb.append(URLEncoder.encode(k, "UTF-8")).append('=').append(URLEncoder.encode(v != null ? v : "", "UTF-8"));
    }

    private HttpResult requestJson(String method, String urlStr, String jsonBody, int timeoutMs) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            String tok = authToken;
            if (tok != null && !tok.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + tok);
                conn.setRequestProperty("X-DVA-Token", tok);
            }
            if (jsonBody != null && !"GET".equalsIgnoreCase(method)) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                OutputStream os = conn.getOutputStream();
                os.write(bytes);
                os.flush();
                os.close();
            }
            int code = conn.getResponseCode();
            String raw = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            JSONObject obj = (raw != null && !raw.trim().isEmpty()) ? new JSONObject(raw) : new JSONObject();
            return new HttpResult(code, obj);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }

    private static String extractError(HttpResult res, String fallback) {
        if (res != null && res.json != null) {
            String err = res.json.optString("error", "");
            if (!err.isEmpty()) return err;
            String msg = res.json.optString("message", "");
            if (!msg.isEmpty()) return msg;
        }
        return fallback;
    }

    private static String buildDeviceName() {
        String brand = Build.MANUFACTURER != null ? Build.MANUFACTURER.trim() : "";
        String model = Build.MODEL != null ? Build.MODEL.trim() : "";
        String combined = (brand + " " + model).trim();
        return combined.isEmpty() ? "Android TV" : ("TV · " + combined);
    }

    private <T> void postSuccess(final Callback<T> cb, final T data) {
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cb.onSuccess(data);
            }
        });
    }

    private <T> void postError(final Callback<T> cb, final String msg, final int code) {
        if (cb == null) return;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                cb.onError(msg, code);
            }
        });
    }

    private static final class HttpResult {
        final int code;
        final JSONObject json;

        HttpResult(int code, JSONObject json) {
            this.code = code;
            this.json = json;
        }
    }
}
