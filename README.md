# StageKeysLive 🎹

**StageKeysLive** es una estación de trabajo de rendimiento en vivo de alto rendimiento para tecladistas (diseñada al estilo de Mainstage) desarrollada con **Compose Multiplatform** (Kotlin). Está optimizada tanto para músicos sesionistas de escenario (Rock, Pop, Jazz) como para ministerios de alabanza en iglesias (Gospel, Worship) que buscan un "cerebro" de sintetizadores estable y de baja latencia en Android y Desktop.

---

## 🚀 Características Clave

### 1. Mezclador Multicanal Dinámico (Mixer)
* **Soporte de 8 Canales + Master Out**: Mezclador con scroll horizontal donde puedes añadir canales al vuelo pulsando el botón `+`.
* **Canal de Salida General (Master OUT)**: Controla el volumen general del sintetizador y cuenta con un vúmetro L-R estéreo mapeable.
* **Glow Neón Reactivo**: Los botones de **Mute (M)** y **Solo (S)** se iluminan en colores neón de alta visibilidad cuando están activos.
* **Paleta de 8 Colores**: Personaliza visualmente cada strip de canal seleccionando colores neón llamativos directamente desde una cuadrícula.

### 2. Motor de Audio de Baja Latencia y MIDI Físico
* **C++ JNI & Oboe (Android)**: Driver nativo en C++ integrado con **Google Oboe** para salida de audio flotante estéreo de latencia ultra-baja y soporte nativo para carga y render de archivos SoundFont (`.sf2`).
* **AndroidMidiManager**: Escucha e interactúa en tiempo real con teclados controladores MIDI físicos conectados por USB OTG o Bluetooth.
* **Mapeo de Controles (MIDI Learn)**: Asigna perillas y faders de tu teclado físico directamente a los faders de canal o master y a los potenciómetros en pantalla.
* **Java Midi System (Desktop Fallback)**: Motor de audio integrado en Desktop utilizando `javax.sound.midi` para que puedas probar parches, splits y sonidos directamente en tu PC sin hardware adicional.

### 3. Teclado de 8 Octavas y Controles de Expresión
* **Teclado Scrollable Expandido**: Abarca de A0 a C8 (88/97 teclas) con botones de desplazamiento de octava rápida (`OCT-` y `OCT+`).
* **Pitch Bend con Autoretorno**: Rueda de modulación de tono que regresa por muelle al valor central (0) al soltar el cursor o arrastre táctil.
* **Pedal de Sustain**: Conmutador virtual que mantiene sostenidas las notas físicamente liberadas hasta que el pedal se apague.

### 4. Herramientas Globales
* **Metrónomo con Volumen**: Control de BPM (40-240) con LED de pulso y slider de volumen independiente para clicks de metrónomo.
* **Grabador MIDI**: Graba secuencias de notas en vivo en una cola de eventos de alta precisión y reprodúcelas al instante con el botón `PLAY REC`.
* **Editor Visual de Zonas (Split/Layer)**: Asigna rangos específicos del teclado a cada canal SF2 mediante controles deslizantes de notas mínimas y máximas.

---

## 🛠️ Estructura del Proyecto

El proyecto está organizado como un desarrollo de **Kotlin Multiplatform (KMP)**:

* `composeApp/src/commonMain/`: Código de la interfaz de usuario en Compose, lógica de estados de conciertos, mapeo de rangos y el analizador JSON de persistencia.
* `composeApp/src/desktopMain/`: Implementación nativa para Desktop usando la API MIDI de Java para emitir sonido real en tu PC.
* `composeApp/src/androidMain/`: 
  * `cpp/`: Código fuente C++ para el motor de audio Oboe + FluidSynth y configuración de compilación CMake.
  * `kotlin/`: Inicializadores del ciclo de vida de Android, persistencia en archivos locales y el receptor de señales USB MIDI.

---

## 💻 Requisitos Previos e Instalación

1. **Java JDK 21 o 25**:
   * Descarga e instala el JDK desde [Adoptium](https://adoptium.net/). Asegúrate de agregar el JDK al `PATH` del sistema.
2. **Android Studio** (Necesario únicamente si deseas compilar para teléfonos/tabletas):
   * Descarga la última versión de [Android Studio](https://developer.android.com/studio). Al abrir el proyecto, el IDE descargará automáticamente el **Android NDK** (compilador C++) y las dependencias de Gradle.

---

## ⚙️ Cómo Ejecutar

Abre una terminal en la raíz del proyecto y ejecuta el siguiente comando:

### 1. Ejecutar en tu PC (Windows/macOS/Linux)
Para probar la interfaz, cargar conciertos, mover faders y tocar usando el sintetizador de tu PC:
```bash
gradlew :composeApp:run
```
*(Nota: Si no tienes el SDK de Android instalado en tu PC, el script de Gradle lo detectará automáticamente y desactivará las tareas de compilación de Android para evitar fallas, permitiéndote correr la versión de escritorio directamente).*

### 2. Instalar en tu Dispositivo Android
Conecta tu dispositivo móvil por USB con la opción de depuración activada y compila el APK ejecutando:
```bash
gradlew :composeApp:installDebug
```
