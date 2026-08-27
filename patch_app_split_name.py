import os

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Update the call to SplitKeyboardSettingsScreen
old_call = """                                          SplitKeyboardSettingsScreen(
                                              concert = concert,"""
new_call = """                                          SplitKeyboardSettingsScreen(
                                              concert = concert,
                                              selectedPatchName = if (settingsOpenedFromConcert && selectedPatchIndex in concert.patches.indices) concert.patches[selectedPatchIndex].name else null,"""
content = content.replace(old_call, new_call)

# Update the definition of SplitKeyboardSettingsScreen
old_def = """@Composable
fun SplitKeyboardSettingsScreen(
    concert: Concert,
    onUpdateRange: (Int, Int, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("RANGOS DE TECLADO Y SPLITS (OCTAVAS A0 - C8)", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Visualiza y modifica las zonas de las teclas activas para cada archivo SF2 cargado.",
            color = TextDark,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )"""

new_def = """@Composable
fun SplitKeyboardSettingsScreen(
    concert: Concert,
    selectedPatchName: String? = null,
    onUpdateRange: (Int, Int, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("RANGOS DE TECLADO Y SPLITS (OCTAVAS A0 - C8)", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        if (selectedPatchName != null) {
            Text(
                "Editando zonas del patch: $selectedPatchName",
                color = NeonGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(
            "Visualiza y modifica las zonas de las teclas activas para cada archivo SF2 cargado.",
            color = TextDark,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )"""

content = content.replace(old_def, new_def)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
