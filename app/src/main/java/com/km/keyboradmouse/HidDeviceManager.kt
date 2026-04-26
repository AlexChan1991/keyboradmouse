package com.km.keyboradmouse

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import java.util.concurrent.Executors

class HidDeviceManager(private val context: Context, private val onStatusChanged: (Int) -> Unit) {

    private val TAG = "HidDeviceManager"
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    var connectedDevice: BluetoothDevice? = null
        private set

    private val prefs = context.getSharedPreferences("hid_prefs", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())

    private var currentModifiers = 0
    private val pressedKeys = mutableSetOf<Int>()

    private val sdpSettings = BluetoothHidDeviceAppSdpSettings(
        "KM Remote",
        "HID Device",
        "Android",
        BluetoothHidDevice.SUBCLASS1_COMBO,
        HID_REPORT_DESCRIPTOR
    )

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "HID App registered: $registered")
            if (registered) {
                handler.postDelayed({ tryAutoReconnect() }, 1000)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "HID Connection state: $state for device ${device?.address}")
            connectedDevice = if (state == BluetoothProfile.STATE_CONNECTED) {
                saveDeviceToHistory(device)
                device
            } else {
                null
            }
            onStatusChanged(state)
        }
    }

    init {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter != null) {
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                @SuppressLint("MissingPermission")
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        bluetoothHidDevice = proxy as BluetoothHidDevice
                        val devices = bluetoothHidDevice?.connectedDevices
                        if (!devices.isNullOrEmpty()) {
                            connectedDevice = devices[0]
                            onStatusChanged(BluetoothProfile.STATE_CONNECTED)
                        }
                        registerApp()
                    }
                }
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) bluetoothHidDevice = null
                }
            }, BluetoothProfile.HID_DEVICE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        Log.d(TAG, "Registering HID App...")
        bluetoothHidDevice?.unregisterApp()
        bluetoothHidDevice?.registerApp(sdpSettings, null, null, Executors.newSingleThreadExecutor(), callback)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        if (bluetoothHidDevice == null) return
        if (connectedDevice?.address == device.address) return
        connectedDevice?.let { bluetoothHidDevice?.disconnect(it) }
        bluetoothHidDevice?.connect(device)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        connectedDevice?.let { bluetoothHidDevice?.disconnect(it) }
    }

    @SuppressLint("MissingPermission")
    fun resetHidState() {
        currentModifiers = 0
        pressedKeys.clear()
        val device = connectedDevice ?: return
        
        bluetoothHidDevice?.sendReport(device, ID_KEYBOARD, ByteArray(8))
        bluetoothHidDevice?.sendReport(device, ID_MOUSE, ByteArray(4))
        bluetoothHidDevice?.sendReport(device, ID_GAMEPAD, ByteArray(6))
        bluetoothHidDevice?.sendReport(device, ID_CONSUMER, ByteArray(2))
        
        Log.d(TAG, "HID channels reset")
    }

    @SuppressLint("MissingPermission")
    fun sendMouseReport(buttons: Int, x: Int, y: Int, wheel: Int) {
        val device = connectedDevice ?: return
        val report = byteArrayOf(
            buttons.toByte(),
            x.coerceIn(-127, 127).toByte(),
            y.coerceIn(-127, 127).toByte(),
            wheel.coerceIn(-127, 127).toByte()
        )
        val success = bluetoothHidDevice?.sendReport(device, ID_MOUSE, report)
        if (success == false) {
            Log.e(TAG, "Failed to send mouse report")
        }
    }

    @SuppressLint("MissingPermission")
    fun updateKeyboardState(modifier: Int, key: Int, isDown: Boolean) {
        val device = connectedDevice ?: return
        if (isDown) {
            if (modifier != 0) currentModifiers = currentModifiers or modifier
            if (key != 0) pressedKeys.add(key)
        } else {
            if (modifier != 0) currentModifiers = currentModifiers and modifier.inv()
            if (key != 0) pressedKeys.remove(key)
        }
        val report = ByteArray(8)
        report[0] = currentModifiers.toByte()
        report[1] = 0 // Reserved byte
        var i = 2
        for (k in pressedKeys) {
            if (i < 8) { report[i] = k.toByte(); i++ }
        }
        bluetoothHidDevice?.sendReport(device, ID_KEYBOARD, report)
    }

    @SuppressLint("MissingPermission")
    fun sendGamepadReport(leftX: Int, leftY: Int, rightX: Int, rightY: Int, buttons1: Int, buttons2: Int, hat: Int) {
        val device = connectedDevice ?: return
        val report = ByteArray(6)
        report[0] = leftX.toByte()
        report[1] = leftY.toByte()
        report[2] = rightX.toByte()
        report[3] = rightY.toByte()
        report[4] = ((hat and 0x0F) or ((buttons1 and 0x0F) shl 4)).toByte()
        report[5] = (((buttons1 and 0xF0) shr 4) or ((buttons2 and 0x0F) shl 4)).toByte()
        bluetoothHidDevice?.sendReport(device, ID_GAMEPAD, report)
    }

    @SuppressLint("MissingPermission")
    fun sendConsumerControl(usageId: Int) {
        val device = connectedDevice ?: return
        val report = byteArrayOf((usageId and 0xFF).toByte(), ((usageId shr 8) and 0xFF).toByte())
        bluetoothHidDevice?.sendReport(device, ID_CONSUMER, report)
        bluetoothHidDevice?.sendReport(device, ID_CONSUMER, byteArrayOf(0, 0))
    }

    @SuppressLint("MissingPermission")
    fun sendAudioReport(data: ByteArray) {
        val device = connectedDevice ?: return
        bluetoothHidDevice?.sendReport(device, ID_MIC, data)
    }

    private fun saveDeviceToHistory(device: BluetoothDevice?) {
        device?.let {
            val history = getHistoryAddresses().toMutableSet()
            history.add(it.address)
            prefs.edit { 
                putStringSet("device_history", history)
                putString("last_device_address", it.address)
            }
        }
    }

    fun getHistoryAddresses(): Set<String> {
        return prefs.getStringSet("device_history", emptySet()) ?: emptySet()
    }

    @SuppressLint("MissingPermission")
    fun tryAutoReconnect() {
        if (connectedDevice != null) return
        val lastAddress = prefs.getString("last_device_address", null) ?: return
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled || bluetoothHidDevice == null) return
        try {
            val device = adapter.getRemoteDevice(lastAddress)
            bluetoothHidDevice?.connect(device)
        } catch (e: Exception) { }
    }

    companion object {
        private const val ID_KEYBOARD = 1
        private const val ID_MOUSE = 2
        private const val ID_GAMEPAD = 3
        private const val ID_CONSUMER = 4
        private const val ID_MIC = 5

        private val HID_REPORT_DESCRIPTOR = byteArrayOf(
            // Keyboard (ID 1)
            0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x06.toByte(), 0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), ID_KEYBOARD.toByte(), 0x05.toByte(), 0x07.toByte(), 0x19.toByte(), 0xE0.toByte(),
            0x29.toByte(), 0xE7.toByte(), 0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(), 0x95.toByte(), 0x08.toByte(), 0x81.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(), 0x75.toByte(), 0x08.toByte(), 0x81.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x05.toByte(), 0x75.toByte(), 0x01.toByte(), 0x05.toByte(), 0x08.toByte(),
            0x19.toByte(), 0x01.toByte(), 0x29.toByte(), 0x05.toByte(), 0x91.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(), 0x75.toByte(), 0x03.toByte(), 0x91.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x06.toByte(), 0x75.toByte(), 0x08.toByte(), 0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x65.toByte(), 0x05.toByte(), 0x07.toByte(), 0x19.toByte(), 0x00.toByte(),
            0x29.toByte(), 0x65.toByte(), 0x81.toByte(), 0x00.toByte(), 0xC0.toByte(),

            // Mouse (ID 2)
            0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x02.toByte(), 0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), ID_MOUSE.toByte(),
            0x09.toByte(), 0x01.toByte(), 0xA1.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x09.toByte(), 0x19.toByte(), 0x01.toByte(), 0x29.toByte(), 0x03.toByte(),
            0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(), 0x95.toByte(), 0x03.toByte(),
            0x75.toByte(), 0x01.toByte(), 0x81.toByte(), 0x02.toByte(), 0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x05.toByte(), 0x81.toByte(), 0x03.toByte(), 0x05.toByte(), 0x01.toByte(),
            0x09.toByte(), 0x30.toByte(), 0x09.toByte(), 0x31.toByte(), 0x09.toByte(), 0x38.toByte(),
            0x15.toByte(), 0x81.toByte(), 0x25.toByte(), 0x7F.toByte(), 0x75.toByte(), 0x08.toByte(),
            0x95.toByte(), 0x03.toByte(), 0x81.toByte(), 0x06.toByte(), 0xC0.toByte(), 0xC0.toByte(),

            // Gamepad (ID 3)
            0x05.toByte(), 0x01.toByte(), 0x09.toByte(), 0x05.toByte(), 0xA1.toByte(), 0x01.toByte(),
            0x85.toByte(), ID_GAMEPAD.toByte(), 0x09.toByte(), 0x30.toByte(), 0x09.toByte(), 0x31.toByte(),
            0x09.toByte(), 0x32.toByte(), 0x09.toByte(), 0x35.toByte(), 0x15.toByte(), 0x81.toByte(),
            0x25.toByte(), 0x7F.toByte(), 0x75.toByte(), 0x08.toByte(), 0x95.toByte(), 0x04.toByte(),
            0x81.toByte(), 0x02.toByte(), 0x05.toByte(), 0x09.toByte(), 0x19.toByte(), 0x01.toByte(),
            0x29.toByte(), 0x10.toByte(), 0x15.toByte(), 0x00.toByte(), 0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(), 0x95.toByte(), 0x10.toByte(), 0x81.toByte(), 0x02.toByte(),
            0xC0.toByte(),

            // Consumer Control (ID 4)
            0x05.toByte(), 0x0C.toByte(), 0x09.toByte(), 0x01.toByte(), 0xA1.toByte(), 0x01.toByte(), 
            0x85.toByte(), ID_CONSUMER.toByte(), 0x15.toByte(), 0x00.toByte(), 0x26.toByte(), 0xFF.toByte(),
            0x03.toByte(), 0x19.toByte(), 0x00.toByte(), 0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(), 
            0x75.toByte(), 0x10.toByte(), 0x95.toByte(), 0x01.toByte(), 0x81.toByte(), 0x00.toByte(), 0xC0.toByte(),

            // Vendor Defined (ID 5) - for MIC audio
            0x06.toByte(), 0x00.toByte(), 0xFF.toByte(), 
            0x09.toByte(), 0x01.toByte(), 
            0xA1.toByte(), 0x01.toByte(), 
            0x85.toByte(), ID_MIC.toByte(), 
            0x09.toByte(), 0x02.toByte(), 
            0x15.toByte(), 0x00.toByte(), 
            0x26.toByte(), 0xFF.toByte(), 0x00.toByte(), 
            0x75.toByte(), 0x08.toByte(), 
            0x95.toByte(), 0x40.toByte(), 
            0x81.toByte(), 0x02.toByte(), 
            0xC0.toByte()
        )
    }
}
