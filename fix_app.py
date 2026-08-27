import sys

def fix_app():
    with open("composeApp/src/commonMain/kotlin/App.kt", "r", encoding="utf-8") as f:
        lines = f.readlines()
        
    # We will build a new list of lines
    new_lines = []
    for i, line in enumerate(lines):
        idx = i + 1
        
        if 461 <= idx <= 464:
            continue
            
        new_lines.append(line)
        
        if idx == 396:
            new_lines.append("""                    is MidiTarget.NextPatch -> {
                        if (floatValue > 0f) {
                            val concert = activeConcert
                            if (concert != null) {
                                val next = (selectedPatchIndex + 1).coerceAtMost(concert.patches.size - 1)
                                if (next != selectedPatchIndex) applyPatch(next)
                            }
                        }
                    }
                    is MidiTarget.PreviousPatch -> {
                        if (floatValue > 0f) {
                            val prev = (selectedPatchIndex - 1).coerceAtLeast(0)
                            if (prev != selectedPatchIndex) applyPatch(prev)
                        }
                    }
""")
        elif idx == 2105:
            new_lines.append("""                is MidiTarget.NextPatch -> "Siguiente Patch"
                is MidiTarget.PreviousPatch -> "Patch Anterior"
""")

    with open("composeApp/src/commonMain/kotlin/App.kt", "w", encoding="utf-8") as f:
        f.writelines(new_lines)

if __name__ == "__main__":
    fix_app()
