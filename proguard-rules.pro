# ---------------------------------------------------------------------------
# Aturan Obfuscation (Pengacakan) untuk Plugin CloudStream
# ---------------------------------------------------------------------------

# Pertahankan class utama yang dibutuhkan oleh aplikasi CloudStream (jangan diacak namanya)
-keep class **.*Plugin { *; }
-keep class com.premium.Config { *; }

# Pertahankan semua fungsi publik dari MainAPI (seperti loadLinks, search, dll)
-keepclassmembers class * extends com.lagradost.cloudstream3.MainAPI {
    public *;
}

# Jaga data class Jackson JSON agar mapping tidak error
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonProperty <fields>;
}
-keep class com.hexated.LicenseClient$* { *; }
-keep class com.MovieBox.LicenseClient$* { *; }
-keep class com.samehadaku.LicenseClient$* { *; }
-keep class com.Anichinmoe.LicenseClient$* { *; }
-keep class com.animesail.LicenseClient$* { *; }

# Sembunyikan informasi asli saat error
-renamesourcefileattribute SourceFile
-keepattributes LineNumberTable

# Obfuscate semaksimal mungkin untuk sisanya
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
