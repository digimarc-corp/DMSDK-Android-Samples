package com.digimarc.dvdemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Point
import androidx.renderscript.*
import android.util.Log
import com.digimarc.dms.readers.ImageFrame

/**
 * This class uses the Android Renderscript support library to perform high performance
 * image conversion from YUV (camera) image frames into displayable ARGB 8888 bitmaps.
 * <p>
 * To use this class in other applications you will need to add several lines to the app's build.gradle.
 * These lines go into the defaultConfig:
 * <pre>
 *     defaultConfig {
 *         ...
 *
 *         renderscriptTargetApi 19
 *         renderscriptSupportModeEnabled true
 *     }
 * </pre>
 *
 */
class YuvToBitmap(context: Context) {
    companion object {
        private val TAG = YuvToBitmap::class.java.name
    }

    private val renderScript = RenderScript.create(context)
    private val convertScript = ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript))
    private var frameSize = Point()
    private var yuv: Allocation? = null
    private var rgb: Allocation? = null

    /**
     * Convert a YUV image frame into an ARGB 8888 bitmap.
     * @param frame         Image data obtained from ReaderResult.getImageFrame()
     * @param rotation      Image rotation. If you are using the CameraHelper this can be retrieved using the
     *                      CameraHelper.getCameraRotation() method. Otherwise you can use the {@link Camera#getCameraInfo(int, Camera.CameraInfo)}
     *                      method and get the orientation value.
     * @return A bitmap object if the conversion was successful, null otherwise.
     */
    fun yuvFrameToBitmap(frame: ImageFrame, rotation: Int): Bitmap? {
        var rotated: Bitmap? = null

        if (frameSize.x != frame.imageWidth || frameSize.y != frame.imageHeight) {
            Log.v(TAG, "YUV: first time in, allocating RS buffers")

            val yuvType = Type.Builder(renderScript,
                    Element.createPixel(renderScript,
                            Element.DataType.UNSIGNED_8,
                            Element.DataKind.PIXEL_YUV))

            yuvType.setX(frame.imageWidth)
            yuvType.setY(frame.imageHeight)
            yuvType.setYuvFormat(ImageFormat.NV21)

            yuv = Allocation.createTyped(renderScript, yuvType.create(), Allocation.USAGE_SCRIPT)

            val rgbaType = Type.Builder(renderScript, Element.RGBA_8888(renderScript))
                    .setX(frame.imageWidth)
                    .setY(frame.imageHeight)
            rgb = Allocation.createTyped(renderScript, rgbaType.create(), Allocation.USAGE_SCRIPT)

            frameSize = Point(frame.imageWidth, frame.imageHeight)
        }

        yuv?.apply {
            val planes = frame.imageBuffer
            planes[0].mPlane?.array()?.let {
                copyFrom(it)
                convertScript.setInput(this)
                convertScript.forEach(rgb)
            } ?: return null
        }

        rgb?.apply {
            Log.v(TAG, "YUV: creating target bitmap")

            try {
                val bitmap = Bitmap.createBitmap(frameSize.x, frameSize.y, Bitmap.Config.ARGB_8888)
                copyTo(bitmap)

                Log.v(TAG, "YUV: creating rotated bitmap")
                rotated = if (rotation != 0) {
                    val matrix = Matrix()
                    matrix.setRotate(rotation.toFloat())

                    Bitmap.createBitmap(bitmap,
                            0,
                            0,
                            frameSize.x,
                            frameSize.y,
                            matrix,
                            true)
                } else {
                    bitmap
                }
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, e.message.toString())
            }
        }

        return rotated
    }
}