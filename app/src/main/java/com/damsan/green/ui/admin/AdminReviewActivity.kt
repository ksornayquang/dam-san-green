package com.damsan.green.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
import com.damsan.green.ui.showDamSanConfirmDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AdminReviewActivity : AppCompatActivity() {

    private val repo = FirebaseRepository()
    private lateinit var adapter: AdminReviewReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_review)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerReports)
        adapter = AdminReviewReportAdapter(
            onApprove = { report -> approveReport(report) },
            onReject = { report -> rejectReport(report) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Setup SwipeRefreshLayout
        val swipeRefreshLayout = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                repo.getAllReportsFlow().collectLatest {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        observeReports()
    }

    private fun observeReports() {
        lifecycleScope.launch {
            repo.getAllReportsFlow().collectLatest { reports ->
                val swipeRefreshLayout = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
                swipeRefreshLayout.isRefreshing = false

                val pendingCount = reports.count { it.status == "pending" }
                val emptyState = findViewById<View>(R.id.layoutEmptyState)
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerReports)

                runOnUiThread {
                    if (pendingCount == 0 && reports.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyState.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                }

                // Hiện tất cả, ưu tiên pending lên trước
                val sorted = reports.sortedWith(
                    compareBy<TrashReport> {
                        when (it.status) {
                            "pending" -> 0
                            "approved" -> 1
                            "rejected" -> 2
                            else -> 3
                        }
                    }.thenByDescending { it.timestamp }
                )
                adapter.submitList(sorted)
            }
        }
    }

    private fun approveReport(report: TrashReport) {
        lifecycleScope.launch {
            val result = repo.updateReportStatus(report.id, "approved")
            result.fold(
                onSuccess = {
                    Toast.makeText(this@AdminReviewActivity,
                        "Đã duyệt. Lớp ${report.className} +${report.points}đ", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    Toast.makeText(this@AdminReviewActivity,
                        "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun rejectReport(report: TrashReport) {
        showDamSanConfirmDialog(
            title = "Xoá báo cáo?",
            message = "Báo cáo của lớp ${report.className} sẽ bị xoá khỏi dữ liệu thi đua. Bạn chắc chắn muốn tiếp tục?",
            iconRes = R.drawable.ic_trash,
            positiveText = "Xoá báo cáo",
            negativeText = "Huỷ",
            danger = true
        ) {
                lifecycleScope.launch {
                    val result = repo.deleteTrashReport(report.id)
                    result.fold(
                        onSuccess = {
                            Toast.makeText(this@AdminReviewActivity,
                                "Đã xoá. Lớp ${report.className} -10đ", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(this@AdminReviewActivity,
                                "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
        }
    }
}

// ===== ADAPTER =====
class AdminReviewReportAdapter(
    private val onApprove: (TrashReport) -> Unit,
    private val onReject: (TrashReport) -> Unit
) : RecyclerView.Adapter<AdminReviewReportAdapter.ViewHolder>() {

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
        private val tvClassName = itemView.findViewById<TextView>(R.id.tvReportClass)
        private val tvReporter = itemView.findViewById<TextView>(R.id.tvReportReporter)
        private val tvTime = itemView.findViewById<TextView>(R.id.tvReportTime)
        private val tvStatus = itemView.findViewById<TextView>(R.id.tvReportStatus)
        private val layoutAiReview = itemView.findViewById<View>(R.id.layoutAiReview)
        private val tvAiReviewTitle = itemView.findViewById<TextView>(R.id.tvAiReviewTitle)
        private val tvAiConfidence = itemView.findViewById<TextView>(R.id.tvAiConfidence)
        private val tvAiReviewSummary = itemView.findViewById<TextView>(R.id.tvAiReviewSummary)
        private val tvAiReviewReason = itemView.findViewById<TextView>(R.id.tvAiReviewReason)
        private val btnApprove = itemView.findViewById<CardView>(R.id.btnApprove)
        private val btnReject = itemView.findViewById<CardView>(R.id.btnReject)

        fun bind(report: TrashReport) {
            tvClassName.text = "Lớp ${report.className}"
            tvReporter.text = report.reporterName

            val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            tvTime.text = if (report.timestamp > 0) sdf.format(Date(report.timestamp)) else "—"

            // Load ảnh
            bindAiReview(report)

            val beforeUrl = report.imageBeforeUrl.ifBlank { report.imageUrl }
            if (beforeUrl.isNotEmpty() && beforeUrl.startsWith("http")) {
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

            // Status badge
            when (report.status) {
                "approved" -> {
                    tvStatus.text = "Đã duyệt"
                    tvStatus.setTextColor(0xFF1E4D2B.toInt())
                    btnApprove.visibility = View.GONE
                    btnReject.visibility = View.VISIBLE
                }
                "rejected" -> {
                    tvStatus.text = "Đã từ chối"
                    tvStatus.setTextColor(0xFFD05A3F.toInt())
                    btnApprove.visibility = View.GONE
                    btnReject.visibility = View.GONE
                }
                else -> { // pending
                    tvStatus.text = "Chờ duyệt"
                    tvStatus.setTextColor(0xFFE5C158.toInt())
                    btnApprove.visibility = View.VISIBLE
                    btnReject.visibility = View.VISIBLE
                }
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
