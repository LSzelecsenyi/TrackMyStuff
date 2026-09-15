package hu.laca.weighttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class GreetingSelectorTest {
    @Test
    fun selectsGreetingFromDeterministicClock() {
        assertEquals(Greeting.Morning, GreetingSelector.from(LocalTime.of(5, 0)))
        assertEquals(Greeting.Morning, GreetingSelector.from(LocalTime.of(8, 15)))
        assertEquals(Greeting.Morning, GreetingSelector.from(LocalTime.of(9, 59)))
        assertEquals(Greeting.Day, GreetingSelector.from(LocalTime.of(10, 0)))
        assertEquals(Greeting.Day, GreetingSelector.from(LocalTime.of(14, 30)))
        assertEquals(Greeting.Day, GreetingSelector.from(LocalTime.of(17, 59)))
        assertEquals(Greeting.Evening, GreetingSelector.from(LocalTime.of(18, 0)))
        assertEquals(Greeting.Evening, GreetingSelector.from(LocalTime.of(4, 59)))
        assertEquals(Greeting.Evening, GreetingSelector.from(LocalTime.of(23, 10)))
    }
}
