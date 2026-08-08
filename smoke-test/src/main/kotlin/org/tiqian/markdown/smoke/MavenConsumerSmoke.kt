package org.tiqian.markdown.smoke

import androidx.compose.runtime.Composable
import org.tiqian.compose.CjkText
import org.tiqian.markdown.MarkdownRenderDocument
import org.tiqian.markdown.TiqianMarkdown

/** Compiles only when the two public entry artifacts and their transitive modules are consumable. */
@Composable
fun MavenConsumerSmoke() {
    CjkText("提椠 Maven 消费验证")
    TiqianMarkdown(MarkdownRenderDocument(emptyList()))
}
