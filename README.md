# StageKeysLive 🎹⚡

**StageKeysLive** es una estación de trabajo de rendimiento en vivo (*Live Performance Audio Workstation*) de nivel profesional para tecladistas y productores, inspirada en las prestaciones de Apple MainStage y diseñada desde cero con **Compose Multiplatform (Kotlin)** y un motor de audio nativo en **C++ (Google Oboe + FluidSynth)**.

Optimizado tanto para músicos sesionistas de escenario (Rock, Pop, Jazz, Latin) como para ministerios de alabanza (Worship, Gospel), StageKeysLive convierte cualquier dispositivo Android o PC en un "cerebro" de sintetizadores ultra estable, de baja latencia y con capacidades avanzadas de control físico y visual en directo.

---

## ✨ Características Principales

### 🎛️ 1. Modo Ejecución (Performance Mode — Vista nanoKONTROL)
* **Diseño orientado a directo**: Interfaz limpia y de alto contraste que maximiza el espacio útil eliminando distracciones durante el show.
* **Superficie de Control Virtual (nanoKONTROL style)**: Faders de canal completos con medidores VU reactivos y perillas dedicadas para efectos en tiempo real (**Reverb**, **Chorus**, **Cutoff**).
* **Conmutador de Parches Compacto**: Barra superior con botones rápidos de parche anterior/siguiente, indicador de número de parche y etiquetas de SoundFonts activas por capa.
* **Monitor de Recursos en Vivo**: Indicador en tiempo real de **CPU %** y **RAM %** en el centro de la barra superior con barras de nivel y códigos de color de estado (verde / advertencia / error).
* **Pads Ambientales Integrados**: Tira de pads táctiles de 12 tonalidades ubicada cómodamente debajo de los faders para transiciones continuas.

---

### 🎹 2. Editor Interactivo de Zonas y Splits (Split / Layer Editor)
* **Editor por Arrastre Táctil (`SplitKeyboardDragEditor`)**: Ajusta visualmente el rango de notas de cada canal arrastrando los extremos sobre la barra del teclado.
* **Selector por Toque en Teclado (`SplitRangeKeyboard`)**: Toca directamente sobre las teclas del piano virtual para fijar la nota más baja (*Start Key*) y más alta (*End Key*) de cada instrumento.
* **Capas y Transposición**: Superpón hasta 8 capas de sonido simultáneas con control independiente de volumen, paneo, transposición por semitonos (+/- 36) y color neón identificador.

---

### 🚀 3. Motor de Audio Nativo C++ de Ultra Baja Latencia
* **Google Oboe + FluidSynth JNI**: Renderizado de audio en punto flotante estéreo de baja latencia con soporte directo para archivos SoundFont (`.sf2`).
* **Soporte de Audio en Segundo Plano (`AudioForegroundService`)**: Servicio de primer plano Android de tipo `mediaPlayback` para tocar sin interrupciones mientras la pantalla se apaga o se usan otras aplicaciones.
* **Cambio de Parches sin Corte (Seamless Spillover)**: Gestión de canales fantasma (*shadow channels*) con corrutinas para que las colas de reverb y notas sostenidas no se corten al cambiar de parche.
* **Control DSP y Hardware xRuns**: Medición de latencia real y contador de sobrecargas de buffer en tiempo real.
* **Limitador Master Anti-Clipping**: Limitador transparente en el canal Master para proteger el sistema de sonido del escenario contra distorsiones digitales.

---

### 🔌 4. Conectividad MIDI Pro y MIDI Learn
* **Detección Plug & Play**: Soporte USB OTG y Bluetooth MIDI para teclados controladores, pedaleras y controladores de faders.
* **Eliminación de Jitter**: Procesamiento de eventos MIDI desacoplado del hilo de UI para una respuesta táctil instantánea y consistente.
* **Mapeo Flexible (MIDI Learn)**: Asigna perillas, faders, botones de Mute/Solo, volumen Master y disparo de Pads a los CC de tu hardware físico.
* **Cambio de Parches por Program Change**: Cambia de sonido al instante desde pedales o botones de tu teclado controlador.
* **Indicador LED de Actividad & Botón de Pánico (PANIC)**: Monitoreo visual de señal MIDI entrante y botón de emergencia para corte inmediato de notas colgadas (*All Notes Off*).

---

### 🌊 5. Ambient Pads Continuos
* **Motor de Drones / Pads Atmosféricos**: Sonido continuo de fondo para rellenar espacios entre canciones y acompañar momentos dinámicos.
* **Crossfade Suave**: Transición armónica imperceptible al cambiar entre tonalidades (C, C#, D, D#, E, F, F#, G, G#, A, A#, B).
* **Múltiples Bancos**: Soporte para diferentes texturas sonoras y perfiles de pads.

---

### 📁 6. SoundFont Explorer y Gestión de Archivos
* **Explorador Integrado con SAF (Storage Access Framework)**: Carga librerías SoundFont `.sf2` desde almacenamiento interno, tarjeta SD o carpetas compartidas.
* **Canal de Preescucha (Scratch Preview)**: Prueba sonidos y presets en vivo antes de agregarlos a la mezcla.
* **Copia de Seguridad en Google Drive**: Sincronización y restauración automática de conciertos en la nube con compatibilidad offline.
* **Exportación / Importación Completa**: Comparte conciertos (`.skz`) y parches individuales entre dispositivos.

---

### 🧰 7. Herramientas de Escenario y UX
* **Grabador / Reproductor MIDI**: Registra ideas en directo y expórtalas en formato estándar `.mid`.
* **Metrónomo con Tap Tempo**: Ajuste preciso de BPM (40-240) con pulsos visuales y control de volumen independiente.
* **Protección contra Borrado Accidental**: Barra de deshacer (*Undo Snackbar*) estilo Gmail para conciertos y parches.
* **Onboarding y Diagnóstico**: Guía interactiva de bienvenida y acceso a soporte integrado vía Telegram con diagnósticos del sistema.

---

## 🏗️ Arquitectura del Proyecto

El proyecto está estructurado como un proyecto **Kotlin Multiplatform (KMP)** moderno:

```
StageKeysLive/
├── composeApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/          # UI en Jetpack Compose, estados, modelos y lógica compartida
│       │   ├── App.kt                  # Navegación principal, configuración y diálogos globales
│       │   ├── ConcertView.kt          # Vista de concierto, modo ejecución y monitor de recursos
│       │   ├── AudioEngine.kt          # Interfaz multiplataforma del motor de sonido
│       │   ├── MidiManager.kt          # Interfaces de entrada y mapeo MIDI
│       │   └── SoundFontManager.kt     # Gestión y catálogo de SoundFonts
│       │
│       ├── androidMain/
│       │   ├── cpp/                    # Motor de audio nativo C++ (Oboe + FluidSynth)
│       │   │   ├── OboeAudioPlayer.cpp # Stream de salida de audio de baja latencia
│       │   │   └── SynthEngine.cpp     # Síntesis SF2 multicanal y efectos
│       │   └── kotlin/                 # Servicios Android, Foreground Service, USB MIDI y Google Drive
│       │       ├── AndroidMidiManager.kt
│       │       ├── AudioForegroundService.kt
│       │       └── AndroidPerformanceMonitor.kt
│       │
│       └── desktopMain/kotlin/         # Implementación Desktop para pruebas en PC (Java Sound MIDI)
```

---

## 💻 Requisitos y Entorno de Desarrollo

1. **Java JDK 21 o 25**: Descargar desde [Adoptium](https://adoptium.net/).
2. **Android Studio Ladybug / Meerkat o superior**: Con Android SDK y **Android NDK (Side by side)** instalados para la compilación del código C++.
3. **Dispositivo de prueba**: Teléfono o tablet Android con soporte USB Host (OTG) y Android 8.0+ (API 26+).

---

## ⚙️ Compilación y Ejecución

### 📱 Ejecutar / Instalar en Dispositivo Android
Conecta tu dispositivo por cable USB con la **Depuración USB** activada y ejecuta:

```bash
./gradlew installDebug
```

Para generar el APK de desarrollo:
```bash
./gradlew assembleDebug
```
El archivo se generará en: `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

---

### 🖥️ Ejecutar en Escritorio (Windows / macOS / Linux)
Para probar la interfaz, editar conciertos y reproducir usando el sintetizador de escritorio:

```bash
./gradlew :composeApp:run
```

---

## 📄 Licencia

Este proyecto está bajo la licencia [MIT](LICENSE). Desarrollado por [Tute Lopez](https://github.com/tutelopez).
