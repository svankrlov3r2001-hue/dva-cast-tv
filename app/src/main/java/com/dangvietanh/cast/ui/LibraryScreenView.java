package com.dangvietanh.cast.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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

public class LibraryScreenView extends FrameLayout {

    public interface Listener {
        void onOpenMovieDetail(Models.MovieItem item);
        void onShowToast(String msg, int accentColor);
    }

    private final ApiClient apiClient;
    private final Listener listener;

    private final TextView tabWishlistBtn;
    private final TextView tabWatchedBtn;
    private final TextView emptyTv;
    private final UiHelper.SpinnerView spinner;
    private final RecyclerView gridRv;

    private final List<Models.MovieItem> wishlistItems = new ArrayList<>();
    private final List<Models.MovieItem> watchedItems = new ArrayList<>();
    private final GridAdapter adapter = new GridAdapter();
    private boolean showingWatched = false;

    public LibraryScreenView(Context context, ApiClient apiClient, Listener listener) {
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

        // Top Bar
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setClipChildren(false);
        topRow.setClipToPadding(false);
        topRow.setPadding(0, UiHelper.dp(context, 3), 0, UiHelper.dp(context, 3));
        LinearLayout.LayoutParams trLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        trLp.bottomMargin = UiHelper.dp(context, 10);

        TextView titleTv = new TextView(context);
        titleTv.setText("Danh sách & Đang xem");
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(18f);
        titleTv.setTypeface(null, Typeface.BOLD);
        topRow.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        spinner = new UiHelper.SpinnerView(context);
        spinner.setVisibility(GONE);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 24), UiHelper.dp(context, 24));
        spLp.rightMargin = UiHelper.dp(context, 14);
        topRow.addView(spinner, spLp);

        tabWishlistBtn = makeTabBtn(context, "Danh sách của tôi", true);
        tabWishlistBtn.setId(View.generateViewId());
        tabWishlistBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showingWatched = false;
                updateTabVisuals();
                tabWishlistBtn.requestFocus();
            }
        });
        LinearLayout.LayoutParams t1Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        t1Lp.rightMargin = UiHelper.dp(context, 10);
        topRow.addView(tabWishlistBtn, t1Lp);

        tabWatchedBtn = makeTabBtn(context, "Đang xem gần đây", false);
        tabWatchedBtn.setId(View.generateViewId());
        tabWatchedBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showingWatched = true;
                updateTabVisuals();
                tabWatchedBtn.requestFocus();
            }
        });
        LinearLayout.LayoutParams t2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        t2Lp.rightMargin = UiHelper.dp(context, 10);
        topRow.addView(tabWatchedBtn, t2Lp);

        TextView refreshBtn = makeTabBtn(context, "Làm mới", false);
        refreshBtn.setId(View.generateViewId());
        refreshBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        topRow.addView(refreshBtn);

        root.addView(topRow, trLp);

        emptyTv = new TextView(context);
        emptyTv.setTextColor(UiHelper.COLOR_TEXT_MUTED);
        emptyTv.setTextSize(15f);
        emptyTv.setGravity(Gravity.CENTER);
        emptyTv.setPadding(0, UiHelper.dp(context, 48), 0, UiHelper.dp(context, 48));
        emptyTv.setVisibility(GONE);
        root.addView(emptyTv);

        gridRv = new RecyclerView(context);
        gridRv.setId(View.generateViewId());
        gridRv.setLayoutManager(new GridLayoutManager(context, 5));
        gridRv.setItemAnimator(null);
        gridRv.setClipChildren(false);
        gridRv.setClipToPadding(false);
        gridRv.setPadding(0, UiHelper.dp(context, 6), 0, UiHelper.dp(context, 24));
        gridRv.setAdapter(adapter);
        root.addView(gridRv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        tabWishlistBtn.setNextFocusRightId(tabWatchedBtn.getId());
        tabWishlistBtn.setNextFocusDownId(gridRv.getId());
        tabWatchedBtn.setNextFocusLeftId(tabWishlistBtn.getId());
        tabWatchedBtn.setNextFocusRightId(refreshBtn.getId());
        tabWatchedBtn.setNextFocusDownId(gridRv.getId());
        refreshBtn.setNextFocusLeftId(tabWatchedBtn.getId());
        refreshBtn.setNextFocusDownId(gridRv.getId());
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        TextView target = showingWatched ? tabWatchedBtn : tabWishlistBtn;
        if (target != null) {
            return target.requestFocus();
        }
        return super.requestFocus(direction, previouslyFocusedRect);
    }

    public void refresh() {
        spinner.setVisibility(VISIBLE);
        spinner.setSpinning(true);
        apiClient.fetchLibrary(new ApiClient.Callback<Models.LibraryResponse>() {
            @Override
            public void onSuccess(Models.LibraryResponse data) {
                spinner.setVisibility(GONE);
                spinner.setSpinning(false);
                wishlistItems.clear();
                watchedItems.clear();
                if (data != null) {
                    wishlistItems.addAll(data.wishlist);
                    watchedItems.addAll(data.watched);
                }
                updateTabVisuals();
            }

            @Override
            public void onError(String message, int httpCode) {
                spinner.setVisibility(GONE);
                spinner.setSpinning(false);
                if (listener != null) listener.onShowToast(message, UiHelper.COLOR_ACCENT_RED);
            }
        });
    }

    private void updateTabVisuals() {
        UiHelper.setButtonSelectedVisual(tabWishlistBtn, getContext(), 22, !showingWatched);
        UiHelper.setButtonSelectedVisual(tabWatchedBtn, getContext(), 22, showingWatched);
        boolean empty = (showingWatched ? watchedItems : wishlistItems).isEmpty();
        emptyTv.setVisibility(empty ? VISIBLE : GONE);
        emptyTv.setText(showingWatched ? "Chưa có phim đang xem gần đây." : "Bạn chưa lưu bộ phim nào vào danh sách.");
        gridRv.setVisibility(empty ? GONE : VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private TextView makeTabBtn(Context ctx, String text, boolean active) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13.5f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 9), UiHelper.dp(ctx, 18), UiHelper.dp(ctx, 9));
        UiHelper.applyFocusableGlassButton(tv, ctx, 22, active);
        return tv;
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
            List<Models.MovieItem> active = showingWatched ? watchedItems : wishlistItems;
            MovieCardView.bind(holder, active.get(position), MovieCardView.TYPE_VERTICAL, position);
        }

        @Override
        public int getItemCount() {
            return (showingWatched ? watchedItems : wishlistItems).size();
        }
    }
}
