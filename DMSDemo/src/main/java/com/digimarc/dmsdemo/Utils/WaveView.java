package com.digimarc.dmsdemo.Utils;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.digimarc.dmsdemo.R;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class WaveView extends View {

    private static final long ANIMATION_DURATION = 2500L;

    private final Paint mPaint;
    private final PointF mCenter;
    private float mRadius;
    private float mMaxRadius;
    private Animator mAnimator;

    public WaveView( @NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(context.getResources().getColor(R.color.colorIcon));
        mPaint.setStrokeWidth(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.0f, context.getResources().getDisplayMetrics()));
        mPaint.setStyle(Paint.Style.STROKE);
        mCenter = new PointF();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }

        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mCenter.set(w / 2.0f, h / 2.0f);
        mMaxRadius = (Math.min(w, h) / 2f) - mPaint.getStrokeWidth();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, mMaxRadius);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                updateWave(value);
            }
        });
        animator.setDuration(ANIMATION_DURATION);
        animator.setRepeatMode(ObjectAnimator.RESTART);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();
        mAnimator = animator;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(mCenter.x, mCenter.y, mRadius, mPaint);
    }

    private void updateWave(float value) {
        mRadius = value;
        float progress = (float) Math.pow(mRadius / mMaxRadius, 2);
        int alpha = (int) (255 - (progress * 255));
        mPaint.setAlpha(alpha);
        postInvalidateOnAnimation();
    }
}