# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ExoPlayer specific rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# libass (ass-media) - keep its classes intact, in particular its JNI-bound
# native methods, which R8 could otherwise rename/strip in a release build.
-keep class io.github.peerless2012.** { *; }
-dontwarn io.github.peerless2012.**

# Kotlin serialization
-keep class com.pira.ccloud.data.model.** { *; }