package com.example.data.service

enum class BackgroundModeState(val displayText: String) {
    STOPPED("STOPPED"),
    STARTING("STARTING"),
    RUNNING("RUNNING"),
    SUSPENDED("SUSPENDED"),
    RESTRICTED("RESTRICTED"),
    STOPPING("STOPPING"),
    ANDROID_TERMINATED("ANDROID_TERMINATED"),
    ERROR("ERROR")
}
