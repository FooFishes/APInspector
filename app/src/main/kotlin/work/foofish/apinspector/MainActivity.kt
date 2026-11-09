package work.foofish.apinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import work.foofish.apinspector.navigation.APInspectorNavDisplay
import work.foofish.apinspector.ui.theme.APInspectorTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            APInspectorTheme {
                APInspectorNavDisplay()
            }
        }
    }
}
