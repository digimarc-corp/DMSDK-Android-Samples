// - - - - - - - - - - - - - - - - - - - -
//
// Digimarc Confidential
// Copyright Digimarc Corporation, 2014-2016
//
// - - - - - - - - - - - - - - - - - - - -
package com.digimarc.dmsdemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionActivity
        extends Activity
{
    private static final int Permission_Code = 1234;

    private int mCurrentIndex = 0;

    private class PermissionPrompt
    {
        final String mPermission;
        final int mPromptRes;

        PermissionPrompt( String permission, int promptRes )
        {
            mPermission = permission;
            mPromptRes = promptRes;
        }
    }

    private final PermissionPrompt mRequiredPermissions[] = {
            new PermissionPrompt( "android.permission.CAMERA", R.string.prompt_camera ),
            new PermissionPrompt( "android.permission.RECORD_AUDIO", R.string.prompt_audio ) };

    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );

        processNextPermission();
    }

    /**
     * This method handles the meat of this activity. For each permission the app requires we'll check
     * whether we have the permission or not. Note that this is just for so-called Dangerous permissions.
     * Things like accessing the Internet and checking Network State don't need to be prompted for. If
     * we don't have the required permission then we'll display a dialog containing our rationale for
     * requesting the permission and then ask for the permission. When the system's permission request
     * is complete we'll come back to this method to process the next one. When all permissions have
     * been processed we'll launch the app's main activity.
     */
    private void processNextPermission()
    {
        boolean permissionLaunched = false;

        while ( mCurrentIndex < mRequiredPermissions.length && !permissionLaunched )
        {
            PermissionPrompt next = mRequiredPermissions[ mCurrentIndex ];
            final String permission[] = { next.mPermission };

            if ( ContextCompat.checkSelfPermission( this, next.mPermission ) != PackageManager.PERMISSION_GRANTED )
            {
                if ( ActivityCompat.shouldShowRequestPermissionRationale( this, next.mPermission ))
                {
                    AlertDialog dlg = new AlertDialog.Builder(this)
                            .setCancelable(false)
                            .setTitle(R.string.prompt_title)
                            .setMessage(next.mPromptRes)
                            .setPositiveButton( "OK", new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    requestPermission(permission);
                                }
                            })
                            .create();
                    dlg.show();
                }
                else
                    requestPermission( permission );

                permissionLaunched = true;
            }

            mCurrentIndex++;
        }

        if (!permissionLaunched)
            finish();
    }


    private void requestPermission( String[] permission )
    {
        ActivityCompat.requestPermissions( this,
                                           permission,
                                           Permission_Code );
    }

    /**
     * This method will be called by the system when the requestPermissions() operation is complete. We
     * don't really care about the specifics of the result here, so we'll just move on to process the next
     * permission.
     * @param requestCode   The request code we passed in.
     * @param permissions   List of permissions.
     * @param grantResults  List of results.
     */
    public void onRequestPermissionsResult( int requestCode, @NonNull  String permissions[], @NonNull int[] grantResults)
    {
        processNextPermission();
    }
}
