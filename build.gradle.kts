// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // We will define the ksp plugin here using an alias as well
    alias(libs.plugins.ksp) apply false
}