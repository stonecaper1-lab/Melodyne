-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod

# kotlinx.serialization
-keep,includedescriptorclasses class org.melodyne.app.**$$serializer { *; }
-keepclassmembers class org.melodyne.app.** { *** Companion; }
-keepclasseswithmembers class org.melodyne.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit interfaces are called through reflection proxies
-keep interface org.melodyne.app.data.remote.HostedApi { *; }
-keep interface org.melodyne.app.data.lastfm.LastFmApi { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-keep interface org.melodyne.app.data.catalog.CatalogApi { *; }
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
