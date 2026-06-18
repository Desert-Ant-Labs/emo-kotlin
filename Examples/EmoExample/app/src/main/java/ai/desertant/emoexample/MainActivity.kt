package ai.desertant.emoexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** App entry point — mirrors `EmoExampleApp` in the iOS example. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmoExampleTheme {
                // Apply safe-area (status/navigation bar) padding once at the root.
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    ContentView()
                }
            }
        }
    }
}

// Accent color matches the iOS example's AccentColor (srgb 0.98, 0.72, 0.20).
private val Accent = Color(0xFFFAB833)

@Composable
fun EmoExampleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(primary = Accent, secondary = Accent)
    } else {
        lightColorScheme(primary = Accent, secondary = Accent)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
