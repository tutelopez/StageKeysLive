import re
import io

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Extract the existing DashboardScreen
idx = content.find("fun DashboardScreen(")
end_idx = content.find("fun ConcertViewScreen(", idx)

old_dash = content[idx:end_idx]

new_dash = """@Composable
fun DashboardScreen(
    concerts: List<Concert>,
    onCreateConcertClick: () -> Unit,
    onEditConcertClick: (Concert) -> Unit,
    onOpenLastConcertClick: () -> Unit,
    onSelectConcert: (Concert) -> Unit,
    onDeleteConcert: (Concert) -> Unit,
    onExportConcertClick: (Concert) -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "STAGEKEYS LIVE",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                
                // Welcome text
                Text(
                    text = "Ready to play \u266B",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.width(24.dp))
                
                IconButton(onClick = onSettingsClick) {
                    Icon(TablerIcons.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onBackground)
                }
            }

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onCreateConcertClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = DarkBackground),
                    modifier = Modifier.height(56.dp).weight(1f)
                ) {
                    Icon(TablerIcons.Plus, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nuevo Concierto", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onOpenLastConcertClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.height(56.dp).weight(1f)
                ) {
                    Icon(TablerIcons.PlayerPlay, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abrir Último", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.height(56.dp).weight(1f)
                ) {
                    Icon(TablerIcons.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importar Concierto", fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "TUS CONCIERTOS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (concerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(TablerIcons.Music, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No hay conciertos creados.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(concerts) { concert ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onSelectConcert(concert) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(TablerIcons.Music, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(concert.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${concert.patches.size} Patches", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { onEditConcertClick(concert) }) {
                                    Icon(TablerIcons.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onExportConcertClick(concert) }) {
                                    Icon(TablerIcons.Share, contentDescription = "Exportar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteConcert(concert) }) {
                                    Icon(TablerIcons.Trash, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

content = content.replace(old_dash, new_dash)

with io.open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
