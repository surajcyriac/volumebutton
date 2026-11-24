package com.volumebutton

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.volumebutton.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsStore: SettingsStore
    private var isServiceRunningCached: Boolean = false

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == OverlayService.ACTION_SERVICE_STATE_CHANGED) {
                isServiceRunningCached = intent.getBooleanExtra(OverlayService.EXTRA_IS_RUNNING, false)
                settingsStore.isServiceRunning = isServiceRunningCached
                updateUiState()
            }
        }
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(this, "Notifications disabled. The service status notification won't show.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsStore = SettingsStore(this)
        isServiceRunningCached = settingsStore.isServiceRunning
        setupListeners()
        loadSettingsToUi()

        val filter = IntentFilter(OverlayService.ACTION_SERVICE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceStateReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        queryServiceState()
        updateUiState()
    }

    @SuppressLint("BatteryLife")
    private fun setupListeners() {
        binding.btnGrantPermission.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.btnToggleService.setOnClickListener {
            if (isServiceRunningCached) {
                stopOverlayService()
            } else {
                startOverlayService()
            }
            updateUiState()
        }

        // Settings switches
        binding.switchStartOnBoot.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.startOnBoot = isChecked
            notifyServiceSettingsChanged()
        }

        binding.switchVolumePopup.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.showVolumePopup = isChecked
            notifyServiceSettingsChanged()
        }

        binding.switchHaptics.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.hapticFeedback = isChecked
            notifyServiceSettingsChanged()
        }

        // Sliders
        binding.sliderOpacity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val opacity = value / 100f
                settingsStore.idleOpacity = opacity
                binding.tvOpacityLabel.text = "Idle Opacity: ${value.toInt()}%"
                notifyServiceSettingsChanged()
            }
        }

        // Battery optimization
        binding.btnBatteryOptimization.setOnClickListener {
            openBatteryOptimizationSettings()
        }
    }

    private fun loadSettingsToUi() {
        binding.switchStartOnBoot.isChecked = settingsStore.startOnBoot
        binding.switchVolumePopup.isChecked = settingsStore.showVolumePopup
        binding.switchHaptics.isChecked = settingsStore.hapticFeedback

        val opacityPercent = (settingsStore.idleOpacity * 100).toInt()
        binding.sliderOpacity.value = opacityPercent.toFloat().coerceIn(20f, 100f)
        binding.tvOpacityLabel.text = "Idle Opacity: $opacityPercent%"
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open battery settings directly", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun startOverlayService() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        isServiceRunningCached = true
        settingsStore.isServiceRunning = true

        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_IDLE_OPACITY, settingsStore.idleOpacity)
            putExtra(OverlayService.EXTRA_SHOW_VOLUME_POPUP, settingsStore.showVolumePopup)
            putExtra(OverlayService.EXTRA_HAPTIC_FEEDBACK, settingsStore.hapticFeedback)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun queryServiceState() {
        val queryIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_QUERY_STATE
        }
        try {
            startService(queryIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun notifyServiceSettingsChanged() {
        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE_SETTINGS
            putExtra(OverlayService.EXTRA_IDLE_OPACITY, settingsStore.idleOpacity)
            putExtra(OverlayService.EXTRA_SHOW_VOLUME_POPUP, settingsStore.showVolumePopup)
            putExtra(OverlayService.EXTRA_HAPTIC_FEEDBACK, settingsStore.hapticFeedback)
        }
        try {
            startService(serviceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopOverlayService() {
        isServiceRunningCached = false
        settingsStore.isServiceRunning = false
        val serviceIntent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(serviceIntent)
        stopService(serviceIntent)
    }

    private fun updateUiState() {
        val hasPermission = hasOverlayPermission()

        if (hasPermission) {
            binding.tvPermissionTitle.visibility = View.GONE
            binding.tvPermissionDesc.visibility = View.GONE
            binding.btnGrantPermission.visibility = View.GONE
            binding.tvPermissionBadge.visibility = View.VISIBLE
            binding.cardServiceControl.visibility = View.VISIBLE
            binding.cardPreferences.visibility = View.VISIBLE
        } else {
            binding.tvPermissionTitle.visibility = View.VISIBLE
            binding.tvPermissionDesc.visibility = View.VISIBLE
            binding.btnGrantPermission.visibility = View.VISIBLE
            binding.tvPermissionBadge.visibility = View.GONE
            binding.cardServiceControl.visibility = View.GONE
            binding.cardPreferences.visibility = View.GONE
        }

        if (isServiceRunningCached) {
            binding.tvServiceStatus.text = getString(R.string.service_running_status)
            binding.tvServiceStatus.setTextColor(getColor(R.color.success))
            binding.btnToggleService.text = getString(R.string.btn_stop_service)
            binding.btnToggleService.setBackgroundColor(getColor(R.color.danger))
        } else {
            binding.tvServiceStatus.text = getString(R.string.service_stopped_status)
            binding.tvServiceStatus.setTextColor(getColor(R.color.on_surface))
            binding.btnToggleService.text = getString(R.string.btn_start_service)
            binding.btnToggleService.setBackgroundColor(getColor(R.color.primary))
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(serviceStateReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
        System.gc()
    }
}
