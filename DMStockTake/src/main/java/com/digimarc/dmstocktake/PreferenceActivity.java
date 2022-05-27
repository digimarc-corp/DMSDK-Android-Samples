// - - - - - - - - - - - - - - - - - - - -
//
// Digimarc Confidential
// Copyright Digimarc Corporation, 2014-2016
//
// - - - - - - - - - - - - - - - - - - - -
package com.digimarc.dmstocktake;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class PreferenceActivity
        extends android.preference.PreferenceActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener
{
    // Named values returned to the calling activity in an intent
    public static final String CHANGED_DISTANCE = "DistanceChanged";

    // Named values of preference entries
    public static final String ENTRY_DISTANCE = "BarcodeDistance";

    private boolean distanceChanged = false;

	@SuppressWarnings("deprecation")
	@Override
    public void onCreate(Bundle savedInstanceState) {  
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences); 

        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        data.putExtra(CHANGED_DISTANCE, distanceChanged);

		setResult(RESULT_OK, data);

		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onBackPressed() {
	    Intent data = new Intent();

        data.putExtra(CHANGED_DISTANCE, distanceChanged);

		setResult(RESULT_OK, data);

		super.onBackPressed();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        switch( key )
        {
            case ENTRY_DISTANCE:
                distanceChanged = true;
                break;
            default:
                break;
        }
    }
}


