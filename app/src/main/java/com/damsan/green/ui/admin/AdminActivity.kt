package com.damsan.green.ui.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.damsan.green.R
import com.damsan.green.data.model.TrashReport
import com.damsan.green.data.repository.FirebaseRepository
import com.damsan.green.ui.auth.LoginActivity
import com.damsan.green.ui.leaderboard.LeaderboardActivity
import com.damsan.green.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AdminActivity : AppCompatActivity() {

    private val repo = FirebaseRepository()
    private lateinit var adapter: AdminReportAdapter
    private var allReports = listOf<TrashReport>()
    private val estimatedCo2KgPerWasteKg = 0.7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        setupRecycler()
        setupClickListeners()
        observeReports()
        loadStats()
    }

    private fun setupRecycler() {
        adapter = AdminReportAdapter(
            onApprove = { report -> approveReport(report) },
            onReject = { report -> rejectReport(report) }
        )
        val rv = findViewById<RecyclerView>(R.id.recyclerAdminReports)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
    }

    private fun setupClickListeners() {
        // Tab: Chờ duyệt
        findViewById<View>(R.id.tabPending)?.setOnClickListener {
            filterReports("pending")
            setActiveTab(it)
        }
        // Tab: Đã duyệt
        findViewById<View>(R.id.tabApproved)?.setOnClickListener {
            filterReports("approved")
            setActiveTab(it)
        }
        // Tab: Từ chối
        findViewById<View>(R.id.tabRejected)?.setOnClickListener {
            filterReports("rejected")
            setActiveTab(it)
        }

        // Nút xem BXH
        findViewById<View>(R.id.btnAdminLeaderboard)?.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }

        // Nút cài đặt branding/app
        findViewById<View>(R.id.btnAdminSettings)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.btnAdminNews)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Nút đăng xuất
        findViewById<View>(R.id.btnAdminLogout)?.setOnClickListener {
            repo.signOut()
            startActivity(Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }

    private fun setActiveTab(activeView: View) {
        // Reset all tabs
        listOf(R.id.tabPending, R.id.tabApproved, R.id.tabRejected).forEach { id ->
            findViewById<TextView>(id)?.apply {
                setBackgroundResource(R.drawable.bg_tab_inactive)
                setTextColor(resources.getColor(R.color.text_ash_gray, null))
            }
        }
        // Activate selected
        (activeView as? TextView)?.apply {
            setBackgroundResource(R.drawable.bg_tab_active)
            setTextColor(resources.getColor(R.color.white, null))
        }
    }

    private fun filterReports(status: String) {
        val filtered = allReports.filter { it.status == status }
        adapter.submitList(filtered)
        findViewById<TextView>(R.id.tvFilterCount)?.text = "${filtered.size} báo cáo"
    }

    private fun observeReports() {
        lifecycleScope.launch {
            repo.getAllReportsFlow().collectLatest { reports ->
                allReports = reports
                runOnUiThread {
                    // Default: hiện pending
                    val pending = reports.filter { it.status == "pending" }
                    adapter.submitList(pending)
                    
                    // Update stats
                    val pendingCount = reports.count { it.status == "pending" }
                    val approvedCount = reports.count { it.status == "approved" }
                    val rejectedCount = reports.count { it.status == "rejected" }
                    val approvedReports = reports.filter { it.status == "approved" }
                    val wasteKg = approvedReports.sumOf { it.impactWasteKg() }
                    val co2Kg = wasteKg * estimatedCo2KgPerWasteKg
                    val activeClassCount = approvedReports
                        .map { it.className }
                        .filter { it.isNotBlank() && it != "Unknown" }
                        .distinct()
                        .size
                    
                    findViewById<TextView>(R.id.tvPendingCount)?.text = "$pendingCount"
                    findViewById<TextView>(R.id.tvApprovedCount)?.text = "$approvedCount"
                    findViewById<TextView>(R.id.tvRejectedCount)?.text = "$rejectedCount"
                    findViewById<TextView>(R.id.tvFilterCount)?.text = "${pending.size} báo cáo"
                    findViewById<TextView>(R.id.tvAdminImpactSummary)?.text =
                        "Tác động vận hành: ${formatImpactNumber(wasteKg)} kg rác • ${formatImpactNumber(co2Kg)} kg CO₂ • $activeClassCount lớp tham gia"
                }
            }
        }
    }

    private fun formatImpactNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private fun loadStats() {
        lifecycleScope.launch {
            repo.getLeaderboardFlow().collectLatest { rankings ->
                runOnUiThread {
                    val top = rankings.getOrNull(0)
                    if (top != null) {
                        findViewById<TextView>(R.id.tvTopClassName)?.text =
                            "Top lớp ${top.className} - ${top.totalPoints}đ"
                    }
                }
            }
        }
    }

    private fun approveReport(report: TrashReport) {
        lifecycleScope.launch {
            repo.updateReportStatus(report.id, "approved")
            Toast.makeText(this@AdminActivity,
                "Đã duyệt. +${report.points}đ cho lớp ${report.className}",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun rejectReport(report: TrashReport) {
        lifecycleScope.launch {
            repo.updateReportStatus(report.id, "rejected")
            Toast.makeText(this@AdminActivity,
                "Đã từ chối báo cáo của lớp ${report.className}",
                Toast.LENGTH_SHORT).show()
        }
    }
}

// ===== ADAPTER =====
class AdminReportAdapter(
    private val onApprove: (TrashReport) -> Unit,
    private val onReject: (TrashReport) -> Unit
) : RecyclerView.Adapter<AdminReportAdapter.ViewHolder>() {

    private var items = listOf<TrashReport>()

    fun submitList(newItems: List<TrashReport>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_report, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPhoto = itemView.findViewById<ImageView>(R.id.ivReportPhoto)
        private val tvClass = itemView.findViewById<TextView>(R.id.tvReportClass)
        private val tvReporter = itemView.findViewById<TextView>(R.id.tvReportReporter)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvReportTime)
        private val tvStatus = itemView.findViewById<TextView>(R.id.tvReportStatus)
        private val layoutAiReview = itemView.findViewById<View>(R.id.layoutAiReview)
        private val tvAiReviewTitle = itemView.findViewById<TextView>(R.id.tvAiReviewTitle)
        private val tvAiConfidence = itemView.findViewById<TextView>(R.id.tvAiConfidence)
        private val tvAiReviewSummary = itemView.findViewById<TextView>(R.id.tvAiReviewSummary)
        private val tvAiReviewReason = itemView.findViewById<TextView>(R.id.tvAiReviewReason)
        private val btnApprove = itemView.findViewById<View>(R.id.btnApprove)
        private val btnReject = itemView.findViewById<View>(R.id.btnReject)

        fun bind(report: TrashReport) {
            tvClass.text = "Lớp ${report.className}"
            tvReporter.text = report.reporterName

            val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            tvTime.text = sdf.format(Date(report.timestamp))
            bindAiReview(report)

            // Status badge
            when (report.status) {
                "pending" -> {
                    tvStatus.text = "Chờ duyệt"
                    tvStatus.setTextColor(0xFFE0B354.toInt())
                    btnApprove.visibility = View.VISIBLE
                    btnReject.visibility = View.VISIBLE
                }
                "approved" -> {
                    tvStatus.text = "Đã duyệt"
                    tvStatus.setTextColor(0xFF14454F.toInt())
                    btnApprove.visibility = View.GONE
                    btnReject.visibility = View.GONE
                }
                "rejected" -> {
                    tvStatus.text = "Từ chối"
                    tvStatus.setTextColor(0xFFB5543D.toInt())
                    btnApprove.visibility = View.VISIBLE
                    btnReject.visibility = View.GONE
                }
            }

            // Load ảnh
            val beforeUrl = report.imageBeforeUrl.ifBlank { report.imageUrl }
            if (beforeUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(beforeUrl)
                    .centerCrop()
                    .into(ivPhoto)
            }
            ivPhoto.tag = false
            ivPhoto.setOnClickListener {
                if (report.imageAfterUrl.isBlank()) return@setOnClickListener
                val showingAfter = ivPhoto.tag as? Boolean ?: false
                Glide.with(itemView.context)
                    .load(if (showingAfter) beforeUrl else report.imageAfterUrl)
                    .centerCrop()
                    .into(ivPhoto)
                ivPhoto.tag = !showingAfter
            }

            btnApprove.setOnClickListener { onApprove(report) }
            btnReject.setOnClickListener { onReject(report) }
        }

        private fun bindAiReview(report: TrashReport) {
            if (report.aiReviewStatus.isBlank() && report.aiConfidence <= 0) {
                layoutAiReview.visibility = View.GONE
                return
            }

            layoutAiReview.visibility = View.VISIBLE
            tvAiReviewTitle.text = when {
                report.aiAutoApproved -> "AI đã tự duyệt"
                report.aiReviewStatus == "failed" -> "AI chưa phân tích được"
                else -> "AI đề xuất admin xem lại"
            }
            tvAiConfidence.text = if (report.aiConfidence > 0) "${report.aiConfidence}%" else "—"
            tvAiReviewSummary.text = buildString {
                append(report.aiTrashName.ifBlank { report.aiWasteType.displayWasteType() })
                if (report.aiCategory.isNotBlank()) append(" · ${report.aiCategory.displayAiCategory()}")
                if (report.trashType.isNotBlank()) append(if (report.trashType == "recyclable") " · Tái chế · 2 ảnh" else " · Sinh hoạt · 2 ảnh")
                if (report.aiDetectedItems > 0) append(" · ${report.aiDetectedItems} món")
                if (report.aiEstimatedKg > 0.0) append(" · ${report.aiEstimatedKg.formatKg()} kg")
                if (report.aiConfidence > 0) append(" · AI ${report.aiConfidence}%")
                if (report.demoMode) append(" · DEMO NGOÀI TRƯỜNG")
                append(" · +${report.points}đ")
            }
            tvAiReviewReason.text = buildString {
                append(report.aiReason.ifBlank { "AI chưa có ghi chú chi tiết." })
                if (report.aiWarnings.isNotBlank()) {
                    append("\nLưu ý: ${report.aiWarnings}")
                }
            }
        }
    }
}

private fun TrashReport.impactWasteKg(): Double {
    return if (aiEstimatedKg > 0.0) aiEstimatedKg else 0.35
}

private fun String.displayWasteType(): String {
    return when (this) {
        "plastic_bottle" -> "Chai nhựa"
        "aluminum_can" -> "Lon nhôm"
        "paper" -> "Giấy"
        "plastic_bag" -> "Túi nilon"
        "organic" -> "Rác hữu cơ"
        "mixed" -> "Rác hỗn hợp"
        "unclear" -> "Chưa rõ loại rác"
        else -> ifBlank { "Chưa rõ loại rác" }
    }
}

private fun String.displayAiCategory(): String = when (this) {
    "RECYCLABLE" -> "Tái chế"
    "NON_RECYCLABLE" -> "Sinh hoạt"
    else -> this
}

private fun Double.formatKg(): String {
    return if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", this).trimEnd('0').trimEnd('.')
    }
}
