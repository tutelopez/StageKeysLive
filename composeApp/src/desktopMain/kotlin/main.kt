import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.midi.mainstage.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "MIDILIVE PRO - Mainstage Mockup",
        state = rememberWindowState(width = 1024.dp, height = 720.dp)
    ) {
        App()
    }
}
