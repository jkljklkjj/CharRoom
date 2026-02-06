package com.example.charroom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.charroom.ui.theme.CharRoomTheme
import core.initKermit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initKermit()
        setContent {
            CharRoomTheme {
                App()
            }
        }
    }
}