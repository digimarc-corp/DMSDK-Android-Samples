// - - - - - - - - - - - - - - - - - - - -
//
// Digimarc Confidential
// Copyright Digimarc Corporation, 2014-2020
//
// - - - - - - - - - - - - - - - - - - - -
package com.digimarc.dmstocktake;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.digimarc.dms.payload.Payload;
import com.digimarc.dms.payload.RawPayload;

import java.util.List;

public class LocationView
        extends View
{
    private static final String TAG = "LocationView";

    private static final String Test_Value = "12345";

    static class DisplayData
    {
        Path mPath;
        Payload mValue;
        Point mPoint;

        /**
         * DisplayData wraps the information required to draw a code's region and value
         * on the screen.
         * @param path      Path for the region
         * @param value     Value to display
         * @param point     Center point of the path. The value will be centered on this.
         */
        DisplayData( Path path, @Nullable Payload value, Point point )
        {
            mPath = path;
            mValue = value;
            mPoint = point;
        }
    }

    private final Paint mRectPaint = new Paint();
    private final Paint mTextPaint = new Paint();
    private List<DisplayData> mDataList;
    private final Matrix mTransform = new Matrix();
    private RectF mImageBounds;
    private Point mFrameSize;
    private int mCameraRotation;
    private int mOrientation;
    private int mTextOffset;

    public LocationView( @NonNull Context context, @Nullable AttributeSet attrs )
    {
        super( context, attrs );

        mRectPaint.setAntiAlias( true );
        mRectPaint.setColor( Color.RED );
        mRectPaint.setAlpha( 0xc0 );
        mRectPaint.setStyle( Paint.Style.FILL );

        mTextPaint.setAntiAlias( true );
        mTextPaint.setColor( Color.WHITE );
        mTextPaint.setAlpha( 0xc0 );
        mTextPaint.setTextAlign( Paint.Align.CENTER );
        mTextPaint.setTextSize( 40 );

        setWillNotDraw( false );

        Rect boundsRect = new Rect();

        // We want to draw the barcode value centered horizontally and vertically on the region.
        // Our paint handles the horizontal alignment, but we need to offset the value vertically.
        mTextPaint.getTextBounds( Test_Value, 0, Test_Value.length(), boundsRect );
        mTextOffset = ( boundsRect.bottom - boundsRect.top ) / 2;

        mOrientation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRotation() * 90;
    }

    public void setScalingParameters( @NonNull RectF rect,
                                      @NonNull Point pt,
                                      int cameraRotation,
                                      int deviceOrientation )
    {
        mTransform.reset();
        mImageBounds = rect;
        mFrameSize = pt;
        mCameraRotation = cameraRotation;
        mOrientation = deviceOrientation;
    }

    public void setDisplayData( @Nullable List<DisplayData> pathList )
    {
        mDataList = pathList;
        invalidate();
    }

    @Override
    protected void onDraw( @NonNull Canvas canvas )
    {
        try
        {
            if ( mDataList != null )
                drawBcLocation( canvas );

            super.onDraw( canvas );
        }
        catch ( Exception e )
        {
            Log.i( TAG, "LocationView.onDraw", e );
        }
    }

    private void drawBcLocation( @NonNull Canvas canvas )
    {
        try
        {
            if ( mDataList != null ) {
                if ( mTransform.isIdentity() && mImageBounds != null )
                {
                    boolean rotatedCamera = mCameraRotation == 90 || mCameraRotation == 270;
                    boolean rotatedDevice = mOrientation == 90 || mOrientation == 270;

                    int surfaceWidth = canvas.getWidth();
                    int surfaceHeight = canvas.getHeight();

                    // If the camera image is rotated vs the display (this is the case on the majority of handsets)
                    // we need to swap the dimensions for our calculations.
                    float frameWidth = ( rotatedCamera == rotatedDevice ) ? mFrameSize.x : mFrameSize.y;
                    float frameHeight = ( rotatedCamera == rotatedDevice ) ? mFrameSize.y : mFrameSize.x;

                    float viewableFrameWidth = mImageBounds.width() * frameWidth;
                    float viewableFrameHeight = mImageBounds.height() * frameHeight;

                    float offsetX = ( frameWidth - viewableFrameWidth ) / 2f;
                    float offsetY = ( frameHeight - viewableFrameHeight ) / 2f;

                    float viewScaleX = surfaceWidth / viewableFrameWidth;
                    float viewScaleY = surfaceHeight / viewableFrameHeight;

                    mTransform.setTranslate( -offsetX, -offsetY );
                    mTransform.postScale( viewScaleX, viewScaleY );
                }

                for (DisplayData data : mDataList) {
                    data.mPath.transform( mTransform );
                    canvas.drawPath(data.mPath, mRectPaint);

                    RawPayload payload = new RawPayload( data.mValue );

                    float[] pts = new float[]{data.mPoint.x, data.mPoint.y + mTextOffset};
                    mTransform.mapPoints( pts );

                    canvas.drawText( payload.getBaseValue(), pts[0], pts[1], mTextPaint );
                }
            }
        }
        catch ( Exception e )
        {
            Log.i( TAG, "LocationView", e );
        }
    }
}
