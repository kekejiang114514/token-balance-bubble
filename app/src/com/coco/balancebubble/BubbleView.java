package com.coco.balancebubble;

import android.animation.ValueAnimator;
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
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话气泡：圆角矩形 + 下方尖角。
 *
 * <p>尺寸完全跟着文字走：先按基准字号排版，一行放不下就折行，折到第三行还放不下才缩字号。
 * 宽高变化用一小段补间动画过渡，所以短句是小气泡、长句自然变宽变高。
 */
public class BubbleView extends View {

    /** 金额最多显示几行，再多就缩字号。 */
    private static final int MAX_LINES = 3;

    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint amountPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float density;
    private final float padH, padT, padB, gap, tailW, tailH, radius;
    private final float maxW, minW, baseAmountSize;

    private String label = "";
    private String amount = "--";
    private boolean error = false;

    private final List<String> lines = new ArrayList<String>();
    private float curAmountSize;
    /** setAmountSize 指定的字号，留到下一次 setData 生效；<=0 表示用基准字号。 */
    private float pendingSize;

    private float animW, animH, targetW, targetH;
    private ValueAnimator sizeAnim;
    private boolean laidOutOnce = false;

    public BubbleView(Context c) {
        super(c);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        density = dm.density;
        padH = dp(15);
        padT = dp(9);
        padB = dp(10);
        gap = dp(2);
        tailW = dp(22);
        tailH = dp(9);
        radius = dp(16);
        maxW = Math.min(dp(300), dm.widthPixels * 0.80f);
        minW = dp(74);
        baseAmountSize = dp(22);

        labelPaint.setColor(0xFF6B6B76);
        labelPaint.setTextSize(dp(11.5f));
        amountPaint.setColor(0xFF1B1B1F);
        amountPaint.setTextSize(baseAmountSize);
        amountPaint.setFakeBoldText(true);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setShadowLayer(dp(4), 0, dp(1.5f), 0x38000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    private float dp(float v) {
        return v * density;
    }

    /** 设置内容。label 空字符串表示只显示一行金额/文字。 */
    public void setData(String label, String amount, boolean error) {
        this.label = label == null ? "" : label;
        this.amount = (amount == null || amount.isEmpty()) ? "--" : amount;
        this.error = error;
        amountPaint.setColor(error ? 0xFFC62828 : 0xFF1B1B1F);
        measureContent(pendingSize > 0 ? pendingSize : baseAmountSize);
        animateToTarget();
        invalidate();
    }

    /** 指定这一句的基准字号（服务端按消息类型调用），仍会按需自动缩小。 */
    public void setAmountSize(float px) {
        pendingSize = px;
        measureContent(px);
        animateToTarget();
        invalidate();
    }

    // ---------------- 排版 ----------------

    private final TextWrap.Width measurer = new TextWrap.Width() {
        @Override
        public float of(String text, int from, int to) {
            return amountPaint.measureText(text, from, to);
        }
    };

    /** 按基准字号排版；放不下时逐级缩小字号。 */
    private void measureContent(float baseSize) {
        float innerMax = maxW - padH * 2;
        float size = baseSize;
        List<String> best = null;
        boolean cut = false;
        float[] sizes = {baseSize, baseSize * 0.82f, baseSize * 0.68f};
        for (float s : sizes) {
            amountPaint.setTextSize(s);
            size = s;
            boolean[] t = new boolean[1];
            best = TextWrap.wrap(amount, innerMax, MAX_LINES, measurer, t);
            cut = t[0];
            if (!cut) break;            // 折得下就用这个字号
        }
        if (cut) {
            // 缩到最小字号仍然折不下：宁可让气泡多长几行，也绝不把文字截断。
            amountPaint.setTextSize(size);
            boolean[] t2 = new boolean[1];
            best = TextWrap.wrap(amount, innerMax, 0, measurer, t2);
        }
        curAmountSize = size;
        lines.clear();
        lines.addAll(best);

        CharSequence lab = displayLabel(innerMax);
        float lw = lab.length() == 0 ? 0 : labelPaint.measureText(lab, 0, lab.length());
        float maxLine = 0;
        for (String l : lines) maxLine = Math.max(maxLine, amountPaint.measureText(l));
        float contentW = Math.max(Math.max(maxLine, lw), minW - padH * 2);
        targetW = contentW + padH * 2;
        boolean hasLabel = lab.length() > 0;
        int n = Math.max(1, lines.size());
        targetH = padT + (hasLabel ? lineH(labelPaint) + gap : 0)
                + n * lineH(amountPaint) + padB + tailH;
    }

    private CharSequence displayLabel(float innerMax) {
        if (labelPaint.measureText(label) <= innerMax) return label;
        return TextUtils.ellipsize(label, labelPaint, innerMax, TextUtils.TruncateAt.END);
    }

    private float lineH(Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    /** 宽高变化用 170ms 补间，避免切换文字时气泡生硬跳变。 */
    private void animateToTarget() {
        if (!laidOutOnce) {
            animW = targetW;
            animH = targetH;
            laidOutOnce = true;
            requestLayout();
            return;
        }
        final float fromW = animW, fromH = animH;
        if (Math.abs(fromW - targetW) < 0.5f && Math.abs(fromH - targetH) < 0.5f) return;
        if (sizeAnim != null) sizeAnim.cancel();
        sizeAnim = ValueAnimator.ofFloat(0f, 1f);
        sizeAnim.setDuration(170);
        sizeAnim.setInterpolator(new LinearInterpolator());
        final float toW = targetW, toH = targetH;
        sizeAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float f = (Float) a.getAnimatedValue();
                animW = fromW + (toW - fromW) * f;
                animH = fromH + (toH - fromH) * f;
                requestLayout();
                invalidate();
            }
        });
        sizeAnim.start();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        if (!laidOutOnce) measureContent(pendingSize > 0 ? pendingSize : baseAmountSize);
        // 补间动画进行中的 animW/animH 可能小于文字实际需要的尺寸。直接用它定尺寸，
        // 窗口就会比文字窄或矮，超出部分被裁掉——表现就是「气泡把文字吞了」。
        // 取「动画值」与「内容所需值」中的较大者，尺寸永不小于内容。
        int w = (int) Math.ceil(Math.max(animW, targetW));
        int h = (int) Math.ceil(Math.max(animH, targetH));
        setMeasuredDimension(resolveSize(w, wSpec), resolveSize(h, hSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float bodyH = Math.max(tailH + 8, h - tailH);
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

        CharSequence lab = displayLabel(w - padH * 2);
        boolean hasLabel = lab.length() > 0;
        float y = padT;
        if (hasLabel) {
            Paint.FontMetrics lfm = labelPaint.getFontMetrics();
            canvas.drawText(lab, 0, lab.length(),
                    (w - labelPaint.measureText(lab, 0, lab.length())) / 2f,
                    y - lfm.ascent, labelPaint);
            y += lineH(labelPaint) + gap;
        }
        Paint.FontMetrics afm = amountPaint.getFontMetrics();
        float lh = lineH(amountPaint);
        for (String l : lines) {
            float lw = amountPaint.measureText(l);
            canvas.drawText(l, (w - lw) / 2f, y - afm.ascent, amountPaint);
            y += lh;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (sizeAnim != null) sizeAnim.cancel();
        super.onDetachedFromWindow();
    }
}
