package app.mymusclemap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        // Room collectors can still be cancelling on a background thread here.
        dispatcher.scheduler.advanceUntilIdle()
        var remainingAttempts = 8
        while (true) {
            try {
                Dispatchers.resetMain()
                return
            } catch (error: IllegalStateException) {
                if (remainingAttempts-- == 0) {
                    throw error
                }
                dispatcher.scheduler.advanceUntilIdle()
                Thread.sleep(5)
            }
        }
    }
}
