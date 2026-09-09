# Tacklebox R8 / ProGuard rules
#
# Most libraries (Retrofit, OkHttp, Okio, Coil, kotlinx-coroutines, Compose, Room)
# ship their own consumer rules. The one thing R8 cannot infer is Gson's
# reflective (de)serialisation of our own DTOs, so we keep those explicitly.

# Generic signatures + annotations Gson needs for generic fields and @SerializedName.
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# Our network DTOs and Retrofit service interfaces are (de)serialised / proxied by
# reflection — keep them and their members intact.
-keep class uk.co.tacklebox.app.services.** { *; }
-keep interface uk.co.tacklebox.app.services.** { *; }

# Room entities and relation POJOs — keep field names stable.
-keep class uk.co.tacklebox.app.data.** { *; }

# Root-package DTOs that Gson (de)serialises reflectively: the portable photo-backup
# envelope, the OAuth token record and the planner forecast cache. Release minifies,
# so their field names MUST stay stable or written JSON becomes unreadable (the
# photo-backup decoder matches literal field-name keys → silent restore failure).
-keep class uk.co.tacklebox.app.BackupPayload { *; }
-keep class uk.co.tacklebox.app.BackupRecord { *; }
-keep class uk.co.tacklebox.app.SpeciesAuthorization { *; }
-keep class uk.co.tacklebox.app.PendingSpeciesSignIn { *; }
-keep class uk.co.tacklebox.app.TripForecast { *; }
-keep class uk.co.tacklebox.app.TripForecastDay { *; }

# Any field annotated with @SerializedName keeps its name even if its class is renamed.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
