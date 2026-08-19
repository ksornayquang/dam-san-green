package com.damsan.green.data.repository

import com.damsan.green.data.model.ClassRanking
import com.damsan.green.data.model.TrashReport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale

class FirebaseRepository {

    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseDatabase = Firebase.database

    companion object {
        private val classNameCache = mutableMapOf<String, String>()
        private val roleCache = mutableMapOf<String, String>()
    }

    // ===== AUTH =====

    suspend fun loginWithClass(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("Đăng nhập thất bại")
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCurrentUser() = auth.currentUser

    fun isLoggedIn() = auth.currentUser != null

    fun signOut() = auth.signOut()

    fun logout() = auth.signOut()

    // Đăng ký tài khoản lớp (chỉ Admin dùng 1 lần)
    suspend fun registerClassAccount(
        className: String,
        email: String,
        password: String
    ): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("Tạo tài khoản thất bại")

            // Lưu thông tin vào /Users/{uid}
            db.reference.child("Users").child(uid).setValue(
                mapOf(
                    "uid" to uid,
                    "className" to className,
                    "email" to email,
                    "displayName" to "Lớp $className",
                    "role" to "student"
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Lấy tên lớp từ UID
    suspend fun getClassName(uid: String): String {
        classNameCache[uid]?.takeIf { it.isKnownClassName() }?.let { return it }
        return try {
            val snapshot = db.reference.child("Users").child(uid).get().await()
            val classNameFromProfile = snapshot.child("className")
                .getValue(String::class.java)
                ?.normalizeClassName()
                ?.takeIf { it.isKnownClassName() }

            if (classNameFromProfile != null) {
                classNameCache[uid] = classNameFromProfile
                return classNameFromProfile
            }

            val classNameFromEmail = classNameFromCurrentEmail(uid)
            if (classNameFromEmail != null) {
                classNameCache[uid] = classNameFromEmail
                repairMissingClassProfile(uid, classNameFromEmail, snapshot.child("role").getValue(String::class.java))
                return classNameFromEmail
            }

            "Unknown"
        } catch (e: Exception) {
            classNameFromCurrentEmail(uid)?.also { classNameCache[uid] = it } ?: "Unknown"
        }
    }

    // Lấy role (admin / student) từ UID
    suspend fun getUserRole(uid: String): String {
        roleCache[uid]?.let { return it }
        return try {
            val snapshot = db.reference.child("Users").child(uid).get().await()
            val role = snapshot.child("role").getValue(String::class.java) ?: "student"
            roleCache[uid] = role
            role
        } catch (e: Exception) {
            "student"
        }
    }

    // Kiểm tra xem user có phải admin không
    suspend fun isAdmin(uid: String): Boolean {
        return getUserRole(uid) == "admin"
    }

    private fun classNameFromCurrentEmail(uid: String): String? {
        val currentUser = auth.currentUser ?: return null
        if (currentUser.uid != uid) return null
        return currentUser.email.classNameFromEmail()
    }

    private fun String?.classNameFromEmail(): String? {
        val localPart = this
            ?.substringBefore("@")
            ?.normalizeClassName()
            ?: return null
        return localPart.takeIf { it.isKnownClassName() }
    }

    private fun String.normalizeClassName(): String {
        return trim()
            .replace(" ", "")
            .replace("-", "")
            .uppercase(Locale.US)
    }

    private fun String.isKnownClassName(): Boolean {
        return matches(Regex("""\d{2}A\d+""")) && this != "UNKNOWN"
    }

    private suspend fun repairMissingClassProfile(uid: String, className: String, existingRole: String?) {
        runCatching {
            val email = auth.currentUser?.email.orEmpty()
            db.reference.child("Users").child(uid).updateChildren(
                mapOf(
                    "uid" to uid,
                    "className" to className,
                    "email" to email,
                    "displayName" to "Lớp $className",
                    "role" to (existingRole?.takeIf { it.isNotBlank() } ?: "student")
                )
            ).await()
        }
    }

    // ===== TRASH REPORTS =====

    // Lưu báo cáo rác mới
    suspend fun saveTrashReport(report: TrashReport): Result<String> {
        return try {
            val reportsRef = db.reference.child("TrashReports")
            val newRef = reportsRef.push()
            val reportWithId = report.copy(id = newRef.key ?: "")
            newRef.setValue(reportWithId).await()
            Result.success(newRef.key ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Xóa báo cáo rác (Admin duyệt gian lận)
    suspend fun deleteTrashReport(reportId: String): Result<Unit> {
        return try {
            db.reference.child("TrashReports").child(reportId).removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Cập nhật trạng thái báo cáo (approved / rejected)
    suspend fun resetOperationalData(): Result<Unit> {
        return try {
            db.reference.child("TrashReports").removeValue().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> {
        return try {
            db.reference.child("TrashReports").child(reportId)
                .child("status").setValue(status).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Lấy tất cả báo cáo realtime
    fun getAllReportsFlow(): Flow<List<TrashReport>> = callbackFlow {
        val ref = db.reference.child("TrashReports")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reports = mutableListOf<TrashReport>()
                for (child in snapshot.children) {
                    try {
                        val report = child.getValue(TrashReport::class.java)
                        if (report != null) reports.add(report)
                    } catch (e: Exception) {
                        // Skip malformed data
                    }
                }
                trySend(reports.sortedByDescending { it.timestamp })
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // Lấy báo cáo của 1 lớp
    fun getClassReportsFlow(className: String): Flow<List<TrashReport>> = callbackFlow {
        val ref = db.reference.child("TrashReports")
            .orderByChild("className")
            .equalTo(className)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reports = mutableListOf<TrashReport>()
                for (child in snapshot.children) {
                    val report = child.getValue(TrashReport::class.java)
                    if (report != null) reports.add(report)
                }
                trySend(reports.sortedByDescending { it.timestamp })
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ===== LEADERBOARD =====

    // Tính bảng xếp hạng realtime
    fun getLeaderboardFlow(): Flow<List<ClassRanking>> = callbackFlow {
        val ref = db.reference.child("TrashReports")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val approvedReports = mutableListOf<TrashReport>()
                for (child in snapshot.children) {
                    val report = child.getValue(TrashReport::class.java) ?: continue
                    approvedReports += report
                }
                trySend(LeaderboardCalculator.calculate(approvedReports))
            }
            override fun onCancelled(error: DatabaseError) {
                trySend(emptyList())
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ===== MOCK DATA (để test) =====
    suspend fun insertMockData() {
        val mockReports = listOf(
            TrashReport(className = "11A1", reporterName = "Y Tú", imageUrl = "https://example.com/1.jpg",
                latitude = 12.900868, longitude = 108.291116, timestamp = System.currentTimeMillis() - 3600000),
            TrashReport(className = "11A1", reporterName = "Văn Quang", imageUrl = "https://example.com/2.jpg",
                latitude = 12.900900, longitude = 108.291200, timestamp = System.currentTimeMillis() - 7200000),
            TrashReport(className = "11A1", reporterName = "Quốc Tuấn", imageUrl = "https://example.com/3.jpg",
                latitude = 12.900950, longitude = 108.291000, timestamp = System.currentTimeMillis() - 10800000),
            TrashReport(className = "11A2", reporterName = "Hồng Sơn", imageUrl = "https://example.com/4.jpg",
                latitude = 12.900800, longitude = 108.291300, timestamp = System.currentTimeMillis() - 5400000),
            TrashReport(className = "11A2", reporterName = "Hồng Sơn", imageUrl = "https://example.com/5.jpg",
                latitude = 12.900750, longitude = 108.291500, timestamp = System.currentTimeMillis() - 9000000),
            TrashReport(className = "12A1", reporterName = "H'Linh", imageUrl = "https://example.com/6.jpg",
                latitude = 12.901000, longitude = 108.290900, timestamp = System.currentTimeMillis() - 1800000),
            TrashReport(className = "12A1", reporterName = "H'Linh", imageUrl = "https://example.com/7.jpg",
                latitude = 12.901100, longitude = 108.290800, timestamp = System.currentTimeMillis() - 2700000),
            TrashReport(className = "12A1", reporterName = "H'Linh", imageUrl = "https://example.com/8.jpg",
                latitude = 12.900900, longitude = 108.290900, timestamp = System.currentTimeMillis() - 900000),
            TrashReport(className = "12A1", reporterName = "H'Linh", imageUrl = "https://example.com/9.jpg",
                latitude = 12.901050, longitude = 108.291100, timestamp = System.currentTimeMillis() - 1200000),
            TrashReport(className = "10A1", reporterName = "Y Kô", imageUrl = "https://example.com/10.jpg",
                latitude = 12.900700, longitude = 108.291400, timestamp = System.currentTimeMillis() - 4500000),
        )

        for (report in mockReports) {
            saveTrashReport(report)
        }
    }

}
