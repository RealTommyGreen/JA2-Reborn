package com.ja2.reborn

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import com.ja2.reborn.databinding.ActivityLauncherBinding
import com.ja2.reborn.ui.main.SectionsPagerAdapter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException


class LauncherActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_LEGACY_STORAGE_PERMISSION = 1421
    }

    private lateinit var binding: ActivityLauncherBinding

    private val activityLogTag = "LauncherActivity"
    private val jsonFormat = Json {
        prettyPrint = true
    }
    private val ja2JsonFilename = ".ja2/ja2.json"
    private val cheatsJsonFilename = ".ja2/cheats.json"
    private lateinit var configurationModel: ConfigurationModel
    private var startPendingStoragePermission = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        LanguageManager.applyLanguage(this)
        super.onCreate(savedInstanceState)

        configurationModel = ViewModelProvider(this)[ConfigurationModel::class.java]
        loadJA2Json()
        syncGameVersionWithLanguageSelection()
        loadCheatsJson()
        deleteStaleGameSession()

        binding = ActivityLauncherBinding.inflate(layoutInflater)
        val view = binding.root

        setContentView(view)
        setupLanguageFlags()
        val sectionsPagerAdapter = SectionsPagerAdapter(this)
        binding.viewPager.adapter = sectionsPagerAdapter

        binding.fab.setOnClickListener {
            startGame()
        }
        hideSystemBars()
        maybePromptAutoUpdateOptIn()
    }

    override fun onResume() {
        super.onResume()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemBars()

        val exception = NativeExceptionContainer.getException()
        Log.i(activityLogTag, "Resuming LauncherActivity, previous exception: $exception")
        if (exception != null) {
            Toast.makeText(
                this,
                getString(R.string.crash_exception_toast, exception),
                Toast.LENGTH_LONG
            ).show()
            NativeExceptionContainer.resetException()
        }

        if (startPendingStoragePermission) {
            startPendingStoragePermission = false
            if (hasRequiredStoragePermission()) {
                startGameAfterPermissionCheck()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.storage_permission_missing_toast),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        maybeHandlePendingApk()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LEGACY_STORAGE_PERMISSION) {
            return
        }

        if (hasRequiredStoragePermission()) {
            startGameAfterPermissionCheck()
        } else {
            Toast.makeText(
                this,
                getString(R.string.storage_permission_missing_toast),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun setupLanguageFlags() {
        val updateCheck = binding.languageFlags.findViewById<android.widget.ImageView>(
            com.ja2.reborn.R.id.updateCheck
        )
        val flagDE = binding.languageFlags.findViewById<android.widget.ImageView>(
            com.ja2.reborn.R.id.flagDE
        )
        val flagGB = binding.languageFlags.findViewById<android.widget.ImageView>(
            com.ja2.reborn.R.id.flagGB
        )

        fun updateFlagHighlight() {
            val current = LanguageManager.getSavedLanguage(this)
            flagDE.alpha = if (current == LanguageManager.Language.GERMAN) 1.0f else 0.35f
            flagGB.alpha = if (current == LanguageManager.Language.ENGLISH) 1.0f else 0.35f
            flagDE.isSelected = current == LanguageManager.Language.GERMAN
            flagGB.isSelected = current == LanguageManager.Language.ENGLISH
        }
        updateFlagHighlight()

        fun selectLanguage(language: LanguageManager.Language) {
            if (LanguageManager.setLanguage(this, language)) {
                configurationModel.setVanillaGameVersion(language.toVanillaVersion())
                saveJA2Json()
                LanguageManager.applyLanguage(this)
                recreate()
            } else {
                configurationModel.setVanillaGameVersion(language.toVanillaVersion())
                saveJA2Json()
                updateFlagHighlight()
            }
        }

        updateCheck.setOnClickListener {
            performUpdateCheck(force = true)
        }

        flagDE.setOnClickListener {
            selectLanguage(LanguageManager.Language.GERMAN)
        }
        flagGB.setOnClickListener {
            selectLanguage(LanguageManager.Language.ENGLISH)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun getNativeMetrics(): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val nativeWidth = Integer.max(metrics.widthPixels, metrics.heightPixels)
        val nativeHeight = Integer.min(metrics.widthPixels, metrics.heightPixels)
        return Pair(nativeWidth, nativeHeight)
    }

    fun getRecommendedResolution(): Resolution {
        val (nativeW, nativeH) = getNativeMetrics()
        return ResolutionPolicy.calculate(ResolutionMode.DEFAULT, nativeW, nativeH)
    }

    fun calculateResolutionForMode(mode: ResolutionMode): Resolution {
        val (nativeW, nativeH) = getNativeMetrics()
        return ResolutionPolicy.calculate(mode, nativeW, nativeH)
    }

    fun persistJA2Configuration() {
        saveJA2Json()
    }

    private fun startGame() {
        if (!hasRequiredStoragePermission()) {
            showStoragePermissionDialog()
            return
        }
        startGameAfterPermissionCheck()
    }

    private fun startGameAfterPermissionCheck() {
        try {
            GameDir.checkGameDirectoryForCommonMistakes(
                this,
                configurationModel.vanillaGameDir.value
            ) {
                saveJA2Json()
                saveCheatsJson()
                NativeExceptionContainer.resetException()
                val intent = Intent(this@LauncherActivity, RebornActivity::class.java)
                startActivity(intent)
            }
        } catch (e: IOException) {
            val message = "Could not write ${ja2JsonPath}: ${e.message}"
            Log.e(activityLogTag, message)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasRequiredStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        val readGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

        return readGranted && writeGranted
    }

    private fun showStoragePermissionDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ja2GuiStyle.panelBackground(this@LauncherActivity)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.storage_permission_title)
            setTextColor(Ja2GuiStyle.ACCENT)
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        content.addView(TextView(this).apply {
            text = getString(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    R.string.storage_permission_message
                } else {
                    R.string.storage_permission_legacy_message
                }
            )
            setTextColor(Ja2GuiStyle.TEXT)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(16))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val dialog = AlertDialog.Builder(this).create()
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(R.string.cancel),
            minHeightDp = 42
        ) {
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(6)
        })
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(R.string.storage_permission_open_settings),
            textColor = Ja2GuiStyle.ACCENT,
            minHeightDp = 42
        ) {
            dialog.dismiss()
            openStoragePermissionSettings()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(6)
        })
        content.addView(buttonRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        dialog.setView(content)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }

    private fun openStoragePermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestLegacyStoragePermission()
            return
        }
        startPendingStoragePermission = true
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(activityLogTag, "Could not open app file access settings: ${e.message}")
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    private fun requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            startGameAfterPermissionCheck()
            return
        }

        val permissions = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            REQUEST_LEGACY_STORAGE_PERMISSION
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private val ja2JsonPath: String
        get() {
            return "${applicationContext.filesDir.absolutePath}/$ja2JsonFilename"
        }

    private fun loadJA2Json() {
        try {
            val text = File(ja2JsonPath).readText()
            val json: Ja2Json = jsonFormat.decodeFromString(text)

            configurationModel.setVanillaGameDir(json.vanillaGameDir)
            configurationModel.setSaveGameDir(json.saveGameDir)
            configurationModel.setExternalizedJsonDir(json.externalizedJsonDir)

            if (json.vanillaGameVersion != null) {
                configurationModel.setVanillaGameVersion(json.vanillaGameVersion)
            } else {
                configurationModel.setVanillaGameVersion(VanillaVersion.DEFAULT)
            }

            // Resolution mode migration
            val resolvedMode = when {
                json.resolutionMode != null -> json.resolutionMode
                json.resolution != null && json.resolution.width == 640u && json.resolution.height == 480u ->
                    ResolutionMode.RETRO
                else -> ResolutionMode.MODERN
            }
            configurationModel.setResolutionMode(resolvedMode)
            val expertSettings = json.expertSettings == true
            configurationModel.setExpertSettings(expertSettings)

            val (nativeW, nativeH) = getNativeMetrics()
            if (expertSettings && json.resolution != null) {
                configurationModel.setResolution(json.resolution)
            } else {
                configurationModel.setResolution(ResolutionPolicy.calculate(resolvedMode, nativeW, nativeH))
            }

            if (expertSettings && json.scalingQuality != null) {
                configurationModel.setScalingQuality(json.scalingQuality)
            } else {
                configurationModel.setScalingQuality(ScalingQuality.DEFAULT)
            }

            if (expertSettings && json.mouseMode != null) {
                configurationModel.setMouseMode(json.mouseMode)
            } else {
                configurationModel.setMouseMode(MouseMode.DEFAULT)
            }
            if (json.debug != null) {
                configurationModel.setDebug(json.debug)
            } else {
                configurationModel.setDebug(false)
            }
        } catch (e: SerializationException) {
            Log.w(activityLogTag, "Could not decode ja2.json: ${e.message}")
            configurationModel.setVanillaGameVersion(VanillaVersion.ENGLISH)
            configurationModel.setScalingQuality(ScalingQuality.DEFAULT)
            configurationModel.setMouseMode(MouseMode.DEFAULT)
            configurationModel.setResolution(getRecommendedResolution())
            configurationModel.setExpertSettings(false)
            configurationModel.setDebug(false)
        } catch (e: IOException) {
            Log.w(activityLogTag, "Could not read $ja2JsonPath: ${e.message}")
            configurationModel.setVanillaGameVersion(VanillaVersion.ENGLISH)
            configurationModel.setScalingQuality(ScalingQuality.DEFAULT)
            configurationModel.setMouseMode(MouseMode.DEFAULT)
            configurationModel.setResolution(getRecommendedResolution())
            configurationModel.setExpertSettings(false)
            configurationModel.setDebug(false)
        }
    }

    private fun syncGameVersionWithLanguageSelection() {
        val language = if (LanguageManager.hasSavedLanguage(this)) {
            LanguageManager.getSavedLanguage(this)
        } else {
            LanguageManager.Language.ENGLISH
        }
        val version = language.toVanillaVersion()
        if (configurationModel.vanillaGameVersion.value != version) {
            configurationModel.setVanillaGameVersion(version)
            saveJA2Json()
        }
    }

    private fun LanguageManager.Language.toVanillaVersion(): VanillaVersion {
        return when (this) {
            LanguageManager.Language.ENGLISH -> VanillaVersion.ENGLISH
            LanguageManager.Language.GERMAN -> VanillaVersion.GERMAN
        }
    }

    private fun saveJA2Json() {
        if (configurationModel.expertSettings.value != true) {
            val mode = configurationModel.resolutionMode.value ?: ResolutionMode.DEFAULT
            configurationModel.setResolution(calculateResolutionForMode(mode))
            configurationModel.setScalingQuality(ScalingQuality.DEFAULT)
            configurationModel.setMouseMode(MouseMode.DEFAULT)
        }

        val json = Ja2Json(
            vanillaGameDir = configurationModel.vanillaGameDir.value,
            vanillaGameVersion = configurationModel.vanillaGameVersion.value,
            saveGameDir = configurationModel.saveGameDir.value,
            externalizedJsonDir = configurationModel.externalizedJsonDir.value,
            resolution = configurationModel.resolution.value,
            resolutionMode = configurationModel.resolutionMode.value,
            scalingQuality = configurationModel.scalingQuality.value,
            mouseMode = configurationModel.mouseMode.value,
            expertSettings = configurationModel.expertSettings.value,
            debug = configurationModel.debug.value
        )
        val parentDir = File(ja2JsonPath).parentFile
        if (parentDir?.exists() != true) {
            parentDir?.mkdirs()
        }
        File(ja2JsonPath).writeText(jsonFormat.encodeToString(json))
    }

    private val cheatsJsonPath: String
        get() {
            return "${applicationContext.filesDir.absolutePath}/$cheatsJsonFilename"
        }

    private fun loadCheatsJson() {
        try {
            val text = File(cheatsJsonPath).readText()
            val cheats: CheatConfig = jsonFormat.decodeFromString(text)
            configurationModel.cheatConfig.value = cheats
        } catch (e: SerializationException) {
            Log.w(activityLogTag, "Could not decode cheats.json: ${e.message}")
            configurationModel.cheatConfig.value = CheatConfig.DEFAULT
        } catch (e: IOException) {
            Log.i(activityLogTag, "No cheats.json found, using defaults")
            configurationModel.cheatConfig.value = CheatConfig.DEFAULT
        }
    }

    private fun deleteStaleGameSession() {
        try {
            val file = File(applicationContext.filesDir, ".ja2/game_session")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(activityLogTag, "Could not delete stale game session marker: ${e.message}")
        }
    }

    private fun maybeHandlePendingApk() {
        val (pendingFile, _) = UpdateApkVerifier.getPendingApk(this) ?: return

        if (!pendingFile.exists()) {
            UpdateApkVerifier.clearPendingApk(this)
            return
        }

        val result = UpdateApkVerifier.verifyApk(this, pendingFile)
        if (!result.passed) {
            Log.w(activityLogTag, "Pending APK re-verification failed: ${result.reason}")
            UpdateApkVerifier.clearPendingApk(this)
            pendingFile.delete()
            return
        }

        if (UpdateApkVerifier.needsInstallPermission(this)) {
            showInstallPermissionDialog()
            return
        }

        UpdateApkVerifier.clearPendingApk(this)
        val intent = UpdateApkVerifier.createInstallerIntent(this, pendingFile)
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(activityLogTag, "Installer intent failed: ${e.message}")
                Toast.makeText(
                    this,
                    getString(R.string.auto_update_installer_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Log.w(activityLogTag, "createInstallerIntent returned null for pending APK")
            Toast.makeText(
                this,
                getString(R.string.auto_update_installer_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // -- Auto-update ----------------------------------------------------------

    private val updatePrefsActivityAlive: Boolean
        get() = !isFinishing && !isDestroyed

    private fun runOnUiIfAlive(action: () -> Unit) {
        if (updatePrefsActivityAlive) runOnUiThread(action)
    }

    private fun maybePromptAutoUpdateOptIn() {
        val currentVersion = UpdatePrefs.getInstalledVersionName(this)

        val optedIn = UpdatePrefs.isOptedIn(this)
        if (optedIn != null) {
            if (optedIn) {
                performUpdateCheck(force = false)
            }
            return
        }

        if (!UpdatePrefs.shouldShowOptIn(this, currentVersion)) {
            return
        }

        UpdatePrefs.setPromptedVersion(this, currentVersion)
        showOptInDialog()
    }

    private fun showOptInDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ja2GuiStyle.panelBackground(this@LauncherActivity)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.auto_update_optin_title)
            setTextColor(Ja2GuiStyle.ACCENT)
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        content.addView(TextView(this).apply {
            text = getString(R.string.auto_update_optin_message)
            setTextColor(Ja2GuiStyle.TEXT)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(16))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val dialog = AlertDialog.Builder(this).create()
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(R.string.auto_update_optin_decline),
            minHeightDp = 42
        ) {
            dialog.dismiss()
            UpdatePrefs.setOptedIn(this@LauncherActivity, false)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(6)
        })
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(R.string.auto_update_optin_activate),
            textColor = Ja2GuiStyle.ACCENT,
            minHeightDp = 42
        ) {
            dialog.dismiss()
            UpdatePrefs.setOptedIn(this@LauncherActivity, true)
            performUpdateCheck(force = true)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(6)
        })
        content.addView(buttonRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        dialog.setView(content)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }

    private fun performUpdateCheck(force: Boolean) {
        if (!isNetworkAvailable()) {
            showUpdateInfoDialog(
                getString(R.string.auto_update_no_network)
            )
            return
        }

        if (UpdatePrefs.isRateLimited(this, force)) return
        UpdatePrefs.setLastCheckTimeNow(this)

        val localVersion = UpdatePrefs.getInstalledVersionName(this)

        Thread({
            val release = UpdateChecker.fetchLatestRelease()
            if (release == null) {
                runOnUiIfAlive {
                    showUpdateInfoDialog(getString(R.string.auto_update_download_failed))
                }
                return@Thread
            }
            if (release.draft || release.prerelease) {
                runOnUiIfAlive {
                    showUpdateUpToDateDialog(localVersion)
                }
                return@Thread
            }

            if (!UpdateChecker.isNewerVersion(release.tagName, localVersion)) {
                runOnUiIfAlive {
                    showUpdateUpToDateDialog(localVersion)
                }
                return@Thread
            }

            val asset = UpdateChecker.selectApkAsset(release)
            if (asset == null) {
                runOnUiIfAlive {
                    showUpdateInfoDialog(getString(R.string.auto_update_download_failed))
                }
                return@Thread
            }

            runOnUiIfAlive {
                showUpdateAvailableDialog(release, asset)
            }
        }, "auto-update-check").apply { isDaemon = true }.start()
    }

    private fun isNetworkAvailable(): Boolean {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            return false
        }
    }

    private fun showUpdateAvailableDialog(release: GitHubRelease, asset: GitHubAsset) {
        val versionName = release.tagName.removePrefix("v")
        val sizeText = formatSizeMB(asset.size)
        val notesText = truncateText(release.body ?: "", 1200)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ja2GuiStyle.panelBackground(this@LauncherActivity)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.auto_update_available_title)
            setTextColor(Ja2GuiStyle.ACCENT)
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        content.addView(TextView(this).apply {
            text = getString(R.string.auto_update_available_message, versionName, sizeText)
            setTextColor(Ja2GuiStyle.TEXT)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, if (notesText.isNotEmpty()) dp(8) else dp(14))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        if (notesText.isNotEmpty()) {
            val scrollView = android.widget.ScrollView(this).apply {
                setPadding(0, 0, 0, dp(12))
            }
            scrollView.addView(TextView(this).apply {
                text = notesText
                setTextColor(Ja2GuiStyle.TEXT)
                textSize = 12f
            })
            content.addView(scrollView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(160)
            ))
        }

        val dialog = AlertDialog.Builder(this).create()
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(R.string.auto_update_later),
            minHeightDp = 42
        ) {
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(6)
        })
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(R.string.auto_update_download),
            textColor = Ja2GuiStyle.ACCENT,
            minHeightDp = 42
        ) {
            dialog.dismiss()
            startUpdateDownload(release, asset)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(6)
        })
        content.addView(buttonRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        dialog.setView(content)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }

    private fun startUpdateDownload(release: GitHubRelease, asset: GitHubAsset) {
        val versionName = release.tagName.removePrefix("v")

        val progressContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ja2GuiStyle.panelBackground(this@LauncherActivity)
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }

        val titleView = TextView(this).apply {
            text = getString(R.string.auto_update_downloading)
            setTextColor(Ja2GuiStyle.ACCENT)
            textSize = 16f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        progressContent.addView(titleView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val statusView = TextView(this).apply {
            text = getString(R.string.auto_update_downloading)
            setTextColor(Ja2GuiStyle.TEXT)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        progressContent.addView(statusView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val progressBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        progressContent.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(12)
        })

        val dialog = AlertDialog.Builder(this).create().apply {
            setView(progressContent)
            setCancelable(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }

        val cacheDir = cacheDir
        Thread({
            val apkFile = UpdateChecker.downloadApk(asset, versionName, cacheDir) { progress ->
                val pct = if (progress.totalBytes > 0) {
                    (progress.bytesDownloaded * 100 / progress.totalBytes).toInt()
                } else {
                    -1
                }
                runOnUiIfAlive {
                    if (pct >= 0) {
                        progressBar.isIndeterminate = false
                        progressBar.max = 100
                        progressBar.progress = pct
                        statusView.text = "$pct% — ${formatSizeMB(progress.bytesDownloaded)} / ${formatSizeMB(progress.totalBytes)}"
                    } else {
                        statusView.text = getString(R.string.auto_update_downloading) +
                                " (${formatSizeMB(progress.bytesDownloaded)})"
                    }
                }
            }

            if (apkFile == null) {
                runOnUiIfAlive {
                    dialog.dismiss()
                    showUpdateErrorDialog(getString(R.string.auto_update_download_failed))
                }
                return@Thread
            }

            val result = UpdateApkVerifier.verifyApk(this@LauncherActivity, apkFile, asset.size, asset.digest)
            runOnUiIfAlive {
                dialog.dismiss()
                if (result.passed) {
                    showInstallReadyDialog(apkFile, versionName)
                } else {
                    Log.w(activityLogTag, "APK verification failed: ${result.reason}")
                    showUpdateErrorDialog(getString(R.string.auto_update_verification_failed))
                }
            }
        }, "auto-update-download").apply { isDaemon = true }.start()
    }

    private fun showInstallReadyDialog(apkFile: File, version: String) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ja2GuiStyle.panelBackground(this@LauncherActivity)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.auto_update_install)
            setTextColor(Ja2GuiStyle.ACCENT)
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        content.addView(TextView(this).apply {
            text = getString(R.string.auto_update_available_message, version, formatSizeMB(apkFile.length()))
            setTextColor(Ja2GuiStyle.TEXT)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(16))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val dialog = AlertDialog.Builder(this).create()
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(R.string.auto_update_later),
            minHeightDp = 42
        ) {
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(6)
        })
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(R.string.auto_update_install),
            textColor = Ja2GuiStyle.ACCENT,
            minHeightDp = 42
        ) {
            dialog.dismiss()
            tryInstallApk(apkFile, version)
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(6)
        })
        content.addView(buttonRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        dialog.setView(content)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }

    private fun tryInstallApk(apkFile: File, version: String) {
        if (UpdateApkVerifier.needsInstallPermission(this)) {
            UpdateApkVerifier.savePendingApk(this, apkFile, version)
            showInstallPermissionDialog()
            return
        }

        val intent = UpdateApkVerifier.createInstallerIntent(this, apkFile)
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(activityLogTag, "Installer intent failed: ${e.message}")
                Toast.makeText(
                    this,
                    getString(R.string.auto_update_installer_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            Log.w(activityLogTag, "createInstallerIntent returned null")
            Toast.makeText(
                this,
                getString(R.string.auto_update_installer_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showInstallPermissionDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ja2GuiStyle.panelBackground(this@LauncherActivity)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.auto_update_permission_title)
            setTextColor(Ja2GuiStyle.ACCENT)
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        content.addView(TextView(this).apply {
            text = getString(R.string.auto_update_permission_message)
            setTextColor(Ja2GuiStyle.TEXT)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(16))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val dialog = AlertDialog.Builder(this).create()
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(R.string.cancel),
            minHeightDp = 42
        ) {
            dialog.dismiss()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(6)
        })
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(R.string.auto_update_open_settings),
            textColor = Ja2GuiStyle.ACCENT,
            minHeightDp = 42
        ) {
            dialog.dismiss()
            try {
                startActivity(UpdateApkVerifier.createInstallPermissionIntent(this@LauncherActivity))
            } catch (e: Exception) {
                Log.w(activityLogTag, "MANAGE_UNKNOWN_APP_SOURCES failed: ${e.message}, trying fallback")
                try {
                    startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                } catch (e2: Exception) {
                    Log.w(activityLogTag, "Security settings fallback also failed: ${e2.message}")
                    showUpdateErrorDialog(getString(R.string.auto_update_installer_failed))
                }
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(6)
        })
        content.addView(buttonRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        dialog.setView(content)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }

    private fun showUpdateErrorDialog(message: String) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ja2GuiStyle.panelBackground(this@LauncherActivity)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        content.addView(TextView(this).apply {
            text = message
            setTextColor(Ja2GuiStyle.TEXT)
            textSize = 14f
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val dialog = AlertDialog.Builder(this).create()
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(android.R.string.ok),
            minHeightDp = 42
        ) {
            dialog.dismiss()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44), 0.5f))
        content.addView(buttonRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(16)
        })

        dialog.setView(content)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }

    private fun showUpdateInfoDialog(message: String) {
        showUpdateErrorDialog(message)
    }

    private fun showUpdateUpToDateDialog(version: String) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ja2GuiStyle.panelBackground(this@LauncherActivity)
            setPadding(dp(20), dp(18), dp(20), dp(18))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.auto_update_up_to_date_title)
            setTextColor(Ja2GuiStyle.ACCENT)
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        content.addView(TextView(this).apply {
            text = getString(R.string.auto_update_up_to_date_message, version)
            setTextColor(Ja2GuiStyle.TEXT)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(16))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val dialog = AlertDialog.Builder(this).create()
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonRow.addView(Ja2GuiStyle.styledButton(
            this,
            getString(android.R.string.ok),
            minHeightDp = 42
        ) {
            dialog.dismiss()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44), 0.5f))
        content.addView(buttonRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(16)
        })

        dialog.setView(content)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }

    private fun formatSizeMB(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb < 1.0) {
            "%.1f MB".format(mb)
        } else if (mb < 10.0) {
            "%.1f MB".format(mb)
        } else {
            "%.0f MB".format(mb)
        }
    }

    private fun truncateText(text: String, maxLen: Int): String {
        return if (text.length <= maxLen) text else text.take(maxLen) + "..."
    }

    private fun saveCheatsJson() {
        val cheats = configurationModel.cheatConfig.value ?: CheatConfig.DEFAULT
        val parentDir = File(cheatsJsonPath).parentFile
        if (parentDir?.exists() != true) {
            parentDir?.mkdirs()
        }
        File(cheatsJsonPath).writeText(jsonFormat.encodeToString(cheats))
    }
}
