package com.instalimit.guard

import kotlin.random.Random

object MotivationalQuotes {
    private val quotes = listOf(
        "You're wasting your potential.",
        "That scroll isn't going to finish your goals.",
        "Your future self is watching. Close the app.",
        "Discipline is choosing what you want most over what you want now.",
        "Stop watching. Start building.",
        "You didn't come this far to scroll past it.",
        "Every minute here is a minute you don't get back.",
        "Potential doesn't grow in a feed.",
        "The algorithm isn't your friend. Your goals are.",
        "Close it. Breathe. Get back to your life.",
        "You already know you've seen enough.",
        "Greatness has one notification setting: off.",
        "You set this limit because you meant it. Mean it.",
        "Nothing you need is on the other side of this swipe.",
        "Your attention is the most expensive thing you own. Spend it well.",
        "This is exactly the moment your plan was made for."
    )

    fun random(): String = quotes[Random.nextInt(quotes.size)]

    fun forReason(reason: String): String = when (reason) {
        AppConfig.REASON_REELS ->
            listOf(
                "Reels are a treadmill. You're not going anywhere.",
                "You came for one. It's been forty. Close it.",
                "Your potential has better things to do than autoplay."
            ).random()

        AppConfig.REASON_STORIES ->
            listOf(
                "Their highlight reel is not your assignment.",
                "You're watching someone else's life instead of living yours.",
                "Stories are off. Go make one worth telling."
            ).random()

        else ->
            listOf(
                "You've hit your limit. You said this was enough, and it is.",
                "Today's budget is spent. Tomorrow is a new day.",
                "You kept your word to yourself. That's the whole point."
            ).random()
    }
}
