package com.pomodoro.timer.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.Text
import com.pomodoro.timer.wear.theme.PomodoroWearTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PomodoroWearTheme {
                Text("Pomodoro", modifier = Modifier.fillMaxSize())
            }
        }
    }
}
