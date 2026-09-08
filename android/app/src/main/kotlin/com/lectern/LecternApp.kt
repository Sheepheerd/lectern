package com.lectern

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class LecternApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // PDFBox loads its font metrics out of the APK's assets.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
