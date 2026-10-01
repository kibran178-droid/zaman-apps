package com.example.zaman_apps

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.zaman_apps.databinding.ActivityAdminDashboardBinding
import com.example.zaman_apps.utils.SessionHelper
import com.example.zaman_apps.utils.VolleyHelper
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.snackbar.Snackbar
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private val urlSheet = "https://script.google.com/macros/s/AKfycby4n_8DasDqYLL-K7DQMUb_6pyF7FkuDaSWde98TDMlF0BnneJjVVwadqzuRzq-M1KguA/exec"

    private var detailDataGlobal: JSONArray? = null
    private var selectedBulan = 0
    private var selectedTahun = 0
    private val currentChartLabels = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SessionHelper.getRole(this) != "admin") {
            finish()
            return
        }

        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val cal = Calendar.getInstance()
        selectedBulan = cal.get(Calendar.MONTH) + 1
        selectedTahun = cal.get(Calendar.YEAR)

        setupUI()
        loadDashboardData()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.swipeRefresh.setColorSchemeResources(R.color.brand_primary)
        binding.swipeRefresh.setOnRefreshListener { loadDashboardData() }

        // Filter Chart (Hari Ini, 7 Hari Terakhir, Bulanan)
        val filters = listOf("Hari Ini", "7 Hari Terakhir", "Bulanan (M1 - M5)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filters)
        binding.spinnerChartFilter.adapter = adapter
        binding.spinnerChartFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                detailDataGlobal?.let { processChartData(it, position) }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.btnNavRekapan.setOnClickListener {
            startActivity(Intent(this, RekapanActivity::class.java))
        }

        binding.btnNavKelolaUser.setOnClickListener {
            startActivity(Intent(this, KelolaUserActivity::class.java))
        }

        setupBarChart()
        setupEntranceAnimations()
    }

    private fun setupEntranceAnimations() {
        val views = listOf(
            binding.tvSummaryTitle,
            binding.cardTotalSite,
            binding.cardTotalInput,
            binding.cardTotalUpdate,
            binding.cardChart,
            binding.tvActionTitle,
            binding.btnNavRekapan,
            binding.btnNavKelolaUser
        )

        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(80L * index)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }
    }

    private fun setupBarChart() {
        binding.barChartAktivitas.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setDrawValueAboveBar(true)
            setPinchZoom(false)
            setDoubleTapToZoomEnabled(false)
            setNoDataText("Memuat data analitik...")

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                setCenterAxisLabels(true)
                textColor = ContextCompat.getColor(this@AdminDashboardActivity, R.color.text_secondary)
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(this@AdminDashboardActivity, R.color.divider)
                axisMinimum = 0f
                textColor = ContextCompat.getColor(this@AdminDashboardActivity, R.color.text_secondary)
            }

            axisRight.isEnabled = false
            legend.isEnabled = true

            // Tampilkan Pop Up / Toast Angka saat Grafis di-klik
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e != null && h != null) {
                        val count = e.y.toInt()
                        val dataSetIndex = h.dataSetIndex
                        val dataSetName = if (dataSetIndex == 0) "Input" else "Update"
                        val xIdx = e.x.toInt()
                        val labelName = currentChartLabels.getOrNull(xIdx) ?: ""

                        val msg = "📊 $labelName — $dataSetName: $count Aktivitas"
                        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT)
                            .setBackgroundTint(Color.parseColor("#1F2937"))
                            .setTextColor(ContextCompat.getColor(this@AdminDashboardActivity, R.color.white))
                            .show()
                    }
                }

                override fun onNothingSelected() {}
            })
        }
    }

    private fun loadDashboardData() {
        binding.overlayLoading.visibility = View.VISIBLE
        val queue = VolleyHelper.getRequestQueue(this)

        val idRequest = StringRequest(Request.Method.GET, "$urlSheet?action=list_ids",
            { response ->
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        val ids = json.optJSONArray("ids")
                        binding.tvTotalSite.text = (ids?.length() ?: 0).toString()
                    }
                } catch (_: Exception) { }
                loadActivityStats()
            },
            { loadActivityStats() }
        )
        VolleyHelper.apply60sTimeout(idRequest)
        queue.add(idRequest)
    }

    private fun loadActivityStats() {
        val queue = VolleyHelper.getRequestQueue(this)
        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                binding.overlayLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        detailDataGlobal = json.optJSONArray("detail")
                        processChartData(detailDataGlobal, binding.spinnerChartFilter.selectedItemPosition)

                        val rekap = json.optJSONArray("rekap")
                        var totalIn = 0
                        var totalUp = 0
                        if (rekap != null) {
                            for (i in 0 until rekap.length()) {
                                val obj = rekap.getJSONObject(i)
                                totalIn += obj.optInt("input")
                                totalUp += obj.optInt("update")
                            }
                        }
                        binding.tvTotalInput.text = totalIn.toString()
                        binding.tvTotalUpdate.text = totalUp.toString()
                    }
                } catch (_: Exception) { }
            },
            {
                binding.overlayLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Connection"] = "close"
                return headers
            }

            override fun getBodyContentType(): String {
                return "application/x-www-form-urlencoded; charset=utf-8"
            }

            override fun getParamsEncoding(): String {
                return "UTF-8"
            }

            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "get_rekap"
                params["requester_role"] = SessionHelper.getRole(this@AdminDashboardActivity)
                params["bulan"] = selectedBulan.toString()
                params["tahun"] = selectedTahun.toString()
                return params
            }
        }

        VolleyHelper.apply60sTimeout(request)
        queue.add(request)
    }

    private fun processChartData(detail: JSONArray?, filterMode: Int) {
        if (detail == null) return

        val inputMap = mutableMapOf<Int, Int>()
        val updateMap = mutableMapOf<Int, Int>()
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()

        for (i in 0 until detail.length()) {
            val obj = detail.getJSONObject(i)
            val dateStr = obj.optString("timestamp").substringBefore(" ")
            val type = obj.optString("action_type").lowercase()

            try {
                val date = sdf.parse(dateStr) ?: continue
                cal.time = date
                val key = when (filterMode) {
                    0 -> 0 // Hari ini
                    1 -> cal.get(Calendar.DAY_OF_YEAR) // 7 hari terakhir
                    else -> cal.get(Calendar.WEEK_OF_MONTH) // Bulanan
                }

                if (type.contains("input")) {
                    inputMap[key] = inputMap.getOrDefault(key, 0) + 1
                } else {
                    updateMap[key] = updateMap.getOrDefault(key, 0) + 1
                }
            } catch (_: Exception) { }
        }

        val entriesInput = mutableListOf<BarEntry>()
        val entriesUpdate = mutableListOf<BarEntry>()
        currentChartLabels.clear()
        var idx = 0f

        when (filterMode) {
            0 -> { // Hari Ini
                val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                var todayInput = 0
                var todayUpdate = 0

                for (i in 0 until detail.length()) {
                    val obj = detail.getJSONObject(i)
                    val dateStr = obj.optString("timestamp").substringBefore(" ")
                    val type = obj.optString("action_type").lowercase()

                    if (dateStr == todayStr) {
                        if (type.contains("input")) todayInput++ else todayUpdate++
                    }
                }

                entriesInput.add(BarEntry(0f, todayInput.toFloat()))
                entriesUpdate.add(BarEntry(0f, todayUpdate.toFloat()))
                currentChartLabels.add("Hari Ini")
            }
            1 -> { // 7 Hari Terakhir
                for (i in 6 downTo 0) {
                    val tempCal = Calendar.getInstance()
                    tempCal.add(Calendar.DAY_OF_YEAR, -i)
                    val key = tempCal.get(Calendar.DAY_OF_YEAR)
                    entriesInput.add(BarEntry(idx, inputMap.getOrDefault(key, 0).toFloat()))
                    entriesUpdate.add(BarEntry(idx, updateMap.getOrDefault(key, 0).toFloat()))
                    currentChartLabels.add(SimpleDateFormat("dd/MM", Locale.getDefault()).format(tempCal.time))
                    idx += 1f
                }
            }
            else -> { // Mingguan (Bulan Ini)
                for (w in 1..5) {
                    entriesInput.add(BarEntry(idx, inputMap.getOrDefault(w, 0).toFloat()))
                    entriesUpdate.add(BarEntry(idx, updateMap.getOrDefault(w, 0).toFloat()))
                    currentChartLabels.add("M$w")
                    idx += 1f
                }
            }
        }

        val set1 = BarDataSet(entriesInput, "Input").apply {
            color = ContextCompat.getColor(this@AdminDashboardActivity, R.color.brand_primary)
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = ContextCompat.getColor(this@AdminDashboardActivity, R.color.text_primary)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0f) value.toInt().toString() else ""
                }
            }
        }

        val set2 = BarDataSet(entriesUpdate, "Update").apply {
            color = ContextCompat.getColor(this@AdminDashboardActivity, R.color.warning)
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = ContextCompat.getColor(this@AdminDashboardActivity, R.color.text_primary)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0f) value.toInt().toString() else ""
                }
            }
        }

        val data = BarData(set1, set2)
        val groupSpace = 0.3f
        val barSpace = 0.05f
        val barWidth = 0.3f
        data.barWidth = barWidth

        binding.barChartAktivitas.apply {
            this.data = data
            xAxis.valueFormatter = IndexAxisValueFormatter(currentChartLabels)
            xAxis.axisMinimum = 0f
            xAxis.axisMaximum = currentChartLabels.size.toFloat()
            xAxis.labelCount = currentChartLabels.size
            groupBars(0f, groupSpace, barSpace)
            animateY(800)
            invalidate()
        }
    }
}
