package com.digimarc.dvdemo

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Headless fragment for permission prompting.
 */
class PermissionFragment : Fragment() {
    companion object {
        private val PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        private const val REQ_CODE = 1234
    }

    interface OnPermissionGrantedListener {
        fun onPermissionGranted()
    }

    private var permissionListener: OnPermissionGrantedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        permissionListener = context as? OnPermissionGrantedListener
        if (permissionListener == null) {
            throw ClassCastException("$context must implement PermissionListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasPermission()) {
            permissionListener?.onPermissionGranted()
        } else {
            requestPermissions(PERMISSIONS, REQ_CODE)
        }
    }

    private fun hasPermission(): Boolean {
        for (permission in PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    requireActivity(),
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQ_CODE -> {
                if (grantResults.isNotEmpty()) {
                    // Don't continue without permissions
                    if (grantResults.contains(PackageManager.PERMISSION_DENIED)) {
                        activity?.let {
                            val builder = AlertDialog.Builder(it)
                            builder
                                    .setTitle("Permission requested")
                                    .setMessage("This application requires access to the camera in and microphone")
                                    .setPositiveButton("Settings"
                                    ) { _, _ ->
                                        val intent =
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        val uri =
                                            Uri.fromParts("package", activity?.packageName, null)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        intent.data = uri
                                        activity?.startActivity(intent)
                                        activity?.finish()
                                    }
                                    .setNegativeButton("Exit") { _, _ ->
                                        activity?.finish()
                                    }

                            builder.create()?.show()
                        }
                    } else {
                        permissionListener?.onPermissionGranted()
                    }
                }
            }
        }
    }
}