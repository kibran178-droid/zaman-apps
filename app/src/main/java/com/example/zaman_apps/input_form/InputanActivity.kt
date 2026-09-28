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
    private val totalSteps = 12

    // List foto URIs yang benar-benar terpilih saja (dinamis)
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
                        pickMultipleImagesLauncher.launch("image/*")
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
        val adapterAngka = ArrayAdapter.createFromResource(this, R.array.jumlah_modul_options, android.R.layout.simple_spinner_dropdown_item)
        val optionsJenisBat = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_JENIS_BATERAI)
        val adapterJenisBat = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsJenisBat)
        val optionsMerkBat = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_MERK_BATERAI)
        val adapterMerkBat = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsMerkBat)
        val optionsJmlBat = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_JUMLAH_BATTERAI)
        val adapterJmlBat = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsJmlBat)
        val optionsBalting = DropdownOptionsHelper.getOptions(this, DropdownOptionsHelper.KEY_BALTING)
        val adapterBalting = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, optionsBalting)
        val adapterArester = ArrayAdapter.createFromResource(this, R.array.arester_options, android.R.layout.simple_spinner_dropdown_item)

        fun setupRectiSpinners(spType: Spinner, spMod: Spinner, spModRusak: Spinner, spJmlBat: Spinner, spBalting: Spinner, spArester: Spinner) {
            spType.adapter = adapterTypeRecti
            spMod.adapter = adapterAngka
            spModRusak.adapter = adapterAngka
            spJmlBat.adapter = adapterJmlBat
            spBalting.adapter = adapterBalting
            spArester.adapter = adapterArester
        }

        binding.spJenisBaterai1.adapter = adapterJenisBat
        binding.spMerkBaterai1.adapter = adapterMerkBat
        binding.spJenisBaterai2.adapter = adapterJenisBat
        binding.spMerkBaterai2.adapter = adapterMerkBat

        setupRectiSpinners(binding.spTypeRectifier1, binding.spJumlahModul1, binding.spJumlahModulRusak1, binding.spJumlahBattery1, binding.spBatteryRecti1, binding.spKondisiArester1)
        setupRectiSpinners(binding.spTypeRectifier2, binding.spJumlahModul2, binding.spJumlahModulRusak2, binding.spJumlahBattery2, binding.spBatteryRecti2, binding.spKondisiArester2)
        setupRectiSpinners(binding.spTypeRectifier3, binding.spJumlahModul3, binding.spJumlahModulRusak3, binding.spJumlahBattery3, binding.spBatteryRecti3, binding.spKondisiArester3)
        setupRectiSpinners(binding.spTypeRectifier4, binding.spJumlahModul4, binding.spJumlahModulRusak4, binding.spJumlahBattery4, binding.spBatteryRecti4, binding.spKondisiArester4)
        setupRectiSpinners(binding.spTypeRectifier5, binding.spJumlahModul5, binding.spJumlahModulRusak5, binding.spJumlahBattery5, binding.spBatteryRecti5, binding.spKondisiArester5)
        setupRectiSpinners(binding.spTypeRectifier6, binding.spJumlahModul6, binding.spJumlahModulRusak6, binding.spJumlahBattery6, binding.spBatteryRecti6, binding.spKondisiArester6)
        setupRectiSpinners(binding.spTypeRectifier7, binding.spJumlahModul7, binding.spJumlahModulRusak7, binding.spJumlahBattery7, binding.spBatteryRecti7, binding.spKondisiArester7)

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
            mcbBatteryRecti2 = if (adaRecti2) binding.etMcbBatteryRecti2.text.toString().trim() else "",
            kondisiArester2 = if (adaRecti2) binding.spKondisiArester2.selectedItem?.toString() ?: "" else "",
            busVoltage2 = if (adaRecti2) binding.etBusVoltage2.text.toString().trim() else "",
            loadA2 = if (adaRecti2) binding.etLoadA2.text.toString().trim() else "",
            perangkatTerhubung2 = if (adaRecti2) binding.etPerangkat2.text.toString().trim() else "",
            remark2 = if (adaRecti2) binding.etRemark2.text.toString().trim() else "",

            typeRectifier3 = if (adaRecti3) binding.spTypeRectifier3.selectedItem?.toString() ?: "" else "",
            jumlahModul3 = if (adaRecti3) binding.spJumlahModul3.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak3 = if (adaRecti3) binding.spJumlahModulRusak3.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP3 = if (adaRecti3) binding.spJumlahBattery3.selectedItem?.toString() ?: "" else "",
            batteryRecti3 = if (adaRecti3) binding.spBatteryRecti3.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti3 = if (adaRecti3) binding.etMcbBatteryRecti3.text.toString().trim() else "",
            kondisiArester3 = if (adaRecti3) binding.spKondisiArester3.selectedItem?.toString() ?: "" else "",
            busVoltage3 = if (adaRecti3) binding.etBusVoltage3.text.toString().trim() else "",
            loadA3 = if (adaRecti3) binding.etLoadA3.text.toString().trim() else "",
            perangkatTerhubung3 = if (adaRecti3) binding.etPerangkat3.text.toString().trim() else "",

            typeRectifier4 = if (adaRecti4) binding.spTypeRectifier4.selectedItem?.toString() ?: "" else "",
            jumlahModul4 = if (adaRecti4) binding.spJumlahModul4.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak4 = if (adaRecti4) binding.spJumlahModulRusak4.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP4 = if (adaRecti4) binding.spJumlahBattery4.selectedItem?.toString() ?: "" else "",
            batteryRecti4 = if (adaRecti4) binding.spBatteryRecti4.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti4 = if (adaRecti4) binding.etMcbBatteryRecti4.text.toString().trim() else "",
            kondisiArester4 = if (adaRecti4) binding.spKondisiArester4.selectedItem?.toString() ?: "" else "",
            busVoltage4 = if (adaRecti4) binding.etBusVoltage4.text.toString().trim() else "",
            loadA4 = if (adaRecti4) binding.etLoadA4.text.toString().trim() else "",
            perangkatTerhubung4 = if (adaRecti4) binding.etPerangkat4.text.toString().trim() else "",

            typeRectifier5 = if (adaRecti5) binding.spTypeRectifier5.selectedItem?.toString() ?: "" else "",
            jumlahModul5 = if (adaRecti5) binding.spJumlahModul5.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak5 = if (adaRecti5) binding.spJumlahModulRusak5.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP5 = if (adaRecti5) binding.spJumlahBattery5.selectedItem?.toString() ?: "" else "",
            batteryRecti5 = if (adaRecti5) binding.spBatteryRecti5.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti5 = if (adaRecti5) binding.etMcbBatteryRecti5.text.toString().trim() else "",
            kondisiArester5 = if (adaRecti5) binding.spKondisiArester5.selectedItem?.toString() ?: "" else "",
            busVoltage5 = if (adaRecti5) binding.etBusVoltage5.text.toString().trim() else "",
            loadA5 = if (adaRecti5) binding.etLoadA5.text.toString().trim() else "",
            perangkatTerhubung5 = if (adaRecti5) binding.etPerangkat5.text.toString().trim() else "",

            typeRectifier6 = if (adaRecti6) binding.spTypeRectifier6.selectedItem?.toString() ?: "" else "",
            jumlahModul6 = if (adaRecti6) binding.spJumlahModul6.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak6 = if (adaRecti6) binding.spJumlahModulRusak6.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP6 = if (adaRecti6) binding.spJumlahBattery6.selectedItem?.toString() ?: "" else "",
            batteryRecti6 = if (adaRecti6) binding.spBatteryRecti6.selectedItem?.toString() ?: "" else "",
            mcbBatteryRecti6 = if (adaRecti6) binding.etMcbBatteryRecti6.text.toString().trim() else "",
            kondisiArester6 = if (adaRecti6) binding.spKondisiArester6.selectedItem?.toString() ?: "" else "",
            busVoltage6 = if (adaRecti6) binding.etBusVoltage6.text.toString().trim() else "",
            loadA6 = if (adaRecti6) binding.etLoadA6.text.toString().trim() else "",
            perangkatTerhubung6 = if (adaRecti6) binding.etPerangkat6.text.toString().trim() else "",

            typeRectifier7 = if (adaRecti7) binding.spTypeRectifier7.selectedItem?.toString() ?: "" else "",
            jumlahModul7 = if (adaRecti7) binding.spJumlahModul7.selectedItem?.toString() ?: "" else "",
            jumlahModulRusak7 = if (adaRecti7) binding.spJumlahModulRusak7.selectedItem?.toString() ?: "" else "",
            jumlahBatteryP7 = if (adaRecti7) binding.spJumlahBattery7.selectedItem?.toString() ?: "" else "",
            batteryRecti7 = if (adaRecti7) binding.spBatteryRecti7.selectedItem?.toString() ?: "" else "",
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

        fun appendRectiPreview(idx: Int, type: String, modul: String, modulRusak: String, jmlBat: String, statusBat: String, mcbBat: String, arester: String, voltage: String, load: String, perangkat: String) {
            text.append("📦 RECTI $idx\n")
            text.append("⚡ Type: ${valOrDash(type)}\n")
            text.append("🔢 Jumlah Modul: ${valOrDash(modul)}\n")
            text.append("🔢 Modul Rusak: ${valOrDash(modulRusak)}\n")
            text.append("🔢 Jumlah Battery: ${valOrDash(jmlBat)}\n")
            text.append("⚙️ Battery Status: ${valOrDash(statusBat)}\n")
            text.append("🔌 MCB Battery Recty: ${valOrDash(mcbBat)}\n")
            text.append("⚡ Kondisi Arester: ${valOrDash(arester)}\n")
            text.append("🔌 Bus Voltage: ${valOrDash(voltage)}\n")
            text.append("⚙️ Load: ${valOrDash(load)}\n")
            text.append("📡 Perangkat: ${valOrDash(perangkat)}\n\n")
        }

        appendRectiPreview(1, d.typeRectifier1, d.jumlahModul1, d.jumlahModulRusak1, d.jumlahBatteryP1, d.batteryRecti1, d.mcbBatteryRecti1, d.kondisiArester1, d.busVoltage1, d.loadA1, d.perangkatTerhubung1)
        if (d.adaRecti2) appendRectiPreview(2, d.typeRectifier2, d.jumlahModul2, d.jumlahModulRusak2, d.jumlahBatteryP2, d.batteryRecti2, d.mcbBatteryRecti2, d.kondisiArester2, d.busVoltage2, d.loadA2, d.perangkatTerhubung2)
        if (d.adaRecti3) appendRectiPreview(3, d.typeRectifier3, d.jumlahModul3, d.jumlahModulRusak3, d.jumlahBatteryP3, d.batteryRecti3, d.mcbBatteryRecti3, d.kondisiArester3, d.busVoltage3, d.loadA3, d.perangkatTerhubung3)
        if (d.adaRecti4) appendRectiPreview(4, d.typeRectifier4, d.jumlahModul4, d.jumlahModulRusak4, d.jumlahBatteryP4, d.batteryRecti4, d.mcbBatteryRecti4, d.kondisiArester4, d.busVoltage4, d.loadA4, d.perangkatTerhubung4)
        if (d.adaRecti5) appendRectiPreview(5, d.typeRectifier5, d.jumlahModul5, d.jumlahModulRusak5, d.jumlahBatteryP5, d.batteryRecti5, d.mcbBatteryRecti5, d.kondisiArester5, d.busVoltage5, d.loadA5, d.perangkatTerhubung5)
        if (d.adaRecti6) appendRectiPreview(6, d.typeRectifier6, d.jumlahModul6, d.jumlahModulRusak6, d.jumlahBatteryP6, d.batteryRecti6, d.mcbBatteryRecti6, d.kondisiArester6, d.busVoltage6, d.loadA6, d.perangkatTerhubung6)
        if (d.adaRecti7) appendRectiPreview(7, d.typeRectifier7, d.jumlahModul7, d.jumlahModulRusak7, d.jumlahBatteryP7, d.batteryRecti7, d.mcbBatteryRecti7, d.kondisiArester7, d.busVoltage7, d.loadA7, d.perangkatTerhubung7)

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

        fun populateRecti(idx: Int, type: String, modul: String, modulRusak: String, jmlBat: String, statusBat: String, mcbBat: String, arester: String, voltage: String, load: String, perangkat: String, remark: String? = null) {
            when (idx) {
                1 -> {
                    setSpinnerSelectionByValue(binding.spTypeRectifier1, type)
                    setSpinnerSelectionByValue(binding.spJumlahModul1, modul)
                    setSpinnerSelectionByValue(binding.spJumlahModulRusak1, modulRusak)
                    setSpinnerSelectionByValue(binding.spJenisBaterai1, dataClassVal(d.jenisBaterai1))
                    setSpinnerSelectionByValue(binding.spMerkBaterai1, dataClassVal(d.merkBaterai1))
                    setSpinnerSelectionByValue(binding.spJumlahBattery1, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti1, statusBat)
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
                    setSpinnerSelectionByValue(binding.spJenisBaterai2, dataClassVal(d.jenisBaterai2))
                    setSpinnerSelectionByValue(binding.spMerkBaterai2, dataClassVal(d.merkBaterai2))
                    setSpinnerSelectionByValue(binding.spJumlahBattery2, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti2, statusBat)
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
                    setSpinnerSelectionByValue(binding.spJumlahBattery3, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti3, statusBat)
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
                    setSpinnerSelectionByValue(binding.spJumlahBattery4, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti4, statusBat)
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
                    setSpinnerSelectionByValue(binding.spJumlahBattery5, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti5, statusBat)
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
                    setSpinnerSelectionByValue(binding.spJumlahBattery6, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti6, statusBat)
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
                    setSpinnerSelectionByValue(binding.spJumlahBattery7, jmlBat)
                    setSpinnerSelectionByValue(binding.spBatteryRecti7, statusBat)
                    setSpinnerSelectionByValue(binding.spKondisiArester7, arester)
                    binding.etMcbBatteryRecti7.setText(mcbBat)
                    binding.etBusVoltage7.setText(voltage)
                    binding.etLoadA7.setText(load)
                    binding.etPerangkat7.setText(perangkat)
                }
            }
        }

        populateRecti(1, d.typeRectifier1, d.jumlahModul1, d.jumlahModulRusak1, d.jumlahBatteryP1, d.batteryRecti1, d.mcbBatteryRecti1, d.kondisiArester1, d.busVoltage1, d.loadA1, d.perangkatTerhubung1, d.remark1)
        if (d.adaRecti2) populateRecti(2, d.typeRectifier2, d.jumlahModul2, d.jumlahModulRusak2, d.jumlahBatteryP2, d.batteryRecti2, d.mcbBatteryRecti2, d.kondisiArester2, d.busVoltage2, d.loadA2, d.perangkatTerhubung2, d.remark2)
        if (d.adaRecti3) populateRecti(3, d.typeRectifier3, d.jumlahModul3, d.jumlahModulRusak3, d.jumlahBatteryP3, d.batteryRecti3, d.mcbBatteryRecti3, d.kondisiArester3, d.busVoltage3, d.loadA3, d.perangkatTerhubung3)
        if (d.adaRecti4) populateRecti(4, d.typeRectifier4, d.jumlahModul4, d.jumlahModulRusak4, d.jumlahBatteryP4, d.batteryRecti4, d.mcbBatteryRecti4, d.kondisiArester4, d.busVoltage4, d.loadA4, d.perangkatTerhubung4)
        if (d.adaRecti5) populateRecti(5, d.typeRectifier5, d.jumlahModul5, d.jumlahModulRusak5, d.jumlahBatteryP5, d.batteryRecti5, d.mcbBatteryRecti5, d.kondisiArester5, d.busVoltage5, d.loadA5, d.perangkatTerhubung5)
        if (d.adaRecti6) populateRecti(6, d.typeRectifier6, d.jumlahModul6, d.jumlahModulRusak6, d.jumlahBatteryP6, d.batteryRecti6, d.mcbBatteryRecti6, d.kondisiArester6, d.busVoltage6, d.loadA6, d.perangkatTerhubung6)
        if (d.adaRecti7) populateRecti(7, d.typeRectifier7, d.jumlahModul7, d.jumlahModulRusak7, d.jumlahBatteryP7, d.batteryRecti7, d.mcbBatteryRecti7, d.kondisiArester7, d.busVoltage7, d.loadA7, d.perangkatTerhubung7)

        selectedImageUris.clear()
        d.imageUris.forEach { uriStr ->
            try {
                selectedImageUris.add(Uri.parse(uriStr))
            } catch (_: Exception) { }
        }
        updatePhotoSlotsUI()
        tampilkanPratinjauRingkasan()
    }

    private fun dataClassVal(v: String?): String = v ?: ""

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
