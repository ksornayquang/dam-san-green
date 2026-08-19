package com.damsan.green.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.damsan.green.R
import com.damsan.green.data.repository.BrandingSettings
import com.damsan.green.data.repository.FirebaseRepository
import com.damsan.green.data.repository.SettingsService
import com.damsan.green.ui.MainActivity
import com.damsan.green.ui.admin.AdminActivity
import com.damsan.green.ui.guest.GuestActivity
import com.damsan.green.ui.showDamSanActionDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val repo = FirebaseRepository()
    private val settingsService = SettingsService()
    private var adminTapCount = 0
    private var lastAdminTapAt = 0L

    private val classOptions = listOf(
        "10A1", "10A2", "10A3", "10A4", "10A5", "10A6",
        "11A1", "11A2", "11A3", "11A4", "11A5", "11A6",
        "12A1", "12A2", "12A3", "12A4", "12A5", "12A6"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val spinnerClass = findViewById<Spinner>(R.id.spinnerClass)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<FrameLayout>(R.id.btnLoginContainer)
        val btnLoginText = findViewById<TextView>(R.id.btnLoginText)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvError = findViewById<TextView>(R.id.tvError)

        loadBranding()
        setupHiddenAdminEntry()
        setupClassSpinner(spinnerClass)

        btnLogin.setOnClickListener {
            val selectedClass = spinnerClass.selectedItem.toString()
            val password = etPassword.text.toString().trim()

            if (password.isEmpty()) {
                showSnackbar("Vui lòng nhập mật khẩu", true)
                return@setOnClickListener
            }

            val email = "${selectedClass.lowercase()}@damsan.edu.vn"
            tvError.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            btnLoginText.visibility = View.GONE
            btnLogin.isEnabled = false

            lifecycleScope.launch {
                val result = repo.loginWithClass(email, password)
                progressBar.visibility = View.GONE
                btnLoginText.visibility = View.VISIBLE
                btnLogin.isEnabled = true

                result.fold(
                    onSuccess = {
                        val uid = repo.getCurrentUser()?.uid ?: ""
                        val role = if (uid.isNotEmpty()) repo.getUserRole(uid) else "student"

                        val intent = when (role) {
                            "admin" -> Intent(this@LoginActivity, AdminActivity::class.java)
                            else -> Intent(this@LoginActivity, MainActivity::class.java)
                        }
                        startActivity(intent)
                        finish()
                    },
                    onFailure = { e ->
                        val errorMsg = when {
                            e.message?.contains("password", ignoreCase = true) == true -> "Sai mật khẩu!"
                            e.message?.contains("user", ignoreCase = true) == true -> "Tài khoản chưa được tạo!"
                            e.message?.contains("network", ignoreCase = true) == true -> "Không có mạng!"
                            else -> "Lỗi: ${e.message}"
                        }
                        showSnackbar(errorMsg, true)
                        tvError.text = errorMsg
                        tvError.visibility = View.VISIBLE
                    }
                )
            }
        }

        findViewById<View>(R.id.btnGuestMode)?.setOnClickListener {
            startActivity(Intent(this, GuestActivity::class.java))
            finish()
        }

        findViewById<TextView>(R.id.tvForgotPassword)?.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun setupClassSpinner(spinnerClass: Spinner) {
        val adapter = ClassSpinnerAdapter(this, classOptions)
        spinnerClass.adapter = adapter
        spinnerClass.fitClassDropdownToField()
        val defaultIndex = classOptions.indexOf("11A1")
        spinnerClass.setSelection(defaultIndex)
        adapter.setSelectedPosition(defaultIndex)
        spinnerClass.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                adapter.setSelectedPosition(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun showSnackbar(message: String, isError: Boolean) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(
            ContextCompat.getColor(this, if (isError) R.color.ds_error else R.color.ds_primary)
        )
        snackbar.show()
    }

    private fun setupHiddenAdminEntry() {
        findViewById<View>(R.id.logoContainer)?.setOnClickListener {
            val now = SystemClock.elapsedRealtime()
            if (now - lastAdminTapAt > 2500) {
                adminTapCount = 0
            }
            lastAdminTapAt = now
            adminTapCount++

            if (adminTapCount >= 7) {
                adminTapCount = 0
                showAdminLoginDialog()
            }
        }
    }

    private fun showAdminLoginDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_admin_login, null)
        val tilEmail = dialogView.findViewById<TextInputLayout>(R.id.tilAdminEmail)
        val tilPassword = dialogView.findViewById<TextInputLayout>(R.id.tilAdminPassword)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.etAdminEmail)
        val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etAdminPassword)
        val progress = dialogView.findViewById<ProgressBar>(R.id.progressAdminLogin)

        showDamSanActionDialog(
            title = "Đăng nhập Admin",
            message = "Cửa sau quản trị dành cho giáo viên/admin, tránh học sinh bấm nhầm vào khu vực duyệt dữ liệu.",
            iconRes = R.drawable.ic_shield,
            positiveText = "Đăng nhập",
            negativeText = "Huỷ",
            contentView = dialogView,
            dismissOnPositive = false
        ) { dialog ->
            tilEmail.error = null
            tilPassword.error = null

            val email = etEmail.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString()?.trim().orEmpty()

            var hasError = false
            if (email.isBlank()) {
                tilEmail.error = "Nhập email admin"
                hasError = true
            }
            if (password.isBlank()) {
                tilPassword.error = "Nhập mật khẩu admin"
                hasError = true
            }
            if (hasError) return@showDamSanActionDialog

            setAdminDialogLoading(progress, true)
            lifecycleScope.launch {
                val result = repo.loginWithClass(email, password)
                result.fold(
                    onSuccess = {
                        val uid = repo.getCurrentUser()?.uid.orEmpty()
                        val role = if (uid.isNotEmpty()) repo.getUserRole(uid) else "student"
                        if (role == "admin") {
                            dialog.dismiss()
                            startActivity(Intent(this@LoginActivity, AdminActivity::class.java))
                            finish()
                        } else {
                            repo.signOut()
                            tilEmail.error = "Tài khoản này chưa có quyền admin"
                            setAdminDialogLoading(progress, false)
                        }
                    },
                    onFailure = { error ->
                        tilPassword.error = error.message ?: "Không đăng nhập được"
                        setAdminDialogLoading(progress, false)
                    }
                )
            }
        }
    }

    private fun setAdminDialogLoading(progress: ProgressBar, isLoading: Boolean) {
        progress.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun loadBranding() {
        lifecycleScope.launch {
            val branding = settingsService.getBranding()
            applyBranding(branding)
        }
    }

    private fun applyBranding(branding: BrandingSettings) {
        findViewById<TextView>(R.id.tvLoginAppName)?.text = branding.appName
        findViewById<TextView>(R.id.tvLoginSchoolName)?.text = branding.schoolName

        val logo = findViewById<ImageView>(R.id.ivLoginLogo)
        logo.visibility = View.VISIBLE
        logo.setImageResource(R.drawable.logo_damsan_green)
        if (branding.logoUrl.isNotBlank()) {
            Glide.with(this)
                .load(branding.logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(logo)
        }

        val banner = findViewById<ImageView>(R.id.ivLoginBanner)
        val hasDedicatedBanner = branding.bannerUrl.isNotBlank() &&
            !branding.bannerUrl.equals(branding.logoUrl, ignoreCase = true)

        if (hasDedicatedBanner) {
            banner?.visibility = View.VISIBLE
            Glide.with(this)
                .load(branding.bannerUrl)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(banner)
        } else {
            banner?.visibility = View.GONE
        }
    }
}
