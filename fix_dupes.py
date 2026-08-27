import io, re

# 1. Remove duplicate PitchBendWheel and ModulationWheel from App.kt
with io.open("composeApp/src/commonMain/kotlin/App.kt", "r", encoding="utf-8") as f:
    content = f.read()

def remove_composable_function(text, func_name):
    pattern = rf'@Composable\s*\nfun {func_name}\('
    idx = 0
    while True:
        m = re.search(pattern, text[idx:])
        if not m:
            break
        start = idx + m.start()
        # find matching braces
        brace_idx = text.find('{', start)
        if brace_idx == -1:
            break
        depth = 1
        i = brace_idx + 1
        while i < len(text) and depth > 0:
            if text[i] == '{': depth += 1
            elif text[i] == '}': depth -= 1
            i += 1
        # We want to remove from @Composable down to closing brace
        text = text[:start] + text[i:]
        idx = start
    return text

content = remove_composable_function(content, "PitchBendWheel")
content = remove_composable_function(content, "ModulationWheel")

with io.open("composeApp/src/commonMain/kotlin/App.kt", "w", encoding="utf-8") as f:
    f.write(content)

# 2. Fix Widgets.kt - remove duplicate OctaveButton, fix Metronome icon
with io.open("composeApp/src/commonMain/kotlin/Widgets.kt", "r", encoding="utf-8") as f:
    widgets = f.read()

widgets = widgets.replace("TablerIcons.Metronome", "TablerIcons.Music")

with io.open("composeApp/src/commonMain/kotlin/Widgets.kt", "w", encoding="utf-8") as f:
    f.write(widgets)

# 3. ConcertView.kt - fix weight(1f) lambda issue in coroutineScope usage (line 474)
with io.open("composeApp/src/commonMain/kotlin/ConcertView.kt", "r", encoding="utf-8") as f:
    cv = f.read()

# line 474: weight is Float not a lambda — the issue is a trailing lambda
# lets check the keyboardScrollState usage
print("PitchBendWheel count in ConcertView:", cv.count("fun PitchBendWheel"))
print("PitchBendWheel calls in ConcertView:", cv.count("PitchBendWheel("))

