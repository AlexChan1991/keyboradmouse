package com.km.keyboradmouse.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.km.keyboradmouse.MainActivity
import com.km.keyboradmouse.databinding.FragmentKeyboardBinding

class KeyboardFragment : Fragment() {

    private var _binding: FragmentKeyboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeyboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupKeyboardListeners(binding.root)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeyboardListeners(view: View) {
        val hidManager = (activity as? MainActivity)?.hidDeviceManager

        if (view is Button) {
            view.setOnTouchListener { _, event ->
                val keyText = view.text.toString().uppercase()
                
                var modifier = 0
                if (keyText == "WIN") modifier = 0x08
                if (keyText == "CTRL") modifier = 0x01
                if (keyText == "ALT") modifier = 0x04
                if (keyText == "SHIFT") modifier = 0x02

                val keyCode = getHidKeyCode(keyText)
                
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        hidManager?.updateKeyboardState(modifier, keyCode, true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        hidManager?.updateKeyboardState(modifier, keyCode, false)
                    }
                }
                true // 必须返回 true 以确保能接收到 ACTION_UP
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setupKeyboardListeners(view.getChildAt(i))
            }
        }
    }

    private fun getHidKeyCode(keyText: String): Int {
        return when (keyText.uppercase()) {
            "A" -> 0x04; "B" -> 0x05; "C" -> 0x06; "D" -> 0x07; "E" -> 0x08
            "F" -> 0x09; "G" -> 0x0A; "H" -> 0x0B; "I" -> 0x0C; "J" -> 0x0D
            "K" -> 0x0E; "L" -> 0x0F; "M" -> 0x10; "N" -> 0x11; "O" -> 0x12
            "P" -> 0x13; "Q" -> 0x14; "R" -> 0x15; "S" -> 0x16; "T" -> 0x17
            "U" -> 0x18; "V" -> 0x19; "W" -> 0x1A; "X" -> 0x1B; "Y" -> 0x1C; "Z" -> 0x1D
            "1" -> 0x1E; "2" -> 0x1F; "3" -> 0x20; "4" -> 0x21; "5" -> 0x22
            "6" -> 0x23; "7" -> 0x24; "8" -> 0x25; "9" -> 0x26; "0" -> 0x27
            "ENTER" -> 0x28; "ESC" -> 0x29; "BS" -> 0x2A; "TAB" -> 0x2B; "SPACE" -> 0x2C
            "F1" -> 0x3A; "F2" -> 0x3B; "F3" -> 0x3C; "F4" -> 0x3D; "F5" -> 0x3E
            "F6" -> 0x3F; "F7" -> 0x40; "F8" -> 0x41; "F9" -> 0x42; "F10" -> 0x43
            "F11" -> 0x44; "F12" -> 0x45; "DEL" -> 0x4C
            "CAPS" -> 0x39
            "-" -> 0x2D; "=" -> 0x2E; "[" -> 0x2F; "]" -> 0x30; "\\" -> 0x31
            ";" -> 0x33; "'" -> 0x34; "`" -> 0x35; "," -> 0x36; "." -> 0x37; "/" -> 0x38
            else -> 0x00
        }
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.hidDeviceManager?.resetHidState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
