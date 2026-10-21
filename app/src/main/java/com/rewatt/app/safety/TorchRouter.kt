package com.rewatt.app.safety

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.rewatt.app.perception.CameraCaptureController

/**
 * Torch for alarm strobes that may fire while no CCTV session holds the camera (e.g. a fire).
 * When the camera service has the back camera bound, the torch must go through CameraX
 * ([CameraCaptureController.setAlarmTorch], which still respects the occupancy-photo torch
 * suppression); otherwise the plain flashlight API is used.
 */
class TorchRouter(context: Context) {
    private val cameraManager = context.applicationContext.getSystemService(CameraManager::class.java)
    private val backTorchId: String? by lazy {
        runCatching {
            cameraManager.cameraIdList.firstOrNull { id ->
                val c = cameraManager.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        }.getOrNull()
    }

    /** Registered by the camera service while it owns a [CameraCaptureController]. */
    @Volatile
    var controller: CameraCaptureController? = null

    fun set(on: Boolean) {
        val bound = controller?.takeIf { it.isCameraBound }
        if (bound != null) {
            bound.setAlarmTorch(on)
            return
        }
        val id = backTorchId ?: return
        runCatching { cameraManager.setTorchMode(id, on) }
            .onFailure { Log.w(TAG, "Torch unavailable: ${it.message}") }
    }

    companion object {
        private const val TAG = "TorchRouter"
    }
}
