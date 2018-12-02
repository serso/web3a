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


# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**
# Okhttp rules https://github.com/square/okhttp/blob/master/okhttp/src/main/resources/META-INF/proguard/okhttp3.pro
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn okhttp3.internal.platform.ConscryptPlatform

# Java8 rules
-dontwarn java8.util.**

# JNR Posix rules
-dontwarn jnr.posix.**

# JFFI rules
-dontwarn com.kenai.**

# BouncyCastle rules
-keep class org.bouncycastle.**
-dontwarn org.bouncycastle.jce.provider.X509LDAPCertStoreSpi
-dontwarn org.bouncycastle.x509.util.LDAPStoreHelper

# Jackson rules: https://github.com/FasterXML/jackson-docs/wiki/JacksonOnAndroid
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }
-keep @com.fasterxml.jackson.annotation.JsonCreator class * { *; }
-keep @com.fasterxml.jackson.annotation.JsonValue class * { *; }
-keep class com.fasterxml.** { *; }
-keep class org.codehaus.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-keepclassmembers public final enum com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility {
    public static final com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility *;
}
-dontwarn com.fasterxml.jackson.databind.ext.*

# Keep Web3j classes serialized via reflection by Jackson
-keepclassmembers class org.web3j.protocol.** { *; }
-keepclassmembers class org.web3j.crypto.** { *; }
# Keep Web3j types as the library extracts data from type names at runtime (e.g. integer size of Int)
-keep class * extends org.web3j.abi.TypeReference
-keep class * extends org.web3j.abi.datatypes.Type

# Don't warn about Web3j's java.lang.SafeVarargs
-dontwarn java.lang.SafeVarargs
