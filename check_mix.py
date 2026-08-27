import io

# Line 474: modifier = Modifier.weight(1f).fillMaxHeight() — this is inside a private fun MixerPanel
# that is called from the ConcertViewScreen Row() composable. weight() works in RowScope/ColumnScope.
# The MixerPanel is a composable itself, but it receives ScrollState parameter.
# The REAL error on line 474: Surface() doesn't have RowScope so weight wont work there.
# Instead, MixerPanel should return the Surface and the Row in ConcertViewScreen applies weight.
# Actually the issue is that weight is called on a private function's local Modifier context 
# outside the Row lambda where weight is defined.
# Let me check how MixerPanel is invoked.

with io.open("composeApp/src/commonMain/kotlin/ConcertView.kt", "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("MixerPanel(")
print(content[idx-200:idx+300])

