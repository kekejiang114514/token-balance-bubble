package com.coco.balancebubble;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;

/** 对话气泡：圆角矩形 + 下方尖角，两行文字（标签 / 金额）。 */
public class BubbleView extends View {

    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint amountPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float density;
    private final float padH, padT, padB, gap, tailW, tailH, radius, maxLabelW;

    private String label = "余额";
    private String amount = "--";
    private boolean error = false;

    public BubbleView(Context c) {
        super(c);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        density = dm.density;
        padH = dp(15);
        padT = dp(9);
        padB = dp(9);
        gap = dp(1);
        tailW = dp(22);
        tailH = dp(9);
        radius = dp(16);
        maxLabelW = Math.min(dp(230), dm.widthPixels * 0.72f);

        labelPaint.setColor(0xFF6B6B76);
        labelPaint.setTextSize(dp(11.5f));
        amountPaint.setColor(0xFF1B1B1F);
        amountPaint.setTextSize(dp(21));
        amountPaint.setFakeBoldText(true);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setShadowLayer(dp(4), 0, dp(1.5f), 0x38000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setData(String label, String amount, boolean error) {
        this.label = label == null ? "" : label;
        this.amount = amount == null ? "--" : amount;
        this.error = error;
        amountPaint.setColor(error ? 0xFFC62828 : 0xFF1B1B1F);
        requestLayout();
        invalidate();
    }

    public void setAmountSize(float px) {
        amountPaint.setTextSize(px);
        requestLayout();
        invalidate();
    }

    private float dp(float v) {
        return v * density;
    }

    /** 标签过长时省略，避免气泡撑满屏幕。 */
    private CharSequence displayLabel() {
        if (labelPaint.measureText(label) <= maxLabelW) return label;
        return TextUtils.ellipsize(label, labelPaint, maxLabelW, TextUtils.TruncateAt.END);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        CharSequence lab = displayLabel();
        boolean hasLabel = lab.length() > 0;
        float lw = hasLabel ? labelPaint.measureText(lab, 0, lab.length()) : 0;
        float aw = amountPaint.measureText(amount);
        float content = Math.max(lw, aw);
        float w = content + padH * 2;
        float bodyH = padT + (hasLabel ? lineH(labelPaint) + gap : 0) + lineH(amountPaint) + padB;
        float h = bodyH + tailH;
        setMeasuredDimension(
                resolveSize((int) Math.ceil(w), wSpec),
                resolveSize((int) Math.ceil(h), hSpec));
    }

    private float lineH(Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float bodyH = h - tailH;
        RectF body = new RectF(0, 0, w, bodyH);
        Path path = new Path();
        path.addRoundRect(body, radius, radius, Path.Direction.CW);
        float cx = w / 2f;
        Path tail = new Path();
        tail.moveTo(cx - tailW / 2f, bodyH - dp(3));
        tail.lineTo(cx, h - dp(1));
        tail.lineTo(cx + tailW / 2f, bodyH - dp(3));
        tail.close();
        path.op(tail, Path.Op.UNION);
        canvas.drawPath(path, fillPaint);

        Paint.FontMetrics lfm = labelPaint.getFontMetrics();
        float baseLabel = padT - lfm.ascent;
        CharSequence lab = displayLabel();
        if (lab.length() > 0) {
            canvas.drawText(lab, 0, lab.length(),
                    (w - labelPaint.measureText(lab, 0, lab.length())) / 2f, baseLabel, labelPaint);
        }

        Paint.FontMetrics afm = amountPaint.getFontMetrics();
        boolean hasLabel = lab.length() > 0;
        float baseAmount = (hasLabel ? padT + lineH(labelPaint) + gap : padT) - afm.ascent;
        canvas.drawText(amount, (w - amountPaint.measureText(amount)) / 2f, baseAmount, amountPaint);
    }
}

