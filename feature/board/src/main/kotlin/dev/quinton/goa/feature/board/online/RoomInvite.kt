package dev.quinton.goa.feature.board.online

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.quinton.goa.feature.board.play.online.parseInvite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await

private const val DEEP_LINK = "goa-companion://play/"

/** The room code in a `goa-companion://play/<code>` link; null when it isn't one, or holds no valid code. */
fun onlineInviteCode(link: String): String? {
    val text = link.trim()
    if (!text.startsWith(DEEP_LINK, ignoreCase = true)) return null
    return parseInvite(text.substring(DEEP_LINK.length).substringBefore('?').substringBefore('#').trimEnd('/'))
}

/** What the home's field or the scanner hands to `OnlineHomePresenter.join`: a deep link becomes its code. */
internal fun inviteText(text: String): String = onlineInviteCode(text) ?: text

/** The invite QR: black modules on white with a one-module quiet zone. Scale with `FilterQuality.None`. */
internal fun roomQr(text: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** How scanning went: the code's text, nothing (backed out), or words for why it can't run. */
internal sealed interface RoomScan {
    data class Scanned(val text: String) : RoomScan

    data object Cancelled : RoomScan

    data object Unavailable : RoomScan
}

/**
 * Reads an invite with Google's code scanner, which runs in Play services: the app needs no CAMERA permission.
 * The same as a group invite's (`InviteScanner`, feature/sync); features don't depend on each other.
 */
@Suppress("SwallowedException") // every failure becomes a RoomScan the person sees
internal suspend fun scanRoomInvite(activity: Activity): RoomScan {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    return try {
        val barcode = GmsBarcodeScanning.getClient(activity, options).startScan().await()
        barcode.rawValue?.let(RoomScan::Scanned) ?: RoomScan.Cancelled
    } catch (e: MlKitException) {
        if (e.errorCode == MlKitException.CODE_SCANNER_CANCELLED) RoomScan.Cancelled else RoomScan.Unavailable
    } catch (e: ApiException) {
        if (e.statusCode == CommonStatusCodes.CANCELED) RoomScan.Cancelled else RoomScan.Unavailable
    } catch (e: CancellationException) {
        // A scan the person backs out of is a cancelled Task; rethrow only if this coroutine was cancelled.
        if (currentCoroutineContext().isActive) RoomScan.Cancelled else throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        RoomScan.Unavailable
    }
}

internal const val SCANNER_UNAVAILABLE =
    "The QR scanner isn't ready on this phone (Google Play services fetches it the first time it's used, " +
        "which needs a connection). Type the room's code instead."
