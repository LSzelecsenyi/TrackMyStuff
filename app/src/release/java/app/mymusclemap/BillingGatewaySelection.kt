package app.mymusclemap

import android.content.Context
import app.mymusclemap.data.billing.BillingGateway
import app.mymusclemap.data.billing.BillingProducts
import app.mymusclemap.data.billing.PlayBillingGateway

/** Release builds always use Google Play Billing. Debug billing modes are ignored. */
object BillingGatewaySelection {
    fun mode(): String = "REAL"

    fun create(context: Context): BillingGateway {
        return PlayBillingGateway(context, BillingProducts.configured())
    }
}
