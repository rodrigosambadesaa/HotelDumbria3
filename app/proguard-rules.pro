# Gson/Jackson inspect generic signatures and annotations at runtime.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# These DTOs are still used by the legacy-import-compatible Gson paths.
-keepclassmembers class org.hoteldumbria.Persona { <fields>; }
-keepclassmembers class org.hoteldumbria.Usuario { <fields>; }
-keepclassmembers class org.hoteldumbria.Habitacion { <fields>; }
-keepclassmembers class org.hoteldumbria.Reserva { <fields>; }
-keepclassmembers class org.hoteldumbria.ReservaListado { <fields>; }
-keepclassmembers class org.hoteldumbria.ui.PoblacionesProvincias { <fields>; }
