package com.example.zaman_apps

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.zaman_apps.databinding.ActivityMainBinding
import com.example.zaman_apps.input_form.EditDataActivity
import com.example.zaman_apps.input_form.InputanActivity
import com.example.zaman_apps.utils.GalleryAdapter
import com.example.zaman_apps.utils.MyActivityAdapter
import com.example.zaman_apps.utils.NotificationHelper
import com.example.zaman_apps.utils.ServerConfig
import com.example.zaman_apps.utils.SessionHelper
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val urlSheet = ServerConfig.SCRIPT_URL
    private var cachedDetailArrayGlobal: JSONArray? = null
    private val namaBulanList = listOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember"
    )

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
                        cachedDetailArrayGlobal = json.optJSONArray("detail")
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

    private fun getUserNameFromObj(obj: JSONObject): String {
        return obj.optString("username")
            .ifEmpty { obj.optString("user") }
            .ifEmpty { obj.optString("name") }
            .ifEmpty { obj.optString("nama_user") }
            .ifEmpty { obj.optString("nama_teknisi") }
            .ifEmpty { obj.optString("teknisi") }
            .ifEmpty { obj.optString("uploader") }
            .ifEmpty { obj.optString("created_by") }
            .ifEmpty { obj.optString("input_by") }
            .ifEmpty { obj.optString("edited_by") }
    }

    private fun isItemBelongsToUser(obj: JSONObject, username: String, name: String): Boolean {
        val u = getUserNameFromObj(obj)
        if (u.isEmpty()) return false
        return u.equals(username, true) || (name.isNotEmpty() && u.equals(name, true))
    }

    private fun parseUserStats(json: JSONObject, username: String, name: String): Pair<Int, Int> {
        var inputCount = 0
        var editCount = 0
        var found = false

        val rekap = json.optJSONArray("rekap")
        if (rekap != null) {
            for (i in 0 until rekap.length()) {
                val obj = rekap.getJSONObject(i)
                if (isItemBelongsToUser(obj, username, name)) {
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
                    if (!isItemBelongsToUser(obj, username, name)) {
                        continue
                    }

                    val timestamp = obj.optString("timestamp").ifEmpty { obj.optString("tanggal") }
                    if (timestamp.isNotEmpty() && !isDateInMonthAndYear(timestamp, currentMonth, currentYear)) {
                        continue
                    }

                    val actionType = obj.optString("action_type").ifEmpty { obj.optString("tipe_aksi") }.uppercase()
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
        if (dateStr.isEmpty()) return true
        return try {
            val datePart = dateStr.split(" ", "T")[0]
            val parts = datePart.split("-", "/")
            if (parts.size >= 3) {
                val (year, month) = if (parts[0].length == 4) {
                    Pair(parts[0].toInt(), parts[1].toInt())
                } else if (parts[2].length == 4) {
                    Pair(parts[2].toInt(), parts[1].toInt())
                } else {
                    Pair(2000 + parts[2].toInt(), parts[1].toInt())
                }
                month == targetMonth && year == targetYear
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
        binding.cardUserStats.setOnClickListener { animateClick(it) {
            showMyActivityHistoryBottomSheet()
        } }

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

    private fun showMyActivityHistoryBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_my_activity_history, null)
        bottomSheetDialog.setContentView(view)

        val btnClose = view.findViewById<MaterialButton>(R.id.btnCloseHistory)
        val spinnerMonth = view.findViewById<Spinner>(R.id.spinnerHistoryMonth)
        val spinnerYear = view.findViewById<Spinner>(R.id.spinnerHistoryYear)
        val tvSummary = view.findViewById<TextView>(R.id.tvHistorySummaryCount)
        val tvEmpty = view.findViewById<TextView>(R.id.tvHistoryEmpty)
        val pbLoading = view.findViewById<ProgressBar>(R.id.pbHistoryLoading)
        val rvHistory = view.findViewById<RecyclerView>(R.id.rvMyActivityHistory)

        rvHistory.layoutManager = LinearLayoutManager(this)

        val cal = Calendar.getInstance()
        var selectedBulan = cal.get(Calendar.MONTH) + 1
        var selectedTahun = cal.get(Calendar.YEAR)

        val adapterBulan = ArrayAdapter(this, R.layout.item_spinner_selected, namaBulanList).apply {
            setDropDownViewResource(R.layout.item_spinner_formal_dropdown)
        }
        spinnerMonth.adapter = adapterBulan
        spinnerMonth.setSelection(selectedBulan - 1)

        val yearList = (2022..selectedTahun + 1).map { it.toString() }.reversed()
        val adapterTahun = ArrayAdapter(this, R.layout.item_spinner_selected, yearList).apply {
            setDropDownViewResource(R.layout.item_spinner_formal_dropdown)
        }
        spinnerYear.adapter = adapterTahun
        val posYear = yearList.indexOf(selectedTahun.toString())
        if (posYear >= 0) spinnerYear.setSelection(posYear)

        val username = SessionHelper.getUsername(this)
        val name = SessionHelper.getName(this)

        fun fetchAndRenderHistoryData(m: Int, y: Int) {
            pbLoading.visibility = View.VISIBLE
            rvHistory.visibility = View.GONE
            tvEmpty.visibility = View.GONE

            val namaBulan = namaBulanList[m - 1]
            tvSummary.text = "Memuat data $namaBulan $y..."

            val queue = Volley.newRequestQueue(this)
            val request = object : StringRequest(Method.POST, urlSheet,
                { response ->
                    try {
                        val json = JSONObject(response)
                        val detail = json.optJSONArray("detail")
                        val filteredList = mutableListOf<JSONObject>()
                        var inputCount = 0
                        var editCount = 0

                        if (detail != null) {
                            for (i in 0 until detail.length()) {
                                val obj = detail.getJSONObject(i)
                                if (!isItemBelongsToUser(obj, username, name)) {
                                    continue
                                }

                                val timestamp = obj.optString("timestamp").ifEmpty { obj.optString("tanggal") }
                                if (timestamp.isNotEmpty() && !isDateInMonthAndYear(timestamp, m, y)) {
                                    continue
                                }

                                filteredList.add(obj)
                                val actionType = obj.optString("action_type").ifEmpty { obj.optString("tipe_aksi") }.uppercase()
                                if (actionType.contains("INPUT")) {
                                    inputCount++
                                } else if (actionType.contains("UPDATE") || actionType.contains("EDIT")) {
                                    editCount++
                                }
                            }
                        }

                        tvSummary.text = "Bulan $namaBulan $y: $inputCount Input • $editCount Update"
                        pbLoading.visibility = View.GONE

                        if (filteredList.isEmpty()) {
                            tvEmpty.visibility = View.VISIBLE
                            tvEmpty.text = "Belum ada riwayat aktivitas pada bulan ini."
                            rvHistory.visibility = View.GONE
                        } else {
                            tvEmpty.visibility = View.GONE
                            rvHistory.visibility = View.VISIBLE
                            rvHistory.adapter = MyActivityAdapter(filteredList)
                        }

                    } catch (e: Exception) {
                        e.printStackTrace()
                        pbLoading.visibility = View.GONE
                        tvEmpty.visibility = View.VISIBLE
                        tvEmpty.text = "Gagal memuat data riwayat."
                    }
                },
                {
                    pbLoading.visibility = View.GONE
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "Gagal terhubung ke server."
                }
            ) {
                override fun getParams(): MutableMap<String, String> {
                    val params = HashMap<String, String>()
                    params["action"] = "get_rekap"
                    params["username"] = username
                    params["requester_username"] = username
                    params["requester_role"] = SessionHelper.getRole(this@MainActivity)
                    params["bulan"] = m.toString()
                    params["bulan_pad"] = String.format(Locale.US, "%02d", m)
                    params["month"] = String.format(Locale.US, "%02d", m)
                    params["tahun"] = y.toString()
                    params["year"] = y.toString()
                    return params
                }
            }
            queue.add(request)
        }

        var isInitialSetup = true

        spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newBulan = position + 1
                if (selectedBulan != newBulan || isInitialSetup) {
                    selectedBulan = newBulan
                    fetchAndRenderHistoryData(selectedBulan, selectedTahun)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerYear.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newTahun = (spinnerYear.selectedItem as String).toInt()
                if (selectedTahun != newTahun || isInitialSetup) {
                    selectedTahun = newTahun
                    fetchAndRenderHistoryData(selectedBulan, selectedTahun)
                }
                isInitialSetup = false
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnClose.setOnClickListener { bottomSheetDialog.dismiss() }

        bottomSheetDialog.show()
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
