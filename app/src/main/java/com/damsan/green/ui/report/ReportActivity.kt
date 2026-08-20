package com.damsan.green.ui.report

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.damsan.green.BuildConfig
import com.damsan.green.R
import com.damsan.green.data.model.TrashReport
import com.damsan.green.data.repository.CloudinaryRepository
import com.damsan.green.data.repository.FirebaseRepository
import com.damsan.green.data.repository.WasteAiReviewService
import com.damsan.green.ui.showDamSanActionDialog
import com.damsan.green.utils.DemoModeSettings
import com.damsan.green.utils.LocationHelper
import com.damsan.green.utils.SoundFeedback
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ReportActivity : AppCompatActivity() {

    private val firebaseRepo = FirebaseRepository()
    private val wasteAiReviewService = WasteAiReviewService()
    private lateinit var cloudinaryRepo: CloudinaryRepository
    private lateinit var locationHelper: LocationHelper

    private enum class CaptureStage { BEFORE, AFTER }
    private var captureStage = CaptureStage.BEFORE
    private var beforePhotoUri: Uri? = null
    private var afterPhotoUri: Uri? = null
    private var currentPhotoUri: Uri? = null
    private var currentPhotoFile: File? = null
    private var trashType: String? = null
    private var currentLat = 0.0
    private var currentLon = 0.0
    private var persistDraftOnPause = true

    // UI Views
    private lateinit var ivPreview: ImageView
    private lateinit var btnCamera: View
    private lateinit var btnSubmit: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var etReporterName: EditText
    private lateinit var tvLocation: TextView
    private lateinit var tvLocationIcon: ImageView
    private lateinit var ivAfterPreview: ImageView
    private lateinit var afterPhotoCard: View
    private lateinit var tvStepGuide: TextView
    private lateinit var switchDemoMode: SwitchCompat
    private lateinit var tvDemoModeLabel: TextView
    private var updatingDemoSwitch = false

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            currentPhotoUri?.let { uri ->
                SoundFeedback.photoCaptured(this)
                when (captureStage) {
                    CaptureStage.BEFORE -> {
                        beforePhotoUri = uri
                        afterPhotoUri = null
                        trashType = null
                        afterPhotoCard.visibility = View.VISIBLE
                        captureStage = CaptureStage.AFTER
                        btnSubmit.isEnabled = false
                        btnSubmit.visibility = View.INVISIBLE
                        ImageViewCompat.setImageTintList(ivPreview, null)
                        ivPreview.setPadding(0, 0, 0, 0)
                        Glide.with(this).load(uri).centerCrop().into(ivPreview)
                        saveDraft()
                        fetchGPS()
                        tvStepGuide.text = "BƯỚC 2/2  •  TỚI THÙNG RÁC RỒI CHỤP MINH CHỨNG"
                        tvStatus.text = "Ảnh hiện trạng đã lưu. Bạn có thể tạm dừng, tới thùng phù hợp rồi chụp ảnh minh chứng."
                    }
                    CaptureStage.AFTER -> {
                        afterPhotoUri = uri
                        Glide.with(this).load(uri).centerCrop().into(ivAfterPreview)
                        afterPhotoCard.visibility = View.VISIBLE
                        btnSubmit.isEnabled = true
                        btnSubmit.visibility = View.VISIBLE
                        tvStepGuide.text = "HOÀN TẤT  •  ĐỦ 2 ẢNH XÁC THỰC"
                        tvStatus.text = "Đã đủ 2 ảnh xác thực. Bạn có thể gửi báo cáo."
                        saveDraft()
                    }
                }
            }
        }
    }

    // Permission launchers
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraOk = permissions[Manifest.permission.CAMERA] == true
        if (cameraOk) openCamera(captureStage)
        else showSnackbar("Cần quyền Camera để chụp ảnh!", true)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationOk = permissions.values.any { it }
        if (locationOk) fetchGPS()
        else tvLocation.text = "Không có quyền GPS - Vị trí sẽ không được lưu"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        cloudinaryRepo = CloudinaryRepository(this)
        locationHelper = LocationHelper(this)

        setupViews()
        restoreDraft(savedInstanceState)
        setupDemoModeSwitch()
        setupClickListeners()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveDraft()
        outState.putString(KEY_CURRENT_URI, currentPhotoUri?.toString())
        outState.putString(KEY_CAPTURE_STAGE, captureStage.name)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        if (persistDraftOnPause && ::etReporterName.isInitialized) saveDraft()
        super.onPause()
    }

    private fun setupViews() {
        ivPreview = findViewById(R.id.ivPhotoPreview)
        btnCamera = findViewById(R.id.btnTakePhoto)
        btnSubmit = findViewById(R.id.btnSubmit)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        etReporterName = findViewById(R.id.etReporterName)
        tvLocation = findViewById(R.id.tvLocation)
        tvLocationIcon = findViewById(R.id.tvLocationIcon)
        ivAfterPreview = findViewById(R.id.ivAfterPhotoPreview)
        afterPhotoCard = findViewById(R.id.afterPhotoCard)
        tvStepGuide = findViewById(R.id.tvStepGuide)
        switchDemoMode = findViewById(R.id.switchDemoMode)
        tvDemoModeLabel = findViewById(R.id.tvDemoModeLabel)

        btnSubmit.isEnabled = false
        btnSubmit.visibility = View.INVISIBLE

        findViewById<TextView>(R.id.tvClassBadge).text =
            classNameFromEmail(firebaseRepo.getCurrentUser()?.email.orEmpty())

        // Lấy tên lớp từ Firebase
        val uid = firebaseRepo.getCurrentUser()?.uid ?: ""
        if (uid.isNotEmpty()) {
            lifecycleScope.launch {
                val className = firebaseRepo.getClassName(uid)
                if (className != "Unknown") {
                    findViewById<TextView>(R.id.tvClassBadge).text = className
                }
            }
        }
    }

    private fun classNameFromEmail(email: String): String {
        val localPart = email.substringBefore("@").uppercase(Locale.getDefault())
        return if (localPart.matches(Regex("""\d{2}A\d+"""))) localPart else "..."
    }

    private fun setupClickListeners() {
        btnCamera.setOnClickListener {
            checkAndOpenCamera()
        }
        
        ivPreview.setOnClickListener { captureStage = CaptureStage.BEFORE; checkAndOpenCamera() }
        afterPhotoCard.setOnClickListener { captureStage = CaptureStage.AFTER; checkAndOpenCamera() }

        btnSubmit.setOnClickListener { submitReport() }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun setupDemoModeSwitch() {
        renderDemoMode(DemoModeSettings.isEnabled(this))
        switchDemoMode.setOnCheckedChangeListener { _, checked ->
            if (updatingDemoSwitch) return@setOnCheckedChangeListener
            if (checked) {
                renderDemoMode(false)
                showEnableDemoModeDialog()
            } else {
                DemoModeSettings.setEnabled(this, false)
                renderDemoMode(false)
                showSnackbar("Đã bật lại giới hạn vị trí 500 m quanh trường.", false)
            }
        }
        findViewById<View>(R.id.demoModeRow).setOnClickListener {
            switchDemoMode.isChecked = !switchDemoMode.isChecked
        }
    }

    private fun showEnableDemoModeDialog() {
        val pinLayout = TextInputLayout(this).apply {
            hint = "Mã trình diễn"
            isErrorEnabled = true
            setStartIconDrawable(R.drawable.ic_lock)
        }
        val pinInput = TextInputEditText(pinLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            isSingleLine = true
        }
        pinLayout.addView(pinInput)

        showDamSanActionDialog(
            title = "Mở chế độ trình diễn BGK",
            message = "GPS vẫn được lưu, nhưng báo cáo ngoài bán kính 500 m sẽ được phép gửi và đánh dấu demoMode trong hệ thống.",
            iconRes = R.drawable.ic_shield,
            positiveText = "Bật trình diễn",
            negativeText = "Huỷ",
            contentView = pinLayout,
            dismissOnPositive = false
        ) { dialog ->
            if (pinInput.text?.toString() == BuildConfig.DEMO_MODE_PIN) {
                pinLayout.error = null
                DemoModeSettings.setEnabled(this, true)
                renderDemoMode(true)
                dialog.dismiss()
                showSnackbar("Đã bật chế độ trình diễn: cho phép gửi ngoài trường.", false)
            } else {
                pinLayout.error = "Mã trình diễn không đúng"
            }
        }.setOnDismissListener {
            renderDemoMode(DemoModeSettings.isEnabled(this))
        }
    }

    private fun renderDemoMode(enabled: Boolean) {
        updatingDemoSwitch = true
        switchDemoMode.isChecked = enabled
        tvDemoModeLabel.text = if (enabled) {
            "CHẾ ĐỘ BGK: ngoài trường được phép"
        } else {
            "Giới hạn vị trí trường: 500 m"
        }
        tvDemoModeLabel.setTextColor(
            ContextCompat.getColor(this, if (enabled) R.color.ds_warning else R.color.eco_text_muted)
        )
        updatingDemoSwitch = false
    }

    private fun checkAndOpenCamera() {
        persistDraftOnPause = true
        val cameraGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (cameraGranted) openCamera(captureStage)
        else cameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun openCamera(stage: CaptureStage) {
        captureStage = stage
        val photoFile = createImageFile()
        currentPhotoFile = photoFile
        currentPhotoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
        }
        cameraLauncher.launch(intent)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("TRASH_${timeStamp}_", ".jpg", storageDir)
    }

    private fun fetchGPS() {
        val hasLocationPerm = locationHelper.hasLocationPermission()

        if (!hasLocationPerm) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        tvLocation.text = "Đang lấy tọa độ GPS..."
        tvLocationIcon.setImageResource(R.drawable.ic_map_pin)

        lifecycleScope.launch {
            val location = locationHelper.getCurrentLocation()
            if (location != null) {
                currentLat = location.latitude
                currentLon = location.longitude
                val formatted = locationHelper.formatCoordinates(currentLat, currentLon)
                tvLocation.text = "$formatted\n±${location.accuracy.toInt()}m"
                tvLocationIcon.setImageResource(R.drawable.ic_check)
                saveDraft()
            } else {
                tvLocation.text = "Không lấy được GPS. Vị trí sẽ là 0,0"
                tvLocationIcon.setImageResource(R.drawable.ic_close)
            }
        }
    }

    private fun submitReport() {
        if (!hasInternetConnection()) {
            showSnackbar("Không có kết nối Internet. Báo cáo chưa được gửi.", true)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            showSnackbar("Cần cấp quyền Camera trước khi gửi báo cáo.", true)
            cameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            return
        }
        if (!isSchoolLocationValid()) {
            val locationError = if (currentLat == 0.0 || currentLon == 0.0) {
                "Chưa lấy được GPS. Hãy chụp lại hoặc bật quyền vị trí."
            } else {
                "Bạn đang ở ngoài bán kính 500 m của trường. Chỉ bật chế độ BGK khi trình diễn."
            }
            showSnackbar(locationError, true)
            return
        }

        val reporterName = etReporterName.text.toString().trim()
        if (reporterName.isEmpty()) {
            showSnackbar("Nhập tên người báo cáo đi!", true)
            return
        }

        val beforeUri = beforePhotoUri ?: run {
            showSnackbar("Chụp ảnh hiện trạng trước đã!", true)
            return
        }
        val afterUri = afterPhotoUri ?: run {
            showSnackbar("Cần chụp ảnh rác đã bỏ vào đúng thùng để xác thực!", true)
            return
        }
        val uid = firebaseRepo.getCurrentUser()?.uid ?: run {
            showSnackbar("Chưa đăng nhập!", true)
            return
        }

        setUploadState(true)

        lifecycleScope.launch {
            try {
                // 1. Lấy tên lớp
                val className = firebaseRepo.getClassName(uid)

                // 2. Nén ảnh
                tvStatus.text = "Đang nén ảnh..."
                val compressedBefore = cloudinaryRepo.compressImage(beforeUri)
                val compressedAfter = cloudinaryRepo.compressImage(afterUri)

                // 3. Upload lên Cloudinary
                tvStatus.text = "Đang đưa ảnh lên đám mây..."
                val uploadResult = cloudinaryRepo.uploadImage(compressedBefore)

                uploadResult.fold(
                    onSuccess = { beforeUrl ->
                        val afterResult = cloudinaryRepo.uploadImage(compressedAfter)
                        afterResult.fold(
                            onFailure = { e -> setUploadState(false); showError("Lỗi upload ảnh xác thực: ${e.message}") },
                            onSuccess = { afterUrl ->
                        tvStatus.text = "AI đang kiểm tra minh chứng..."
                        val aiReview = wasteAiReviewService.analyzeTrashPhotos(compressedBefore, compressedAfter)
                        val inferredTrashType = if (aiReview.category == "RECYCLABLE") "recyclable" else "household"
                        val reportStatus = if (aiReview.autoApproved && aiReview.isTrash && aiReview.afterIsDisposed) "approved" else "pending"
                        val reportPoints = pointsForEstimatedWaste(aiReview.estimatedKg)
                        val isDemoMode = DemoModeSettings.isEnabled(this@ReportActivity)

                        val coordinateText = String.format(Locale.US, "%.6f, %.6f", currentLat, currentLon)
                        val detectedName = aiReview.trashName.ifBlank { "rác chưa xác định" }
                        val estimatedText = String.format(Locale.US, "%.3f", aiReview.estimatedKg)
                        tvStatus.text = "Đã phát hiện $detectedName (~$estimatedText kg), dự kiến $reportPoints điểm tại $coordinateText"
                        showSnackbar(tvStatus.text.toString(), false)

                        // 4. Lưu vào Firebase
                        tvStatus.text = "Sắp xong rồi..."
                        val baseReport = TrashReport(
                            className = className,
                            reporterName = reporterName,
                            imageUrl = beforeUrl,
                            imageBeforeUrl = beforeUrl,
                            imageAfterUrl = afterUrl,
                            trashType = inferredTrashType,
                            latitude = currentLat,
                            longitude = currentLon,
                            timestamp = System.currentTimeMillis(),
                            address = if (isDemoMode) "Trình diễn ngoài trường" else "Trường Đam San",
                            demoMode = isDemoMode
                        )

                        val report = baseReport.copy(
                            points = reportPoints,
                            status = reportStatus,
                            aiIsTrash = aiReview.isTrash,
                            aiTrashName = aiReview.trashName,
                            aiCategory = aiReview.category,
                            aiReviewStatus = aiReview.reviewStatus,
                            aiWasteType = aiReview.wasteType,
                            aiDetectedItems = aiReview.detectedItems,
                            aiEstimatedKg = aiReview.estimatedKg,
                            aiConfidence = aiReview.confidence,
                            aiReason = aiReview.reason,
                            aiWarnings = aiReview.warnings,
                            aiAutoApproved = aiReview.autoApproved,
                            aiAfterIsDisposed = aiReview.afterIsDisposed,
                            aiAnalyzedAt = System.currentTimeMillis()
                        )

                        val saveResult = firebaseRepo.saveTrashReport(report)
                        saveResult.fold(
                            onSuccess = {
                                tvStatus.text = if (isDemoMode) {
                                    "Hệ thống xác nhận: Báo cáo trình diễn đã lưu kèm tọa độ thực tế."
                                } else {
                                    "Hệ thống xác nhận: Báo cáo đúng địa chỉ, vị trí hợp lệ."
                                }
                                showSnackbar(tvStatus.text.toString(), false)
                                delay(900)
                                setUploadState(false)
                                clearDraft(stopPersisting = true)
                                if (reportStatus == "approved") {
                                    showSuccess(className, reportPoints)
                                } else {
                                    val reason = aiReview.reason.ifBlank { "AI chưa xác nhận đủ ảnh trước và ảnh minh chứng sau." }
                                    resetFormAfterSubmit()
                                    showPendingReview(reason)
                                }
                            },
                            onFailure = { e ->
                                setUploadState(false)
                                showError("Lỗi lưu dữ liệu: ${e.message}")
                            }
                        )
                            }
                        )
                    },
                    onFailure = { e ->
                        setUploadState(false)
                        showError("Lỗi upload ảnh: ${e.message}")
                    }
                )

            } catch (e: Exception) {
                setUploadState(false)
                showError("Lỗi: ${e.message}")
            }
        }
    }

    private fun setUploadState(isUploading: Boolean) {
        progressBar.visibility = if (isUploading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !isUploading
        btnCamera.isEnabled = !isUploading
        if (!isUploading) tvStatus.text = ""
    }

    private fun showSuccess(className: String, awardedPoints: Int) {
        triggerConfetti()
        triggerHaptic()
        SoundFeedback.success(this)

        val message = "Hoàn thành nhiệm vụ! Lớp $className được cộng $awardedPoints điểm thi đua."
        tvStatus.text = message
        showSnackbar(message, false)

        // Delay 1.5 giây cho đẹp rồi finish
        window.decorView.postDelayed({
            setResult(RESULT_OK)
            finish()
        }, 1500)
    }

    private fun showPendingReview(reason: String) {
        val message = "Báo cáo đã lưu và đang chờ Ban thi đua duyệt. $reason"
        tvStatus.text = message
        showSnackbar(message, false)
    }

    private fun resetFormAfterSubmit() {
        beforePhotoUri = null
        afterPhotoUri = null
        currentPhotoUri = null
        currentPhotoFile = null
        trashType = null
        captureStage = CaptureStage.BEFORE
        currentLat = 0.0
        currentLon = 0.0
        ivPreview.setImageResource(R.drawable.img_placeholder_upload)
        ivPreview.setPadding(dp(40), dp(40), dp(40), dp(40))
        ImageViewCompat.setImageTintList(ivPreview, null)
        ivAfterPreview.setImageDrawable(null)
        afterPhotoCard.visibility = View.GONE
        btnSubmit.isEnabled = false
        btnSubmit.visibility = View.INVISIBLE
        tvStepGuide.text = "BƯỚC 1/2  •  CHỤP HIỆN TRẠNG"
        tvLocation.text = "Chụp ảnh để lấy tọa độ GPS..."
        tvLocationIcon.setImageResource(R.drawable.ic_pin_3d)
        etReporterName.text?.clear()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Converts the conservative AI mass estimate into an explainable 3-15 point score. */
    private fun pointsForEstimatedWaste(estimatedKg: Double): Int = when {
        estimatedKg <= 0.02 -> 3
        estimatedKg <= 0.05 -> 5
        estimatedKg <= 0.15 -> 7
        estimatedKg <= 0.35 -> 9
        estimatedKg <= 0.75 -> 12
        else -> 15
    }

    private fun saveDraft() {
        if (!::etReporterName.isInitialized) return
        val prefs = getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putLong(KEY_TIMESTAMP, System.currentTimeMillis())
        editor.putString(KEY_BEFORE_URI, beforePhotoUri?.toString())
        editor.putString(KEY_AFTER_URI, afterPhotoUri?.toString())
        editor.putString(KEY_TRASH_TYPE, trashType)
        editor.putString(KEY_CAPTURE_STAGE, captureStage.name)
        editor.putString(KEY_REPORTER_NAME, etReporterName.text.toString())
        editor.putLong(KEY_LAT_BITS, java.lang.Double.doubleToRawLongBits(currentLat))
        editor.putLong(KEY_LON_BITS, java.lang.Double.doubleToRawLongBits(currentLon))
        editor.apply()
    }

    private fun restoreDraft(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE)
        val savedAt = prefs.getLong(KEY_TIMESTAMP, 0L)
        if (savedAt == 0L || System.currentTimeMillis() - savedAt > DRAFT_MAX_AGE_MS) {
            clearDraft()
            return
        }

        beforePhotoUri = prefs.getString(KEY_BEFORE_URI, null)?.toUriOrNull()
        afterPhotoUri = prefs.getString(KEY_AFTER_URI, null)?.toUriOrNull()
        trashType = prefs.getString(KEY_TRASH_TYPE, null)
        captureStage = runCatching {
            CaptureStage.valueOf(prefs.getString(KEY_CAPTURE_STAGE, CaptureStage.BEFORE.name).orEmpty())
        }.getOrDefault(CaptureStage.BEFORE)
        etReporterName.setText(prefs.getString(KEY_REPORTER_NAME, "").orEmpty())
        currentLat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAT_BITS, 0L))
        currentLon = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LON_BITS, 0L))

        // A pending camera result must survive an activity recreation as well.
        savedInstanceState?.getString(KEY_CURRENT_URI)?.toUriOrNull()?.let { currentPhotoUri = it }
        savedInstanceState?.getString(KEY_CAPTURE_STAGE)?.let {
            captureStage = runCatching { CaptureStage.valueOf(it) }.getOrDefault(captureStage)
        }

        beforePhotoUri?.let {
            ImageViewCompat.setImageTintList(ivPreview, null)
            ivPreview.setPadding(0, 0, 0, 0)
            Glide.with(this).load(it).centerCrop().into(ivPreview)
        }
        if (afterPhotoUri != null) {
            Glide.with(this).load(afterPhotoUri).centerCrop().into(ivAfterPreview)
            afterPhotoCard.visibility = View.VISIBLE
            btnSubmit.isEnabled = true
            btnSubmit.visibility = View.VISIBLE
            tvStepGuide.text = "HOÀN TẤT  •  ĐỦ 2 ẢNH XÁC THỰC"
            tvStatus.text = "Đã khôi phục bản nháp. Bạn có thể gửi báo cáo."
        } else if (beforePhotoUri != null) {
            afterPhotoCard.visibility = View.VISIBLE
            captureStage = CaptureStage.AFTER
            tvStepGuide.text = "BƯỚC 2/2  •  CHỜ ẢNH TRONG THÙNG"
            tvStatus.text = "Bản nháp đã khôi phục. Tới đúng thùng rác rồi bấm camera để chụp minh chứng."
        }
        if (currentLat != 0.0 && currentLon != 0.0) {
            tvLocation.text = locationHelper.formatCoordinates(currentLat, currentLon)
        }
    }

    private fun clearDraft(stopPersisting: Boolean = false) {
        if (stopPersisting) persistDraftOnPause = false
        getSharedPreferences(DRAFT_PREFS, MODE_PRIVATE).edit().clear().apply()
    }

    private fun String.toUriOrNull(): Uri? = runCatching { Uri.parse(this) }.getOrNull()

    private fun hasInternetConnection(): Boolean {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isSchoolLocationValid(): Boolean {
        if (currentLat == 0.0 || currentLon == 0.0) return false
        if (DemoModeSettings.isEnabled(this)) return true
        val distance = FloatArray(1)
        Location.distanceBetween(
            SCHOOL_LATITUDE,
            SCHOOL_LONGITUDE,
            currentLat,
            currentLon,
            distance
        )
        return distance[0] <= MAX_SCHOOL_DISTANCE_METERS
    }

    private fun triggerConfetti() {
        val konfettiView = findViewById<KonfettiView>(R.id.konfettiView) ?: return

        val party = Party(
            emitter = Emitter(duration = 2, TimeUnit.SECONDS).perSecond(40),
            position = Position.Relative(0.5, 0.0),
            colors = listOf(
                0xFFE5C158.toInt(),
                0xFF1E4D2B.toInt(),
                0xFFD05A3F.toInt(),
                0xFFF9F6F0.toInt()
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
        } catch (_: Exception) { }
    }

    private fun showError(msg: String) {
        SoundFeedback.error(this)
        showSnackbar(msg, true)
        tvStatus.text = msg
    }

    private fun showSnackbar(message: String, isError: Boolean) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        if (isError) {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.accent_earth_red))
        } else {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.primary_forest_green))
        }
        snackbar.show()
    }

    private companion object {
        const val SCHOOL_LATITUDE = 12.900868056693273
        const val SCHOOL_LONGITUDE = 108.2911159047231
        const val MAX_SCHOOL_DISTANCE_METERS = 500f
        const val DRAFT_PREFS = "trash_report_draft"
        const val DRAFT_MAX_AGE_MS = 48L * 60L * 60L * 1000L
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_BEFORE_URI = "before_uri"
        const val KEY_AFTER_URI = "after_uri"
        const val KEY_TRASH_TYPE = "trash_type"
        const val KEY_CAPTURE_STAGE = "capture_stage"
        const val KEY_CURRENT_URI = "current_uri"
        const val KEY_REPORTER_NAME = "reporter_name"
        const val KEY_LAT_BITS = "lat_bits"
        const val KEY_LON_BITS = "lon_bits"
    }
}
