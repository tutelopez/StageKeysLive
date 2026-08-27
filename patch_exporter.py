import os

filepath = "composeApp/src/androidMain/kotlin/com/midi/mainstage/PackageExporter.android.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Fix Point 5 (fileName logic)
old_filename_logic = """val fileName = if (isConcert) "${concertToExport!!.name.replace(" ", "_")}.skconcert" else "${patchToExport!!.name.replace(" ", "_")}.skpatch"
                
                // Use cache dir for temporary zip creation
                val zipFile = File(context.cacheDir, fileName)"""
                
new_filename_logic = """val baseName = (concertToExport?.name ?: patchToExport?.name ?: "export")
                    .replace(Regex("[^A-Za-z0-9_\\\\-]"), "_")
                val extension = if (isConcert) ".skconcert" else ".skpatch"
                val zipFile = File(context.cacheDir, "$baseName$extension")"""
                
content = content.replace(old_filename_logic, new_filename_logic)

# Fix Point 1 (soundfonts entry name)
old_soundfonts_logic = """zout.putNextEntry(ZipEntry("soundfonts/"))"""
new_soundfonts_logic = """zout.putNextEntry(ZipEntry("soundfonts/${sf2File.name}"))"""

content = content.replace(old_soundfonts_logic, new_soundfonts_logic)

# Fix Point 2 (FileProvider authority + feedback)
old_uri_logic = """val uri: Uri = FileProvider.getUriForFile(context, ".fileprovider", zipFile)"""
new_uri_logic = """val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)"""

content = content.replace(old_uri_logic, new_uri_logic)

old_catch_logic = """} catch (e: Exception) {
                e.printStackTrace()
            }"""
new_catch_logic = """} catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Error al exportar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }"""

content = content.replace(old_catch_logic, new_catch_logic)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
