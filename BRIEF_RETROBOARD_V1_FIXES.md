# BRIEF — RetroBoard V1 Fixes
> À exécuter avec Claude Code dans `C:\Users\romua\AndroidStudioProjects\retroboard3`

---

## Contexte

RetroBoard est un clavier Android custom (IME) développé en Kotlin.
- Package : `com.cabinskollar.retroboard`
- Fichiers clés :
  - `app/src/main/java/com/cabinskollar/retroboard/RetroboardIME.kt` — service principal
  - `app/src/main/java/com/cabinskollar/retroboard/RetroKeyboardView.kt` — vue custom Canvas
  - `app/src/main/java/com/cabinskollar/retroboard/SkinManager.kt` — 3 skins IBM/Atari/Apple
  - `app/src/main/res/xml/keyboard_azerty.xml` — layout AZERTY
  - `app/src/main/res/xml/keyboard_symbols.xml` — layout symboles
  - `app/src/main/res/layout/keyboard_view.xml` — layout XML du clavier

---

## Bug 1 — Shift / CapsLock ne fonctionne pas sur device réel

**Symptôme :** Les majuscules ne s'affichent pas quand Shift est activé. Fonctionne sur émulateur mais pas sur Redmi Note 13 Pro (MIUI).

**Cause probable :** `RetroKeyboardView.onDraw()` affiche `key.label` brut sans tenir compte de `keyboard.isShifted`. De plus `invalidateAllKeys()` seul ne force pas le redraw sur certains devices.

**Fix à appliquer dans `RetroKeyboardView.kt`** — dans `onDraw()`, remplacer :
```kotlin
val label = key.label?.toString() ?: continue
if (label.isEmpty()) continue
```
par :
```kotlin
val label = key.label?.toString() ?: continue
if (label.isEmpty()) continue
val displayLabel = if (keyboard?.isShifted == true && label.length == 1 && label[0].isLetter()) {
    label.uppercase()
} else {
    label
}
```
Et remplacer `canvas.drawText(label, ...)` par `canvas.drawText(displayLabel, ...)`.

**Fix à appliquer dans `RetroboardIME.kt`** — case `-1` dans `onKey()` :
```kotlin
-1 -> {
    keyboardAzerty.isShifted = !keyboardAzerty.isShifted
    keyboardView.keyboard = keyboardAzerty
    keyboardView.invalidateAllKeys()
}
```

---

## Bug 2 — Vocal ne fonctionne pas sur MIUI

**Symptôme :** Le bouton 🎤 ne déclenche rien sur Redmi Note 13 Pro (MIUI 14).

**Cause :** MIUI bloque `startActivity()` depuis un `InputMethodService` en arrière-plan.

**Fix — remplacer `launchVoiceInput()` dans `RetroboardIME.kt`** par une approche notification/overlay :
```kotlin
private fun launchVoiceInput() {
    try {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez...")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                     Intent.FLAG_ACTIVITY_CLEAR_TOP or
                     Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        currentInputConnection?.commitText("[vocal non disponible]", 1)
    } catch (e: Exception) {
        // MIUI fallback — tenter via performEditorAction
        currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_UNSPECIFIED)
    }
}
```

Si le problème persiste sur MIUI, désactiver le bouton mic et afficher un toast :
```kotlin
private fun launchVoiceInput() {
    android.widget.Toast.makeText(
        applicationContext,
        "Vocal non supporté sur MIUI — utilisez le micro système",
        android.widget.Toast.LENGTH_SHORT
    ).show()
}
```

---

## Feature 3 — Accents en appui long

**Objectif :** Appui long sur une lettre → popup avec les variantes accentuées.

**À ajouter dans `keyboard_azerty.xml`** sur les touches concernées :
```xml
<Key android:codes="101" android:keyLabel="E" 
     android:popupCharacters="éèêë" />
<Key android:codes="97"  android:keyLabel="A" 
     android:popupCharacters="àâä" />
<Key android:codes="117" android:keyLabel="U" 
     android:popupCharacters="ùûü" />
<Key android:codes="105" android:keyLabel="I" 
     android:popupCharacters="îï" />
<Key android:codes="111" android:keyLabel="O" 
     android:popupCharacters="ôö" />
<Key android:codes="99"  android:keyLabel="C" 
     android:popupCharacters="ç" />
```

**Note :** `popupCharacters` est géré nativement par `KeyboardView` mais notre `RetroKeyboardView` override `onDraw`. Il faut vérifier que le popup natif s'affiche encore correctement. Si non, implémenter un popup custom dans `RetroKeyboardView` via `onLongPress`.

---

## Feature 4 — Touche CTRL (V2)

**Objectif :** Touche CTRL permettant CTRL+A, CTRL+C, CTRL+V, CTRL+Z, CTRL+X.

**Approche :**
1. Ajouter `android:codes="-104"` dans `keyboard_azerty.xml` rangée 5 à la place de `SYM` ou en plus
2. Dans `RetroboardIME.kt` gérer un état `ctrlPressed: Boolean`
3. Dans `onKey()`, case `-104` → toggle `ctrlPressed`
4. Pour les lettres, si `ctrlPressed` :
```kotlin
'a' -> { ic.performContextMenuAction(android.R.id.selectAll); ctrlPressed = false }
'c' -> { ic.performContextMenuAction(android.R.id.copy); ctrlPressed = false }
'v' -> { ic.performContextMenuAction(android.R.id.paste); ctrlPressed = false }
'z' -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON); ctrlPressed = false }
'x' -> { ic.performContextMenuAction(android.R.id.cut); ctrlPressed = false }
```

---

## Build & Install

```bash
# Build debug
.\gradlew assembleDebug

# Build release (nécessite keystore)
.\gradlew assembleRelease

# Install sur device connecté
.\gradlew installDebug

# Ou via ADB
adb install app\build\outputs\apk\debug\app-debug.apk
```

---

## Notes techniques

- Min SDK : 26 (Android 8)
- `KeyboardView` est deprecated depuis API 29 mais fonctionne jusqu'à API 34+
- Device de test : Redmi Note 13 Pro, MIUI 14, Android 13
- 3 skins : IBM (sombre), Atari (terracotta), Apple (beige clair)
- Toggle flèches : appui long sur ESPACE
- Switch skin : bouton 🎨 dans la rangée flèches
