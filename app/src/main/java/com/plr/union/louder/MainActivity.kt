package com.plr.union.louder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.plr.union.louder.tools.Tools
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class MainActivity : AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback {


    private lateinit var textureView: TextureView
    private var captureSession: CameraCaptureSession? = null
    private var cameraDevice: CameraDevice? = null
    private lateinit var previewSize: Size
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private var imageReader: ImageReader? = null
    private lateinit var previewRequest: CaptureRequest
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var state = 0

    private val STATE_PREVIEW = 0

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private val STATE_WAITING_LOCK = 1

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private val STATE_WAITING_PRECAPTURE = 2

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private val STATE_WAITING_NON_PRECAPTURE = 3

    /**
     * Camera state: Picture was taken.
     */
    private val STATE_PICTURE_TAKEN = 4


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textureView = textureview
        textureView.surfaceTextureListener = surfaceTextureListener;
        startBackgroundThread()
    }

    fun openCamera() {
        var manager: CameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            var camerId: String = manager.getCameraIdList()[0]
            var characteristics: CameraCharacteristics = manager.getCameraCharacteristics(camerId);
            var map: StreamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            previewSize = map.getOutputSizes(SurfaceTexture::class.java)[0]

            val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission()
                return
            }

            manager.openCamera(camerId, stateCallback, null);
        } catch (e: Exception) {
            Log.e("erf", "Permissions not allowed");
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            //cameraOpenCloseLock.release()
            this@MainActivity.cameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            //cameraOpenCloseLock.release()
            cameraDevice.close()
            this@MainActivity.cameraDevice = null
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            onDisconnected(cameraDevice)
            finish()
        }

    }

    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
            )
            previewRequestBuilder.addTarget(surface)

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice?.createCaptureSession(Arrays.asList(surface, imageReader?.surface),
                    object : CameraCaptureSession.StateCallback() {

                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            // The camera is already closed
                            if (cameraDevice == null) return

                            // When the session is ready, we start displaying the preview.
                            captureSession = cameraCaptureSession
                            try {
                                // Auto focus should be continuous for camera preview.
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                // Flash is automatically enabled when necessary.
                                //setAutoFlash(previewRequestBuilder)

                                // Finally, we start displaying the camera preview.
                                previewRequest = previewRequestBuilder.build()
                                captureSession?.setRepeatingRequest(previewRequest,
                                        captureCallback, Handler(backgroundThread?.looper))
                            } catch (e: CameraAccessException) {
                                Log.e("erfs", e.toString())
                            }

                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Tools.makeToast(baseContext, "Failed")
                        }
                    }, null)
        } catch (e: CameraAccessException) {
            Log.e("erf", e.toString())
        }

    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {
            when (state) {
                STATE_PREVIEW -> Unit // Do nothing when the camera preview is working normally.
                STATE_WAITING_LOCK -> Unit//capturePicture(result)
                STATE_WAITING_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        state = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        state = STATE_PICTURE_TAKEN
                        //captureStillPicture()
                    }
                }
            }
        }
    }

    fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(baseContext)
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        requestPermissions(arrayOf(Manifest.permission.CAMERA),
                                200)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        finish()
                    }
                    .create()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 200)
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {

            imageReader = ImageReader.newInstance(width, height,
                    ImageFormat.JPEG, /*maxImages*/ 2);

            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            return false
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }
}
