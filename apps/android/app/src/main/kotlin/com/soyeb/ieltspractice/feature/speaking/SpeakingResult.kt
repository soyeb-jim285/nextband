package com.soyeb.ieltspractice.feature.speaking

import androidx.compose.runtime.Composable
import com.soyeb.ieltspractice.data.model.Attempt
import com.soyeb.ieltspractice.nav.Nav

/**
 * Contract used by feature/result/ResultScreen: the finished speaking result for one attempt or a multi-part session
 * ([attempts] in part order, every one `status == "done"`). Owned by the speaking feature.
 */
@Composable
fun SpeakingResult(attempts: List<Attempt>, nav: Nav) {
    com.soyeb.ieltspractice.feature.StubPushed("Speaking result", nav)
}
