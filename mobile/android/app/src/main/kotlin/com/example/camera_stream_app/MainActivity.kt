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
import android.util.Log
import android.view.Surface
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
        const val ROT_TAG = "JpegEncoder"

        @Volatile
        var rotationLogged = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var encoderExecutor: ExecutorService? = null
    private var pendingPermissionResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val executor = Executors.newFixedThreadPool(4)
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
        val rotationDegrees: Int,
        val sensorOrientation: Int,
        val isBackCamera: Boolean,
        val quality: Int,
    ) {
        companion object {
            fun from(argument: (String) -> Any?): EncodeRequest {
                fun int(key: String) = (argument(key) as Number).toInt()
                fun bytes(key: String) = argument(key) as ByteArray

                // Eski bir Dart tarafı bu alanı göndermezse kare döndürülmeden
                // kodlanır; her karede BAD_ARGS üretmek yerine bozulmadan akar.
                fun optionalInt(key: String, fallback: Int) =
                    (argument(key) as? Number)?.toInt() ?: fallback

                fun optionalBool(key: String, fallback: Boolean) =
                    argument(key) as? Boolean ?: fallback

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
                    rotationDegrees = optionalInt("rotationDegrees", 0),
                    sensorOrientation = optionalInt("sensorOrientation", 0),
                    isBackCamera = optionalBool("isBackCamera", true),
                    quality = int("quality"),
                )
            }
        }
    }

    /**
     * Hedef pikselden (dx, dy) kaynak piksele (sx, sy) afin eşleme.
     *
     * ```text
     * sx = sxBase + dx * sxPerDx + dy * sxPerDy
     * sy = syBase + dx * syPerDx + dy * syPerDy
     * ```
     *
     * Her dönüşte sxPerDx ve syPerDx'ten tam olarak biri sıfırdır; bu sayede
     * satır içi ilerleme tek bir sabit byte adımına indirgenebilir.
     */
    private class RotationMap(
        val sxBase: Int,
        val sxPerDx: Int,
        val sxPerDy: Int,
        val syBase: Int,
        val syPerDx: Int,
        val syPerDy: Int,
    ) {
        companion object {
            fun of(rotation: Int, srcWidth: Int, srcHeight: Int): RotationMap =
                when (rotation) {
                    90 -> RotationMap(0, 0, 1, srcHeight - 1, -1, 0)
                    180 -> RotationMap(srcWidth - 1, -1, 0, srcHeight - 1, 0, -1)
                    270 -> RotationMap(srcWidth - 1, 0, -1, 0, 1, 0)
                    else -> RotationMap(0, 1, 0, 0, 0, 1)
                }
        }
    }

    /**
     * YUV_420_888 düzlemlerini NV21'e paketler. YuvImage yalnızca NV21/YUY2
     * kabul ettiği için ara adım zorunlu; paketleme sırasında [step] ile
     * alt örnekleme ve [EncodeRequest.rotationDegrees] ile döndürme de yapılır.
     *
     * Döndürme ayrı bir geçiş DEĞİLDİR: zaten yapılan tek kopyalamanın kaynak
     * indeksi değiştirilir. Ek buffer ayrılmaz, çıktı buffer boyutu aynı kalır
     * ve rotation=0 durumunda erişim deseni önceki kodla birebir aynıdır.
     */
    private fun encode(request: EncodeRequest): ByteArray {
        val step = request.step.coerceAtLeast(1)

        // NV21 kroma düzlemi yarı çözünürlüklü olduğundan boyutlar çift olmalı.
        val sampledWidth = (request.width / step) and 1.inv()
        val sampledHeight = (request.height / step) and 1.inv()

        require(sampledWidth > 0 && sampledHeight > 0) {
            "Invalid sampled size ${sampledWidth}x$sampledHeight"
        }

        val dartRotation = when (request.rotationDegrees) {
            90, 180, 270 -> request.rotationDegrees
            else -> 0
        }
        val displayDegrees = displayRotationDegrees()
        val nativeComputed = relativeImageRotation(
            sensor = request.sensorOrientation,
            isBackCamera = request.isBackCamera,
            displayDegrees = displayDegrees,
        )
        // Dart 0 gönderirse (plugin landscapeLeft yutması) Display+sensör yedeği.
        val rotation = if (dartRotation != 0) dartRotation else nativeComputed
        val swapAxes = rotation == 90 || rotation == 270

        val outWidth = if (swapAxes) sampledHeight else sampledWidth
        val outHeight = if (swapAxes) sampledWidth else sampledHeight

        val lumaSize = outWidth * outHeight
        val nv21 = ByteArray(lumaSize + lumaSize / 2)

        var index = 0

        val lumaMap = RotationMap.of(rotation, sampledWidth, sampledHeight)
        val lumaAdvance =
            lumaMap.syPerDx * step * request.yRowStride + lumaMap.sxPerDx * step

        for (dy in 0 until outHeight) {
            val sx = lumaMap.sxBase + dy * lumaMap.sxPerDy
            val sy = lumaMap.syBase + dy * lumaMap.syPerDy
            var src = sy * step * request.yRowStride + sx * step

            for (dx in 0 until outWidth) {
                nv21[index++] = request.y[src]
                src += lumaAdvance
            }
        }

        val chromaMap = RotationMap.of(rotation, sampledWidth / 2, sampledHeight / 2)
        val uAdvance = chromaMap.syPerDx * step * request.uRowStride +
            chromaMap.sxPerDx * step * request.uPixelStride
        val vAdvance = chromaMap.syPerDx * step * request.vRowStride +
            chromaMap.sxPerDx * step * request.vPixelStride

        for (dy in 0 until outHeight / 2) {
            val sx = chromaMap.sxBase + dy * chromaMap.sxPerDy
            val sy = chromaMap.syBase + dy * chromaMap.syPerDy
            var uSrc = sy * step * request.uRowStride + sx * step * request.uPixelStride
            var vSrc = sy * step * request.vRowStride + sx * step * request.vPixelStride

            for (dx in 0 until outWidth / 2) {
                nv21[index++] = request.v[vSrc]
                nv21[index++] = request.u[uSrc]
                uSrc += uAdvance
                vSrc += vAdvance
            }
        }

        val output = ByteArrayOutputStream(lumaSize / 2)

        YuvImage(nv21, ImageFormat.NV21, outWidth, outHeight, null).compressToJpeg(
            Rect(0, 0, outWidth, outHeight),
            request.quality,
            output,
        )

        if (!rotationLogged) {
            rotationLogged = true
            Log.w(
                ROT_TAG,
                "ROT native | received=$dartRotation display=$displayDegrees " +
                    "sensor=${request.sensorOrientation} back=${request.isBackCamera} " +
                    "nativeComputed=$nativeComputed applied=$rotation " +
                    "src=${request.width}x${request.height} " +
                    "out=${outWidth}x$outHeight",
            )
        }

        return output.toByteArray()
    }

    @Suppress("DEPRECATION")
    private fun displayRotationDegrees(): Int {
        val rotation = windowManager.defaultDisplay.rotation

        return when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    /** CameraX `getRelativeImageRotation` ile aynı. 90 sabiti yok. */
    private fun relativeImageRotation(
        sensor: Int,
        isBackCamera: Boolean,
        displayDegrees: Int,
    ): Int {
        val snappedSensor = snapRotation(sensor)
        val snappedDisplay = snapRotation(displayDegrees)

        return snapRotation(
            if (isBackCamera) snappedSensor - snappedDisplay
            else snappedSensor + snappedDisplay,
        )
    }

    private fun snapRotation(degrees: Int): Int {
        val snapped = ((degrees + 45) / 90) * 90

        return ((snapped % 360) + 360) % 360
    }
}
