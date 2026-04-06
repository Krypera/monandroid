package com.monandroido.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.monandroido.app.ui.MonandroidoAppContent
import com.monandroido.app.ui.theme.MonandroidoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MonandroidoTheme {
                MonandroidoAppContent(application = applicationContext as MonandroidoApplication)
            }
        }
    }
}
