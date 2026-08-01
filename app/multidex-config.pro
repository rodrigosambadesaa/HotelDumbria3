# AndroidJUnitRunner calls this before the target Application can install its
# secondary dex files on API 16-20. It must therefore remain in classes.dex.
-keep class androidx.tracing.** { *; }
