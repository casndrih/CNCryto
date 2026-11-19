# Preserve reflection-based AndroidX security classes and app model metadata.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class androidx.security.crypto.** { *; }
-keep class com.cndrih.cncryto.ai.** { *; }

# Keep Chaquopy runtime entry points when R8 minifies the release variant.
-keep class com.chaquo.python.** { *; }
-keep class com.cndrih.cncryto.PythonForexEngine { *; }
