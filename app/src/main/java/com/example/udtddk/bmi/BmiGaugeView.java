package com.example.udtddk.bmi;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class BmiGaugeView extends View {

    private static final int CLR_THIN       = 0xFF5BBFFA;
    private static final int CLR_NORMAL     = 0xFF2E8B57;
    private static final int CLR_OVERWEIGHT = 0xFFFFC107;
    private static final int CLR_OBESE      = 0xFFE53935;

    private static final float[] ZONE_SWEEPS = {45f, 60f, 45f, 30f};
    private static final int[] ZONE_COLORS = {CLR_THIN, CLR_NORMAL, CLR_OVERWEIGHT, CLR_OBESE};

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private float bmi = 0f;
    private float animBmi = 0f;

    public BmiGaugeView(Context ctx) { super(ctx); init(); }
    public BmiGaugeView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }
    public BmiGaugeView(Context ctx, AttributeSet attrs, int defStyle) { super(ctx, attrs, defStyle); init(); }

    private void init() {
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.BUTT);

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
        needlePaint.setColor(0xFF0F2D1F);

        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(0xFFFFFFFF);
    }

    public void setBmi(float newBmi) {
        bmi = newBmi;
        ValueAnimator anim = ValueAnimator.ofFloat(animBmi, newBmi);
        anim.setDuration(900);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            animBmi = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float cy = h * 0.82f;
        float strokeW = w * 0.085f;
        float radius = w * 0.38f;

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        arcPaint.setStrokeWidth(strokeW);

        float startAngle = 180f;
        for (int i = 0; i < ZONE_SWEEPS.length; i++) {
            arcPaint.setColor(ZONE_COLORS[i]);
            canvas.drawArc(arcRect, startAngle, ZONE_SWEEPS[i], false, arcPaint);
            startAngle += ZONE_SWEEPS[i];
        }

        float angle = bmiToAngle(animBmi);
        float radA = (float) Math.toRadians(angle);
        float needleLen = radius - strokeW * 0.5f;
        float needleBase = strokeW * 0.35f;
        float nx = cx + (float)Math.cos(radA) * needleLen;
        float ny = cy + (float)Math.sin(radA) * needleLen;

        needlePaint.setStrokeWidth(6f);
        canvas.drawLine(cx, cy, nx, ny, needlePaint);

        centerPaint.setColor(0xFF0F2D1F);
        canvas.drawCircle(cx, cy, needleBase * 1.8f, centerPaint);
        centerPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, cy, needleBase, centerPaint);
    }

    private float bmiToAngle(float b) {
        float MIN_BMI = 10f, MAX_BMI = 40f;
        float clamped = Math.max(MIN_BMI, Math.min(MAX_BMI, b));
        float ratio = (clamped - MIN_BMI) / (MAX_BMI - MIN_BMI);
        return 180f + ratio * 180f;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, (int)(w * 0.55f));
    }
}