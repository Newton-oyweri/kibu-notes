# R8 keeps for the release build.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.kibu.notes.GitHubRelease { *; }
-keep class com.kibu.notes.GitHubAsset { *; }
-keep class com.kibu.notes.UpdateEngine { *; }
-keep class com.kibu.notes.data.models.** { *; }
