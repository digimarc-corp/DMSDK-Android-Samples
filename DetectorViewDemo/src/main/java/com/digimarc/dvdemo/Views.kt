package com.digimarc.dvdemo

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.graphics.RectF
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.digimarc.capture.camera.BitmapUtils
import com.digimarc.dis.DMSDetectorView
import com.digimarc.dis.interfaces.DISResultListener
import com.digimarc.dms.payload.Payload
import com.digimarc.dms.readers.BaseReader
import com.digimarc.dms.readers.ImageFrameStorage
import com.digimarc.dms.readers.ReaderResult
import com.digimarc.dms.resolver.ResolvedContent
import kotlinx.coroutines.*
import java.util.*

class CameraFragment : Fragment() {
    companion object {
        private const val useRenderScript = true  // Toggle RenderScript usage for converting image data to bitmaps
        private const val BUNDLE_ITEMS = "ITEMS"
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var root: ViewGroup
    private lateinit var recyclerView: RecyclerView

    private var mediaPlayer: MediaPlayer? = null
    private var dmsDetectorView: DMSDetectorView? = null
    private var disAdapter: DISAdapter? = null
    private var yuvToBitmap: YuvToBitmap? = null
    private val frameStorage = ImageFrameStorage()
    private var torchMenuItem: MenuItem? = null
    private var isTorchOn: Boolean = false
        // Toggle the torch state. We'll  update the icon display so that if the user comes back into this app session
        // the on-screen icon will match the actual torch state.
        set(value) {
            dmsDetectorView?.setTorchOn(value)
            torchMenuItem?.setIcon((if (value) R.drawable.ic_flash_on else R.drawable.ic_flash_off))
            field = value
        }

    private val bitmapRotation by lazy {
        val sensorRotation = dmsDetectorView?.cameraRotation ?: 0

        // This code is needed if the app supports landscape mode. If the frame
        // capture occurred when the device wasn't in portrait orientation then we
        // need to consider both the camera & device rotation in order to display
        // the captured thumbnail image correctly.
        val displayRotation = activity?.windowManager?.defaultDisplay?.rotation?.times(90) ?: 0
        (sensorRotation - displayRotation) % 360
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.camera_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        root = view.findViewById(R.id.root)
        dmsDetectorView = view.findViewById(R.id.component)
        recyclerView = view.findViewById(R.id.recycler_view)

        mediaPlayer = MediaPlayer()
        val rawFileDescriptor: AssetFileDescriptor? = context?.resources?.openRawResourceFd(R.raw.ding)
        rawFileDescriptor?.let {
            mediaPlayer?.setDataSource(rawFileDescriptor.fileDescriptor, rawFileDescriptor.startOffset, rawFileDescriptor.length)
            mediaPlayer?.prepareAsync()
            rawFileDescriptor.close()
        }

        disAdapter = DISAdapter(scope, DISItemClickListener { url ->
            launchContent(url)
        })
        recyclerView.adapter = disAdapter
        val decorator = DividerItemDecoration(recyclerView.context, RecyclerView.VERTICAL)
        recyclerView.addItemDecoration(decorator)

        activity?.let {
            yuvToBitmap = YuvToBitmap(it)
        }

        initDis()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.menu_flash)?.isVisible = dmsDetectorView?.isTorchAvailable!!
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main, menu)
        torchMenuItem = menu.findItem(R.id.menu_flash)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_clear_history -> disAdapter?.clear()
            R.id.menu_flash -> isTorchOn = !isTorchOn
        }
        return false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelableArrayList(BUNDLE_ITEMS, disAdapter?.items as ArrayList<DISItem>)

        super.onSaveInstanceState(outState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        savedInstanceState?.let { bundle ->
            val restoredItems = bundle.getParcelableArrayList<DISItem>(BUNDLE_ITEMS) as MutableList<DISItem>

            disAdapter?.apply {
                items = restoredItems
                notifyDataSetChanged()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dmsDetectorView?.start()
    }

    override fun onPause() {
        dmsDetectorView?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        dmsDetectorView?.release()
        mediaPlayer?.release()
        scope.coroutineContext.cancelChildren()
        super.onDestroy()
    }

    private fun initDis() {
        if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

            // There are several ways of specifying the symbologies you want to use, the easiest one being
            // (assuming that you want to use all detectors) the predefined bitmask values found in DMSDetectorView.
            //
            // You can remove any symbologies below that you don't want to use. Removing all audio symbologies
            // will prevent the DMSDetectorView component from capturing and processing audio. Likewise with
            // image symbologies and camera image capture.
            val symbologies = BaseReader.buildSymbologyMask(
                    BaseReader.ImageSymbology.Image_Digimarc, // Image Symbologies
                    BaseReader.ImageSymbology.Image_1D_UPCA,
                    BaseReader.ImageSymbology.Image_1D_UPCE,
                    BaseReader.ImageSymbology.Image_1D_EAN13,
                    BaseReader.ImageSymbology.Image_1D_EAN8,
                    BaseReader.ImageSymbology.Image_1D_Code39,
                    BaseReader.ImageSymbology.Image_1D_Code128,
                    BaseReader.ImageSymbology.Image_1D_DataBar,
                    BaseReader.ImageSymbology.Image_QRCode,
                    BaseReader.ImageSymbology.Image_1D_ITF_GTIN_14,
                    BaseReader.ImageSymbology.Image_1D_ITF_Variable,
                    BaseReader.AudioSymbology.Audio_Digimarc)         // Audio Symbologies

            dmsDetectorView?.initialize(
                symbologies,
                null,
                object : DISResultListener {

                    override fun onImageResult(result: ReaderResult): List<Payload>? {
                        return result.newPayloads?.let { newPayloads ->
                            // Store the image data for later retrieval
                            result.imageFrame?.let { img ->
                                newPayloads.forEach { payload ->
                                    frameStorage.storeFrame(payload.payloadString, img)
                                }
                            }
                            // Resolve new payloads
                            newPayloads
                        }
                        // No new payloads - don't resolve
                            ?: return null
                    }

                    override fun onAudioResult(result: ReaderResult): List<Payload>? =
                        result.newPayloads

                    override fun onResolved(result: ResolvedContent) {
                        scope.launch {
                            val item = createEntry(result)
                            disAdapter?.add(item)
                            recyclerView.scrollToPosition(disAdapter?.itemCount!!)
                        }
                    }

                }
            ) { _, description ->
                description?.let {
                    showErrorDialog(it)
                }
            }

            // By default tapping on the camera view will cause an immediate focus operation
            // to be performed.  Change this to false to disable.
            dmsDetectorView?.setTapFocusState(true)

            // Update the menu - we'll remove the torch icon if the device we're running on
            // does not support it.
            dmsDetectorView?.setNotifyListener {
                activity?.invalidateOptionsMenu()
            }

            // Uncomment to constrain detection to a specific region of the camera frame.
//            enableImageDetectionRegion()
        }
    }

    private fun enableImageDetectionRegion() {

        // Define a detection region at the center of the frame. In DMSDetectorView region bounds
        // represent a percentage of the visible image within the preview view. Region bounds should be
        // specified relative to the display's current rotation. (Note that the visible region is often
        // a sub-region of the full image frame. Applications that need need to set regions beyond the
        // visible area should use lower level the ImageReader or VideoCaptureReader API from dms package).
        val detectRegion = RectF(0.15f, 0.3f, 0.85f, 0.7f)
        dmsDetectorView?.setImageDetectionRegion(detectRegion)

        // Show the detect region view. This draws a translucent overlay on the areas of the preview
        // that are not being used for detection.
        dmsDetectorView?.setOverlayType(DMSDetectorView.OverlayType.DetectRegion)
    }

    suspend fun createEntry(result: ResolvedContent) = withContext(Dispatchers.Default) {
        val bmp = frameStorage.removeFrame(result.payload.payloadString)?.let { frame ->
            if (useRenderScript) {
                yuvToBitmap?.yuvFrameToBitmap(frame, bitmapRotation)
            } else {
                BitmapUtils.convertRawImageToBitmap(frame)
            }
        }

        val scaledBmp = bmp?.let {
            BitmapUtils.createScaledBitmap(
                it,
                128,
                128,
                BitmapUtils.SCALING_LOGIC.SCALING_CROP
            )
        }

        DISItem(result, scaledBmp)
    }

    private fun showErrorDialog(msg: String) {
        activity?.let {
            val builder = AlertDialog.Builder(it)
            builder.setTitle("Error")
                    .setMessage(msg)
                    .setPositiveButton("OK") { dialog, _ ->
                        dialog?.dismiss()
                    }
            builder.create().apply {
                if (!isShowing) {
                    show()
                }
            }
        }
    }

    private fun launchContent(url: String) {
        isTorchOn = false

        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url).normalizeScheme())
            context?.startActivity(i)
        } catch(ex: Exception) {
            Log.i("DetectorViewDemo", "Unable to launch content")
        }
    }
}

/**
 * Headless fragment for permission prompting.
 */
class PermissionFragment : Fragment() {
    companion object {
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
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
            if (ContextCompat.checkSelfPermission(requireActivity(), permission) != PackageManager.PERMISSION_GRANTED) {
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
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        val uri = Uri.fromParts("package", activity?.packageName, null)
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
