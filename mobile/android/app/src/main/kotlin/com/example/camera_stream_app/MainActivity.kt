package com.example.camera_stream_app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Saf Dart JPEG kodlama gerçek cihazda kare başına ~2 sn sürüyor ve 15 FPS'i
 * karşılamıyor. Kodlama platformun native YuvImage.compressToJpeg'ine devredildi.
 */
class MainActivity : FlutterActivity() {

    private companion object {
        const val ENCODER_CHANNEL = "camera_stream_app/jpeg_encoder"
        const val STORAGE_CHANNEL = "camera_stream_app/device_storage"
        const val PERMISSION_CHANNEL = "camera_stream_app/permissions"
        const val PREFS_NAME = "camera_stream_app_identity"
        const val CAMERA_PERMISSION_REQUEST = 4711
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var encoderExecutor: ExecutorService? = null
    private var pendingPermissionResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val executor = Executors.newFixedThreadPool(3)
        encoderExecutor = executor

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            PERMISSION_CHANNEL,
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "checkCameraPermission" -> result.success(cameraPermissionStatus())

                "requestCameraPermission" -> requestCameraPermission(result)

                "openAppSettings" -> result.success(openAppSettings())

                else -> result.notImplemented()
            }
        }

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            STORAGE_CHANNEL,
        ).setMethodCallHandler { call, result ->
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val key = call.argument<String>("key")

            if (key.isNullOrEmpty()) {
                result.error("BAD_ARGS", "key is required", null)
                return@setMethodCallHandler
            }

            when (call.method) {
                "read" -> result.success(prefs.getString(key, null))

                "write" -> {
                    prefs.edit().putString(key, call.argument<String>("value")).apply()
                    result.success(null)
                }

                else -> result.notImplemented()
            }
        }

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            ENCODER_CHANNEL,
        ).setMethodCallHandler { call, result ->
            if (call.method != "encodeYuv420") {
                result.notImplemented()
                return@setMethodCallHandler
            }

            val request = try {
                EncodeRequest.from(call::argument)
            } catch (error: Exception) {
                result.error("BAD_ARGS", error.message, null)
                return@setMethodCallHandler
            }

            executor.execute {
                val response = try {
                    Result.success(encode(request))
                } catch (error: Exception) {
                    Result.failure<ByteArray>(error)
                }

                mainHandler.post {
                    response.fold(
                        onSuccess = { result.success(it) },
                        onFailure = { result.error("ENCODE_FAILED", it.message, null) },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        encoderExecutor?.shutdownNow()
        encoderExecutor = null
        pendingPermissionResult = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Kamera izni
    // ------------------------------------------------------------------

    private fun isCameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * İzin verilmemişken kalıcı ret ile geçici reti ayırmak mümkün değildir:
     * kullanıcıya hiç sorulmamışken de shouldShowRequestPermissionRationale
     * false döner. Bu yüzden sorulmadan önce "denied" denir; kalıcı ret ancak
     * bir istek sonucunda anlaşılır.
     */
    private fun cameraPermissionStatus(): String =
        if (isCameraGranted()) "granted" else "denied"

    private fun requestCameraPermission(result: MethodChannel.Result) {
        if (isCameraGranted()) {
            result.success("granted")
            return
        }

        if (pendingPermissionResult != null) {
            result.error("ALREADY_PENDING", "A permission request is in flight", null)
            return
        }

        pendingPermissionResult = result

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != CAMERA_PERMISSION_REQUEST) {
            return
        }

        val result = pendingPermissionResult ?: return
        pendingPermissionResult = null

        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED

        if (granted) {
            result.success("granted")
            return
        }

        // İstek sonrasında rationale gösterilemiyorsa kullanıcı "bir daha sorma"
        // demiştir; tek çıkış yolu uygulama ayarlarıdır.
        val canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.CAMERA,
        )

        result.success(if (canAskAgain) "denied" else "permanentlyDenied")
    }

    private fun openAppSettings(): Boolean = try {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (error: Exception) {
        false
    }

    private class EncodeRequest(
        val y: ByteArray,
        val u: ByteArray,
        val v: ByteArray,
        val yRowStride: Int,
        val uRowStride: Int,
        val vRowStride: Int,
        val uPixelStride: Int,
        val vPixelStride: Int,
        val width: Int,
        val height: Int,
        val step: Int,
        val quality: Int,
    ) {
        companion object {
            fun from(argument: (String) -> Any?): EncodeRequest {
                fun int(key: String) = (argument(key) as Number).toInt()
                fun bytes(key: String) = argument(key) as ByteArray

                return EncodeRequest(
                    y = bytes("y"),
                    u = bytes("u"),
                    v = bytes("v"),
                    yRowStride = int("yRowStride"),
                    uRowStride = int("uRowStride"),
                    vRowStride = int("vRowStride"),
                    uPixelStride = int("uPixelStride"),
                    vPixelStride = int("vPixelStride"),
                    width = int("width"),
                    height = int("height"),
                    step = int("step"),
                    quality = int("quality"),
                )
            }
        }
    }

    /**
     * YUV_420_888 düzlemlerini NV21'e paketler. YuvImage yalnızca NV21/YUY2
     * kabul ettiği için ara adım zorunlu; paketleme sırasında [step] ile
     * alt örnekleme de yapılır.
     */
    private fun encode(request: EncodeRequest): ByteArray {
        val step = request.step.coerceAtLeast(1)

        // NV21 kroma düzlemi yarı çözünürlüklü olduğundan boyutlar çift olmalı.
        val outWidth = (request.width / step) and 1.inv()
        val outHeight = (request.height / step) and 1.inv()

        require(outWidth > 0 && outHeight > 0) {
            "Invalid output size ${outWidth}x$outHeight"
        }

        val lumaSize = outWidth * outHeight
        val nv21 = ByteArray(lumaSize + lumaSize / 2)

        var index = 0
        for (row in 0 until outHeight) {
            val srcRow = row * step * request.yRowStride
            for (column in 0 until outWidth) {
                nv21[index++] = request.y[srcRow + column * step]
            }
        }

        for (row in 0 until outHeight / 2) {
            val srcRow = row * step
            val uRow = srcRow * request.uRowStride
            val vRow = srcRow * request.vRowStride

            for (column in 0 until outWidth / 2) {
                val srcColumn = column * step
                nv21[index++] = request.v[vRow + srcColumn * request.vPixelStride]
                nv21[index++] = request.u[uRow + srcColumn * request.uPixelStride]
            }
        }

        val output = ByteArrayOutputStream(lumaSize / 2)

        YuvImage(nv21, ImageFormat.NV21, outWidth, outHeight, null).compressToJpeg(
            Rect(0, 0, outWidth, outHeight),
            request.quality,
            output,
        )

        return output.toByteArray()
    }
}
