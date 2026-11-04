package com.soyeb.ieltspractice.nav

import kotlinx.serialization.Serializable

// Every pushed destination, in one place so any feature can link to any other. Tab roots are in BottomBar.kt.
// Each feature registers the composables for its own routes in feature/<name>/<Name>Graph.kt.

// Auth & onboarding (feature/auth)
@Serializable data object Welcome
/** [mode]: signin | signup | verify | forgot | reset. [reason]: why the user was sent here ("Sign in to keep your results"). [email] prefills the address. */
@Serializable data class Auth(val mode: String = "signin", val reason: String? = null, val email: String? = null)
@Serializable data object Onboarding

// You (feature/you)
@Serializable data object You
@Serializable data object SettingsRoute
@Serializable data object Plan

// Study tools
@Serializable data class History(val skill: String? = null)       // feature/history
@Serializable data class Mistakes(val category: String? = null)   // feature/study
/** The drill. [pattern]: one mistake category; [ahead]: cards not yet due too (a drill on demand). */
@Serializable data class Review(val pattern: String? = null, val ahead: Boolean = false) // feature/study
@Serializable data object CoachRoute                              // feature/study
@Serializable data object StreakRoute                             // feature/streak
/** Prompt bank. [skill]: speaking | writing. */
@Serializable data class Bank(val skill: String = "speaking", val part: Int? = null, val variant: String? = null) // feature/study

// Listening & Reading (feature/lr)
/** [skill]: listening | reading. A pushed detail for one test (deep links from Today / History); hubs may use a sheet instead. */
@Serializable data class LrTestRoute(val testId: String, val skill: String)
@Serializable data class LrRun(val attemptId: String, val mockId: String? = null)
@Serializable data class LrResult(val attemptId: String)
@Serializable data object Spelling

// Writing (feature/writing)
/** [task]: task1 | task2 | full. [variant]: academic | general (task 1). [promptId] null = random; a resumed full test also sets [promptId2] (Task 2). */
@Serializable data class WritingTask(
    val task: String,
    val variant: String? = null,
    val promptId: String? = null,
    val promptId2: String? = null,
    val mockId: String? = null,
    /** A retake: the attempt this one improves on (the result then shows what changed). */
    val parentId: String? = null,
)

// Speaking (feature/speaking)
/** [mode]: full | part1 | part2 | part3. [promptId] null = random / full test set. [parentId]: the attempt being retried ("Practise this again"). */
@Serializable data class SpeakingSession(val mode: String, val promptId: String? = null, val mockId: String? = null, val parentId: String? = null)
@Serializable data class LiveExam(val mockId: String? = null)

/** A speaking or writing result. Several ids = one multi-part session (full speaking test). */
@Serializable data class AttemptResult(val ids: List<String>) {
    companion object {
        fun of(vararg ids: String) = AttemptResult(ids.toList())
    }
}

// Full mock (feature/mock)
@Serializable data object MockStart
@Serializable data class MockHub(val mockId: String)
@Serializable data class MockResult(val mockId: String)
