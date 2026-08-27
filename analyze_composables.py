import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# We need to find App(), DashboardScreen(), ConcertViewScreen(), ChannelStripItem(), VolumeFader(), LevelMeter(), AddChannelButton(), EmptyChannelPlaceholder(), MasterOutputChannelItem(), MetronomeChannelItem()
# We will use simple regex matching to find the start of each and then use brace counting to get the whole block.
def get_block(text, start_idx):
    idx = start_idx
    braces = 0
    while idx < len(text) and text[idx] != '{':
        idx += 1
    if idx == len(text):
        return None
    
    braces = 1
    idx += 1
    while idx < len(text) and braces > 0:
        if text[idx] == '{':
            braces += 1
        elif text[idx] == '}':
            braces -= 1
        idx += 1
    return text[start_idx:idx]

targets = ["fun App(", "fun DashboardScreen(", "fun ConcertViewScreen(", "fun ChannelStripItem(", "fun VolumeFader(", "fun LevelMeter(", "fun AddChannelButton(", "fun EmptyChannelPlaceholder(", "fun MasterOutputChannelItem(", "fun MetronomeChannelItem("]

for t in targets:
    idx = content.find(t)
    if idx != -1:
        # Move back to include @Composable if it exists
        composable_idx = content.rfind("@Composable", 0, idx)
        if composable_idx != -1 and (idx - composable_idx) < 100:
            idx = composable_idx
        block = get_block(content, idx)
        print(f"--- {t} --- (len: {len(block) if block else 'None'})")

