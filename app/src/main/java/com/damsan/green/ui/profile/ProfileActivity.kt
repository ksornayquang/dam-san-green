package com.damsan.green.ui.profile

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.damsan.green.R
import com.damsan.green.data.model.TrashReport
import com.damsan.green.data.repository.FirebaseRepository
import com.damsan.green.ui.auth.LoginActivity
import com.damsan.green.ui.showDamSanActionDialog
import com.damsan.green.ui.showDamSanConfirmDialog
import com.damsan.green.ui.showDamSanInfoDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private val repo = FirebaseRepository()
    private var currentClassName = "Unknown"
    private var currentEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        findViewById<android.widget.ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }

        loadProfile()
        setupMenuClicks()
    }

    private fun loadProfile() {
        val uid = repo.getCurrentUser()?.uid ?: return
        currentEmail = repo.getCurrentUser()?.email.orEmpty()
        currentClassName = classNameFromEmail(currentEmail)
        renderProfile(points = 0, reportCount = 0, rank = null)

        lifecycleScope.launch {
            val firebaseClassName = repo.getClassName(uid).fixVietnameseMojibake()
            if (firebaseClassName != "Unknown") {
                currentClassName = firebaseClassName
                renderProfile(points = 0, reportCount = 0, rank = null)
            }

            repo.getLeaderboardFlow().collectLatest { rankings ->
                val myRanking = rankings.find { it.className.fixVietnameseMojibake() == currentClassName }
                val points = myRanking?.totalPoints ?: 0
                val reportCount = myRanking?.reportCount ?: 0
                val rank = myRanking?.rank ?: rankings.size.takeIf { it > 0 }?.plus(1)

                runOnUiThread {
                    renderProfile(points, reportCount, rank)
                }
            }
        }
    }

    private fun renderProfile(points: Int, reportCount: Int, rank: Int?) {
        findViewById<TextView>(R.id.tvProfileClass)?.text = "Lớp $currentClassName"
        findViewById<TextView>(R.id.tvProfileEmail)?.text = currentEmail
        findViewById<TextView>(R.id.statPoints)?.text = "$points"
        findViewById<TextView>(R.id.statReports)?.text = "$reportCount"
        findViewById<TextView>(R.id.statRank)?.text = rank?.let { "#$it" } ?: "#-"
    }

    private fun setupMenuClicks() {
        bindMenuRow(R.id.menuClassInfo) {
            showDamSanInfoDialog(
                title = "Thông tin lớp",
                message = buildClassInfo(),
                iconRes = R.drawable.ic_user
            )
        }

        bindMenuRow(R.id.menuHistory) {
            showHistoryDialog()
        }

        bindMenuRow(R.id.menuChangePass) {
            showChangePasswordDialog()
        }

        bindMenuRow(R.id.menuAbout) {
            showDamSanInfoDialog(
                title = "Dam San Green",
                message = """
                    Phiên bản: 1.0.0

                    Ứng dụng quản lý môi trường và thi đua nhặt rác dành cho học sinh Trường PTDTNT THPT Đam San.

                    Xã EaDrông, tỉnh Đắk Lắk
                    Phong trào: Trường học Xanh - Sạch - Đẹp

                    Được phát triển bởi học sinh Trường Đam San.
                """.trimIndent(),
                iconRes = R.drawable.ic_leaf
            )
        }

        bindMenuRow(R.id.menuFeedback) {
            showDamSanInfoDialog(
                title = "Góp ý & Báo lỗi",
                message = "Liên hệ giáo viên phụ trách CNTT để góp ý hoặc báo lỗi.",
                iconRes = R.drawable.ic_feedback
            )
        }

        findViewById<View>(R.id.btnLogout)?.setOnClickListener {
            showDamSanConfirmDialog(
                title = "Đăng xuất",
                message = "Bạn chắc chắn muốn đăng xuất khỏi tài khoản lớp?",
                iconRes = R.drawable.ic_logout,
                positiveText = "Đăng xuất",
                negativeText = "Huỷ",
                danger = true
            ) {
                repo.logout()
                startActivity(
                    Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
                finish()
            }
        }
    }

    private fun bindMenuRow(rowId: Int, onClick: () -> Unit) {
        val row = findViewById<View>(rowId) ?: return
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener { onClick() }
        if (row is ViewGroup) {
            row.forwardChildClicksTo(row)
        }
    }

    private fun ViewGroup.forwardChildClicksTo(target: View) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            child.isClickable = true
            child.isFocusable = false
            child.setOnClickListener { target.performClick() }
            if (child is ViewGroup) {
                child.forwardChildClicksTo(target)
            }
        }
    }

    private fun buildClassInfo(): String {
        return """
            Lớp: $currentClassName
            Email đăng nhập: $currentEmail

            Mỗi báo cáo rác được duyệt sẽ cộng 3–15 điểm theo khối lượng AI ước tính.

            Điểm được cộng dồn realtime và hiển thị trên bảng xếp hạng.
        """.trimIndent()
    }

    private fun showHistoryDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_report_history, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.btnHistoryClose)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            decorView.setPadding(0, 0, 0, 0)
            setDimAmount(0.48f)
            setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        lifecycleScope.launch {
            val reports = repo.getClassReportsFlow(currentClassName).first()
            runOnUiThread {
                renderHistoryDialog(dialogView, reports)
            }
        }
    }

    private fun renderHistoryDialog(dialogView: View, reports: List<TrashReport>) {
        val sortedReports = reports.sortedByDescending { it.timestamp }
        val approvedCount = sortedReports.count { it.status == "approved" }
        val pendingCount = sortedReports.count { it.status == "pending" }
        val rejectedCount = sortedReports.count { it.status == "rejected" }
        val approvedPoints = sortedReports
            .filter { it.status == "approved" }
            .sumOf { it.points.coerceAtLeast(0) }
        val rows = dialogView.findViewById<LinearLayout>(R.id.historyRows)
        val scrollView = dialogView.findViewById<ScrollView>(R.id.historyScroll)
        val emptyView = dialogView.findViewById<TextView>(R.id.tvHistoryEmpty)
        val moreView = dialogView.findViewById<TextView>(R.id.tvHistoryMore)

        dialogView.findViewById<TextView>(R.id.tvHistoryTitle)?.text = "Lịch sử báo cáo"
        dialogView.findViewById<TextView>(R.id.tvHistorySubtitle)?.text =
            "Lớp $currentClassName · ${sortedReports.size} báo cáo đã gửi"
        dialogView.findViewById<TextView>(R.id.tvHistorySummary)?.text =
            "Đã duyệt $approvedCount · Chờ $pendingCount · Cần xem lại $rejectedCount · $approvedPoints điểm"

        rows?.removeAllViews()
        emptyView?.visibility = if (sortedReports.isEmpty()) View.VISIBLE else View.GONE

        val visibleReports = sortedReports.take(10)
        scrollView?.visibility = if (visibleReports.isEmpty()) View.GONE else View.VISIBLE
        scrollView?.layoutParams = scrollView?.layoutParams?.apply {
            height = minOf(visibleReports.size * 70.dpToPx(), 280.dpToPx())
        }

        if (rows != null) {
            visibleReports.forEach { report ->
                rows.addView(createHistoryRow(report, rows))
            }
        }

        if (sortedReports.size > visibleReports.size) {
            moreView?.visibility = View.VISIBLE
            moreView?.text = "Còn ${sortedReports.size - visibleReports.size} báo cáo khác"
        } else {
            moreView?.visibility = View.GONE
        }
    }

    private fun createHistoryRow(report: TrashReport, parent: LinearLayout): View {
        val row = layoutInflater.inflate(R.layout.item_report_history_dialog, parent, false)
        val date = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(report.timestamp))
        val reporterName = report.reporterName.ifBlank { "Lớp $currentClassName" }.fixVietnameseMojibake()
        val statusText = row.findViewById<TextView>(R.id.tvHistoryStatus)
        val pointsText = row.findViewById<TextView>(R.id.tvHistoryPoints)
        val statusIcon = row.findViewById<ImageView>(R.id.ivHistoryStatus)

        row.findViewById<TextView>(R.id.tvHistoryReporter)?.text = reporterName
        row.findViewById<TextView>(R.id.tvHistoryTime)?.text = date

        when (report.status) {
            "approved" -> {
                statusText?.text = "Đã duyệt"
                statusText?.setTextColor(ContextCompat.getColor(this, R.color.ds_success))
                statusText?.setBackgroundResource(R.drawable.bg_status_chip_success)
                pointsText?.text = "+${report.points}đ"
                pointsText?.setTextColor(ContextCompat.getColor(this, R.color.ds_primary))
                statusIcon?.setImageResource(R.drawable.ic_check)
                statusIcon?.setColorFilter(ContextCompat.getColor(this, R.color.ds_success))
            }
            "rejected" -> {
                statusText?.text = "Cần xem lại"
                statusText?.setTextColor(ContextCompat.getColor(this, R.color.ds_error))
                statusText?.setBackgroundResource(R.drawable.bg_status_chip_error)
                pointsText?.text = "0đ"
                pointsText?.setTextColor(ContextCompat.getColor(this, R.color.ds_error))
                statusIcon?.setImageResource(R.drawable.ic_close)
                statusIcon?.setColorFilter(ContextCompat.getColor(this, R.color.ds_error))
            }
            else -> {
                statusText?.text = "Chờ duyệt"
                statusText?.setTextColor(ContextCompat.getColor(this, R.color.ds_warning))
                statusText?.setBackgroundResource(R.drawable.bg_status_chip_warning)
                pointsText?.text = "Đang chờ"
                pointsText?.setTextColor(ContextCompat.getColor(this, R.color.ds_warning))
                statusIcon?.setImageResource(R.drawable.ic_history)
                statusIcon?.setColorFilter(ContextCompat.getColor(this, R.color.ds_warning))
            }
        }

        return row
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun showChangePasswordDialog() {
        val input = EditText(this).apply {
            hint = "Mật khẩu mới"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
            setTextColor(ContextCompat.getColor(this@ProfileActivity, R.color.ds_text_primary))
            setHintTextColor(ContextCompat.getColor(this@ProfileActivity, R.color.ds_text_hint))
            background = ContextCompat.getDrawable(this@ProfileActivity, R.drawable.bg_spinner_ds)
            typeface = ResourcesCompat.getFont(this@ProfileActivity, R.font.be_vietnam_pro_regular)
        }

        showDamSanActionDialog(
            title = "Đổi mật khẩu",
            message = "Nhập mật khẩu mới cho tài khoản lớp. Mật khẩu cần có ít nhất 6 ký tự.",
            iconRes = R.drawable.ic_key,
            positiveText = "Đổi mật khẩu",
            negativeText = "Huỷ",
            contentView = input,
            dismissOnPositive = false
        ) { dialog ->
            submitPasswordChange(input.text.toString(), dialog)
        }
    }

    private fun submitPasswordChange(newPassword: String, dialog: AlertDialog) {
        if (newPassword.length < 6) {
            showSnackbar("Mật khẩu phải ít nhất 6 ký tự", true)
            return
        }

        repo.getCurrentUser()?.updatePassword(newPassword)
            ?.addOnSuccessListener {
                dialog.dismiss()
                showSnackbar("Đổi mật khẩu thành công!", false)
            }
            ?.addOnFailureListener {
                showSnackbar("Lỗi: ${it.message}", true)
            }
    }

    private fun classNameFromEmail(email: String): String {
        val localPart = email.substringBefore("@").uppercase(Locale.getDefault())
        return if (localPart.matches(Regex("""\d{2}A\d+"""))) localPart else "Unknown"
    }

    private fun avatarText(className: String): String {
        return if (className == "Unknown") "DS" else className.take(3)
    }

    private fun showSnackbar(message: String, isError: Boolean) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(
            ContextCompat.getColor(this, if (isError) R.color.ds_error else R.color.ds_primary)
        )
        snackbar.show()
    }

    private fun String.fixVietnameseMojibake(): String {
        val replacements = mapOf(
            "Lá»›p" to "Lớp",
            "ÄÃ£" to "Đã",
            "Äang" to "Đang",
            "Ä‘" to "đ",
            "Y TÃº" to "Y Tú",
            "VÄƒn" to "Văn",
            "Quá»‘c" to "Quốc",
            "Tuáº¥n" to "Tuấn",
            "Há»“ng SÆ¡n" to "Hồng Sơn",
            "Y KÃ´" to "Y Kô"
        )
        return replacements.entries.fold(this) { value, (bad, good) -> value.replace(bad, good) }
    }
}
