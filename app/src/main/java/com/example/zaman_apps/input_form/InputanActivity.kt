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
import android.widget.ArrayAdapter
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar

class InputanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInputanBinding

    private var currentStep = 1
    private val totalSteps = 8

    // List foto URIs yang benar-benar terpilih saja (dinamis)
    private val selectedImageUris = mutableListOf<Uri>()
    private var activeCameraSlotIndex = -1
    private var pendingCameraUri: Uri? = null

    private var adaGenset2 = false
    private var adaRecti2 = false
    private var adaRecti3 = false

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
                showSnackbar("Maksimal 10 foto dokumentasi sudah terpenuhi!", isError = false)
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

        binding.switchAdaRecti2.setOnCheckedChangeListener { _, isChecked ->
            adaRecti2 = isChecked
        }

        binding.switchAdaRecti3.setOnCheckedChangeListener { _, isChecked ->
            adaRecti3 = isChecked
        }

        binding.btnPilihFotoMulti.setOnClickListener {
            if (selectedImageUris.size >= 10) {
                showSnackbar("Maksimal 10 foto telah terpilih", isError = false)
                return@setOnClickListener
            }
            pickMultipleImagesLauncher.launch("image/*")
        }

        binding.btnAmbilFotoKamera.setOnClickListener {
            if (selectedImageUris.size >= 10) {
                showSnackbar("Maksimal 10 foto telah terpilih", isError = false)
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

        if (count == 10) {
            binding.tvCountFoto.setBackgroundResource(R.drawable.badge_welcome_bg)
            binding.tvCountFoto.setTextColor(Color.WHITE)
        } else {
            binding.tvCountFoto.setBackgroundResource(R.drawable.bg_badge)
            binding.tvCountFoto.setTextColor(ContextCompat.getColor(this, R.color.brand_primary))
        }

        binding.containerSlotsFoto.removeAllViews()

        if (selectedImageUris.isEmpty()) {
            val tvEmpty = TextView(this)
            tvEmpty.text = "Belum ada foto terpilih. Gunakan tombol 'Pilih Foto' atau 'Ambil Kamera' di atas."
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

        // Thumbnail Preview cepat tanpa lag
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

        // Info Slot
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

        // Action Ganti
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

        // Action Hapus dengan Alert
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
                        pickMultipleImagesLauncher.launch("image/*")
                    }
                }
            }
            .show()
    }

    private fun showStep(step: Int, animate: Boolean = true) {
        currentStep = step

        binding.tvStepIndicator.text = "Langkah $currentStep dari $totalSteps"
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

    private fun validasiLangkah1(): Boolean {
        val siteId = binding.etSiteId.text.toString().trim()
        val siteName = binding.etSiteName.text.toString().trim()

        if (siteId.isEmpty()) {
            binding.siteIdLayout.error = "Site ID wajib diisi!"
            showSnackbar("Site ID tidak boleh kosong", isError = true)
            binding.etSiteId.requestFocus()
            return false
        } else {
            binding.siteIdLayout.error = null
        }

        if (siteName.isEmpty()) {
            binding.siteNameLayout.error = "Nama Site wajib diisi!"
            showSnackbar("Nama Site tidak boleh kosong", isError = true)
            binding.etSiteName.requestFocus()
            return false
        } else {
            binding.siteNameLayout.error = null
        }

        val photoCount = selectedImageUris.size
        if (photoCount < 10) {
            showSnackbar("Informasi: Anda memilih $photoCount/10 foto (Opsional untuk testing)", isError = false)
        }

        return true
    }

    private fun onNextClicked() {
        if (currentStep == 1) {
            if (!validasiLangkah1()) return
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
            5 -> if (adaRecti2) 6 else if (adaRecti3) 7 else 8
            6 -> if (adaRecti3) 7 else 8
            else -> from + 1
        }
    }

    private fun getPrevValidStep(from: Int): Int {
        return when (from) {
            8 -> if (adaRecti3) 7 else if (adaRecti2) 6 else 5
            7 -> if (adaRecti2) 6 else 5
            6 -> 5
            else -> from - 1
        }
    }

    private fun setupSpinners() {
        val selectedMainsFail = binding.spMainsFail.selectedItem?.toString()
        val selectedRectyFail = binding.spRectyFail.selectedItem?.toString()
        val selectedStolen = binding.spStolen.selectedItem?.toString()
        val selectedLowBatt = binding.spLowBatt.selectedItem?.toString()
        val selectedEngRun = binding.spEngRun.selectedItem?.toString()

        val selectedMerkGenset1 = binding.spMerkGenset1.selectedItem?.toString()
        val selectedStatusGenset1 = binding.spStatusGenset1.selectedItem?.toString()
        val selectedMerkGenset2 = binding.spMerkGenset2.selectedItem?.toString()
        val selectedStatusGenset2 = binding.spStatusGenset2.selectedItem?.toString()

        val selectedTypeRecti1 = binding.spTypeRectifier1.selectedItem?.toString()
        val selectedJumlahModul1 = binding.spJumlahModul1.selectedItem?.toString()
        val selectedJumlahModulRusak1 = binding.spJumlahModulRusak1.selectedItem?.toString()
        val selectedJenisBat1 = binding.spJenisBaterai1.selectedItem?.toString()
        val selectedMerkBat1 = binding.spMerkBaterai1.selectedItem?.toString()
        val selectedJmlBat1 = binding.spJumlahBattery1.selectedItem?.toString()
        val selectedBalting1 = binding.spBatteryRecti1.selectedItem?.toString()

        val selectedTypeRecti2 = binding.spTypeRectifier2.selectedItem?.toString()
        val selectedJumlahModul2 = binding.spJumlahModul2.selectedItem?.toString()
        val selectedJumlahModulRusak2 = binding.spJumlahModulRusak2.selectedItem?.toString()
        val selectedJenisBat2 = binding.spJenisBaterai2.selectedItem?.toString()
        val selectedMerkBat2 = binding.spMerkBaterai2.selectedItem?.toString()
        val selectedJmlBat2 = binding.spJumlahBattery2.selectedItem?.toString()
        val selectedBalting2 = binding.spBatteryRecti2.selectedItem?.toString()

        val selectedTypeRecti3 = binding.spTypeRectifier3.selectedItem?.toString()
        val selectedJumlahModul3 = binding.spJumlahModul3.selectedItem?.toString()
        val selectedJumlahModulRusak3 = binding.spJumlahModulRusak3.selectedItem?.toString()
        val selectedJmlBat3 = binding.spJumlahBattery3.selectedItem?.toString()
        val selectedBalting3 = binding.spBatteryRecti3.selectedItem?.toString()

        val optionsEas = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_EAS)
        val adapterStatus = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsEas)

        binding.spMainsFail.adapter = adapterStatus
        binding.spRectyFail.adapter = adapterStatus
        binding.spStolen.adapter = adapterStatus
        binding.spLowBatt.adapter = adapterStatus
        binding.spEngRun.adapter = adapterStatus

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
        binding.spTypeRectifier1.adapter = adapterTypeRecti
        binding.spTypeRectifier2.adapter = adapterTypeRecti
        binding.spTypeRectifier3.adapter = adapterTypeRecti

        val adapterAngka = ArrayAdapter.createFromResource(this, R.array.jumlah_modul_options, android.R.layout.simple_spinner_dropdown_item)
        binding.spJumlahModul1.adapter = adapterAngka
        binding.spJumlahModulRusak1.adapter = adapterAngka
        binding.spJumlahModul2.adapter = adapterAngka
        binding.spJumlahModulRusak2.adapter = adapterAngka
        binding.spJumlahModul3.adapter = adapterAngka
        binding.spJumlahModulRusak3.adapter = adapterAngka

        val optionsJenisBat = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_JENIS_BATERAI)
        val adapterJenisBat = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsJenisBat)
        binding.spJenisBaterai1.adapter = adapterJenisBat
        binding.spJenisBaterai2.adapter = adapterJenisBat

        val optionsMerkBat = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_MERK_BATERAI)
        val adapterMerkBat = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsMerkBat)
        binding.spMerkBaterai1.adapter = adapterMerkBat
        binding.spMerkBaterai2.adapter = adapterMerkBat

        val optionsJmlBat = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_JUMLAH_BATTERAI)
        val adapterJmlBat = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsJmlBat)
        binding.spJumlahBattery1.adapter = adapterJmlBat
        binding.spJumlahBattery2.adapter = adapterJmlBat
        binding.spJumlahBattery3.adapter = adapterJmlBat

        val optionsBalting = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_BALTING)
        val adapterBalting = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsBalting)
        binding.spBatteryRecti1.adapter = adapterBalting
        binding.spBatteryRecti2.adapter = adapterBalting
        binding.spBatteryRecti3.adapter = adapterBalting

        selectedMainsFail?.let { setSpinnerSelectionByValue(binding.spMainsFail, it) }
        selectedRectyFail?.let { setSpinnerSelectionByValue(binding.spRectyFail, it) }
        selectedStolen?.let { setSpinnerSelectionByValue(binding.spStolen, it) }
        selectedLowBatt?.let { setSpinnerSelectionByValue(binding.spLowBatt, it) }
        selectedEngRun?.let { setSpinnerSelectionByValue(binding.spEngRun, it) }

        selectedMerkGenset1?.let { setSpinnerSelectionByValue(binding.spMerkGenset1, it) }
        selectedStatusGenset1?.let { setSpinnerSelectionByValue(binding.spStatusGenset1, it) }
        selectedMerkGenset2?.let { setSpinnerSelectionByValue(binding.spMerkGenset2, it) }
        selectedStatusGenset2?.let { setSpinnerSelectionByValue(binding.spStatusGenset2, it) }

        selectedTypeRecti1?.let { setSpinnerSelectionByValue(binding.spTypeRectifier1, it) }
        selectedJumlahModul1?.let { setSpinnerSelectionByValue(binding.spJumlahModul1, it) }
        selectedJumlahModulRusak1?.let { setSpinnerSelectionByValue(binding.spJumlahModulRusak1, it) }
        selectedJenisBat1?.let { setSpinnerSelectionByValue(binding.spJenisBaterai1, it) }
        selectedMerkBat1?.let { setSpinnerSelectionByValue(binding.spMerkBaterai1, it) }
        selectedJmlBat1?.let { setSpinnerSelectionByValue(binding.spJumlahBattery1, it) }
        selectedBalting1?.let { setSpinnerSelectionByValue(binding.spBatteryRecti1, it) }

        selectedTypeRecti2?.let { setSpinnerSelectionByValue(binding.spTypeRectifier2, it) }
        selectedJumlahModul2?.let { setSpinnerSelectionByValue(binding.spJumlahModul2, it) }
        selectedJumlahModulRusak2?.let { setSpinnerSelectionByValue(binding.spJumlahModulRusak2, it) }
        selectedJenisBat2?.let { setSpinnerSelectionByValue(binding.spJenisBaterai2, it) }
        selectedMerkBat2?.let { setSpinnerSelectionByValue(binding.spMerkBaterai2, it) }
        selectedJmlBat2?.let { setSpinnerSelectionByValue(binding.spJumlahBattery2, it) }
        selectedBalting2?.let { setSpinnerSelectionByValue(binding.spBatteryRecti2, it) }

        selectedTypeRecti3?.let { setSpinnerSelectionByValue(binding.spTypeRectifier3, it) }
        selectedJumlahModul3?.let { setSpinnerSelectionByValue(binding.spJumlahModul3, it) }
        selectedJumlahModulRusak3?.let { setSpinnerSelectionByValue(binding.spJumlahModulRusak3, it) }
        selectedJmlBat3?.let { setSpinnerSelectionByValue(binding.spJumlahBattery3, it) }
        selectedBalting3?.let { setSpinnerSelectionByValue(binding.spBatteryRecti3, it) }
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
            busVoltage2 = if (adaRecti2) binding.etBusVoltage2.text.toString().trim() else "",
            loadA2 = if (adaRecti2) binding.etLoadA2.text.toString().trim() else "",
            perangkatTerhubung2 = if (adaRecti2) binding.etPerangkat2.text.toString().trim() else "",
            remark2 = if (adaRecti2) binding.etRemark2.text.toString().trim() else "",
            adaRecti2 = adaRecti2,
            typeRectifier3 = if (adaRecti3) binding.spTypeRectifier3.selectedItem?.toString() ?: "" else "",
            jumlahModul3 = if (adaRecti3) binding.spJumlahModul3.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak3 = if (adaRecti3) binding.spJumlahModulRusak3.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP3 = if (adaRecti3) binding.spJumlahBattery3.selectedItem?.toString() ?: "" else "",
            batteryRecti3 = if (adaRecti3) binding.spBatteryRecti3.selectedItem?.toString() ?: "" else "",
            busVoltage3 = if (adaRecti3) binding.etBusVoltage3.text.toString().trim() else "",
            loadA3 = if (adaRecti3) binding.etLoadA3.text.toString().trim() else "",
            perangkatTerhubung3 = if (adaRecti3) binding.etPerangkat3.text.toString().trim() else "",
            adaRecti3 = adaRecti3,
            imageUris = listFotoUris
        )
    }

    private fun tampilkanPratinjauRingkasan() {
        val d = buildSiteData()
        fun valOrDash(v: String): String = if (v.isBlank()) "-" else v

        val text = StringBuilder()
        text.append("🏢 SITE ID: ${valOrDash(d.siteId)}\n")
        text.append("📍 Site Name: ${valOrDash(d.siteName)}\n")
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

        text.append("🔧 GENSET 2\n")
        text.append("🏷️ Merk: ${valOrDash(d.merkGenset2)}\n")
        text.append("⚡ Capacity: ${valOrDash(d.capacityGenset2)}\n")
        text.append("⚙️ Status: ${valOrDash(d.statusGenset2)}\n\n")

        text.append("📦 RECTI 1\n")
        text.append("⚡ Type: ${valOrDash(d.typeRectifier1)}\n")
        text.append("🔢 Jumlah Modul: ${valOrDash(d.jumlahModul1)}\n")
        text.append("🔢 Modul Rusak: ${valOrDash(d.jumlahModulRusak1)}\n")
        text.append("🔋 Jenis Baterai: ${valOrDash(d.jenisBaterai1)}\n")
        text.append("🏷️ Merk Baterai: ${valOrDash(d.merkBaterai1)}\n")
        text.append("🔢 Jumlah Battery: ${valOrDash(d.jumlahBatteryP1)}\n")
        text.append("🔌 Bus Voltage: ${valOrDash(d.busVoltage1)}\n")
        text.append("⚙️ Load: ${valOrDash(d.loadA1)}\n")
        text.append("📡 Perangkat: ${valOrDash(d.perangkatTerhubung1)}\n")
        text.append("📝 Remark: ${valOrDash(d.remark1)}")

        if (d.adaRecti2) {
            text.append("\n\n📦 RECTI 2\n")
            text.append("⚡ Type: ${valOrDash(d.typeRectifier2)}\n")
            text.append("🔢 Jumlah Modul: ${valOrDash(d.jumlahModul2)}\n")
            text.append("🔢 Modul Rusak: ${valOrDash(d.jumlahModulRusak2)}\n")
            text.append("🔋 Jenis Baterai: ${valOrDash(d.jenisBaterai2)}\n")
            text.append("🏷️ Merk Baterai: ${valOrDash(d.merkBaterai2)}\n")
            text.append("🔢 Jumlah Battery: ${valOrDash(d.jumlahBatteryP2)}\n")
            text.append("🔌 Bus Voltage: ${valOrDash(d.busVoltage2)}\n")
            text.append("⚙️ Load: ${valOrDash(d.loadA2)}\n")
            text.append("📡 Perangkat: ${valOrDash(d.perangkatTerhubung2)}\n")
            text.append("📝 Remark: ${valOrDash(d.remark2)}")
        }

        if (d.adaRecti3) {
            text.append("\n\n📦 RECTI 3\n")
            text.append("⚡ Type: ${valOrDash(d.typeRectifier3)}\n")
            text.append("🔢 Jumlah Modul: ${valOrDash(d.jumlahModul3)}\n")
            text.append("🔢 Modul Rusak: ${valOrDash(d.jumlahModulRusak3)}\n")
            text.append("🔢 Jumlah Battery: ${valOrDash(d.jumlahBatteryP3)}\n")
            text.append("🔌 Bus Voltage: ${valOrDash(d.busVoltage3)}\n")
            text.append("⚙️ Load: ${valOrDash(d.loadA3)}\n")
            text.append("📡 Perangkat: ${valOrDash(d.perangkatTerhubung3)}")
        }

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

        setSpinnerSelectionByValue(binding.spTypeRectifier1, d.typeRectifier1)
        setSpinnerSelectionByValue(binding.spJumlahModul1, d.jumlahModul1)
        setSpinnerSelectionByValue(binding.spJumlahModulRusak1, d.jumlahModulRusak1)
        setSpinnerSelectionByValue(binding.spJenisBaterai1, d.jenisBaterai1)
        setSpinnerSelectionByValue(binding.spMerkBaterai1, d.merkBaterai1)
        setSpinnerSelectionByValue(binding.spJumlahBattery1, d.jumlahBatteryP1)
        setSpinnerSelectionByValue(binding.spBatteryRecti1, d.batteryRecti1)
        binding.etBusVoltage1.setText(d.busVoltage1)
        binding.etLoadA1.setText(d.loadA1)
        binding.etPerangkat1.setText(d.perangkatTerhubung1)
        binding.etRemark1.setText(d.remark1)

        binding.switchAdaRecti2.isChecked = d.adaRecti2
        if (d.adaRecti2) {
            setSpinnerSelectionByValue(binding.spTypeRectifier2, d.typeRectifier2)
            setSpinnerSelectionByValue(binding.spJumlahModul2, d.jumlahModul2)
            setSpinnerSelectionByValue(binding.spJumlahModulRusak2, d.jumlahModulRusak2)
            setSpinnerSelectionByValue(binding.spJenisBaterai2, d.jenisBaterai2)
            setSpinnerSelectionByValue(binding.spMerkBaterai2, d.merkBaterai2)
            setSpinnerSelectionByValue(binding.spJumlahBattery2, d.jumlahBatteryP2)
            setSpinnerSelectionByValue(binding.spBatteryRecti2, d.batteryRecti2)
            binding.etBusVoltage2.setText(d.busVoltage2)
            binding.etLoadA2.setText(d.loadA2)
            binding.etPerangkat2.setText(d.perangkatTerhubung2)
            binding.etRemark2.setText(d.remark2)
        }

        binding.switchAdaRecti3.isChecked = d.adaRecti3
        if (d.adaRecti3) {
            setSpinnerSelectionByValue(binding.spTypeRectifier3, d.typeRectifier3)
            setSpinnerSelectionByValue(binding.spJumlahModul3, d.jumlahModul3)
            setSpinnerSelectionByValue(binding.spJumlahModulRusak3, d.jumlahModulRusak3)
            setSpinnerSelectionByValue(binding.spJumlahBattery3, d.jumlahBatteryP3)
            setSpinnerSelectionByValue(binding.spBatteryRecti3, d.batteryRecti3)
            binding.etBusVoltage3.setText(d.busVoltage3)
            binding.etLoadA3.setText(d.loadA3)
            binding.etPerangkat3.setText(d.perangkatTerhubung3)
        }

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
