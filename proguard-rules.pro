# Web3j
-keep class org.web3j.** { *; }
-dontwarn org.web3j.**

# BitcoinJ
-keep class org.bitcoinj.** { *; }
-dontwarn org.bitcoinj.**

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.hackerai.walletseeker.data.model.** { *; }
-keep class com.hackerai.walletseeker.domain.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keep class androidx.compose.** { *; }

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**