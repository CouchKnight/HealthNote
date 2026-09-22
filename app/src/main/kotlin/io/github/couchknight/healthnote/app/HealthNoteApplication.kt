package io.github.couchknight.healthnote.app

import android.app.Application
import io.github.couchknight.healthnote.render.android.AndroidPdf

class HealthNoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPdf.init(this)
    }
}
