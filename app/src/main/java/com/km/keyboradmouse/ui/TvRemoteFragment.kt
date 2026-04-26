package com.km.keyboradmouse.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.km.keyboradmouse.MainActivity
import com.km.keyboradmouse.databinding.FragmentTvRemoteBinding

class TvRemoteFragment : Fragment() {

    private var _binding: FragmentTvRemoteBinding? = null
    private val binding get() = _binding!!
    
    private val hidManager get() = (activity as? MainActivity)?.hidDeviceManager

    // Brand specific HID Usage IDs (Common differences)
    private enum class TvBrand { GENERIC, SAMSUNG, XIAOMI, SONY, TCL, HISENSE }
    private var currentBrand = TvBrand.GENERIC

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTvRemoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBrandSelector()

        // Navigation (Standard HID)
        setupRemoteButton(binding.btnUp, 0x42)
        setupRemoteButton(binding.btnDown, 0x43)
        setupRemoteButton(binding.btnLeft, 0x44)
        setupRemoteButton(binding.btnRight, 0x45)
        setupRemoteButton(binding.btnOk, 0x41)

        // System
        setupRemoteButton(binding.btnBack, 0x224)
        setupRemoteButton(binding.btnHome, 0x223)
        setupRemoteButton(binding.btnMenu, 0x40)
        
        // Power (Standard: 0x30, some brands use special keys in IR but BT/HID is usually 0x30)
        setupRemoteButton(binding.btnPower, 0x30)
        
        // Source
        setupRemoteButton(binding.btnSource) {
            when (currentBrand) {
                TvBrand.SONY -> 0x1BB
                TvBrand.SAMSUNG -> 0x1BB
                else -> 0x1BB // Standard Consumer Control "AL Context-aware Menu" or similar
            }
        }

        // Volume
        setupRemoteButton(binding.btnVolUp, 0xE9)
        setupRemoteButton(binding.btnVolDown, 0xEA)
        
        // Channel (Standard: 0x9C/0x9D)
        setupRemoteButton(binding.btnChUp, 0x9C)
        setupRemoteButton(binding.btnChDown, 0x9D)
        
        // Voice Control / Search
        setupRemoteButton(binding.btnVoice, 0x221)
    }

    private fun setupBrandSelector() {
        binding.chipGroupBrands.setOnCheckedStateChangeListener { _, checkedIds ->
            currentBrand = when (checkedIds.firstOrNull()) {
                binding.chipSamsung.id -> TvBrand.SAMSUNG
                binding.chipXiaomi.id -> TvBrand.XIAOMI
                binding.chipSony.id -> TvBrand.SONY
                binding.chipTcl.id -> TvBrand.TCL
                binding.chipHisense.id -> TvBrand.HISENSE
                else -> TvBrand.GENERIC
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRemoteButton(view: View, usageId: Int) {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hidManager?.sendConsumerControl(usageId)
            }
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRemoteButton(view: View, usageIdProvider: () -> Int) {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hidManager?.sendConsumerControl(usageIdProvider())
            }
            false
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.hidDeviceManager?.tryAutoReconnect()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
