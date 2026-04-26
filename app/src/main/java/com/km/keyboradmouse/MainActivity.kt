package com.km.keyboradmouse

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.km.keyboradmouse.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var bluetoothAdapter: BluetoothAdapter? = null
    
    var hidService: HidService? = null
    private var isBound = false

    val hidDeviceManager get() = hidService?.hidDeviceManager

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HidService.LocalBinder
            hidService = binder.getService()
            isBound = true
            updateBluetoothStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            hidService = null
            isBound = false
        }
    }

    private val hidStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                HidService.ACTION_HID_STATUS_CHANGED -> {
                    updateBluetoothStatus()
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startAndBindHidService()
            updateBluetoothStatus()
        } else {
            Toast.makeText(this, "需要权限才能正常工作", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_mouse, R.id.nav_keyboard, R.id.nav_kb_mouse, 
                R.id.nav_xbox, R.id.nav_tv_remote, R.id.nav_settings, R.id.nav_paired_devices
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        
        refreshNavigationMenu()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (hasAllPermissions()) {
            startAndBindHidService()
            updateBluetoothStatus()
        } else {
            requestPermissions()
        }

        binding.btnBluetoothStatus.setOnClickListener {
            showBluetoothMenu(it)
        }

        navController.addOnDestinationChangedListener { _, _, _ ->
            hidDeviceManager?.resetHidState()
        }
    }

    fun refreshNavigationMenu() {
        val navView: NavigationView = binding.navView
        val menu = navView.menu
        val prefs = getSharedPreferences("menu_prefs", Context.MODE_PRIVATE)
        val savedOrder = prefs.getString("menu_order", null) ?: ""

        val idOrder = savedOrder.split(",").mapNotNull { it.toIntOrNull() }.toMutableList()
        
        // 定义主导航项
        val sortableIds = setOf(
            R.id.nav_mouse, R.id.nav_keyboard, R.id.nav_kb_mouse, 
            R.id.nav_xbox, R.id.nav_tv_remote, R.id.nav_paired_devices
        )
        
        // 确保所有必需的 ID 都在顺序列表中
        sortableIds.forEach { id ->
            if (!idOrder.contains(id)) idOrder.add(id)
        }
        
        // 收集现有项数据并移除
        val itemsData = mutableMapOf<Int, Triple<CharSequence?, Drawable?, Boolean>>()
        sortableIds.forEach { id ->
            menu.findItem(id)?.let { item ->
                itemsData[id] = Triple(item.title, item.icon, item.isCheckable)
                menu.removeItem(id)
            }
        }
        
        // 按照保存的顺序重新添加
        idOrder.distinct().forEachIndexed { index, id ->
            if (sortableIds.contains(id)) {
                itemsData[id]?.let { (title, icon, checkable) ->
                    menu.add(R.id.nav_group_main, id, index, title).apply {
                        this.icon = icon
                        this.isCheckable = checkable
                    }
                }
            }
        }
        
        menu.setGroupCheckable(R.id.nav_group_main, true, true)
    }

    private fun startAndBindHidService() {
        val intent = Intent(this, HidService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(HidService.ACTION_HID_STATUS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(hidStatusReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(hidStatusReceiver, filter)
        }
        
        if (hasAllPermissions() && !isBound) {
            startAndBindHidService()
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(hidStatusReceiver)
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    fun setImmersiveMode(enable: Boolean) {
        if (enable) {
            binding.appBar.visibility = View.GONE
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
            }
        } else {
            binding.appBar.visibility = View.VISIBLE
            binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(true)
                window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            }
        }
    }

    fun hasAllPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return permissions.toTypedArray()
    }

    fun requestPermissions() {
        requestPermissionLauncher.launch(getRequiredPermissions())
    }

    @SuppressLint("MissingPermission")
    private fun updateBluetoothStatus() {
        if (!hasAllPermissions()) {
            binding.btnBluetoothStatus.text = "未授权"
            return
        }
        
        val manager = hidDeviceManager
        val hidDevice = manager?.connectedDevice
        if (hidDevice != null) {
            binding.btnBluetoothStatus.text = hidDevice.name ?: "已连接"
            binding.btnBluetoothStatus.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_bluetooth, 0, 0, 0)
        } else {
            binding.btnBluetoothStatus.text = "未连接"
            binding.btnBluetoothStatus.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showBluetoothMenu(view: View) {
        val wrapper = ContextThemeWrapper(this, R.style.CustomPopupMenuOverlap)
        val popup = PopupMenu(wrapper, view)
        val manager = hidDeviceManager
        
        if (manager == null) {
            popup.menu.add(Menu.NONE, -1, 0, "蓝牙服务加载中...").isEnabled = false
        } else {
            val bondedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
            val connected = manager.connectedDevice

            if (bondedDevices.isEmpty()) {
                popup.menu.add(Menu.NONE, -1, 0, "暂无已匹配设备").isEnabled = false
            } else {
                bondedDevices.forEachIndexed { index, device ->
                    val isConnected = connected?.address == device.address
                    val title = if (isConnected) "● ${device.name ?: device.address}" else device.name ?: device.address
                    popup.menu.add(0, index, index, title)
                }
            }
        }

        popup.menu.add(1, 999, 999, "设置新设备")
        
        popup.setOnMenuItemClickListener { item ->
            when (item.groupId) {
                0 -> {
                    val m = hidDeviceManager ?: return@setOnMenuItemClickListener false
                    val bonded = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                    if (item.itemId < bonded.size) {
                        val device = bonded[item.itemId]
                        if (m.connectedDevice?.address == device.address) {
                            m.disconnect()
                        } else {
                            m.connect(device)
                        }
                    }
                    true
                }
                1 -> {
                    navController.navigate(R.id.nav_setup_device)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()
        if (hasAllPermissions()) {
            updateBluetoothStatus()
            if (hidDeviceManager?.connectedDevice == null) {
                hidDeviceManager?.tryAutoReconnect()
            }
        }
    }
}
