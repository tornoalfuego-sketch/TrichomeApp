package com.trichome.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.trichome.app.di.AppContainer
import com.trichome.app.ui.navigation.AppNavigation
import com.trichome.app.ui.screens.onboarding.OnboardingScreen
import com.trichome.app.ui.theme.TrichomeTheme
import com.trichome.app.ui.theme.TrichomeThemeState
import com.trichome.app.viewmodel.container
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    // Load persisted appearance once at startup (theme, accent, font).
    LaunchedEffect(Unit) {
        themeState.collectFromRepository()
    }

    TrichomeTheme(themeState = themeState) {
        val container = container()
        val scope = rememberCoroutineScope()
        val onboarding = container.onboarding

        // `null` until the persisted counter has been read, so the main UI is
        // never covered by a flash of onboarding on launches where it is hidden.
        var showOnboarding by remember { mutableStateOf<Boolean?>(null) }

        LaunchedEffect(Unit) {
            if (onboarding.state.first().shouldShow) {
                onboarding.registerLaunch()
                showOnboarding = true
            } else {
                showOnboarding = false
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
                AppNavigation(themeState)

                AnimatedVisibility(
                    visible = showOnboarding == true,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    OnboardingScreen(
                        themeState = themeState,
                        onFinish = {
                            scope.launch { onboarding.complete() }
                            showOnboarding = false
                        }
                    )
                }
            }
        }
    }
}
