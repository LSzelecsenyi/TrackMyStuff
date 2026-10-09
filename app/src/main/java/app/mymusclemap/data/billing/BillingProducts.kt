package app.mymusclemap.data.billing

import app.mymusclemap.BuildConfig

object BillingProducts {
    fun configured(): List<String> {
        return BuildConfig.STRICT_BILLING_PRODUCT_IDS
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
