package com.example.zaman_apps

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class LoginActivity : AppCompatActivity() {

    private val urlSheet = "https://script.google.com/macros/s/AKfycby4n_8DasDqYLL-K7DQMUb_6pyF7FkuDaSWde98TDMlF0BnneJjVVwadqzuRzq-M1KguA/exec"

    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progressLogin: ProgressBar
    private lateinit var cardLogin: View
    private lateinit var cardLogo: View
    private lateinit var tvAppTitle: View
    private lateinit var tvAppSubtitle: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        setupEntranceAnimations()
        
        btnLogin.setOnClickListener {
            animateButtonClick(it) { doLogin() }
        }
    }

    private fun initViews() {
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressLogin = findViewById(R.id.progressLogin)
        cardLogin = findViewById(R.id.cardLogin)
        cardLogo = findViewById(R.id.cardLogo)
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvAppSubtitle = findViewById(R.id.tvAppSubtitle)
    }

    private fun setupEntranceAnimations() {
        val views = listOf(cardLogo, tvAppTitle, tvAppSubtitle, cardLogin)
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(150L * index)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }

    private fun animateButtonClick(view: View, onEnd: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
                onEnd()
            }
            .start()
    }

    private fun doLogin() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            showSnackbar("Username & password wajib diisi", isError = true)
            return
        }

        setLoading(true)

        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                setLoading(false)
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        val prefs = getSharedPreferences("session", MODE_PRIVATE)
                        prefs.edit()
                            .putString("username", json.optString("username"))
                            .putString("name", json.optString("name"))
                            .putString("role", json.optString("role"))
                            .putBoolean("can_edit", json.optBoolean("can_edit"))
                            .apply()

                        showSnackbar("Login berhasil! Selamat datang ${json.optString("name")}", isError = false)
                        
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        showSnackbar(json.optString("message", "Login gagal"), isError = true)
                    }
                } catch (e: Exception) {
                    showSnackbar("Gagal membaca respons server", isError = true)
                }
            },
            { error ->
                setLoading(false)
                val msg = when (error.cause) {
                    is SocketTimeoutException -> "Koneksi timeout, coba lagi"
                    is UnknownHostException -> "Tidak ada koneksi internet"
                    else -> "Koneksi terputus"
                }
                showSnackbar(msg, isError = true)
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "login"
                params["username"] = username
                params["password"] = password
                return params
            }
        }

        request.retryPolicy = DefaultRetryPolicy(15000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        queue.add(request)
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        val color = if (isError) getColor(R.color.error) else getColor(R.color.success)
        snackbar.view.setBackgroundColor(color)
        snackbar.show()
    }

    private fun setLoading(loading: Boolean) {
        progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        btnLogin.isEnabled = !loading
    }
}
