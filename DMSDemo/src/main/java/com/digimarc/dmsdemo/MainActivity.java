// - - - - - - - - - - - - - - - - - - - -
//
// Digimarc Confidential
// Copyright Digimarc Corporation, 2014-2017
//
// - - - - - - - - - - - - - - - - - - - -
package com.digimarc.dmsdemo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.digimarc.capture.camera.CameraRegionListener;
import com.digimarc.dms.internal.ReaderOptionsInternal;
import com.digimarc.dms.readers.ReaderOptions;
import com.digimarc.dms.readers.image.DetectionRegion;
import com.digimarc.dms.readers.image.PreviewDetectionRegion;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.digimarc.capture.camera.CameraHelper;
import com.digimarc.capture.camera.CameraNotifyListener;
import com.digimarc.capture.camera.CameraSurfaceView;
import com.digimarc.dms.SdkSession;
import com.digimarc.dms.payload.Payload;
import com.digimarc.dms.readers.BaseReader;
import com.digimarc.dms.readers.Manager;
import com.digimarc.dms.readers.ReaderException;
import com.digimarc.dms.readers.ReaderResult;
import com.digimarc.dms.readers.ResultListener;
import com.digimarc.dms.readers.audio.AudioCaptureReader;
import com.digimarc.dms.readers.image.VideoCaptureReader;
import com.digimarc.dms.resolver.ContentItem;
import com.digimarc.dms.resolver.ResolveListener;
import com.digimarc.dms.resolver.ResolvedContent;
import com.digimarc.dms.resolver.Resolver;
import com.digimarc.dmsdemo.Utils.ListenIcon;

import java.util.List;

import static com.digimarc.capture.audio.AudioHelper.haveAudioPermission;
import static com.digimarc.capture.camera.CameraHelper.haveCameraPermission;

public class MainActivity
        extends AppCompatActivity
        implements OnClickListener
{
    private static final String TAG = "MainActivity";

    private static final int REQUEST_PREFERENCES = 2;

    private ProgressBar mSpinner = null;

    private SdkSession mSession = null;
    private Resolver mResolver = null;
    private VideoCaptureReader mCameraReader = null;
    private CameraHelper mCameraHelper = null;
    private CameraSurfaceView mCameraSurface = null;

    private ListenIcon mListenIcon;
    private ImageView mTorchIcon;

    private AudioCaptureReader mAudioReader = null;

    private MediaPlayer mMediaPlayer;

    private boolean mStartedAudio = false;
    private boolean mStartedVideo = false;

    private boolean mTorchAvailable = false;
    private boolean mTorchOn = false;

    private boolean mCameraPermissionFailed = false;

    private boolean mMessageShown = false;

    private final ResultListener mResultListener = new ResultListener()
    {
        @Override
        public void onReaderResult(@NonNull ReaderResult result,
                                   @NonNull BaseReader.ResultType resultType) {
            // Note that we are checking specifically for new payloads here. This is intended
            // to prevent the same code from reading twice in a row. To handle all reads
            // rather than just new ones change the lines below to use the getPayloads()
            // method instead.
            if (result.getNewPayloads() != null) {
                processPayloads(result.getNewPayloads());
            }
        }

        @Override
        public void onError( @NonNull BaseReader.ReaderError errorCode,
                             @NonNull BaseReader.ResultType resultType )
        {
            // We're handling network errors elsewhere by showing the no network icon, so we are
            // not going to display a popup when network errors come through.
            if ( !mMessageShown )
                showMsg( "Reader Error", Manager.getDescriptionForErrorCode(errorCode) );
        }
    };

    public void showMsg( @Nullable String title, @Nullable String msg)
    {
        try
        {
            if ((title != null) && !title.isEmpty() && (msg != null) && !msg.isEmpty())
            {
                Log.i(TAG, "Title: " + title + ", Msg: " + msg);

                mMessageShown = true;
                AlertDialog alertDialog = new AlertDialog.Builder(this)
                        .setTitle(title)
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener()
                         {
                             public void onClick(DialogInterface dialog, int id)
                             {
                                 mMessageShown = false;
                                 if (dialog != null)
                                 {
                                     dialog.dismiss();
                                 }
                             }
                         })
                        .create();
                alertDialog.show();
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "showMsg", e);
        }
    }

    ResolveListener mResolveListener = new ResolveListener() {
        @Override
        public void onPayloadResolved( @NonNull ResolvedContent result )
        {
            showSpinner( false );

            boolean shouldLaunch = ( result.getPayload().getSymbology() !=
                    BaseReader.AudioSymbology.Audio_Digimarc );
            ContentItem content = getHighestPriorityContentItem( result );

            if ( content != null )
            {
                if ( shouldLaunch )
                    launchContent( content.getContent() );
                else
                    showContentSnackBar( content );
            }
        }

        @Override
        public void onError( @Nullable Payload payload, @NonNull Resolver.ResolveError error )
        {
            String errorMsg = Manager.getDescriptionForErrorCode( error );

            showSpinner( false );

            showMsg( "Network Error", errorMsg );

            // Clear the payload cache since this resolve didn't succeed
            if (mCameraReader != null)
                mCameraReader.clearCache();
        }
    };

    private void logPayload(Payload payload)
    {
        try
        {
            if (payload != null)
            {
                String payloadID = payload.getPayloadString();
                if (payloadID.isEmpty())
                {
                    payloadID = "unknown";
                }

                String payloadType = "unknown";
                if ((payload.getSymbology().getBitmaskValue() & BaseReader.All_Audio_Readers) != 0)
                {
                    payloadType = "Audio";
                }
                else if (payload.getSymbology() == BaseReader.ImageSymbology.Image_Digimarc)
                {
                    payloadType = "Image";
                }
                else if (payload.getSymbology() == BaseReader.ImageSymbology.Image_QRCode)
                {
                    payloadType = "QRCode";
                }
                else if ((payload.getSymbology().getBitmaskValue() & BaseReader.All_Barcode_Readers) != 0)
                {
                    payloadType = "Barcode";
                }
                Log.i(TAG,
                      "logPayload\r\n    Payload ID: " + payloadID +
                              "\r\n    Payload Type: " + payloadType);
            }
        }
        catch (Exception e)
        {
            Log.e(TAG, "logPayload", e);
        }
    }

    private class SnackBarListener implements View.OnClickListener
    {
        private final ContentItem mContent;

        SnackBarListener( ContentItem content )
        {
            mContent = content;
        }

        @Override
        public void onClick( View v )
        {
            if ( mContent != null )
                launchContent( mContent.getContent() );
        }
    }

    private final CameraNotifyListener mCameraNotify = new CameraNotifyListener()
    {
        @Override
        public void onCameraAvailable()
        {
            mTorchAvailable = mCameraHelper.isTorchSupported();

            mTorchIcon.setVisibility( mTorchAvailable ? View.VISIBLE : View.GONE );

            if ( mTorchAvailable )
                mCameraHelper.setTorch( mTorchOn );
        }
    };

    // The code in this callback will limit image detection to the visible area of the camera frame.
    private final CameraRegionListener mCameraRegion = new CameraRegionListener()
    {
        @Override
        public void onVisibleRegionChanged()
        {
            RectF visibleRegion = mCameraHelper.getRectForVisibleSurface();

            DetectionRegion detectionRegion = PreviewDetectionRegion.Builder( visibleRegion)
                    .build();

            if ( mCameraReader != null )
                mCameraReader.setImageDetectionRegion(detectionRegion);
        }
    };

    @Override
    public void onCreate( @Nullable Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );

        // Make sure we use vector drawables
        AppCompatDelegate.setCompatVectorFromResourcesEnabled( true);

        boolean cameraPermissionGranted = haveCameraPermission();
        boolean audioPermissionGranted  = haveAudioPermission();

        if ( !cameraPermissionGranted || !audioPermissionGranted )
            startActivity( new Intent( this, PermissionActivity.class) );

        try
        {
            Log.i( TAG, "onCreate" );

            setContentView( R.layout.main );

            // make sure we have a camera
            if ( cameraPermissionGranted )
                alertAbsenceOfCamera();

            mSpinner = findViewById( R.id.spinner );
            if ( mSpinner != null )
            {
                mSpinner.bringToFront();
            }

            ImageButton settingsButton = findViewById( R.id.settingsButton );
            if ( settingsButton != null )
            {
                settingsButton.setOnClickListener( this );
            }

            mCameraSurface = findViewById( R.id.cameraView ) ;
            if (mCameraSurface != null)
            {
                mCameraSurface.setOnClickListener( new OnClickListener()
                {
                    @Override
                    public void onClick( View view )
                    {
                        if (haveCameraPermission())
                            mCameraHelper.triggerCenterFocus();
                    }
                });
            }

            mTorchIcon = findViewById( R.id.torchButton );
            mTorchIcon.setOnClickListener( new OnClickListener()
            {
                @Override
                public void onClick( View v )
                {
                    if ( mTorchAvailable )
                    {
                        mTorchOn = !mTorchOn;

                        mCameraHelper.setTorch( mTorchOn );

                        mTorchIcon.setImageResource( mTorchOn ? R.drawable.ic_flash_on_gray_24px
                                                             : R.drawable.ic_flash_off_gray_24px );
                    }
                }
            } );

            mListenIcon = findViewById( R.id.listenIcon );
            mListenIcon.setIconClickListener( new ListenIcon.IconClickListener()
            {
                @Override
                public void onIconClick( boolean state )
                {
                    boolean useAudio = getReaderSetting( PreferenceActivity.ENTRY_AUDIO, true );

                    useAudio = !useAudio;
                    setReaderSetting( PreferenceActivity.ENTRY_AUDIO, useAudio );

                    constructAudioReader();

                    if ( useAudio && mAudioReader != null )
                        mAudioReader.start();
                }
            } );

            boolean useAudio = getReaderSetting( PreferenceActivity.ENTRY_AUDIO, true );
            mListenIcon.setState( useAudio );

            mMediaPlayer = new MediaPlayer();
            AssetFileDescriptor rawFileDescriptor = getApplicationContext().getResources()
                    .openRawResourceFd(R.raw.ding);
            mMediaPlayer.setDataSource(rawFileDescriptor.getFileDescriptor(),
                                       rawFileDescriptor.getStartOffset(),
                                       rawFileDescriptor.getLength());
            mMediaPlayer.prepareAsync();
            rawFileDescriptor.close();

            // Initialize DM SDK
            mSession = SdkSession.Builder()
                    .build();

            // Reader and resolver objects are being constructed separately. In many apps
            // this wouldn't be necessary, but since we allow readers to be enabled &
            // disabled in the settings menu we've moved the code to separate, reusable, methods.
            constructImageReader();
            constructResolver();
        }
        catch ( Exception e )
        {
            Log.e( TAG, "onCreate", e );
        }
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        // We want to make sure that the spinner isn't visible when we come back to the main view
        showSpinner( false );

        // It's possible for the user to grant us audio and/or camera permission while the app
        // was in the background. These checks allows us to properly start media capture when the
        // app resumes.

        if ( !mStartedAudio )
            constructAudioReader();

        if ( !mStartedVideo )
            constructVideoCapture();

        if ( mAudioReader != null )
            mAudioReader.start();

        if (mCameraReader != null)
            mCameraReader.clearCache();

        if (mAudioReader != null)
            mAudioReader.clearCache();

        if (mResolver != null) {
            mResolver.start();
        }
    }

    @Override
    protected void onPause()
    {
        if ( mAudioReader != null )
            mAudioReader.stop();

        if (mResolver != null) {
            mResolver.stop();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        if ( mCameraReader != null )
        {
            mCameraReader.release();
            mCameraReader = null;
        }
        if ( mAudioReader != null )
        {
            mAudioReader.release();
            mAudioReader = null;
        }

        if ( mResolver != null )
        {
            mResolver.release();
            mResolver = null;
        }

        if ( mMediaPlayer != null )
        {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }

        if ( mCameraHelper != null )
        {
            mCameraHelper.release();
            mCameraHelper = null;
        }

        super.onDestroy();
    }

    @Override
    public void onClick( @NonNull View view )
    {
        try
        {
            if ( view.getId() == R.id.settingsButton )
            {
                startActivityForResult( new Intent( this, PreferenceActivity.class ),
                                        REQUEST_PREFERENCES );
            }
        }
        catch ( Exception e )
        {
            Log.e( TAG, "onClick", e );
        }
    }

    private boolean getReaderSetting( String name, boolean defaultValue )
    {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences( this );
        boolean value;

        value = sharedPrefs.getBoolean( name, defaultValue );

        return value;
    }

    private void setReaderSetting( String name, boolean value )
    {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences( this );

        SharedPreferences.Editor edit = sharedPrefs.edit();
        edit.putBoolean( name, value );
        edit.apply();
    }

    /**
     * This method will be called on return from the DMSPreferencesActivity. If preference changes
     * were made then we'll recreate/reconfigure our objects as needed.
     */
    @Override
    protected void onActivityResult( int requestCode, int resultCode, @Nullable Intent data )
    {
        if ( requestCode == REQUEST_PREFERENCES && data != null )
        {
            boolean changedSymbology = data.getBooleanExtra( PreferenceActivity.CHANGED_SYMBOLOGY,
                                                             false );

            // If the user changed which symbologies to use we'll need to recreate our
            // reader objects.
            if ( changedSymbology )
            {
                updateImageSymbologies();
                constructAudioReader();
            }
        }
        else
        {
            super.onActivityResult( requestCode, resultCode, data );
        }
    }

    private void constructVideoCapture()
    {
        // We are always going to use the camera in this app, so we'll create the CameraHelper object here.
        if ( ContextCompat.checkSelfPermission( this, "android.permission.CAMERA" )
                == PackageManager.PERMISSION_GRANTED )
        {
            mCameraHelper = CameraHelper.Builder()
                    .setNotifyListener( mCameraNotify )
                    .setRegionListener( mCameraRegion )
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

            if (mCameraReader != null) {
                mCameraReader.attachCamera(mCameraHelper);
            }

            mStartedVideo = true;
        }
        else
            mCameraPermissionFailed = true;
    }

    /**
     * This method creates and configures a VideoCaptureReader object based on the settings
     * found in the app's preferences. The "ImageDigimarc" and "ImageBarcode" preference
     * values are queried and used to build a symbology mask with the proper decoders selected
     * for this session. If a VideoCaptureReader object already exists it will be reused
     * unless the selected symbologies have changed.
     */
    private void constructImageReader()
    {
        // Digimarc Barcode reader is always enabled
        int imageSymbologies = BaseReader.buildSymbologyMask( BaseReader.ImageSymbology.Image_Digimarc );

        // If barcode reading is enabled, add all barcode symbologies to symbology bitmask
        if ( getReaderSetting( PreferenceActivity.ENTRY_BARCODE, true ) )
            imageSymbologies |= BaseReader.All_Barcode_Readers;

        if ( mCameraReader != null )
        {
            // The requested symbologies have changed so we'll clean up our existing reader before
            // we create a new one.
            mCameraReader.release();
            mCameraReader = null;
        }

        if ( imageSymbologies != 0 )
        {
            try
            {
                mCameraReader = VideoCaptureReader.Builder()
                        .setSdkSession( mSession )
                        .setSymbologies( imageSymbologies )
                        .setResultListener( mResultListener )
                        .setCameraHelper( mCameraHelper )
                        .build();
            }
            catch ( ReaderException e )
            {
                showMsg("Error", e.getMessage());
            }
        }
    }

    /**
     * This method creates and configures a AudioCaptureReader object based on the settings found
     * in the app's preferences.  If a AudioCaptureReader object already exists it will be reused
     * unless the selected symbologies have changed. If no audio symbologies are requested then
     * existing objects will be cleaned up before returning.
     */
    private void constructAudioReader()
    {
        boolean useAudio = false;

        if ( ContextCompat.checkSelfPermission( this, "android.permission.RECORD_AUDIO" )
                == PackageManager.PERMISSION_GRANTED )
        {
            int audioSymbologies = 0;
            if (getReaderSetting( PreferenceActivity.ENTRY_AUDIO, true ))
            {
                audioSymbologies |= BaseReader.All_Audio_Readers;
                useAudio = true;
            }

            if ( mAudioReader != null && audioSymbologies != mAudioReader.getSymbologies() )
            {
                // The requested symbologies have changed so we'll clean up our existing
                // reader before we create a new one.
                mAudioReader.release();
                mAudioReader = null;
            }

            if ( audioSymbologies != 0 )
            {
                try
                {
                    mAudioReader = AudioCaptureReader.Builder()
                            .setSdkSession( mSession )
                            .setSymbologies( audioSymbologies )
                            .setResultListener( mResultListener )
                            .build();
                }
                catch ( ReaderException e )
                {
                    e.printStackTrace();
                }
            }
            else
            {
                // No audio symbologies requested so we'll clean up any allocated objects
                if ( mAudioReader != null )
                {
                    mAudioReader.release();
                    mAudioReader = null;
                }
            }

            mStartedAudio = true;
        }

        mListenIcon.setState( useAudio );
    }

    private void updateImageSymbologies()
    {
        // Digimarc Barcode reader is always enabled
        int imageSymbologies = BaseReader.buildSymbologyMask( BaseReader.ImageSymbology.Image_Digimarc );

        // If barcode reading is enabled, add all barcode symbologies to symbology bitmask
        if ( getReaderSetting( PreferenceActivity.ENTRY_BARCODE, true ) )
            imageSymbologies |= BaseReader.All_Barcode_Readers;

        try
        {
            if ( imageSymbologies != 0 && mCameraReader != null )
                mCameraReader.setSymbologies( imageSymbologies );
        }
        catch ( ReaderException e )
        {
            e.printStackTrace();
        }
    }

    /**
     * This method constructs a Resolver object using the built in credentials along with
     * the resolver URL specified in preferences. If no URL is present then the default
     * (production) resolver is used.
     */
    private void constructResolver()
    {
        if ( mResolver != null )
        {
            mResolver.release();
            mResolver = null;
        }

        try {
            mResolver = Resolver.Builder()
                    .setSdkSession( mSession )
                    .setResultListener(mResolveListener)
                    .build();
        } catch (ReaderException e) {
            e.printStackTrace();
        }

    }

    private void processPayloads(List<Payload> payloads)
    {
        for ( Payload payload : payloads ) {
            logPayload(payload);

            if ( mMediaPlayer != null )
                mMediaPlayer.start();

            // QR codes are a special case. We'll launch them here rather than passing
            // them to the resolver.
            String qrData =
                    (String) payload.getRepresentation( Payload.BasicRepresentation.QRCode );

            if ( qrData != null ) {
                launchContent( qrData );
            } else {
                mResolver.resolve( payload );

                showSpinner( true );
            }
        }
    }

    /**
     * Internal method that touches the UI controls to set the visibility of the
     * spinner and crosshairs. This method runs on the UI thread and should only be
     * called by the Handler object, not from user app methods.
     *
     * @param show Should the spinner be shown.
     */
    @UiThread
    private void showSpinner( boolean show )
    {
        mSpinner.setVisibility( show ? View.VISIBLE : View.INVISIBLE );
    }

    /**
     * If a resolve result contains multiple content items we always want to launch
     * the most important of those. At this point we only looking at whether the item
     * is for a SmartLabel link or not, but in the future this could be expanded to
     * support more levels.
     * @param resolvedContent    The ResolvedContent received from the Digimarc Resolver.
     * @return The most important content item within the resolve data. This will be the SmartLabel
     * content link if the data contains one, otherwise it will be the first content item found.
     */
    private ContentItem getHighestPriorityContentItem( @NonNull ResolvedContent resolvedContent )
    {
        ContentItem content = resolvedContent.getContentItems().get( 0 );

        for ( ContentItem next : resolvedContent.getContentItems() )
        {
            if ( next.getCategory() == ContentItem.Category.SmartLabel )
            {
                content = next;
                break;
            }
        }

        return content;
    }

    /**
     * @param content     Content string received from the resolver.
     */
    private void launchContent( @NonNull String content )
    {
        try
        {
            Intent intent = new Intent( Intent.ACTION_VIEW, Uri.parse(content).normalizeScheme() );
            startActivity( intent);
        }
        catch ( Exception e )
        {
            showMsg( "Error", "Unable to launch content data." );
        }
    }

    /**
     * Notify user of absence of camera.  Camera device is necessary to run this app.
     */
   private void alertAbsenceOfCamera()
    {
        boolean hasCamera;

        try
        {
            hasCamera = CameraHelper.deviceHasCamera();
        }
        catch ( SecurityException e )
        {
            hasCamera = false;
        }

        if ( !hasCamera )
        {
            AlertDialog alert = new AlertDialog.Builder( this )
                    .setTitle( "Camera Detection" )
                    .setMessage("No camera detected, aborting..." )
                    .setPositiveButton( "OK", new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick( DialogInterface dialog, int which )
                        {
                            finish(); // abort gracefully
                        }
                    } )
                    .create();
            alert.show();
        }
    }

    private void showContentSnackBar( ContentItem content )
    {
        Snackbar snackbar = Snackbar.make( findViewById( R.id.camera_layout ),
                                           content.getTitle(),
                                           Snackbar.LENGTH_LONG );
        snackbar.setAction( R.string.snackbar_action, new SnackBarListener( content ) );
        snackbar.show();
    }
}
