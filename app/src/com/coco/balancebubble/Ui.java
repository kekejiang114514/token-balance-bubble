package com.coco.balancebubble;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * 界面控件工厂：卡片、标题、说明、输入框、下拉框的样式统一在这里，避免各处硬编码。
 * 没有 aapt2，界面全部用代码搭。
 */
public class Ui {

    public static final int BG = 0xFFEFF3F9;
    public static final int CARD = 0xFFFFFFFF;
    public static final int FIELD_BG = 0xFFF4F7FB;
    public static final int LINE = 0xFFE1E8F2;
    public static final int TXT = 0xFF16212D;
    public static final int SUB = 0xFF64748B;
    public static final int ACCENT = 0xFF2F6FED;
    public static final int OK = 0xFF12734F;
    public static final int OK_BG = 0xFFE7F6EE;
    public static final int ERR = 0xFFC0392B;
    public static final int ERR_BG = 0xFFFDECEA;
    public static final int INFO_BG = 0xFFEDF3FF;

    private final Context ctx;
    private final float d;

    public Ui(Context c) {
        ctx = c;
        d = c.getResources().getDisplayMetrics().density;
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

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ---------------- 容器 ----------------

    /** 白底圆角卡片，底部留间距。 */
    public LinearLayout card() {
        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, dp(16), dp(1), LINE));
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
        bar.setBackground(round(ACCENT, dp(2), 0, 0));
        row.addView(bar, new LinearLayout.LayoutParams(dp(4), dp(16)));

        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(15.5f);
        t.setTextColor(TXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = dp(9);
        row.addView(t, tp);
        return row;
    }

    /** 页面大标题。 */
    public TextView title(String text) {
        TextView t = new TextView(ctx);
        t.setText(text);
        t.setTextSize(23f);
        t.setTextColor(TXT);
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
        TextView t = text(s, 11.8f, SUB);
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = dp(4);
        t.setLayoutParams(p);
        return t;
    }

    /** 字段标题。 */
    public TextView label(String s) {
        TextView t = text(s, 13.2f, TXT);
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = dp(13);
        p.bottomMargin = dp(5);
        t.setLayoutParams(p);
        return t;
    }

    public EditText edit(String hintText, int inputType) {
        EditText e = new EditText(ctx);
        e.setHint(hintText);
        e.setHintTextColor(0xFFA9B4C4);
        e.setTextSize(14f);
        e.setTextColor(TXT);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setBackground(round(FIELD_BG, dp(10), dp(1), LINE));
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
        if (primary) {
            b.setTextColor(0xFFFFFFFF);
            b.setBackground(round(ACCENT, dp(11), 0, 0));
        } else {
            b.setTextColor(TXT);
            b.setBackground(round(0xFFF2F5FA, dp(11), dp(1), LINE));
        }
        b.setClickable(true);
        return b;
    }

    public TextView link(String s) {
        TextView t = text(s, 12.5f, ACCENT);
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = dp(6);
        t.setLayoutParams(p);
        t.setPadding(0, dp(6), 0, dp(6));
        t.setClickable(true);
        return t;
    }

    public View divider() {
        View v = new View(ctx);
        v.setBackgroundColor(LINE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        p.topMargin = dp(14);
        p.bottomMargin = dp(4);
        v.setLayoutParams(p);
        return v;
    }

    public View gap(int h) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    // ---------------- 下拉框 ----------------

    /** 下拉框：自带配色，避免跟随系统主题变成看不清的样子。 */
    public Spinner spinner(String[] items) {
        Spinner sp = new Spinner(ctx);
        sp.setAdapter(adapter(items));
        sp.setBackground(round(FIELD_BG, dp(10), dp(1), LINE));
        sp.setPadding(dp(6), dp(4), dp(6), dp(4));
        sp.setPopupBackgroundDrawable(round(CARD, dp(10), dp(1), LINE));
        return sp;
    }

    public ArrayAdapter<String> adapter(String[] items) {
        return new Adapter(ctx, items);
    }

    /** 下拉框与弹出列表都用同一套配色。 */
    private static class Adapter extends ArrayAdapter<String> {
        private final Ui ui;

        Adapter(Context c, String[] items) {
            super(c, android.R.layout.simple_spinner_item, items);
            ui = new Ui(c);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        private TextView build(String s) {
            TextView t = new TextView(getContext());
            t.setText(s);
            t.setTextSize(14f);
            t.setTextColor(TXT);
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
}
