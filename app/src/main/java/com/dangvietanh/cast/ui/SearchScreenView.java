package com.dangvietanh.cast.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
import java.util.Arrays;
import java.util.List;

public class SearchScreenView extends FrameLayout {

    public interface Listener {
        void onOpenMovieDetail(Models.MovieItem item);
    }

    private final ApiClient apiClient;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final EditText searchInput;
    private final TextView searchBtn;
    private final LinearLayout quickChipsRow;
    private final TextView resultHeadingTv;
    private final RecyclerView gridRv;
    private final UiHelper.SpinnerView spinner;

    private final List<Models.MovieItem> items = new ArrayList<>();
    private final GridAdapter adapter = new GridAdapter();
    private Runnable suggestRunnable;
    private int searchSeq = 0;

    private static final List<String> TRENDING_TAGS = Arrays.asList(
            "Hành động", "Kinh dị", "Anime", "Hàn Quốc", "Trung Quốc", "Khoa học viễn tưởng", "Tâm lý", "4K"
    );

    public SearchScreenView(Context context, ApiClient apiClient, Listener listener) {
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
        addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Search Bar Frosted Container
        LinearLayout searchBar = new LinearLayout(context);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(UiHelper.dp(context, 16), UiHelper.dp(context, 7),
                UiHelper.dp(context, 10), UiHelper.dp(context, 7));
        UiHelper.applyGlassCardStyle(searchBar, context, 14, UiHelper.COLOR_SURFACE_GLASS, 14);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sbLp.bottomMargin = UiHelper.dp(context, 10);

        searchInput = new EditText(context);
        searchInput.setHint("Nhập tên phim, diễn viên hoặc từ khoá...");
        searchInput.setHintTextColor(UiHelper.COLOR_TEXT_MUTED);
        searchInput.setTextColor(Color.WHITE);
        searchInput.setTextSize(14f);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setSingleLine(true);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBar.addView(searchInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        spinner = new UiHelper.SpinnerView(context);
        spinner.setVisibility(GONE);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(UiHelper.dp(context, 22), UiHelper.dp(context, 22));
        spLp.rightMargin = UiHelper.dp(context, 10);
        searchBar.addView(spinner, spLp);

        TextView clearBtn = makeBtn(context, "Xoá", false);
        clearBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                searchInput.setText("");
                performSearch("");
            }
        });
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbLp.rightMargin = UiHelper.dp(context, 10);
        searchBar.addView(clearBtn, cbLp);

        searchBtn = makeBtn(context, "Tìm kiếm", true);
        searchBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                hideKeyboard();
                performSearch(searchInput.getText().toString().trim());
            }
        });
        searchBar.addView(searchBtn);
        root.addView(searchBar, sbLp);

        // Quick Keyword Chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipChildren(false);
        quickChipsRow = new LinearLayout(context);
        quickChipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(quickChipsRow);
        for (final String tag : TRENDING_TAGS) {
            TextView chip = makeBtn(context, tag, false);
            chip.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    searchInput.setText(tag);
                    performSearch(tag);
                }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = UiHelper.dp(context, 10);
            quickChipsRow.addView(chip, clp);
        }
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        csLp.bottomMargin = UiHelper.dp(context, 14);
        root.addView(chipScroll, csLp);

        resultHeadingTv = new TextView(context);
        resultHeadingTv.setText("Phim đề xuất phổ biến");
        resultHeadingTv.setTextColor(UiHelper.COLOR_TEXT_PRIMARY);
        resultHeadingTv.setTextSize(18f);
        resultHeadingTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams rhLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rhLp.bottomMargin = UiHelper.dp(context, 10);
        root.addView(resultHeadingTv, rhLp);

        gridRv = new RecyclerView(context);
        gridRv.setLayoutManager(new GridLayoutManager(context, 5));
        gridRv.setClipChildren(false);
        gridRv.setClipToPadding(false);
        gridRv.setPadding(0, UiHelper.dp(context, 6), 0, UiHelper.dp(context, 24));
        gridRv.setAdapter(adapter);
        root.addView(gridRv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        searchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard();
                    performSearch(searchInput.getText().toString().trim());
                    return true;
                }
                return false;
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suggestRunnable != null) handler.removeCallbacks(suggestRunnable);
                final String q = s.toString().trim();
                suggestRunnable = new Runnable() {
                    @Override
                    public void run() {
                        performSearch(q);
                    }
                };
                handler.postDelayed(suggestRunnable, 480L);
            }
        });
    }

    public void onOpened() {
        if (items.isEmpty()) {
            performSearch("");
        }
        searchInput.post(new Runnable() {
            @Override
            public void run() {
                searchInput.requestFocus();
            }
        });
    }

    private void performSearch(final String query) {
        final int seq = ++searchSeq;
        spinner.setVisibility(VISIBLE);
        spinner.setSpinning(true);

        if (query.isEmpty()) {
            resultHeadingTv.setText("Phim nổi bật được tìm kiếm nhiều");
            apiClient.fetchMoviesCatalog(1, 25, "all", "", "", "", "popular", new ApiClient.Callback<Models.CatalogResponse>() {
                @Override
                public void onSuccess(Models.CatalogResponse data) {
                    if (seq != searchSeq) return;
                    spinner.setVisibility(GONE);
                    spinner.setSpinning(false);
                    items.clear();
                    if (data != null) items.addAll(data.data);
                    adapter.notifyDataSetChanged();
                }

                @Override
                public void onError(String message, int httpCode) {
                    if (seq != searchSeq) return;
                    spinner.setVisibility(GONE);
                    spinner.setSpinning(false);
                }
            });
            return;
        }

        resultHeadingTv.setText("Kết quả cho \"" + query + "\"");
        apiClient.fetchMoviesCatalog(1, 30, "all", "", "", query, "popular", new ApiClient.Callback<Models.CatalogResponse>() {
            @Override
            public void onSuccess(Models.CatalogResponse data) {
                if (seq != searchSeq) return;
                spinner.setVisibility(GONE);
                spinner.setSpinning(false);
                items.clear();
                if (data != null) items.addAll(data.data);
                if (items.isEmpty()) {
                    resultHeadingTv.setText("Không tìm thấy phim phù hợp cho \"" + query + "\"");
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String message, int httpCode) {
                if (seq != searchSeq) return;
                spinner.setVisibility(GONE);
                spinner.setSpinning(false);
            }
        });
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
            }
        } catch (Exception ignored) {
        }
    }

    private TextView makeBtn(Context ctx, String text, boolean accent) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 9), UiHelper.dp(ctx, 16), UiHelper.dp(ctx, 9));
        UiHelper.applyFocusableGlassButton(tv, ctx, 22, accent);
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
            MovieCardView.bind(holder, items.get(position), MovieCardView.TYPE_VERTICAL, position);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
