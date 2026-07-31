package com.ja2.reborn.ui.main

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ja2.reborn.*
import com.ja2.reborn.databinding.FragmentLauncherDataTabBinding
import java.io.File


class DataTabFragment : Fragment() {
    private var _binding: FragmentLauncherDataTabBinding? = null
    private val binding get() = _binding!!

    private lateinit var configurationModel: ConfigurationModel
    private lateinit var versions: Array<VanillaVersion>
    private lateinit var resolutionModes: Array<ResolutionMode>
    private lateinit var scalingQualities: Array<ScalingQuality>
    private lateinit var mouseModes: Array<MouseMode>
    private var updatingResolutionFields = false
    private val gameDirectoryPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { handleGameDirectoryPicked(it) }
        }
    private val saveGameDirectoryPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { handleSaveGameDirectoryPicked(it) }
        }
    private val externalizedJsonDirectoryPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { handleExternalizedJsonDirectoryPicked(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        configurationModel = ViewModelProvider(requireActivity())[ConfigurationModel::class.java]
        versions = (VanillaVersion::values)()
        resolutionModes = arrayOf(
            ResolutionMode.MODERN,
            ResolutionMode.HIGH_RES,
            ResolutionMode.RETRO
        )
        scalingQualities = arrayOf(
            ScalingQuality.NEAR_PERFECT,
            ScalingQuality.PERFECT,
            ScalingQuality.LINEAR
        )
        mouseModes = MouseMode.DISPLAY_ORDER

        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLauncherDataTabBinding.inflate(inflater, container, false)

        val spinnerLabels = versions.map { v: VanillaVersion -> LocalizationHelper.getVanillaVersionLabel(requireContext(), v) }
        val adapter: ArrayAdapter<String> =
            ArrayAdapter(this.requireContext(), R.layout.launcher_spinner_item, spinnerLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.gameVersionSpinner.adapter = adapter

        configurationModel.vanillaGameDir.observe(
            viewLifecycleOwner
        ) { vanillaGameDir ->
            if (vanillaGameDir != null) {
                binding.gameDirValueText.text = vanillaGameDir
            }
        }
        configurationModel.vanillaGameVersion.observe(
            viewLifecycleOwner
        ) { vanillaGameVersion ->
            val index = versions.indexOf(vanillaGameVersion)
            binding.gameVersionSpinner.setSelection(index)
        }
        configurationModel.saveGameDir.observe(
            viewLifecycleOwner
        ) { saveGameDir ->
            if (saveGameDir != null) {
                binding.saveGameDirValueText.text = saveGameDir
            }
        }
        configurationModel.externalizedJsonDir.observe(
            viewLifecycleOwner
        ) { externalizedJsonDir ->
            binding.externalizedJsonDirValueText.text = externalizedJsonDir
                ?: getString(R.string.externalized_json_dir_empty_text)
        }
        binding.gameDirChooseButton.setOnClickListener {
            gameDirectoryPicker.launch(null)
        }
        binding.gameVersionSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position >= 0 && position < versions.size) {
                        configurationModel.setVanillaGameVersion(versions[position])
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }
        binding.saveGameDirChooseButton.setOnClickListener {
            saveGameDirectoryPicker.launch(null)
        }
        binding.externalizedJsonDirChooseButton.setOnClickListener {
            externalizedJsonDirectoryPicker.launch(null)
        }

        val resolutionModeLabels = resolutionModes.map { LocalizationHelper.getResolutionModeLabel(requireContext(), it) }
        val resolutionModeAdapter: ArrayAdapter<String> =
            ArrayAdapter(this.requireContext(), R.layout.launcher_spinner_item, resolutionModeLabels)
        resolutionModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.resolutionModeSpinner.adapter = resolutionModeAdapter

        configurationModel.resolutionMode.observe(viewLifecycleOwner) { mode ->
            val index = resolutionModes.indexOf(mode)
            if (index >= 0) {
                binding.resolutionModeSpinner.setSelection(index)
            }
            if (configurationModel.expertSettings.value != true) {
                applyPresetResolution(mode)
            }
        }
        binding.resolutionModeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position >= 0 && position < resolutionModes.size) {
                        val mode = resolutionModes[position]
                        configurationModel.setResolutionMode(mode)
                        if (configurationModel.expertSettings.value != true) {
                            applyPresetResolution(mode)
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val scalingLabels = scalingQualities.map { LocalizationHelper.getScalingQualityLabel(requireContext(), it) }
        val scalingAdapter: ArrayAdapter<String> =
            ArrayAdapter(this.requireContext(), R.layout.launcher_spinner_item, scalingLabels)
        scalingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.scalingQualitySpinner.adapter = scalingAdapter

        configurationModel.scalingQuality.observe(viewLifecycleOwner) { scalingQuality ->
            val index = scalingQualities.indexOf(scalingQuality)
            if (index >= 0) {
                binding.scalingQualitySpinner.setSelection(index)
            }
        }
        binding.scalingQualitySpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position >= 0 && position < scalingQualities.size) {
                        configurationModel.setScalingQuality(scalingQualities[position])
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        val mouseModeLabels = mouseModes.map { LocalizationHelper.getMouseModeLabel(requireContext(), it) }
        val mouseModeAdapter: ArrayAdapter<String> =
            ArrayAdapter(this.requireContext(), R.layout.launcher_spinner_item, mouseModeLabels)
        mouseModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.mouseModeSpinner.adapter = mouseModeAdapter

        configurationModel.mouseMode.observe(viewLifecycleOwner) { mouseMode ->
            val index = mouseModes.indexOf(mouseMode)
            if (index >= 0) {
                binding.mouseModeSpinner.setSelection(index)
            }
        }
        binding.mouseModeSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (position >= 0 && position < mouseModes.size) {
                        configurationModel.setMouseMode(mouseModes[position])
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                }
            }

        configurationModel.resolution.observe(viewLifecycleOwner) { resolution ->
            updatingResolutionFields = true
            binding.resolutionWidthEditText.setText(resolution.width.toString())
            binding.resolutionHeightEditText.setText(resolution.height.toString())
            updatingResolutionFields = false
        }

        binding.resolutionWidthEditText.addTextChangedListener(resolutionTextWatcher)
        binding.resolutionHeightEditText.addTextChangedListener(resolutionTextWatcher)

        configurationModel.expertSettings.observe(viewLifecycleOwner) { enabled ->
            binding.expertSettingsCheckbox.isChecked = enabled
            setExpertControlsEnabled(enabled)
            if (!enabled) {
                applyStandardSettings()
            }
        }

        binding.expertSettingsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (configurationModel.expertSettings.value == isChecked) {
                return@setOnCheckedChangeListener
            }

            configurationModel.setExpertSettings(isChecked)
            if (isChecked) {
                showExpertSettingsWarning()
            } else {
                applyStandardSettings()
            }
        }

        return binding.root
    }

    private val resolutionTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable?) {
            if (updatingResolutionFields || configurationModel.expertSettings.value != true) {
                return
            }

            val width = binding.resolutionWidthEditText.text.toString().toUIntOrNull()
            val height = binding.resolutionHeightEditText.text.toString().toUIntOrNull()
            if (width != null && height != null && width > 0u && height > 0u) {
                configurationModel.setResolution(Resolution(width, height))
            }
        }
    }

    private fun applyPresetResolution(mode: ResolutionMode) {
        val launcherActivity = requireActivity()
        if (launcherActivity is LauncherActivity) {
            configurationModel.setResolution(launcherActivity.calculateResolutionForMode(mode))
        }
    }

    private fun applyStandardSettings() {
        val mode = configurationModel.resolutionMode.value ?: ResolutionMode.DEFAULT
        applyPresetResolution(mode)
        configurationModel.setScalingQuality(ScalingQuality.DEFAULT)
        configurationModel.setMouseMode(MouseMode.DEFAULT)
    }

    private fun setExpertControlsEnabled(enabled: Boolean) {
        binding.resolutionInfoTitle.isEnabled = !enabled
        binding.resolutionInfoText.isEnabled = !enabled
        binding.resolutionModeSpinner.isEnabled = !enabled
        binding.manualResolutionPanel.isEnabled = enabled
        binding.manualResolutionLabel.isEnabled = enabled
        binding.resolutionWidthEditText.isEnabled = enabled
        binding.resolutionHeightEditText.isEnabled = enabled
        binding.scalingQualitySpinner.isEnabled = enabled
        binding.mouseModeSpinner.isEnabled = enabled

        val alpha = if (enabled) 1.0f else 0.42f
        val presetAlpha = if (enabled) 0.42f else 1.0f
        binding.resolutionInfoTitle.alpha = presetAlpha
        binding.resolutionInfoText.alpha = presetAlpha
        binding.resolutionModeSpinner.alpha = presetAlpha
        binding.manualResolutionPanel.alpha = alpha
        binding.scalingQualityInfoTitle.alpha = alpha
        binding.scalingQualityInfoText.alpha = alpha
        binding.scalingQualitySpinner.alpha = alpha
        binding.mouseModeInfoTitle.alpha = alpha
        binding.mouseModeInfoText.alpha = alpha
        binding.mouseModeSpinner.alpha = alpha
    }

    private fun showExpertSettingsWarning() {
        val context = requireContext()
        val dialog = AlertDialog.Builder(context).create()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Ja2GuiStyle.panelBackground(context)
            setPadding(dp(22), dp(18), dp(22), dp(18))
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(context).apply {
            text = "!"
            gravity = Gravity.CENTER
            setTextColor(Ja2GuiStyle.ACCENT)
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            background = Ja2GuiStyle.buttonBackground(
                context,
                fillColor = Ja2GuiStyle.WARNING_FILL,
                strokeColor = Ja2GuiStyle.ACCENT
            )
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
            marginEnd = dp(12)
        })
        titleRow.addView(TextView(context).apply {
            text = getString(R.string.expert_settings_warning_title)
            setTextColor(Ja2GuiStyle.ACCENT)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(titleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        content.addView(TextView(context).apply {
            text = getString(R.string.expert_settings_warning_message)
            setTextColor(Ja2GuiStyle.TEXT)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, dp(18))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        content.addView(Ja2GuiStyle.styledButton(
            context,
            getString(R.string.expert_settings_confirm),
            textColor = Ja2GuiStyle.ACCENT,
            minHeightDp = 44
        ) {
            dialog.dismiss()
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(46)
        ))

        dialog.setView(content)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun handleGameDirectoryPicked(uri: Uri) {
        persistDirectoryPermission(uri)
        val path = uri.toExternalStoragePath()
        if (path == null || !File(path).exists()) {
            Toast.makeText(requireContext(), R.string.directory_picker_path_error, Toast.LENGTH_SHORT).show()
            return
        }

        GameDir.checkGameDirectoryForCommonMistakes(requireContext(), path) {
            configurationModel.setVanillaGameDir(path)
            (activity as? LauncherActivity)?.persistJA2Configuration()
        }
    }

    private fun handleSaveGameDirectoryPicked(uri: Uri) {
        persistDirectoryPermission(uri)
        val path = uri.toExternalStoragePath()
        if (path == null || !File(path).exists()) {
            Toast.makeText(requireContext(), R.string.directory_picker_path_error, Toast.LENGTH_SHORT).show()
            return
        }

        configurationModel.setSaveGameDir(path)
        (activity as? LauncherActivity)?.persistJA2Configuration()
    }

    private fun handleExternalizedJsonDirectoryPicked(uri: Uri) {
        persistDirectoryPermission(uri)
        val path = uri.toExternalStoragePath()
        val directory = path?.let(::File)
        if (directory?.isDirectory != true) {
            Toast.makeText(requireContext(), R.string.directory_picker_path_error, Toast.LENGTH_SHORT).show()
            return
        }

        configurationModel.setExternalizedJsonDir(directory.absolutePath)
        (activity as? LauncherActivity)?.persistJA2Configuration()
    }

    private fun persistDirectoryPermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    private fun Uri.toExternalStoragePath(): String? {
        if (authority != "com.android.externalstorage.documents") {
            return null
        }

        val documentId = DocumentsContract.getTreeDocumentId(this)
        val separatorIndex = documentId.indexOf(':')
        if (separatorIndex < 0) {
            return null
        }

        val volume = documentId.substring(0, separatorIndex)
        val relativePath = documentId.substring(separatorIndex + 1)
        val rootPath = when (volume.lowercase()) {
            "primary" -> Environment.getExternalStorageDirectory().absolutePath
            "home" -> File(Environment.getExternalStorageDirectory(), "Documents").absolutePath
            else -> "/storage/$volume"
        }

        return if (relativePath.isEmpty()) rootPath else File(rootPath, relativePath).absolutePath
    }

    companion object {
        private const val TAG = "DataTabFragment"
        private const val ARG_SECTION_NUMBER = "section_number"

        @JvmStatic
        fun newInstance(sectionNumber: Int): DataTabFragment {
            return DataTabFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }
}
