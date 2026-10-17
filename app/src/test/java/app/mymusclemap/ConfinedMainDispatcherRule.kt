package app.mymusclemap

import java.util.PriorityQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Installs a single-thread [Dispatchers.Main] for one test.
 *
 * [kotlinx.coroutines.test.UnconfinedTestDispatcher] resumes a continuation on whatever
 * thread completed the last suspension. Room's query pool then enters the ViewModel
 * collector while the test thread mutates the same state. This dispatcher reports that
 * a dispatch is needed from every thread except its own, so those resumes are queued
 * back onto one thread.
 *
 * It is not a [kotlinx.coroutines.test.TestDispatcher]. `runTest` executes
 * [TestDispatcher] tasks on the test thread, which would undo the confinement.
 * Draft delays use a virtual clock advanced only on this thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfinedMainDispatcherRule : TestWatcher() {
    private lateinit var dispatcher: ConfinedMainDispatcher

    override fun starting(description: Description) {
        dispatcher = ConfinedMainDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        try {
            Dispatchers.resetMain()
        } finally {
            if (::dispatcher.isInitialized) {
                dispatcher.close()
            }
        }
    }

    /**
     * Advances this dispatcher's virtual clock on its own thread, then lets work that
     * the resumed coroutine handed to Room post its continuation back here.
     */
    suspend fun advanceTimeBy(delayMillis: Long) {
        check(delayMillis >= 0L)
        withContext(Dispatchers.Default) {
            withContext(Dispatchers.Main) {
                dispatcher.advanceTimeBy(delayMillis)
            }
            repeat(DRAIN_PASSES) {
                yield()
                withContext(Dispatchers.Main) { }
            }
        }
    }

    suspend fun advanceUntilIdle() {
        advanceTimeBy(0L)
    }

    @OptIn(InternalCoroutinesApi::class)
    private class ConfinedMainDispatcher : CoroutineDispatcher(), Delay {
        private val queue = LinkedBlockingQueue<Runnable>()
        private val lock = Any()
        private val delayed = PriorityQueue<DelayedTask>()
        private val sequence = AtomicLong()
        private val stop = Runnable { }
        private var virtualNow = 0L

        @Volatile
        private var closed = false

        val worker: Thread = Thread(::runLoop, THREAD_NAME).apply {
            isDaemon = true
            start()
        }

        override fun isDispatchNeeded(context: CoroutineContext): Boolean =
            Thread.currentThread() !== worker

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            // combine() yields after every upstream value. yield() calls dispatch even when
            // isDispatchNeeded is false, and only keeps the continuation inline if the
            // dispatcher reports itself as unconfined. Queueing that yield parks the
            // collector until this thread is idle, so a later StateFlow write is invisible
            // to an immediate uiState read.
            if (markUnconfinedYield(context)) {
                return
            }
            if (closed) {
                return
            }
            queue.offer(block)
        }

        override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
            val task = schedule(timeMillis) {
                if (continuation.isActive) {
                    continuation.resume(Unit) { _, _, _ -> }
                }
            }
            continuation.invokeOnCancellation { task.cancelled = true }
        }

        override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
            val task = schedule(timeMillis) { block.run() }
            return DisposableHandle { task.cancelled = true }
        }

        fun advanceTimeBy(delayMillis: Long) {
            check(Thread.currentThread() === worker) {
                "Virtual time must advance on $THREAD_NAME"
            }
            synchronized(lock) {
                virtualNow += delayMillis
            }
            while (true) {
                val task = synchronized(lock) {
                    val next = delayed.peek() ?: return
                    if (next.at > virtualNow) {
                        return
                    }
                    delayed.poll()
                } ?: return
                if (!task.cancelled) {
                    task.block()
                }
            }
        }

        fun close() {
            closed = true
            queue.offer(stop)
            worker.join(JOIN_TIMEOUT_MS)
        }

        private fun schedule(delayMillis: Long, block: () -> Unit): DelayedTask {
            return synchronized(lock) {
                val task = DelayedTask(
                    at = virtualNow + delayMillis,
                    sequence = sequence.incrementAndGet(),
                    block = block
                )
                delayed.add(task)
                task
            }
        }

        private fun markUnconfinedYield(context: CoroutineContext): Boolean {
            val yieldContext = context.fold<Any?>(null) { found, element ->
                found ?: element.takeIf { it.javaClass.name == "kotlinx.coroutines.YieldContext" }
            } ?: return false
            val field = yieldContext.javaClass.getDeclaredField("dispatcherWasUnconfined")
            field.isAccessible = true
            field.setBoolean(yieldContext, true)
            return true
        }

        private fun runLoop() {
            while (true) {
                val task = queue.take()
                if (task === stop) {
                    break
                }
                task.run()
            }
        }
    }

    private class DelayedTask(
        val at: Long,
        private val sequence: Long,
        val block: () -> Unit
    ) : Comparable<DelayedTask> {
        @Volatile
        var cancelled: Boolean = false

        override fun compareTo(other: DelayedTask): Int {
            val byTime = at.compareTo(other.at)
            return if (byTime != 0) byTime else sequence.compareTo(other.sequence)
        }
    }

    private companion object {
        const val THREAD_NAME = "active-workout-main"
        const val DRAIN_PASSES = 8
        const val JOIN_TIMEOUT_MS = 10_000L
    }
}
