// - - - - - - - - - - - - - - - - - - - -
//
// Digimarc Confidential
// Copyright Digimarc Corporation, 2014-2017
//
// - - - - - - - - - - - - - - - - - - - -

package com.digimarc.dmsdemo.Utils;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.digimarc.dmsdemo.R;


public class ListenIcon
        extends FrameLayout
{
    private static final float Icon_Size_Factor = 0.50f;

    private static final int Icon_On = R.drawable.ic_mic_gray_24px;
    private static final int Icon_Off = R.drawable.ic_mic_off_gray_24px;

    private ImageView mIcon = null;
    private WaveView mWaveView = null;

    private boolean mState;

    private LayoutParams mIconParams;

    public interface IconClickListener
    {
        void onIconClick( boolean state );
    }

    private IconClickListener mListener;

    /**
     * The class attaches an animated microphone icon to a layout. The microphone is contained within
     * another layout that must be added to yours by including 'layout="@layout/visualizer_mic"'.
     * @param context         Context
     * @param attrs           Optional attributes
     */
    public ListenIcon( @NonNull Context context,
                       @Nullable AttributeSet attrs )
    {
        super( context, attrs );

        initialize( context, attrs );
    }

    private void initialize( Context context, @Nullable AttributeSet attrs )
    {
        mState = true;

        mIconParams = new LayoutParams( LayoutParams.MATCH_PARENT,
                                                    LayoutParams.MATCH_PARENT,
                                                    Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL );
        LayoutParams circleParams = new LayoutParams( LayoutParams.MATCH_PARENT,
                                                      LayoutParams.MATCH_PARENT,
                                                      Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL );

        mIcon = new ImageView( context, attrs );
        mIcon.setLayoutParams( mIconParams );
        mIcon.setImageResource( Icon_On );

        mWaveView = new WaveView( context, attrs );
        mWaveView.setLayoutParams( circleParams );

        addView( mIcon );
        addView( mWaveView );

        addOnLayoutChangeListener( new OnLayoutChangeListener()
        {
            @Override
            public void onLayoutChange( View view, int left, int right, int top, int bottom, int oldLeft, int oldRight,
                                        int oldTop, int oldBottom )
            {
                int width = view.getWidth();
                int height = view.getHeight();

                int newWidth = (int) ( (float) width * Icon_Size_Factor );
                int newHeight = (int) ( (float) height * Icon_Size_Factor );

                if ( newWidth != mIconParams.width || newHeight != mIconParams.height )
                {
                    mIconParams.width = (int) ( (float) width * Icon_Size_Factor );
                    mIconParams.height = (int) ( (float) height * Icon_Size_Factor );

                    mIcon.setLayoutParams( mIconParams );
                }
            }
        } );

        setOnClickListener( new OnClickListener()
        {
            @Override
            public void onClick( View view )
            {
                setState( !mState );

                if ( mListener != null )
                {
                    mListener.onIconClick( mState );
                }
            }
        } );
    }

    public void setIconClickListener( @Nullable IconClickListener listener )
    {
        mListener = listener;
    }

    public void setState( boolean state )
    {
        mState = state;

        mIcon.setImageResource( mState ? Icon_On : Icon_Off );
        mWaveView.setVisibility( mState ? View.VISIBLE : INVISIBLE );
    }
}
