# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\tools\adt-bundle-windows-x86_64-20131030\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-dontobfuscate

# ---------------------------------------------------------------------------
# P3WIN
# ---------------------------------------------------------------------------
# P3 не использует рефлексию по классам ядра: экраны открываются прямыми
# ссылками (MainActivity.class, ControlsEditorActivity.class), поэтому R8
# видит зависимость и не вырезает их.
#
# Правила ниже страхуют точки входа, которые вызывает Android по имени из
# манифеста и которые нельзя терять в release: без них игра запускается, но
# «Открыть ядро» или редактор раскладок могут отвалиться только в подписанной
# сборке — самый дорогой вид дефекта.
-keep class com.winlator.MainActivity { *; }
-keep class com.winlator.XServerDisplayActivity { *; }
-keep class com.winlator.ControlsEditorActivity { *; }
-keep class com.winlator.ExternalControllerBindingsActivity { *; }

# P3-оверлей HUD создаётся кодом и цепляется к root-view игры по id.
# Обфускация имён полей не нужна, но keep делает стектрейсы читаемыми.
-keep class com.p3win.core.engine.** { *; }
