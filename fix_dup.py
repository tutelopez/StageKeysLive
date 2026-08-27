import os

with open("composeApp/src/commonMain/kotlin/App.kt", "r", encoding="utf-8") as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if "val saveConcertsList: (List<Concert>) -> Unit = { list ->" in line:
        if skip:
            # this is the second occurrence, delete it
            continue
        skip = True
        new_lines.append(line)
    elif skip and "saveTextToFile(\"concerts.json\"," in line:
        pass # wait, it spans multiple lines. let's just delete by line number.
        
with open("composeApp/src/commonMain/kotlin/App.kt", "r", encoding="utf-8") as f:
    text = f.read()
    
# Find the second occurrence and remove it
first = text.find("val saveConcertsList")
if first != -1:
    second = text.find("val saveConcertsList", first + 1)
    if second != -1:
        end = text.find("}", second) + 1
        text = text[:second] + text[end:]
        with open("composeApp/src/commonMain/kotlin/App.kt", "w", encoding="utf-8") as f:
            f.write(text)

