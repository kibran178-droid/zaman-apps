package com.example.zaman_apps.input_form

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.TimeoutError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.example.zaman_apps.R
import com.example.zaman_apps.databinding.ActivityRingkasanBinding
import com.example.zaman_apps.databinding.DialogEditSectionBinding
import com.example.zaman_apps.databinding.ItemCardRingkasanBinding
import com.example.zaman_apps.utils.DropdownOptionsHelper
import com.example.zaman_apps.utils.ImageCaptureHelper
import com.example.zaman_apps.utils.ImageHelper
import com.example.zaman_apps.utils.NotificationHelper
import com.example.zaman_apps.utils.SessionHelper
import com.example.zaman_apps.utils.VolleyHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class RingkasanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRingkasanBinding
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var data: SiteData
    private var pendingAction: String = "input"
    private var isSaving = false

    private val photoUris = mutableListOf<String>()
    private val imageBase64Caches = mutableMapOf<Int, String>()

    private var currentPopupDialog: Dialog? = null
    private var pendingPhotoIndexPopup = -1
    private var pendingCameraUriRingkasan: Uri? = null

    private val pickPhotoRingkasanLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (pendingPhotoIndexPopup in 0 until photoUris.size) {
                photoUris[pendingPhotoIndexPopup] = it.toString()
                imageBase64Caches.remove(pendingPhotoIndexPopup)
                binding.tvBadgeTotalFoto.text = "${photoUris.size} Foto"
                showPopupLihatFoto()
                showSnackbar("Foto #${pendingPhotoIndexPopup + 1} berhasil diperbarui")
            }
        }
    }

    private val addMultiplePhotosRingkasanLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val spaceLeft = 10 - photoUris.size
            if (spaceLeft > 0) {
                val toAdd = uris.take(spaceLeft)
                toAdd.forEach { uri ->
                    photoUris.add(uri.toString())
                }
                binding.tvBadgeTotalFoto.text = "${photoUris.size} Foto"
                showPopupLihatFoto()
                tampilkanRingkasan()
                showSnackbar("Berhasil menambahkan ${toAdd.size} foto")
            } else {
                showSnackbar("Maksimal 10 foto sudah tercapai", isError = true)
            }
        }
    }

    private val takePhotoRingkasanLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUriRingkasan != null) {
            if (pendingPhotoIndexPopup in 0 until photoUris.size) {
                photoUris[pendingPhotoIndexPopup] = pendingCameraUriRingkasan.toString()
                imageBase64Caches.remove(pendingPhotoIndexPopup)
                binding.tvBadgeTotalFoto.text = "${photoUris.size} Foto"
                showPopupLihatFoto()
                showSnackbar("Foto #${pendingPhotoIndexPopup + 1} berhasil diperbarui")
            }
        }
    }

    private val addCameraPhotoRingkasanLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUriRingkasan != null) {
            if (photoUris.size < 10) {
                photoUris.add(pendingCameraUriRingkasan.toString())
                binding.tvBadgeTotalFoto.text = "${photoUris.size} Foto"
                showPopupLihatFoto()
                tampilkanRingkasan()
                showSnackbar("Foto baru berhasil ditambahkan")
            }
        }
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            bukaKameraRingkasan()
        } else {
            showSnackbar("Izin kamera ditolak", isError = true)
        }
    }

    private val urlSheet = "https://script.google.com/macros/s/AKfycby4n_8DasDqYLL-K7DQMUb_6pyF7FkuDaSWde98TDMlF0BnneJjVVwadqzuRzq-M1KguA/exec"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()
        requestNotificationPermission()

        binding = ActivityRingkasanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Ringkasan Data"
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_back)
        }

        binding.toolbar.setNavigationOnClickListener {
            handleBackNavigation()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        data = intent.getParcelableExtra("SITE_DATA") ?: SiteData()

        photoUris.clear()
        photoUris.addAll(data.imageUris)

        binding.tvBadgeTotalFoto.text = "${photoUris.size} Foto"
        binding.btnLihatFoto.setOnClickListener {
            showPopupLihatFoto()
        }

        tampilkanRingkasan()

        binding.btnSimpanFinal.setOnClickListener { konfirmasiSubmit() }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun bukaKameraRingkasan() {
        val uri = ImageCaptureHelper.buatUriFotoBaru(this)
        pendingCameraUriRingkasan = uri
        if (pendingPhotoIndexPopup == -1) {
            addCameraPhotoRingkasanLauncher.launch(uri)
        } else {
            takePhotoRingkasanLauncher.launch(uri)
        }
    }

    private fun showPopupLihatFoto() {
        currentPopupDialog?.dismiss()

        val dialog = Dialog(this, R.style.DialogEditTheme)
        currentPopupDialog = dialog

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(32, 32, 32, 32)
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.section_bg))

        val tvTitle = TextView(this)
        tvTitle.text = "Dokumentasi Foto Site (${photoUris.size} Foto)"
        tvTitle.textSize = 18f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        tvTitle.setPadding(0, 0, 0, 16)
        root.addView(tvTitle)

        val btnTambahFotoPop = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
        btnTambahFotoPop.text = "Tambah Foto Dokumentasi Baru"
        btnTambahFotoPop.textSize = 14f
        btnTambahFotoPop.setTypeface(null, Typeface.BOLD)
        btnTambahFotoPop.setTextColor(ContextCompat.getColor(this, R.color.white))
        btnTambahFotoPop.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_primary)
        btnTambahFotoPop.cornerRadius = (16 * resources.displayMetrics.density).toInt()
        val btnAddParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (50 * resources.displayMetrics.density).toInt()
        ).apply { bottomMargin = 16 }
        btnTambahFotoPop.layoutParams = btnAddParams
        btnTambahFotoPop.setOnClickListener {
            if (photoUris.size >= 10) {
                showSnackbar("Maksimal 10 foto dokumentasi sudah tercapai", isError = true)
                return@setOnClickListener
            }
            pendingPhotoIndexPopup = -1
            val opsi = arrayOf("📷 Ambil dari Kamera", "🖼️ Pilih dari Galeri")
            AlertDialog.Builder(this)
                .setTitle("Tambah Foto Dokumentasi")
                .setItems(opsi) { _, optIdx ->
                    when (optIdx) {
                        0 -> {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                bukaKameraRingkasan()
                            } else {
                                requestCameraPermission.launch(Manifest.permission.CAMERA)
                            }
                        }
                        1 -> addMultiplePhotosRingkasanLauncher.launch("image/*")
                    }
                }
                .show()
        }
        root.addView(btnTambahFotoPop)

        val scrollView = NestedScrollView(this)
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
        scrollView.layoutParams = scrollParams

        val listContainer = LinearLayout(this)
        listContainer.orientation = LinearLayout.VERTICAL

        if (photoUris.isEmpty()) {
            val tvEmpty = TextView(this)
            tvEmpty.text = "Belum ada foto terpilih."
            tvEmpty.setPadding(0, 48, 0, 48)
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            listContainer.addView(tvEmpty)
        } else {
            photoUris.forEachIndexed { index, uriStr ->
                val card = MaterialCardView(this)
                card.radius = 16f
                card.cardElevation = 4f
                val cardParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 20 }
                card.layoutParams = cardParams

                val cardContent = LinearLayout(this)
                cardContent.orientation = LinearLayout.VERTICAL
                cardContent.setPadding(20, 20, 20, 20)

                val tvLabel = TextView(this)
                tvLabel.text = "Foto Dokumentasi #${index + 1}"
                tvLabel.textSize = 14f
                tvLabel.setTypeface(null, Typeface.BOLD)
                tvLabel.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                tvLabel.setPadding(0, 0, 0, 12)
                cardContent.addView(tvLabel)

                val iv = ImageView(this)
                val imgParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 450
                )
                iv.layoutParams = imgParams
                iv.scaleType = ImageView.ScaleType.CENTER_CROP

                val progressSpinner = CircularProgressDrawable(this).apply {
                    strokeWidth = 6f
                    centerRadius = 36f
                    setColorSchemeColors(ContextCompat.getColor(this@RingkasanActivity, R.color.brand_primary))
                    start()
                }

                if (uriStr.startsWith("http")) {
                    val fileIdMatch = Regex("/d/([a-zA-Z0-9_-]+)").find(uriStr)
                    val directUrl = if (fileIdMatch != null) {
                        "https://drive.google.com/uc?export=view&id=${fileIdMatch.groupValues[1]}"
                    } else {
                        uriStr
                    }
                    Glide.with(this)
                        .load(directUrl)
                        .placeholder(progressSpinner)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(iv)
                } else {
                    try {
                        val uri = Uri.parse(uriStr)
                        val thumb = ImageHelper.decodeThumbnailFromUri(this, uri, 500, 500)
                        if (thumb != null) {
                            iv.setImageBitmap(thumb)
                        } else {
                            iv.setImageURI(uri)
                        }
                    } catch (_: Exception) {
                        iv.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
                cardContent.addView(iv)

                val rowBtn = LinearLayout(this)
                rowBtn.orientation = LinearLayout.HORIZONTAL
                rowBtn.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 16 }

                val btnGanti = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle)
                btnGanti.text = "✏️ Ganti Foto"
                btnGanti.textSize = 12f
                btnGanti.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
                btnGanti.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                btnGanti.setOnClickListener {
                    pendingPhotoIndexPopup = index
                    val opsi = arrayOf("📷 Ambil dari Kamera", "🖼️ Pilih dari Galeri")
                    AlertDialog.Builder(this)
                        .setTitle("Ganti Foto Dokumentasi #${index + 1}")
                        .setItems(opsi) { _, optIdx ->
                            when (optIdx) {
                                0 -> {
                                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        bukaKameraRingkasan()
                                    } else {
                                        requestCameraPermission.launch(Manifest.permission.CAMERA)
                                    }
                                }
                                1 -> pickPhotoRingkasanLauncher.launch("image/*")
                            }
                        }
                        .show()
                }
                rowBtn.addView(btnGanti)

                val btnHapus = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle)
                btnHapus.text = "🗑️ Hapus"
                btnHapus.textSize = 12f
                btnHapus.setTextColor(ContextCompat.getColor(this, R.color.error))
                btnHapus.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                btnHapus.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Hapus Foto Dokumentasi #${index + 1}?")
                        .setMessage("Yakin ingin menghapus foto dokumentasi ke-${index + 1} ini?")
                        .setPositiveButton("Hapus") { _, _ ->
                            if (index in 0 until photoUris.size) {
                                photoUris.removeAt(index)
                                imageBase64Caches.remove(index)
                                binding.tvBadgeTotalFoto.text = "${photoUris.size} Foto"
                                showSnackbar("Foto #${index + 1} berhasil dihapus")
                                showPopupLihatFoto()
                                tampilkanRingkasan()
                            }
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
                rowBtn.addView(btnHapus)

                cardContent.addView(rowBtn)
                card.addView(cardContent)
                listContainer.addView(card)
            }
        }

        scrollView.addView(listContainer)
        root.addView(scrollView)

        val btnClose = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
        btnClose.text = "Tutup"
        btnClose.textSize = 14f
        btnClose.setTypeface(null, Typeface.BOLD)
        btnClose.setTextColor(ContextCompat.getColor(this, R.color.white))
        btnClose.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_primary)
        btnClose.cornerRadius = (16 * resources.displayMetrics.density).toInt()
        btnClose.setOnClickListener { dialog.dismiss() }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (50 * resources.displayMetrics.density).toInt()
        ).apply { topMargin = 16 }
        btnClose.layoutParams = btnParams
        root.addView(btnClose)

        dialog.setContentView(root)
        dialog.setCancelable(true)
        dialog.show()

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )
    }

    private fun handleBackNavigation() {
        if (isSaving) {
            showSnackbar("Mohon tunggu proses penyimpanan selesai", isError = true)
            return
        }
        prepareReturnResult()
        finish()
    }

    private fun prepareReturnResult() {
        data.imageUris.clear()
        data.imageUris.addAll(photoUris)
        val returnIntent = Intent().apply {
            putExtra("UPDATED_SITE_DATA", data)
        }
        setResult(RESULT_OK, returnIntent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleBackNavigation()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        handleBackNavigation()
        return true
    }

    override fun onBackPressed() {
        handleBackNavigation()
        super.onBackPressed()
    }

    private fun showLoadingOverlay(message: String = "Menyimpan data...") {
        try {
            isSaving = true

            supportActionBar?.setDisplayHomeAsUpEnabled(false)
            supportActionBar?.setDisplayShowHomeEnabled(false)
            binding.toolbar.setNavigationOnClickListener(null)

            binding.btnSimpanFinal.isEnabled = false
            binding.btnSimpanFinal.alpha = 0.5f

            binding.overlayLoading.visibility = View.VISIBLE
            val textView = binding.overlayLoading.findViewById<TextView>(R.id.tvLoadingMessage)
            textView?.text = message

            binding.root.findViewById<NestedScrollView>(R.id.nestedScrollView)?.isEnabled = false

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideLoadingOverlay() {
        try {
            isSaving = false

            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            binding.toolbar.setNavigationOnClickListener {
                if (!isSaving) {
                    onBackPressedDispatcher.onBackPressed()
                }
            }

            binding.btnSimpanFinal.isEnabled = true
            binding.btnSimpanFinal.alpha = 1.0f

            binding.overlayLoading.visibility = View.GONE

            binding.root.findViewById<NestedScrollView>(R.id.nestedScrollView)?.isEnabled = false

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun valOrDash(v: String?): String = if (v.isNullOrEmpty()) "-" else v

    private fun tampilkanRingkasan() {
        binding.containerCards.removeAllViews()

        tambahCard(
            judul = "Informasi Site",
            isi = """
                Site ID: ${valOrDash(data.siteId)}
                Site Name: ${valOrDash(data.siteName)}
                ID Pelanggan: ${valOrDash(data.idPelanggan)}
                MCB kWh: ${valOrDash(data.mcbKwh)}
                Daya: ${valOrDash(data.daya)}
                Jumlah Foto: ${photoUris.size} Foto Terlampir
            """.trimIndent(),
            section = "informasi_site"
        )

        tambahCard(
            judul = "Status Perangkat",
            isi = """
                Mains Fail: ${valOrDash(data.mainsFail)}
                Recty Fail: ${valOrDash(data.rectyFail)}
                Stolen: ${valOrDash(data.stolen)}
                Low Batt: ${valOrDash(data.lowBatt)}
                Eng Run: ${valOrDash(data.engRun)}
            """.trimIndent(),
            section = "status_perangkat"
        )

        tambahCard(
            judul = "Tegangan & Arus",
            isi = """
                Tegangan R: ${valOrDash(data.rV)} V | S: ${valOrDash(data.sV)} V | T: ${valOrDash(data.tV)} V
                Arus R: ${valOrDash(data.rA)} A | S: ${valOrDash(data.sA)} A | T: ${valOrDash(data.tA)} A
            """.trimIndent(),
            section = "tegangan_arus"
        )

        var gensetText = """
            Genset 1: ${valOrDash(data.merkGenset1)}
            Kapasitas: ${valOrDash(data.capacityGenset1)} kVA
            Status: ${valOrDash(data.statusGenset1)}
            Catatan: ${valOrDash(data.remarkGenset1)}
        """.trimIndent()

        if (data.adaGenset2) {
            gensetText += """
                
                --------------------------------------------------------
                Genset 2: ${valOrDash(data.merkGenset2)}
                Kapasitas: ${valOrDash(data.capacityGenset2)} kVA
                Status: ${valOrDash(data.statusGenset2)}
                Catatan: ${valOrDash(data.remarkGenset2)}
            """.trimIndent()
        }

        tambahCard(
            judul = "Data Genset",
            isi = gensetText,
            section = "genset"
        )

        fun buildRectiText(
            type: String, modul: String, modulRusak: String,
            jenisBat: String? = null, merkBat: String? = null,
            jmlBat: String, statusBat: String, mcbBat: String,
            arester: String, voltage: String, load: String,
            perangkat: String, remark: String? = null,
            statusNotAktif: Boolean = false, jmlBankNotAktif: String = ""
        ): String {
            val sb = StringBuilder()
            sb.append("Type: ${valOrDash(type)}\n")
            sb.append("Modul: ${valOrDash(modul)} (Rusak: ${valOrDash(modulRusak)})\n")
            if (!jenisBat.isNullOrEmpty()) sb.append("Jenis Baterai: ${valOrDash(jenisBat)}\n")
            if (!merkBat.isNullOrEmpty()) sb.append("Merk Baterai: ${valOrDash(merkBat)}\n")
            sb.append("Jumlah Battery: ${valOrDash(jmlBat)}\n")
            sb.append("Battery Status: ${valOrDash(statusBat)}\n")
            if (statusNotAktif || jmlBankNotAktif.isNotBlank()) {
                sb.append("Status Battery Not Aktif: Ya (${valOrDash(jmlBankNotAktif)} Bank)\n")
            }
            sb.append("MCB Battery Recty: ${valOrDash(mcbBat)}\n")
            sb.append("Kondisi Arester: ${valOrDash(arester)}\n")
            sb.append("Voltage: ${valOrDash(voltage)} V | Load: ${valOrDash(load)} A\n")
            sb.append("Perangkat: ${valOrDash(perangkat)}")
            if (remark != null) sb.append("\nCatatan: ${valOrDash(remark)}")
            return sb.toString()
        }

        tambahCard(
            judul = "Rectifier Unit 1",
            isi = buildRectiText(
                data.typeRectifier1, data.jumlahModul1, data.jumlahModulRusak1,
                data.jenisBaterai1, data.merkBaterai1, data.jumlahBatteryP1, data.batteryRecti1,
                data.mcbBatteryRecti1, data.kondisiArester1, data.busVoltage1, data.loadA1,
                data.perangkatTerhubung1, data.remark1, data.statusBatteryNotAktif1, data.jumlahBankNotAktif1
            ),
            section = "rectifier1"
        )

        if (data.adaRecti2) {
            tambahCard(
                judul = "Rectifier Unit 2",
                isi = buildRectiText(
                    data.typeRectifier2, data.jumlahModul2, data.jumlahModulRusak2,
                    data.jenisBaterai2, data.merkBaterai2, data.jumlahBatteryP2, data.batteryRecti2,
                    data.mcbBatteryRecti2, data.kondisiArester2, data.busVoltage2, data.loadA2,
                    data.perangkatTerhubung2, data.remark2, data.statusBatteryNotAktif2, data.jumlahBankNotAktif2
                ),
                section = "rectifier2"
            )
        } else {
            tambahCardTombol("➕ Tambah Rectifier Unit 2") {
                data.adaRecti2 = true
                showEditDialog("rectifier2")
            }
        }

        if (data.adaRecti3) {
            tambahCard(
                judul = "Rectifier Unit 3",
                isi = buildRectiText(
                    data.typeRectifier3, data.jumlahModul3, data.jumlahModulRusak3,
                    data.jenisBaterai3, data.merkBaterai3, data.jumlahBatteryP3, data.batteryRecti3,
                    data.mcbBatteryRecti3, data.kondisiArester3, data.busVoltage3, data.loadA3,
                    data.perangkatTerhubung3, null, data.statusBatteryNotAktif3, data.jumlahBankNotAktif3
                ),
                section = "rectifier3"
            )
        } else if (data.adaRecti2) {
            tambahCardTombol("➕ Tambah Rectifier Unit 3") {
                data.adaRecti3 = true
                showEditDialog("rectifier3")
            }
        }

        if (data.adaRecti4) {
            tambahCard(
                judul = "Rectifier Unit 4",
                isi = buildRectiText(
                    data.typeRectifier4, data.jumlahModul4, data.jumlahModulRusak4,
                    data.jenisBaterai4, data.merkBaterai4, data.jumlahBatteryP4, data.batteryRecti4,
                    data.mcbBatteryRecti4, data.kondisiArester4, data.busVoltage4, data.loadA4,
                    data.perangkatTerhubung4, null, data.statusBatteryNotAktif4, data.jumlahBankNotAktif4
                ),
                section = "rectifier4"
            )
        } else if (data.adaRecti3) {
            tambahCardTombol("➕ Tambah Rectifier Unit 4") {
                data.adaRecti4 = true
                showEditDialog("rectifier4")
            }
        }

        if (data.adaRecti5) {
            tambahCard(
                judul = "Rectifier Unit 5",
                isi = buildRectiText(
                    data.typeRectifier5, data.jumlahModul5, data.jumlahModulRusak5,
                    data.jenisBaterai5, data.merkBaterai5, data.jumlahBatteryP5, data.batteryRecti5,
                    data.mcbBatteryRecti5, data.kondisiArester5, data.busVoltage5, data.loadA5,
                    data.perangkatTerhubung5, null, data.statusBatteryNotAktif5, data.jumlahBankNotAktif5
                ),
                section = "rectifier5"
            )
        } else if (data.adaRecti4) {
            tambahCardTombol("➕ Tambah Rectifier Unit 5") {
                data.adaRecti5 = true
                showEditDialog("rectifier5")
            }
        }

        if (data.adaRecti6) {
            tambahCard(
                judul = "Rectifier Unit 6",
                isi = buildRectiText(
                    data.typeRectifier6, data.jumlahModul6, data.jumlahModulRusak6,
                    data.jenisBaterai6, data.merkBaterai6, data.jumlahBatteryP6, data.batteryRecti6,
                    data.mcbBatteryRecti6, data.kondisiArester6, data.busVoltage6, data.loadA6,
                    data.perangkatTerhubung6, null, data.statusBatteryNotAktif6, data.jumlahBankNotAktif6
                ),
                section = "rectifier6"
            )
        } else if (data.adaRecti5) {
            tambahCardTombol("➕ Tambah Rectifier Unit 6") {
                data.adaRecti6 = true
                showEditDialog("rectifier6")
            }
        }

        if (data.adaRecti7) {
            tambahCard(
                judul = "Rectifier Unit 7",
                isi = buildRectiText(
                    data.typeRectifier7, data.jumlahModul7, data.jumlahModulRusak7,
                    data.jenisBaterai7, data.merkBaterai7, data.jumlahBatteryP7, data.batteryRecti7,
                    data.mcbBatteryRecti7, data.kondisiArester7, data.busVoltage7, data.loadA7,
                    data.perangkatTerhubung7, null, data.statusBatteryNotAktif7, data.jumlahBankNotAktif7
                ),
                section = "rectifier7"
            )
        } else if (data.adaRecti6) {
            tambahCardTombol("➕ Tambah Rectifier Unit 7") {
                data.adaRecti7 = true
                showEditDialog("rectifier7")
            }
        }
    }

    private fun tambahCardTombol(textTombol: String, onClick: () -> Unit) {
        val card = MaterialCardView(this)
        card.radius = (16 * resources.displayMetrics.density)
        card.cardElevation = (2 * resources.displayMetrics.density)
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_bg))
        card.strokeWidth = 1
        card.strokeColor = ContextCompat.getColor(this, R.color.brand_primary)
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12; bottomMargin = 12 }
        card.layoutParams = cardParams

        val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle)
        btn.text = textTombol
        btn.textSize = 14f
        btn.setTypeface(null, Typeface.BOLD)
        btn.setTextColor(ContextCompat.getColor(this, R.color.white))
        btn.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_primary)
        btn.cornerRadius = (14 * resources.displayMetrics.density).toInt()
        btn.setOnClickListener { onClick() }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (50 * resources.displayMetrics.density).toInt()
        ).apply { setMargins(20, 20, 20, 20) }
        btn.layoutParams = btnParams

        card.addView(btn)
        binding.containerCards.addView(card)
    }

    private fun tambahCard(judul: String, isi: String, section: String) {
        val cardBinding = ItemCardRingkasanBinding.inflate(layoutInflater)
        cardBinding.tvJudulCard.text = judul

        renderKeyValuesInCard(cardBinding.containerContentCard, isi)

        val iconRes = when (section) {
            "informasi_site" -> android.R.drawable.ic_menu_mylocation
            "status_perangkat" -> android.R.drawable.ic_dialog_alert
            "tegangan_arus" -> android.R.drawable.ic_menu_compass
            "genset" -> android.R.drawable.ic_menu_manage
            "rectifier1", "rectifier2", "rectifier3", "rectifier4", "rectifier5", "rectifier6", "rectifier7" -> android.R.drawable.ic_menu_preferences
            else -> android.R.drawable.ic_menu_info_details
        }
        cardBinding.ivIconCard.setImageResource(iconRes)

        cardBinding.btnEditCard.setOnClickListener {
            showEditDialog(section)
        }

        val cardView = cardBinding.root
        cardView.alpha = 0f
        cardView.translationY = 60f
        cardView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay((binding.containerCards.childCount * 50).toLong())
            .setInterpolator(OvershootInterpolator(1.0f))
            .start()

        binding.containerCards.addView(cardView)
    }

    private fun renderKeyValuesInCard(container: LinearLayout, rawText: String) {
        container.removeAllViews()
        val lines = rawText.split("\n")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("-")) {
                val divider = View(this)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (1 * resources.displayMetrics.density).toInt()
                ).apply { topMargin = 10; bottomMargin = 10 }
                divider.layoutParams = params
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider))
                container.addView(divider)
                continue
            }

            val colonIdx = trimmed.indexOf(":")
            if (colonIdx > 0) {
                val key = trimmed.substring(0, colonIdx).trim()
                val value = trimmed.substring(colonIdx + 1).trim()

                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                val rowParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4; bottomMargin = 4 }
                row.layoutParams = rowParams

                val tvKey = TextView(this)
                tvKey.text = key
                tvKey.textSize = 13f
                tvKey.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                tvKey.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f)

                val tvVal = TextView(this)
                tvVal.text = value
                tvVal.textSize = 14f
                tvVal.setTypeface(null, Typeface.BOLD)
                tvVal.gravity = Gravity.END
                tvVal.setPadding(8, 0, 0, 0)

                if (value.equals("Normal", true) || value.equals("OK", true) || value.equals("Balting", true) || value.contains("Aktif", true)) {
                    tvVal.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
                } else if (value.contains("Fail", true) || value.contains("Rusak", true) || value.contains("Alarm", true) || value.equals("NOK", true)) {
                    tvVal.setTextColor(ContextCompat.getColor(this, R.color.error))
                } else {
                    tvVal.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                }
                tvVal.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)

                row.addView(tvKey)
                row.addView(tvVal)
                container.addView(row)
            } else {
                val tvSub = TextView(this)
                tvSub.text = trimmed
                tvSub.textSize = 14f
                tvSub.setTypeface(null, Typeface.BOLD)
                tvSub.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
                tvSub.setPadding(0, 8, 0, 4)
                container.addView(tvSub)
            }
        }
    }

    private fun setupSpinnerAdapter(spinner: Spinner?, categoryKey: String) {
        if (spinner == null) return
        val options = DropdownOptionsHelper.getOptions(this, categoryKey)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        spinner.adapter = adapter
    }

    private fun setupSpinnerAdapterFromRes(spinner: Spinner?, arrayResId: Int) {
        if (spinner == null) return
        val adapter = ArrayAdapter.createFromResource(this, arrayResId, android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun setupCascadingBateraiListeners(
        spJenis: Spinner?,
        spMerk: Spinner?,
        spJml: Spinner?
    ) {
        if (spJenis == null || spMerk == null || spJml == null) return
        spJenis.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val jenis = parent?.getItemAtPosition(position)?.toString() ?: ""
                val currentMerk = spMerk.selectedItem?.toString() ?: ""
                val currentJml = spJml.selectedItem?.toString() ?: ""

                val merkOpts = DropdownOptionsHelper.getMerkBateraiFiltered(jenis)
                val jmlOpts = DropdownOptionsHelper.getJumlahBatteryFiltered(jenis)

                spMerk.adapter = ArrayAdapter(this@RingkasanActivity, android.R.layout.simple_spinner_dropdown_item, merkOpts)
                val merkIdx = merkOpts.indexOfFirst { it.equals(currentMerk, ignoreCase = true) }
                if (merkIdx >= 0) spMerk.setSelection(merkIdx)

                spJml.adapter = ArrayAdapter(this@RingkasanActivity, android.R.layout.simple_spinner_dropdown_item, jmlOpts)
                val jmlIdx = jmlOpts.indexOfFirst { it.equals(currentJml, ignoreCase = true) }
                if (jmlIdx >= 0) spJml.setSelection(jmlIdx)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showEditDialog(section: String) {
        try {
            val dialogBinding = DialogEditSectionBinding.inflate(LayoutInflater.from(this))
            val dialog = Dialog(this, R.style.DialogEditTheme)
            dialog.setContentView(dialogBinding.root)
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)

            dialogBinding.toolbarDialog.setNavigationOnClickListener {
                dialog.dismiss()
            }

            val title = when (section) {
                "informasi_site" -> "Edit Informasi Site"
                "status_perangkat" -> "Edit Status Perangkat"
                "tegangan_arus" -> "Edit Tegangan & Arus"
                "genset" -> "Edit Data Genset"
                "rectifier1" -> "Edit Rectifier Unit 1"
                "rectifier2" -> "Edit Rectifier Unit 2"
                "rectifier3" -> "Edit Rectifier Unit 3"
                "rectifier4" -> "Edit Rectifier Unit 4"
                "rectifier5" -> "Edit Rectifier Unit 5"
                "rectifier6" -> "Edit Rectifier Unit 6"
                "rectifier7" -> "Edit Rectifier Unit 7"
                else -> "Edit Data"
            }
            dialogBinding.dialogTitle.text = title

            val layoutId = when (section) {
                "informasi_site" -> R.layout.dialog_edit_informasi_site
                "status_perangkat" -> R.layout.dialog_edit_status_perangkat
                "tegangan_arus" -> R.layout.dialog_edit_tegangan_arus
                "genset" -> R.layout.dialog_edit_genset
                "rectifier1" -> R.layout.dialog_edit_rectifier1
                "rectifier2" -> R.layout.dialog_edit_rectifier2
                "rectifier3" -> R.layout.dialog_edit_rectifier3
                "rectifier4" -> R.layout.dialog_edit_rectifier4
                "rectifier5" -> R.layout.dialog_edit_rectifier5
                "rectifier6" -> R.layout.dialog_edit_rectifier6
                "rectifier7" -> R.layout.dialog_edit_rectifier7
                else -> 0
            }

            if (layoutId != 0) {
                val customView = LayoutInflater.from(this).inflate(layoutId, dialogBinding.dialogContent, false)
                dialogBinding.dialogContent.addView(customView)
                populateDialogFields(customView, section)
            }

            dialogBinding.btnCancel.setOnClickListener {
                dialog.dismiss()
            }

            dialogBinding.btnSave.setOnClickListener {
                if (saveDialogFields(dialogBinding.dialogContent, section)) {
                    tampilkanRingkasan()
                    dialog.dismiss()
                    showSnackbar("Perubahan berhasil diperbarui!")
                }
            }

            dialog.show()

            val width = (resources.displayMetrics.widthPixels * 0.92).toInt()
            dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        } catch (e: Exception) {
            e.printStackTrace()
            showSnackbar("Gagal membuka form edit", isError = true)
        }
    }

    private fun populateDialogFields(view: View, section: String) {
        when (section) {
            "informasi_site" -> {
                view.findViewById<TextInputEditText>(R.id.editSiteId)?.setText(data.siteId)
                view.findViewById<TextInputEditText>(R.id.editSiteName)?.setText(data.siteName)
                view.findViewById<TextInputEditText>(R.id.editIdPelanggan)?.setText(data.idPelanggan)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spMcbKwh), R.array.mcb_kwh_options)
                setSpinnerSelectionByValue(view.findViewById(R.id.spMcbKwh), data.mcbKwh)
                view.findViewById<TextInputEditText>(R.id.editDaya)?.setText(data.daya)
            }
            "status_perangkat" -> {
                setupSpinnerAdapter(view.findViewById(R.id.spMainsFail), DropdownOptionsHelper.KEY_EAS)
                setupSpinnerAdapter(view.findViewById(R.id.spRectyFail), DropdownOptionsHelper.KEY_EAS)
                setupSpinnerAdapter(view.findViewById(R.id.spStolen), DropdownOptionsHelper.KEY_EAS)
                setupSpinnerAdapter(view.findViewById(R.id.spLowBatt), DropdownOptionsHelper.KEY_EAS)
                setupSpinnerAdapter(view.findViewById(R.id.spEngRun), DropdownOptionsHelper.KEY_EAS)

                setSpinnerSelectionByValue(view.findViewById(R.id.spMainsFail), data.mainsFail)
                setSpinnerSelectionByValue(view.findViewById(R.id.spRectyFail), data.rectyFail)
                setSpinnerSelectionByValue(view.findViewById(R.id.spStolen), data.stolen)
                setSpinnerSelectionByValue(view.findViewById(R.id.spLowBatt), data.lowBatt)
                setSpinnerSelectionByValue(view.findViewById(R.id.spEngRun), data.engRun)
            }
            "tegangan_arus" -> {
                view.findViewById<TextInputEditText>(R.id.editRV)?.setText(data.rV)
                view.findViewById<TextInputEditText>(R.id.editSV)?.setText(data.sV)
                view.findViewById<TextInputEditText>(R.id.editTV)?.setText(data.tV)
                view.findViewById<TextInputEditText>(R.id.editRA)?.setText(data.rA)
                view.findViewById<TextInputEditText>(R.id.editSA)?.setText(data.sA)
                view.findViewById<TextInputEditText>(R.id.editTA)?.setText(data.tA)
            }
            "genset" -> {
                setupSpinnerAdapter(view.findViewById(R.id.spMerkGenset1), DropdownOptionsHelper.KEY_MERK_GENSET)
                setupSpinnerAdapter(view.findViewById(R.id.spStatusGenset1), DropdownOptionsHelper.KEY_STATUS_GENSET)
                setupSpinnerAdapter(view.findViewById(R.id.spMerkGenset2), DropdownOptionsHelper.KEY_MERK_GENSET)
                setupSpinnerAdapter(view.findViewById(R.id.spStatusGenset2), DropdownOptionsHelper.KEY_STATUS_GENSET)

                setSpinnerSelectionByValue(view.findViewById(R.id.spMerkGenset1), data.merkGenset1)
                view.findViewById<TextInputEditText>(R.id.etCapacityGenset1)?.setText(data.capacityGenset1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spStatusGenset1), data.statusGenset1)
                view.findViewById<TextInputEditText>(R.id.etRemarkGenset1)?.setText(data.remarkGenset1)

                val swGenset2 = view.findViewById<SwitchMaterial>(R.id.switchAdaGenset2)
                val layoutGenset2 = view.findViewById<View>(R.id.layoutGenset2Container)
                swGenset2?.isChecked = data.adaGenset2
                layoutGenset2?.visibility = if (data.adaGenset2) View.VISIBLE else View.GONE
                swGenset2?.setOnCheckedChangeListener { _, isChecked ->
                    layoutGenset2?.visibility = if (isChecked) View.VISIBLE else View.GONE
                }

                setSpinnerSelectionByValue(view.findViewById(R.id.spMerkGenset2), data.merkGenset2)
                view.findViewById<TextInputEditText>(R.id.etCapacityGenset2)?.setText(data.capacityGenset2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spStatusGenset2), data.statusGenset2)
                view.findViewById<TextInputEditText>(R.id.etRemarkGenset2)?.setText(data.remarkGenset2)
            }
            "rectifier1" -> {
                val spJenis = view.findViewById<Spinner>(R.id.spJenisBaterai1)
                val spMerk = view.findViewById<Spinner>(R.id.spMerkBaterai1)
                val spJml = view.findViewById<Spinner>(R.id.spJumlahBattery1)

                setupSpinnerAdapter(view.findViewById(R.id.spTypeRectifier1), DropdownOptionsHelper.KEY_TYPE_RECTIFIER)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spJumlahModul1), R.array.jumlah_modul_options)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spJumlahModulRusak1), R.array.jumlah_modul_options)
                setupSpinnerAdapter(spJenis, DropdownOptionsHelper.KEY_JENIS_BATERAI)
                setupSpinnerAdapter(view.findViewById(R.id.spBatteryRecti1), DropdownOptionsHelper.KEY_BALTING)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spKondisiArester1), R.array.arester_options)

                setSpinnerSelectionByValue(view.findViewById(R.id.spTypeRectifier1), data.typeRectifier1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahModul1), data.jumlahModul1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahModulRusak1), data.jumlahModulRusak1)
                setSpinnerSelectionByValue(spJenis, data.jenisBaterai1)
                setSpinnerSelectionByValue(spMerk, data.merkBaterai1)
                setSpinnerSelectionByValue(spJml, data.jumlahBatteryP1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spBatteryRecti1), data.batteryRecti1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spKondisiArester1), data.kondisiArester1)

                setupCascadingBateraiListeners(spJenis, spMerk, spJml)

                setupSpinnerAdapter(view.findViewById(R.id.spJumlahBankNotAktif1), DropdownOptionsHelper.KEY_JUMLAH_BANK_NOT_AKTIF)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahBankNotAktif1), data.jumlahBankNotAktif1)

                val swNotAktif = view.findViewById<SwitchMaterial>(R.id.switchStatusBatteryNotAktif1)
                val layoutNotAktif = view.findViewById<View>(R.id.layoutJumlahBankNotAktif1)
                swNotAktif?.isChecked = data.statusBatteryNotAktif1
                layoutNotAktif?.visibility = if (data.statusBatteryNotAktif1) View.VISIBLE else View.GONE
                swNotAktif?.setOnCheckedChangeListener { _, isChecked ->
                    layoutNotAktif?.visibility = if (isChecked) View.VISIBLE else View.GONE
                }

                view.findViewById<TextInputEditText>(R.id.editMcbBatteryRecti1)?.setText(data.mcbBatteryRecti1)
                view.findViewById<TextInputEditText>(R.id.editBusVoltage1)?.setText(data.busVoltage1)
                view.findViewById<TextInputEditText>(R.id.editLoadA1)?.setText(data.loadA1)
                view.findViewById<TextInputEditText>(R.id.editPerangkat1)?.setText(data.perangkatTerhubung1)
                view.findViewById<TextInputEditText>(R.id.editRemark1)?.setText(data.remark1)
            }
            "rectifier2" -> {
                val spJenis = view.findViewById<Spinner>(R.id.spJenisBaterai2)
                val spMerk = view.findViewById<Spinner>(R.id.spMerkBaterai2)
                val spJml = view.findViewById<Spinner>(R.id.spJumlahBattery2)

                setupSpinnerAdapter(view.findViewById(R.id.spTypeRectifier2), DropdownOptionsHelper.KEY_TYPE_RECTIFIER)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spJumlahModul2), R.array.jumlah_modul_options)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spJumlahModulRusak2), R.array.jumlah_modul_options)
                setupSpinnerAdapter(spJenis, DropdownOptionsHelper.KEY_JENIS_BATERAI)
                setupSpinnerAdapter(view.findViewById(R.id.spBatteryRecti2), DropdownOptionsHelper.KEY_BALTING)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spKondisiArester2), R.array.arester_options)

                setSpinnerSelectionByValue(view.findViewById(R.id.spTypeRectifier2), data.typeRectifier2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahModul2), data.jumlahModul2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahModulRusak2), data.jumlahModulRusak2)
                setSpinnerSelectionByValue(spJenis, data.jenisBaterai2)
                setSpinnerSelectionByValue(spMerk, data.merkBaterai2)
                setSpinnerSelectionByValue(spJml, data.jumlahBatteryP2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spBatteryRecti2), data.batteryRecti2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spKondisiArester2), data.kondisiArester2)

                setupCascadingBateraiListeners(spJenis, spMerk, spJml)

                setupSpinnerAdapter(view.findViewById(R.id.spJumlahBankNotAktif2), DropdownOptionsHelper.KEY_JUMLAH_BANK_NOT_AKTIF)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahBankNotAktif2), data.jumlahBankNotAktif2)

                val swNotAktif = view.findViewById<SwitchMaterial>(R.id.switchStatusBatteryNotAktif2)
                val layoutNotAktif = view.findViewById<View>(R.id.layoutJumlahBankNotAktif2)
                swNotAktif?.isChecked = data.statusBatteryNotAktif2
                layoutNotAktif?.visibility = if (data.statusBatteryNotAktif2) View.VISIBLE else View.GONE
                swNotAktif?.setOnCheckedChangeListener { _, isChecked ->
                    layoutNotAktif?.visibility = if (isChecked) View.VISIBLE else View.GONE
                }

                view.findViewById<TextInputEditText>(R.id.editMcbBatteryRecti2)?.setText(data.mcbBatteryRecti2)
                view.findViewById<TextInputEditText>(R.id.editBusVoltage2)?.setText(data.busVoltage2)
                view.findViewById<TextInputEditText>(R.id.editLoadA2)?.setText(data.loadA2)
                view.findViewById<TextInputEditText>(R.id.editPerangkat2)?.setText(data.perangkatTerhubung2)
                view.findViewById<TextInputEditText>(R.id.editRemark2)?.setText(data.remark2)
            }
            "rectifier3" -> populateRectiGeneric(view, 3, data.typeRectifier3, data.jumlahModul3, data.jumlahModulRusak3, data.jenisBaterai3, data.merkBaterai3, data.jumlahBatteryP3, data.batteryRecti3, data.mcbBatteryRecti3, data.kondisiArester3, data.busVoltage3, data.loadA3, data.perangkatTerhubung3, data.statusBatteryNotAktif3, data.jumlahBankNotAktif3)
            "rectifier4" -> populateRectiGeneric(view, 4, data.typeRectifier4, data.jumlahModul4, data.jumlahModulRusak4, data.jenisBaterai4, data.merkBaterai4, data.jumlahBatteryP4, data.batteryRecti4, data.mcbBatteryRecti4, data.kondisiArester4, data.busVoltage4, data.loadA4, data.perangkatTerhubung4, data.statusBatteryNotAktif4, data.jumlahBankNotAktif4)
            "rectifier5" -> populateRectiGeneric(view, 5, data.typeRectifier5, data.jumlahModul5, data.jumlahModulRusak5, data.jenisBaterai5, data.merkBaterai5, data.jumlahBatteryP5, data.batteryRecti5, data.mcbBatteryRecti5, data.kondisiArester5, data.busVoltage5, data.loadA5, data.perangkatTerhubung5, data.statusBatteryNotAktif5, data.jumlahBankNotAktif5)
            "rectifier6" -> populateRectiGeneric(view, 6, data.typeRectifier6, data.jumlahModul6, data.jumlahModulRusak6, data.jenisBaterai6, data.merkBaterai6, data.jumlahBatteryP6, data.batteryRecti6, data.mcbBatteryRecti6, data.kondisiArester6, data.busVoltage6, data.loadA6, data.perangkatTerhubung6, data.statusBatteryNotAktif6, data.jumlahBankNotAktif6)
            "rectifier7" -> populateRectiGeneric(view, 7, data.typeRectifier7, data.jumlahModul7, data.jumlahModulRusak7, data.jenisBaterai7, data.merkBaterai7, data.jumlahBatteryP7, data.batteryRecti7, data.mcbBatteryRecti7, data.kondisiArester7, data.busVoltage7, data.loadA7, data.perangkatTerhubung7, data.statusBatteryNotAktif7, data.jumlahBankNotAktif7)
        }
    }

    private fun populateRectiGeneric(
        view: View, idx: Int, type: String, modul: String, modulRusak: String,
        jenisBat: String, merkBat: String, jmlBat: String, statusBat: String, mcbBat: String, arester: String,
        voltage: String, load: String, perangkat: String,
        statusNotAktif: Boolean = false, jmlBankNotAktif: String = ""
    ) {
        val spJenis = view.findViewById<Spinner>(resources.getIdentifier("spJenisBaterai$idx", "id", packageName))
        val spMerk = view.findViewById<Spinner>(resources.getIdentifier("spMerkBaterai$idx", "id", packageName))
        val spJml = view.findViewById<Spinner>(resources.getIdentifier("spJumlahBattery$idx", "id", packageName))

        setupSpinnerAdapter(view.findViewById(resources.getIdentifier("spTypeRectifier$idx", "id", packageName)), DropdownOptionsHelper.KEY_TYPE_RECTIFIER)
        setupSpinnerAdapterFromRes(view.findViewById(resources.getIdentifier("spJumlahModul$idx", "id", packageName)), R.array.jumlah_modul_options)
        setupSpinnerAdapterFromRes(view.findViewById(resources.getIdentifier("spJumlahModulRusak$idx", "id", packageName)), R.array.jumlah_modul_options)
        setupSpinnerAdapter(spJenis, DropdownOptionsHelper.KEY_JENIS_BATERAI)
        setupSpinnerAdapter(view.findViewById(resources.getIdentifier("spBatteryRecti$idx", "id", packageName)), DropdownOptionsHelper.KEY_BALTING)
        setupSpinnerAdapterFromRes(view.findViewById(resources.getIdentifier("spKondisiArester$idx", "id", packageName)), R.array.arester_options)

        setSpinnerSelectionByValue(view.findViewById(resources.getIdentifier("spTypeRectifier$idx", "id", packageName)), type)
        setSpinnerSelectionByValue(view.findViewById(resources.getIdentifier("spJumlahModul$idx", "id", packageName)), modul)
        setSpinnerSelectionByValue(view.findViewById(resources.getIdentifier("spJumlahModulRusak$idx", "id", packageName)), modulRusak)
        setSpinnerSelectionByValue(spJenis, jenisBat)
        setSpinnerSelectionByValue(spMerk, merkBat)
        setSpinnerSelectionByValue(spJml, jmlBat)
        setSpinnerSelectionByValue(view.findViewById(resources.getIdentifier("spBatteryRecti$idx", "id", packageName)), statusBat)
        setSpinnerSelectionByValue(view.findViewById(resources.getIdentifier("spKondisiArester$idx", "id", packageName)), arester)

        setupCascadingBateraiListeners(spJenis, spMerk, spJml)

        setupSpinnerAdapter(view.findViewById(resources.getIdentifier("spJumlahBankNotAktif$idx", "id", packageName)), DropdownOptionsHelper.KEY_JUMLAH_BANK_NOT_AKTIF)
        setSpinnerSelectionByValue(view.findViewById(resources.getIdentifier("spJumlahBankNotAktif$idx", "id", packageName)), jmlBankNotAktif)

        val swNotAktif = view.findViewById<SwitchMaterial>(resources.getIdentifier("switchStatusBatteryNotAktif$idx", "id", packageName))
        val layoutNotAktif = view.findViewById<View>(resources.getIdentifier("layoutJumlahBankNotAktif$idx", "id", packageName))
        swNotAktif?.isChecked = statusNotAktif
        layoutNotAktif?.visibility = if (statusNotAktif) View.VISIBLE else View.GONE
        swNotAktif?.setOnCheckedChangeListener { _, isChecked ->
            layoutNotAktif?.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        view.findViewById<TextInputEditText>(resources.getIdentifier("editMcbBatteryRecti$idx", "id", packageName))?.setText(mcbBat)
        view.findViewById<TextInputEditText>(resources.getIdentifier("editBusVoltage$idx", "id", packageName))?.setText(voltage)
        view.findViewById<TextInputEditText>(resources.getIdentifier("editLoadA$idx", "id", packageName))?.setText(load)
        view.findViewById<TextInputEditText>(resources.getIdentifier("editPerangkat$idx", "id", packageName))?.setText(perangkat)
    }

    private fun validasiRecti(rectiNum: Int, voltStr: String, loadStr: String): Boolean {
        if (voltStr.isNotBlank()) {
            val volt = voltStr.replace(",", ".").toDoubleOrNull()
            if (volt == null || volt < 40.0 || volt > 60.0) {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Bus Voltage Tidak Valid")
                    .setMessage("Bus Voltage pada Recti #$rectiNum harus di antara 40 - 60 V.\n\nNilai diinput: $voltStr V")
                    .setPositiveButton("Mengerti", null)
                    .show()
                showSnackbar("Bus Voltage Recti #$rectiNum harus antara 40 - 60 V", isError = true)
                return false
            }
        }
        if (loadStr.isNotBlank()) {
            val load = loadStr.replace(",", ".").toDoubleOrNull()
            if (load == null || load < 0.0 || load > 200.0) {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Load (A) Tidak Valid")
                    .setMessage("Load (A) pada Recti #$rectiNum harus di antara 0 - 200 A.\n\nNilai diinput: $loadStr A")
                    .setPositiveButton("Mengerti", null)
                    .show()
                showSnackbar("Load Recti #$rectiNum harus antara 0 - 200 A", isError = true)
                return false
            }
        }
        return true
    }

    private fun saveDialogFields(formView: View, section: String): Boolean {
        when (section) {
            "informasi_site" -> {
                data.siteId = formView.findViewById<TextInputEditText>(R.id.editSiteId)?.text.toString().trim()
                data.siteName = formView.findViewById<TextInputEditText>(R.id.editSiteName)?.text.toString().trim()
                data.idPelanggan = formView.findViewById<TextInputEditText>(R.id.editIdPelanggan)?.text.toString().trim()
                data.mcbKwh = formView.findViewById<Spinner>(R.id.spMcbKwh)?.selectedItem?.toString() ?: ""
                data.daya = formView.findViewById<TextInputEditText>(R.id.editDaya)?.text.toString().trim()
            }
            "status_perangkat" -> {
                data.mainsFail = formView.findViewById<Spinner>(R.id.spMainsFail)?.selectedItem?.toString() ?: ""
                data.rectyFail = formView.findViewById<Spinner>(R.id.spRectyFail)?.selectedItem?.toString() ?: ""
                data.stolen = formView.findViewById<Spinner>(R.id.spStolen)?.selectedItem?.toString() ?: ""
                data.lowBatt = formView.findViewById<Spinner>(R.id.spLowBatt)?.selectedItem?.toString() ?: ""
                data.engRun = formView.findViewById<Spinner>(R.id.spEngRun)?.selectedItem?.toString() ?: ""
            }
            "tegangan_arus" -> {
                data.rV = formView.findViewById<TextInputEditText>(R.id.editRV)?.text.toString().trim()
                data.sV = formView.findViewById<TextInputEditText>(R.id.editSV)?.text.toString().trim()
                data.tV = formView.findViewById<TextInputEditText>(R.id.editTV)?.text.toString().trim()
                data.rA = formView.findViewById<TextInputEditText>(R.id.editRA)?.text.toString().trim()
                data.sA = formView.findViewById<TextInputEditText>(R.id.editSA)?.text.toString().trim()
                data.tA = formView.findViewById<TextInputEditText>(R.id.editTA)?.text.toString().trim()
            }
            "genset" -> {
                data.merkGenset1 = formView.findViewById<Spinner>(R.id.spMerkGenset1)?.selectedItem?.toString() ?: ""
                data.capacityGenset1 = formView.findViewById<TextInputEditText>(R.id.etCapacityGenset1)?.text.toString().trim()
                data.statusGenset1 = formView.findViewById<Spinner>(R.id.spStatusGenset1)?.selectedItem?.toString() ?: ""
                data.remarkGenset1 = formView.findViewById<TextInputEditText>(R.id.etRemarkGenset1)?.text.toString().trim()

                data.adaGenset2 = formView.findViewById<SwitchMaterial>(R.id.switchAdaGenset2)?.isChecked ?: false
                if (data.adaGenset2) {
                    data.merkGenset2 = formView.findViewById<Spinner>(R.id.spMerkGenset2)?.selectedItem?.toString() ?: ""
                    data.capacityGenset2 = formView.findViewById<TextInputEditText>(R.id.etCapacityGenset2)?.text.toString().trim()
                    data.statusGenset2 = formView.findViewById<Spinner>(R.id.spStatusGenset2)?.selectedItem?.toString() ?: ""
                    data.remarkGenset2 = formView.findViewById<TextInputEditText>(R.id.etRemarkGenset2)?.text.toString().trim()
                }
            }
            "rectifier1" -> {
                val volt = formView.findViewById<TextInputEditText>(R.id.editBusVoltage1)?.text.toString().trim()
                val load = formView.findViewById<TextInputEditText>(R.id.editLoadA1)?.text.toString().trim()
                if (!validasiRecti(1, volt, load)) return false

                data.typeRectifier1 = formView.findViewById<Spinner>(R.id.spTypeRectifier1)?.selectedItem?.toString() ?: ""
                data.jumlahModul1 = formView.findViewById<Spinner>(R.id.spJumlahModul1)?.selectedItem?.toString() ?: ""
                data.jumlahModulRusak1 = formView.findViewById<Spinner>(R.id.spJumlahModulRusak1)?.selectedItem?.toString() ?: ""
                data.jenisBaterai1 = formView.findViewById<Spinner>(R.id.spJenisBaterai1)?.selectedItem?.toString() ?: ""
                data.merkBaterai1 = formView.findViewById<Spinner>(R.id.spMerkBaterai1)?.selectedItem?.toString() ?: ""
                data.jumlahBatteryP1 = formView.findViewById<Spinner>(R.id.spJumlahBattery1)?.selectedItem?.toString() ?: ""
                data.batteryRecti1 = formView.findViewById<Spinner>(R.id.spBatteryRecti1)?.selectedItem?.toString() ?: ""

                data.statusBatteryNotAktif1 = formView.findViewById<SwitchMaterial>(R.id.switchStatusBatteryNotAktif1)?.isChecked ?: false
                data.jumlahBankNotAktif1 = if (data.statusBatteryNotAktif1) formView.findViewById<Spinner>(R.id.spJumlahBankNotAktif1)?.selectedItem?.toString() ?: "" else ""

                data.mcbBatteryRecti1 = formView.findViewById<TextInputEditText>(R.id.editMcbBatteryRecti1)?.text.toString().trim()
                data.kondisiArester1 = formView.findViewById<Spinner>(R.id.spKondisiArester1)?.selectedItem?.toString() ?: ""
                data.busVoltage1 = volt
                data.loadA1 = load
                data.perangkatTerhubung1 = formView.findViewById<TextInputEditText>(R.id.editPerangkat1)?.text.toString().trim()
                data.remark1 = formView.findViewById<TextInputEditText>(R.id.editRemark1)?.text.toString().trim()
            }
            "rectifier2" -> {
                val volt = formView.findViewById<TextInputEditText>(R.id.editBusVoltage2)?.text.toString().trim()
                val load = formView.findViewById<TextInputEditText>(R.id.editLoadA2)?.text.toString().trim()
                if (!validasiRecti(2, volt, load)) return false

                data.typeRectifier2 = formView.findViewById<Spinner>(R.id.spTypeRectifier2)?.selectedItem?.toString() ?: ""
                data.jumlahModul2 = formView.findViewById<Spinner>(R.id.spJumlahModul2)?.selectedItem?.toString() ?: ""
                data.jumlahModulRusak2 = formView.findViewById<Spinner>(R.id.spJumlahModulRusak2)?.selectedItem?.toString() ?: ""
                data.jenisBaterai2 = formView.findViewById<Spinner>(R.id.spJenisBaterai2)?.selectedItem?.toString() ?: ""
                data.merkBaterai2 = formView.findViewById<Spinner>(R.id.spMerkBaterai2)?.selectedItem?.toString() ?: ""
                data.jumlahBatteryP2 = formView.findViewById<Spinner>(R.id.spJumlahBattery2)?.selectedItem?.toString() ?: ""
                data.batteryRecti2 = formView.findViewById<Spinner>(R.id.spBatteryRecti2)?.selectedItem?.toString() ?: ""

                data.statusBatteryNotAktif2 = formView.findViewById<SwitchMaterial>(R.id.switchStatusBatteryNotAktif2)?.isChecked ?: false
                data.jumlahBankNotAktif2 = if (data.statusBatteryNotAktif2) formView.findViewById<Spinner>(R.id.spJumlahBankNotAktif2)?.selectedItem?.toString() ?: "" else ""

                data.mcbBatteryRecti2 = formView.findViewById<TextInputEditText>(R.id.editMcbBatteryRecti2)?.text.toString().trim()
                data.kondisiArester2 = formView.findViewById<Spinner>(R.id.spKondisiArester2)?.selectedItem?.toString() ?: ""
                data.busVoltage2 = volt
                data.loadA2 = load
                data.perangkatTerhubung2 = formView.findViewById<TextInputEditText>(R.id.editPerangkat2)?.text.toString().trim()
                data.remark2 = formView.findViewById<TextInputEditText>(R.id.editRemark2)?.text.toString().trim()
            }
            "rectifier3" -> if (!saveRectiGeneric(formView, 3, { data.typeRectifier3 = it }, { data.jumlahModul3 = it }, { data.jumlahModulRusak3 = it }, { data.jenisBaterai3 = it }, { data.merkBaterai3 = it }, { data.jumlahBatteryP3 = it }, { data.batteryRecti3 = it }, { data.mcbBatteryRecti3 = it }, { data.kondisiArester3 = it }, { data.busVoltage3 = it }, { data.loadA3 = it }, { data.perangkatTerhubung3 = it }, { data.statusBatteryNotAktif3 = it }, { data.jumlahBankNotAktif3 = it })) return false
            "rectifier4" -> if (!saveRectiGeneric(formView, 4, { data.typeRectifier4 = it }, { data.jumlahModul4 = it }, { data.jumlahModulRusak4 = it }, { data.jenisBaterai4 = it }, { data.merkBaterai4 = it }, { data.jumlahBatteryP4 = it }, { data.batteryRecti4 = it }, { data.mcbBatteryRecti4 = it }, { data.kondisiArester4 = it }, { data.busVoltage4 = it }, { data.loadA4 = it }, { data.perangkatTerhubung4 = it }, { data.statusBatteryNotAktif4 = it }, { data.jumlahBankNotAktif4 = it })) return false
            "rectifier5" -> if (!saveRectiGeneric(formView, 5, { data.typeRectifier5 = it }, { data.jumlahModul5 = it }, { data.jumlahModulRusak5 = it }, { data.jenisBaterai5 = it }, { data.merkBaterai5 = it }, { data.jumlahBatteryP5 = it }, { data.batteryRecti5 = it }, { data.mcbBatteryRecti5 = it }, { data.kondisiArester5 = it }, { data.busVoltage5 = it }, { data.loadA5 = it }, { data.perangkatTerhubung5 = it }, { data.statusBatteryNotAktif5 = it }, { data.jumlahBankNotAktif5 = it })) return false
            "rectifier6" -> if (!saveRectiGeneric(formView, 6, { data.typeRectifier6 = it }, { data.jumlahModul6 = it }, { data.jumlahModulRusak6 = it }, { data.jenisBaterai6 = it }, { data.merkBaterai6 = it }, { data.jumlahBatteryP6 = it }, { data.batteryRecti6 = it }, { data.mcbBatteryRecti6 = it }, { data.kondisiArester6 = it }, { data.busVoltage6 = it }, { data.loadA6 = it }, { data.perangkatTerhubung6 = it }, { data.statusBatteryNotAktif6 = it }, { data.jumlahBankNotAktif6 = it })) return false
            "rectifier7" -> if (!saveRectiGeneric(formView, 7, { data.typeRectifier7 = it }, { data.jumlahModul7 = it }, { data.jumlahModulRusak7 = it }, { data.jenisBaterai7 = it }, { data.merkBaterai7 = it }, { data.jumlahBatteryP7 = it }, { data.batteryRecti7 = it }, { data.mcbBatteryRecti7 = it }, { data.kondisiArester7 = it }, { data.busVoltage7 = it }, { data.loadA7 = it }, { data.perangkatTerhubung7 = it }, { data.statusBatteryNotAktif7 = it }, { data.jumlahBankNotAktif7 = it })) return false
        }
        return true
    }

    private fun saveRectiGeneric(
        formView: View, idx: Int,
        setType: (String) -> Unit, setModul: (String) -> Unit, setModulRusak: (String) -> Unit,
        setJenisBat: (String) -> Unit, setMerkBat: (String) -> Unit,
        setJmlBat: (String) -> Unit, setStatusBat: (String) -> Unit, setMcb: (String) -> Unit,
        setArester: (String) -> Unit, setVolt: (String) -> Unit, setLoad: (String) -> Unit,
        setPerangkat: (String) -> Unit, setStatusNotAktif: (Boolean) -> Unit, setJmlBankNotAktif: (String) -> Unit
    ): Boolean {
        val volt = formView.findViewById<TextInputEditText>(resources.getIdentifier("editBusVoltage$idx", "id", packageName))?.text.toString().trim()
        val load = formView.findViewById<TextInputEditText>(resources.getIdentifier("editLoadA$idx", "id", packageName))?.text.toString().trim()
        if (!validasiRecti(idx, volt, load)) return false

        setType(formView.findViewById<Spinner>(resources.getIdentifier("spTypeRectifier$idx", "id", packageName))?.selectedItem?.toString() ?: "")
        setModul(formView.findViewById<Spinner>(resources.getIdentifier("spJumlahModul$idx", "id", packageName))?.selectedItem?.toString() ?: "")
        setModulRusak(formView.findViewById<Spinner>(resources.getIdentifier("spJumlahModulRusak$idx", "id", packageName))?.selectedItem?.toString() ?: "")
        setJenisBat(formView.findViewById<Spinner>(resources.getIdentifier("spJenisBaterai$idx", "id", packageName))?.selectedItem?.toString() ?: "")
        setMerkBat(formView.findViewById<Spinner>(resources.getIdentifier("spMerkBaterai$idx", "id", packageName))?.selectedItem?.toString() ?: "")
        setJmlBat(formView.findViewById<Spinner>(resources.getIdentifier("spJumlahBattery$idx", "id", packageName))?.selectedItem?.toString() ?: "")
        setStatusBat(formView.findViewById<Spinner>(resources.getIdentifier("spBatteryRecti$idx", "id", packageName))?.selectedItem?.toString() ?: "")

        val swNotAktif = formView.findViewById<SwitchMaterial>(resources.getIdentifier("switchStatusBatteryNotAktif$idx", "id", packageName))
        val isNotAktif = swNotAktif?.isChecked ?: false
        setStatusNotAktif(isNotAktif)

        val jmlNotAktif = if (isNotAktif) formView.findViewById<Spinner>(resources.getIdentifier("spJumlahBankNotAktif$idx", "id", packageName))?.selectedItem?.toString() ?: "" else ""
        setJmlBankNotAktif(jmlNotAktif)

        setMcb(formView.findViewById<TextInputEditText>(resources.getIdentifier("editMcbBatteryRecti$idx", "id", packageName))?.text.toString().trim())
        setArester(formView.findViewById<Spinner>(resources.getIdentifier("spKondisiArester$idx", "id", packageName))?.selectedItem?.toString() ?: "")
        setVolt(volt)
        setLoad(load)
        setPerangkat(formView.findViewById<TextInputEditText>(resources.getIdentifier("editPerangkat$idx", "id", packageName))?.text.toString().trim())
        return true
    }

    private fun setSpinnerSelectionByValue(spinner: Spinner?, value: String) {
        if (spinner == null || spinner.adapter == null) return
        val adapter = spinner.adapter
        for (i in 0 until adapter.count) {
            if (adapter.getItem(i).toString().equals(value, ignoreCase = true)) {
                spinner.setSelection(i)
                break
            }
        }
    }

    private fun konfirmasiSubmit() {
        AlertDialog.Builder(this)
            .setTitle("Simpan Data")
            .setMessage("Yakin ingin menyimpan seluruh data site ${data.siteId}?")
            .setPositiveButton("Ya, Simpan") { _, _ -> kirimDataKeServer() }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun kirimDataKeServer() {
        kompresSemuaFotoLaluLanjut { lanjutkanKirimDataBaru() }
    }

    private fun kompresSemuaFotoLaluLanjut(onSelesai: () -> Unit) {
        val uncompressed = mutableListOf<Pair<Int, Uri>>()
        for (i in 0 until photoUris.size) {
            if (!imageBase64Caches.containsKey(i)) {
                try {
                    uncompressed.add(Pair(i, Uri.parse(photoUris[i])))
                } catch (_: Exception) { }
            }
        }

        if (uncompressed.isEmpty()) {
            onSelesai()
            return
        }

        showLoadingOverlay("Mengompres ${uncompressed.size} foto...")
        binding.btnSimpanFinal.isEnabled = false
        Thread {
            val latch = CountDownLatch(uncompressed.size)
            val executor = Executors.newFixedThreadPool(4)

            for ((index, uri) in uncompressed) {
                executor.execute {
                    try {
                        val b64 = ImageHelper.uriToCompressedBase64(this, uri)
                        if (b64 != null) {
                            synchronized(imageBase64Caches) {
                                imageBase64Caches[index] = b64
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        latch.countDown()
                    }
                }
            }

            try {
                latch.await()
            } catch (_: Exception) { }
            executor.shutdown()

            runOnUiThread { onSelesai() }
        }.start()
    }

    private fun lanjutkanKirimDataBaru() {
        pendingAction = "input"
        showLoadingOverlay("Menyimpan data...")
        binding.btnSimpanFinal.isEnabled = false

        val queue = VolleyHelper.getRequestQueue(this)

        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                hideLoadingOverlay()
                try {
                    val json = JSONObject(response)
                    when (json.optString("status")) {
                        "success" -> {
                            notificationHelper.showNotification(
                                "✅ Data Berhasil Disimpan",
                                "Site ${data.siteId} berhasil ditambahkan.",
                                true
                            )
                            showSnackbar("✅ Data berhasil disimpan!")
                            val intent = Intent()
                            intent.putExtra("RESET_FORM", true)
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                        "duplicate" -> {
                            notificationHelper.showNotification(
                                "⚠️ Site ID Sudah Ada",
                                "Site ${data.siteId} sudah terdaftar di sistem.",
                                false
                            )
                            showDuplicateDialog()
                        }
                        else -> {
                            showSnackbar("❌ Gagal: ${json.optString("message")}", isError = true)
                        }
                    }
                } catch (e: Exception) {
                    showSnackbar("❌ Gagal membaca respons server", isError = true)
                }
                binding.btnSimpanFinal.isEnabled = true
            },
            { error ->
                hideLoadingOverlay()
                binding.btnSimpanFinal.isEnabled = true

                notificationHelper.showNotification(
                    "❌ Gagal Menyimpan Data",
                    "Gagal menyimpan Site ${data.siteId}. Cek koneksi internet.",
                    false
                )

                if (error.cause is SocketTimeoutException || error is com.android.volley.TimeoutError) {
                    showTimeoutDialog()
                } else {
                    showErrorDialog(error.message ?: "Koneksi terputus")
                }
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
                return getSubmitParams()
            }
        }

        VolleyHelper.apply60sTimeout(request)
        queue.add(request)
    }

    private fun showDuplicateDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Site ID Sudah Terdaftar")
            .setMessage("Site ID ${data.siteId} sudah terdaftar di sistem.")
            .setPositiveButton("Mengerti", null)
            .show()
    }

    private fun showTimeoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Koneksi Timeout")
            .setMessage("Proses pengiriman memakan waktu lebih lama dari biasanya.\n\nCoba kirim ulang?")
            .setPositiveButton("Kirim Ulang") { _, _ ->
                if (pendingAction == "update") kirimUpdateKeServer() else kirimDataKeServer()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showErrorDialog(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Gagal Menyimpan")
            .setMessage(msg)
            .setPositiveButton("Coba Lagi") { _, _ ->
                if (pendingAction == "update") kirimUpdateKeServer() else kirimDataKeServer()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun kirimUpdateKeServer() {
        kompresSemuaFotoLaluLanjut { lanjutkanKirimUpdate() }
    }

    private fun lanjutkanKirimUpdate() {
        pendingAction = "update"
        showLoadingOverlay("Mengupdate data...")
        binding.btnSimpanFinal.isEnabled = false

        val queue = VolleyHelper.getRequestQueue(this)

        val request = object : StringRequest(
            Method.POST, urlSheet,
            { response ->
                hideLoadingOverlay()
                try {
                    val json = JSONObject(response)
                    if (json.optString("status") == "success") {
                        showSnackbar("✅ Data berhasil diupdate!")
                        val intent = Intent()
                        intent.putExtra("RESET_FORM", true)
                        setResult(RESULT_OK, intent)
                        finish()
                    } else {
                        showSnackbar("❌ Gagal update: ${json.optString("message")}", isError = true)
                    }
                } catch (e: Exception) {
                    showSnackbar("❌ Gagal membaca respons server", isError = true)
                }
                binding.btnSimpanFinal.isEnabled = true
            },
            { error ->
                hideLoadingOverlay()
                binding.btnSimpanFinal.isEnabled = true

                if (error.cause is SocketTimeoutException || error is TimeoutError) {
                    showTimeoutDialog()
                } else {
                    showErrorDialog(error.message ?: "Koneksi terputus")
                }
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
                val params = getSubmitParams()
                params["action"] = "update"
                return params
            }
        }

        VolleyHelper.apply60sTimeout(request)
        queue.add(request)
    }

    private fun getSubmitParams(): MutableMap<String, String> {
        val params = HashMap<String, String>()
        params["username"] = SessionHelper.getUsername(this)

        for (i in 0 until photoUris.size) {
            val b64 = imageBase64Caches[i]
            if (b64 != null) {
                params["image_base64_${i + 1}"] = b64
            }
        }
        if (imageBase64Caches[0] != null) params["image_base64_1"] = imageBase64Caches[0]!!
        if (imageBase64Caches[1] != null) params["image_base64_2"] = imageBase64Caches[1]!!
        if (imageBase64Caches[2] != null) params["image_base64_3"] = imageBase64Caches[2]!!

        params["site_id"] = data.siteId
        params["site_name"] = data.siteName
        params["id_pelanggan"] = data.idPelanggan
        params["mcb_kwh"] = data.mcbKwh
        params["daya"] = data.daya
        params["mains_fail"] = data.mainsFail
        params["recty_fail"] = data.rectyFail
        params["stolen"] = data.stolen
        params["low_batt"] = data.lowBatt
        params["eng_run"] = data.engRun
        params["r_v"] = data.rV
        params["s_v"] = data.sV
        params["t_v"] = data.tV
        params["r_a"] = data.rA
        params["s_a"] = data.sA
        params["t_a"] = data.tA

        params["merk_genset_1"] = data.merkGenset1
        params["capacity_genset_1"] = data.capacityGenset1
        params["status_genset_1"] = data.statusGenset1
        params["remark_genset_1"] = data.remarkGenset1

        params["ada_genset_2"] = if (data.adaGenset2) "1" else "0"
        if (data.adaGenset2) {
            params["merk_genset_2"] = data.merkGenset2
            params["capacity_genset_2"] = data.capacityGenset2
            params["status_genset_2"] = data.statusGenset2
            params["remark_genset_2"] = data.remarkGenset2
        }

        params["type_rectifier_1"] = data.typeRectifier1
        params["jumlah_modul_1"] = data.jumlahModul1
        params["jumlah_modul_rusak_1"] = data.jumlahModulRusak1
        params["jenis_baterai_1"] = data.jenisBaterai1
        params["merk_baterai_1"] = data.merkBaterai1
        params["jumlah_battery_p"] = data.jumlahBatteryP1
        params["battery_recti_1"] = data.batteryRecti1
        params["jumlah_bank_not_aktif_1"] = if (data.statusBatteryNotAktif1) data.jumlahBankNotAktif1 else ""
        params["mcb_battery_recti_1"] = data.mcbBatteryRecti1
        params["kondisi_arester_1"] = data.kondisiArester1
        params["bus_voltage_1"] = data.busVoltage1
        params["load_a_1"] = data.loadA1
        params["perangkat_terhubung_1"] = data.perangkatTerhubung1
        params["remark_1"] = data.remark1

        params["ada_recti_2"] = if (data.adaRecti2) "1" else "0"
        if (data.adaRecti2) {
            params["type_rectifier_2"] = data.typeRectifier2
            params["jumlah_modul_2"] = data.jumlahModul2
            params["jumlah_modul_rusak_2"] = data.jumlahModulRusak2
            params["jenis_baterai_2"] = data.jenisBaterai2
            params["merk_baterai_2"] = data.merkBaterai2
            params["jumlah_battery_p_2"] = data.jumlahBatteryP2
            params["battery_recti_2"] = data.batteryRecti2
            params["jumlah_bank_not_aktif_2"] = if (data.statusBatteryNotAktif2) data.jumlahBankNotAktif2 else ""
            params["mcb_battery_recti_2"] = data.mcbBatteryRecti2
            params["kondisi_arester_2"] = data.kondisiArester2
            params["bus_voltage_2"] = data.busVoltage2
            params["load_a_2"] = data.loadA2
            params["perangkat_terhubung_2"] = data.perangkatTerhubung2
            params["remark_2"] = data.remark2
        }

        fun putRectiParams(idx: Int, enabled: Boolean, type: String, modul: String, modulRusak: String, jenisBat: String, merkBat: String, jmlBat: String, statusBat: String, notAktif: Boolean, jmlNotAktif: String, mcbBat: String, arester: String, voltage: String, load: String, perangkat: String) {
            params["ada_recti_$idx"] = if (enabled) "1" else "0"
            if (enabled) {
                params["type_rectifier_$idx"] = type
                params["jumlah_modul_$idx"] = modul
                params["jumlah_modul_rusak_$idx"] = modulRusak
                params["jenis_baterai_$idx"] = jenisBat
                params["merk_baterai_$idx"] = merkBat
                params["jumlah_battery_p_$idx"] = jmlBat
                params["battery_recti_$idx"] = statusBat
                params["jumlah_bank_not_aktif_$idx"] = if (notAktif) jmlNotAktif else ""
                params["mcb_battery_recti_$idx"] = mcbBat
                params["kondisi_arester_$idx"] = arester
                params["bus_voltage_$idx"] = voltage
                params["load_a_$idx"] = load
                params["perangkat_terhubung_$idx"] = perangkat
            }
        }

        putRectiParams(3, data.adaRecti3, data.typeRectifier3, data.jumlahModul3, data.jumlahModulRusak3, data.jenisBaterai3, data.merkBaterai3, data.jumlahBatteryP3, data.batteryRecti3, data.statusBatteryNotAktif3, data.jumlahBankNotAktif3, data.mcbBatteryRecti3, data.kondisiArester3, data.busVoltage3, data.loadA3, data.perangkatTerhubung3)
        putRectiParams(4, data.adaRecti4, data.typeRectifier4, data.jumlahModul4, data.jumlahModulRusak4, data.jenisBaterai4, data.merkBaterai4, data.jumlahBatteryP4, data.batteryRecti4, data.statusBatteryNotAktif4, data.jumlahBankNotAktif4, data.mcbBatteryRecti4, data.kondisiArester4, data.busVoltage4, data.loadA4, data.perangkatTerhubung4)
        putRectiParams(5, data.adaRecti5, data.typeRectifier5, data.jumlahModul5, data.jumlahModulRusak5, data.jenisBaterai5, data.merkBaterai5, data.jumlahBatteryP5, data.batteryRecti5, data.statusBatteryNotAktif5, data.jumlahBankNotAktif5, data.mcbBatteryRecti5, data.kondisiArester5, data.busVoltage5, data.loadA5, data.perangkatTerhubung5)
        putRectiParams(6, data.adaRecti6, data.typeRectifier6, data.jumlahModul6, data.jumlahModulRusak6, data.jenisBaterai6, data.merkBaterai6, data.jumlahBatteryP6, data.batteryRecti6, data.statusBatteryNotAktif6, data.jumlahBankNotAktif6, data.mcbBatteryRecti6, data.kondisiArester6, data.busVoltage6, data.loadA6, data.perangkatTerhubung6)
        putRectiParams(7, data.adaRecti7, data.typeRectifier7, data.jumlahModul7, data.jumlahModulRusak7, data.jenisBaterai7, data.merkBaterai7, data.jumlahBatteryP7, data.batteryRecti7, data.statusBatteryNotAktif7, data.jumlahBankNotAktif7, data.mcbBatteryRecti7, data.kondisiArester7, data.busVoltage7, data.loadA7, data.perangkatTerhubung7)

        return params
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(binding.main, message, Snackbar.LENGTH_LONG)
        snackbar.anchorView = binding.btnSimpanFinal
        if (isError) {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.error))
            snackbar.setTextColor(ContextCompat.getColor(this, R.color.white))
        } else {
            snackbar.setBackgroundTint(Color.parseColor("#1F2937"))
            snackbar.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
        snackbar.show()
    }
}
