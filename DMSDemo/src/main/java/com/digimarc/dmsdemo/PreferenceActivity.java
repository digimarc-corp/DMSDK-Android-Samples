// - - - - - - - - - - - - - - - - - - - -
//
// Digimarc Confidential
// Copyright Digimarc Corporation, 2014-2016
//
// - - - - - - - - - - - - - - - - - - - -
package com.digimarc.dmsdemo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.util.Log;

import com.digimarc.dms.readers.Manager;

import java.util.Locale;

public class PreferenceActivity
        extends android.preference.PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener
{
    private static final String TAG = "PreferenceActivity";

    private static final String About_Section = "About";

    // Named values returned to the calling activity in an intent
    public static final String CHANGED_SYMBOLOGY = "SymbologyChanged";

    // Named values of preference entries
    public static final String ENTRY_BARCODE = "ImageBarcode";
    public static final String ENTRY_AUDIO = "AudioDigimarc";

    private boolean symbologyChanged = false;

	@SuppressWarnings("deprecation")
	@Override
    public void onCreate(Bundle savedInstanceState) {  
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences); 

        Manager mgr = Manager.getInstance();

        PreferenceCategory category = (PreferenceCategory)findPreference( About_Section );

        if (category != null)
        {
            Preference pref = new Preference( this );

            pref.setTitle( getString( R.string.app_name ) );
            pref.setSummary( String.format( Locale.US,
                                            getString( R.string.preferences_version ),
                                            mgr.getSdkVersion(),
                                            mgr.getCameraSettingsKBVersion() ) );
            pref.setKey( "about" );

            category.addPreference( pref );
        }

        // record KB version used and rule name selected
		Log.i( TAG, "KB version: " + mgr.getCameraSettingsKBVersion());
		Log.i(TAG,"KB rule name selected: " + mgr.getCameraSettingsKBCurrentRuleName());

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        data.putExtra(CHANGED_SYMBOLOGY, symbologyChanged);

		setResult(RESULT_OK, data);

		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onBackPressed() {
	    Intent data = new Intent();

        data.putExtra(CHANGED_SYMBOLOGY, symbologyChanged);

		setResult(RESULT_OK, data);

		super.onBackPressed();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        switch( key )
        {
            case ENTRY_BARCODE:
            case ENTRY_AUDIO:
                symbologyChanged = true;
                break;
            default:
                break;
        }
    }
}


