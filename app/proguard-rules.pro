# Preserve reflection-based AndroidX security classes and app model metadata.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class androidx.security.crypto.** { *; }
-keep class com.cndrih.cncryto.ai.** { *; }
