package com.dangvietanh.cast.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.dangvietanh.cast.UiHelper;
import com.dangvietanh.cast.data.ApiClient;
import com.dangvietanh.cast.data.Models;

import java.util.ArrayList;
import java.util.List;

public class ExploreScreenView extends FrameLayout {

    public interface Listener {
        void onOpenMovieDetail(Models.MovieItem item);
    }

    private final ApiClient apiClient;
    private final Listener listener;

    private final LinearLayout typeRow;
    private final LinearLayout sortRow;
    private final LinearLayout genreRow;
    private final LinearLayout countryRow;
    private final RecyclerView gridRv;
    private final TextView btnLoadMore;
    private final UiHelper.SpinnerView spinner;

    private final List<Models.MovieItem> items = new ArrayList<>();
    private final List<Models.FilterOption> genres = new ArrayList<>();
    private final List<Models.FilterOption> countries = new ArrayList<>();
    private final GridAdapter adapter = new GridAdapter();

    private String selectedType = "all";
    private String selectedSort = "latest";
    private String selectedGenre = "";
    private String selectedCountry = "";
    private int currentPage = 1;
    private int totalPages = 1;
    private int reqSeq = 0;

    public ExploreScreenView(Context context, ApiClient apiClient, Listener listener) {
        super(context);
        this.apiClient = apiClient;
        this.listener = listener;

        setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiHelper.dp(context, 20), UiHelper.dp(context, 16),
                UiHelper.dp(context, 20), UiHelper.dp(context, 14));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout hdrRow = new LinearLayout(context);
        hdrRow.setOrientation(LinearLayout.HORIZONTAL);
        hdrRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView hdr = new TextView(context);
        hdr.setText("Khám phá kho phim");
        hdr.setTextColor(Color.WHITE);
        hdr.setTextSize(18f);
        hdr.setTypeface(null, Typeface.BOLD);
        hdrRow.addView(hdr, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        spinner = new UiHelper.SpinnerView(context);
        spinner.setVisibility(GONE);
        hdrRow.addView(spinner, new LinearLayout.LayoutParams(UiHelper.dp(context, 20), UiHelper.dp(context, 20)));
        LinearLayout.LayoutParams hrLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hrLp.bottomMargin = UiHelper.dp(context, 10);
        root.addView(hdrRow, hrLp);

        // Filter Panel (Compact Frosted Surface)
        LinearLayout filterCard = new LinearLayout(context);
        filterCard.setOrientation(LinearLayout.VERTICAL);
        filterCard.setClipChildren(false);
        filterCard.setClipToPadding(false);
        filterCard.setPadding(UiHelper.dp(context, 12), UiHelper.dp(context, 8),
                UiHelper.dp(context, 12), UiHelper.dp(context, 4));
        UiHelper.applyGlassCardStyle(filterCard, context, 14, UiHelper.COLOR_SURFACE_GLASS, 12);
        filterCard.setClipToOutline(false);
        LinearLayout.LayoutParams fcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fcLp.bottomMargin = UiHelper.dp(context, 10);

        typeRow = createFilterStrip(context, filterCard);
        sortRow = createFilterStrip(context, filterCard);
        genreRow = createFilterStrip(context, filterCard);
        countryRow = createFilterStrip(context, filterCard);
        root.addView(filterCard, fcLp);

        gridRv = new RecyclerView(context);
        gridRv.setId(View.generateViewId());
        UiHelper.makeNonFocusable(gridRv);
        gridRv.setLayoutManager(new GridLayoutManager(context, 5));
        gridRv.setItemAnimator(null);
        gridRv.setVerticalScrollBarEnabled(false);
        gridRv.setHorizontalScrollBarEnabled(false);
        gridRv.setClipChildren(false);
        gridRv.setClipToPadding(false);
        gridRv.setPadding(0, UiHelper.dp(context, 6), 0, UiHelper.dp(context, 14));
        gridRv.setAdapter(adapter);
        root.addView(gridRv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        btnLoadMore = new TextView(context);
        btnLoadMore.setId(View.generateViewId());
        btnLoadMore.setText("Tải thêm phim");
        btnLoadMore.setTextColor(Color.WHITE);
        btnLoadMore.setTextSize(13.5f);
        btnLoadMore.setTypeface(null, Typeface.BOLD);
        btnLoadMore.setGravity(Gravity.CENTER);
        btnLoadMore.setPadding(UiHelper.dp(context, 24), UiHelper.dp(context, 10),
                UiHelper.dp(context, 24), UiHelper.dp(context, 10));
        UiHelper.applyFocusableGlassButton(btnLoadMore, context, 24, false);
        btnLoadMore.setVisibility(GONE);
        btnLoadMore.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    int cc = gridRv.getChildCount();
                    if (cc > 0) {
                        UiHelper.requestTvFocus(gridRv.getChildAt(cc - 1));
                        return true;
                    }
                    if (typeRow.getChildCount() > 0) {
                        UiHelper.requestTvFocus(typeRow.getChildAt(0));
                        return true;
                    }
                }
                return false;
            }
        });
        btnLoadMore.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPage < totalPages) {
                    fetchCatalog(currentPage + 1, true);
                }
            }
        });
        LinearLayout.LayoutParams lmLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lmLp.gravity = Gravity.CENTER_HORIZONTAL;
        lmLp.topMargin = UiHelper.dp(context, 8);
        root.addView(btnLoadMore, lmLp);

        renderStaticFilters();
    }

    private LinearLayout createFilterStrip(Context ctx, LinearLayout parent) {
        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        UiHelper.makeNonFocusable(hsv);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setClipChildren(false);
        hsv.setClipToPadding(false);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 3), UiHelper.dp(ctx, 2), UiHelper.dp(ctx, 3));
        hsv.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UiHelper.dp(ctx, 4);
        parent.addView(hsv, lp);
        return row;
    }

    public void onOpened() {
        if (items.isEmpty()) {
            fetchCatalog(1, false);
        }
        requestInitialFocus();
    }

    public void requestInitialFocus() {
        post(new Runnable() {
            @Override
            public void run() {
                if (typeRow.getChildCount() > 0) {
                    UiHelper.requestTvFocus(typeRow.getChildAt(0));
                } else if (gridRv.getChildCount() > 0) {
                    UiHelper.requestTvFocus(gridRv.getChildAt(0));
                }
            }
        });
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        if (typeRow.getChildCount() > 0) {
            return UiHelper.requestTvFocus(typeRow.getChildAt(0));
        }
        if (gridRv.getChildCount() > 0) {
            return UiHelper.requestTvFocus(gridRv.getChildAt(0));
        }
        return super.requestFocus(direction, previouslyFocusedRect);
    }

    public boolean hasValidFocus() {
        return UiHelper.isValidInteractiveFocus(findFocus(), this);
    }

    public boolean ensureValidFocus() {
        if (getVisibility() != VISIBLE) return false;
        if (typeRow.getChildCount() > 0) {
            return UiHelper.requestTvFocus(typeRow.getChildAt(0));
        }
        if (gridRv.getChildCount() > 0) {
            return UiHelper.requestTvFocus(gridRv.getChildAt(0));
        }
        return false;
    }

    private void renderStaticFilters() {
        typeRow.removeAllViews();
        addTypeChip("all", "Tất cả");
        addTypeChip("movie", "Phim Lẻ");
        addTypeChip("series", "Phim Bộ");
        addTypeChip("anime", "Anime");
        addTypeChip("tvshow", "TV Show");

        sortRow.removeAllViews();
        addSortChip("latest", "Mới cập nhật");
        addSortChip("popular", "Xem nhiều");
        addSortChip("rating", "IMDb cao");
        addSortChip("year", "Mới phát hành");
    }

    private void renderDynamicFilters() {
        genreRow.removeAllViews();
        addGenreChip("", "Mọi thể loại");
        for (Models.FilterOption g : genres) {
            addGenreChip(String.valueOf(g.id), g.name);
        }

        countryRow.removeAllViews();
        addCountryChip("", "Mọi quốc gia");
        for (Models.FilterOption c : countries) {
            addCountryChip(String.valueOf(c.id), c.name);
        }
    }

    private void addTypeChip(final String key, String label) {
        final TextView tv = makeChip(label, key.equals(selectedType));
        tv.setTag(key);
        tv.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedType = key;
                updateRowSelectionVisuals(typeRow, selectedType);
                fetchCatalog(1, false);
            }
        });
        typeRow.addView(tv, chipLp());
    }

    private void addSortChip(final String key, String label) {
        final TextView tv = makeChip(label, key.equals(selectedSort));
        tv.setTag(key);
        tv.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedSort = key;
                updateRowSelectionVisuals(sortRow, selectedSort);
                fetchCatalog(1, false);
            }
        });
        sortRow.addView(tv, chipLp());
    }

    private void addGenreChip(final String key, String label) {
        final TextView tv = makeChip(label, key.equals(selectedGenre));
        tv.setTag(key);
        tv.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedGenre = key;
                updateRowSelectionVisuals(genreRow, selectedGenre);
                fetchCatalog(1, false);
            }
        });
        genreRow.addView(tv, chipLp());
    }

    private void addCountryChip(final String key, String label) {
        final TextView tv = makeChip(label, key.equals(selectedCountry));
        tv.setTag(key);
        tv.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedCountry = key;
                updateRowSelectionVisuals(countryRow, selectedCountry);
                fetchCatalog(1, false);
            }
        });
        countryRow.addView(tv, chipLp());
    }

    private void updateRowSelectionVisuals(LinearLayout row, String activeKey) {
        Context ctx = getContext();
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            boolean sel = activeKey != null && activeKey.equals(child.getTag());
            boolean hadFocus = child.hasFocus();
            UiHelper.setButtonSelectedVisual(child, ctx, 20, sel);
            if (hadFocus) {
                UiHelper.applyTvFocusEffect(child, ctx, true, 1.06f, 16);
                UiHelper.requestTvFocus(child);
            }
        }
    }

    private TextView makeChip(String label, boolean selected) {
        Context ctx = getContext();
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 7), UiHelper.dp(ctx, 14), UiHelper.dp(ctx, 7));
        UiHelper.applyFocusableGlassButton(tv, ctx, 20, selected);
        return tv;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiHelper.dp(getContext(), 8);
        return lp;
    }

    private void fetchCatalog(final int page, final boolean append) {
        final int seq = ++reqSeq;
        spinner.setVisibility(VISIBLE);
        spinner.setSpinning(true);

        apiClient.fetchMoviesCatalog(page, 25, selectedType, selectedGenre, selectedCountry, "", selectedSort,
                new ApiClient.Callback<Models.CatalogResponse>() {
                    @Override
                    public void onSuccess(Models.CatalogResponse data) {
                        if (seq != reqSeq) return;
                        spinner.setVisibility(GONE);
                        spinner.setSpinning(false);
                        if (data == null) return;

                        final boolean hadFocusInside = hasFocus();
                        final boolean loadMoreHadFocus = btnLoadMore.hasFocus();

                        currentPage = data.page;
                        totalPages = Math.max(1, data.totalPages);
                        if (genres.isEmpty() && !data.genres.isEmpty()) {
                            genres.addAll(data.genres);
                            countries.addAll(data.countries);
                            renderDynamicFilters();
                        }
                        if (!append) {
                            items.clear();
                            gridRv.scrollToPosition(0);
                        }
                        items.addAll(data.data);
                        adapter.notifyDataSetChanged();

                        if (currentPage < totalPages) {
                            btnLoadMore.setVisibility(VISIBLE);
                            btnLoadMore.setText("Tải thêm phim (" + currentPage + "/" + totalPages + ")");
                        } else {
                            btnLoadMore.setVisibility(GONE);
                        }

                        if (hadFocusInside || loadMoreHadFocus) {
                            post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!UiHelper.isValidInteractiveFocus(findFocus(), ExploreScreenView.this)) {
                                        int cc = gridRv.getChildCount();
                                        if (loadMoreHadFocus && cc > 0) {
                                            UiHelper.requestTvFocus(gridRv.getChildAt(cc - 1));
                                        } else if (cc > 0) {
                                            UiHelper.requestTvFocus(gridRv.getChildAt(0));
                                        } else if (typeRow.getChildCount() > 0) {
                                            UiHelper.requestTvFocus(typeRow.getChildAt(0));
                                        }
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(String message, int httpCode) {
                        if (seq != reqSeq) return;
                        spinner.setVisibility(GONE);
                        spinner.setSpinning(false);
                    }
                });
    }

    private class GridAdapter extends RecyclerView.Adapter<MovieCardView.Holder> {
        @NonNull
        @Override
        public MovieCardView.Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return MovieCardView.createHolder(parent.getContext(), MovieCardView.TYPE_VERTICAL, true,
                    new MovieCardView.OnMovieClickListener() {
                        @Override
                        public void onMovieClick(Models.MovieItem item) {
                            if (listener != null) listener.onOpenMovieDetail(item);
                        }
                    }, null);
        }

        @Override
        public void onBindViewHolder(@NonNull MovieCardView.Holder holder, int position) {
            MovieCardView.bind(holder, items.get(position), MovieCardView.TYPE_VERTICAL, position);
            final int pos = position;
            holder.itemView.setOnKeyListener(new OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        int total = items.size();
                        int lastRow = (total - 1) / 5;
                        int curRow = pos / 5;
                        if (curRow == lastRow && btnLoadMore.getVisibility() == VISIBLE) {
                            UiHelper.requestTvFocus(btnLoadMore);
                            return true;
                        }
                    }
                    return false;
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
