package io.github.shmemcat.shmemplaylist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.shmemcat.shmemplaylist.ui.ShmemplaylistApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShmemplaylistApp()
        }
    }
}
