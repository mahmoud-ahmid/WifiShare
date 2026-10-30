package com.example.wifishare.ui.send

import android.net.Uri
import com.example.wifishare.data.send.SendItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SelectionState {
    private val map = linkedMapOf<String, SendItem>()
    private val _countFlow = MutableStateFlow(0)
    val countFlow: StateFlow<Int> = _countFlow.asStateFlow()

    private fun key(item: SendItem) = item.sourcePath ?: item.uri.toString()

    fun toggle(item: SendItem): Boolean {
        val k = key(item)
        val added = if (map.containsKey(k)) { map.remove(k); false }
                    else { map[k] = item; true }
        _countFlow.value = map.size
        return added
    }
    fun clear() { map.clear(); _countFlow.value = 0 }
    fun count(): Int = map.size
    fun isEmpty(): Boolean = map.isEmpty()
    fun totalBytes(): Long = map.values.sumOf { it.sizeBytes }
    fun items(): List<SendItem> = map.values.toList()
    fun uris(): List<Uri> = map.values.map { it.uri }
    fun isSelected(item: SendItem) = map.containsKey(key(item))

    fun selectAll(items: List<SendItem>) {
        items.forEach { map[key(it)] = it }; _countFlow.value = map.size
    }
    fun allSelected(items: List<SendItem>) =
        items.isNotEmpty() && items.all { map.containsKey(key(it)) }
}
