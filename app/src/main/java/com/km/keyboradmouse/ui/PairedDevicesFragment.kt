package com.km.keyboradmouse.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.km.keyboradmouse.MainActivity
import com.km.keyboradmouse.R
import com.km.keyboradmouse.databinding.FragmentPairedDevicesBinding

class PairedDevicesFragment : Fragment() {

    private var _binding: FragmentPairedDevicesBinding? = null
    private val binding get() = _binding!!

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val deviceList = mutableListOf<BluetoothDevice>()
    private lateinit var deviceAdapter: PairedDeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPairedDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        deviceAdapter = PairedDeviceAdapter(
            deviceList,
            onItemClick = { device -> handleDeviceClick(device) },
            onItemLongClick = { device -> showUnpairDialog(device) }
        )

        binding.rvPairedDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPairedDevices.adapter = deviceAdapter

        refreshDeviceList()
    }

    @SuppressLint("MissingPermission")
    private fun refreshDeviceList() {
        deviceList.clear()
        bluetoothAdapter?.bondedDevices?.let { 
            deviceList.addAll(it)
        }
        deviceAdapter.notifyDataSetChanged()
        binding.tvEmpty.visibility = if (deviceList.isEmpty()) View.VISIBLE else View.GONE
    }

    @SuppressLint("MissingPermission")
    private fun handleDeviceClick(device: BluetoothDevice) {
        val hidManager = (activity as? MainActivity)?.hidDeviceManager
        if (hidManager?.connectedDevice?.address == device.address) {
            hidManager.disconnect()
            Toast.makeText(context, "正在断开 ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
        } else {
            hidManager?.connect(device)
            Toast.makeText(context, "正在连接 ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showUnpairDialog(device: BluetoothDevice) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.unpair)
            .setMessage(getString(R.string.unpair_confirm, device.name ?: device.address))
            .setPositiveButton(R.string.unpair) { _, _ ->
                unpairDevice(device)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun unpairDevice(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device)
            Toast.makeText(context, "已取消匹配", Toast.LENGTH_SHORT).show()
            // Refresh list after a short delay to allow system to update
            view?.postDelayed({ refreshDeviceList() }, 500)
        } catch (e: Exception) {
            Toast.makeText(context, "取消匹配失败", Toast.LENGTH_SHORT).show()
        }
    }

    class PairedDeviceAdapter(
        private val devices: List<BluetoothDevice>,
        private val onItemClick: (BluetoothDevice) -> Unit,
        private val onItemLongClick: (BluetoothDevice) -> Unit
    ) : RecyclerView.Adapter<PairedDeviceAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tv_device_name)
            val address: TextView = view.findViewById(R.id.tv_device_address)
            val status: TextView = view.findViewById(R.id.tv_device_status)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_paired, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("MissingPermission")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            holder.name.text = device.name ?: "未知设备"
            holder.address.text = device.address
            
            val hidManager = (holder.itemView.context as? MainActivity)?.hidDeviceManager
            val isConnected = hidManager?.connectedDevice?.address == device.address
            
            holder.status.text = if (isConnected) "已连接" else "未连接"
            holder.status.setTextColor(if (isConnected) 
                holder.itemView.context.getColor(R.color.google_blue) 
            else 
                holder.itemView.context.getColor(android.R.color.darker_gray))

            holder.itemView.setOnClickListener { onItemClick(device) }
            holder.itemView.setOnLongClickListener {
                onItemLongClick(device)
                true
            }
        }

        override fun getItemCount() = devices.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
