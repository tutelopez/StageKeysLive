import re

path = 'composeApp/src/commonMain/kotlin/Dashboard.kt'
content = open(path, 'r', encoding='utf-8').read()

content = re.sub(r'"Buenos d.*as.*"', '"Buenos días, hora de tocar \U0001f304"', content)
content = re.sub(r'"Buenas tardes.*"', '"Buenas tardes, hora de tocar \U0001f3b9"', content)
content = re.sub(r'"Buenas noches.*"', '"Buenas noches, hora de tocar \U0001f319"', content)
content = re.sub(r'"Hora de tocar.*" *//', '"Hora de tocar \U0001f3b9" //', content)
content = re.sub(r'"Abrir.*ltimo"', '"Abrir Último"', content)

open(path, 'w', encoding='utf-8', newline='\n').write(content)
