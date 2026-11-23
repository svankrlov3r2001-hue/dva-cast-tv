package com.dangvietanh.cast.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class Models {

    private Models() {
    }

    public static final class User {
        public int userId;
        public String name = "";
        public String username = "";
        public String email = "";
        public String role = "";
        public String ctv = "";
        public String avatarUrl = "";

        public static User fromJson(JSONObject obj) {
            if (obj == null) return null;
            User u = new User();
            u.userId = obj.optInt("user_id", 0);
            u.name = obj.optString("name", "");
            u.username = obj.optString("username", "");
            u.email = obj.optString("email", "");
            u.role = obj.optString("role", "");
            u.ctv = obj.optString("CTV", "");
            u.avatarUrl = obj.optString("avatar_url", "");
            return u;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("user_id", userId);
                obj.put("name", name);
                obj.put("username", username);
                obj.put("email", email);
                obj.put("role", role);
                obj.put("CTV", ctv);
                obj.put("avatar_url", avatarUrl);
            } catch (Exception ignored) {
            }
            return obj;
        }
    }

    public static final class QrPollResponse {
        public boolean success;
        public String state = "waiting"; // waiting | claimed | approved | expired | error
        public String key = "";
        public String formattedKey = "";
        public String token = "";
        public User user;
        public int claimedUserId;
        public String claimedName = "";
        public String claimedEmail = "";
        public String claimedAvatar = "";
        public String error = "";
    }

    public static final class FilterOption {
        public int id;
        public String name = "";
        public String slug = "";

        public static FilterOption fromJson(JSONObject obj) {
            FilterOption opt = new FilterOption();
            if (obj == null) return opt;
            opt.id = obj.optInt("id", 0);
            opt.name = obj.optString("name", "");
            opt.slug = obj.optString("slug", "");
            return opt;
        }
    }

    public static final class MovieItem {
        public int videosId;
        public String title = "";
        public String titlePrimary = "";
        public String subtitle = "";
        public String slug = "";
        public String description = "";
        public String year = "";
        public String runtime = "";
        public String videoQuality = "FHD";
        public String imdbRating = "";
        public int isTvSeries;
        public String episodeBadge = "";
        public String seasonLabel = "";
        public final List<String> genres = new ArrayList<>();
        public final List<String> countries = new ArrayList<>();
        public String posterUrl = "";
        public String thumbnailUrl = "";
        public boolean inWishlist;
        public String streamCode = "";
        public int rank;
        public int progressPercent;
        public String viewedRate = "";
        public String timeRemaining = "";
        public String viewedInfo = "";
        public int resumeSeconds;
        public int totalView;

        public String getDisplayTitle() {
            if (titlePrimary != null && !titlePrimary.trim().isEmpty()) {
                return titlePrimary.trim();
            }
            if (title != null && title.contains(" - ")) {
                return title.substring(0, title.indexOf(" - ")).trim();
            }
            return title != null ? title : "";
        }

        public static MovieItem fromJson(JSONObject obj) {
            MovieItem m = new MovieItem();
            if (obj == null) return m;
            m.videosId = obj.optInt("videos_id", 0);
            m.title = obj.optString("title", "");
            m.titlePrimary = obj.optString("title_primary", "");
            m.subtitle = obj.optString("subtitle", "");
            m.slug = obj.optString("slug", "");
            m.description = obj.optString("description", "");
            m.year = obj.optString("year", "");
            m.runtime = obj.optString("runtime", "");
            m.videoQuality = obj.optString("video_quality", "FHD");
            if (m.videoQuality.trim().isEmpty()) m.videoQuality = "FHD";
            m.imdbRating = obj.optString("imdb_rating", "");
            m.isTvSeries = obj.optInt("is_tvseries", 0);
            m.episodeBadge = obj.optString("episode_badge", "");
            m.seasonLabel = obj.optString("season_label", "");
            m.posterUrl = obj.optString("poster_url", obj.optString("poster", ""));
            m.thumbnailUrl = obj.optString("thumbnail_url", obj.optString("thumb_url", m.posterUrl));
            if (m.posterUrl.isEmpty()) m.posterUrl = m.thumbnailUrl;
            m.inWishlist = obj.optBoolean("in_wishlist", false);
            m.streamCode = obj.optString("stream_code", "");
            m.rank = obj.optInt("rank", 0);
            m.progressPercent = obj.optInt("progress_percent", 0);
            m.viewedRate = obj.optString("viewed_rate", "");
            m.timeRemaining = obj.optString("time_remaining", "");
            m.viewedInfo = obj.optString("viewed_info", "");
            m.resumeSeconds = obj.optInt("resume_seconds", 0);
            m.totalView = obj.optInt("total_view", 0);

            JSONArray gArr = obj.optJSONArray("genres");
            if (gArr != null) {
                for (int i = 0; i < gArr.length(); i++) {
                    String g = gArr.optString(i, "").trim();
                    if (!g.isEmpty()) m.genres.add(g);
                }
            }
            JSONArray cArr = obj.optJSONArray("countries");
            if (cArr != null) {
                for (int i = 0; i < cArr.length(); i++) {
                    String c = cArr.optString(i, "").trim();
                    if (!c.isEmpty()) m.countries.add(c);
                }
            }
            return m;
        }
    }

    public static final class TopicItem {
        public String id = "";
        public String title = "";
        public String subtitle = "";
        public String theme = "red";
        public String targetSection = "";

        public static TopicItem fromJson(JSONObject obj) {
            TopicItem t = new TopicItem();
            if (obj == null) return t;
            t.id = obj.optString("id", "");
            t.title = obj.optString("title", "");
            t.subtitle = obj.optString("subtitle", "");
            t.theme = obj.optString("theme", "red");
            t.targetSection = obj.optString("target_section", "");
            return t;
        }
    }

    public static final class CountryHubRow {
        public String id = "";
        public String title = "";
        public String gradient = "purple";
        public final List<MovieItem> items = new ArrayList<>();

        public static CountryHubRow fromJson(JSONObject obj) {
            CountryHubRow r = new CountryHubRow();
            if (obj == null) return r;
            r.id = obj.optString("id", "");
            r.title = obj.optString("title", "").replace("\n", " ");
            r.gradient = obj.optString("gradient", "purple");
            JSONArray arr = obj.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject it = arr.optJSONObject(i);
                    if (it != null) r.items.add(MovieItem.fromJson(it));
                }
            }
            return r;
        }
    }

    public static final class HomeSection {
        public String id = "";
        public String title = "";
        public String layout = "vertical";
        public final List<MovieItem> items = new ArrayList<>();
        public final List<TopicItem> topics = new ArrayList<>();
        public final List<CountryHubRow> hubRows = new ArrayList<>();

        public static HomeSection fromJson(JSONObject obj) {
            HomeSection s = new HomeSection();
            if (obj == null) return s;
            s.id = obj.optString("id", "");
            s.title = obj.optString("title", "");
            s.layout = obj.optString("layout", "vertical");
            JSONArray itArr = obj.optJSONArray("items");
            if (itArr != null) {
                for (int i = 0; i < itArr.length(); i++) {
                    JSONObject it = itArr.optJSONObject(i);
                    if (it != null) s.items.add(MovieItem.fromJson(it));
                }
            }
            JSONArray tpArr = obj.optJSONArray("topics");
            if (tpArr != null) {
                for (int i = 0; i < tpArr.length(); i++) {
                    JSONObject tp = tpArr.optJSONObject(i);
                    if (tp != null) s.topics.add(TopicItem.fromJson(tp));
                }
            }
            JSONArray hbArr = obj.optJSONArray("hub_rows");
            if (hbArr != null) {
                for (int i = 0; i < hbArr.length(); i++) {
                    JSONObject hb = hbArr.optJSONObject(i);
                    if (hb != null) s.hubRows.add(CountryHubRow.fromJson(hb));
                }
            }
            return s;
        }
    }

    public static final class HomeHubResponse {
        public String mode = "home";
        public final List<FilterOption> genres = new ArrayList<>();
        public final List<MovieItem> hero = new ArrayList<>();
        public final List<HomeSection> sections = new ArrayList<>();
    }

    public static final class CatalogResponse {
        public int page = 1;
        public int total = 0;
        public int totalPages = 1;
        public final List<MovieItem> data = new ArrayList<>();
        public final List<FilterOption> genres = new ArrayList<>();
        public final List<FilterOption> countries = new ArrayList<>();
    }

    public static final class EpisodeItem {
        public int episodesId;
        public int seasonsId;
        public String episodesName = "";
        public String streamCode = "";
        public int order;

        public static EpisodeItem fromJson(JSONObject obj) {
            EpisodeItem ep = new EpisodeItem();
            if (obj == null) return ep;
            ep.episodesId = obj.optInt("episodes_id", 0);
            ep.seasonsId = obj.optInt("seasons_id", 0);
            ep.episodesName = obj.optString("episodes_name", "");
            ep.streamCode = obj.optString("stream_code", "");
            ep.order = obj.optInt("order", 0);
            return ep;
        }
    }

    public static final class SeasonItem {
        public int seasonsId;
        public String seasonsName = "";
        public int order;
        public final List<EpisodeItem> episodes = new ArrayList<>();

        public static SeasonItem fromJson(JSONObject obj) {
            SeasonItem s = new SeasonItem();
            if (obj == null) return s;
            s.seasonsId = obj.optInt("seasons_id", 0);
            s.seasonsName = obj.optString("seasons_name", "Phần 1");
            s.order = obj.optInt("order", 0);
            JSONArray arr = obj.optJSONArray("episodes");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject it = arr.optJSONObject(i);
                    if (it != null) s.episodes.add(EpisodeItem.fromJson(it));
                }
            }
            return s;
        }
    }

    public static final class SourceItem {
        public int videoFileId;
        public String streamKey = "";
        public String label = "";
        public String fileSource = "";
        public String streamCode = "";

        public static SourceItem fromJson(JSONObject obj) {
            SourceItem src = new SourceItem();
            if (obj == null) return src;
            src.videoFileId = obj.optInt("video_file_id", 0);
            src.streamKey = obj.optString("stream_key", "");
            src.label = obj.optString("label", "Server");
            src.fileSource = obj.optString("file_source", "");
            src.streamCode = obj.optString("stream_code", "");
            return src;
        }
    }

    public static final class StarItem {
        public int id;
        public String name = "";
        public String starType = "";
        public String imageUrl = "";

        public static StarItem fromJson(JSONObject obj) {
            StarItem st = new StarItem();
            if (obj == null) return st;
            st.id = obj.optInt("id", 0);
            st.name = obj.optString("name", "");
            st.starType = obj.optString("star_type", "");
            st.imageUrl = obj.optString("image_url", "");
            return st;
        }
    }

    public static final class WatchHistory {
        public String viewedRate = "0%";
        public int progressPercent;
        public String timeRemaining = "";
        public String viewedInformation = "Xem ngay";
        public int resumeSeconds;
        public String lastStreamCode = "";
        public int lastEpisodeId;

        public static WatchHistory fromJson(JSONObject obj) {
            WatchHistory wh = new WatchHistory();
            if (obj == null) return wh;
            wh.viewedRate = obj.optString("viewed_rate", "0%");
            wh.progressPercent = obj.optInt("progress_percent", 0);
            wh.timeRemaining = obj.optString("time_remaining", "");
            wh.viewedInformation = obj.optString("viewed_information", "Xem ngay");
            wh.resumeSeconds = obj.optInt("resume_seconds", 0);
            wh.lastStreamCode = obj.optString("last_stream_code", "");
            wh.lastEpisodeId = obj.optInt("last_episode_id", 0);
            return wh;
        }
    }

    public static final class MovieDetail {
        public int videosId;
        public String code = "";
        public String streamCode = "";
        public String title = "";
        public String slug = "";
        public String description = "";
        public String release = "";
        public String year = "";
        public String runtime = "";
        public String videoQuality = "HD";
        public String imdbRating = "";
        public int isTvSeries;
        public int totalRating;
        public int totalView;
        public String posterUrl = "";
        public String thumbnailUrl = "";
        public boolean inWishlist;
        public WatchHistory watchHistory = new WatchHistory();
        public final List<FilterOption> genres = new ArrayList<>();
        public final List<FilterOption> countries = new ArrayList<>();
        public final List<StarItem> directors = new ArrayList<>();
        public final List<StarItem> writers = new ArrayList<>();
        public final List<StarItem> stars = new ArrayList<>();
        public final List<EpisodeItem> episodes = new ArrayList<>();
        public final List<SeasonItem> seasons = new ArrayList<>();
        public final List<SourceItem> sources = new ArrayList<>();

        public static MovieDetail fromJson(JSONObject obj) {
            MovieDetail md = new MovieDetail();
            if (obj == null) return md;
            md.videosId = obj.optInt("videos_id", 0);
            md.code = obj.optString("code", "");
            md.streamCode = obj.optString("stream_code", "");
            md.title = obj.optString("title", "");
            md.slug = obj.optString("slug", "");
            md.description = obj.optString("description", "");
            md.release = obj.optString("release", "");
            md.year = obj.optString("year", "");
            md.runtime = obj.optString("runtime", "");
            md.videoQuality = obj.optString("video_quality", "HD");
            md.imdbRating = obj.optString("imdb_rating", "");
            md.isTvSeries = obj.optInt("is_tvseries", 0);
            md.totalRating = obj.optInt("total_rating", 0);
            md.totalView = obj.optInt("total_view", 0);
            md.posterUrl = obj.optString("poster_url", "");
            md.thumbnailUrl = obj.optString("thumbnail_url", md.posterUrl);
            md.inWishlist = obj.optBoolean("in_wishlist", false);
            md.watchHistory = WatchHistory.fromJson(obj.optJSONObject("watch_history"));

            JSONArray gArr = obj.optJSONArray("genres");
            if (gArr != null) {
                for (int i = 0; i < gArr.length(); i++) md.genres.add(FilterOption.fromJson(gArr.optJSONObject(i)));
            }
            JSONArray cArr = obj.optJSONArray("countries");
            if (cArr != null) {
                for (int i = 0; i < cArr.length(); i++) md.countries.add(FilterOption.fromJson(cArr.optJSONObject(i)));
            }
            JSONArray dArr = obj.optJSONArray("director_list");
            if (dArr != null) {
                for (int i = 0; i < dArr.length(); i++) md.directors.add(StarItem.fromJson(dArr.optJSONObject(i)));
            }
            JSONArray wArr = obj.optJSONArray("writer_list");
            if (wArr != null) {
                for (int i = 0; i < wArr.length(); i++) md.writers.add(StarItem.fromJson(wArr.optJSONObject(i)));
            }
            JSONArray sArr = obj.optJSONArray("star_list");
            if (sArr != null) {
                for (int i = 0; i < sArr.length(); i++) md.stars.add(StarItem.fromJson(sArr.optJSONObject(i)));
            }
            JSONArray epArr = obj.optJSONArray("episodes");
            if (epArr != null) {
                for (int i = 0; i < epArr.length(); i++) md.episodes.add(EpisodeItem.fromJson(epArr.optJSONObject(i)));
            }
            JSONArray seArr = obj.optJSONArray("seasons");
            if (seArr != null) {
                for (int i = 0; i < seArr.length(); i++) md.seasons.add(SeasonItem.fromJson(seArr.optJSONObject(i)));
            }
            JSONArray srArr = obj.optJSONArray("sources");
            if (srArr != null) {
                for (int i = 0; i < srArr.length(); i++) md.sources.add(SourceItem.fromJson(srArr.optJSONObject(i)));
            }
            return md;
        }
    }

    public static final class MovieDetailResponse {
        public MovieDetail movie;
        public final List<MovieItem> related = new ArrayList<>();
    }

    public static final class LibraryResponse {
        public final List<MovieItem> wishlist = new ArrayList<>();
        public final List<MovieItem> watched = new ArrayList<>();
    }

    public static final class StreamQuality {
        public String label = "";
        public String url = "";
        public String type = "mp4";

        public static StreamQuality fromJson(JSONObject obj) {
            StreamQuality q = new StreamQuality();
            if (obj == null) return q;
            q.label = obj.optString("label", "");
            q.url = obj.optString("url", "");
            q.type = obj.optString("type", "mp4");
            return q;
        }
    }

    public static final class StreamSubtitle {
        public String label = "";
        public String url = "";
        public String lang = "vi";

        public static StreamSubtitle fromJson(JSONObject obj) {
            StreamSubtitle s = new StreamSubtitle();
            if (obj == null) return s;
            s.label = obj.optString("label", "Phụ đề");
            s.url = obj.optString("url", "");
            s.lang = obj.optString("lang", "vi");
            return s;
        }
    }

    public static final class StreamMovieContext {
        public int videosId;
        public String title = "";
        public String slug = "";
        public String description = "";
        public String year = "";
        public String runtime = "";
        public String videoQuality = "Full HD";
        public String imdbRating = "";
        public int isTvSeries;
        public String posterUrl = "";
        public String thumbnailUrl = "";
        public boolean inWishlist;

        public static StreamMovieContext fromJson(JSONObject obj) {
            if (obj == null) return null;
            StreamMovieContext c = new StreamMovieContext();
            c.videosId = obj.optInt("videos_id", 0);
            c.title = obj.optString("title", "");
            c.slug = obj.optString("slug", "");
            c.description = obj.optString("description", "");
            c.year = obj.optString("year", "");
            c.runtime = obj.optString("runtime", "");
            c.videoQuality = obj.optString("video_quality", "Full HD");
            c.imdbRating = obj.optString("imdb_rating", "");
            c.isTvSeries = obj.optInt("is_tvseries", 0);
            c.posterUrl = obj.optString("poster_url", "");
            c.thumbnailUrl = obj.optString("thumbnail_url", c.posterUrl);
            c.inWishlist = obj.optBoolean("in_wishlist", false);
            return c;
        }
    }

    public static final class StreamMemberInfo {
        public int userId;
        public String name = "";
        public String avatarUrl = "";
        public int points;
        public String rank = "Thành viên";
        public boolean isVip;
        public int daysLeft;
        public String joinDate = "";
        public String expiryDate = "";

        public static StreamMemberInfo fromJson(JSONObject obj) {
            if (obj == null) return null;
            StreamMemberInfo m = new StreamMemberInfo();
            m.userId = obj.optInt("user_id", 0);
            m.name = obj.optString("name", "");
            m.avatarUrl = obj.optString("avatar_url", "");
            m.points = obj.optInt("points", 0);
            m.rank = obj.optString("rank", "Thành viên");
            m.isVip = obj.optBoolean("is_vip", false);
            m.daysLeft = obj.optInt("days_left", 0);
            m.joinDate = obj.optString("join_date", "");
            m.expiryDate = obj.optString("expiry_date", "");
            return m;
        }
    }

    public static final class NextEpisodeMeta {
        public String streamCode = "";
        public String serverFileKey = "";
        public String serverMovieTitle = "";
        public String serverTapPhim = "";
        public int skipIntroSec;
        public int endCreditsSec;
        public String subHigh = "";
        public boolean ggdriver1;
        public boolean ggdriver2;
        public boolean ggdriver3;

        public static NextEpisodeMeta fromJson(JSONObject obj) {
            if (obj == null) return null;
            NextEpisodeMeta n = new NextEpisodeMeta();
            n.streamCode = obj.optString("stream_code", "");
            n.serverFileKey = obj.optString("server_file_key", "");
            n.serverMovieTitle = obj.optString("server_movie_title", "");
            n.serverTapPhim = obj.optString("server_tap_phim", "");
            n.skipIntroSec = obj.optInt("skip_intro_sec", 0);
            n.endCreditsSec = obj.optInt("end_credits_sec", 0);
            n.subHigh = obj.optString("sub_high", "");
            n.ggdriver1 = obj.optBoolean("ggdriver_1", false);
            n.ggdriver2 = obj.optBoolean("ggdriver_2", false);
            n.ggdriver3 = obj.optBoolean("ggdriver_3", false);
            return n;
        }
    }

    public static final class StreamResolvedData {
        public boolean success;
        public String streamCode = "";
        public String serverMovieTitle = "";
        public String serverTapPhim = "";
        public String serverFileKey = "";
        public String title = "";
        public String episodeTitle = "";
        public boolean canPlayNative;
        public String primaryUrl = "";
        public String streamType = "mp4";
        public final List<StreamQuality> qualities = new ArrayList<>();
        public final List<StreamSubtitle> subtitles = new ArrayList<>();
        public int skipIntroSec;
        public int endCreditsSec;
        public String subHigh = "";
        public boolean ggdriver1;
        public boolean ggdriver2;
        public boolean ggdriver3;
        public String hanquocUrl = "";
        public String phien40Url = "";
        public String nextEpisodeSlug = "";
        public NextEpisodeMeta nextEpisodeMeta;
        public int resumeSeconds;
        public StreamMovieContext movie;
        public final List<SourceItem> sources = new ArrayList<>();
        public final List<SeasonItem> seasons = new ArrayList<>();
        public final List<EpisodeItem> episodes = new ArrayList<>();
        public EpisodeItem nextEpisode;
        public StreamMemberInfo member;

        public static StreamResolvedData fromJson(JSONObject obj) {
            StreamResolvedData d = new StreamResolvedData();
            if (obj == null) return d;
            d.success = obj.optBoolean("success", false);
            d.streamCode = obj.optString("stream_code", "");
            d.serverMovieTitle = obj.optString("server_movie_title", "");
            d.serverTapPhim = obj.optString("server_tap_phim", "");
            d.serverFileKey = obj.optString("server_file_key", "");
            d.title = obj.optString("title", "");
            d.episodeTitle = obj.optString("episode_title", "");
            d.canPlayNative = obj.optBoolean("can_play_native", false);
            d.primaryUrl = obj.optString("primary_url", "");
            d.streamType = obj.optString("stream_type", "mp4");
            d.skipIntroSec = obj.optInt("skip_intro_sec", 0);
            d.endCreditsSec = obj.optInt("end_credits_sec", 0);
            d.subHigh = obj.optString("sub_high", "");
            d.ggdriver1 = obj.optBoolean("ggdriver_1", false);
            d.ggdriver2 = obj.optBoolean("ggdriver_2", false);
            d.ggdriver3 = obj.optBoolean("ggdriver_3", false);
            d.hanquocUrl = obj.optString("hanquoc_url", "");
            d.phien40Url = obj.optString("phien40_url", "");
            d.nextEpisodeSlug = obj.optString("next_episode_slug", "");
            d.nextEpisodeMeta = NextEpisodeMeta.fromJson(obj.optJSONObject("next_episode_meta"));
            d.resumeSeconds = obj.optInt("resume_seconds", 0);
            d.movie = StreamMovieContext.fromJson(obj.optJSONObject("movie"));
            d.member = StreamMemberInfo.fromJson(obj.optJSONObject("member"));

            JSONArray qArr = obj.optJSONArray("qualities");
            if (qArr != null) {
                for (int i = 0; i < qArr.length(); i++) d.qualities.add(StreamQuality.fromJson(qArr.optJSONObject(i)));
            }
            JSONArray subArr = obj.optJSONArray("subtitles");
            if (subArr != null) {
                for (int i = 0; i < subArr.length(); i++) d.subtitles.add(StreamSubtitle.fromJson(subArr.optJSONObject(i)));
            }
            JSONArray srArr = obj.optJSONArray("sources");
            if (srArr != null) {
                for (int i = 0; i < srArr.length(); i++) d.sources.add(SourceItem.fromJson(srArr.optJSONObject(i)));
            }
            JSONArray seArr = obj.optJSONArray("seasons");
            if (seArr != null) {
                for (int i = 0; i < seArr.length(); i++) d.seasons.add(SeasonItem.fromJson(seArr.optJSONObject(i)));
            }
            JSONArray epArr = obj.optJSONArray("episodes");
            if (epArr != null) {
                for (int i = 0; i < epArr.length(); i++) d.episodes.add(EpisodeItem.fromJson(epArr.optJSONObject(i)));
            }
            JSONObject nextEpObj = obj.optJSONObject("next_episode");
            if (nextEpObj != null) {
                d.nextEpisode = EpisodeItem.fromJson(nextEpObj);
            }
            return d;
        }
    }

    public static final class ApiEpisodeItem {
        public String streamKey = "";
        public String episodeName = "";
        public String videoName = "";
        public String posterUrl = "";
        public String watchedStatus = "";
        public String remainingTime = "";
        public int percentWatched;
        public boolean isWatching;
        public String seasonsId = "";

        public static ApiEpisodeItem fromJson(JSONObject obj) {
            ApiEpisodeItem e = new ApiEpisodeItem();
            if (obj == null) return e;
            e.streamKey = obj.optString("stream_key", "");
            e.episodeName = obj.optString("episode_name", "");
            e.videoName = obj.optString("video_name", "");
            e.posterUrl = obj.optString("poster_url", "");
            e.watchedStatus = obj.optString("watched_status", "");
            e.remainingTime = obj.optString("remaining_time", "");
            e.percentWatched = obj.optInt("percent_watched", 0);
            e.isWatching = obj.optBoolean("is_watching", false);
            e.seasonsId = obj.isNull("seasons_id") ? "" : obj.optString("seasons_id", "");
            return e;
        }
    }

    public static final class ApiSeasonItem {
        public String seasonsId = "";
        public String seasonsName = "";

        public static ApiSeasonItem fromJson(JSONObject obj) {
            ApiSeasonItem s = new ApiSeasonItem();
            if (obj == null) return s;
            s.seasonsId = obj.isNull("seasons_id") ? "" : obj.optString("seasons_id", "");
            s.seasonsName = obj.optString("seasons_name", "");
            return s;
        }
    }

    public static final class EpisodeListApiResponse {
        public final List<ApiEpisodeItem> data = new ArrayList<>();
        public final List<ApiSeasonItem> seasons = new ArrayList<>();
        public String currentSeasonId = "";
        public String watchingSeasonId = "";
    }

    public static final class PlayerLaunchRequest {
        public int videosId;
        public String streamCode = "";
        public int episodeId;
        public int episodeNumber;
        public String seasonId = "";
        public String title = "";
        public String episodeTitle = "";
        public String posterUrl = "";
        public String thumbnailUrl = "";
        public int initialResumeSec = -1;
        public final List<EpisodeItem> episodes = new ArrayList<>();
        public final List<SeasonItem> seasons = new ArrayList<>();
    }
}
