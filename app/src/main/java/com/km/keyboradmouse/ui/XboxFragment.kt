package com.km.keyboradmouse.ui

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.km.keyboradmouse.MainActivity
import com.km.keyboradmouse.databinding.FragmentXboxBinding
import kotlin.math.roundToInt

class XboxFragment : Fragment() {

    private var _binding: FragmentXboxBinding? = null
    private val binding get() = _binding!!
    
    private val hidManager get() = (activity as? MainActivity)?.hidDeviceManager

    // Gamepad state
    private var buttons1 = 0 // A, B, X, Y, LB, RB, View, Menu
    private var buttons2 = 0 // Xbox, LStick, RStick
    private var leftStickX = 0
    private var leftStickY = 0
    private var rightStickX = 0
    private var rightStickY = 0
    private var hatSwitch = 8 // Centered/Released

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Force Landscape for this fragment
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        (activity as? MainActivity)?.setImmersiveMode(true)
        _binding = FragmentXboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- ABXY Buttons ---
        setupButton(binding.btnA, 0x01)
        setupButton(binding.btnB, 0x02)
        setupButton(binding.btnX, 0x04)
        setupButton(binding.btnY, 0x08)

        // --- Shoulder Buttons ---
        setupButton(binding.btnLb, 0x10)
        setupButton(binding.btnRb, 0x20)
        setupButton(binding.btnLt, 0x40) // Simplified as digital
        setupButton(binding.btnRt, 0x80) // Simplified as digital

        // --- Center Buttons ---
        setupButton(binding.btnView, 0x01, isButtons2 = true)
        setupButton(binding.btnMenu, 0x02, isButtons2 = true)
        setupButton(binding.btnXboxHome, 0x04, isButtons2 = true)

        // --- D-Pad (Hat Switch) ---
        binding.btnUp.setOnTouchListener { _, e -> handleHat(e, 0); true }
        binding.btnRightPad.setOnTouchListener { _, e -> handleHat(e, 2); true }
        binding.btnDown.setOnTouchListener { _, e -> handleHat(e, 4); true }
        binding.btnLeftPad.setOnTouchListener { _, e -> handleHat(e, 6); true }

        // --- Joysticks (Touchpad Emulation) ---
        setupJoystick(binding.viewLeftStick, isLeft = true)
        setupJoystick(binding.viewRightStick, isLeft = false)
    }

    private fun setupButton(view: View, bit: Int, isButtons2: Boolean = false) {
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isButtons2) buttons2 = buttons2 or bit else buttons1 = buttons1 or bit
                    sendUpdate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isButtons2) buttons2 = buttons2 and bit.inv() else buttons1 = buttons1 and bit.inv()
                    sendUpdate()
                }
            }
            false
        }
    }

    private fun handleHat(event: MotionEvent, value: Int) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> hatSwitch = value
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> hatSwitch = 8
        }
        sendUpdate()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupJoystick(view: View, isLeft: Boolean) {
        var startX = 0f
        var startY = 0f
        val range = 60f // Based on UI size

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ((event.rawX - startX) / range * 127).roundToInt().coerceIn(-127, 127)
                    val dy = ((event.rawY - startY) / range * 127).roundToInt().coerceIn(-127, 127)
                    if (isLeft) {
                        leftStickX = dx
                        leftStickY = dy
                    } else {
                        rightStickX = dx
                        rightStickY = dy
                    }
                    sendUpdate()
                    
                    // Visual feedback for stick movement
                    v.translationX = (dx.toFloat() / 127 * 20)
                    v.translationY = (dy.toFloat() / 127 * 20)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isLeft) {
                        leftStickX = 0
                        leftStickY = 0
                    } else {
                        rightStickX = 0
                        rightStickY = 0
                    }
                    sendUpdate()
                    v.translationX = 0f
                    v.translationY = 0f
                }
            }
            true
        }
    }

    private fun sendUpdate() {
        hidManager?.sendGamepadReport(
            leftStickX, leftStickY,
            rightStickX, rightStickY,
            buttons1, buttons2,
            hatSwitch
        )
    }

    override fun onStop() {
        super.onStop()
        (activity as? MainActivity)?.setImmersiveMode(false)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
