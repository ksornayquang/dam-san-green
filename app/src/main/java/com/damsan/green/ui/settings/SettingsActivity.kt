package com.damsan.green.ui.settings

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.damsan.green.R
import com.damsan.green.data.repository.BrandingSettings
import com.damsan.green.data.repository.CloudinaryRepository
import com.damsan.green.data.repository.FirebaseRepository
import com.damsan.green.data.repository.SettingsService
import com.damsan.green.ui.showDamSanConfirmDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private val settingsService = SettingsService()
    private val repo = FirebaseRepository()
    private lateinit var cloudinaryRepo: CloudinaryRepository
    private var pendingImageTarget: ImageTarget? = null

    private lateinit var etAppName: TextInputEditText
    private lateinit var etSchoolName: TextInputEditText
    private lateinit var etBannerUrl: TextInputEditText
    private lateinit var etLogoUrl: TextInputEditText
    private lateinit var etSchoolImages: TextInputEditText
    private lateinit var etSchoolBulletins: TextInputEditText
    private lateinit var etNewsTitle: TextInputEditText
    private lateinit var etNewsBody: TextInputEditText
    private lateinit var etNewsImageUrl: TextInputEditText
    private lateinit var ivBannerPreview: ImageView
    private lateinit var tvPreviewAppName: TextView
    private lateinit var tvPreviewSchoolName: TextView
    private lateinit var btnUploadSchoolImage: FrameLayout
    private lateinit var tvUploadSchoolImageText: TextView
    private lateinit var btnUploadNewsImage: FrameLayout
    private lateinit var tvUploadNewsImageText: TextView
    private lateinit var btnAddBulletin: FrameLayout
    private lateinit var btnSaveSettings: FrameLayout
    private lateinit var tvSaveSettingsText: TextView
    private lateinit var progressSettings: ProgressBar
    private lateinit var btnResetData: FrameLayout
    private lateinit var tvResetDataText: View
    private lateinit var progressResetData: ProgressBar

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadPickedImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        cloudinaryRepo = CloudinaryRepository(this)

        bindViews()
        setupClickListeners()
        setupPreviewWatchers()
        loadSettings()
    }

    private fun bindViews() {
        etAppName = findViewById(R.id.etAppName)
        etSchoolName = findViewById(R.id.etSchoolName)
        etBannerUrl = findViewById(R.id.etBannerUrl)
        etLogoUrl = findViewById(R.id.etLogoUrl)
        etSchoolImages = findViewById(R.id.etSchoolImages)
        etSchoolBulletins = findViewById(R.id.etSchoolBulletins)
        etNewsTitle = findViewById(R.id.etNewsTitle)
        etNewsBody = findViewById(R.id.etNewsBody)
        etNewsImageUrl = findViewById(R.id.etNewsImageUrl)
        ivBannerPreview = findViewById(R.id.ivBannerPreview)
        tvPreviewAppName = findViewById(R.id.tvPreviewAppName)
        tvPreviewSchoolName = findViewById(R.id.tvPreviewSchoolName)
        btnUploadSchoolImage = findViewById(R.id.btnUploadSchoolImage)
        tvUploadSchoolImageText = findViewById(R.id.tvUploadSchoolImageText)
        btnUploadNewsImage = findViewById(R.id.btnUploadNewsImage)
        tvUploadNewsImageText = findViewById(R.id.tvUploadNewsImageText)
        btnAddBulletin = findViewById(R.id.btnAddBulletin)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        tvSaveSettingsText = findViewById(R.id.tvSaveSettingsText)
        progressSettings = findViewById(R.id.progressSettings)
        btnResetData = findViewById(R.id.btnResetData)
        tvResetDataText = findViewById(R.id.tvResetDataText)
        progressResetData = findViewById(R.id.progressResetData)
    }

    private fun setupClickListeners() {
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }

        btnUploadSchoolImage.setOnClickListener {
            pendingImageTarget = ImageTarget.SCHOOL_GALLERY
            imagePicker.launch("image/*")
        }

        btnUploadNewsImage.setOnClickListener {
            pendingImageTarget = ImageTarget.NEWS
            imagePicker.launch("image/*")
        }

        btnAddBulletin.setOnClickListener {
            addBulletinDraft()
        }

        btnSaveSettings.setOnClickListener {
            val settings = collectSettings()
            setLoading(true)
            lifecycleScope.launch {
                val result = settingsService.updateBranding(settings)
                setLoading(false)

                result.fold(
                    onSuccess = {
                        applyPreview(settings)
                        showSnackbar("Đã lưu cài đặt branding", false)
                    },
                    onFailure = { error ->
                        showSnackbar("Không lưu được cài đặt: ${error.message}", true)
                    }
                )
            }
        }

        btnResetData.setOnClickListener {
            showResetDataDialog()
        }
    }

    private fun showResetDataDialog() {
        showDamSanConfirmDialog(
            title = "Reset dữ liệu test",
            message = "Thao tác này sẽ xoá toàn bộ báo cáo rác và điểm xếp hạng đang test. Tài khoản lớp, tài khoản admin và branding Cloudinary vẫn được giữ lại.",
            iconRes = R.drawable.ic_trash,
            positiveText = "Reset dữ liệu",
            negativeText = "Huỷ",
            danger = true
        ) {
                resetOperationalData()
        }
    }

    private fun uploadPickedImage(uri: Uri) {
        val target = pendingImageTarget ?: return
        setImageUploadLoading(target, true)
        lifecycleScope.launch {
            val result = runCatching {
                val compressedFile = cloudinaryRepo.compressImage(uri, quality = 82)
                cloudinaryRepo.uploadImage(
                    imageFile = compressedFile,
                    publicIdPrefix = if (target == ImageTarget.NEWS) "news" else "school"
                ).getOrThrow()
            }
            setImageUploadLoading(target, false)

            result.fold(
                onSuccess = { url ->
                    when (target) {
                        ImageTarget.SCHOOL_GALLERY -> {
                            etSchoolImages.appendCleanLine(url)
                            showSnackbar("Đã upload ảnh trường lên Cloudinary", false)
                        }
                        ImageTarget.NEWS -> {
                            etNewsImageUrl.setText(url)
                            showSnackbar("Đã upload ảnh bản tin lên Cloudinary", false)
                        }
                    }
                    applyPreview(collectSettings())
                },
                onFailure = { error ->
                    showSnackbar("Không upload được ảnh: ${error.message}", true)
                }
            )
            pendingImageTarget = null
        }
    }

    private fun addBulletinDraft() {
        val title = etNewsTitle.text?.toString()?.trim().orEmpty()
        val body = etNewsBody.text?.toString()?.trim().orEmpty()
        val imageUrl = etNewsImageUrl.text?.toString()?.trim().orEmpty()

        if (title.isBlank()) {
            etNewsTitle.error = "Nhập tiêu đề bản tin"
            return
        }
        if (body.isBlank()) {
            etNewsBody.error = "Nhập nội dung ngắn"
            return
        }

        val line = buildString {
            append(title.replace("|", "-"))
            append(" | ")
            append(body.replace("|", "-"))
            if (imageUrl.isNotBlank()) {
                append(" | ")
                append(imageUrl)
            }
        }

        etSchoolBulletins.appendCleanLine(line)
        etNewsTitle.setText("")
        etNewsBody.setText("")
        etNewsImageUrl.setText("")
        showSnackbar("Đã thêm bản tin vào danh sách. Bấm Lưu cài đặt để đăng.", false)
    }

    private fun resetOperationalData() {
        setResetLoading(true)
        lifecycleScope.launch {
            val result = repo.resetOperationalData()
            setResetLoading(false)
            result.fold(
                onSuccess = {
                    showSnackbar("Đã xoá dữ liệu test. Bảng điểm đã về 0.", false)
                },
                onFailure = { error ->
                    showSnackbar("Không reset được dữ liệu: ${error.message}", true)
                }
            )
        }
    }

    private fun setupPreviewWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyPreview(collectSettings())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        }

        etAppName.addTextChangedListener(watcher)
        etSchoolName.addTextChangedListener(watcher)
        etBannerUrl.addTextChangedListener(watcher)
        etLogoUrl.addTextChangedListener(watcher)
        etSchoolImages.addTextChangedListener(watcher)
        etSchoolBulletins.addTextChangedListener(watcher)
    }

    private fun loadSettings() {
        setLoading(true)
        lifecycleScope.launch {
            val settings = settingsService.getBranding()
            setLoading(false)
            etAppName.setText(settings.appName)
            etSchoolName.setText(settings.schoolName)
            etBannerUrl.setText(settings.bannerUrl)
            etLogoUrl.setText(settings.logoUrl)
            etSchoolImages.setText(settings.schoolImageUrls.joinToString("\n"))
            etSchoolBulletins.setText(settings.schoolBulletins.joinToString("\n"))
            applyPreview(settings)
        }
    }

    private fun collectSettings(): BrandingSettings {
        val appName = etAppName.text?.toString()?.trim().orEmpty()
        val schoolName = etSchoolName.text?.toString()?.trim().orEmpty()
        return BrandingSettings(
            appName = appName.ifBlank { BrandingSettings.DEFAULT.appName },
            schoolName = schoolName.ifBlank { BrandingSettings.DEFAULT.schoolName },
            bannerUrl = etBannerUrl.text?.toString()?.trim().orEmpty(),
            logoUrl = etLogoUrl.text?.toString()?.trim().orEmpty(),
            schoolImageUrls = etSchoolImages.toCleanLines(),
            schoolBulletins = etSchoolBulletins.toCleanLines()
        )
    }

    private fun TextInputEditText.toCleanLines(): List<String> {
        return text?.toString()
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    private fun TextInputEditText.appendCleanLine(line: String) {
        val current = text?.toString()?.trim().orEmpty()
        setText(if (current.isBlank()) line else "$current\n$line")
        setSelection(text?.length ?: 0)
    }

    private fun applyPreview(settings: BrandingSettings) {
        tvPreviewAppName.text = settings.appName
        tvPreviewSchoolName.text = settings.schoolName

        if (settings.bannerUrl.isBlank()) {
            ivBannerPreview.visibility = View.GONE
        } else {
            ivBannerPreview.visibility = View.VISIBLE
            Glide.with(this)
                .load(settings.bannerUrl)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(ivBannerPreview)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        btnSaveSettings.isEnabled = !isLoading
        tvSaveSettingsText.visibility = if (isLoading) View.GONE else View.VISIBLE
        progressSettings.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun setResetLoading(isLoading: Boolean) {
        btnResetData.isEnabled = !isLoading
        tvResetDataText.visibility = if (isLoading) View.GONE else View.VISIBLE
        progressResetData.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun setImageUploadLoading(target: ImageTarget, isLoading: Boolean) {
        val button = if (target == ImageTarget.NEWS) btnUploadNewsImage else btnUploadSchoolImage
        val label = if (target == ImageTarget.NEWS) tvUploadNewsImageText else tvUploadSchoolImageText
        button.isEnabled = !isLoading
        label.text = when {
            isLoading -> "Đang upload..."
            target == ImageTarget.NEWS -> "Chọn ảnh"
            else -> "Chọn ảnh trường và upload Cloudinary"
        }
    }

    private fun showSnackbar(message: String, isError: Boolean) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(
            ContextCompat.getColor(this, if (isError) R.color.ds_error else R.color.ds_success)
        )
        snackbar.show()
    }

    private enum class ImageTarget {
        SCHOOL_GALLERY,
        NEWS
    }
}
