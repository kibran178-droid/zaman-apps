package com.example.zaman_apps.input_form

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.zaman_apps.R
import com.example.zaman_apps.databinding.ActivityInputanBinding
import com.example.zaman_apps.utils.DropdownOptionsHelper
import com.example.zaman_apps.utils.ImageCaptureHelper
import com.example.zaman_apps.utils.ImageHelper
import com.example.zaman_apps.utils.PerangkatTerhubungHelper
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial

class InputanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInputanBinding

    private var currentStep = 1
    private val totalSteps = 12

    // List foto URIs yang terpilih
    private val selectedImageUris = mutableListOf<Uri>()
    private var activeCameraSlotIndex = -1
    private var pendingCameraUri: Uri? = null

    private var adaGenset2 = false
    private var adaRecti2 = false
    private var adaRecti3 = false
    private var adaRecti4 = false
    private var adaRecti5 = false
    private var adaRecti6 = false
    private var adaRecti7 = false

    private lateinit var stepViews: List<View>

    private val launcherRingkasan = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val dataIntent = result.data
            if (dataIntent?.getBooleanExtra("RESET_FORM", false) == true) {
                finish()
            } else if (dataIntent?.hasExtra("UPDATED_SITE_DATA") == true) {
                val updatedData = IntentCompat.getParcelableExtra(
                    dataIntent, "UPDATED_SITE_DATA", SiteData::class.java
                )
                if (updatedData != null) {
                    populateFromSiteData(updatedData)
                }
            }
        }
    }

    // Multi-Select Gallery Launcher (sampai 10 foto)
    private val pickMultipleImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val spaceLeft = 10 - selectedImageUris.size
            if (spaceLeft > 0) {
                val toAdd = uris.take(spaceLeft)
                selectedImageUris.addAll(toAdd)
                updatePhotoSlotsUI()
                showSnackbar("Berhasil menambahkan ${toAdd.size} foto", isError = false)
            } else {
                showSnackbar("Maksimal 10 foto dokumentasi sudah terpenuhi!", isError = true)
            }
        }
    }

    // Single Image Replace Launcher (khusus tombol Ganti pada slot tertentu)
    private var activeSlotIndexToReplace = -1
    private val replaceSingleImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            if (activeSlotIndexToReplace in 0 until selectedImageUris.size) {
                selectedImageUris[activeSlotIndexToReplace] = it
                updatePhotoSlotsUI()
                showSnackbar("Foto #${activeSlotIndexToReplace + 1} berhasil diganti", isError = false)
            }
        }
    }

    // Single Camera Launcher
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingCameraUri != null) {
            if (activeCameraSlotIndex in 0 until selectedImageUris.size) {
                selectedImageUris[activeCameraSlotIndex] = pendingCameraUri!!
            } else if (selectedImageUris.size < 10) {
                selectedImageUris.add(pendingCameraUri!!)
            }
            updatePhotoSlotsUI()
            showSnackbar("Foto berhasil diambil", isError = false)
        }
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            bukaKamera()
        } else {
            showSnackbar("Izin kamera ditolak", isError = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityInputanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        selectedImageUris.clear()

        initViews()
        setupListeners()
        setupSpinners()
        updatePhotoSlotsUI()
        showStep(1, animate = false)
    }

    private fun initViews() {
        binding.toolbar.setNavigationOnClickListener { showKonfirmasiKeluarDialog() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showKonfirmasiKeluarDialog()
            }
        })

        stepViews = listOf(
            binding.step1,
            binding.step3,
            binding.step4,
            binding.stepGenset,
            binding.step5,
            binding.step6,
            binding.step7,
            binding.step8Recti4,
            binding.step9Recti5,
            binding.step10Recti6,
            binding.step11Recti7,
            binding.step8
        )
    }

    private fun showKonfirmasiKeluarDialog() {
        AlertDialog.Builder(this)
            .setTitle("Batalkan Input Data?")
            .setMessage("Data yang sudah diisi atau foto yang sudah dipilih akan hilang jika Anda keluar.\n\nYakin ingin membatalkan dan kembali ke menu utama?")
            .setPositiveButton("Ya, Keluar") { _, _ -> finish() }
            .setNegativeButton("Lanjutkan Input", null)
            .show()
    }

    private fun setupListeners() {
        binding.btnNext.setOnClickListener { onNextClicked() }
        binding.btnBack.setOnClickListener { onBackClicked() }

        binding.switchAdaGenset2.setOnCheckedChangeListener { _, isChecked ->
            adaGenset2 = isChecked
            binding.layoutGenset2Container.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.switchAdaRecti2.setOnCheckedChangeListener { _, isChecked -> adaRecti2 = isChecked }
        binding.switchAdaRecti3.setOnCheckedChangeListener { _, isChecked -> adaRecti3 = isChecked }
        binding.switchAdaRecti4.setOnCheckedChangeListener { _, isChecked -> adaRecti4 = isChecked }
        binding.switchAdaRecti5.setOnCheckedChangeListener { _, isChecked -> adaRecti5 = isChecked }
        binding.switchAdaRecti6.setOnCheckedChangeListener { _, isChecked -> adaRecti6 = isChecked }
        binding.switchAdaRecti7.setOnCheckedChangeListener { _, isChecked -> adaRecti7 = isChecked }

        binding.switchStatusBatteryNotAktif1.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif1.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif2.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif2.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif3.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif3.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif4.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif4.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif5.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif5.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif6.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif6.visibility = if (isChecked) View.VISIBLE else View.GONE }
        binding.switchStatusBatteryNotAktif7.setOnCheckedChangeListener { _, isChecked -> binding.layoutJumlahBankNotAktif7.visibility = if (isChecked) View.VISIBLE else View.GONE }

        fun setupPerangkatClick(editText: EditText, rectiNum: Int) {
            editText.isFocusable = false
            editText.isClickable = true
            editText.setOnClickListener {
                PerangkatTerhubungHelper.showDialog(this, rectiNum, editText.text.toString().trim()) { selected ->
                    editText.setText(selected)
                }
            }
        }

        setupPerangkatClick(binding.etPerangkat1, 1)
        setupPerangkatClick(binding.etPerangkat2, 2)
        setupPerangkatClick(binding.etPerangkat3, 3)
        setupPerangkatClick(binding.etPerangkat4, 4)
        setupPerangkatClick(binding.etPerangkat5, 5)
        setupPerangkatClick(binding.etPerangkat6, 6)
        setupPerangkatClick(binding.etPerangkat7, 7)

        binding.btnPilihFotoMulti.setOnClickListener {
            if (selectedImageUris.size >= 10) {
                showSnackbar("Maksimal 10 foto telah terpilih!", isError = true)
                return@setOnClickListener
            }
            pickMultipleImagesLauncher.launch("image/*")
        }

        binding.btnAmbilFotoKamera.setOnClickListener {
            if (selectedImageUris.size >= 10) {
                showSnackbar("Maksimal 10 foto telah terpilih!", isError = true)
                return@setOnClickListener
            }
            activeCameraSlotIndex = -1
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                bukaKamera()
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnResetFoto.setOnClickListener {
            if (selectedImageUris.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Reset Foto")
                .setMessage("Yakin ingin menghapus seluruh foto terpilih?")
                .setPositiveButton("Hapus") { _, _ ->
                    selectedImageUris.clear()
                    updatePhotoSlotsUI()
                    showSnackbar("Seluruh foto telah di-reset", isError = false)
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun bukaKamera() {
        pendingCameraUri = ImageCaptureHelper.buatUriFotoBaru(this)
        pendingCameraUri?.let { takePictureLauncher.launch(it) }
    }

    private fun updatePhotoSlotsUI() {
        val count = selectedImageUris.size
        binding.tvCountFoto.text = "$count / 10"

        if (count in 3..10) {
            binding.tvCountFoto.setBackgroundResource(R.drawable.badge_welcome_bg)
            binding.tvCountFoto.setTextColor(Color.WHITE)
        } else {
            binding.tvCountFoto.setBackgroundResource(R.drawable.bg_badge)
            binding.tvCountFoto.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        }

        binding.containerSlotsFoto.removeAllViews()

        if (selectedImageUris.isEmpty()) {
            val tvEmpty = TextView(this)
            tvEmpty.text = "Belum ada foto terpilih (minimal 3 foto wajib terpilih). Gunakan tombol 'Pilih Foto' atau 'Ambil Kamera'."
            tvEmpty.textSize = 13f
            tvEmpty.gravity = Gravity.CENTER
            tvEmpty.setPadding(32, 48, 32, 48)
            tvEmpty.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.containerSlotsFoto.addView(tvEmpty)
            return
        }

        selectedImageUris.forEachIndexed { index, uri ->
            val slotCard = buatCardSlotFoto(index + 1, uri)
            binding.containerSlotsFoto.addView(slotCard)
        }
    }

    private fun buatCardSlotFoto(slotNumber: Int, uri: Uri): View {
        val card = MaterialCardView(this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 }
        card.layoutParams = params
        card.radius = 16f
        card.cardElevation = 2f
        card.strokeWidth = 1
        card.strokeColor = ContextCompat.getColor(this, R.color.brand_primary)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.HORIZONTAL
        root.gravity = Gravity.CENTER_VERTICAL
        root.setPadding(20, 16, 20, 16)

        val ivThumb = ImageView(this)
        val thumbParams = LinearLayout.LayoutParams(110, 110)
        ivThumb.layoutParams = thumbParams
        ivThumb.scaleType = ImageView.ScaleType.CENTER_CROP

        val thumb = ImageHelper.decodeThumbnailFromUri(this, uri, 150, 150)
        if (thumb != null) {
            ivThumb.setImageBitmap(thumb)
        } else {
            ivThumb.setImageURI(uri)
        }
        root.addView(ivThumb)

        val infoCol = LinearLayout(this)
        infoCol.orientation = LinearLayout.VERTICAL
        infoCol.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 20
        }

        val tvTitle = TextView(this)
        tvTitle.text = "Foto Dokumentasi #$slotNumber"
        tvTitle.textSize = 14f
        tvTitle.setTypeface(null, Typeface.BOLD)
        tvTitle.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        infoCol.addView(tvTitle)

        val tvStatus = TextView(this)
        tvStatus.text = "✓ Terisi"
        tvStatus.textSize = 12f
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        tvStatus.setPadding(0, 4, 0, 0)
        infoCol.addView(tvStatus)

        root.addView(infoCol)

        val btnAksi = TextView(this)
        btnAksi.text = "✏️ Ganti"
        btnAksi.textSize = 12f
        btnAksi.setTypeface(null, Typeface.BOLD)
        btnAksi.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        btnAksi.setPadding(16, 12, 16, 12)
        btnAksi.setOnClickListener {
            dialogPilihFotoSlot(slotNumber - 1)
        }
        root.addView(btnAksi)

        val btnHapus = TextView(this)
        btnHapus.text = "🗑️ Hapus"
        btnHapus.textSize = 12f
        btnHapus.setTypeface(null, Typeface.BOLD)
        btnHapus.setTextColor(ContextCompat.getColor(this, R.color.error))
        btnHapus.setPadding(16, 12, 16, 12)
        btnHapus.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Hapus Foto Dokumentasi #$slotNumber?")
                .setMessage("Yakin ingin menghapus foto dokumentasi ke-$slotNumber ini?")
                .setPositiveButton("Hapus") { _, _ ->
                    if (slotNumber - 1 in 0 until selectedImageUris.size) {
                        selectedImageUris.removeAt(slotNumber - 1)
                        updatePhotoSlotsUI()
                        showSnackbar("Foto #$slotNumber berhasil dihapus", isError = false)
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
        root.addView(btnHapus)

        card.addView(root)
        return card
    }

    private fun dialogPilihFotoSlot(slotIndex: Int) {
        val opsi = arrayOf("📷 Ambil dari Kamera", "🖼️ Pilih dari Galeri")
        AlertDialog.Builder(this)
            .setTitle("Ganti Foto #${slotIndex + 1}")
            .setItems(opsi) { _, index ->
                when (index) {
                    0 -> {
                        activeCameraSlotIndex = slotIndex
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            bukaKamera()
                        } else {
                            requestCameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> {
                        activeSlotIndexToReplace = slotIndex
                        replaceSingleImageLauncher.launch("image/*")
                    }
                }
            }
            .show()
    }

    private fun showStep(step: Int, animate: Boolean = true) {
        currentStep = step

        binding.tvStepIndicator.text = "Langkah $currentStep dari $totalSteps"
        binding.progressStep.max = totalSteps
        binding.progressStep.progress = currentStep

        stepViews.forEachIndexed { index, view ->
            val stepNum = index + 1
            if (stepNum == currentStep) {
                view.visibility = View.VISIBLE
                if (animate) {
                    view.alpha = 0f
                    view.translationX = 100f
                    view.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(400)
                        .setInterpolator(OvershootInterpolator(1.0f))
                        .start()
                }
            } else {
                view.visibility = View.GONE
            }
        }

        binding.btnBack.visibility = if (currentStep > 1) View.VISIBLE else View.INVISIBLE
        binding.btnNext.text = if (currentStep == totalSteps) "TINJAU & SIMPAN" else "LANJUTKAN"

        if (currentStep == totalSteps) {
            tampilkanPratinjauRingkasan()
        }
    }

    // ==================== MANDATORY VALIDATIONS ====================

    private fun validasiStep1(): Boolean {
        val siteId = binding.etSiteId.text.toString().trim()
        val siteName = binding.etSiteName.text.toString().trim()
        val idPelanggan = binding.etIdPelanggan.text.toString().trim()
        val mcbKwh = binding.spMcbKwh.selectedItem?.toString() ?: ""
        val daya = binding.etDaya.text.toString().trim()

        if (siteId.isEmpty()) {
            binding.siteIdLayout.error = "Site ID wajib diisi!"
            showSnackbar("Site ID tidak boleh kosong!", isError = true)
            binding.etSiteId.requestFocus()
            return false
        } else {
            binding.siteIdLayout.error = null
        }

        if (siteName.isEmpty()) {
            binding.siteNameLayout.error = "Nama Site wajib diisi!"
            showSnackbar("Nama Site tidak boleh kosong!", isError = true)
            binding.etSiteName.requestFocus()
            return false
        } else {
            binding.siteNameLayout.error = null
        }

        if (idPelanggan.isEmpty()) {
            showSnackbar("ID Pelanggan (PLN) wajib diisi!", isError = true)
            binding.etIdPelanggan.requestFocus()
            return false
        }

        if (mcbKwh.isBlank()) {
            showSnackbar("MCB kWh wajib dipilih!", isError = true)
            return false
        }

        if (daya.isEmpty()) {
            showSnackbar("Daya wajib diisi!", isError = true)
            binding.etDaya.requestFocus()
            return false
        }

        val photoCount = selectedImageUris.size
        if (photoCount < 3) {
            showSnackbar("Minimal 3 foto dokumentasi wajib dipilih! (Saat ini: $photoCount foto)", isError = true)
            return false
        }

        return true
    }

    private fun validasiStepEas(): Boolean {
        if ((binding.spMainsFail.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Mains Fail wajib dipilih!", isError = true); return false }
        if ((binding.spRectyFail.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Recty Fail wajib dipilih!", isError = true); return false }
        if ((binding.spStolen.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Stolen wajib dipilih!", isError = true); return false }
        if ((binding.spLowBatt.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Low Batt wajib dipilih!", isError = true); return false }
        if ((binding.spEngRun.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Eng Run wajib dipilih!", isError = true); return false }
        return true
    }

    private fun validasiStepListrik(): Boolean {
        if (binding.etRV.text.toString().trim().isEmpty()) { showSnackbar("Voltage R wajib diisi!", isError = true); binding.etRV.requestFocus(); return false }
        if (binding.etSV.text.toString().trim().isEmpty()) { showSnackbar("Voltage S wajib diisi!", isError = true); binding.etSV.requestFocus(); return false }
        if (binding.etTV.text.toString().trim().isEmpty()) { showSnackbar("Voltage T wajib diisi!", isError = true); binding.etTV.requestFocus(); return false }
        if (binding.etRA.text.toString().trim().isEmpty()) { showSnackbar("Arus R wajib diisi!", isError = true); binding.etRA.requestFocus(); return false }
        if (binding.etSA.text.toString().trim().isEmpty()) { showSnackbar("Arus S wajib diisi!", isError = true); binding.etSA.requestFocus(); return false }
        if (binding.etTA.text.toString().trim().isEmpty()) { showSnackbar("Arus T wajib diisi!", isError = true); binding.etTA.requestFocus(); return false }
        return true
    }

    private fun validasiStepGenset(): Boolean {
        if ((binding.spMerkGenset1.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Merk Genset 1 wajib dipilih!", isError = true); return false }
        if (binding.etCapacityGenset1.text.toString().trim().isEmpty()) { showSnackbar("Capacity Genset 1 wajib diisi!", isError = true); binding.etCapacityGenset1.requestFocus(); return false }
        if ((binding.spStatusGenset1.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Status Genset 1 wajib dipilih!", isError = true); return false }
        if (binding.etRemarkGenset1.text.toString().trim().isEmpty()) { showSnackbar("Catatan Genset 1 wajib diisi!", isError = true); binding.etRemarkGenset1.requestFocus(); return false }

        if (adaGenset2) {
            if ((binding.spMerkGenset2.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Merk Genset 2 wajib dipilih!", isError = true); return false }
            if (binding.etCapacityGenset2.text.toString().trim().isEmpty()) { showSnackbar("Capacity Genset 2 wajib diisi!", isError = true); binding.etCapacityGenset2.requestFocus(); return false }
            if ((binding.spStatusGenset2.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Status Genset 2 wajib dipilih!", isError = true); return false }
            if (binding.etRemarkGenset2.text.toString().trim().isEmpty()) { showSnackbar("Catatan Genset 2 wajib diisi!", isError = true); binding.etRemarkGenset2.requestFocus(); return false }
        }
        return true
    }

    private fun validasiRectiFull(
        rectiNum: Int,
        spType: Spinner, spMod: Spinner, spModRusak: Spinner,
        spJenisBat: Spinner, spMerkBat: Spinner, spJmlBat: Spinner,
        spBalting: Spinner, switchNotAktif: SwitchMaterial,
        spBankNotAktif: Spinner, etMcb: EditText,
        spArester: Spinner, etVolt: EditText,
        etLoad: EditText, etPerangkat: EditText,
        etRemark: EditText? = null
    ): Boolean {
        if ((spType.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Type Rectifier #$rectiNum wajib dipilih!", isError = true); return false }
        if ((spMod.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Jumlah Modul #$rectiNum wajib dipilih!", isError = true); return false }
        if ((spModRusak.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Modul Rusak #$rectiNum wajib dipilih!", isError = true); return false }
        if ((spJenisBat.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Jenis Baterai #$rectiNum wajib dipilih!", isError = true); return false }
        if ((spMerkBat.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Merk Baterai #$rectiNum wajib dipilih!", isError = true); return false }
        if ((spJmlBat.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Jumlah Battery #$rectiNum wajib dipilih!", isError = true); return false }
        if ((spBalting.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Battery Status #$rectiNum wajib dipilih!", isError = true); return false }

        if (switchNotAktif.isChecked) {
            if ((spBankNotAktif.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Jumlah Bank Not Aktif Recti #$rectiNum wajib dipilih!", isError = true); return false }
        }

        val mcbStr = etMcb.text.toString().trim()
        if (mcbStr.isEmpty()) { showSnackbar("MCB Battery Recti #$rectiNum wajib diisi!", isError = true); etMcb.requestFocus(); return false }

        if ((spArester.selectedItem?.toString() ?: "").isBlank()) { showSnackbar("Kondisi Arester #$rectiNum wajib dipilih!", isError = true); return false }

        val voltStr = etVolt.text.toString().trim()
        if (voltStr.isEmpty()) { showSnackbar("Bus Voltage Recti #$rectiNum wajib diisi!", isError = true); etVolt.requestFocus(); return false }
        val volt = voltStr.replace(",", ".").toDoubleOrNull()
        if (volt == null || volt < 40.0 || volt > 60.0) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Bus Voltage Tidak Valid")
                .setMessage("Bus Voltage pada Recti #$rectiNum harus di antara 40 - 60 V.\n\nNilai diinput: $voltStr V")
                .setPositiveButton("Mengerti", null)
                .show()
            showSnackbar("Bus Voltage Recti #$rectiNum harus antara 40 - 60 V", isError = true)
            etVolt.requestFocus()
            return false
        }

        val loadStr = etLoad.text.toString().trim()
        if (loadStr.isEmpty()) { showSnackbar("Load (A) Recti #$rectiNum wajib diisi!", isError = true); etLoad.requestFocus(); return false }
        val load = loadStr.replace(",", ".").toDoubleOrNull()
        if (load == null || load < 0.0 || load > 200.0) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Load (A) Tidak Valid")
                .setMessage("Load (A) pada Recti #$rectiNum harus di antara 0 - 200 A.\n\nNilai diinput: $loadStr A")
                .setPositiveButton("Mengerti", null)
                .show()
            showSnackbar("Load Recti #$rectiNum harus antara 0 - 200 A", isError = true)
            etLoad.requestFocus()
            return false
        }

        val perangkatStr = etPerangkat.text.toString().trim()
        if (perangkatStr.isEmpty()) { showSnackbar("Perangkat Terhubung Recti #$rectiNum wajib dipilih!", isError = true); etPerangkat.requestFocus(); return false }

        if (etRemark != null && etRemark.text.toString().trim().isEmpty()) {
            showSnackbar("Catatan Recti #$rectiNum wajib diisi!", isError = true)
            etRemark.requestFocus()
            return false
        }

        return true
    }

    private fun onNextClicked() {
        when (currentStep) {
            1 -> if (!validasiStep1()) return
            2 -> if (!validasiStepEas()) return
            3 -> if (!validasiStepListrik()) return
            4 -> if (!validasiStepGenset()) return
            5 -> if (!validasiRectiFull(1, binding.spTypeRectifier1, binding.spJumlahModul1, binding.spJumlahModulRusak1, binding.spJenisBaterai1, binding.spMerkBaterai1, binding.spJumlahBattery1, binding.spBatteryRecti1, binding.switchStatusBatteryNotAktif1, binding.spJumlahBankNotAktif1, binding.etMcbBatteryRecti1, binding.spKondisiArester1, binding.etBusVoltage1, binding.etLoadA1, binding.etPerangkat1, binding.etRemark1)) return
            6 -> if (adaRecti2 && !validasiRectiFull(2, binding.spTypeRectifier2, binding.spJumlahModul2, binding.spJumlahModulRusak2, binding.spJenisBaterai2, binding.spMerkBaterai2, binding.spJumlahBattery2, binding.spBatteryRecti2, binding.switchStatusBatteryNotAktif2, binding.spJumlahBankNotAktif2, binding.etMcbBatteryRecti2, binding.spKondisiArester2, binding.etBusVoltage2, binding.etLoadA2, binding.etPerangkat2, binding.etRemark2)) return
            7 -> if (adaRecti3 && !validasiRectiFull(3, binding.spTypeRectifier3, binding.spJumlahModul3, binding.spJumlahModulRusak3, binding.spJenisBaterai3, binding.spMerkBaterai3, binding.spJumlahBattery3, binding.spBatteryRecti3, binding.switchStatusBatteryNotAktif3, binding.spJumlahBankNotAktif3, binding.etMcbBatteryRecti3, binding.spKondisiArester3, binding.etBusVoltage3, binding.etLoadA3, binding.etPerangkat3)) return
            8 -> if (adaRecti4 && !validasiRectiFull(4, binding.spTypeRectifier4, binding.spJumlahModul4, binding.spJumlahModulRusak4, binding.spJenisBaterai4, binding.spMerkBaterai4, binding.spJumlahBattery4, binding.spBatteryRecti4, binding.switchStatusBatteryNotAktif4, binding.spJumlahBankNotAktif4, binding.etMcbBatteryRecti4, binding.spKondisiArester4, binding.etBusVoltage4, binding.etLoadA4, binding.etPerangkat4)) return
            9 -> if (adaRecti5 && !validasiRectiFull(5, binding.spTypeRectifier5, binding.spJumlahModul5, binding.spJumlahModulRusak5, binding.spJenisBaterai5, binding.spMerkBaterai5, binding.spJumlahBattery5, binding.spBatteryRecti5, binding.switchStatusBatteryNotAktif5, binding.spJumlahBankNotAktif5, binding.etMcbBatteryRecti5, binding.spKondisiArester5, binding.etBusVoltage5, binding.etLoadA5, binding.etPerangkat5)) return
            10 -> if (adaRecti6 && !validasiRectiFull(6, binding.spTypeRectifier6, binding.spJumlahModul6, binding.spJumlahModulRusak6, binding.spJenisBaterai6, binding.spMerkBaterai6, binding.spJumlahBattery6, binding.spBatteryRecti6, binding.switchStatusBatteryNotAktif6, binding.spJumlahBankNotAktif6, binding.etMcbBatteryRecti6, binding.spKondisiArester6, binding.etBusVoltage6, binding.etLoadA6, binding.etPerangkat6)) return
            11 -> if (adaRecti7 && !validasiRectiFull(7, binding.spTypeRectifier7, binding.spJumlahModul7, binding.spJumlahModulRusak7, binding.spJenisBaterai7, binding.spMerkBaterai7, binding.spJumlahBattery7, binding.spBatteryRecti7, binding.switchStatusBatteryNotAktif7, binding.spJumlahBankNotAktif7, binding.etMcbBatteryRecti7, binding.spKondisiArester7, binding.etBusVoltage7, binding.etLoadA7, binding.etPerangkat7)) return
        }

        if (currentStep < totalSteps) {
            val next = getNextValidStep(currentStep)
            showStep(next)
        } else {
            lanjutKeHalamanRingkasan()
        }
    }

    private fun onBackClicked() {
        if (currentStep > 1) {
            val prev = getPrevValidStep(currentStep)
            showStep(prev)
        }
    }

    private fun getNextValidStep(from: Int): Int {
        return when (from) {
            5 -> if (adaRecti2) 6 else if (adaRecti3) 7 else if (adaRecti4) 8 else if (adaRecti5) 9 else if (adaRecti6) 10 else if (adaRecti7) 11 else 12
            6 -> if (adaRecti3) 7 else if (adaRecti4) 8 else if (adaRecti5) 9 else if (adaRecti6) 10 else if (adaRecti7) 11 else 12
            7 -> if (adaRecti4) 8 else if (adaRecti5) 9 else if (adaRecti6) 10 else if (adaRecti7) 11 else 12
            8 -> if (adaRecti5) 9 else if (adaRecti6) 10 else if (adaRecti7) 11 else 12
            9 -> if (adaRecti6) 10 else if (adaRecti7) 11 else 12
            10 -> if (adaRecti7) 11 else 12
            else -> from + 1
        }
    }

    private fun getPrevValidStep(from: Int): Int {
        return when (from) {
            12 -> if (adaRecti7) 11 else if (adaRecti6) 10 else if (adaRecti5) 9 else if (adaRecti4) 8 else if (adaRecti3) 7 else if (adaRecti2) 6 else 5
            11 -> if (adaRecti6) 10 else if (adaRecti5) 9 else if (adaRecti4) 8 else if (adaRecti3) 7 else if (adaRecti2) 6 else 5
            10 -> if (adaRecti5) 9 else if (adaRecti4) 8 else if (adaRecti3) 7 else if (adaRecti2) 6 else 5
            9 -> if (adaRecti4) 8 else if (adaRecti3) 7 else if (adaRecti2) 6 else 5
            8 -> if (adaRecti3) 7 else if (adaRecti2) 6 else 5
            7 -> if (adaRecti2) 6 else 5
            6 -> 5
            else -> from - 1
        }
    }

    private fun setupCascadingBateraiListeners(
        spJenis: Spinner,
        spMerk: Spinner,
        spJml: Spinner
    ) {
        spJenis.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val jenis = parent?.getItemAtPosition(position)?.toString() ?: ""
                val currentMerk = spMerk.selectedItem?.toString() ?: ""
                val currentJml = spJml.selectedItem?.toString() ?: ""

                val merkOpts = DropdownOptionsHelper.getMerkBateraiFiltered(this@InputanActivity, jenis)
                val jmlOpts = DropdownOptionsHelper.getJumlahBatteryFiltered(this@InputanActivity, jenis)

                val adapterMerk = ArrayAdapter(this@InputanActivity, android.R.layout.simple_spinner_dropdown_item, merkOpts)
                spMerk.adapter = adapterMerk
                val merkIdx = merkOpts.indexOfFirst { it.equals(currentMerk, ignoreCase = true) }
                if (merkIdx >= 0) spMerk.setSelection(merkIdx)

                val adapterJml = ArrayAdapter(this@InputanActivity, android.R.layout.simple_spinner_dropdown_item, jmlOpts)
                spJml.adapter = adapterJml
                val jmlIdx = jmlOpts.indexOfFirst { it.equals(currentJml, ignoreCase = true) }
                if (jmlIdx >= 0) spJml.setSelection(jmlIdx)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSpinners() {
        val selectedMcbKwh = binding.spMcbKwh.selectedItem?.toString()
        val selectedMainsFail = binding.spMainsFail.selectedItem?.toString()
        val selectedRectyFail = binding.spRectyFail.selectedItem?.toString()
        val selectedStolen = binding.spStolen.selectedItem?.toString()
        val selectedLowBatt = binding.spLowBatt.selectedItem?.toString()
        val selectedEngRun = binding.spEngRun.selectedItem?.toString()

        val selectedMerkGenset1 = binding.spMerkGenset1.selectedItem?.toString()
        val selectedStatusGenset1 = binding.spStatusGenset1.selectedItem?.toString()
        val selectedMerkGenset2 = binding.spMerkGenset2.selectedItem?.toString()
        val selectedStatusGenset2 = binding.spStatusGenset2.selectedItem?.toString()

        val optionsEas = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_EAS)
        val adapterStatus = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsEas)

        binding.spMainsFail.adapter = adapterStatus
        binding.spRectyFail.adapter = adapterStatus
        binding.spStolen.adapter = adapterStatus
        binding.spLowBatt.adapter = adapterStatus
        binding.spEngRun.adapter = adapterStatus

        val optionsMcbKwh = ArrayAdapter.createFromResource(this, R.array.mcb_kwh_options, android.R.layout.simple_spinner_dropdown_item)
        binding.spMcbKwh.adapter = optionsMcbKwh

        val optionsMerkGenset = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_MERK_GENSET)
        val adapterMerkGenset = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsMerkGenset)
        binding.spMerkGenset1.adapter = adapterMerkGenset
        binding.spMerkGenset2.adapter = adapterMerkGenset

        val optionsStatusGenset = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_STATUS_GENSET)
        val adapterStatusGenset = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsStatusGenset)
        binding.spStatusGenset1.adapter = adapterStatusGenset
        binding.spStatusGenset2.adapter = adapterStatusGenset

        val optionsTypeRecti = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_TYPE_RECTIFIER)
        val adapterTypeRecti = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsTypeRecti)
        val adapterAngka = ArrayAdapter.createFromResource(this, R.array.jumlah_modul_options, android.R.layout.simple_spinner_dropdown_item)
        val optionsJenisBat = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_JENIS_BATERAI)
        val adapterJenisBat = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsJenisBat)
        val optionsBalting = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_BALTING)
        val adapterBalting = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsBalting)
        val adapterArester = ArrayAdapter.createFromResource(this, R.array.arester_options, android.R.layout.simple_spinner_dropdown_item)
        val optionsBankNotAktif = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_JUMLAH_BANK_NOT_AKTIF)
        val adapterBankNotAktif = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsBankNotAktif)

        fun setupRectiSpinners(spType: Spinner, spMod: Spinner, spModRusak: Spinner, spBalting: Spinner, spArester: Spinner, spNotAktif: Spinner) {
            spType.adapter = adapterTypeRecti
            spMod.adapter = adapterAngka
            spModRusak.adapter = adapterAngka
            spBalting.adapter = adapterBalting
            spArester.adapter = adapterArester
            spNotAktif.adapter = adapterBankNotAktif
        }

        binding.spJenisBaterai1.adapter = adapterJenisBat
        binding.spJenisBaterai2.adapter = adapterJenisBat
        binding.spJenisBaterai3.adapter = adapterJenisBat
        binding.spJenisBaterai4.adapter = adapterJenisBat
        binding.spJenisBaterai5.adapter = adapterJenisBat
        binding.spJenisBaterai6.adapter = adapterJenisBat
        binding.spJenisBaterai7.adapter = adapterJenisBat

        setupRectiSpinners(binding.spTypeRectifier1, binding.spJumlahModul1, binding.spJumlahModulRusak1, binding.spBatteryRecti1, binding.spKondisiArester1, binding.spJumlahBankNotAktif1)
        setupRectiSpinners(binding.spTypeRectifier2, binding.spJumlahModul2, binding.spJumlahModulRusak2, binding.spBatteryRecti2, binding.spKondisiArester2, binding.spJumlahBankNotAktif2)
        setupRectiSpinners(binding.spTypeRectifier3, binding.spJumlahModul3, binding.spJumlahModulRusak3, binding.spBatteryRecti3, binding.spKondisiArester3, binding.spJumlahBankNotAktif3)
        setupRectiSpinners(binding.spTypeRectifier4, binding.spJumlahModul4, binding.spJumlahModulRusak4, binding.spBatteryRecti4, binding.spKondisiArester4, binding.spJumlahBankNotAktif4)
        setupRectiSpinners(binding.spTypeRectifier5, binding.spJumlahModul5, binding.spJumlahModulRusak5, binding.spBatteryRecti5, binding.spKondisiArester5, binding.spJumlahBankNotAktif5)
        setupRectiSpinners(binding.spTypeRectifier6, binding.spJumlahModul6, binding.spJumlahModulRusak6, binding.spBatteryRecti6, binding.spKondisiArester6, binding.spJumlahBankNotAktif6)
        setupRectiSpinners(binding.spTypeRectifier7, binding.spJumlahModul7, binding.spJumlahModulRusak7, binding.spBatteryRecti7, binding.spKondisiArester7, binding.spJumlahBankNotAktif7)

        setupCascadingBateraiListeners(binding.spJenisBaterai1, binding.spMerkBaterai1, binding.spJumlahBattery1)
        setupCascadingBateraiListeners(binding.spJenisBaterai2, binding.spMerkBaterai2, binding.spJumlahBattery2)
        setupCascadingBateraiListeners(binding.spJenisBaterai3, binding.spMerkBaterai3, binding.spJumlahBattery3)
        setupCascadingBateraiListeners(binding.spJenisBaterai4, binding.spMerkBaterai4, binding.spJumlahBattery4)
        setupCascadingBateraiListeners(binding.spJenisBaterai5, binding.spMerkBaterai5, binding.spJumlahBattery5)
        setupCascadingBateraiListeners(binding.spJenisBaterai6, binding.spMerkBaterai6, binding.spJumlahBattery6)
        setupCascadingBateraiListeners(binding.spJenisBaterai7, binding.spMerkBaterai7, binding.spJumlahBattery7)

        selectedMcbKwh?.let { setSpinnerSelectionByValue(binding.spMcbKwh, it) }
        selectedMainsFail?.let { setSpinnerSelectionByValue(binding.spMainsFail, it) }
        selectedRectyFail?.let { setSpinnerSelectionByValue(binding.spRectyFail, it) }
        selectedStolen?.let { setSpinnerSelectionByValue(binding.spStolen, it) }
        selectedLowBatt?.let { setSpinnerSelectionByValue(binding.spLowBatt, it) }
        selectedEngRun?.let { setSpinnerSelectionByValue(binding.spEngRun, it) }

        selectedMerkGenset1?.let { setSpinnerSelectionByValue(binding.spMerkGenset1, it) }
        selectedStatusGenset1?.let { setSpinnerSelectionByValue(binding.spStatusGenset1, it) }
        selectedMerkGenset2?.let { setSpinnerSelectionByValue(binding.spMerkGenset2, it) }
        selectedStatusGenset2?.let { setSpinnerSelectionByValue(binding.spStatusGenset2, it) }
    }

    override fun onResume() {
        super.onResume()
        setupSpinners()
    }

    private fun buildSiteData(): SiteData {
        val listFotoUris = ArrayList<String>()
        selectedImageUris.forEach { uri ->
            listFotoUris.add(uri.toString())
        }

        return SiteData(
            siteId = binding.etSiteId.text.toString().trim(),
            siteName = binding.etSiteName.text.toString().trim(),
            idPelanggan = binding.etIdPelanggan.text.toString().trim(),
            mcbKwh = binding.spMcbKwh.selectedItem?.toString() ?: "",
            daya = binding.etDaya.text.toString().trim(),
            mainsFail = binding.spMainsFail.selectedItem?.toString() ?: "",
            rectyFail = binding.spRectyFail.selectedItem?.toString() ?: "",
            stolen = binding.spStolen.selectedItem?.toString() ?: "",
            lowBatt = binding.spLowBatt.selectedItem?.toString() ?: "",
            engRun = binding.spEngRun.selectedItem?.toString() ?: "",
            rV = binding.etRV.text.toString().trim(),
            sV = binding.etSV.text.toString().trim(),
            tV = binding.etTV.text.toString().trim(),
            rA = binding.etRA.text.toString().trim(),
            sA = binding.etSA.text.toString().trim(),
            tA = binding.etTA.text.toString().trim(),
            merkGenset1 = binding.spMerkGenset1.selectedItem?.toString() ?: "",
            capacityGenset1 = binding.etCapacityGenset1.text.toString().trim(),
            statusGenset1 = binding.spStatusGenset1.selectedItem?.toString() ?: "",
            remarkGenset1 = binding.etRemarkGenset1.text.toString().trim(),
            merkGenset2 = if (adaGenset2) binding.spMerkGenset2.selectedItem?.toString() ?: "" else "",
            capacityGenset2 = if (adaGenset2) binding.etCapacityGenset2.text.toString().trim() else "",
            statusGenset2 = if (adaGenset2) binding.spStatusGenset2.selectedItem?.toString() ?: "" else "",
            remarkGenset2 = if (adaGenset2) binding.etRemarkGenset2.text.toString().trim() else "",
            adaGenset2 = adaGenset2,

            typeRectifier1 = binding.spTypeRectifier1.selectedItem?.toString() ?: "",
            jumlahModul1 = binding.spJumlahModul1.selectedItem?.toString() ?: "",
            jumlahModulRusak1 = binding.spJumlahModulRusak1.selectedItem?.toString() ?: "",
            jenisBaterai1 = binding.spJenisBaterai1.selectedItem?.toString() ?: "",
            merkBaterai1 = binding.spMerkBaterai1.selectedItem?.toString() ?: "",
            jumlahBatteryP1 = binding.spJumlahBattery1.selectedItem?.toString() ?: "",
            batteryRecti1 = binding.spBatteryRecti1.selectedItem?.toString() ?: "",
            statusBatteryNotAktif1 = binding.switchStatusBatteryNotAktif1.isChecked,
            jumlahBankNotAktif1 = if (binding.switchStatusBatteryNotAktif1.isChecked) binding.spJumlahBankNotAktif1.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti1 = binding.etMcbBatteryRecti1.text.toString().trim(),
            kondisiArester1 = binding.spKondisiArester1.selectedItem?.toString() ?: "",
            busVoltage1 = binding.etBusVoltage1.text.toString().trim(),
            loadA1 = binding.etLoadA1.text.toString().trim(),
            perangkatTerhubung1 = binding.etPerangkat1.text.toString().trim(),
            remark1 = binding.etRemark1.text.toString().trim(),

            typeRectifier2 = if (adaRecti2) binding.spTypeRectifier2.selectedItem?.toString() ?: "" else "",
            jumlahModul2 = if (adaRecti2) binding.spJumlahModul2.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak2 = if (adaRecti2) binding.spJumlahModulRusak2.selectedItem?.toString() ?: "" else "",
            jenisBaterai2 = if (adaRecti2) binding.spJenisBaterai2.selectedItem?.toString() ?: "" else "",
            merkBaterai2 = if (adaRecti2) binding.spMerkBaterai2.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP2 = if (adaRecti2) binding.spJumlahBattery2.selectedItem?.toString() ?: "" else "",
            batteryRecti2 = if (adaRecti2) binding.spBatteryRecti2.selectedItem?.toString() ?: "" else "",
            statusBatteryNotAktif2 = binding.switchStatusBatteryNotAktif2.isChecked,
            jumlahBankNotAktif2 = if (binding.switchStatusBatteryNotAktif2.isChecked) binding.spJumlahBankNotAktif2.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti2 = if (adaRecti2) binding.etMcbBatteryRecti2.text.toString().trim() else "",
            kondisiArester2 = if (adaRecti2) binding.spKondisiArester2.selectedItem?.toString() ?: "" else "",
            busVoltage2 = if (adaRecti2) binding.etBusVoltage2.text.toString().trim() else "",
            loadA2 = if (adaRecti2) binding.etLoadA2.text.toString().trim() else "",
            perangkatTerhubung2 = if (adaRecti2) binding.etPerangkat2.text.toString().trim() else "",
            remark2 = if (adaRecti2) binding.etRemark2.text.toString().trim() else "",

            typeRectifier3 = if (adaRecti3) binding.spTypeRectifier3.selectedItem?.toString() ?: "" else "",
            jumlahModul3 = if (adaRecti3) binding.spJumlahModul3.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak3 = if (adaRecti3) binding.spJumlahModulRusak3.selectedItem?.toString() ?: "" else "",
            jenisBaterai3 = if (adaRecti3) binding.spJenisBaterai3.selectedItem?.toString() ?: "" else "",
            merkBaterai3 = if (adaRecti3) binding.spMerkBaterai3.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP3 = if (adaRecti3) binding.spJumlahBattery3.selectedItem?.toString() ?: "" else "",
            batteryRecti3 = if (adaRecti3) binding.spBatteryRecti3.selectedItem?.toString() ?: "" else "",
            statusBatteryNotAktif3 = binding.switchStatusBatteryNotAktif3.isChecked,
            jumlahBankNotAktif3 = if (binding.switchStatusBatteryNotAktif3.isChecked) binding.spJumlahBankNotAktif3.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti3 = if (adaRecti3) binding.etMcbBatteryRecti3.text.toString().trim() else "",
            kondisiArester3 = if (adaRecti3) binding.spKondisiArester3.selectedItem?.toString() ?: "" else "",
            busVoltage3 = if (adaRecti3) binding.etBusVoltage3.text.toString().trim() else "",
            loadA3 = if (adaRecti3) binding.etLoadA3.text.toString().trim() else "",
            perangkatTerhubung3 = if (adaRecti3) binding.etPerangkat3.text.toString().trim() else "",

            typeRectifier4 = if (adaRecti4) binding.spTypeRectifier4.selectedItem?.toString() ?: "" else "",
            jumlahModul4 = if (adaRecti4) binding.spJumlahModul4.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak4 = if (adaRecti4) binding.spJumlahModulRusak4.selectedItem?.toString() ?: "" else "",
            jenisBaterai4 = if (adaRecti4) binding.spJenisBaterai4.selectedItem?.toString() ?: "" else "",
            merkBaterai4 = if (adaRecti4) binding.spMerkBaterai4.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP4 = if (adaRecti4) binding.spJumlahBattery4.selectedItem?.toString() ?: "" else "",
            batteryRecti4 = if (adaRecti4) binding.spBatteryRecti4.selectedItem?.toString() ?: "" else "",
            statusBatteryNotAktif4 = binding.switchStatusBatteryNotAktif4.isChecked,
            jumlahBankNotAktif4 = if (binding.switchStatusBatteryNotAktif4.isChecked) binding.spJumlahBankNotAktif4.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti4 = if (adaRecti4) binding.etMcbBatteryRecti4.text.toString().trim() else "",
            kondisiArester4 = if (adaRecti4) binding.spKondisiArester4.selectedItem?.toString() ?: "" else "",
            busVoltage4 = if (adaRecti4) binding.etBusVoltage4.text.toString().trim() else "",
            loadA4 = if (adaRecti4) binding.etLoadA4.text.toString().trim() else "",
            perangkatTerhubung4 = if (adaRecti4) binding.etPerangkat4.text.toString().trim() else "",

            typeRectifier5 = if (adaRecti5) binding.spTypeRectifier5.selectedItem?.toString() ?: "" else "",
            jumlahModul5 = if (adaRecti5) binding.spJumlahModul5.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak5 = if (adaRecti5) binding.spJumlahModulRusak5.selectedItem?.toString() ?: "" else "",
            jenisBaterai5 = if (adaRecti5) binding.spJenisBaterai5.selectedItem?.toString() ?: "" else "",
            merkBaterai5 = if (adaRecti5) binding.spMerkBaterai5.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP5 = if (adaRecti5) binding.spJumlahBattery5.selectedItem?.toString() ?: "" else "",
            batteryRecti5 = if (adaRecti5) binding.spBatteryRecti5.selectedItem?.toString() ?: "" else "",
            statusBatteryNotAktif5 = binding.switchStatusBatteryNotAktif5.isChecked,
            jumlahBankNotAktif5 = if (binding.switchStatusBatteryNotAktif5.isChecked) binding.spJumlahBankNotAktif5.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti5 = if (adaRecti5) binding.etMcbBatteryRecti5.text.toString().trim() else "",
            kondisiArester5 = if (adaRecti5) binding.spKondisiArester5.selectedItem?.toString() ?: "" else "",
            busVoltage5 = if (adaRecti5) binding.etBusVoltage5.text.toString().trim() else "",
            loadA5 = if (adaRecti5) binding.etLoadA5.text.toString().trim() else "",
            perangkatTerhubung5 = if (adaRecti5) binding.etPerangkat5.text.toString().trim() else "",

            typeRectifier6 = if (adaRecti6) binding.spTypeRectifier6.selectedItem?.toString() ?: "" else "",
            jumlahModul6 = if (adaRecti6) binding.spJumlahModul6.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak6 = if (adaRecti6) binding.spJumlahModulRusak6.selectedItem?.toString() ?: "" else "",
            jenisBaterai6 = if (adaRecti6) binding.spJenisBaterai6.selectedItem?.toString() ?: "" else "",
            merkBaterai6 = if (adaRecti6) binding.spMerkBaterai6.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP6 = if (adaRecti6) binding.spJumlahBattery6.selectedItem?.toString() ?: "" else "",
            batteryRecti6 = if (adaRecti6) binding.spBatteryRecti6.selectedItem?.toString() ?: "" else "",
            statusBatteryNotAktif6 = binding.switchStatusBatteryNotAktif6.isChecked,
            jumlahBankNotAktif6 = if (binding.switchStatusBatteryNotAktif6.isChecked) binding.spJumlahBankNotAktif6.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti6 = if (adaRecti6) binding.etMcbBatteryRecti6.text.toString().trim() else "",
            kondisiArester6 = if (adaRecti6) binding.spKondisiArester6.selectedItem?.toString() ?: "" else "",
            busVoltage6 = if (adaRecti6) binding.etBusVoltage6.text.toString().trim() else "",
            loadA6 = if (adaRecti6) binding.etLoadA6.text.toString().trim() else "",
            perangkatTerhubung6 = if (adaRecti6) binding.etPerangkat6.text.toString().trim() else "",

            typeRectifier7 = if (adaRecti7) binding.spTypeRectifier7.selectedItem?.toString() ?: "" else "",
            jumlahModul7 = if (adaRecti7) binding.spJumlahModul7.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak7 = if (adaRecti7) binding.spJumlahModulRusak7.selectedItem?.toString() ?: "" else "",
            jenisBaterai7 = if (adaRecti7) binding.spJenisBaterai7.selectedItem?.toString() ?: "" else "",
            merkBaterai7 = if (adaRecti7) binding.spMerkBaterai7.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP7 = if (adaRecti7) binding.spJumlahBattery7.selectedItem?.toString() ?: "" else "",
            batteryRecti7 = if (adaRecti7) binding.spBatteryRecti7.selectedItem?.toString() ?: "" else "",
            statusBatteryNotAktif7 = binding.switchStatusBatteryNotAktif7.isChecked,
            jumlahBankNotAktif7 = if (binding.switchStatusBatteryNotAktif7.isChecked) binding.spJumlahBankNotAktif7.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti7 = if (adaRecti7) binding.etMcbBatteryRecti7.text.toString().trim() else "",
            kondisiArester7 = if (adaRecti7) binding.spKondisiArester7.selectedItem?.toString() ?: "" else "",
            busVoltage7 = if (adaRecti7) binding.etBusVoltage7.text.toString().trim() else "",
            loadA7 = if (adaRecti7) binding.etLoadA7.text.toString().trim() else "",
            perangkatTerhubung7 = if (adaRecti7) binding.etPerangkat7.text.toString().trim() else "",

            adaRecti2 = adaRecti2,
            adaRecti3 = adaRecti3,
            adaRecti4 = adaRecti4,
            adaRecti5 = adaRecti5,
            adaRecti6 = adaRecti6,
            adaRecti7 = adaRecti7,
            imageUris = listFotoUris
        )
    }

    private fun tampilkanPratinjauRingkasan() {
        val d = buildSiteData()
        fun valOrDash(v: String): String = if (v.isBlank()) "-" else v

        val text = StringBuilder()
        text.append("🏢 SITE ID: ${valOrDash(d.siteId)}\n")
        text.append("📍 Site Name: ${valOrDash(d.siteName)}\n")
        text.append("💳 ID Pelanggan (PLN): ${valOrDash(d.idPelanggan)}\n")
        text.append("⚡ MCB kWh: ${valOrDash(d.mcbKwh)}\n")
        text.append("⚡ Daya: ${valOrDash(d.daya)}\n")
        text.append("🖼️ Total Foto Dokumentasi: ${d.imageUris.size} / 10 foto terlampir\n\n")

        text.append("⚠️ Mains Fail: ${valOrDash(d.mainsFail)}\n")
        text.append("⚠️ Recty Fail: ${valOrDash(d.rectyFail)}\n")
        text.append("⚠️ Stolen: ${valOrDash(d.stolen)}\n")
        text.append("⚠️ Low Batt: ${valOrDash(d.lowBatt)}\n")
        text.append("⚠️ Eng Run: ${valOrDash(d.engRun)}\n\n")

        text.append("🔋 R (V): ${valOrDash(d.rV)}\n")
        text.append("🔋 S (V): ${valOrDash(d.sV)}\n")
        text.append("🔋 T (V): ${valOrDash(d.tV)}\n")
        text.append("⚡ R (A): ${valOrDash(d.rA)}\n")
        text.append("⚡ S (A): ${valOrDash(d.sA)}\n")
        text.append("⚡ T (A): ${valOrDash(d.tA)}\n\n")

        text.append("🔧 GENSET 1\n")
        text.append("🏷️ Merk: ${valOrDash(d.merkGenset1)}\n")
        text.append("⚡ Capacity: ${valOrDash(d.capacityGenset1)}\n")
        text.append("⚙️ Status: ${valOrDash(d.statusGenset1)}\n\n")

        if (d.adaGenset2) {
            text.append("🔧 GENSET 2\n")
            text.append("🏷️ Merk: ${valOrDash(d.merkGenset2)}\n")
            text.append("⚡ Capacity: ${valOrDash(d.capacityGenset2)}\n")
            text.append("⚙️ Status: ${valOrDash(d.statusGenset2)}\n\n")
        }

        fun appendRectiPreview(idx: Int, type: String, modul: String, modulRusak: String, jenisBat: String, merkBat: String, jmlBat: String, statusBat: String, mcbBat: String, arester: String, voltage: String, load: String, perangkat: String, notAktif: Boolean, jmlNotAktif: String) {
            text.append("📦 RECTI $idx\n")
            text.append("⚡ Type: ${valOrDash(type)}\n")
            text.append("🔢 Jumlah Modul: ${valOrDash(modul)}\n")
            text.append("🔢 Modul Rusak: ${valOrDash(modulRusak)}\n")
            if (jenisBat.isNotBlank()) text.append("🔋 Jenis Baterai: ${valOrDash(jenisBat)}\n")
            if (merkBat.isNotBlank()) text.append("🏷️ Merk Baterai: ${valOrDash(merkBat)}\n")
            text.append("🔢 Jumlah Battery: ${valOrDash(jmlBat)}\n")
            text.append("⚙️ Battery Status: ${valOrDash(statusBat)}\n")
            if (notAktif || jmlNotAktif.isNotBlank()) {
                text.append("⚠️ Status Battery Not Aktif: Ya (${valOrDash(jmlNotAktif)} Bank)\n")
            }
            text.append("🔌 MCB Battery Recty: ${valOrDash(mcbBat)}\n")
            text.append("⚡ Kondisi Arester: ${valOrDash(arester)}\n")
            text.append("🔌 Bus Voltage: ${valOrDash(voltage)}\n")
            text.append("⚙️ Load: ${valOrDash(load)}\n")
            text.append("📡 Perangkat: ${valOrDash(perangkat)}\n\n")
        }

        appendRectiPreview(1, d.typeRectifier1, d.jumlahModul1, d.jumlahModulRusak1, d.jenisBaterai1, d.merkBaterai1, d.jumlahBatteryP1, d.batteryRecti1, d.mcbBatteryRecti1, d.kondisiArester1, d.busVoltage1, d.loadA1, d.perangkatTerhubung1, d.statusBatteryNotAktif1, d.jumlahBankNotAktif1)
        if (d.adaRecti2) appendRectiPreview(2, d.typeRectifier2, d.jumlahModul2, d.jumlahModulRusak2, d.jenisBaterai2, d.merkBaterai2, d.jumlahBatteryP2, d.batteryRecti2, d.mcbBatteryRecti2, d.kondisiArester2, d.busVoltage2, d.loadA2, d.perangkatTerhubung2, d.statusBatteryNotAktif2, d.jumlahBankNotAktif2)
        if (d.adaRecti3) appendRectiPreview(3, d.typeRectifier3, d.jumlahModul3, d.jumlahModulRusak3, d.jenisBaterai3, d.merkBaterai3, d.jumlahBatteryP3, d.batteryRecti3, d.mcbBatteryRecti3, d.kondisiArester3, d.busVoltage3, d.loadA3, d.perangkatTerhubung3, d.statusBatteryNotAktif3, d.jumlahBankNotAktif3)
        if (d.adaRecti4) appendRectiPreview(4, d.typeRectifier4, d.jumlahModul4, d.jumlahModulRusak4, d.jenisBaterai4, d.merkBaterai4, d.jumlahBatteryP4, d.batteryRecti4, d.mcbBatteryRecti4, d.kondisiArester4, d.busVoltage4, d.loadA4, d.perangkatTerhubung4, d.statusBatteryNotAktif4, d.jumlahBankNotAktif4)
        if (d.adaRecti5) appendRectiPreview(5, d.typeRectifier5, d.jumlahModul5, d.jumlahModulRusak5, d.jenisBaterai5, d.merkBaterai5, d.jumlahBatteryP5, d.batteryRecti5, d.mcbBatteryRecti5, d.kondisiArester5, d.busVoltage5, d.loadA5, d.perangkatTerhubung5, d.statusBatteryNotAktif5, d.jumlahBankNotAktif5)
        if (d.adaRecti6) appendRectiPreview(6, d.typeRectifier6, d.jumlahModul6, d.jumlahModulRusak6, d.jenisBaterai6, d.merkBaterai6, d.jumlahBatteryP6, d.batteryRecti6, d.mcbBatteryRecti6, d.kondisiArester6, d.busVoltage6, d.loadA6, d.perangkatTerhubung6, d.statusBatteryNotAktif6, d.jumlahBankNotAktif6)
        if (d.adaRecti7) appendRectiPreview(7, d.typeRectifier7, d.jumlahModul7, d.jumlahModulRusak7, d.jenisBaterai7, d.merkBaterai7, d.jumlahBatteryP7, d.batteryRecti7, d.mcbBatteryRecti7, d.kondisiArester7, d.busVoltage7, d.loadA7, d.perangkatTerhubung7, d.statusBatteryNotAktif7, d.jumlahBankNotAktif7)

        binding.tvRingkasan.text = text.toString()
    }

    private fun lanjutKeHalamanRingkasan() {
        val intent = Intent(this, RingkasanActivity::class.java).apply {
            putExtra("SITE_DATA", buildSiteData())
        }
        launcherRingkasan.launch(intent)
    }

    private fun populateFromSiteData(d: SiteData) {
        binding.etSiteId.setText(d.siteId)
        binding.etSiteName.setText(d.siteName)
        binding.etIdPelanggan.setText(d.idPelanggan)
        setSpinnerSelectionByValue(binding.spMcbKwh, d.mcbKwh)
        binding.etDaya.setText(d.daya)

        setSpinnerSelectionByValue(binding.spMainsFail, d.mainsFail)
        setSpinnerSelectionByValue(binding.spRectyFail, d.rectyFail)
        setSpinnerSelectionByValue(binding.spStolen, d.stolen)
        setSpinnerSelectionByValue(binding.spLowBatt, d.lowBatt)
        setSpinnerSelectionByValue(binding.spEngRun, d.engRun)

        binding.etRV.setText(d.rV)
        binding.etSV.setText(d.sV)
        binding.etTV.setText(d.tV)
        binding.etRA.setText(d.rA)
        binding.etSA.setText(d.sA)
        binding.etTA.setText(d.tA)

        setSpinnerSelectionByValue(binding.spMerkGenset1, d.merkGenset1)
        binding.etCapacityGenset1.setText(d.capacityGenset1)
        setSpinnerSelectionByValue(binding.spStatusGenset1, d.statusGenset1)
        binding.etRemarkGenset1.setText(d.remarkGenset1)

        binding.switchAdaGenset2.isChecked = d.adaGenset2
        if (d.adaGenset2) {
            setSpinnerSelectionByValue(binding.spMerkGenset2, d.merkGenset2)
            binding.etCapacityGenset2.setText(d.capacityGenset2)
            setSpinnerSelectionByValue(binding.spStatusGenset2, d.statusGenset2)
            binding.etRemarkGenset2.setText(d.remarkGenset2)
        }

        adaRecti2 = d.adaRecti2
        adaRecti3 = d.adaRecti3
        adaRecti4 = d.adaRecti4
        adaRecti5 = d.adaRecti5
        adaRecti6 = d.adaRecti6
        adaRecti7 = d.adaRecti7

        binding.switchAdaRecti2.isChecked = d.adaRecti2
        binding.switchAdaRecti3.isChecked = d.adaRecti3
        binding.switchAdaRecti4.isChecked = d.adaRecti4
        binding.switchAdaRecti5.isChecked = d.adaRecti5
        binding.switchAdaRecti6.isChecked = d.adaRecti6
        binding.switchAdaRecti7.isChecked = d.adaRecti7

        fun populateRecti(idx: Int, type: String, modul: String, modulRusak: String, jenisBat: String, merkBat: String, jmlBat: String, statusBat: String, mcbBat: String, arester: String, voltage: String, load: String, perangkat: String, notAktif: Boolean, jmlNotAktif: String, remark: String? = null) {
            when (idx) {
                1 -> {
                    setSpinnerSelectionByValue(binding.spTypeRectifier1, type)
                    setSpinnerSelectionByValue(binding.spJumlahModul1, modul)
                    setSpinnerSelectionByValue(binding.spJumlahModulRusak1, modulRusak)
                    setSpinnerSelectionByValue(binding.spJenisBaterai1, jenisBat)
                    setSpinnerSelectionByValue(binding.spMerkBaterai1, merkBat)
                    setSpinnerSelectionByValue(binding.spJumlahBattery1, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti1, statusBat)
                    binding.switchStatusBatteryNotAktif1.isChecked = notAktif
                    binding.layoutJumlahBankNotAktif1.visibility = if (notAktif) View.VISIBLE else View.GONE
                    setSpinnerSelectionByValue(binding.spJumlahBankNotAktif1, jmlNotAktif)
                    setSpinnerSelectionByValue(binding.spKondisiArester1, arester)
                    binding.etMcbBatteryRecti1.setText(mcbBat)
                    binding.etBusVoltage1.setText(voltage)
                    binding.etLoadA1.setText(load)
                    binding.etPerangkat1.setText(perangkat)
                    if (remark != null) binding.etRemark1.setText(remark)
                }
                2 -> {
                    setSpinnerSelectionByValue(binding.spTypeRectifier2, type)
                    setSpinnerSelectionByValue(binding.spJumlahModul2, modul)
                    setSpinnerSelectionByValue(binding.spJumlahModulRusak2, modulRusak)
                    setSpinnerSelectionByValue(binding.spJenisBaterai2, jenisBat)
                    setSpinnerSelectionByValue(binding.spMerkBaterai2, merkBat)
                    setSpinnerSelectionByValue(binding.spJumlahBattery2, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti2, statusBat)
                    binding.switchStatusBatteryNotAktif2.isChecked = notAktif
                    binding.layoutJumlahBankNotAktif2.visibility = if (notAktif) View.VISIBLE else View.GONE
                    setSpinnerSelectionByValue(binding.spJumlahBankNotAktif2, jmlNotAktif)
                    setSpinnerSelectionByValue(binding.spKondisiArester2, arester)
                    binding.etMcbBatteryRecti2.setText(mcbBat)
                    binding.etBusVoltage2.setText(voltage)
                    binding.etLoadA2.setText(load)
                    binding.etPerangkat2.setText(perangkat)
                    if (remark != null) binding.etRemark2.setText(remark)
                }
                3 -> {
                    setSpinnerSelectionByValue(binding.spTypeRectifier3, type)
                    setSpinnerSelectionByValue(binding.spJumlahModul3, modul)
                    setSpinnerSelectionByValue(binding.spJumlahModulRusak3, modulRusak)
                    setSpinnerSelectionByValue(binding.spJenisBaterai3, jenisBat)
                    setSpinnerSelectionByValue(binding.spMerkBaterai3, merkBat)
                    setSpinnerSelectionByValue(binding.spJumlahBattery3, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti3, statusBat)
                    binding.switchStatusBatteryNotAktif3.isChecked = notAktif
                    binding.layoutJumlahBankNotAktif3.visibility = if (notAktif) View.VISIBLE else View.GONE
                    setSpinnerSelectionByValue(binding.spJumlahBankNotAktif3, jmlNotAktif)
                    setSpinnerSelectionByValue(binding.spKondisiArester3, arester)
                    binding.etMcbBatteryRecti3.setText(mcbBat)
                    binding.etBusVoltage3.setText(voltage)
                    binding.etLoadA3.setText(load)
                    binding.etPerangkat3.setText(perangkat)
                }
                4 -> {
                    setSpinnerSelectionByValue(binding.spTypeRectifier4, type)
                    setSpinnerSelectionByValue(binding.spJumlahModul4, modul)
                    setSpinnerSelectionByValue(binding.spJumlahModulRusak4, modulRusak)
                    setSpinnerSelectionByValue(binding.spJenisBaterai4, jenisBat)
                    setSpinnerSelectionByValue(binding.spMerkBaterai4, merkBat)
                    setSpinnerSelectionByValue(binding.spJumlahBattery4, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti4, statusBat)
                    binding.switchStatusBatteryNotAktif4.isChecked = notAktif
                    binding.layoutJumlahBankNotAktif4.visibility = if (notAktif) View.VISIBLE else View.GONE
                    setSpinnerSelectionByValue(binding.spJumlahBankNotAktif4, jmlNotAktif)
                    setSpinnerSelectionByValue(binding.spKondisiArester4, arester)
                    binding.etMcbBatteryRecti4.setText(mcbBat)
                    binding.etBusVoltage4.setText(voltage)
                    binding.etLoadA4.setText(load)
                    binding.etPerangkat4.setText(perangkat)
                }
                5 -> {
                    setSpinnerSelectionByValue(binding.spTypeRectifier5, type)
                    setSpinnerSelectionByValue(binding.spJumlahModul5, modul)
                    setSpinnerSelectionByValue(binding.spJumlahModulRusak5, modulRusak)
                    setSpinnerSelectionByValue(binding.spJenisBaterai5, jenisBat)
                    setSpinnerSelectionByValue(binding.spMerkBaterai5, merkBat)
                    setSpinnerSelectionByValue(binding.spJumlahBattery5, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti5, statusBat)
                    binding.switchStatusBatteryNotAktif5.isChecked = notAktif
                    binding.layoutJumlahBankNotAktif5.visibility = if (notAktif) View.VISIBLE else View.GONE
                    setSpinnerSelectionByValue(binding.spJumlahBankNotAktif5, jmlNotAktif)
                    setSpinnerSelectionByValue(binding.spKondisiArester5, arester)
                    binding.etMcbBatteryRecti5.setText(mcbBat)
                    binding.etBusVoltage5.setText(voltage)
                    binding.etLoadA5.setText(load)
                    binding.etPerangkat5.setText(perangkat)
                }
                6 -> {
                    setSpinnerSelectionByValue(binding.spTypeRectifier6, type)
                    setSpinnerSelectionByValue(binding.spJumlahModul6, modul)
                    setSpinnerSelectionByValue(binding.spJumlahModulRusak6, modulRusak)
                    setSpinnerSelectionByValue(binding.spJenisBaterai6, jenisBat)
                    setSpinnerSelectionByValue(binding.spMerkBaterai6, merkBat)
                    setSpinnerSelectionByValue(binding.spJumlahBattery6, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti6, statusBat)
                    binding.switchStatusBatteryNotAktif6.isChecked = notAktif
                    binding.layoutJumlahBankNotAktif6.visibility = if (notAktif) View.VISIBLE else View.GONE
                    setSpinnerSelectionByValue(binding.spJumlahBankNotAktif6, jmlNotAktif)
                    setSpinnerSelectionByValue(binding.spKondisiArester6, arester)
                    binding.etMcbBatteryRecti6.setText(mcbBat)
                    binding.etBusVoltage6.setText(voltage)
                    binding.etLoadA6.setText(load)
                    binding.etPerangkat6.setText(perangkat)
                }
                7 -> {
                    setSpinnerSelectionByValue(binding.spTypeRectifier7, type)
                    setSpinnerSelectionByValue(binding.spJumlahModul7, modul)
                    setSpinnerSelectionByValue(binding.spJumlahModulRusak7, modulRusak)
                    setSpinnerSelectionByValue(binding.spJenisBaterai7, jenisBat)
                    setSpinnerSelectionByValue(binding.spMerkBaterai7, merkBat)
                    setSpinnerSelectionByValue(binding.spJumlahBattery7, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti7, statusBat)
                    binding.switchStatusBatteryNotAktif7.isChecked = notAktif
                    binding.layoutJumlahBankNotAktif7.visibility = if (notAktif) View.VISIBLE else View.GONE
                    setSpinnerSelectionByValue(binding.spJumlahBankNotAktif7, jmlNotAktif)
                    setSpinnerSelectionByValue(binding.spKondisiArester7, arester)
                    binding.etMcbBatteryRecti7.setText(mcbBat)
                    binding.etBusVoltage7.setText(voltage)
                    binding.etLoadA7.setText(load)
                    binding.etPerangkat7.setText(perangkat)
                }
            }
        }

        populateRecti(1, d.typeRectifier1, d.jumlahModul1, d.jumlahModulRusak1, d.jenisBaterai1, d.merkBaterai1, d.jumlahBatteryP1, d.batteryRecti1, d.mcbBatteryRecti1, d.kondisiArester1, d.busVoltage1, d.loadA1, d.perangkatTerhubung1, d.statusBatteryNotAktif1, d.jumlahBankNotAktif1, d.remark1)
        if (d.adaRecti2) populateRecti(2, d.typeRectifier2, d.jumlahModul2, d.jumlahModulRusak2, d.jenisBaterai2, d.merkBaterai2, d.jumlahBatteryP2, d.batteryRecti2, d.mcbBatteryRecti2, d.kondisiArester2, d.busVoltage2, d.loadA2, d.perangkatTerhubung2, d.statusBatteryNotAktif2, d.jumlahBankNotAktif2, d.remark2)
        if (d.adaRecti3) populateRecti(3, d.typeRectifier3, d.jumlahModul3, d.jumlahModulRusak3, d.jenisBaterai3, d.merkBaterai3, d.jumlahBatteryP3, d.batteryRecti3, d.mcbBatteryRecti3, d.kondisiArester3, d.busVoltage3, d.loadA3, d.perangkatTerhubung3, d.statusBatteryNotAktif3, d.jumlahBankNotAktif3)
        if (d.adaRecti4) populateRecti(4, d.typeRectifier4, d.jumlahModul4, d.jumlahModulRusak4, d.jenisBaterai4, d.merkBaterai4, d.jumlahBatteryP4, d.batteryRecti4, d.mcbBatteryRecti4, d.kondisiArester4, d.busVoltage4, d.loadA4, d.perangkatTerhubung4, d.statusBatteryNotAktif4, d.jumlahBankNotAktif4)
        if (d.adaRecti5) populateRecti(5, d.typeRectifier5, d.jumlahModul5, d.jumlahModulRusak5, d.jenisBaterai5, d.merkBaterai5, d.jumlahBatteryP5, d.batteryRecti5, d.mcbBatteryRecti5, d.kondisiArester5, d.busVoltage5, d.loadA5, d.perangkatTerhubung5, d.statusBatteryNotAktif5, d.jumlahBankNotAktif5)
        if (d.adaRecti6) populateRecti(6, d.typeRectifier6, d.jumlahModul6, d.jumlahModulRusak6, d.jenisBaterai6, d.merkBaterai6, d.jumlahBatteryP6, d.batteryRecti6, d.mcbBatteryRecti6, d.kondisiArester6, d.busVoltage6, d.loadA6, d.perangkatTerhubung6, d.statusBatteryNotAktif6, d.jumlahBankNotAktif6)
        if (d.adaRecti7) populateRecti(7, d.typeRectifier7, d.jumlahModul7, d.jumlahModulRusak7, d.jenisBaterai7, d.merkBaterai7, d.jumlahBatteryP7, d.batteryRecti7, d.mcbBatteryRecti7, d.kondisiArester7, d.busVoltage7, d.loadA7, d.perangkatTerhubung7, d.statusBatteryNotAktif7, d.jumlahBankNotAktif7)

        selectedImageUris.clear()
        d.imageUris.forEach { uriStr ->
            try {
                selectedImageUris.add(Uri.parse(uriStr))
            } catch (_: Exception) { }
        }
        updatePhotoSlotsUI()
        tampilkanPratinjauRingkasan()
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

    private fun showSnackbar(message: String, isError: Boolean) {
        val snackbar = Snackbar.make(binding.main, message, Snackbar.LENGTH_LONG)
        snackbar.anchorView = binding.btnNext
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
