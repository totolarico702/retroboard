package com.cabinskollar.retroboard

import android.content.ActivityNotFoundException
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.cabinskollar.retroboard.RetroKeyboardView

class RetroboardIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardAzerty: Keyboard
    private lateinit var keyboardSymbols: Keyboard
    private lateinit var keyboardView: RetroKeyboardView
    private lateinit var arrowRow: LinearLayout

    private var clipboard: String = ""
    private var arrowsVisible = false
    private var isSymbols = false
    private var isShifted = false
    private var ctrlPressed = false
    private var spacePressTime = 0L
    private val LONG_PRESS_MS = 500L

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)

        keyboardAzerty  = Keyboard(this, R.xml.keyboard_azerty)
        keyboardSymbols = Keyboard(this, R.xml.keyboard_symbols)

        keyboardView = view.findViewById(R.id.keyboard_view)
        keyboardView.keyboard = keyboardAzerty
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false
        view.setPadding(0, 0, 0, getNavigationBarHeight())
        arrowRow = view.findViewById(R.id.arrow_row)

        view.findViewById<Button>(R.id.btn_left).setOnClickListener {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
        }
        view.findViewById<Button>(R.id.btn_right).setOnClickListener {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
        }
        view.findViewById<Button>(R.id.btn_up).setOnClickListener {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
        }
        view.findViewById<Button>(R.id.btn_down).setOnClickListener {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        view.findViewById<Button>(R.id.btn_ctrl).setOnClickListener {
            ctrlPressed = !ctrlPressed
        }
        view.findViewById<Button>(R.id.btn_skin).setOnClickListener {
            SkinManager.next()
            applySkin(view)
        }

        keyboardView.setOnTouchListener { _, event ->
            handleSpaceLongPress(event)
        }

        applySkin(view)
        return view
    }

    private fun applySkin(view: View) {
        val t = SkinManager.tokens()
        val radius = 20f
        keyboardView.skinTokens = t
        keyboardView.invalidateAllKeys()

        arrowRow.setBackgroundColor(t.arrowRowBg)

        listOf(R.id.btn_left, R.id.btn_right, R.id.btn_up, R.id.btn_down).forEach {
            view.findViewById<Button>(it).apply {
                background = roundedDrawable(t.arrowBg, t.arrowBg, radius)
                setTextColor(t.arrowText)
            }
        }
        listOf(R.id.btn_ctrl, R.id.btn_skin).forEach {
            view.findViewById<Button>(it).apply {
                background = roundedDrawable(t.actionBg, t.actionBg, radius)
                setTextColor(t.actionText)
            }
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        when (primaryCode) {
            -5 -> {
                val selected = ic.getSelectedText(0)
                if (!selected.isNullOrEmpty()) {
                    ic.commitText("", 1)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            -200 -> {
                isShifted = !isShifted
                android.widget.Toast.makeText(
                    applicationContext,
                    "DBG Shift: isShifted=$isShifted",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                keyboardView.shiftActive = isShifted
                keyboardView.invalidateAllKeys()
            }
            10   -> ic.commitText("\n", 1)
            32   -> ic.commitText(" ", 1)
            -100 -> {
                val text = ic.getSelectedText(0)
                if (!text.isNullOrEmpty()) clipboard = text.toString()
            }
            -102 -> {
                if (clipboard.isNotEmpty()) ic.commitText(clipboard, 1)
            }
            -101 -> {
                isSymbols = true
                keyboardView.keyboard = keyboardSymbols
                keyboardView.invalidateAllKeys()
            }
            -103 -> {
                isSymbols = false
                keyboardView.keyboard = keyboardAzerty
                keyboardView.invalidateAllKeys()
            }
            -104 -> {
                ctrlPressed = !ctrlPressed
            }
            else -> {
                if (ctrlPressed) {
                    when (primaryCode.toChar().lowercaseChar()) {
                        'a' -> { ic.performContextMenuAction(android.R.id.selectAll); ctrlPressed = false }
                        'c' -> { ic.performContextMenuAction(android.R.id.copy); ctrlPressed = false }
                        'v' -> { ic.performContextMenuAction(android.R.id.paste); ctrlPressed = false }
                        'z' -> {
                            ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
                            ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
                            ctrlPressed = false
                        }
                        'x' -> { ic.performContextMenuAction(android.R.id.cut); ctrlPressed = false }
                        else -> ctrlPressed = false
                    }
                } else {
                    val char = primaryCode.toChar()
                    val output = if (isShifted) char.uppercaseChar() else char
                    ic.commitText(output.toString(), 1)
                    if (isShifted) {
                        isShifted = false
                        keyboardView.shiftActive = false
                        keyboardView.invalidateAllKeys()
                    }
                }
            }
        }
    }

    private fun handleSpaceLongPress(event: MotionEvent): Boolean {
        val activeKeyboard = if (isSymbols) keyboardSymbols else keyboardAzerty
        val key = activeKeyboard.keys.firstOrNull { k ->
            event.x >= k.x && event.x <= k.x + k.width &&
                    event.y >= k.y && event.y <= k.y + k.height &&
                    k.codes.firstOrNull() == 32
        }
        if (key == null) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                spacePressTime = SystemClock.elapsedRealtime()
                android.widget.Toast.makeText(
                    applicationContext,
                    "DBG Space DOWN",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            MotionEvent.ACTION_UP -> {
                val held = SystemClock.elapsedRealtime() - spacePressTime
                android.widget.Toast.makeText(
                    applicationContext,
                    "DBG Space UP held=${held}ms (seuil=${LONG_PRESS_MS}ms)",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                if (held >= LONG_PRESS_MS) {
                    toggleArrowRow()
                    return true
                }
            }
        }
        return false
    }

    private fun toggleArrowRow() {
        arrowsVisible = !arrowsVisible
        arrowRow.visibility = if (arrowsVisible) View.VISIBLE else View.GONE
    }

    private fun roundedDrawable(colorTop: Int, colorBot: Int, radius: Float): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            colors = intArrayOf(colorTop, colorBot)
            orientation = android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM
            cornerRadius = radius
        }
    }

    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
    override fun onText(text: CharSequence) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
}