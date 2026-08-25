# MIDILIVE PRO - Mainstage clone for Android & Desktop

Este es un prototipo inicial de la aplicación **MIDILIVE PRO** (estilo Mainstage) desarrollado usando **Compose Multiplatform** (Kotlin).

## Requisitos Previos

1. **Java JDK 17 o 21**:
   * Descárgalo desde [Adoptium](https://adoptium.net/) (selecciona el instalador `.msi` para Windows).
   * Asegúrate de marcar la casilla "Add to PATH" durante la instalación.

2. **Android Studio** (Para compilar en celular):
   * Descárgalo desde [developer.android.com/studio](https://developer.android.com/studio).

---

## Cómo Ejecutar las Pruebas

Una vez instalado Java JDK, abre tu consola en esta carpeta (`C:\Users\thefi\.gemini\antigravity\scratch\mainstage_android`) y ejecuta:

### 1. Ejecutar Prototipo de Escritorio (En tu PC)
Para ver la interfaz, mover perillas, cambiar de parches y tocar el teclado virtual en tu PC:
```bash
gradlew :composeApp:run
```

### 2. Ejecutar en tu Celular Android
Conecta tu teléfono Android por USB, activa la "Depuración USB" en tu celular y ejecuta:
```bash
gradlew :composeApp:installDebug
```

---

## Estructura del Proyecto

* `composeApp/src/commonMain/kotlin/App.kt`: Contiene el código de la interfaz gráfica estilo Mainstage (Knobs, Sliders, Setlist, Teclado Virtual) que se comparte entre Windows y Android.
* `composeApp/src/desktopMain/`: Código específico para arrancar la app como ventana nativa en Windows.
* `composeApp/src/androidMain/`: Código específico para el ciclo de vida de Android (donde conectaremos la API de MIDI y FluidSynth).
