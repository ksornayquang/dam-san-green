package com.damsan.green.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * AuthService — tách logic Auth ra khỏi FirebaseRepository.
 *
 * Quản lý:
 * - Login/Logout
 * - Register (Admin tạo tài khoản lớp)
 * - Password reset / change
 * - Google Sign-In (tùy chọn)
 *
 * Vẫn dùng Realtime Database cho user profile (giữ tương thích).
 */
class AuthService {

    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseDatabase = Firebase.database

    // ===== GETTERS =====

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    fun isLoggedIn(): Boolean = auth.currentUser != null

    fun getCurrentUid(): String? = auth.currentUser?.uid

    // ===== LOGIN =====

    /**
     * Đăng nhập bằng email/password (tài khoản lớp).
     * VD: email = "11a1@damsan.edu.vn", password = "classpassword"
     */
    suspend fun loginWithEmail(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("Đăng nhập thất bại")
            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đăng nhập bằng Google ID Token.
     * Token được lấy từ Google Sign-In Intent.
     */
    suspend fun loginWithGoogle(idToken: String): Result<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val uid = result.user?.uid ?: throw Exception("Đăng nhập Google thất bại")

            // Tạo profile nếu chưa có
            val snapshot = db.reference.child("Users").child(uid).get().await()
            if (!snapshot.exists()) {
                val user = result.user!!
                db.reference.child("Users").child(uid).setValue(
                    mapOf(
                        "uid" to uid,
                        "email" to (user.email ?: ""),
                        "displayName" to (user.displayName ?: "Người dùng"),
                        "role" to "student",
                        "loginMethod" to "google"
                    )
                ).await()
            }

            Result.success(uid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== REGISTER (Admin) =====

    /**
     * Đăng ký tài khoản lớp mới — chỉ Admin dùng.
     * VD: className = "11A1", email = "11a1@damsan.edu.vn"
     */
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

    // ===== PASSWORD =====

    /**
     * Gửi email reset mật khẩu qua Firebase Auth.
     * User nhập email → Firebase gửi link reset.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đổi mật khẩu — cần re-authenticate trước.
     * @param currentPassword Mật khẩu hiện tại (để xác thực lại)
     * @param newPassword Mật khẩu mới (tối thiểu 6 ký tự)
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: throw Exception("Chưa đăng nhập")
            val email = user.email ?: throw Exception("Không có email")

            // Re-authenticate
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()

            // Update password
            user.updatePassword(newPassword).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đổi mật khẩu đơn giản — KHÔNG re-authenticate.
     * Dùng khi user vừa đăng nhập gần đây.
     */
    suspend fun changePasswordSimple(newPassword: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: throw Exception("Chưa đăng nhập")
            user.updatePassword(newPassword).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===== LOGOUT =====

    fun logout() = auth.signOut()

    fun signOut() = auth.signOut()

    // ===== HELPERS =====

    /**
     * Kiểm tra email đã tồn tại chưa.
     * Dùng khi Admin tạo tài khoản lớp mới.
     */
    suspend fun checkEmailExists(email: String): Boolean {
        return try {
            val methods = auth.fetchSignInMethodsForEmail(email).await()
            !methods.signInMethods.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
