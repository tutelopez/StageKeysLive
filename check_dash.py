import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("fun DashboardScreen(")
end_idx = content.find("fun ConcertViewScreen(", idx)

print(content[idx:idx+1500])

