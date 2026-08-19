package com.damsan.green.ui.guest

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.damsan.green.R
import com.damsan.green.data.model.ClassRanking
import com.damsan.green.data.repository.BrandingSettings
import com.damsan.green.data.repository.FirebaseRepository
import com.damsan.green.data.repository.SettingsService
import com.damsan.green.ui.auth.LoginActivity
import com.damsan.green.ui.intro.SchoolInfoActivity
import com.damsan.green.ui.leaderboard.LeaderboardActivity
import com.damsan.green.ui.map.Campus3DMapView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GuestActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var campusMapView: Campus3DMapView
    private var googleMap: GoogleMap? = null
    private val repo = FirebaseRepository()
    private val settingsService = SettingsService()
    private val guestReportMarkers = mutableListOf<Marker>()
    private var isMapViewCreated = false
    private var isActivityStarted = false
    private var isActivityResumed = false
    private var isCampus3DMode = false

    private val schoolLat = 12.900868
    private val schoolLon = 108.291116

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guest)

        mapView = findViewById(R.id.guestMapView)
        mapView.visibility = View.INVISIBLE
        campusMapView = findViewById(R.id.guestCampus3DMapView)
        campusMapView.visibility = View.GONE

        setupMapModeToggle()
        setupClickListeners()
        loadBranding()
        loadLeaderboardPreview()
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
        findViewById<TextView>(R.id.btnGuestMapModeGps)?.setOnClickListener {
            setMapMode(use3D = false)
        }
        findViewById<TextView>(R.id.btnGuestMapMode3d)?.setOnClickListener {
            setMapMode(use3D = true)
        }
        setMapMode(use3D = false)
    }

    private fun setMapMode(use3D: Boolean) {
        isCampus3DMode = use3D
        if (::campusMapView.isInitialized) {
            campusMapView.visibility = if (use3D) View.VISIBLE else View.GONE
        }
        if (::mapView.isInitialized) {
            mapView.visibility = if (!use3D && isMapViewCreated) View.VISIBLE else View.INVISIBLE
        }

        val gpsButton = findViewById<TextView>(R.id.btnGuestMapModeGps)
        val map3DButton = findViewById<TextView>(R.id.btnGuestMapMode3d)
        gpsButton?.setBackgroundResource(
            if (use3D) R.drawable.bg_map_mode_unselected else R.drawable.bg_map_mode_selected
        )
        gpsButton?.setTextColor(
            if (use3D) getColor(R.color.primary_forest_green) else getColor(R.color.white)
        )
        map3DButton?.setBackgroundResource(
            if (use3D) R.drawable.bg_map_mode_selected else R.drawable.bg_map_mode_unselected
        )
        map3DButton?.setTextColor(
            if (use3D) getColor(R.color.white) else getColor(R.color.primary_forest_green)
        )
    }

    private fun setupClickListeners() {
        findViewById<FrameLayout>(R.id.btnGuestLogin)?.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        findViewById<FrameLayout>(R.id.btnAiBubbleGuest)?.setOnClickListener {
            startActivity(
                Intent(this, SchoolInfoActivity::class.java)
                    .putExtra(SchoolInfoActivity.EXTRA_OPEN_CHAT, true)
            )
        }

        findViewById<FrameLayout>(R.id.btnViewLeaderboard)?.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.btnGuestReport)?.setOnClickListener {
            Toast.makeText(
                this,
                "Đăng nhập tài khoản lớp để chụp ảnh báo cáo!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun loadBranding() {
        lifecycleScope.launch {
            applyBranding(settingsService.getBranding())
        }
    }

    private fun applyBranding(branding: BrandingSettings) {
        val logoCard = findViewById<View>(R.id.cardGuestLogo)
        val logo = findViewById<ImageView>(R.id.ivGuestLogo)
        logoCard.visibility = View.VISIBLE
        logo.visibility = View.VISIBLE
        logo.setImageResource(R.drawable.logo_damsan_green)

        if (branding.logoUrl.isNotBlank()) {
            Glide.with(this)
                .load(branding.logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(logo)
        }
    }

    private fun loadLeaderboardPreview() {
        lifecycleScope.launch {
            repo.getLeaderboardFlow().collectLatest { rankings ->
                renderLeaderboardPreview(rankings)
            }
        }
    }

    private fun renderLeaderboardPreview(rankings: List<ClassRanking>) {
        if (rankings.isEmpty()) {
            findViewById<TextView>(R.id.tvGuestRank1)?.text = "Chưa có dữ liệu xếp hạng"
            findViewById<TextView>(R.id.tvGuestRank2)?.text = "Đăng nhập lớp để bắt đầu ghi điểm xanh"
            findViewById<TextView>(R.id.tvGuestRank3)?.text = "BXH sẽ cập nhật khi có báo cáo được duyệt"
            return
        }

        val top1 = rankings.getOrNull(0)
        val top2 = rankings.getOrNull(1)
        val top3 = rankings.getOrNull(2)

        findViewById<TextView>(R.id.tvGuestRank1)?.text =
            top1?.let { "Hạng 1 - Lớp ${it.className} - ${it.totalPoints}đ" }
                ?: "Hạng 1 đang chờ dữ liệu"
        findViewById<TextView>(R.id.tvGuestRank2)?.text =
            top2?.let { "Hạng 2 - Lớp ${it.className} - ${it.totalPoints}đ" }
                ?: "Hạng 2 đang chờ dữ liệu"
        findViewById<TextView>(R.id.tvGuestRank3)?.text =
            top3?.let { "Hạng 3 - Lớp ${it.className} - ${it.totalPoints}đ" }
                ?: "Hạng 3 đang chờ dữ liệu"
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = false
        }

        val schoolPosition = LatLng(schoolLat, schoolLon)
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(schoolPosition)
                    .zoom(17f)
                    .tilt(30f)
                    .build()
            )
        )

        map.addMarker(
            MarkerOptions()
                .position(schoolPosition)
                .title("Trường PTDTNT Đam San")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )?.showInfoWindow()

        lifecycleScope.launch {
            repo.getAllReportsFlow().collectLatest { reports ->
                campusMapView.setReports(reports.filter { it.status == "approved" })
                guestReportMarkers.forEach { it.remove() }
                guestReportMarkers.clear()

                reports
                    .filter { it.latitude != 0.0 && it.longitude != 0.0 && it.status == "approved" }
                    .forEach { report ->
                        val marker = googleMap?.addMarker(
                            MarkerOptions()
                                .position(LatLng(report.latitude, report.longitude))
                                .title("Lớp ${report.className}")
                                .snippet("+${report.points}đ · Đã duyệt")
                                .icon(
                                    BitmapDescriptorFactory.defaultMarker(
                                        BitmapDescriptorFactory.HUE_GREEN
                                    )
                                )
                        )
                        if (marker != null) guestReportMarkers.add(marker)
                    }
            }
        }
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
}
