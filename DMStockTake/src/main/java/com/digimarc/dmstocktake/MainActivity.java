// - - - - - - - - - - - - - - - - - - - -
//
// Digimarc Confidential
// Copyright Digimarc Corporation, 2014-2020
//
// - - - - - - - - - - - - - - - - - - - -
package com.digimarc.dmstocktake;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.RectF;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.digimarc.capture.camera.CameraConfigurationListener;
import com.digimarc.capture.camera.CameraDataListener;
import com.digimarc.capture.camera.CameraErrorListener;
import com.digimarc.capture.camera.CameraHelper;
import com.digimarc.capture.camera.CameraNotifyListener;
import com.digimarc.capture.camera.CameraRegionListener;
import com.digimarc.capture.camera.CameraSurfaceView;
import com.digimarc.capture.camera.ImageData;
import com.digimarc.dms.payload.Payload;
import com.digimarc.dms.readers.BaseReader;
import com.digimarc.dms.readers.DataDictionary;
import com.digimarc.dms.readers.Manager;
import com.digimarc.dms.readers.ReaderException;
import com.digimarc.dms.readers.ReaderOptions;
import com.digimarc.dms.readers.ReaderResult;
import com.digimarc.dms.readers.ResultListener;
import com.digimarc.dms.readers.Utility;
import com.digimarc.dms.readers.image.DetectionRegion;
import com.digimarc.dms.readers.image.PreviewDetectionRegion;
import com.digimarc.dms.readers.image.VideoCaptureReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity
{
    private static final String[] DistanceOptions = {
            ReaderOptions.Distance_Near,
            ReaderOptions.Distance_Far,
            ReaderOptions.Distance_FullRange
    };

    private static final String Default_Distance = ReaderOptions.Distance_Far;

    private static final int REQUEST_PERMISSION = 1;
    private static final int REQUEST_PREFERENCES = 2;

    private MenuItem mTorchIcon;

    private boolean mCameraPermissionFailed = true;
    private boolean mStartedVideo = false;

    private boolean mMessageShown = false;

    private String mReadDistance = Default_Distance;

    private RegionView mRegionView;

    private CameraHelper mCamera;
    private int mOrientation;
    private int mCameraRotation;

    private CameraSurfaceView mCameraSurface;

    private VideoCaptureReader mCameraReader;

    private LocationView mLocationView;
    private final List<LocationView.DisplayData> mLocationData = new ArrayList<>();

    private final ReaderResultCache mCache = new ReaderResultCache();

    private final Paint mPaint = new Paint();

    private int mCurrentRegionIndex = 0;
    private final List<RectF> mRegions = Arrays.asList( new RectF( 0f, 0f, 1f, 1f),
                                                        new RectF( 0.1f, 0.33f, 0.9f, 0.66f ),
                                                        new RectF( 0.2f, 0.4f, 0.8f, 0.6f) );

    private final ResultListener mResultListener = new ResultListener()
    {
        @Override
        public void onReaderResult(@NonNull ReaderResult result, @NonNull BaseReader.ResultType resultType) {
            if ( resultType == BaseReader.ResultType.Image )
                parseImageResults( result );
        }

        @Override
        public void onError( @NonNull BaseReader.ReaderError errorCode,
                             @NonNull BaseReader.ResultType resultType )
        {
            showMsg( "Reader Error", Manager.getDescriptionForErrorCode(errorCode) );
        }
    };

    private final CameraDataListener mCameraListener = new CameraDataListener()
    {
        @Override
        public void onPreviewFrame( @NonNull ImageData data )
        {
            try
            {
                mCameraReader.processImageFrame(data);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    };

    private final CameraRegionListener mRegionListener = new CameraRegionListener()
    {
        @Override
        public void onVisibleRegionChanged()
        {
            Point pt = mCamera.getResolution();

            if ( pt != null )
            {
                // Camera Rotation is a constant value that is baked into every device. We are
                // going to cache the value now for reuse later.
                mCameraRotation = mCamera.getCameraRotation();

                RectF visible = mCamera.getRectForVisibleSurface();
                mLocationView.setScalingParameters(visible, pt, mCameraRotation, mOrientation);

                setReadRegion();
            }
        }
    };

    private final CameraConfigurationListener mConfigListener = new CameraConfigurationListener()
    {
        @NonNull
        @Override
        public Object onConfigureCamera( int cameraId, @NonNull Object parameters )
        {
            return Manager.getInstance().getRecommendedCameraParameters(cameraId, parameters);
        }

        @NonNull
        @Override
        public Point onConfigureResolution( int cameraId )
        {
            return Manager.getInstance().getRecommendedResolution(cameraId);
        }
    };

    private final CameraErrorListener mErrorListener = new CameraErrorListener()
    {
        @Override
        public void onError( @NonNull CameraHelper.CameraError error )
        {
            Toast.makeText( MainActivity.this, "Camera Error: " + error, Toast.LENGTH_LONG )
                    .show();
        }
    };


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if ( !PermissionActivity.haveRequiredPermissions( this ) )
        {
            Intent i = new Intent( this, PermissionActivity.class);

            startActivityForResult(i, REQUEST_PERMISSION);
        }

        loadDistanceSetting();

        mRegionView = findViewById( R.id.regionView );

        mLocationView = findViewById( R.id.locationView );
        if (mLocationView != null) {
            mLocationView.setOnClickListener( new View.OnClickListener()
            {
                @Override
                public void onClick( View view )
                {
                    mCamera.triggerCenterFocus();
                }
            } );
        }

        mCameraSurface = findViewById( R.id.surfaceView );

        //
        // Image initialization
        //

        WindowManager mgr = ((WindowManager) getSystemService(Context.WINDOW_SERVICE));

        if ( mgr != null )
            mOrientation = mgr.getDefaultDisplay().getRotation() * 90;

        startCamera();

        ReaderOptions options = getReaderOptions();

        try
        {
            int readerMask = BaseReader.buildSymbologyMask( BaseReader.ImageSymbology.Image_Digimarc,
                                                            BaseReader.ImageSymbology.Image_1D_UPCA,
                                                            BaseReader.ImageSymbology.Image_1D_UPCE,
                                                            BaseReader.ImageSymbology.Image_1D_EAN13,
                                                            BaseReader.ImageSymbology.Image_1D_EAN8,
                                                            BaseReader.ImageSymbology.Image_1D_Code128,
                                                            BaseReader.ImageSymbology.Image_1D_ITF_GTIN_14,
                                                            BaseReader.ImageSymbology.Image_1D_ITF_Variable );

            mCameraReader = VideoCaptureReader.Builder()
                    .setSymbologies( readerMask )
                    .setReaderOptions( options )
                    .setResultListener( mResultListener )
                    .build();
        }
        catch ( Exception e )
        {
            showMsg("Error", e.getMessage());
        }

        mPaint.setAntiAlias( true );
        mPaint.setColor( Color.RED );
        mPaint.setStyle( Paint.Style.STROKE );
        mPaint.setStrokeWidth( 3f );
        mPaint.setAlpha( 150 );
        mPaint.setStrokeJoin( Paint.Join.MITER );
    }

    @Override
    public boolean onCreateOptionsMenu( @NonNull Menu menu )
    {
        super.onCreateOptionsMenu( menu );

        getMenuInflater().inflate(R.menu.main, menu);

        mTorchIcon = menu.findItem( R.id.menu_torch );

        return true;
    }

    @Override
    public boolean onOptionsItemSelected( @NonNull MenuItem item )
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if ( id == R.id.menu_torch )
        {
            boolean torch = mCamera.isTorchOn();
            mCamera.setTorch( !torch );
            mTorchIcon.setIcon( torch ? R.drawable.ic_flash_off_light_24px : R.drawable.ic_flash_on_light_24px );
        }
        else if ( id == R.id.menu_settings )
        {
            startActivityForResult( new Intent( this, PreferenceActivity.class ),
                                    REQUEST_PREFERENCES );
        }
        else if ( id == R.id.menu_region )
        {
            mCurrentRegionIndex++;
            if ( mCurrentRegionIndex >= mRegions.size())
                mCurrentRegionIndex = 0;

            setReadRegion();
        }

        return super.onOptionsItemSelected( item );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        if ( requestCode == REQUEST_PERMISSION)
        {
            if ( !mStartedVideo )
                startCamera();
        }
        else if ( requestCode == REQUEST_PREFERENCES && data != null )
        {
            boolean changedDistance = data.getBooleanExtra( PreferenceActivity.CHANGED_DISTANCE,
                                                             false );

            // If the user changed the barcode read distance so we'll send new options to the reader
            if ( changedDistance )
            {
                loadDistanceSetting();

                try
                {
                    mCameraReader.setReaderOptions( getReaderOptions() );
                }
                catch ( ReaderException e )
                {
                    e.printStackTrace();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        if ( mCameraReader != null )
        {
            mCameraReader.release();
            mCameraReader = null;
        }

        if ( mCamera != null )
        {
            mCamera.release();
            mCamera = null;
        }

        super.onDestroy();
    }

    private void loadDistanceSetting()
    {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences( this );

        String distance = sharedPrefs.getString( PreferenceActivity.ENTRY_DISTANCE, "1" );

        try
        {
            mReadDistance = DistanceOptions[Integer.parseInt( distance )];
        }
        catch ( NumberFormatException e )
        {
            mReadDistance = Default_Distance;
        }

    }

    private void setReadRegion()
    {
        RectF frameReadRegion = new RectF(mRegions.get(mCurrentRegionIndex));
        mRegionView.setRegion( frameReadRegion );

        RectF visibleRegion = mCamera.getRectForVisibleSurface();

        // PreviewDetectionRegion picks up the camera & display rotation automatically
        DetectionRegion detectionRegion = PreviewDetectionRegion.Builder(visibleRegion)
                .setRegionOfInterest( frameReadRegion )
                .build();

        if ( mCameraReader != null )
            mCameraReader.setImageDetectionRegion(detectionRegion);
    }

    private void startCamera()
    {
        if ( ContextCompat.checkSelfPermission( this, "android.permission.CAMERA" )
                == PackageManager.PERMISSION_GRANTED )
        {
            mCamera = CameraHelper.Builder()
                    .setDataListener( mCameraListener )
                    .setConfigurationListener( mConfigListener )
                    .setErrorListener( mErrorListener )
                    .setNotifyListener( new CameraNotifyListener()
                    {
                        @Override
                        public void onCameraAvailable()
                        {
                            mTorchIcon.setVisible( mCamera.isTorchSupported() );
                        }
                    } )
                    .setRegionListener( mRegionListener )
                    .build();

            if ( mCameraPermissionFailed )
            {
                // If we did not have camera permission when the app started then this call
                // is needed to start camera capture. The CameraHelper library relies on getting
                // the SurfaceTexture's onSurfaceTextureAvailable call, and that notification
                // only gets sent on app startup. This call will resend the notification and get
                // capture started.
                mCameraSurface.notifySurfaceTextureAvailable();
                mCameraPermissionFailed = false;
            }

            mStartedVideo = true;
        }
        else
            mCameraPermissionFailed = true;
    }

    private ReaderOptions getReaderOptions()
    {
        ReaderOptions options = new ReaderOptions();

        options.setValue( ReaderOptions.TraditionalBarcodeReadDistance, mReadDistance );

        // Uncomment the following line if you need to read ITF Variable codes that are 8 digits
        // long. By default DMSDK will not read ITF Variable codes shorter than the SDK's default
        // minimum length (see the documentation for BaseReader.ImageSymbology.Image_1D_ITF_Variable
        // for more information).
//        options.setValue( ReaderOptions.ITF_BarcodeMinLength, 8 );

        return options;
    }

    /**
     * Parses the result data from an image reader, either synchronous or asynchronous. New read
     * results will be resolved and also displayed via a toast. The spinner will be shown during
     * resolving.
     * @param readerResult List of read results.
     */
    private void parseImageResults(ReaderResult readerResult)
    {
        Map<Payload, List<DataDictionary>> metadataForPayloads = readerResult.getMetadataForAllPayloads();

        // Set up the result cache for processing a new frame
        mCache.startFrame();

        mLocationData.clear();

        if ( metadataForPayloads != null )
        {
            // Step through read data and add payloads to result cache
            int rotation = (mCameraRotation - mOrientation + 360) % 360;

            for ( Map.Entry<Payload, List<DataDictionary>> entry : metadataForPayloads.entrySet() )
            {
                List<DataDictionary> metadataList = entry.getValue();

                for ( DataDictionary metadata : metadataList )
                {
                    mCache.addReadRecord( entry.getKey(), metadata, rotation );
                }
            }
        }

        mCache.endFrame();

        for ( ReaderResultCache.ReadData data : mCache.getCurrentResults() ) {

            Path path = Utility.convertRegionPointsToPath( data.getRegionPoints() );

            mLocationData.add( new LocationView.DisplayData( path, data.getPayload(),
                                                             getCenterPoint( data.getRegionPoints() ) ) );
        }

        mLocationView.setDisplayData( mLocationData );
    }

    private Point getCenterPoint( @NonNull List<Point> points )
    {
        int cx = 0;
        int cy = 0;

        int numberOfPoints = points.size();

        for (int i = 0; i < numberOfPoints; i++) {
            cx += points.get(i).x;
            cy += points.get(i).y;
        }

        cx /= numberOfPoints;
        cy /= numberOfPoints;

        return new Point(cx, cy);
    }

    // Show an error message. While the error dialog is on the screen all further errors will
    // be ignored.
    public void showMsg( @NonNull String title, String msg)
    {
        if ( msg == null )
            msg = "";

        try
        {
            if ( !mMessageShown )
            {
                mMessageShown = true;
                AlertDialog alertDialog = new AlertDialog.Builder( this )
                        .setTitle( title )
                        .setMessage( msg )
                        .setCancelable( false )
                        .setPositiveButton( "OK", new DialogInterface.OnClickListener()
                        {
                            public void onClick( DialogInterface dialog, int id )
                            {
                                mMessageShown = false;
                                if ( dialog != null )
                                {
                                    dialog.dismiss();
                                }
                            }
                        } )
                        .create();
                alertDialog.show();
            }
        }
        catch (Exception e)
        {
            //
        }
    }
}
