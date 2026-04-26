package com.km.keyboradmouse.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.km.keyboradmouse.MainActivity
import com.km.keyboradmouse.R
import com.km.keyboradmouse.databinding.FragmentMouseBinding
import kotlin.math.roundToInt

class MouseFragment : Fragment() {

    private var _binding: FragmentMouseBinding? = null
    private val binding get() = _binding!!

    private var lastX = 0f
    private var lastY = 0f
    private var lastScrollY = 0f
    private var scrollAccumulator = 0f
    private var buttons = 0

    private lateinit var gestureDetector: GestureDetector
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    
    // Always get the latest manager instance from Activity
    private val hidManager get() = (activity as? MainActivity)?.hidDeviceManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMouseBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                hidManager?.sendMouseReport(0x01, 0, 0, 0)
                handler.postDelayed({ hidManager?.sendMouseReport(0x00, 0, 0, 0) }, 50)
                return true
            }
        })

        binding.mouseTouchpad.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        hidManager?.sendMouseReport(0x02, 0, 0, 0)
                        handler.postDelayed({ hidManager?.sendMouseReport(0x00, 0, 0, 0) }, 50)
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.x - lastX).roundToInt()
                    val dy = (event.y - lastY).roundToInt()
                    
                    if (dx != 0 || dy != 0) {
                        hidManager?.sendMouseReport(buttons, dx, dy, 0)
                        lastX = event.x
                        lastY = event.y
                    }
                }
            }
            true
        }

        binding.mouseScroll.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastScrollY = event.y
                    scrollAccumulator = 0f
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - lastScrollY
                    // Increase threshold to reduce sensitivity (damping)
                    // 1 unit of scroll per ~20dp movement
                    scrollAccumulator += dy
                    val threshold = 20f 
                    
                    if (kotlin.math.abs(scrollAccumulator) >= threshold) {
                        val scrollAmount = if (scrollAccumulator > 0) -1 else 1
                        hidManager?.sendMouseReport(buttons, 0, 0, scrollAmount)
                        scrollAccumulator %= threshold
                    }
                    lastScrollY = event.y
                }
            }
            true
        }

        binding.btnLeft.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.btnLeftCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_pressed))
                    buttons = buttons or 0x01
                    hidManager?.sendMouseReport(buttons, 0, 0, 0)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.btnLeftCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_idle))
                    buttons = buttons and 0x01.inv()
                    hidManager?.sendMouseReport(buttons, 0, 0, 0)
                }
            }
            true
        }

        binding.btnMiddle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.btnMiddleCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_pressed))
                    buttons = buttons or 0x04
                    hidManager?.sendMouseReport(buttons, 0, 0, 0)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.btnMiddleCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_idle))
                    buttons = buttons and 0x04.inv()
                    hidManager?.sendMouseReport(buttons, 0, 0, 0)
                }
            }
            true
        }

        binding.btnRight.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.btnRightCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_pressed))
                    buttons = buttons or 0x02
                    hidManager?.sendMouseReport(buttons, 0, 0, 0)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.btnRightCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.button_idle))
                    buttons = buttons and 0x02.inv()
                    hidManager?.sendMouseReport(buttons, 0, 0, 0)
                }
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        hidManager?.tryAutoReconnect()
    }

    override fun onPause() {
        super.onPause()
        hidManager?.resetHidState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
