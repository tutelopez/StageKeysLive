import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tutelopezmusic.stagekeyslive.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "StageKeysLive",
        state = rememberWindowState(width = 1280.dp, height = 800.dp)
    ) {
        window.minimumSize = java.awt.Dimension(960, 600)
        App()
    }
}
