package com.example.zaman_apps

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.view.animation.AnimationUtils
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.zaman_apps.databinding.ActivitySplashScreenBinding
import com.example.zaman_apps.input_form.InputanActivity
import com.example.zaman_apps.utils.DropdownOptionsHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inisialisasi View Binding
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Mengatur status bar agar transparan dengan gradient
        setupStatusBar()

        // Mengatur window insets
        setupWindowInsets()

        // Menjalankan animasi dan navigasi
        startSplashAnimation()
    }

    private fun setupStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.apply {
                // Warna status bar transparan untuk efek gradient penuh
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    statusBarColor = ContextCompat.getColor(this@SplashScreenActivity, R.color.brand_bg_light)
                } else {
                    @Suppress("DEPRECATION")
                    statusBarColor = ContextCompat.getColor(this@SplashScreenActivity, R.color.brand_bg_light)
                }

                // Untuk Android 10+ bisa menggunakan full screen
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun startSplashAnimation() {
        // Load animasi fade in
        val fadeInAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        fadeInAnimation.duration = 1500 // 1.5 detik

        // Load animasi scale (zoom in)
        val scaleAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        scaleAnimation.duration = 2000 // 2 detik

        // Terapkan animasi ke logo container
        binding.cvLogoContainer.apply {
            startAnimation(fadeInAnimation)
            // Efek scale dengan animasi custom
            animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .setDuration(2000)
                .start()
        }

        // Animasi fade in untuk text
        binding.tvAppName.startAnimation(fadeInAnimation)
        binding.tvSubtitle.startAnimation(fadeInAnimation)
        binding.progressBar.startAnimation(fadeInAnimation)
        binding.tvLoading.startAnimation(fadeInAnimation)

        // Sync dropdown options dari cloud di background
        DropdownOptionsHelper.syncCloudOptions(this)

        // Coroutine untuk navigasi dengan delay
        lifecycleScope.launch {
            delay(2500) // Tunggu 2.5 detik untuk animasi selesai

            // Intent ke InputanActivity
            // Cek dulu apakah user sudah pernah login sebelumnya
            val prefs = getSharedPreferences("session", MODE_PRIVATE)
            val savedUsername = prefs.getString("username", null)

            val targetActivity = if (savedUsername != null) {
                MainActivity::class.java
            } else {
                LoginActivity::class.java
            }
            val intent = Intent(this@SplashScreenActivity, targetActivity)
            startActivity(intent)

            // Transisi animasi
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

            // Tutup SplashScreen
            finish()
        }
    }

    // Opsional: Method untuk mencegah back button menutup splash screen
    override fun onBackPressed() {
        // Nonaktifkan back button selama splash screen
        // Tidak melakukan apapun
    }
}