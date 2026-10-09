package app.mymusclemap

import android.content.Context
import app.mymusclemap.data.billing.BillingGateway
import app.mymusclemap.data.billing.BillingProducts
import app.mymusclemap.data.billing.FakeBillingGateway
import app.mymusclemap.data.billing.PlayBillingGateway

/** Debug builds can replace Play Billing with a local preview. Release cannot. */
object BillingGatewaySelection {
    fun mode(): String = BuildConfig.STRICT_DEBUG_BILLING

    fun create(context: Context): BillingGateway {
        val mode = mode()
        if (mode == "REAL") {
            return PlayBillingGateway(context, BillingProducts.configured())
        }
        return FakeBillingGateway(mode)
    }
}
