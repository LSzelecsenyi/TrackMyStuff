package app.mymusclemap

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.test.core.app.ApplicationProvider

fun testString(@StringRes id: Int, vararg formatArgs: Any): String {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return if (formatArgs.isEmpty()) {
        context.getString(id)
    } else {
        context.getString(id, *formatArgs)
    }
}

fun testQuantity(@PluralsRes id: Int, count: Int): String {
    return ApplicationProvider.getApplicationContext<Context>()
        .resources
        .getQuantityString(id, count, count)
}
