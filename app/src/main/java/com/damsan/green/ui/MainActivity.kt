package com.damsan.green.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.damsan.green.R
import com.damsan.green.data.repository.FirebaseRepository
import com.damsan.green.data.repository.SettingsService
import com.damsan.green.ui.admin.AdminReviewActivity
import com.damsan.green.ui.intro.SchoolInfoActivity
import com.damsan.green.ui.leaderboard.LeaderboardActivity
import com.damsan.green.ui.map.Campus3DMapView
import com.damsan.green.ui.profile.ProfileActivity
import com.damsan.green.ui.report.ReportActivity
import com.damsan.green.utils.LocationHelper
import com.damsan.green.utils.HandlesOwnInsets
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import com.damsan.green.data.model.DailyMission

class MainActivity : AppCompatActivity(), OnMapReadyCallback, HandlesOwnInsets {

    private lateinit var mapView: MapView
    private lateinit var campusMapView: Campus3DMapView
    private var googleMap: GoogleMap? = null
    private val repo = FirebaseRepository()
    private val settingsService = SettingsService()
    private lateinit var locationHelper: LocationHelper
    private val reportMarkers = mutableListOf<Marker>()
    private lateinit var todayMission: DailyMission
    private var isMapViewCreated = false
    private var isActivityStarted = false
    private var isActivityResumed = false
    private var isCampus3DMode = false

    // Mặc định: Tọa độ trường Đam San
    private val SCHOOL_LAT = 12.900868056693273
    private val SCHOOL_LON = 108.2911159047231
    private val DEFAULT_LOCATION_TEXT = "EaDrông, Đắk Lắk"

    // OpenWeatherMap API (free tier)
    private val WEATHER_API_KEY = "88a8573664afe7976f6bb6e79720e915" // Thay bằng key thực tế
    private val ESTIMATED_WASTE_KG_PER_REPORT = 0.35
    private val ESTIMATED_CO2_KG_PER_WASTE_KG = 0.7

    // Location permission launcher
    private val locationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            fetchAndDisplayLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupEdgeToEdgeInsets()

        locationHelper = LocationHelper(this)

        mapView = findViewById(R.id.googleMapView)
        mapView.translationZ = -2f
        mapView.visibility = View.INVISIBLE
        campusMapView = findViewById(R.id.campus3DMapView)
        campusMapView.translationZ = -2f
        campusMapView.visibility = View.GONE

        setupMapModeToggle()
        setupClickListeners()
        loadBranding()
        setupDailyMission()
        observeReports()
        observeLeaderboardPreview()
        checkAdminRole()
        requestLocationAndDisplay()
        fetchWeather()
        deferMapInit(savedInstanceState)
    }

    private fun deferMapInit(savedInstanceState: Bundle?) {
        window.decorView.postDelayed({
            if (!isFinishing && !isDestroyed) {
                initializeMapView(savedInstanceState)
            }
        }, 600L)
    }

    private fun initializeMapView(savedInstanceState: Bundle?) {
        if (isMapViewCreated) return
        isMapViewCreated = true
        mapView.onCreate(savedInstanceState)
        if (isActivityStarted) mapView.onStart()
        if (isActivityResumed) mapView.onResume()
        mapView.visibility = if (isCampus3DMode) View.INVISIBLE else View.VISIBLE
        mapView.getMapAsync(this)
    }

    private fun setupMapModeToggle() {
        findViewById<TextView>(R.id.btnMapModeGps)?.setOnClickListener { setMapMode(use3D = false) }
        findViewById<TextView>(R.id.btnMapMode3d)?.setOnClickListener { setMapMode(use3D = true) }
        setMapMode(use3D = false)
    }

    private fun setMapMode(use3D: Boolean) {
        isCampus3DMode = use3D
        if (::campusMapView.isInitialized) {
            campusMapView.visibility = if (use3D) View.VISIBLE else View.GONE
        }
        if (::mapView.isInitialized) {
            mapView.visibility = if (!use3D && isMapViewCreated) View.VISIBLE else View.INVISIBLE
            mapView.translationZ = -2f
        }
        applyMapChromeForMode(use3D)
        findViewById<View>(R.id.mainUiOverlay)?.bringToFront()
        findViewById<View>(R.id.mapModeToggle)?.bringToFront()
        findViewById<View>(R.id.btnAiBubbleHome)?.bringToFront()
        findViewById<View>(R.id.btnMapZoomIn)?.bringToFront()
        findViewById<View>(R.id.btnMapZoomOut)?.bringToFront()

        val gpsButton = findViewById<TextView>(R.id.btnMapModeGps)
        val map3DButton = findViewById<TextView>(R.id.btnMapMode3d)
        val activeColor = ContextCompat.getColor(this, R.color.white)
        val inactiveColor = ContextCompat.getColor(this, R.color.eco_text)

        gpsButton?.setBackgroundResource(
            if (use3D) R.drawable.bg_map_mode_unselected else R.drawable.bg_map_mode_selected
        )
        gpsButton?.setTextColor(if (use3D) inactiveColor else activeColor)

        map3DButton?.setBackgroundResource(
            if (use3D) R.drawable.bg_map_mode_selected else R.drawable.bg_map_mode_unselected
        )
        map3DButton?.setTextColor(if (use3D) activeColor else inactiveColor)
    }

    private fun applyMapChromeForMode(use3D: Boolean) {
        findViewById<View>(R.id.weatherInfoRow)?.apply {
            alpha = 0.9f
            translationY = 0f
            translationZ = 0f
        }
        findViewById<View>(R.id.bottomCardsPanel)?.apply {
            translationY = 0f
        }
    }

    private fun setupEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val root = findViewById<View>(R.id.mainRoot)
        val header = findViewById<View>(R.id.headerBar)
        val bottomPanel = findViewById<View>(R.id.bottomCardsPanel)
        val mapToggle = findViewById<View>(R.id.mapModeToggle)
        val aiBubble = findViewById<View>(R.id.btnAiBubbleHome)
        val zoomIn = findViewById<View>(R.id.btnMapZoomIn)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            header?.setPadding(dp(16), bars.top + dp(12), dp(16), dp(8))
            bottomPanel?.setPadding(dp(16), 0, dp(16), bars.bottom + dp(12))
            mapToggle?.setTopMargin(bars.top + dp(132))
            zoomIn?.setTopMargin(bars.top + dp(132))
            aiBubble?.setTopMargin(bars.top + dp(332))
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun View.setTopMargin(value: Int) {
        (layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            if (params.topMargin != value) {
                params.topMargin = value
                layoutParams = params
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun loadBranding() {
        lifecycleScope.launch {
            val branding = settingsService.getBranding()
            runOnUiThread {
                findViewById<TextView>(R.id.tvHeaderSchoolName)?.text = branding.schoolName
                findViewById<TextView>(R.id.tvHeaderAppName)?.text = branding.appName

                val logo = findViewById<ImageView>(R.id.ivHeaderLogo)
                logo.visibility = View.VISIBLE
                logo.setImageResource(R.drawable.logo_damsan_green)
                if (branding.logoUrl.isNotBlank()) {
                    Glide.with(this@MainActivity)
                        .load(branding.logoUrl)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .into(logo)
                }
            }
        }
    }

    // ========== DAILY MISSION — Xoay vòng theo ngày ==========

    private fun setupDailyMission() {
        todayMission = DailyMission.getTodayMission()
        val bonusMission = DailyMission.getTodayBonusMission()

        // Populate UI
        findViewById<ImageView>(R.id.ivMissionIcon)?.setImageResource(getMissionIcon(todayMission.id))
        findViewById<TextView>(R.id.tvMissionTitle)?.text = todayMission.title
        findViewById<TextView>(R.id.tvMissionDesc)?.text = todayMission.description
        findViewById<TextView>(R.id.tvTaskCount)?.text = "(0/${todayMission.target} ${todayMission.unit})"
        findViewById<ProgressBar>(R.id.taskProgress)?.max = todayMission.target
        findViewById<ProgressBar>(R.id.taskProgress)?.progress = 0

        // Bonus mission hint
        findViewById<TextView>(R.id.tvBonusMission)?.text =
            "Tiếp: ${bonusMission.title}"
    }

    // ========== WEATHER — OpenWeatherMap API ==========

    private fun fetchWeather() {
        val tvTemp = findViewById<TextView>(R.id.tvTemperature)
        val tvDesc = findViewById<TextView>(R.id.tvWeatherDesc)

        lifecycleScope.launch {
            try {
                val weatherData = withContext(Dispatchers.IO) {
                    val client = OkHttpClient()
                    val url = "https://api.openweathermap.org/data/2.5/weather" +
                            "?lat=$SCHOOL_LAT&lon=$SCHOOL_LON" +
                            "&appid=$WEATHER_API_KEY" +
                            "&units=metric&lang=vi"
                    val request = Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    response.body?.string()
                }

                if (weatherData != null) {
                    val json = JSONObject(weatherData)

                    // Check for API error
                    if (json.has("cod") && json.getInt("cod") != 200) {
                        runOnUiThread {
                            tvTemp?.text = "28°C"
                            tvDesc?.text = "Trời nắng nhẹ"
                        }
                        return@launch
                    }

                    val main = json.getJSONObject("main")
                    val temp = main.getDouble("temp").toInt()
                    val weatherArray = json.getJSONArray("weather")
                    val description = if (weatherArray.length() > 0) {
                        weatherArray.getJSONObject(0).getString("description")
                    } else {
                        "Không rõ"
                    }

                    // Capitalize first letter
                    val descCapitalized = description.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                    }

                    runOnUiThread {
                        tvTemp?.text = "${temp}°C"
                        tvDesc?.text = descCapitalized
                    }
                } else {
                    // Fallback
                    runOnUiThread {
                        tvTemp?.text = "28°C"
                        tvDesc?.text = "Trời nắng nhẹ"
                    }
                }
            } catch (e: Exception) {
                Log.e("Weather", "Failed to fetch weather", e)
                // Fallback data
                runOnUiThread {
                    tvTemp?.text = "28°C"
                    tvDesc?.text = "Trời nắng nhẹ"
                }
            }
        }
    }

    // ========== LOCATION — FusedLocationProviderClient ==========

    private fun requestLocationAndDisplay() {
        val hasPerm = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPerm) {
            fetchAndDisplayLocation()
        } else {
            locationPermLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun fetchAndDisplayLocation() {
        val tvLocation = findViewById<TextView>(R.id.tvLocationChip)

        lifecycleScope.launch {
            val location = locationHelper.getCurrentLocation()
            if (location != null) {
                // Geocode tọa độ → tên địa phương
                try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(this@MainActivity, Locale("vi"))
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val subLocality = addr.subLocality ?: addr.locality ?: ""
                        val province = addr.adminArea ?: "Đắk Lắk"
                        val displayText = if (subLocality.isNotEmpty()) {
                            "$subLocality, $province"
                        } else {
                            DEFAULT_LOCATION_TEXT
                        }
                        runOnUiThread { tvLocation?.text = displayText }
                    } else {
                        runOnUiThread { tvLocation?.text = DEFAULT_LOCATION_TEXT }
                    }
                } catch (e: Exception) {
                    runOnUiThread { tvLocation?.text = DEFAULT_LOCATION_TEXT }
                }
            } else {
                // Không lấy được GPS → hiển thị mặc định
                runOnUiThread { tvLocation?.text = DEFAULT_LOCATION_TEXT }
            }
        }
    }

    // ========== CLICK LISTENERS ==========

    private fun setupClickListeners() {
        // Nút "Quét Rác Ngay" — CTA card
        findViewById<View>(R.id.btnReportPill).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
        findViewById<View>(R.id.btnReportPill)?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                startActivity(Intent(this, ReportActivity::class.java))
                true
            } else {
                false
            }
        }

        findViewById<View>(R.id.btnAiBubbleHome)?.setOnClickListener {
            startActivity(
                Intent(this, SchoolInfoActivity::class.java)
                    .putExtra(SchoolInfoActivity.EXTRA_OPEN_CHAT, true)
            )
        }

        findViewById<View>(R.id.btnMapZoomIn)?.setOnClickListener {
            if (isCampus3DMode) campusMapView.zoomIn()
            else googleMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        findViewById<View>(R.id.btnMapZoomOut)?.setOnClickListener {
            if (isCampus3DMode) campusMapView.zoomOut()
            else googleMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }

        val bellClick = View.OnClickListener {
            NotificationBottomSheet().show(supportFragmentManager, "NotificationBottomSheet")
        }
        findViewById<View>(R.id.btnBell)?.setOnClickListener(bellClick)
        findViewById<ImageView>(R.id.ivBell)?.setOnClickListener(bellClick)

        val menuClick = View.OnClickListener { view ->
            val anchor = findViewById<View>(R.id.btnMainMenu) ?: view
            showMainMenu(anchor)
        }
        findViewById<View>(R.id.btnMainMenu)?.setOnClickListener(menuClick)
        findViewById<ImageView>(R.id.ivMenu)?.setOnClickListener(menuClick)

        // Card Leaderboard → Bảng xếp hạng full
        findViewById<View>(R.id.cardLeaderboard)?.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }
        findViewById<View>(R.id.cardLeaderboard)?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                startActivity(Intent(this, LeaderboardActivity::class.java))
                true
            } else {
                false
            }
        }

        // Card Nhiệm vụ → show mission detail
        findViewById<View>(R.id.cardTask)?.setOnClickListener {
            showMissionHint()
        }
        findViewById<View>(R.id.cardTask)?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                showMissionHint()
                true
            } else {
                false
            }
        }

        // Admin → duyệt rác (chỉ hiện cho admin)
        findViewById<CardView>(R.id.cardAdmin)?.setOnClickListener {
            startActivity(Intent(this, AdminReviewActivity::class.java))
        }
    }

    private fun showMissionHint() {
        val mission = if (::todayMission.isInitialized) todayMission else null
        val msg = if (mission != null) {
            "${mission.title} — ${mission.description}"
        } else {
            "Đang tải nhiệm vụ..."
        }
        com.google.android.material.snackbar.Snackbar.make(
            findViewById(android.R.id.content),
            msg,
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showMainMenu(anchor: View) {
        val menuView = LayoutInflater.from(this).inflate(R.layout.popup_main_menu_glass, null)
        val popup = PopupWindow(
            menuView,
            resources.getDimensionPixelSize(R.dimen.main_menu_width),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = 18f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        fun open(intent: Intent) {
            popup.dismiss()
            anchor.post { startActivity(intent) }
        }

        menuView.findViewById<View>(R.id.menuProfile).setOnClickListener {
            open(Intent(this, ProfileActivity::class.java))
        }
        menuView.findViewById<View>(R.id.menuLeaderboard).setOnClickListener {
            open(Intent(this, LeaderboardActivity::class.java))
        }
        menuView.findViewById<View>(R.id.menuSchoolInfo).setOnClickListener {
            open(Intent(this, SchoolInfoActivity::class.java))
        }
        menuView.findViewById<View>(R.id.menuLogout).setOnClickListener {
            popup.dismiss()
            showLogoutDialog()
        }

        menuView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val xOffset = anchor.width - menuView.measuredWidth
        popup.showAsDropDown(anchor, xOffset, 8)
    }

    private fun showLogoutDialog() {
        showDamSanConfirmDialog(
            title = "Đăng xuất",
            message = "Bạn chắc chắn muốn đăng xuất khỏi Dam San Green?",
            iconRes = R.drawable.ic_logout,
            positiveText = "Đăng xuất",
            negativeText = "Huỷ",
            danger = true
        ) {
                repo.logout()
                startActivity(Intent(this, com.damsan.green.ui.auth.LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
        }
    }

    // ========== ADMIN CHECK ==========

    private fun checkAdminRole() {
        val uid = repo.getCurrentUser()?.uid ?: return
        lifecycleScope.launch {
            val isAdmin = repo.isAdmin(uid)
            runOnUiThread {
                findViewById<CardView>(R.id.cardAdmin)?.visibility =
                    if (isAdmin) View.VISIBLE else View.GONE
            }
        }
    }

    // ========== MAP ==========

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        mapView.translationZ = -2f
        findViewById<View>(R.id.mainUiOverlay)?.bringToFront()
        findViewById<View>(R.id.mapModeToggle)?.bringToFront()
        findViewById<View>(R.id.btnAiBubbleHome)?.bringToFront()
        findViewById<View>(R.id.btnMapZoomIn)?.bringToFront()
        findViewById<View>(R.id.btnMapZoomOut)?.bringToFront()
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            isTiltGesturesEnabled = true
            isRotateGesturesEnabled = true
            isMapToolbarEnabled = false
        }

        val schoolPos = LatLng(SCHOOL_LAT, SCHOOL_LON)
        map.moveCamera(CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(schoolPos)
                .zoom(18f)
                .tilt(60f)
                .bearing(0f)
                .build()
        ))

        // School marker
        map.addMarker(
            MarkerOptions()
                .position(schoolPos)
                .title("Trường PTDTNT Đam San")
                .snippet("Điểm xuất phát · Hãy nhặt rác!")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
        )?.showInfoWindow()

        // School radius circle
        map.addCircle(
            CircleOptions()
                .center(schoolPos)
                .radius(150.0)
                .strokeColor(0x551E4D2B)
                .fillColor(0x111E4D2B)
                .strokeWidth(2f)
        )

        // Bật my location nếu có quyền
        if (locationHelper.hasLocationPermission()) {
            try {
                @Suppress("MissingPermission")
                map.isMyLocationEnabled = true
            } catch (_: Exception) { }
        }
    }

    // ========== REALTIME DATA ==========

    private fun observeReports() {
        lifecycleScope.launch {
            repo.getAllReportsFlow().collectLatest { reports ->
                val uid = repo.getCurrentUser()?.uid ?: return@collectLatest
                val className = repo.getClassName(uid)
                val myPoints = reports
                    .filter { it.className == className && it.status == "approved" }
                    .sumOf { it.points.coerceAtLeast(0) }
                val approvedReports = reports.filter { it.status == "approved" }
                val approvedWasteKg = approvedReports.sumOf { it.impactWasteKg() }

                runOnUiThread {
                    // Update điểm
                    findViewById<TextView>(R.id.tvMyPoints)?.text = "$myPoints Điểm"
                    updateImpactStats(
                        cleanupCount = approvedReports.size,
                        wasteKg = approvedWasteKg,
                        co2Kg = approvedWasteKg * ESTIMATED_CO2_KG_PER_WASTE_KG,
                        activeClassCount = approvedReports
                            .map { it.className }
                            .filter { it.isNotBlank() && it != "Unknown" }
                            .distinct()
                            .size
                    )

                    // Update task progress
                    val todayCount = reports.count {
                        it.className == className &&
                        it.status != "rejected" &&
                        (System.currentTimeMillis() - it.timestamp) < 86400000
                    }
                    val missionTarget = if (::todayMission.isInitialized) todayMission.target else 2
                    val missionUnit = if (::todayMission.isInitialized) todayMission.unit else "báo cáo"
                    val capped = todayCount.coerceAtMost(missionTarget)
                    findViewById<ProgressBar>(R.id.taskProgress)?.apply {
                        max = missionTarget
                        progress = capped
                    }
                    findViewById<TextView>(R.id.tvTaskCount)?.text = "($capped/$missionTarget $missionUnit)"

                    campusMapView.setReports(
                        reports.filter { it.status != "rejected" }
                    )

                    // Clear + re-add markers
                    reportMarkers.forEach { it.remove() }
                    reportMarkers.clear()

                    for (report in reports) {
                        if (report.latitude != 0.0 && report.longitude != 0.0 && report.status != "rejected") {
                            val marker = googleMap?.addMarker(
                                MarkerOptions()
                                    .position(LatLng(report.latitude, report.longitude))
                                    .title("Lớp ${report.className}")
                                    .snippet("${report.reporterName} · +${report.points}đ")
                                    .icon(BitmapDescriptorFactory.defaultMarker(
                                        BitmapDescriptorFactory.HUE_CYAN
                                    ))
                            )
                            if (marker != null) reportMarkers.add(marker)
                        }
                    }
                }
            }
        }
    }

    private fun updateImpactStats(
        cleanupCount: Int,
        wasteKg: Double,
        co2Kg: Double,
        activeClassCount: Int
    ) {
        findViewById<TextView>(R.id.tvImpactCleanups)?.text = cleanupCount.toString()
        findViewById<TextView>(R.id.tvImpactWaste)?.text = "${formatImpactNumber(wasteKg)} kg"
        findViewById<TextView>(R.id.tvImpactCo2)?.text = "${formatImpactNumber(co2Kg)} kg"
        findViewById<TextView>(R.id.tvImpactClasses)?.text = activeClassCount.toString()
    }

    private fun formatImpactNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }
    }

    private fun observeLeaderboardPreview() {
        lifecycleScope.launch {
            repo.getLeaderboardFlow().collectLatest { rankings ->
                runOnUiThread {
                    val top1 = rankings.getOrNull(0)
                    val top2 = rankings.getOrNull(1)
                    val maxPoints = top1?.totalPoints?.coerceAtLeast(1) ?: 1

                    // Update leaderboard title with top class name
                    top1?.let {
                        findViewById<TextView>(R.id.tvLeaderboardTitle)?.text = "TOP LỚP ${it.className}"
                        findViewById<TextView>(R.id.tvRank1Name)?.text = "Lớp ${it.className}"
                        findViewById<TextView>(R.id.tvRank1Points)?.text = "${it.totalPoints} điểm"
                        findViewById<ProgressBar>(R.id.progressRank1)?.apply {
                            max = maxPoints
                            progress = it.totalPoints
                        }
                    }
                    top2?.let {
                        findViewById<TextView>(R.id.tvRank2Name)?.text = "Lớp ${it.className}"
                        findViewById<TextView>(R.id.tvRank2Points)?.text = "${it.totalPoints}đ"
                        findViewById<ProgressBar>(R.id.progressRank2)?.apply {
                            max = maxPoints
                            progress = it.totalPoints
                        }
                    }
                }
            }
        }
    }

    // ========== LIFECYCLE ==========

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) {
            findViewById<View>(R.id.cardLeaderboard)?.let { card ->
                if (card.isShown && card.containsRawTouch(ev)) {
                    startActivity(Intent(this, LeaderboardActivity::class.java))
                    return true
                }
            }
            findViewById<View>(R.id.btnReportPill)?.let { cta ->
                if (cta.isShown && cta.containsRawTouch(ev)) {
                    startActivity(Intent(this, ReportActivity::class.java))
                    return true
                }
            }
            findViewById<View>(R.id.cardTask)?.let { card ->
                if (card.isShown && card.containsRawTouch(ev)) {
                    showMissionHint()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun View.containsRawTouch(ev: MotionEvent): Boolean {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return ev.rawX >= location[0] &&
            ev.rawX <= location[0] + width &&
            ev.rawY >= location[1] &&
            ev.rawY <= location[1] + height
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        if (isMapViewCreated) mapView.onResume()
    }

    override fun onPause() {
        isActivityResumed = false
        if (isMapViewCreated) mapView.onPause()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        isActivityStarted = true
        if (isMapViewCreated) mapView.onStart()
    }

    override fun onStop() {
        isActivityStarted = false
        if (isMapViewCreated) mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        if (isMapViewCreated) mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (isMapViewCreated) mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (isMapViewCreated) mapView.onSaveInstanceState(outState)
    }

    // ========== MISSION ICON MAPPER ==========

    /**
     * Maps DailyMission ID to a vector drawable resource.
     * Maps mission categories to SVG icons.
     */
    private fun getMissionIcon(missionId: String): Int {
        return when (missionId) {
            "ktx_plastic",
            "canteen_sort",
            "class_recycle" -> R.drawable.ic_recycle
            "ktx_garden",
            "yard_sweep",
            "tree_water",
            "plant_tree" -> R.drawable.ic_eco_leaf
            else -> R.drawable.ic_eco_clean
        }
    }

    private fun com.damsan.green.data.model.TrashReport.impactWasteKg(): Double {
        return if (aiEstimatedKg > 0.0) aiEstimatedKg else ESTIMATED_WASTE_KG_PER_REPORT
    }
}
