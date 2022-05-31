package com.digimarc.dvdemo

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), PermissionFragment.OnPermissionGrantedListener {
    override fun onPermissionGranted() {
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.frame, DetectorViewFragment())
                .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main_activity)

        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.frame, PermissionFragment())
                    .commit()
        }
    }
}
