package dev.shizzi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shizzi.R
import dev.shizzi.ShizukuGate
import dev.shizzi.ShizukuState
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.themedSurface

private val BrandIconSize = 40.dp

@Composable
fun ShizukuCard(state: ShizukuState, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .themedSurface(fill = ShizziTheme.colors.surface)
            .padding(ShizziTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.lg),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shizuku),
            contentDescription = null,
            tint = ShizziTheme.colors.onSurface,
            modifier = Modifier.size(BrandIconSize),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.sm),
        ) {
            StatusRow(name = "الحالة", value = statusText(state))
            StatusRow(name = "الخدمة", value = serviceText(state))
            StatusRow(name = "الإصدار", value = ShizukuGate.installedVersion() ?: "غير مثبّت")

            if (state is ShizukuState.PermissionRequired) {
                GrantButton(onGrant)
            }
        }
    }
}

@Composable
private fun StatusRow(name: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = name,
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.onSurfaceMuted,
        )

        Text(
            text = value,
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = ShizziTheme.spacing.md),
        )
    }
}

@Composable
private fun GrantButton(onGrant: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ShizziTheme.spacing.sm)
            .themedSurface(fill = ShizziTheme.colors.primary)
            .clickable(onClick = onGrant)
            .padding(vertical = ShizziTheme.spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "منح الإذن",
            style = ShizziTheme.typography.label,
            color = ShizziTheme.colors.onPrimary,
        )
    }
}

private fun statusText(state: ShizukuState): String = when (state) {
    is ShizukuState.Ready -> "جاهز"
    ShizukuState.NotInstalled -> "غير مثبّت"
    ShizukuState.NotRunning -> "غير قيد التشغيل"
    ShizukuState.PermissionRequired -> "الإذن مطلوب"
}

private fun serviceText(state: ShizukuState): String = when (state) {
    is ShizukuState.Ready -> ShizukuGate.shortUid(state.uid)
    else -> "—"
}
