package io.github.couchknight.healthnote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.couchknight.healthnote.app.ui.HealthNoteRoot
import io.github.couchknight.healthnote.app.ui.HealthNoteViewModel

class MainActivity : ComponentActivity() {
    private val vm: HealthNoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { HealthNoteRoot(vm) }
    }
}
