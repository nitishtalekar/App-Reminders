package app.kept

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.kept.ui.AppRoot
import app.kept.ui.AppViewModel
import app.kept.ui.KeptTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels { AppViewModel.Factory(app) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { KeptTheme { AppRoot(vm) } }
    }

    override fun onStart() {
        super.onStart()
        // Reopened after being unused: make sure widget, alarms and notifications match the data and today's date.
        app.scope.launch { app.syncSystem() }
    }
}
