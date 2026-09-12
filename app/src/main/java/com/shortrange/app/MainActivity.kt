package com.shortrange.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.shortrange.app.ui.navigation.ShortRangeNavHost
import com.shortrange.app.ui.theme.ShortRangeTheme
import com.shortrange.app.ui.theme.TechnicalWhite

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShortRangeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TechnicalWhite
                ) {
                    ShortRangeNavHost()
                }
            }
        }
    }
}
