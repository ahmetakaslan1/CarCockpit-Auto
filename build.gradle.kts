// Tüm modüller için ortak plugin tanımları.
// KSP'yi burada apply false ile tanımlıyoruz; app modülü kendi build.gradle'ında apply eder.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}