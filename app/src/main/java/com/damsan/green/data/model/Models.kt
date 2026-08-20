package com.damsan.green.data.model

import com.google.firebase.database.PropertyName

// Model báo cáo rác thải
data class TrashReport(
    val id: String = "",
    val className: String = "",        // VD: "11A1"
    val reporterName: String = "",     // Tên người báo cáo
    val imageUrl: String = "",         // Link Cloudinary
    @get:PropertyName("image_before_url")
    @set:PropertyName("image_before_url")
    var imageBeforeUrl: String = "",   // Ảnh hiện trạng trước khi thu gom
    @get:PropertyName("image_after_url")
    @set:PropertyName("image_after_url")
    var imageAfterUrl: String = "",    // Ảnh sau khi bỏ đúng thùng
    @get:PropertyName("trash_type")
    @set:PropertyName("trash_type")
    var trashType: String = "",        // recyclable / household
    val latitude: Double = 0.0,        // Tọa độ GPS
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,          // Unix timestamp
    val address: String = "",          // Địa chỉ mô tả (tùy chọn)
    val demoMode: Boolean = false,      // Cho phép trình diễn ngoài bán kính trường
    val points: Int = 10,              // Giá trị legacy; báo cáo mới nhận 3-15 điểm theo AI
    val status: String = "pending",    // pending / approved / rejected
    val aiIsTrash: Boolean = false,
    val aiTrashName: String = "",
    val aiCategory: String = "",
    val aiReviewStatus: String = "",   // auto_approved / needs_review / failed
    val aiWasteType: String = "",
    val aiDetectedItems: Int = 0,
    val aiEstimatedKg: Double = 0.0,
    val aiConfidence: Int = 0,
    val aiReason: String = "",
    val aiWarnings: String = "",
    val aiAutoApproved: Boolean = false,
    val aiAfterIsDisposed: Boolean = false,
    val aiAnalyzedAt: Long = 0L
)

// Model xếp hạng lớp
data class ClassRanking(
    val className: String = "",
    val totalPoints: Int = 0,
    val reportCount: Int = 0,
    val rank: Int = 0,
    val lastActivity: Long = 0L
)

// Model thông tin người dùng (theo lớp)
data class ClassUser(
    val uid: String = "",
    val className: String = "",        // VD: "11A1"
    val email: String = "",            // VD: "11a1@damsan.edu.vn"
    val displayName: String = "",      // VD: "Lớp 11A1"
    val role: String = "student"       // student / admin
)

// Enum trạng thái upload
enum class UploadState {
    IDLE, UPLOADING, SUCCESS, ERROR
}
