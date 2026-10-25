# unidocs — правила R8.
# SPI-плагины в будущем будут регистрироваться явно; конструкторы data-классов Model не отражаются.
-keepattributes SourceFile,LineNumberTable

# junrar (CBR) использует slf4j без бэкенда — байндинг не нужен
-dontwarn org.slf4j.impl.**
-dontwarn org.slf4j.**

# kotlinx.serialization
-keepclassmembers class tech.stpnk.unidocs.core.storage.** {
    *** Companion;
}
-keepclasseswithmembers class tech.stpnk.unidocs.core.storage.** {
    kotlinx.serialization.KSerializer serializer(...);
}
