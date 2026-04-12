package com.example.shieldblock.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class SecurityReport(
    val highRiskAppsCount: Int,
    val securityScore: Int,
    val risks: List<String>,
    val appRisks: List<Pair<String, String>>
)

class SecurityAuditor(private val context: Context) {
    fun runAudit(): SecurityReport {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var highRiskCount = 0
        val risks = mutableListOf<String>()
        val appRisks = mutableListOf<Pair<String, String>>()

        apps.forEach { app ->
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                val pkgInfo = try { pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS) } catch(e: Exception) { null }
                val permissions = pkgInfo?.requestedPermissions ?: emptyArray()

                val foundPerms = mutableListOf<String>()
                var riskPoints = 0
                if (permissions.contains("android.permission.READ_SMS")) { riskPoints += 20; foundPerms.add("SMS") }
                if (permissions.contains("android.permission.READ_CONTACTS")) { riskPoints += 15; foundPerms.add("Contacts") }
                if (permissions.contains("android.permission.ACCESS_FINE_LOCATION")) { riskPoints += 10; foundPerms.add("Location") }
                if (permissions.contains("android.permission.RECORD_AUDIO")) { riskPoints += 25; foundPerms.add("Microphone") }
                if (permissions.contains("android.permission.CAMERA")) { riskPoints += 15; foundPerms.add("Camera") }

                if (riskPoints >= 30) {
                    highRiskCount++
                    val appLabel = app.loadLabel(pm).toString()
                    appRisks.add(appLabel to foundPerms.joinToString(", "))
                }
            }
        }

        val score = (100 - (highRiskCount * 5)).coerceIn(0, 100)
        if (highRiskCount > 0) risks.add("Found $highRiskCount apps with high-risk permission profiles.")

        return SecurityReport(highRiskCount, score, risks, appRisks)
    }
}
