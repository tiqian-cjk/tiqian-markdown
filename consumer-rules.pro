# Keep packed Compose TextUnit style derivation out of R8 class merging. On Android 9,
# merging these methods into an unrelated large host class can corrupt the unit tag.
-keep,allowobfuscation class org.tiqian.markdown.compose.MarkdownStyleKt {
    <methods>;
}

# Keep Material mapping out of large host Compose methods for the same Android 9 ART boundary.
-keep,allowobfuscation class org.tiqian.markdown.compose.MaterialMarkdownStyleKt {
    <methods>;
}
