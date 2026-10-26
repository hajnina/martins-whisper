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
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.martins.whisper.core.ComputeBackend
import com.martins.whisper.data.SettingsStore
import com.martins.whisper.service.DictationService
import com.martins.whisper.service.DictationState
import com.martins.whisper.ui.ModelOptions
import com.martins.whisper.ui.PermissionGate
import com.martins.whisper.ui.PermissionSnapshot
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var statusView: TextView
    private lateinit var serviceView: TextView
    private lateinit var micStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var notificationsStatus: TextView
    private lateinit var overlayButton: Button
    private var bootSwitch: Switch? = null
    private var suppressSpinnerCallbacks = false

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
        root.addView(label(getString(R.string.model_label)))
        val modelSpinner = Spinner(this)
        val modelAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            ModelOptions.specs.map { it.displayName },
        )
        modelSpinner.adapter = modelAdapter
        val modelIndex = ModelOptions.specs.indexOfFirst { it.id == settings.modelId }.coerceAtLeast(0)
        suppressSpinnerCallbacks = true
        modelSpinner.setSelection(modelIndex, false)
        suppressSpinnerCallbacks = false
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressSpinnerCallbacks) return
                settings.modelId = ModelOptions.specs[position].id
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        root.addView(modelSpinner, matchWidth())
        root.addView(caption(getString(R.string.model_stub_note)).apply {
            setPadding(0, dp(4), 0, dp(10))
        })

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
        root.addView(caption(getString(R.string.diagnostics_text)).apply {
            setPadding(0, 0, 0, dp(8))
        })
        return scroll
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
