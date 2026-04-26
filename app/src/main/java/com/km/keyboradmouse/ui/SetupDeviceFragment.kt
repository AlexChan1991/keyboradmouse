package com.km.keyboradmouse.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.km.keyboradmouse.MainActivity
import com.km.keyboradmouse.R
import com.km.keyboradmouse.databinding.FragmentSetupDeviceBinding

class SetupDeviceFragment : Fragment() {

    private var _binding: FragmentSetupDeviceBinding? = null
    private val binding get() = _binding!!

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val deviceList = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: DeviceAdapter
    
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    // Classic Bluetooth Receiver
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { addDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (isScanning) {
                        // Restart discovery for continuous scanning
                        bluetoothAdapter?.startDiscovery()
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        startScan()
                    } else if (state == BluetoothAdapter.STATE_OFF) {
                        stopScan()
                    }
                }
            }
        }
    }

    // BLE Scanner
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addDevice(result.device)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                addDevice(result.device)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            Log.e("SetupDevice", "BLE Scan failed: $errorCode")
        }
    }

    @SuppressLint("MissingPermission")
    private fun addDevice(device: BluetoothDevice) {
        handler.post {
            // Filter out devices with no address or already in list
            if (device.address == null) return@post
            
            val index = deviceList.indexOfFirst { it.address == device.address }
            if (index == -1) {
                deviceList.add(device)
                // Sort: Devices with names first
                deviceList.sortByDescending { it.name != null }
                deviceAdapter.notifyDataSetChanged()
            } else {
                // Update device if it now has a name
                if (device.name != null && deviceList[index].name == null) {
                    deviceList[index] = device
                    deviceList.sortByDescending { it.name != null }
                    deviceAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupDeviceBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        deviceAdapter = DeviceAdapter(deviceList) { device ->
            stopScan()
            (activity as? MainActivity)?.hidDeviceManager?.connect(device)
            Toast.makeText(context, "正在连接 ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
        }

        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = deviceAdapter

        binding.btnScan.setOnClickListener {
            if (isScanning) {
                stopScan()
            } else {
                val mainActivity = activity as? MainActivity
                if (mainActivity?.hasAllPermissions() == true) {
                    startScan()
                } else {
                    mainActivity?.requestPermissions()
                }
            }
        }

        // Initial scan trigger
        handler.postDelayed({
            if (isAdded) startScan()
        }, 500)
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (isScanning) return
        
        val mainActivity = activity as? MainActivity
        if (mainActivity?.hasAllPermissions() != true) {
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(context, "请先开启蓝牙", Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        binding.progressBar.isVisible = true
        binding.btnScan.text = getString(R.string.stop_scan)
        
        deviceList.clear()
        // Add paired devices
        bluetoothAdapter?.bondedDevices?.let { deviceList.addAll(it) }
        deviceAdapter.notifyDataSetChanged()

        // 1. Classic Discovery
        bluetoothAdapter?.startDiscovery()
        
        // 2. BLE Scan with high frequency settings
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bluetoothAdapter?.bluetoothLeScanner?.startScan(null, settings, leScanCallback)
        
        Log.d("SetupDevice", "Hybrid scan started")
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        binding.progressBar.isVisible = false
        binding.btnScan.text = getString(R.string.scan_bluetooth)
        
        bluetoothAdapter?.cancelDiscovery()
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(leScanCallback)
        } catch (e: Exception) {
            Log.e("SetupDevice", "Error stopping BLE scan", e)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        requireActivity().registerReceiver(receiver, filter)
    }

    override fun onStop() {
        super.onStop()
        stopScan()
        try {
            requireActivity().unregisterReceiver(receiver)
        } catch (e: Exception) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class DeviceAdapter(
        private val devices: List<BluetoothDevice>,
        private val onClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tv_device_name)
            val address: TextView = view.findViewById(R.id.tv_device_address)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("MissingPermission")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            holder.name.text = device.name ?: "未知设备"
            holder.address.text = device.address
            holder.itemView.setOnClickListener { onClick(device) }
        }

        override fun getItemCount() = devices.size
    }
}
