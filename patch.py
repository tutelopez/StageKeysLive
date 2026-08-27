import os

def replace_in_file(filepath, replacements):
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()
    
    for search, replace in replacements:
        if search not in content:
            print(f"ERROR: Could not find '{search[:50]}...' in {filepath}")
        else:
            content = content.replace(search, replace)
            
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)

replacements = [
    (
"""    var showCreateConcertDialog by remember { mutableStateOf(false) }
    var concertToEdit by remember { mutableStateOf<Concert?>(null) }
    var newConcertName by remember { mutableStateOf("") }""",
"""    var showCreateConcertDialog by remember { mutableStateOf(false) }
    var concertToEdit by remember { mutableStateOf<Concert?>(null) }
    var newConcertName by remember { mutableStateOf("") }

    // Import / Export states
    var showPackagePicker by remember { mutableStateOf(false) }
    var concertToExport by remember { mutableStateOf<Concert?>(null) }
    var patchToExport by remember { mutableStateOf<PatchState?>(null) }

    SkPackagePicker(
        show = showPackagePicker,
        onPackageSelected = { concert, patch ->
            showPackagePicker = false
            if (concert != null) {
                val newList = concerts + concert
                saveConcertsList(newList)
            } else if (patch != null && activeConcert != null) {
                val updatedConcert = activeConcert!!.copy(
                    patches = activeConcert!!.patches + patch,
                    lastModified = System.currentTimeMillis()
                )
                val newList = concerts.map { if (it.id == updatedConcert.id) updatedConcert else it }
                saveConcertsList(newList)
                activeConcert = updatedConcert
            }
        }
    )

    PackageExporter(
        concertToExport = concertToExport,
        patchToExport = patchToExport,
        onExportComplete = {
            concertToExport = null
            patchToExport = null
        }
    )"""
    ),
    (
"""fun DashboardScreen(
    concerts: List<Concert>,
    onCreateConcertClick: () -> Unit,
    onEditConcertClick: (Concert) -> Unit,
    onOpenLastConcertClick: () -> Unit,
    onSelectConcert: (Concert) -> Unit,
    onDeleteConcert: (Concert) -> Unit,
    onSettingsClick: () -> Unit
) {""",
"""fun DashboardScreen(
    concerts: List<Concert>,
    onCreateConcertClick: () -> Unit,
    onEditConcertClick: (Concert) -> Unit,
    onOpenLastConcertClick: () -> Unit,
    onSelectConcert: (Concert) -> Unit,
    onDeleteConcert: (Concert) -> Unit,
    onExportConcertClick: (Concert) -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit
) {"""
    ),
    (
"""            Text(
                text = "MIS CONCIERTOS RECIENTES",
                color = TextDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )""",
"""            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MIS CONCIERTOS RECIENTES",
                    color = TextDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Importar", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }"""
    ),
    (
"""                        IconButton(onClick = { onEditConcertClick(concert) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Edit", tint = TextDark, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { onDeleteConcert(concert) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }""",
"""                        IconButton(onClick = { onExportConcertClick(concert) }, modifier = Modifier.size(24.dp)) {
                            Text("↑", color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { onEditConcertClick(concert) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Edit", tint = TextDark, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { onDeleteConcert(concert) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                        }"""
    ),
    (
"""                onDeleteConcert = { concert ->
                    val newList = concerts.filter { it.id != concert.id }
                    saveConcertsList(newList)
                    if (activeConcert?.id == concert.id) {
                        stopConcert()
                        activeConcert = null
                    }
                },
                onSettingsClick = { showSettingsDialog = true }""",
"""                onDeleteConcert = { concert ->
                    val newList = concerts.filter { it.id != concert.id }
                    saveConcertsList(newList)
                    if (activeConcert?.id == concert.id) {
                        stopConcert()
                        activeConcert = null
                    }
                },
                onExportConcertClick = { concertToExport = it },
                onImportClick = { showPackagePicker = true },
                onSettingsClick = { showSettingsDialog = true }"""
    ),
    (
"""fun ConcertViewScreen(
    concert: Concert,
    selectedPatchIndex: Int,
    onSelectPatch: (Int) -> Unit,
    onAddPatchClick: () -> Unit,
    onEditPatchClick: (PatchState) -> Unit,
    onDeletePatch: (PatchState) -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currentConnectedDevices: List<String>,
    midiActivityIndicator: Boolean,
) {""",
"""fun ConcertViewScreen(
    concert: Concert,
    selectedPatchIndex: Int,
    onSelectPatch: (Int) -> Unit,
    onAddPatchClick: () -> Unit,
    onEditPatchClick: (PatchState) -> Unit,
    onDeletePatch: (PatchState) -> Unit,
    onExportPatchClick: (PatchState) -> Unit,
    onImportPatchClick: () -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    currentConnectedDevices: List<String>,
    midiActivityIndicator: Boolean,
) {"""
    ),
    (
"""                    IconButton(
                        onClick = onAddPatchClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add patch", tint = NeonGreen, modifier = Modifier.size(16.dp))
                    }""",
"""                    IconButton(
                        onClick = onImportPatchClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Text("↓", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onAddPatchClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add patch", tint = NeonGreen, modifier = Modifier.size(16.dp))
                    }"""
    ),
    (
"""                            IconButton(onClick = { onEditPatchClick(patch) }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = "Edit", tint = TextDark, modifier = Modifier.size(14.dp))
                            }""",
"""                            IconButton(onClick = { onExportPatchClick(patch) }, modifier = Modifier.size(20.dp)) {
                                Text("↑", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { onEditPatchClick(patch) }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = "Edit", tint = TextDark, modifier = Modifier.size(14.dp))
                            }"""
    ),
    (
"""                    onDeletePatch = { patch ->
                        val updatedPatches = concert.patches.filter { it.id != patch.id }
                        val updatedConcert = concert.copy(patches = updatedPatches, lastModified = System.currentTimeMillis())
                        val newList = concerts.map { if (it.id == concert.id) updatedConcert else it }
                        saveConcertsList(newList)
                        activeConcert = updatedConcert
                        if (selectedPatchIndex >= updatedPatches.size) {
                            selectedPatchIndex = (updatedPatches.size - 1).coerceAtLeast(0)
                        }
                    },
                    onBackClick = { currentScreen = ScreenState.DASHBOARD },
                    onSettingsClick = { showSettingsDialog = true },
                    currentConnectedDevices = currentConnectedDevices,
                    midiActivityIndicator = midiActivityIndicator,
                )""",
"""                    onDeletePatch = { patch ->
                        val updatedPatches = concert.patches.filter { it.id != patch.id }
                        val updatedConcert = concert.copy(patches = updatedPatches, lastModified = System.currentTimeMillis())
                        val newList = concerts.map { if (it.id == concert.id) updatedConcert else it }
                        saveConcertsList(newList)
                        activeConcert = updatedConcert
                        if (selectedPatchIndex >= updatedPatches.size) {
                            selectedPatchIndex = (updatedPatches.size - 1).coerceAtLeast(0)
                        }
                    },
                    onExportPatchClick = { patchToExport = it },
                    onImportPatchClick = { showPackagePicker = true },
                    onBackClick = { currentScreen = ScreenState.DASHBOARD },
                    onSettingsClick = { showSettingsDialog = true },
                    currentConnectedDevices = currentConnectedDevices,
                    midiActivityIndicator = midiActivityIndicator,
                )"""
    )
]

replace_in_file("composeApp/src/commonMain/kotlin/App.kt", replacements)
