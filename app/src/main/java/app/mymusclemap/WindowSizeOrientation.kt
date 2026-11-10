package app.mymusclemap

import android.content.pm.ActivityInfo

/**
 * Windows at least this narrow in their smaller dimension keep the phone portrait lock.
 * Wider windows, including tablets in either orientation, follow the user.
 */
internal const val TABLET_SMALLEST_WIDTH_DP = 600

internal fun requestedOrientationForSmallestWidth(smallestWidthDp: Int): Int {
    return if (smallestWidthDp >= TABLET_SMALLEST_WIDTH_DP) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
