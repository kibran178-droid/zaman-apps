package com.example.zaman_apps

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.zaman_apps.databinding.ActivityMainBinding
import com.example.zaman_apps.input_form.EditDataActivity
import com.example.zaman_apps.input_form.InputanActivity
import com.example.zaman_apps.utils.NotificationHelper
import com.example.zaman_apps.utils.ServerConfig
import com.example.zaman_apps.utils.SessionHelper
import org.json.JSONObject
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val urlSheet = ServerConfig.SCRIPT_URL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        val name = SessionHelper.getName(this)
        binding.tvWelcome.text = getGreetingByTime(name)

        binding.swipeRefresh.setColorSchemeResources(R.color.brand_primary)
        binding.swipeRefresh.setOnRefreshListener { loadUserStats() }

        val isAdmin = SessionHelper.getRole(this) == "admin"
        setupMenuVisibility(isAdmin)
        setupAnimations(isAdmin)
        setupClicks()
        loadUserStats()
    }

    private fun getGreetingByTime(name: String): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val sapaan = when (hour) {
            in 4..10 -> "Selamat Pagi"
            in 11..14 -> "Selamat Siang"
            in 15..18 -> "Selamat Sore"
            else -> "Selamat Malam"
        }
        return "$sapaan, $name"
    }

    private fun setupMenuVisibility(isAdmin: Boolean) {
        val adminVisibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.tvAdminSection.visibility = adminVisibility
        binding.cardDashboardAdmin.visibility = adminVisibility
        binding.cardRekapan.visibility = adminVisibility
        binding.cardKelolaUser.visibility = adminVisibility
        binding.cardKelolaDropdown.visibility = adminVisibility
    }

    private fun loadUserStats() {
        binding.swipeRefresh.isRefreshing = true
        val username = SessionHelper.getUsername(this)
        val name = SessionHelper.getName(this)
        val cal = Calendar.getInstance()
        val bulan = cal.get(Calendar.MONTH) + 1
        val tahun = cal.get(Calendar.YEAR)

        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(Method.POST, urlSheet,
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        val (inputCount, editCount) = parseUserStats(json, username, name)
                        binding.tvMyInputCount.text = inputCount.toString()
                        binding.tvMyEditCount.text = editCount.toString()
                    }
                } catch (e: Exception) { e.printStackTrace() }
                binding.swipeRefresh.isRefreshing = false
            },
            { binding.swipeRefresh.isRefreshing = false }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "get_rekap"
                params["username"] = username
                params["requester_username"] = username
                params["requester_role"] = SessionHelper.getRole(this@MainActivity)
                params["bulan"] = bulan.toString()
                params["bulan_pad"] = String.format(Locale.US, "%02d", bulan)
                params["month"] = String.format(Locale.US, "%02d", bulan)
                params["tahun"] = tahun.toString()
                params["year"] = tahun.toString()
                return params
            }
        }
        queue.add(request)
    }

    private fun parseUserStats(json: JSONObject, username: String, name: String): Pair<Int, Int> {
        var inputCount = 0
        var editCount = 0
        var found = false

        val rekap = json.optJSONArray("rekap")
        if (rekap != null) {
            for (i in 0 until rekap.length()) {
                val obj = rekap.getJSONObject(i)
                val u = obj.optString("username").ifEmpty { obj.optString("user").ifEmpty { obj.optString("name") } }
                if (u.equals(username, true) || (name.isNotEmpty() && u.equals(name, true))) {
                    inputCount = obj.optInt("input", 0)
                    editCount = obj.optInt("update", 0)
                    found = true
                    break
                }
            }
        }

        if (!found) {
            val detail = json.optJSONArray("detail")
            if (detail != null) {
                val cal = Calendar.getInstance()
                val currentMonth = cal.get(Calendar.MONTH) + 1
                val currentYear = cal.get(Calendar.YEAR)

                for (i in 0 until detail.length()) {
                    val obj = detail.getJSONObject(i)
                    val u = obj.optString("username").ifEmpty { obj.optString("user").ifEmpty { obj.optString("name") } }
                    val actionType = obj.optString("action_type").uppercase()
                    val timestamp = obj.optString("timestamp")

                    if (!u.equals(username, true) && (name.isEmpty() || !u.equals(name, true))) {
                        continue
                    }

                    if (timestamp.isNotEmpty() && !isDateInMonthAndYear(timestamp, currentMonth, currentYear)) {
                        continue
                    }

                    if (actionType.contains("INPUT")) {
                        inputCount++
                    } else if (actionType.contains("UPDATE") || actionType.contains("EDIT")) {
                        editCount++
                    }
                }
            }
        }

        return Pair(inputCount, editCount)
    }

    private fun isDateInMonthAndYear(dateStr: String, targetMonth: Int, targetYear: Int): Boolean {
        return try {
            val parts = dateStr.split(" ", "T")
            val datePart = parts[0]
            val ymd = datePart.split("-", "/")
            if (ymd.size >= 3) {
                val y = if (ymd[0].length == 4) ymd[0].toInt() else ymd[2].toInt()
                val m = if (ymd[0].length == 4) ymd[1].toInt() else ymd[0].toInt()
                m == targetMonth && y == targetYear
            } else true
        } catch (_: Exception) {
            true
        }
    }

    private fun setupAnimations(isAdmin: Boolean) {
        val views = mutableListOf(binding.cardUserStats, binding.cardInputData, binding.cardEditData)
        if (isAdmin) {
            views.add(binding.cardDashboardAdmin)
            views.add(binding.cardRekapan)
            views.add(binding.cardKelolaUser)
            views.add(binding.cardKelolaDropdown)
        }

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 60f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(100L * index)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }
    }

    private fun setupClicks() {
        binding.cardInputData.setOnClickListener { animateClick(it) {
            startActivity(Intent(this, InputanActivity::class.java))
        } }
        binding.cardEditData.setOnClickListener { animateClick(it) {
            startActivity(Intent(this, EditDataActivity::class.java))
        } }
        binding.cardDashboardAdmin.setOnClickListener { animateClick(it) {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
        } }
        binding.cardRekapan.setOnClickListener { animateClick(it) {
            startActivity(Intent(this, RekapanActivity::class.java))
        } }
        binding.cardKelolaUser.setOnClickListener { animateClick(it) {
            startActivity(Intent(this, KelolaUserActivity::class.java))
        } }
        binding.cardKelolaDropdown.setOnClickListener { animateClick(it) {
            startActivity(Intent(this, KelolaDropdownActivity::class.java))
        } }

        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Keluar Sesi")
                .setMessage("Yakin ingin keluar dari aplikasi?")
                .setPositiveButton("Ya") { _, _ ->
                    SessionHelper.logout(this)
                    startActivity(Intent(this, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun animateClick(view: View, onEnd: () -> Unit) {
        view.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(80)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                onEnd()
            }
            .start()
    }
}
