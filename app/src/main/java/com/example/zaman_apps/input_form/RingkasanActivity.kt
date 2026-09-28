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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONObject
import java.net.SocketTimeoutException

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

    private val addPhotoRingkasanLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (photoUris.size < 10) {
                photoUris.add(it.toString())
                binding.tvBadgeTotalFoto.text = "${photoUris.size} Foto"
                showPopupLihatFoto()
                tampilkanRingkasan()
                showSnackbar("Foto baru berhasil ditambahkan")
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

        // Tombol Tambah Foto Baru di Pop Up
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
                        1 -> addPhotoRingkasanLauncher.launch("image/*")
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

            binding.root.findViewById<NestedScrollView>(R.id.nestedScrollView)?.isEnabled = true

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

        val recti1Text = StringBuilder()
        recti1Text.append("Type: ${valOrDash(data.typeRectifier1)}\n")
        recti1Text.append("Modul: ${valOrDash(data.jumlahModul1)} (Rusak: ${valOrDash(data.jumlahModulRusak1)})\n")
        recti1Text.append("Jenis Baterai: ${valOrDash(data.jenisBaterai1)}\n")
        recti1Text.append("Merk Baterai: ${valOrDash(data.merkBaterai1)}\n")
        recti1Text.append("Jumlah Battery: ${valOrDash(data.jumlahBatteryP1)}\n")
        recti1Text.append("Battery Status: ${valOrDash(data.batteryRecti1)}\n")
        recti1Text.append("Voltage: ${valOrDash(data.busVoltage1)} V | Load: ${valOrDash(data.loadA1)} A\n")
        recti1Text.append("Perangkat: ${valOrDash(data.perangkatTerhubung1)}\n")
        recti1Text.append("Catatan: ${valOrDash(data.remark1)}")

        tambahCard(
            judul = "Rectifier Unit 1",
            isi = recti1Text.toString(),
            section = "rectifier1"
        )

        if (data.adaRecti2) {
            val recti2Text = StringBuilder()
            recti2Text.append("Type: ${valOrDash(data.typeRectifier2)}\n")
            recti2Text.append("Modul: ${valOrDash(data.jumlahModul2)} (Rusak: ${valOrDash(data.jumlahModulRusak2)})\n")
            recti2Text.append("Jenis Baterai: ${valOrDash(data.jenisBaterai2)}\n")
            recti2Text.append("Merk Baterai: ${valOrDash(data.merkBaterai2)}\n")
            recti2Text.append("Jumlah Battery: ${valOrDash(data.jumlahBatteryP2)}\n")
            recti2Text.append("Battery Status: ${valOrDash(data.batteryRecti2)}\n")
            recti2Text.append("Voltage: ${valOrDash(data.busVoltage2)} V | Load: ${valOrDash(data.loadA2)} A\n")
            recti2Text.append("Perangkat: ${valOrDash(data.perangkatTerhubung2)}\n")
            recti2Text.append("Catatan: ${valOrDash(data.remark2)}")

            tambahCard(
                judul = "Rectifier Unit 2",
                isi = recti2Text.toString(),
                section = "rectifier2"
            )
        } else {
            tambahCardTombol("➕ Tambah Rectifier Unit 2") {
                data.adaRecti2 = true
                showEditDialog("rectifier2")
            }
        }

        if (data.adaRecti3) {
            val recti3Text = StringBuilder()
            recti3Text.append("Type: ${valOrDash(data.typeRectifier3)}\n")
            recti3Text.append("Modul: ${valOrDash(data.jumlahModul3)} (Rusak: ${valOrDash(data.jumlahModulRusak3)})\n")
            recti3Text.append("Jumlah Battery: ${valOrDash(data.jumlahBatteryP3)}\n")
            recti3Text.append("Battery Status: ${valOrDash(data.batteryRecti3)}\n")
            recti3Text.append("Voltage: ${valOrDash(data.busVoltage3)} V | Load: ${valOrDash(data.loadA3)} A\n")
            recti3Text.append("Perangkat: ${valOrDash(data.perangkatTerhubung3)}")

            tambahCard(
                judul = "Rectifier Unit 3",
                isi = recti3Text.toString(),
                section = "rectifier3"
            )
        } else if (data.adaRecti2) {
            tambahCardTombol("➕ Tambah Rectifier Unit 3") {
                data.adaRecti3 = true
                showEditDialog("rectifier3")
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
            "rectifier1", "rectifier2", "rectifier3" -> android.R.drawable.ic_menu_preferences
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
                } else if (value.contains("Fail", true) || value.contains("Rusak", true) || value.contains("Alarm", true)) {
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
                saveDialogFields(dialogBinding.dialogContent, section)
                tampilkanRingkasan()
                dialog.dismiss()
                showSnackbar("Perubahan berhasil diperbarui!")
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
                setupSpinnerAdapter(view.findViewById(R.id.spTypeRectifier1), DropdownOptionsHelper.KEY_TYPE_RECTIFIER)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spJumlahModul1), R.array.jumlah_modul_options)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spJumlahModulRusak1), R.array.jumlah_modul_options)
                setupSpinnerAdapter(view.findViewById(R.id.spJenisBaterai1), DropdownOptionsHelper.KEY_JENIS_BATERAI)
                setupSpinnerAdapter(view.findViewById(R.id.spMerkBaterai1), DropdownOptionsHelper.KEY_MERK_BATERAI)
                setupSpinnerAdapter(view.findViewById(R.id.spJumlahBattery1), DropdownOptionsHelper.KEY_JUMLAH_BATTERAI)
                setupSpinnerAdapter(view.findViewById(R.id.spBatteryRecti1), DropdownOptionsHelper.KEY_BALTING)

                setSpinnerSelectionByValue(view.findViewById(R.id.spTypeRectifier1), data.typeRectifier1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahModul1), data.jumlahModul1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahModulRusak1), data.jumlahModulRusak1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJenisBaterai1), data.jenisBaterai1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spMerkBaterai1), data.merkBaterai1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahBattery1), data.jumlahBatteryP1)
                setSpinnerSelectionByValue(view.findViewById(R.id.spBatteryRecti1), data.batteryRecti1)

                view.findViewById<TextInputEditText>(R.id.editBusVoltage1)?.setText(data.busVoltage1)
                view.findViewById<TextInputEditText>(R.id.editLoadA1)?.setText(data.loadA1)
                view.findViewById<TextInputEditText>(R.id.editPerangkat1)?.setText(data.perangkatTerhubung1)
                view.findViewById<TextInputEditText>(R.id.editRemark1)?.setText(data.remark1)
            }
            "rectifier2" -> {
                setupSpinnerAdapter(view.findViewById(R.id.spTypeRectifier2), DropdownOptionsHelper.KEY_TYPE_RECTIFIER)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spJumlahModul2), R.array.jumlah_modul_options)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spJumlahModulRusak2), R.array.jumlah_modul_options)
                setupSpinnerAdapter(view.findViewById(R.id.spJenisBaterai2), DropdownOptionsHelper.KEY_JENIS_BATERAI)
                setupSpinnerAdapter(view.findViewById(R.id.spMerkBaterai2), DropdownOptionsHelper.KEY_MERK_BATERAI)
                setupSpinnerAdapter(view.findViewById(R.id.spJumlahBattery2), DropdownOptionsHelper.KEY_JUMLAH_BATTERAI)
                setupSpinnerAdapter(view.findViewById(R.id.spBatteryRecti2), DropdownOptionsHelper.KEY_BALTING)

                setSpinnerSelectionByValue(view.findViewById(R.id.spTypeRectifier2), data.typeRectifier2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahModul2), data.jumlahModul2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahModulRusak2), data.jumlahModulRusak2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJenisBaterai2), data.jenisBaterai2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spMerkBaterai2), data.merkBaterai2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahBattery2), data.jumlahBatteryP2)
                setSpinnerSelectionByValue(view.findViewById(R.id.spBatteryRecti2), data.batteryRecti2)

                view.findViewById<TextInputEditText>(R.id.editBusVoltage2)?.setText(data.busVoltage2)
                view.findViewById<TextInputEditText>(R.id.editLoadA2)?.setText(data.loadA2)
                view.findViewById<TextInputEditText>(R.id.editPerangkat2)?.setText(data.perangkatTerhubung2)
                view.findViewById<TextInputEditText>(R.id.editRemark2)?.setText(data.remark2)
            }
            "rectifier3" -> {
                setupSpinnerAdapter(view.findViewById(R.id.spTypeRectifier3), DropdownOptionsHelper.KEY_TYPE_RECTIFIER)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spJumlahModul3), R.array.jumlah_modul_options)
                setupSpinnerAdapterFromRes(view.findViewById(R.id.spJumlahModulRusak3), R.array.jumlah_modul_options)
                setupSpinnerAdapter(view.findViewById(R.id.spJumlahBattery3), DropdownOptionsHelper.KEY_JUMLAH_BATTERAI)
                setupSpinnerAdapter(view.findViewById(R.id.spBatteryRecti3), DropdownOptionsHelper.KEY_BALTING)

                setSpinnerSelectionByValue(view.findViewById(R.id.spTypeRectifier3), data.typeRectifier3)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahModul3), data.jumlahModul3)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahModulRusak3), data.jumlahModulRusak3)
                setSpinnerSelectionByValue(view.findViewById(R.id.spJumlahBattery3), data.jumlahBatteryP3)
                setSpinnerSelectionByValue(view.findViewById(R.id.spBatteryRecti3), data.batteryRecti3)

                view.findViewById<TextInputEditText>(R.id.editBusVoltage3)?.setText(data.busVoltage3)
                view.findViewById<TextInputEditText>(R.id.editLoadA3)?.setText(data.loadA3)
                view.findViewById<TextInputEditText>(R.id.editPerangkat3)?.setText(data.perangkatTerhubung3)
            }
        }
    }

    private fun saveDialogFields(formView: View, section: String) {
        when (section) {
            "informasi_site" -> {
                data.siteId = formView.findViewById<TextInputEditText>(R.id.editSiteId)?.text.toString().trim()
                data.siteName = formView.findViewById<TextInputEditText>(R.id.editSiteName)?.text.toString().trim()
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
                data.typeRectifier1 = formView.findViewById<Spinner>(R.id.spTypeRectifier1)?.selectedItem?.toString() ?: ""
                data.jumlahModul1 = formView.findViewById<Spinner>(R.id.spJumlahModul1)?.selectedItem?.toString() ?: ""
                data.jumlahModulRusak1 = formView.findViewById<Spinner>(R.id.spJumlahModulRusak1)?.selectedItem?.toString() ?: ""
                data.jenisBaterai1 = formView.findViewById<Spinner>(R.id.spJenisBaterai1)?.selectedItem?.toString() ?: ""
                data.merkBaterai1 = formView.findViewById<Spinner>(R.id.spMerkBaterai1)?.selectedItem?.toString() ?: ""
                data.jumlahBatteryP1 = formView.findViewById<Spinner>(R.id.spJumlahBattery1)?.selectedItem?.toString() ?: ""
                data.batteryRecti1 = formView.findViewById<Spinner>(R.id.spBatteryRecti1)?.selectedItem?.toString() ?: ""
                data.busVoltage1 = formView.findViewById<TextInputEditText>(R.id.editBusVoltage1)?.text.toString().trim()
                data.loadA1 = formView.findViewById<TextInputEditText>(R.id.editLoadA1)?.text.toString().trim()
                data.perangkatTerhubung1 = formView.findViewById<TextInputEditText>(R.id.editPerangkat1)?.text.toString().trim()
                data.remark1 = formView.findViewById<TextInputEditText>(R.id.editRemark1)?.text.toString().trim()
            }
            "rectifier2" -> {
                data.typeRectifier2 = formView.findViewById<Spinner>(R.id.spTypeRectifier2)?.selectedItem?.toString() ?: ""
                data.jumlahModul2 = formView.findViewById<Spinner>(R.id.spJumlahModul2)?.selectedItem?.toString() ?: ""
                data.jumlahModulRusak2 = formView.findViewById<Spinner>(R.id.spJumlahModulRusak2)?.selectedItem?.toString() ?: ""
                data.jenisBaterai2 = formView.findViewById<Spinner>(R.id.spJenisBaterai2)?.selectedItem?.toString() ?: ""
                data.merkBaterai2 = formView.findViewById<Spinner>(R.id.spMerkBaterai2)?.selectedItem?.toString() ?: ""
                data.jumlahBatteryP2 = formView.findViewById<Spinner>(R.id.spJumlahBattery2)?.selectedItem?.toString() ?: ""
                data.batteryRecti2 = formView.findViewById<Spinner>(R.id.spBatteryRecti2)?.selectedItem?.toString() ?: ""
                data.busVoltage2 = formView.findViewById<TextInputEditText>(R.id.editBusVoltage2)?.text.toString().trim()
                data.loadA2 = formView.findViewById<TextInputEditText>(R.id.editLoadA2)?.text.toString().trim()
                data.perangkatTerhubung2 = formView.findViewById<TextInputEditText>(R.id.editPerangkat2)?.text.toString().trim()
                data.remark2 = formView.findViewById<TextInputEditText>(R.id.editRemark2)?.text.toString().trim()
            }
            "rectifier3" -> {
                data.typeRectifier3 = formView.findViewById<Spinner>(R.id.spTypeRectifier3)?.selectedItem?.toString() ?: ""
                data.jumlahModul3 = formView.findViewById<Spinner>(R.id.spJumlahModul3)?.selectedItem?.toString() ?: ""
                data.jumlahModulRusak3 = formView.findViewById<Spinner>(R.id.spJumlahModulRusak3)?.selectedItem?.toString() ?: ""
                data.jumlahBatteryP3 = formView.findViewById<Spinner>(R.id.spJumlahBattery3)?.selectedItem?.toString() ?: ""
                data.batteryRecti3 = formView.findViewById<Spinner>(R.id.spBatteryRecti3)?.selectedItem?.toString() ?: ""
                data.busVoltage3 = formView.findViewById<TextInputEditText>(R.id.editBusVoltage3)?.text.toString().trim()
                data.loadA3 = formView.findViewById<TextInputEditText>(R.id.editLoadA3)?.text.toString().trim()
                data.perangkatTerhubung3 = formView.findViewById<TextInputEditText>(R.id.editPerangkat3)?.text.toString().trim()
            }
        }
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
            for ((index, uri) in uncompressed) {
                try {
                    val b64 = ImageHelper.uriToCompressedBase64(this, uri)
                    if (b64 != null) {
                        imageBase64Caches[index] = b64
                    }
                } catch (_: Exception) { }
            }
            runOnUiThread { onSelesai() }
        }.start()
    }

    private fun lanjutkanKirimDataBaru() {
        pendingAction = "input"
        showLoadingOverlay("Menyimpan data...")
        binding.btnSimpanFinal.isEnabled = false

        val queue = Volley.newRequestQueue(this)

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

                if (error.cause is SocketTimeoutException) {
                    showTimeoutDialog()
                } else {
                    showErrorDialog(error.message ?: "Koneksi terputus")
                }
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return getSubmitParams()
            }
        }

        request.retryPolicy = DefaultRetryPolicy(60000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
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

        val queue = Volley.newRequestQueue(this)

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

                if (error.cause is SocketTimeoutException) {
                    showTimeoutDialog()
                } else {
                    showErrorDialog(error.message ?: "Koneksi terputus")
                }
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = getSubmitParams()
                params["action"] = "update"
                return params
            }
        }

        request.retryPolicy = DefaultRetryPolicy(60000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
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
            params["bus_voltage_2"] = data.busVoltage2
            params["load_a_2"] = data.loadA2
            params["perangkat_terhubung_2"] = data.perangkatTerhubung2
            params["remark_2"] = data.remark2
        }
        params["ada_recti_3"] = if (data.adaRecti3) "1" else "0"
        if (data.adaRecti3) {
            params["type_rectifier_3"] = data.typeRectifier3
            params["jumlah_modul_3"] = data.jumlahModul3
            params["jumlah_modul_rusak_3"] = data.jumlahModulRusak3
            params["jumlah_battery_p_3"] = data.jumlahBatteryP3
            params["battery_recti_3"] = data.batteryRecti3
            params["bus_voltage_3"] = data.busVoltage3
            params["load_a_3"] = data.loadA3
            params["perangkat_terhubung_3"] = data.perangkatTerhubung3
        }
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
