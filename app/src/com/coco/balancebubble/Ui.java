package com.coco.balancebubble;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 界面控件工厂：卡片、行列、开关、滑杆、色板的样式统一在这里，避免各处硬编码色值。
 *
 * <p>没有 aapt2，界面全部用代码搭，所以这里相当于一套「微型主题系统」：
 * 所有颜色都从 {@link Theme} 取，深浅模式和六套主题色只改一个对象。
 */
public class Ui {

    private final Context ctx;
    private final float d;
    /** 当前配色 */
    public final Theme th;

    public Ui(Context c) {
        this(c, Prefs.themeFor(c));
    }

    public Ui(Context c, Theme theme) {
        ctx = c;
        d = c.getResources().getDisplayMetrics().density;
        th = theme;
    }

    public Ui with(Theme theme) {
        return new Ui(ctx, theme);
    }

    /** 屏幕密度，给需要在 dp 和 px 之间换算的地方用。 */
    public float density() {
        return d;
    }

    public Context ctx() {
        return ctx;
    }

    public int dp(float v) {
        return Math.round(v * d);
    }

    // ---------------- 基础图形 ----------------

    public static GradientDrawable round(int fill, float radiusPx, int strokePx, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, strokeColor);
        return g;
    }

    /** 全圆角（药丸）底色。 */
    public GradientDrawable pill(int fill) {
        return round(fill, dp(999), 0, 0);
    }

    public static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static LinearLayout.LayoutParams fixed(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private static LinearLayout.LayoutParams rowParams(Ui ui, int topMarginDp) {
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = ui.dp(topMarginDp);
        return p;
    }

    // ---------------- 容器 ----------------

    /** 卡片：圆角 + 细边 + 内边距，底部留间距。 */
    public LinearLayout card() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(th.card(), dp(16), dp(1), th.line()));
        l.setPadding(dp(16), dp(15), dp(16), dp(16));
        LinearLayout.LayoutParams p = wrap();
        p.bottomMargin = dp(13);
        l.setLayoutParams(p);
        return l;
    }

    /** 卡片里的小标题：左侧一根主题色竖条 + 文字。 */
    public View cardTitle(String text) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View bar = new View(ctx);
        bar.setBackground(round(th.accent(), dp(2), 0, 0));
        row.addView(bar, fixed(dp(4), dp(16)));

        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(15.5f);
        t.setTextColor(th.txt());
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = fixed(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = dp(9);
        tp.weight = 1f;
        row.addView(t, tp);
        return row;
    }

    /**
     * 卡片标题 + 一句补充说明。
     *
     * <p>说明单独占一行而不是挤在右边：手机上横向空间不够，长句子会折得很难看。
     */
    public View cardTitle(String text, String desc) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(cardTitle(text));
        if (desc != null && !desc.trim().isEmpty()) {
            TextView t = text(desc, 11.8f, th.sub());
            LinearLayout.LayoutParams p = wrap();
            p.topMargin = dp(4);
            p.leftMargin = dp(13); // 与标题文字左对齐：竖条 4dp + 间距 9dp
            t.setLayoutParams(p);
            box.addView(t);
        }
        return box;
    }

    /** 页面大标题。 */
    public TextView title(String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(23f);
        t.setTextColor(th.txt());
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public TextView text(String s, float sizeSp, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setLineSpacing(dp(4), 1f);
        return t;
    }

    /** 灰色小字说明，跟在字段下面解释这一项是干什么的。 */
    public TextView hint(String s) {
        TextView t = text(s, 11.8f, th.sub());
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = dp(4);
        t.setLayoutParams(p);
        return t;
    }

    /** 独立的说明块（浅色底 + 描边）。 */
    public TextView note(String s) {
        TextView t = text(s, 12.2f, th.txt());
        t.setBackground(round(th.info(), dp(10), dp(1), th.infoLine()));
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = dp(9);
        t.setLayoutParams(p);
        return t;
    }

    /** 字段标题。 */
    public TextView label(String s) {
        TextView t = text(s, 13.2f, th.txt());
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = dp(13);
        p.bottomMargin = dp(5);
        t.setLayoutParams(p);
        return t;
    }

    public EditText edit(String hintText, int inputType) {
        EditText e = new EditText(ctx);
        e.setHint(hintText);
        e.setHintTextColor(th.sub());
        e.setTextSize(14f);
        e.setTextColor(th.txt());
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setBackground(round(th.field(), dp(10), dp(1), th.line()));
        e.setPadding(dp(12), dp(11), dp(12), dp(11));
        return e;
    }

    public TextView button(String s, boolean primary) {
        TextView b = new TextView(ctx);
        b.setText(s);
        b.setTextSize(15f);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(10), dp(13), dp(10), dp(13));
        b.setBackground(round(primary ? th.accent() : th.pillOff(), dp(11), dp(1),
                primary ? th.accent() : th.line()));
        b.setTextColor(primary ? Theme.inkOn(th.accent()) : th.txt());
        b.setClickable(true);
        return b;
    }

    /**
     * 一行两三个按钮：等分宽度、自动留边距，直接塞进已有的横向 LinearLayout 里。
     */
    public void buttonRow(LinearLayout row, String text, boolean primary,
                          View.OnClickListener l) {
        TextView b = button(text, primary);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        row.addView(b, p);
    }

    /** 正文文字色（跟随深浅模式），给需要自己配色的控件用。 */
    public int themeTxt() {
        return th.txt();
    }

    public TextView link(String s) {
        TextView t = text(s, 12.5f, th.accent());
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = dp(6);
        t.setLayoutParams(p);
        t.setPadding(0, dp(6), 0, dp(6));
        t.setClickable(true);
        return t;
    }

    public View divider() {
        View v = new View(ctx);
        v.setBackgroundColor(th.line());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        p.topMargin = dp(14);
        p.bottomMargin = dp(4);
        v.setLayoutParams(p);
        return v;
    }

    public View gap(int h) {
        View v = new View(ctx);
        v.setLayoutParams(fixed(1, dp(h)));
        return v;
    }

    // ---------------- 通用交互协议 ----------------

    /** 选项被点中（单选组、色板、标签页都用它）。 */
    public interface OnPick {
        void onPick(int index);
    }

    /** 一行「标题 + 说明」，很多行都要它。 */
    public LinearLayout titled(String title, String desc) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 13.6f, th.txt());
        t.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(t);
        if (desc != null && !desc.isEmpty()) {
            TextView s = text(desc, 11.6f, th.sub());
            LinearLayout.LayoutParams p = wrap();
            p.topMargin = dp(2);
            s.setLayoutParams(p);
            box.addView(s);
        }
        return box;
    }

    // ---------------- 开关行 ----------------

    /**
     * 开关行：左边标题＋说明，右边一个开关。
     * 开关用系统 {@link Switch} 但把 track/thumb 的颜色按主题染上，免得跟随系统变成看不清的灰。
     */
    public LinearLayout switchRow(String title, String desc, boolean value,
                                  android.widget.CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));

        LinearLayout left = titled(title, desc);
        row.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(ctx);
        sw.setChecked(value);
        sw.setShowText(false);
        sw.setThumbTintList(ColorStateList.valueOf(
                value ? Theme.inkOn(th.accent()) : 0xFFFFFFFF));
        sw.setTrackTintList(ColorStateList.valueOf(
                value ? th.accent() : th.line()));
        if (l != null) {
            sw.setOnCheckedChangeListener(l);
            // 打开/关闭时立刻换色，不用等重建界面
            final Switch fsw = sw;
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                    fsw.setThumbTintList(ColorStateList.valueOf(
                            on ? Theme.inkOn(th.accent()) : 0xFFFFFFFF));
                    fsw.setTrackTintList(ColorStateList.valueOf(on ? th.accent() : th.line()));
                    l.onCheckedChanged(b, on);
                }
            });
        }
        row.addView(sw, fixed(dp(52), dp(32)));
        return row;
    }

    // ---------------- 滑杆行 ----------------

    /** 滑杆行的三件套，外面可以通过它改数值文字和进度。 */
    public static class Slider {
        public LinearLayout row;
        public SeekBar bar;
        public TextView value;
    }

    /**
     * 滑杆行：标题左边、当前值右边，下面一条滑杆。
     *
     * @param min   实际最小值（SeekBar 内部进度从 0 开始，这里做偏移）
     * @param fmt   数值到文字的格式化，例如 "300dp"、"7 秒"
     */
    public Slider sliderRow(String title, String desc, final int min, int max, int value,
                            final Fmt fmt, final SeekBar.OnSeekBarChangeListener l) {
        final Slider s = new Slider();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(9), 0, dp(3));

        LinearLayout head = new LinearLayout(ctx);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = titled(title, desc);
        head.addView(left, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        s.value = text(fmt.text(value), 13f, th.accent());
        s.value.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(s.value);
        box.addView(head);

        s.bar = new SeekBar(ctx);
        s.bar.setMax(Math.max(1, max - min));
        s.bar.setProgress(value - min);
        s.bar.setProgressTintList(ColorStateList.valueOf(th.accent()));
        s.bar.setThumbTintList(ColorStateList.valueOf(th.accent()));
        s.bar.setPadding(0, dp(6), 0, dp(6));
        // 外面套着 ScrollView：手指横着拖滑杆时只要带一点上下抖动，
        // ScrollView 就会把手势抢走去滚页面，滑杆拖到一半断掉。
        // 手按在滑杆上的这段时间，明确告诉父容器别拦。
        s.bar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
        });
        s.bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                int v = progress + min;
                s.value.setText(fmt.text(v));
                if (fromUser && l != null) l.onProgressChanged(b, progress, true);
            }

            @Override
            public void onStartTrackingTouch(SeekBar b) {
                if (l != null) l.onStartTrackingTouch(b);
            }

            @Override
            public void onStopTrackingTouch(SeekBar b) {
                if (l != null) l.onStopTrackingTouch(b);
            }
        });
        s.row = box;
        return s;
    }

    /** 滑杆进度换算：SeekBar 的 progress 是「相对最小值」的偏移。 */
    public static int sliderValue(SeekBar b, int min) {
        return b.getProgress() + min;
    }

    /** 数值格式化：把滑杆进度（已经加了最小值）转成显示文字。 */
    public interface Fmt {
        String text(int value);
    }

    // ---------------- 下拉框 ----------------

    public Spinner spinner(String[] items) {
        Spinner sp = new Spinner(ctx);
        sp.setAdapter(adapter(items));
        sp.setBackground(round(th.field(), dp(10), dp(1), th.line()));
        sp.setPadding(dp(6), dp(4), dp(6), dp(4));
        sp.setPopupBackgroundDrawable(round(th.card(), dp(10), dp(1), th.line()));
        return sp;
    }

    /** 标题 + 下拉框，返回下拉框本体方便挂监听。 */
    public Spinner choiceRow(LinearLayout into, String title, String desc, String[] items,
                             int sel, AdapterView.OnItemSelectedListener l) {
        into.addView(titled(title, desc));
        Spinner sp = spinner(items);
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = dp(7);
        sp.setLayoutParams(p);
        sp.setSelection(sel < 0 ? 0 : sel);
        if (l != null) sp.setOnItemSelectedListener(l);
        into.addView(sp);
        return sp;
    }

    public ArrayAdapter<String> adapter(String[] items) {
        return new Adapter(ctx, items, this);
    }

    /** 下拉框与弹出列表都用同一套配色。 */
    private static class Adapter extends ArrayAdapter<String> {
        private final Ui ui;

        Adapter(Context c, String[] items, Ui ui) {
            super(c, android.R.layout.simple_spinner_item, items);
            this.ui = ui;
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        private TextView build(String s) {
            TextView t = new TextView(getContext());
            t.setText(s);
            t.setTextSize(14f);
            t.setTextColor(ui.th.txt());
            t.setPadding(ui.dp(10), ui.dp(11), ui.dp(10), ui.dp(11));
            return t;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return build(getItem(position) == null ? "" : getItem(position));
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return build(getItem(position) == null ? "" : getItem(position));
        }
    }

    // ---------------- 单选组（标签页 / 模式切换 / 分段选择） ----------------

    /** 分段选择器的状态：一排按钮 + 当前选中项。 */
    public static class Segment {
        public LinearLayout row;
        public TextView[] btns;
        public int selected;
    }

    /**
     * 横向等分的单选组：所有选项平分宽度，选中的那个用主题色底。
     * 顶部四个标签页和「模式」都用它，保证点哪选哪、互斥。
     */
    public Segment segment(String[] items, int sel, final OnPick pick) {
        final Segment seg = new Segment();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(round(th.field(), dp(12), dp(1), th.line()));
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        seg.btns = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView b = new TextView(ctx);
            b.setText(items[i]);
            b.setTextSize(13f);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(4), dp(9), dp(4), dp(9));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (seg.selected == idx) return;
                    paintSegment(seg, idx);
                    if (pick != null) pick.onPick(idx);
                }
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            b.setLayoutParams(p);
            seg.btns[i] = b;
            row.addView(b);
        }
        seg.row = row;
        paintSegment(seg, sel);
        return seg;
    }

    /** 刷新单选组的选中态。 */
    public void paintSegment(Segment seg, int sel) {
        seg.selected = sel;
        for (int i = 0; i < seg.btns.length; i++) {
            boolean on = i == sel;
            seg.btns[i].setTextColor(on ? Theme.inkOn(th.accent()) : th.sub());
            seg.btns[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            seg.btns[i].setBackground(on ? round(th.accent(), dp(9), 0, 0) : null);
        }
    }

    // ---------------- 色板 ----------------

    /** 色板状态：一排圆点 + 当前选中项。 */
    public static class Swatches {
        public LinearLayout row;
        public View[] dots;
        public int selected;
    }

    /**
     * 色板：给主题色和气泡底色选颜色用。
     *
     * @param colors 每个圆点的颜色；某一位传 0 表示「自动」（跟随深浅模式）
     */
    public Swatches swatchRow(String title, String desc, final int[] colors,
                              int sel, final OnPick pick) {
        final Swatches sw = new Swatches();
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(9), 0, dp(5));
        box.addView(titled(title, desc));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = wrap();
        rp.topMargin = dp(9);
        row.setLayoutParams(rp);

        sw.dots = new View[colors.length];
        for (int i = 0; i < colors.length; i++) {
            final int idx = i;
            View dot = new View(ctx);
            int size = dp(30);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
            if (i > 0) p.leftMargin = dp(9);
            dot.setLayoutParams(p);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 先把自己点成选中态再回调：选中态是控件自己的状态，
                    // 让调用方回头再刷一遍很容易漏（也会写成自引用）。
                    paintSwatches(sw, colors, idx);
                    if (pick != null) pick.onPick(idx);
                }
            });
            sw.dots[i] = dot;
            row.addView(dot);
        }
        box.addView(row);
        sw.row = box;
        paintSwatches(sw, colors, sel);
        return sw;
    }

    /** 刷新色板的选中态。 */
    public void paintSwatches(Swatches sw, int[] colors, int sel) {
        sw.selected = sel;
        for (int i = 0; i < sw.dots.length; i++) {
            boolean on = i == sel;
            int fill = colors[i];
            if (fill == 0) {
                // 「自动」：用卡片色当底，靠描边区分选中
                sw.dots[i].setBackground(round(th.card(), dp(15),
                        on ? dp(3) : dp(1), on ? th.accent() : th.line()));
            } else {
                sw.dots[i].setBackground(round(fill, dp(15),
                        on ? dp(3) : 0, on ? th.accent() : 0));
            }
        }
    }

    // ---------------- 状态小标签 ----------------

    /** 状态药丸：跑起来了就是绿色底，停着就是灰色底。 */
    public TextView badge(String text, boolean on) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(11.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        int fill = on ? th.okBg() : th.field();
        t.setTextColor(on ? th.ok() : th.sub());
        t.setBackground(pill(fill));
        return t;
    }
}
