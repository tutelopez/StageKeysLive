import io

# Check MainActivity.kt or wherever App() is called
import os

for fname in os.listdir("composeApp/src/androidMain/kotlin/com/midi/mainstage/"):
    print(fname)
