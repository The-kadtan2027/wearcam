# Kadb talks to adbd over a custom binary protocol; keep its entry points.
-keep class com.flyfishxu.kadb.** { *; }
-keep class okio.** { *; }

# TLS/pairing providers are loaded by name.
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# BouncyCastle providers (Kadb's pairing TLS).
-keep class org.bouncycastle.jce.provider.** { *; }
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.crypto.** { *; }
-dontwarn org.bouncycastle.**
