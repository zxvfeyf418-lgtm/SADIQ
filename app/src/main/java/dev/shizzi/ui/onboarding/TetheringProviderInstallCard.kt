package dev.shizzi.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.shizzi.CompatibilityState
import dev.shizzi.ui.theme.ShizziTheme
import dev.shizzi.ui.theme.themedSurface

@Composable
fun TetheringProviderInstallCard(state: CompatibilityState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .themedSurface(fill = ShizziTheme.colors.surface)
            .padding(ShizziTheme.spacing.lg),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.md),
    ) {
        ModuleStateIcon(state)

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ShizziTheme.spacing.xs),
        ) {
            Text(
                text = titleFor(state),
                style = ShizziTheme.typography.subheading,
                color = ShizziTheme.colors.onSurface,
            )

            Text(
                text = bodyFor(state),
                style = ShizziTheme.typography.body,
                color = ShizziTheme.colors.onSurfaceMuted,
            )

            if (state is CompatibilityState.InstallFailed) {
                InstallFailureDetail(state.reason)
            }
        }
    }
}

@Composable
private fun InstallFailureDetail(reason: String) {
    Text(
        text = breakableIdentifiers(reason),
        style = ShizziTheme.typography.log,
        color = ShizziTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = ShizziTheme.spacing.xs),
    )
}

private fun titleFor(state: CompatibilityState): String = when (state) {
    is CompatibilityState.Staged -> "أعد تشغيل هاتفك لإكمال التثبيت"
    is CompatibilityState.InstallFailed -> "لم يتم قبول الوحدة"
    else -> "وحدة المشاركة"
}

private fun bodyFor(state: CompatibilityState): String = when (state) {
    is CompatibilityState.Installing -> "جارٍ تثبيت الوحدة…"

    is CompatibilityState.Staged ->
        "الوحدة جاهزة وسيكتمل تثبيتها عند تشغيل هاتفك المرة القادمة. " +
            "ارجع إلى هنا بعدها لفحص التوافق."

    is CompatibilityState.InstallFailed ->
        "رفض هاتفك هذه الوحدة، وهذا يحدث في بعض إصدارات الشركات المصنّعة. " +
            "لم يتغير شيء."

    else -> "تم تنزيل الوحدة والتحقق منها، وهي جاهزة للتثبيت."
}
