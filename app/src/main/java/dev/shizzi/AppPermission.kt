package dev.shizzi

import android.Manifest
import android.os.Build

enum class AppPermission {
    NOTIFICATIONS,
    BATTERY_EXEMPTION,
}

data class PermissionStatus(
    val permission: AppPermission,
    val isGranted: Boolean,
)

val AppPermission.title: String
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> "الإشعارات"
        AppPermission.BATTERY_EXEMPTION -> "النشاط في الخلفية"
    }

val AppPermission.rationale: String
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> "مطلوب لإبقاء الجلسة تعمل"
        AppPermission.BATTERY_EXEMPTION -> "مطلوب للتحكم بالجلسة من تطبيقات أخرى"
    }

val AppPermission.manifestName: String?
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
        AppPermission.BATTERY_EXEMPTION -> null
    }

val AppPermission.isApplicable: Boolean
    get() = when (this) {
        AppPermission.NOTIFICATIONS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        AppPermission.BATTERY_EXEMPTION -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
