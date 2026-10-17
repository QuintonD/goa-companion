package dev.quinton.goa.feature.sync.invite

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await

/** The invite QR code: black modules on white with a one-module quiet zone, one pixel per module. */
object InviteQr {
    fun matrix(text: String): BitMatrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
    )

    /** Scale up with `FilterQuality.None` so modules stay sharp. */
    fun bitmap(text: String): ImageBitmap {
        val m = matrix(text)
        val pixels = IntArray(m.width * m.height) { i -> if (m[i % m.width, i / m.width]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(pixels, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** What scanning an invite gave. */
sealed interface ScanResult {
    data class Scanned(val text: String) : ScanResult

    data object Cancelled : ScanResult

    /** The scanner can't run (no Play services, module not downloaded yet, …). */
    data class Unavailable(val message: String) : ScanResult
}

/**
 * Scans an invite with Google's code scanner: it runs in Play services, so the app needs no
 * CAMERA permission. Play services downloads the scanner the first time (that phone needs a
 * connection once; this app still has none).
 */
object InviteScanner {
    @Suppress("SwallowedException") // every failure becomes a ScanResult the user sees
    suspend fun scan(activity: Activity): ScanResult {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        return try {
            val barcode = GmsBarcodeScanning.getClient(activity, options).startScan().await()
            barcode.rawValue?.let(ScanResult::Scanned) ?: ScanResult.Cancelled
        } catch (e: MlKitException) {
            if (e.errorCode == MlKitException.CODE_SCANNER_CANCELLED) {
                ScanResult.Cancelled
            } else {
                ScanResult.Unavailable(UNAVAILABLE)
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            val cancelled = e.statusCode == CommonStatusCodes.CANCELED
            if (cancelled) ScanResult.Cancelled else ScanResult.Unavailable(UNAVAILABLE)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // A scan the user backs out of is a cancelled Task, which await() reports as a
            // CancellationException; only rethrow when this coroutine itself was cancelled.
            if (currentCoroutineContext().isActive) ScanResult.Cancelled else throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            ScanResult.Unavailable(UNAVAILABLE)
        }
    }

    const val UNAVAILABLE =
        "The QR scanner isn't ready on this phone (Google Play services fetches it the first time it's used, " +
            "which needs a connection once). Paste the invite instead."
}
