import os

filepath = "composeApp/src/androidMain/kotlin/com/midi/mainstage/MainActivity.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

imports = """import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat"""
content = content.replace("""import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent""", imports)

on_create = """    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system bars (Full Screen Immersive Mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE"""
        
content = content.replace("""    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)""", on_create)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
