-keep class dev.shizzi.TetherService { *; }

-keep interface dev.shizzi.ITetherService { *; }
-keep class dev.shizzi.ITetherService$* { *; }

-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

-keep class dev.shizzi.App
-keep class dev.shizzi.MainActivity
-keep class dev.shizzi.SessionService

-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes Signature,InnerClasses,EnclosingMethod

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# AmneziaWG library: native methods are looked up by class name from libam-go.
-keep class org.amnezia.awg.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
