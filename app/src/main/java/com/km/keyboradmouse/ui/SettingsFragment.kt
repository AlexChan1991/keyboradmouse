package com.km.keyboradmouse.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.km.keyboradmouse.MainActivity
import com.km.keyboradmouse.R
import com.km.keyboradmouse.databinding.FragmentSettingsBinding
import java.util.Collections

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MenuOrderAdapter
    private val menuItems = mutableListOf<MenuItemInfo>()

    data class MenuItemInfo(val id: Int, val title: String, val iconRes: Int)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCurrentOrder()

        adapter = MenuOrderAdapter(menuItems)
        binding.rvMenuOrder.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMenuOrder.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.bindingAdapterPosition
                val toPos = target.bindingAdapterPosition
                
                Collections.swap(menuItems, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)
                saveOrder()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })

        touchHelper.attachToRecyclerView(binding.rvMenuOrder)
    }

    private fun loadCurrentOrder() {
        val prefs = requireContext().getSharedPreferences("menu_prefs", Context.MODE_PRIVATE)
        val savedOrder = prefs.getString("menu_order", null)
        
        val defaultItems = listOf(
            MenuItemInfo(R.id.nav_mouse, getString(R.string.menu_mouse), R.drawable.ic_mouse),
            MenuItemInfo(R.id.nav_keyboard, getString(R.string.menu_keyboard), R.drawable.ic_keyboard),
            MenuItemInfo(R.id.nav_kb_mouse, getString(R.string.menu_kb_mouse), R.drawable.ic_keyboard_mouse),
            MenuItemInfo(R.id.nav_xbox, getString(R.string.menu_xbox), R.drawable.ic_xbox),
            MenuItemInfo(R.id.nav_tv_remote, getString(R.string.menu_remote), R.drawable.ic_tv_remote),
            MenuItemInfo(R.id.nav_paired_devices, getString(R.string.setup_device), R.drawable.ic_bluetooth)
        )

        menuItems.clear()
        
        if (savedOrder == null) {
            menuItems.addAll(defaultItems)
        } else {
            val idStrings = savedOrder.split(",")
            idStrings.forEach { idStr ->
                val id = idStr.toIntOrNull()
                // 排除“菜单排序”，因为它现在是固定的
                if (id != null && id != R.id.nav_settings) {
                    defaultItems.find { it.id == id }?.let { menuItems.add(it) }
                }
            }
            // 补全任何缺失的默认项
            defaultItems.forEach { item ->
                if (menuItems.none { it.id == item.id }) {
                    menuItems.add(item)
                }
            }
        }
    }

    private fun saveOrder() {
        // 保存列表顺序
        val orderString = menuItems.joinToString(",") { it.id.toString() }
        val prefs = requireContext().getSharedPreferences("menu_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("menu_order", orderString).apply()
        
        (activity as? MainActivity)?.refreshNavigationMenu()
    }

    class MenuOrderAdapter(private val items: List<MenuItemInfo>) :
        RecyclerView.Adapter<MenuOrderAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_menu_icon)
            val title: TextView = view.findViewById(R.id.tv_menu_title)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_menu_order, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageResource(item.iconRes)
            holder.title.text = item.title
        }

        override fun getItemCount() = items.size
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
