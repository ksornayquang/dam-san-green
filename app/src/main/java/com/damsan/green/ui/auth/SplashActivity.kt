package com.damsan.green.ui.auth

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.damsan.green.R
import com.damsan.green.data.repository.BrandingSettings
import com.damsan.green.data.repository.FirebaseRepository
import com.damsan.green.data.repository.SettingsService
import com.damsan.green.ui.MainActivity
import com.damsan.green.ui.admin.AdminActivity
import com.damsan.green.ui.guest.GuestActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SplashActivity : AppCompatActivity() {

    private val repo = FirebaseRepository()
    private val settingsService = SettingsService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        loadBranding()
        animateSplash()

        Handler(Looper.getMainLooper()).postDelayed({
            navigateByRole()
        }, 2500)
    }

    private fun loadBranding() {
        lifecycleScope.launch {
            val branding = settingsService.getBranding()
            applyBranding(branding)
        }
    }

    private fun applyBranding(branding: BrandingSettings) {
        val banner = findViewById<ImageView>(R.id.ivSplashBanner)
        banner.visibility = View.GONE
        val splashBackgroundUrl = branding.bannerUrl.ifBlank {
            branding.schoolImageUrls.firstOrNull().orEmpty()
        }
        if (splashBackgroundUrl.isNotBlank()) {
            banner.visibility = View.VISIBLE
            Glide.with(this)
                .load(splashBackgroundUrl)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(banner)
        }

        val logoFrame = findViewById<View>(R.id.logoFrame)
        val logo = findViewById<ImageView>(R.id.ivSplashLogo)
        logo.setImageResource(R.drawable.logo_damsan_green)
        logoFrame.visibility = View.VISIBLE
        if (branding.logoUrl.isNotBlank()) {
            Glide.with(this)
                .load(branding.logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        logo.setImageResource(R.drawable.logo_damsan_green)
                        logoFrame.visibility = View.VISIBLE
                        return true
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        logoFrame.alpha = 0f
                        logoFrame.scaleX = 0.85f
                        logoFrame.scaleY = 0.85f
                        logoFrame.visibility = View.VISIBLE
                        logoFrame.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(360)
                            .setInterpolator(OvershootInterpolator(1.05f))
                            .start()
                        return false
                    }
                })
                .into(logo)
        }

        val appName = branding.appName.trim().ifBlank { BrandingSettings.DEFAULT.appName }
        val mainText = appName.removeSuffix("Green").trim().ifBlank { appName }
        findViewById<TextView>(R.id.tvSplashBrandMain)?.text = mainText.uppercase()
        findViewById<TextView>(R.id.tvSplashGreen)?.text =
            if (appName.endsWith("Green", ignoreCase = true)) "GREEN" else ""
        findViewById<TextView>(R.id.tvSplashSubtitle)?.text = branding.schoolName
    }

    private fun animateSplash() {
        // Logo — scale from 0 to 1 with overshoot
        val card = findViewById<View>(R.id.splashCard)
        card?.apply {
            translationY = 38f
            scaleX = 0f
            scaleY = 0f
            alpha = 0f
            animate()
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(800)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }

        val logo = findViewById<View>(R.id.logoFrame)
        logo?.apply {
            scaleX = 0.7f
            scaleY = 0.7f
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(700)
                .setStartDelay(220)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }

        // Brand name row — slide up + fade in with delay
        val brandName = findViewById<View>(R.id.brandNameRow)
        brandName?.apply {
            translationY = 30f
            alpha = 0f
            animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(400)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // GREEN text — slide up + fade in with more delay
        val greenText = findViewById<View>(R.id.tvSplashGreen)
        greenText?.apply {
            translationY = 30f
            alpha = 0f
            animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(550)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // Subtitle — fade in last
        val subtitle = findViewById<View>(R.id.tvSplashSubtitle)
        subtitle?.apply {
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(500)
                .setStartDelay(800)
                .start()
        }

        listOf(R.id.splashFeatureRow, R.id.splashStatusRow, R.id.tvSplashFooter).forEachIndexed { index, id ->
            findViewById<View>(id)?.apply {
                translationY = 22f
                alpha = 0f
                animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(520)
                    .setStartDelay(900L + index * 120L)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        }
    }

    private fun navigateByRole() {
        if (!repo.isLoggedIn()) {
            // Chưa đăng nhập → Login (có nút Guest)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Đã đăng nhập → kiểm tra role
        val uid = repo.getCurrentUser()?.uid ?: run {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            val role = withTimeoutOrNull(3000) {
                repo.getUserRole(uid)
            } ?: "student"
            val intent = when (role) {
                "admin" -> Intent(this@SplashActivity, AdminActivity::class.java)
                "student" -> Intent(this@SplashActivity, MainActivity::class.java)
                else -> Intent(this@SplashActivity, MainActivity::class.java)
            }
            startActivity(intent)
            finish()
        }
    }
}
