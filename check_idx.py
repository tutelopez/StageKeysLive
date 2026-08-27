import re
import io

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

print("Dashboard: ", content.find("fun DashboardScreen("))
print("Concert: ", content.find("fun ConcertViewScreen("))
print("Channel: ", content.find("fun ChannelStripItem("))

