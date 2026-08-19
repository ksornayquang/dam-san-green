package com.damsan.green.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * SettingsService — quản lý settings/branding qua Firestore.
 *
 * Firestore collection: "settings"
 * Document: "branding" — chứa bannerUrl, logoUrl, schoolName, appName
 *
 * Tách riêng khỏi Realtime Database vì:
 * 1. Settings ít thay đổi, phù hợp Firestore document model
 * 2. Không cần realtime listener cho settings
 * 3. Firestore query linh hoạt hơn cho settings phức tạp
 */
class SettingsService {

    private val firestore: FirebaseFirestore = Firebase.firestore
    private val realtimeDb: FirebaseDatabase = Firebase.database

    // ===== BRANDING =====

    /**
     * Lấy thông tin branding (banner URL, logo URL, tên trường, tên app).
     */
    suspend fun getBranding(): BrandingSettings {
        val realtimeSettings = runCatching {
            brandingRef().get().await().toBrandingSettings()
        }.getOrNull()
        if (realtimeSettings != null) return realtimeSettings

        return runCatching {
            firestore.collection("settings").document("branding")
                .get()
                .await()
                .toBrandingSettings()
                ?: BrandingSettings.DEFAULT
        }.getOrDefault(BrandingSettings.DEFAULT)
    }

    /**
     * Cập nhật branding — chỉ Admin dùng.
     *
     * Realtime Database là nguồn ghi chính vì role admin của app đang nằm ở
     * /Users/{uid}/role. Firestore chỉ được ghi mirror nếu server rules cho phép.
     */
    suspend fun updateBranding(settings: BrandingSettings): Result<Unit> {
        val data = settings.toFirebaseMap()
        return runCatching {
            brandingRef().setValue(data).await()
            runCatching {
                firestore.collection("settings").document("branding").set(data).await()
            }
            Unit
        }
    }

    /**
     * Cập nhật chỉ banner URL.
     */
    suspend fun updateBannerUrl(url: String): Result<Unit> {
        val current = getBranding()
        return updateBranding(current.copy(bannerUrl = url))
    }

    // ===== APP SETTINGS =====

    /**
     * Lấy cài đặt app (thông báo, theme, v.v.).
     */
    suspend fun getAppSettings(): AppSettings {
        val realtimeSettings = runCatching {
            appSettingsRef().get().await().toAppSettings()
        }.getOrNull()
        if (realtimeSettings != null) return realtimeSettings

        return runCatching {
            val doc = firestore.collection("settings").document("app").get().await()
            if (doc.exists()) {
                AppSettings(
                    notificationsEnabled = doc.getBoolean("notificationsEnabled") ?: true,
                    pointsPerReport = doc.getLong("pointsPerReport")?.toInt() ?: 10,
                    maxDailyReports = doc.getLong("maxDailyReports")?.toInt() ?: 10
                )
            } else {
                AppSettings.DEFAULT
            }
        }.getOrDefault(AppSettings.DEFAULT)
    }

    /**
     * Cập nhật cài đặt app — chỉ Admin dùng.
     */
    suspend fun updateAppSettings(settings: AppSettings): Result<Unit> {
        val data = mapOf(
            "notificationsEnabled" to settings.notificationsEnabled,
            "pointsPerReport" to settings.pointsPerReport,
            "maxDailyReports" to settings.maxDailyReports,
            "updatedAt" to System.currentTimeMillis()
        )
        return runCatching {
            appSettingsRef().setValue(data).await()
            runCatching {
                firestore.collection("settings").document("app").set(data).await()
            }
            Unit
        }
    }

    private fun brandingRef() = realtimeDb.reference.child("Settings").child("branding")

    private fun appSettingsRef() = realtimeDb.reference.child("Settings").child("app")

    private fun BrandingSettings.toFirebaseMap(): Map<String, Any> = mapOf(
        "bannerUrl" to bannerUrl,
        "logoUrl" to logoUrl,
        "schoolName" to schoolName,
        "appName" to appName,
        "schoolImageUrls" to schoolImageUrls,
        "schoolBulletins" to schoolBulletins,
        "updatedAt" to System.currentTimeMillis()
    )

    private fun DataSnapshot.toBrandingSettings(): BrandingSettings? {
        if (!exists()) return null
        return BrandingSettings(
            bannerUrl = child("bannerUrl").getValue(String::class.java).orEmpty(),
            logoUrl = child("logoUrl").getValue(String::class.java).orEmpty(),
            schoolName = child("schoolName").getValue(String::class.java)
                ?: BrandingSettings.DEFAULT.schoolName,
            appName = child("appName").getValue(String::class.java)
                ?: BrandingSettings.DEFAULT.appName,
            schoolImageUrls = child("schoolImageUrls").children
                .mapNotNull { it.getValue(String::class.java) }
                .filter { it.isNotBlank() },
            schoolBulletins = child("schoolBulletins").children
                .mapNotNull { it.getValue(String::class.java) }
                .filter { it.isNotBlank() }
        )
    }

    private fun DocumentSnapshot.toBrandingSettings(): BrandingSettings? {
        if (!exists()) return null
        return BrandingSettings(
            bannerUrl = getString("bannerUrl").orEmpty(),
            logoUrl = getString("logoUrl").orEmpty(),
            schoolName = getString("schoolName") ?: BrandingSettings.DEFAULT.schoolName,
            appName = getString("appName") ?: BrandingSettings.DEFAULT.appName,
            schoolImageUrls = (get("schoolImageUrls") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.filter { it.isNotBlank() }
                ?: emptyList(),
            schoolBulletins = (get("schoolBulletins") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }

    private fun DataSnapshot.toAppSettings(): AppSettings? {
        if (!exists()) return null
        return AppSettings(
            notificationsEnabled = child("notificationsEnabled").getValue(Boolean::class.java) ?: true,
            pointsPerReport = (child("pointsPerReport").value as? Number)?.toInt() ?: 10,
            maxDailyReports = (child("maxDailyReports").value as? Number)?.toInt() ?: 10
        )
    }
}

/**
 * Data class cho branding settings.
 */
data class BrandingSettings(
    val bannerUrl: String = "",
    val logoUrl: String = "",
    val schoolName: String = "Trường PTDTNT THPT Đam San",
    val appName: String = "Dam San Green",
    val schoolImageUrls: List<String> = emptyList(),
    val schoolBulletins: List<String> = emptyList()
) {
    companion object {
        val DEFAULT = BrandingSettings()
    }
}

/**
 * Data class cho app settings.
 */
data class AppSettings(
    val notificationsEnabled: Boolean = true,
    val pointsPerReport: Int = 10,
    val maxDailyReports: Int = 10
) {
    companion object {
        val DEFAULT = AppSettings()
    }
}
