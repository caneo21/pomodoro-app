package com.pomodoro.timer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pomodoro.timer.ui.TimerViewModel
import com.pomodoro.timer.ui.theme.PomodoroTheme
import com.pomodoro.timer.update.UpdateViewModel

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    private val viewModel: TimerViewModel by viewModels {
        TimerViewModel.Factory(applicationContext)
    }

    private val updateViewModel: UpdateViewModel by viewModels {
        UpdateViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            PomodoroTheme(colorTheme = uiState.colorTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PomodoroApp(viewModel, updateViewModel)
                }
            }
        }
    }
}
