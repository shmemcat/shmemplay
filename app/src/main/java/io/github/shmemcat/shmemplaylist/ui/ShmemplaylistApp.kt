package io.github.shmemcat.shmemplaylist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import io.github.shmemcat.shmemplaylist.ui.theme.ShmemplaylistTheme

@Composable
fun ShmemplaylistApp() {
    ShmemplaylistTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Shmemplaylist",
                    modifier = Modifier.semantics { testTag = "app-title" },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Playlist writes are not implemented.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
