package com.soyeb.ieltspractice.data.logic

/** Mirrors core DisfluencyKind (apps/ios/scripts/check-models.mjs keeps the keys in sync). `rate` = [good, warn] events per minute; the copy mirrors web GUIDE. */
data class DisfluencyKind(
    val key: String, val label: String, val what: String, val normal: String, val harmful: String,
    val rate: List<Double>,
    /** Always listed (a zero is good news); cut-offs and held sounds only when present. */
    val core: Boolean,
)

object DisfluencyKinds {
    val all = listOf(
        DisfluencyKind("filled", "Filled pauses", "\"um\", \"uh\", \"er\" while searching for a word.",
            "A couple a minute is natural, even for native speakers.",
            "More than about 4 a minute, or several in a row, sounds unsure. Pause silently instead.", listOf(2.0, 4.0), true),
        DisfluencyKind("repetition", "Repetitions", "A word or phrase said twice, like \"I I think\".",
            "An occasional repeat while you plan the next word is fine.",
            "Frequent repeats signal word-searching. Plan the first few words before you start.", listOf(1.0, 2.0), true),
        DisfluencyKind("repair", "Self-corrections", "You restart and change the wording, like \"he go… he goes\".",
            "Fixing a real mistake shows self-monitoring; band 7 allows some.",
            "Many restarts make the listener lose the idea. Correct only what matters.", listOf(1.0, 2.0), true),
        DisfluencyKind("false_start", "False starts", "A sentence you abandon and begin again.",
            "One now and then is normal in unplanned speech.",
            "Often abandoning sentences hurts coherence. Start with a short, safe clause.", listOf(1.0, 2.0), true),
        DisfluencyKind("partial", "Cut-off words", "A word you cut off and restart, like \"sh- she\".",
            "Occasional cut-offs happen when you change your mind about a word.",
            "Many cut-offs suggest reaching for words you are unsure of. Choose a simpler word.", listOf(1.0, 2.0), false),
        DisfluencyKind("prolongation", "Held sounds", "A sound held while you think, like \"sooo\".",
            "An occasional stretched word buys thinking time.",
            "Frequent stretching slows the answer and sounds hesitant. Try a short pause.", listOf(2.0, 4.0), false),
    )
}

