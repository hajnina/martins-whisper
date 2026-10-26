package com.martins.whisper

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.martins.whisper.asr.ModelRepositoryProvider
import com.martins.whisper.asr.WhisperCppEngine
import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.core.ModelSpec
import com.martins.whisper.core.asr.DownloadProgress
import com.martins.whisper.core.asr.ModelCatalog
import com.martins.whisper.core.asr.ModelRepository
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.service.DictationService
import com.martins.whisper.service.DictationState
import com.martins.whisper.ui.ModelAction
import com.martins.whisper.ui.ModelBrowser
import com.martins.whisper.ui.ModelPresentation
import com.martins.whisper.ui.ModelTransfer
import com.martins.whisper.ui.PermissionGate
import com.martins.whisper.ui.PermissionSnapshot
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var statusView: TextView
    private lateinit var serviceView: TextView
    private lateinit var micStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var notificationsStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var modelContainer: LinearLayout
    private lateinit var modelProgressBar: ProgressBar
    private lateinit var modelTransferView: TextView
    private lateinit var diagnosticsView: TextView
    private lateinit var modelBrowser: ModelBrowser
    private var bootSwitch: Switch? = null
    private var pendingImportId: String? = null

    private val repository: ModelRepository by lazy { ModelRepositoryProvider.forContext(this) }

    private val importDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pendingImportId
        pendingImportId = null
        if (uri != null && id != null) importModelFrom(id, uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        setContentView(buildContent())
        observeService()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) {
            intent?.removeExtra(EXTRA_OPEN_SETTINGS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun buildContent(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(40))
        }
        scroll.addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        root.addView(title(getString(R.string.onboarding_title)))
        root.addView(body(getString(R.string.privacy_note)).apply {
            setPadding(0, dp(10), 0, dp(14))
            setTextColor(Color.rgb(55, 65, 70))
        })

        statusView = body("").apply {
            setTextColor(Color.rgb(19, 93, 102))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(statusView)
        serviceView = body("").apply {
            setTextColor(Color.rgb(80, 80, 80))
            setPadding(0, 0, 0, dp(18))
        }
        root.addView(serviceView)

        root.addView(section(getString(R.string.section_permissions)))
        val micRow = permissionRow(getString(R.string.permission_mic)) { requestMic() }
        micStatus = micRow.status
        root.addView(micRow.row)
        val overlayRow = permissionRow(getString(R.string.permission_overlay)) { openOverlaySettings() }
        overlayStatus = overlayRow.status
        root.addView(overlayRow.row)
        val accessibilityRow = permissionRow(getString(R.string.permission_accessibility)) {
            openAccessibilitySettings()
        }
        accessibilityStatus = accessibilityRow.status
        root.addView(accessibilityRow.row)
        val notificationsRow = permissionRow(getString(R.string.permission_notifications)) {
            requestNotifications()
        }
        notificationsStatus = notificationsRow.status
        root.addView(notificationsRow.row)

        val bootRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(4))
        }
        val bootLabel = body(getString(R.string.permission_boot)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        bootRow.addView(bootLabel)
        bootSwitch = Switch(this).apply {
            isChecked = settings.bootStart
            setOnCheckedChangeListener { _: CompoundButton, checked: Boolean ->
                settings.bootStart = checked
            }
        }
        bootRow.addView(bootSwitch)
        root.addView(bootRow)
        root.addView(caption(getString(R.string.boot_start_summary)).apply {
            setPadding(0, 0, 0, dp(8))
        })

        root.addView(section(getString(R.string.section_overlay)))
        overlayButton = Button(this).apply {
            setOnClickListener { toggleOverlay() }
        }
        root.addView(overlayButton, matchWidth())

        root.addView(section(getString(R.string.section_model)))
        modelBrowser = ModelBrowser(ModelCatalog.specs, installedModelIds(), settings.modelId)
        modelContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(modelContainer, matchWidth())
        modelProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(modelProgressBar, matchWidth())
        modelTransferView = caption("").apply {
            visibility = View.GONE
            setPadding(0, dp(4), 0, dp(4))
        }
        root.addView(modelTransferView)
        root.addView(caption(getString(R.string.model_note)).apply {
            setPadding(0, dp(4), 0, dp(10))
        })
        renderModels()

        root.addView(section(getString(R.string.section_asr)))
        root.addView(label(getString(R.string.language_label)))
        val languages = listOf(
            SettingsStore.LANGUAGE_AUTO to getString(R.string.language_auto),
            "en" to "English",
            "de" to "Deutsch",
            "fr" to "Fran\u00e7ais",
            "es" to "Espa\u00f1ol",
            "it" to "Italiano",
            "pt" to "Portugu\u00eas",
            "nl" to "Nederlands",
            "ru" to "Russian",
            "zh" to "Chinese",
            "ja" to "Japanese",
        )
        root.addView(languageSpinner(languages), matchWidth())

        root.addView(label(getString(R.string.backend_label)))
        val backends = listOf(
            ComputeBackend.AUTO to getString(R.string.backend_auto),
            ComputeBackend.GPU to getString(R.string.backend_gpu),
            ComputeBackend.CPU to getString(R.string.backend_cpu),
        )
        val backendSpinner = Spinner(this)
        backendSpinner.adapter = spinnerAdapter(backends.map { it.second })
        backendSpinner.setSelection(backends.indexOfFirst { it.first == settings.backend }.coerceAtLeast(0), false)
        backendSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settings.backend = backends[position].first
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        root.addView(backendSpinner, matchWidth())

        root.addView(label(getString(R.string.idle_unload_label)))
        val idleOptions = listOf(
            60 to "1 minute (60 s)",
            300 to "5 minutes (300 s, default)",
            600 to "10 minutes (600 s)",
            900 to "15 minutes (900 s)",
        )
        val idleSpinner = Spinner(this)
        idleSpinner.adapter = spinnerAdapter(idleOptions.map { it.second })
        idleSpinner.setSelection(
            idleOptions.indexOfFirst { it.first == settings.idleUnloadSeconds }.coerceAtLeast(1),
            false,
        )
        idleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settings.idleUnloadSeconds = idleOptions[position].first
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        root.addView(idleSpinner, matchWidth())

        root.addView(section(getString(R.string.section_diagnostics)))
        diagnosticsView = body("").apply {
            setTypeface(Typeface.MONOSPACE)
            setTextColor(Color.rgb(60, 70, 75))
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(diagnosticsView)
        root.addView(caption(getString(R.string.diagnostics_text)).apply {
            setPadding(0, 0, 0, dp(8))
        })
        updateDiagnostics()
        return scroll
    }

    private fun installedModelIds(): Set<String> =
        ModelCatalog.specs.asSequence()
            .filter { repository.isInstalled(it) }
            .map { it.id }
            .toSet()

    private fun renderModels() {
        if (!::modelContainer.isInitialized) return
        modelContainer.removeAllViews()
        val state = modelBrowser.state
        for (row in state.rows) {
            val spec = row.spec
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8), 0, dp(10))
                isClickable = true
                setOnClickListener { selectModel(spec.id) }
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            header.addView(body(spec.displayName).apply {
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (row.selected) Color.rgb(19, 93, 102) else Color.rgb(35, 42, 46))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            header.addView(caption(ModelPresentation.sizeLabel(spec.sizeBytes)))
            box.addView(header)

            box.addView(caption(ModelPresentation.statusLabel(row.installed, row.selected)).apply {
                setTextColor(if (row.installed) Color.rgb(19, 120, 70) else Color.rgb(170, 90, 20))
            })
            if (spec.notes.isNotBlank()) box.addView(caption(spec.notes))

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, 0)
            }
            if (!row.installed && spec.url != null) {
                actions.addView(actionButton(R.string.model_download, !state.isBusy) {
                    downloadModel(spec.id)
                })
            }
            actions.addView(actionButton(R.string.model_import, !state.isBusy) {
                pickImport(spec.id)
            })
            if (row.installed) {
                actions.addView(actionButton(R.string.model_delete, !state.isBusy) {
                    deleteModel(spec.id)
                })
            }
            box.addView(actions)
            modelContainer.addView(box, matchWidth())
        }
        state.error?.let {
            modelContainer.addView(caption(it).apply { setTextColor(Color.rgb(180, 40, 40)) })
        }
        state.message?.let {
            modelContainer.addView(caption(it).apply { setTextColor(Color.rgb(19, 93, 102)) })
        }
        renderTransfer()
    }

    private fun renderTransfer() {
        if (!::modelProgressBar.isInitialized) return
        val transfer = modelBrowser.state.transfer
        if (transfer == null) {
            modelProgressBar.visibility = View.GONE
            modelTransferView.visibility = View.GONE
            return
        }
        val displayName = ModelCatalog.byId(transfer.modelId)?.displayName ?: transfer.modelId
        val fraction = when (transfer) {
            is ModelTransfer.Download -> transfer.fraction
            is ModelTransfer.Import -> transfer.fraction
            is ModelTransfer.Delete -> null
        }
        modelProgressBar.visibility = View.VISIBLE
        modelProgressBar.isIndeterminate = fraction == null
        if (fraction != null) modelProgressBar.progress = (fraction * 100).roundToInt()
        modelTransferView.visibility = View.VISIBLE
        modelTransferView.text = ModelPresentation.transferLabel(transfer, displayName)
    }

    private fun actionButton(label: Int, enabled: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            setText(label)
            isEnabled = enabled
            minWidth = 0
            minimumWidth = 0
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }

    private fun selectModel(id: String) {
        settings.modelId = id
        modelBrowser.select(id)
        renderModels()
        updateDiagnostics()
    }

    private fun downloadModel(id: String) {
        val spec = ModelCatalog.byId(id) ?: return
        if (!modelBrowser.begin(id, ModelAction.DOWNLOAD)) return
        renderModels()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.install(spec = spec) { progress -> publishProgress(progress) }
                }
                modelBrowser.completeInstalled(id)
            } catch (t: Throwable) {
                modelBrowser.fail(t.message ?: t.javaClass.simpleName)
            }
            renderModels()
            updateDiagnostics()
        }
    }

    private fun pickImport(id: String) {
        pendingImportId = id
        try {
            importDocument.launch(arrayOf("*/*"))
        } catch (t: Throwable) {
            pendingImportId = null
            modelBrowser.fail(t.message ?: "Cannot open the file picker")
            renderModels()
        }
    }

    private fun importModelFrom(id: String, uri: Uri) {
        val spec = ModelCatalog.byId(id) ?: return
        if (!modelBrowser.begin(id, ModelAction.IMPORT)) return
        renderModels()
        lifecycleScope.launch {
            var temp: File? = null
            try {
                withContext(Dispatchers.IO) {
                    val tempFile = File(cacheDir, "model-import-${System.currentTimeMillis()}.bin")
                    temp = tempFile
                    val input = contentResolver.openInputStream(uri)
                        ?: error("Cannot open the selected file")
                    input.use { source ->
                        tempFile.outputStream().use { target -> source.copyTo(target) }
                    }
                    repository.importFile(spec = spec, source = tempFile) { progress ->
                        publishProgress(progress)
                    }
                }
                modelBrowser.completeInstalled(id)
            } catch (t: Throwable) {
                modelBrowser.fail(t.message ?: t.javaClass.simpleName)
            } finally {
                temp?.delete()
            }
            renderModels()
            updateDiagnostics()
        }
    }

    private fun deleteModel(id: String) {
        val spec = ModelCatalog.byId(id) ?: return
        if (!modelBrowser.begin(id, ModelAction.DELETE)) return
        renderModels()
        lifecycleScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) { repository.delete(spec) }
                if (deleted) {
                    modelBrowser.completeDeleted(id)
                } else {
                    modelBrowser.fail("Model file was already absent")
                }
            } catch (t: Throwable) {
                modelBrowser.fail(t.message ?: t.javaClass.simpleName)
            }
            renderModels()
            updateDiagnostics()
        }
    }

    private fun publishProgress(progress: DownloadProgress) {
        val fraction = progress.fraction
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            modelBrowser.progress(fraction)
            renderTransfer()
        }
    }

    private fun updateDiagnostics() {
        if (!::diagnosticsView.isInitialized) return
        val engine = settings.engineState
        val nativeAvailable = WhisperCppEngine.isNativeAvailable
        val lines = buildList {
            add(
                getString(
                    R.string.diag_native,
                    getString(
                        if (nativeAvailable) R.string.diag_native_available else R.string.diag_native_missing,
                    ),
                ),
            )
            if (!nativeAvailable) {
                WhisperCppEngine.nativeLoadError?.let { add(getString(R.string.diag_native_error, it)) }
            }
            add(getString(R.string.diag_backend, engine.activeBackend ?: getString(R.string.diag_backend_unloaded)))
            add(getString(R.string.diag_fallback, ModelPresentation.fallbackLabel(engine.fallbackReason)))
            add(getString(R.string.diag_gpu_failures, engine.gpuFailureCount))
            engine.lastError?.let { add(getString(R.string.diag_last_error, it)) }
            if (engine.activeBackend == null && engine.lastError == null) {
                add(getString(R.string.diag_idle))
            }
        }
        diagnosticsView.text = lines.joinToString("\n")
    }

    private fun languageSpinner(languages: List<Pair<String, String>>): Spinner {
        val spinner = Spinner(this)
        spinner.adapter = spinnerAdapter(languages.map { it.second })
        spinner.setSelection(
            languages.indexOfFirst { it.first == settings.language }.coerceAtLeast(0),
            false,
        )
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settings.language = languages[position].first
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        return spinner
    }

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

    private fun toggleOverlay() {
        if (settings.overlayEnabled) {
            settings.overlayEnabled = false
            DictationService.stop(this)
        } else {
            settings.overlayEnabled = true
            DictationService.start(this)
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.error_overlay, Toast.LENGTH_LONG).show()
            }
        }
        rootView?.postDelayed({ refresh() }, 250)
    }

    private fun refresh() {
        val snapshot = currentSnapshot()
        statusView.text = PermissionGate.summary(snapshot)
        micStatus.text = grantedText(snapshot.microphone)
        overlayStatus.text = grantedText(snapshot.overlay)
        accessibilityStatus.text = grantedText(snapshot.accessibility)
        notificationsStatus.text = grantedText(snapshot.notifications)
        overlayButton.text = if (settings.overlayEnabled) {
            getString(R.string.stop_overlay)
        } else {
            getString(R.string.start_overlay)
        }
        bootSwitch?.isChecked = settings.bootStart
        if (::modelBrowser.isInitialized) {
            modelBrowser.syncInstalled(installedModelIds())
            renderModels()
            updateDiagnostics()
        }
    }

    private fun observeService() {
        lifecycleScope.launch {
            DictationService.state.collect { updateServiceStatus(it) }
        }
    }

    private fun updateServiceStatus(state: DictationState) {
        val label = when (state) {
            is DictationState.Idle -> "idle"
            is DictationState.Loading -> state.label
            is DictationState.Recording -> "recording ${state.elapsedMs / 1000}s"
            is DictationState.Processing -> state.label
            is DictationState.Success -> state.message
            is DictationState.Error -> state.message
        }
        serviceView.text = getString(R.string.service_state, label)
    }

    private fun currentSnapshot(): PermissionSnapshot = PermissionSnapshot(
        microphone = checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED,
        overlay = Settings.canDrawOverlays(this),
        accessibility = isAccessibilityEnabled(),
        notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    )

    private fun isAccessibilityEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    private fun requestMic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, R.string.error_overlay, Toast.LENGTH_LONG).show() }
    }

    private fun openAccessibilitySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private fun grantedText(granted: Boolean): String =
        if (granted) getString(R.string.status_granted) else getString(R.string.status_denied)

    private data class PermissionRow(val row: LinearLayout, val status: TextView)

    private fun permissionRow(title: String, onClick: () -> Unit): PermissionRow {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val titleView = body(title).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(titleView)
        val status = body(getString(R.string.status_unknown)).apply {
            setPadding(dp(8), 0, dp(8), 0)
            setTextColor(Color.rgb(100, 100, 100))
        }
        row.addView(status)
        row.addView(Button(this).apply {
            text = getString(R.string.grant)
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { onClick() }
        })
        return PermissionRow(row, status)
    }

    private fun title(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 28f
        setTextColor(Color.rgb(19, 93, 102))
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.rgb(30, 40, 45))
        setPadding(0, dp(22), 0, dp(6))
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(60, 70, 75))
        setPadding(0, dp(8), 0, dp(2))
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.rgb(35, 42, 46))
    }

    private fun caption(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.rgb(100, 105, 110))
    }

    private fun matchWidth(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private val rootView: View? get() = window.decorView

    companion object {
        const val EXTRA_OPEN_SETTINGS = "open_settings"
        private const val REQUEST_MIC = 100
        private const val REQUEST_NOTIFICATIONS = 101
    }
}
