package com.pomodoro.timer.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.MaterialTheme
import com.pomodoro.timer.wear.theme.PomodoroWearTheme
import com.pomodoro.timer.wear.ui.WearTimerScreen
import com.pomodoro.timer.wear.ui.WearTimerViewModel

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    private val viewModel: WearTimerViewModel by viewModels {
        WearTimerViewModel.Factory(applicationContext)
    }

    private val isAmbient = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val ambientObserver = AmbientLifecycleObserver(
            this,
            object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                    isAmbient.value = true
                }

                override fun onExitAmbient() {
                    isAmbient.value = false
                }
            }
        )
        lifecycle.addObserver(ambientObserver)

        setContent {
            PomodoroWearTheme {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val ambient by isAmbient
                    WearTimerScreen(
                        uiState = uiState,
                        isAmbient = ambient,
                        onStart = viewModel::start,
                        onPause = viewModel::pause,
                        onReset = viewModel::reset
                    )
                }
            }
        }
    }
}
