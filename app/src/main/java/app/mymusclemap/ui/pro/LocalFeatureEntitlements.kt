package app.mymusclemap.ui.pro

import androidx.compose.runtime.staticCompositionLocalOf
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements

val LocalFeatureEntitlements = staticCompositionLocalOf<FeatureEntitlements> {
    OpenFeatureEntitlements
}
