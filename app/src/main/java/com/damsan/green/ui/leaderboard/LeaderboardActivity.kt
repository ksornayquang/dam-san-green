package com.damsan.green.ui.leaderboard

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.damsan.green.R
import com.damsan.green.data.model.ClassRanking
import com.damsan.green.data.repository.FirebaseRepository
import com.damsan.green.utils.HandlesOwnInsets
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class LeaderboardActivity : AppCompatActivity(), HandlesOwnInsets {

    private val repo = FirebaseRepository()
    private lateinit var adapter: LeaderboardAdapter
    private var previousTop1: String? = null
    private var myClassName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)
        setupEdgeToEdgeInsets()

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerLeaderboard)
        adapter = LeaderboardAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        // Load tÃªn lá»›p hiá»‡n táº¡i
        val uid = repo.getCurrentUser()?.uid
        if (uid != null) {
            lifecycleScope.launch {
                myClassName = repo.getClassName(uid)
            }
        }

        // Setup SwipeRefreshLayout
        val swipeRefreshLayout = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
        swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch {
                adapter.submitList(repo.getLeaderboardFlow().first())
                swipeRefreshLayout.isRefreshing = false
            }
        }

        observeLeaderboard()
    }

    private fun setupEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val root = findViewById<View>(R.id.leaderboardRoot)
        val content = findViewById<View>(R.id.leaderboardContent)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerLeaderboard)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            content?.setPadding(dp(16), bars.top + dp(8), dp(16), 0)
            recyclerView?.setPadding(dp(16), dp(8), dp(16), bars.bottom + dp(18))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun observeLeaderboard() {
        lifecycleScope.launch {
            repo.getLeaderboardFlow().collectLatest { rankings ->
                val swipeRefreshLayout = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)
                swipeRefreshLayout.isRefreshing = false
                adapter.submitList(rankings)

                // Handle Empty State
                val emptyState = findViewById<View>(R.id.layoutEmptyState)
                val recyclerView = findViewById<RecyclerView>(R.id.recyclerLeaderboard)
                
                runOnUiThread {
                    if (rankings.isEmpty()) {
                        emptyState.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyState.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                }

                // Cáº­p nháº­t podium top 3
                val top1 = rankings.getOrNull(0)
                val top2 = rankings.getOrNull(1)
                val top3 = rankings.getOrNull(2)

                runOnUiThread {
                    // Top 1 (Gold)
                    top1?.let {
                        findViewById<TextView>(R.id.tvTop1Name).text = it.className
                        findViewById<TextView>(R.id.tvTop1Points).text = "${it.totalPoints} điểm"
                    }
                    // Top 2 (Silver)
                    top2?.let {
                        findViewById<TextView>(R.id.tvTop2Name).text = it.className
                        findViewById<TextView>(R.id.tvTop2Points).text = "${it.totalPoints} điểm"
                    }
                    // Top 3 (Bronze)
                    top3?.let {
                        findViewById<TextView>(R.id.tvTop3Name).text = it.className
                        findViewById<TextView>(R.id.tvTop3Points).text = "${it.totalPoints} điểm"
                    }

                    // Confetti khi lá»›p mÃ¬nh lÃªn Top 1
                    if (top1 != null && top1.className == myClassName) {
                        if (previousTop1 != myClassName) {
                            triggerConfetti()
                            triggerHaptic()
                        }
                    }
                    previousTop1 = top1?.className
                }
            }
        }
    }

    private fun triggerConfetti() {
        val konfettiView = findViewById<KonfettiView>(R.id.konfettiView) ?: return

        val party = Party(
            emitter = Emitter(duration = 3, TimeUnit.SECONDS).perSecond(50),
            position = Position.Relative(0.5, 0.0),
            colors = listOf(
                0xFFE5C158.toInt(), // VÃ ng cá»“ng chiÃªng
                0xFF1E4D2B.toInt(), // Xanh Ä‘áº¡i ngÃ n
                0xFFD05A3F.toInt(), // Äá» Ä‘áº¥t
                0xFFF9F6F0.toInt()  // Tráº¯ng sá»£i bÃ´ng
            )
        )
        konfettiView.start(party)
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(200)
            }
        } catch (_: Exception) {
            // Ignore vibration errors
        }
    }
}

// ===== ADAPTER =====
class LeaderboardAdapter : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    private var items = listOf<ClassRanking>()

    fun submitList(newItems: List<ClassRanking>) {
        items = newItems.filter { it.className.isNotBlank() && it.className != "Unknown" }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvRank = itemView.findViewById<TextView>(R.id.tvRank)
        private val tvClassName = itemView.findViewById<TextView>(R.id.tvClassName)
        private val tvPoints = itemView.findViewById<TextView>(R.id.tvPoints)
        private val tvReportCount = itemView.findViewById<TextView>(R.id.tvReportCount)
        private val tvLastActivity = itemView.findViewById<TextView>(R.id.tvLastActivity)
        private val cardView = itemView.findViewById<androidx.cardview.widget.CardView>(R.id.cardView)

        fun bind(ranking: ClassRanking) {
            tvRank.text = when (ranking.rank) {
                1 -> "#1"
                2 -> "#2"
                3 -> "#3"
                else -> "#${ranking.rank}"
            }
            tvClassName.text = "Lớp ${ranking.className}"
            tvPoints.text = "${ranking.totalPoints} điểm"
            tvReportCount.text = "${ranking.reportCount} lần nhặt rác"

            val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            tvLastActivity.text = if (ranking.lastActivity > 0) {
                "Lần cuối: ${sdf.format(Date(ranking.lastActivity))}"
            } else ""
            cardView.setCardBackgroundColor(0xFFFFFFFF.toInt())
        }
    }
}

