package dev.shizzi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue

@Composable
fun ClearLogToast(
    isConfirming: Boolean,
    toasts: ToastState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {

    val confirm by rememberUpdatedState(onConfirm)
    val cancel by rememberUpdatedState(onCancel)

    LaunchedEffect(isConfirming) {
        if (!isConfirming) {
            toasts.dismiss(ToastKeys.CLEAR_LOG)
            return@LaunchedEffect
        }

        var isAnswered = false

        toasts.show(
            Toast(
                key = ToastKeys.CLEAR_LOG,
                message = "مسح السجل؟",
                detail = "لا يمكن التراجع عن هذا الإجراء.",

                duration = ToastDuration.Indefinite,
                action = ToastAction("مسح") {
                    isAnswered = true
                    confirm()
                },
                onDismiss = { if (!isAnswered) cancel() },
            ),
        )
    }
}

fun clearedToast(problem: String?): Toast = when (problem) {
    null -> Toast(
        key = ToastKeys.CLEAR_LOG,
        message = "تم مسح السجل",
    )

    else -> Toast(
        key = ToastKeys.CLEAR_LOG,
        message = "تم مسح سجلات هذا التطبيق فقط",
        detail = problem,
        duration = ToastDuration.Indefinite,
    )
}
