package org.tiqian.markdown.compose

internal expect fun beginMarkdownTraceSection(name: String)

internal expect fun endMarkdownTraceSection()

internal inline fun <T> markdownTraceSection(name: String, block: () -> T): T {
    beginMarkdownTraceSection(name)
    return try {
        block()
    } finally {
        endMarkdownTraceSection()
    }
}
