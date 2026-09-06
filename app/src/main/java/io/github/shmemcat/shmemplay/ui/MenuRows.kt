package io.github.shmemcat.shmemplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable internal fun MenuAction(label: String, icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            CompositionLocalProvider(LocalContentColor provides color) { PlayerIcon(icon, null, Modifier.size(23.dp)) }
            Spacer(Modifier.width(16.dp))
        }
        Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun RadioOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun CheckOption(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}


@Composable internal fun BackChevronButton(onClick: () -> Unit, enabled: Boolean = true) {
    Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = if (enabled) 1f else .38f)) {
                PlayerIcon(io.github.shmemcat.shmemplay.R.drawable.ic_chevron_down, "Back", Modifier.size(25.dp).rotate(90f))
            }
        }
    }
}
