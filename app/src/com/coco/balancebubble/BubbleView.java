package com.coco.balancebubble;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * 对话气泡：圆角矩形 + 尖角，尺寸完全跟着文字走。
 *
 * <p>排版交给 {@link BubbleLayout}（纯逻辑，可单测）：先按用户设定的基准字号折行，
 * 折不下就逐级缩字号，再折不下就让气泡长高，最后才截断。这里只负责三件事：
 * <ul>
 *   <li>把 {@link BubbleStyle} 里的尺寸换算成排版约束；</li>
 *   <li>把 {@link BubbleLayout.Result} 画出来（含透明度、自定义底色、投影、上/下尖角）；</li>
 *   <li>宽高变化时做一小段补间，避免切换文字时生硬跳变。</li>
 * </ul>
 *
 * <p><b>不变量：视图尺寸永远不小于内容所需尺寸。</b>动画中途的插值尺寸可能偏小，
 * 直接拿它去 measure，窗口就会比文字小、文字被裁掉（表现为「气泡把文字吞了」）。
 */
public class BubbleView extends View {

    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint amountPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final float density;
    private BubbleStyle style;
    private BubbleLayout.Spec spec;

    private String label = "";
    private String amount = "--";
    private boolean error = false;
    /** 服务端指定的相对缩放（1 = 用设置里的基准字号）。 */
    private float scale = 1f;

    private final BubbleLayout.Result layout = new BubbleLayout.Result();

    private float animW, animH, targetW, targetH;
    private ValueAnimator sizeAnim;
    private boolean laidOutOnce = false;

    /** 给 BubbleLayout 用的量字接口：全部走 amountPaint，字号由排版引擎指定。 */
    private final BubbleLayout.Metrics metrics = new BubbleLayout.Metrics() {
        @Override
        public void setSize(float px) {
            amountPaint.setTextSize(px);
        }

        @Override
        public float width(String text, int from, int to) {
            if (text == null || from >= to) return 0f;
            return amountPaint.measureText(text, from, to);
        }

        @Override
        public float lineHeight() {
            return BubbleView.this.lineHeight(amountPaint);
        }
    };

    public BubbleView(Context c) {
        this(c, BubbleStyle.from(c));
    }

    public BubbleView(Context c, BubbleStyle st) {
        super(c);
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        density = dm.density;
        amountPaint.setFakeBoldText(true);
        labelPaint.setFakeBoldText(false);
        applyStyle(st == null ? BubbleStyle.from(c) : st);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    private float dp(float v) {
        return v * density;
    }

    private float lineHeight(Paint p) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    // ---------------- 样式 ----------------

    /** 换一套外观（设置页改完调用）。会立刻重新排版。 */
    public void applyStyle(BubbleStyle st) {
        style = st;
        float screen = getResources().getDisplayMetrics().widthPixels;
        float cap = screen * 0.86f;
        if (style.maxWidth > cap) style.maxWidth = cap;
        if (style.minWidth > style.maxWidth) style.minWidth = style.maxWidth * 0.5f;
        spec = style.spec();
        // 把「相对缩放」折进基准字号，排版引擎自己会再往下缩
        BubbleLayout.Spec s = spec.copy();
        s.baseSize = Math.max(s.minSize, style.baseSize * scale);
        spec = s;
        labelPaint.setTextSize(style.labelSize);
        fillPaint.setColor(style.bg);
        fillPaint.setAlpha(style.alpha);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(1f, dp(1)));
        strokePaint.setColor(Theme.blend(style.bg, style.ink, 0.14f));
        strokePaint.setAlpha(style.alpha);
        if (style.shadow) {
            fillPaint.setShadowLayer(dp(4), 0, dp(1.5f), 0x38000000);
        } else {
            fillPaint.clearShadowLayer();
        }
        relayout();
        if (!laidOutOnce) {
            animW = targetW;
            animH = targetH;
            laidOutOnce = true;
        }
        requestLayout();
        invalidate();
    }

    public BubbleStyle style() {
        return style;
    }

    /** 气泡当前需要的高度（px），服务端排窗口时用。 */
    public float targetHeight() {
        return targetH;
    }

    public float targetWidth() {
        return targetW;
    }

    /**
     * 一句话说明当前排版算出来的结果，给设置页预览下面那行小字用。
     *
     * <p>和 {@link BubbleStyle} 里的静态参数不同：这里报的是实际渲染值，
     * 长文字自动缩小、行数封顶、宽度压到屏幕以内这些都会体现在里面。
     */
    public String describe() {
        if (style == null || layout.lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("实际 ").append(Math.round(layout.width / density)).append("dp 宽")
                .append(" · ").append(Math.round(layout.size / density)).append("sp 字号")
                .append(" · ").append(layout.lines.size()).append(" 行");
        if (layout.shrinkSteps > 0) {
            sb.append(" · 自动缩了 ").append(layout.shrinkSteps).append(" 档");
        }
        if (layout.truncated) {
            sb.append(" · 超过 ").append(style.hardMaxLines).append(" 行，已截断");
        }
        return sb.toString();
    }

    // ---------------- 内容 ----------------

    /** 设置内容。label 传空字符串表示只显示一行金额/文字。 */
    public void setData(String label, String amount, boolean error) {
        this.label = label == null ? "" : label;
        this.amount = (amount == null || amount.isEmpty()) ? "--" : amount;
        this.error = error;
        relayout();
        animateToTarget();
        invalidate();
    }

    /**
     * 指定这一句的相对字号（服务端按消息类型调用）：1 = 设置里的基准字号，
     * 传 0.7 表示这一句用七折。仍会按需自动缩小。
     */
    public void setAmountScale(float k) {
        scale = k <= 0f ? 1f : k;
        if (spec != null) {
            BubbleLayout.Spec s = style.spec();
            s.baseSize = Math.max(s.minSize, style.baseSize * scale);
            spec = s;
        }
        relayout();
        animateToTarget();
        invalidate();
    }

    /** 兼容旧接口：按像素指定字号。 */
    public void setAmountSize(float px) {
        if (style != null && style.baseSize > 0f) setAmountScale(px / style.baseSize);
    }

    // ---------------- 排版与绘制 ----------------

    private void relayout() {
        if (style == null) return;
        BubbleLayout.Result r = BubbleLayout.fit(label, amount, spec, metrics);
        layout.lines.clear();
        layout.lines.addAll(r.lines);
        layout.label = r.label;
        layout.size = r.size;
        layout.labelLineH = r.labelLineH;
        layout.lineH = r.lineH;
        layout.width = r.width;
        layout.height = r.height;
        layout.contentWidth = r.contentWidth;
        layout.truncated = r.truncated;
        layout.shrinkSteps = r.shrinkSteps;
        targetW = r.width;
        targetH = r.height;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        if (style == null) {
            setMeasuredDimension(resolveSize((int) dp(120), wSpec), resolveSize((int) dp(48), hSpec));
            return;
        }
        if (!laidOutOnce) {
            relayout();
            animW = targetW;
            animH = targetH;
            laidOutOnce = true;
        }
        int w = (int) Math.ceil(Math.max(animW, targetW));
        int h = (int) Math.ceil(Math.max(animH, targetH));
        setMeasuredDimension(resolveSize(w, wSpec), resolveSize(h, hSpec));
    }

    private void animateToTarget() {
        if (!laidOutOnce) {
            animW = targetW;
            animH = targetH;
            laidOutOnce = true;
            requestLayout();
            return;
        }
        if (Math.abs(animW - targetW) < 0.5f && Math.abs(animH - targetH) < 0.5f) {
            animW = targetW;
            animH = targetH;
            requestLayout();
            return;
        }
        final long dur = style == null ? 0L : style.animMs;
        if (sizeAnim != null) sizeAnim.cancel();
        if (dur <= 0) {
            animW = targetW;
            animH = targetH;
            requestLayout();
            invalidate();
            return;
        }
        final float fromW = animW, fromH = animH;
        final float toW = targetW, toH = targetH;
        sizeAnim = ValueAnimator.ofFloat(0f, 1f);
        sizeAnim.setDuration(dur);
        sizeAnim.setInterpolator(new DecelerateInterpolator());
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
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || style == null) return;
        final float tailH = style.tailH;
        final float bodyH = Math.max(tailH + 8f, h - tailH);
        final float bodyTop = style.tailUp ? tailH : 0f;
        final float bodyBot = style.tailUp ? h : bodyH;

        path.reset();
        RectF body = new RectF(0, bodyTop, w, bodyBot);
        path.addRoundRect(body, style.radius, style.radius, Path.Direction.CW);
        if (tailH > 0.5f) {
            float cx = tailCx(w);
            Path tail = new Path();
            if (style.tailUp) {
                tail.moveTo(cx - style.tailW / 2f, bodyTop + dp(3));
                tail.lineTo(cx, dp(1));
                tail.lineTo(cx + style.tailW / 2f, bodyTop + dp(3));
            } else {
                tail.moveTo(cx - style.tailW / 2f, bodyBot - dp(3));
                tail.lineTo(cx, h - dp(1));
                tail.lineTo(cx + style.tailW / 2f, bodyBot - dp(3));
            }
            tail.close();
            path.op(tail, Path.Op.UNION);
        }
        canvas.drawPath(path, fillPaint);
        if (style.border) {
            // 浅色气泡在浅色壁纸上会糊成一片，加一圈极淡的描边定形。
            canvas.drawPath(path, strokePaint);
        }

        // 文字在「身体」里居中，不把尖角算进去，否则字会偏心
        float y = bodyTop + style.padT;
        boolean hasLabel = layout.label.length() > 0;
        if (hasLabel) {
            labelPaint.setTextSize(style.labelSize);
            labelPaint.setColor(error ? style.errInk : style.subInk);
            labelPaint.setAlpha(style.alpha);
            float lw = labelPaint.measureText(layout.label);
            Paint.FontMetrics lfm = labelPaint.getFontMetrics();
            canvas.drawText(layout.label, (w - lw) / 2f, y - lfm.ascent, labelPaint);
            y += layout.labelLineH + style.gap;
        }
        amountPaint.setTextSize(layout.size);
        amountPaint.setColor(error ? style.errInk : style.ink);
        amountPaint.setAlpha(style.alpha);
        Paint.FontMetrics afm = amountPaint.getFontMetrics();
        for (String l : layout.lines) {
            float lw = amountPaint.measureText(l);
            canvas.drawText(l, (w - lw) / 2f, y - afm.ascent, amountPaint);
            y += layout.lineH;
        }
    }

    /** 尖角位置：贴在手写体中间略偏上，看起来像从角色头上冒出来的。 */
    private float tailCx(float w) {
        return w / 2f;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (sizeAnim != null) sizeAnim.cancel();
        super.onDetachedFromWindow();
    }
}
