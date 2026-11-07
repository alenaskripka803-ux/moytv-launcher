package ru.moytv.launcher;

import android.content.Context;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full-screen frosted panel with a searchable grid: all apps, "add to home", or TV inputs. */
class AppsOverlay extends FrameLayout {
    static final class Item {
        final String key, label;
        final Drawable icon;
        final int lineIcon;
        final Runnable action;

        Item(String key, String label, Drawable icon, int lineIcon, Runnable action) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            this.lineIcon = lineIcon;
            this.action = action;
        }
    }

    private static final int COLS = 6;
    private final TextView title;
    private final EditText search;
    private final GlassFrame searchBox;
    private final LinearLayout grid;
    private final ScrollView scroll;
    private final TextView empty;
    private final Paint bg = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private List<Item> items = new ArrayList<>();

    AppsOverlay(Context c) {
        super(c);
        setWillNotDraw(false);
        setClickable(true);
        setVisibility(GONE);
        setClipChildren(false);

        BitmapShader sh = new BitmapShader(Wallpaper.blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale((float) Ui.W / Wallpaper.blur.getWidth(), (float) Ui.H / Wallpaper.blur.getHeight());
        sh.setLocalMatrix(m);
        bg.setShader(sh);

        title = Ui.text(c, "", 40, Ui.semibold, 0xFFFFFFFF);
        addView(title, Ui.at(Ui.WRAP, Ui.WRAP, Gravity.TOP | Gravity.LEFT, 120, 66, 0, 0));

        searchBox = new GlassFrame(c, 32);
        searchBox.setPadding(Ui.px(26), 0, Ui.px(26), 0);
        LinearLayout sRow = new LinearLayout(c);
        sRow.setGravity(Gravity.CENTER_VERTICAL);
        Icons.IconView sIcon = new Icons.IconView(c, Icons.SEARCH, 26);
        sRow.addView(sIcon, new LinearLayout.LayoutParams(Ui.px(30), Ui.px(30)));
        search = new EditText(c);
        search.setBackground(null);
        search.setTextColor(0xFFFFFFFF);
        search.setHintTextColor(0x99FFFFFF);
        search.setHint("Поиск приложения");
        search.setTypeface(Ui.medium);
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24 * Ui.s);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setPadding(Ui.px(14), 0, 0, 0);
        search.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean f) {
                searchBox.setDashed(false);
                searchBox.invalidate();
                searchBox.setSelected(f);
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable e) { rebuild(); }
        });
        sRow.addView(search, new LinearLayout.LayoutParams(0, Ui.MATCH, 1f));
        searchBox.addView(sRow, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));
        addView(searchBox, Ui.at(Ui.px(560), Ui.px(64), Gravity.TOP | Gravity.RIGHT, 0, 56, 120, 0));

        scroll = new ScrollView(c);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(0, Ui.px(30), 0, Ui.px(60));
        grid = new LinearLayout(c);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER_HORIZONTAL);
        grid.setClipChildren(false);
        scroll.addView(grid, new FrameLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
        scroll.setOnScrollChangeListener(new OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int x, int y, int ox, int oy) {
                invalidateTiles();
            }
        });
        addView(scroll, Ui.at(Ui.MATCH, Ui.MATCH, Gravity.TOP, 0, 150, 0, 0));

        empty = Ui.text(c, "Ничего не найдено", 26, Ui.medium, 0xB3FFFFFF);
        empty.setVisibility(GONE);
        addView(empty, Ui.at(Ui.WRAP, Ui.WRAP, Ui.TOP_CENTER, 0, 300, 0, 0));
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), bg);
        c.drawColor(0xA60A1028);
    }

    void show(String heading, List<Item> list, boolean withSearch, boolean focusSearch) {
        title.setText(heading);
        items = list;
        searchBox.setVisibility(withSearch ? VISIBLE : GONE);
        search.setText("");
        rebuild();
        setVisibility(VISIBLE);
        scroll.scrollTo(0, 0);
        if (withSearch && focusSearch) {
            search.requestFocus();
        } else if (grid.getChildCount() > 0) {
            View first = ((LinearLayout) grid.getChildAt(0)).getChildAt(0);
            if (first != null) first.requestFocus();
        }
    }

    void hide() {
        setVisibility(GONE);
    }

    boolean isShowing() {
        return getVisibility() == VISIBLE;
    }

    private void rebuild() {
        grid.removeAllViews();
        String q = search.getText().toString().trim().toLowerCase(Locale.getDefault());
        LinearLayout row = null;
        int n = 0;
        for (final Item it : items) {
            if (q.length() > 0 && !it.label.toLowerCase(Locale.getDefault()).contains(q)) continue;
            if (n % COLS == 0) {
                row = new LinearLayout(getContext());
                row.setClipChildren(false);
                row.setGravity(Gravity.CENTER_HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP);
                rlp.bottomMargin = Ui.px(0);
                grid.addView(row, rlp);
            }
            TileView t = new TileView(getContext(), it.key, it.label, it.icon, it.lineIcon, 220, 190, 88);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
            t.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    it.action.run();
                }
            });
            row.addView(t, lp);
            n++;
        }
        empty.setVisibility(n == 0 ? VISIBLE : GONE);
    }

    private void invalidateTiles() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            LinearLayout r = (LinearLayout) grid.getChildAt(i);
            for (int j = 0; j < r.getChildCount(); j++) r.getChildAt(j).invalidate();
        }
    }
}
