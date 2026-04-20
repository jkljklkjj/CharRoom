package com.chatlite.charroom

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.view.Gravity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple non-Compose fallback for the Android sample activity. This
        // avoids Compose/Kotlin/AGP version mismatches in the minimal build.
        val tv = TextView(this).apply {
            text = "CharRoom Android App"
            textSize = 20f
            gravity = Gravity.CENTER
        }
        setContentView(tv)
    }
}

