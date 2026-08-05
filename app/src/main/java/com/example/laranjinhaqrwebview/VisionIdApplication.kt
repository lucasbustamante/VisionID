package com.example.laranjinhaqrwebview

import android.app.Application
import android.os.Build
import android.webkit.WebView

class VisionIdApplication : Application() {
    override fun onCreate() {
        // O WebView principal roda em processo dedicado e precisa de diretório próprio.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Application.getProcessName().endsWith(":webcontent")
        ) {
            WebView.setDataDirectorySuffix("webcontent")
        }
        super.onCreate()
    }
}
