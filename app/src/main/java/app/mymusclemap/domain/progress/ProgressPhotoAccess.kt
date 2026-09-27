package app.mymusclemap.domain.progress

import app.mymusclemap.domain.entitlement.AppFeature

object ProgressPhotoAccess {
    fun canAdd(hasAccess: (AppFeature) -> Boolean): Boolean {
        return hasAccess(AppFeature.ProgressPhotos)
    }
}
