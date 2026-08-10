package org.tiqian.markdown

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class MarkdownFootnoteNavigationState(
    private val coroutineScope: CoroutineScope,
) {
    private val definitions = mutableMapOf<String, BringIntoViewRequester>()

    fun registerDefinition(label: String, requester: BringIntoViewRequester) {
        definitions[label] = requester
    }

    fun unregisterDefinition(label: String, requester: BringIntoViewRequester) {
        if (definitions[label] === requester) definitions.remove(label)
    }

    fun bringDefinitionIntoView(label: String) {
        coroutineScope.launch { definitions[label]?.bringIntoView() }
    }
}

internal val LocalMarkdownFootnoteNavigationState =
    compositionLocalOf<MarkdownFootnoteNavigationState?> { null }

@Composable
internal fun rememberMarkdownFootnoteNavigationState(): MarkdownFootnoteNavigationState {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope) { MarkdownFootnoteNavigationState(coroutineScope) }
}
