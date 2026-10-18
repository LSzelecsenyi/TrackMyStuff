package app.mymusclemap.ui.workout

import java.util.concurrent.atomic.AtomicInteger

/** True while an activity of this process is started. */
object AppForeground {
    private val started = AtomicInteger(0)

    fun enter() {
        started.incrementAndGet()
    }

    fun exit() {
        started.updateAndGet { count -> (count - 1).coerceAtLeast(0) }
    }

    fun inForeground(): Boolean = started.get() > 0
}
