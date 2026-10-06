package app.mymusclemap.ui.achievements

import app.mymusclemap.R

/**
 * Maps a stable [badgeKey] to placeholder artwork.
 * The key is the identity. Replacing the drawable does not change stored achievements.
 */
object BadgeArtworkResolver {
    fun drawableFor(badgeKey: String): Int {
        return when (badgeKey) {
            else -> R.drawable.ic_badge_placeholder
        }
    }
}
