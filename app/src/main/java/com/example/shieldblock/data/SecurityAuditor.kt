package com.example.shieldblock.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class SecurityReport(
    val highRiskAppsCount: Int,
    val securityScore: Int,
    val risks: List<String>
)

class SecurityAuditor(private val context: Context) {
    fun runAudit(): SecurityReport {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        var highRiskCount = 0
        val risks = mutableListOf<String>()

        apps.forEach { app ->
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                val pkgInfo = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                val permissions = pkgInfo.requestedPermissions ?: emptyArray()

                var riskPoints = 0
                if (permissions.contains("android.permission.READ_SMS")) riskPoints += 20
                if (permissions.contains("android.permission.READ_CONTACTS")) riskPoints += 15
                if (permissions.contains("android.permission.ACCESS_FINE_LOCATION")) riskPoints += 10
                if (permissions.contains("android.permission.RECORD_AUDIO")) riskPoints += 25

                if (riskPoints >= 30) {
                    highRiskCount++
                }
            }
        }

        val score = (100 - (highRiskCount * 5)).coerceIn(0, 100)
        if (highRiskCount > 0) risks.add("$highRiskCount apps have excessive data permissions.")

        return SecurityReport(highRiskCount, score, risks)
    }
}
