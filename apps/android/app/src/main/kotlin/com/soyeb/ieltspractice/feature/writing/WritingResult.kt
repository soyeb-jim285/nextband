package com.soyeb.ieltspractice.feature.writing

import androidx.compose.runtime.Composable
import com.soyeb.ieltspractice.data.model.Attempt
import com.soyeb.ieltspractice.nav.Nav

/** Contract used by feature/result/ResultScreen: the finished writing result (one task, or both tasks of a full test). Owned by the writing feature. */
@Composable
fun WritingResult(attempts: List<Attempt>, nav: Nav) {
    com.soyeb.ieltspractice.feature.StubPushed("Writing result", nav)
}
