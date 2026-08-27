import io

# Fix: MixerPanel is called inside a Row() - weight(1f) works fine there as RowScope.
# But the error says ConcertView.kt:474 - Surface(modifier = Modifier.weight(1f)) is 
# inside a private @Composable fun MixerPanel which has Row context from the caller.
# 
# Actually, Compose works: if MixerPanel is called inside a Row{} block, then inside
# MixerPanel you can't use Modifier.weight() because that requires RowScope on the modifier.
# The Surface inside MixerPanel doesn't have access to RowScope.
#
# Fix: pass the modifier from the call site into MixerPanel or use fillMaxWidth/fillMaxSize.
# Let's just change the weight(1f) to fillMaxWidth() inside MixerPanel surface.

with io.open("composeApp/src/commonMain/kotlin/ConcertView.kt", "r", encoding="utf-8") as f:
    content = f.read()

# Fix the MixerPanel surface modifier
content = content.replace(
    "        modifier = Modifier.weight(1f).fillMaxHeight()\n    ) {\n        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {\n            Text(\n                \"MIXER\"",
    "        modifier = Modifier.fillMaxWidth().fillMaxHeight()\n    ) {\n        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {\n            Text(\n                \"MIXER\""
)

# Actually we need to use weight - let's check the function signature and add a modifier parameter
# Better: Add modifier param to MixerPanel
content = content.replace(
    "private fun MixerPanel(\n    channels: List<ChannelStripState>,",
    "private fun MixerPanel(\n    modifier: Modifier = Modifier,\n    channels: List<ChannelStripState>,"
)
# And apply it to the Surface
content = content.replace(
    "    Surface(\n        shape = RoundedCornerShape(20.dp),\n        color = Color(0xFF161620),\n        modifier = Modifier.fillMaxWidth().fillMaxHeight()",
    "    Surface(\n        shape = RoundedCornerShape(20.dp),\n        color = Color(0xFF161620),\n        modifier = modifier.fillMaxHeight()"
)
# And pass weight from call site
content = content.replace(
    "                MixerPanel(\n                    channels = concert.channels,",
    "                MixerPanel(\n                    modifier = Modifier.weight(1f),\n                    channels = concert.channels,"
)

with io.open("composeApp/src/commonMain/kotlin/ConcertView.kt", "w", encoding="utf-8") as f:
    f.write(content)

print("Fixed MixerPanel modifier")
