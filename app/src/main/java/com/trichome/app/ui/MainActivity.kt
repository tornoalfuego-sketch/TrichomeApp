package com.trichome.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.trichome.app.di.AppContainer
import com.trichome.app.ui.navigation.AppNavigation
import com.trichome.app.ui.theme.TrichomeTheme
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.container

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer
    private lateinit var themeState: TrichomeThemeState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = (application as com.trichome.app.TrichomeApp).appContainer
        themeState = TrichomeThemeState(container)
        enableEdgeToEdge()
        setContent {
            TrichomeAppRoot(themeState)
        }
    }
}

@Composable
private fun TrichomeAppRoot(themeState: TrichomeThemeState) {
    // Load persisted appearance once at startup (glass opacity, blur, theme, font).
    LaunchedEffect(Unit) {
        themeState.collectFromRepository()
    }

    TrichomeTheme(themeState = themeState) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppNavigation(themeState)
        }
    }
}