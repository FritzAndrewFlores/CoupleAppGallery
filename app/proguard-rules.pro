# Add project specific ProGuard rules here.
# Keep line number information for readable stack traces in release builds.
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Preserve classes and members annotated with @Keep (Firestore POJOs & models).
-keep @androidx.annotation.Keep class * { *; }

# Preserve Firestore custom property/document annotations.
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.DocumentId <methods>;
}

# Preserve enums serialized by name in SharedPreferences.
-keepclassmembers enum com.example.coupleappgallery.HomeCardType {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
