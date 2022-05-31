// - - - - - - - - - - - - - - - - - - - -
//
// Digimarc Confidential
// Copyright Digimarc Corporation, 2014-2017
//
// - - - - - - - - - - - - - - - - - - - -

package com.digimarc.dmstocktake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RegionView
        extends View
{
    private static final int Corner_Radius = 16;

    private Paint mPaint;
    private RectF mViewRect;
    private RectF mRegion;
    private float mAlpha = 0.4f;
    private float mRadius;
    private boolean mRegionActive;

    public RegionView( @NonNull Context context )
    {
        super( context );
        initObjects();
    }

    public RegionView( @NonNull Context context, @Nullable AttributeSet attrs )
    {
        super( context, attrs );
        initObjects();
    }

    public RegionView( @NonNull Context context, @Nullable AttributeSet attrs, int defStyle )
    {
        super( context, attrs, defStyle );
        initObjects();
    }

    private void initObjects()
    {
        final float scale = getContext().getResources().getDisplayMetrics().density;
        mRadius = ( scale * Corner_Radius );
        mPaint = new Paint();
        mPaint.setColor( Color.TRANSPARENT );
        mPaint.setAlpha( 0 );
        mPaint.setXfermode( new PorterDuffXfermode( PorterDuff.Mode.CLEAR ) );

        mViewRect = new RectF();
        mRegion = new RectF(0.0f, 0.0f, 1.0f, 1.0f);
        setBackgroundColor(Color.BLACK);
        setAlpha( 0 );
        mRegionActive = false;
    }

    public void setRegion( @NonNull RectF rect )
    {
        mRegion = rect;
        calculateDisplayRegion();
        updateDisplay();
        invalidate();
    }

    public void setRegionAlpha( float alpha )
    {
        mAlpha = alpha;
        updateDisplay();
    }

    private void updateDisplay()
    {
        mRegionActive = (mRegion.width() < 1.0 || mRegion.height() < 1.0);

        setAlpha( mRegionActive ? mAlpha : 0 );
    }

    private void calculateDisplayRegion() {
        int width = getWidth();
        int height = getHeight();

        mViewRect.left = width * mRegion.left;
        mViewRect.top = height * mRegion.top;
        mViewRect.right = width * mRegion.right;
        mViewRect.bottom = height * mRegion.bottom;
    }

    @Override
    protected void onLayout( boolean changed, int left, int top, int right, int bottom )
    {
        super.onLayout( changed, left, top, right, bottom );

        calculateDisplayRegion();
    }

    @Override
    protected void onDraw( @NonNull Canvas canvas )
    {
        super.onDraw( canvas );

        if ( mRegionActive )
            canvas.drawRoundRect( mViewRect, mRadius, mRadius, mPaint );
    }
}
