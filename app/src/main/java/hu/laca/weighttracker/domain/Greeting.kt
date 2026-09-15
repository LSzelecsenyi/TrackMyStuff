package hu.laca.weighttracker.domain

import java.time.LocalTime

enum class Greeting {
    Morning,
    Day,
    Evening
}

object GreetingSelector {
    fun from(time: LocalTime): Greeting {
        val hour = time.hour
        return when {
            hour in 5..9 -> Greeting.Morning
            hour in 10..17 -> Greeting.Day
            else -> Greeting.Evening
        }
    }
}
