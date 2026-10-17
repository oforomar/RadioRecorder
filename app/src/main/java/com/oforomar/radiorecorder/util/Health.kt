package com.oforomar.radiorecorder.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.schedule.Scheduler

/** Things that would stop a recording from starting on time, and how to fix each. */
object Health {
    enum class Issue(val title: String, val why: String) {
        NOTIFICATIONS("السماح بالإشعارات", "لتعرف متى بدأ التسجيل ومتى حُفظ."),
        EXACT_ALARM("السماح بالمنبّهات", "ليبدأ التسجيل في موعده بالضبط."),
        BATTERY("إيقاف توفير البطارية للتطبيق", "حتى لا يوقف الهاتف التسجيل في الخلفية."),
        AUTOSTART("السماح بالتشغيل التلقائي", "مطلوب في بعض الهواتف ليعمل التطبيق وهو مغلق. فعّله ثم اضغط «تم»."),
    }

    private val autostartBrands = setOf(
        "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "vivo", "iqoo",
        "huawei", "honor", "infinix", "tecno", "itel", "transsion",
    )

    private val brand: String get() = Build.MANUFACTURER.lowercase()

    fun notificationsGranted(ctx: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun issues(ctx: Context): List<Issue> = buildList {
        if (!notificationsGranted(ctx)) add(Issue.NOTIFICATIONS)
        if (!Scheduler.canScheduleExact(ctx)) add(Issue.EXACT_ALARM)
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) add(Issue.BATTERY)
        if (brand in autostartBrands && !Repo.autostartAcknowledged) add(Issue.AUTOSTART)
    }

    /** Opens the right settings screen. Notifications use a runtime permission handled by the UI first. */
    fun fix(ctx: Context, issue: Issue) {
        val pkg = Uri.parse("package:${ctx.packageName}")
        val specific: List<Intent> = when (issue) {
            Issue.NOTIFICATIONS -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
            )
            Issue.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
            } else emptyList()
            Issue.BATTERY -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            )
            Issue.AUTOSTART -> autostartIntents()
        }
        val candidates = specific + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)

        for (intent in candidates) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // try the next one
            }
        }
    }

    private fun autostartIntents(): List<Intent> = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
    ).map { (pkg, cls) -> Intent().setComponent(ComponentName(pkg, cls)) }
}
