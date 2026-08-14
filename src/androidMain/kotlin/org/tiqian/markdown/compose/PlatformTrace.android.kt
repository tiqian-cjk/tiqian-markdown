package org.tiqian.markdown.compose

import android.os.Trace

internal actual fun beginMarkdownTraceSection(name: String) {
    Trace.beginSection(name)
}

internal actual fun endMarkdownTraceSection() {
    Trace.endSection()
}
