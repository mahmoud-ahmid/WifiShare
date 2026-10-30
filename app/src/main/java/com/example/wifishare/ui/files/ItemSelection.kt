package com.example.wifishare.ui.files

import android.net.Uri
import com.example.wifishare.data.files.MediaItem

class ItemSelection {
    private val _selected = linkedSetOf<Uri>()
    val selected: Set<Uri> get() = _selected
    val size: Int get() = _selected.size

    fun isEmpty(): Boolean = _selected.isEmpty()
    fun toggle(uri: Uri) {
        if (_selected.contains(uri)) _selected.remove(uri) else _selected.add(uri)
    }
    fun clear() { _selected.clear() }
    fun isSelected(uri: Uri): Boolean = _selected.contains(uri)

    fun selectAll(items: List<MediaItem>) {
        items.forEach { _selected.add(it.uri) }
    }
    fun allSelected(items: List<MediaItem>): Boolean {
        return items.isNotEmpty() && items.all { _selected.contains(it.uri) }
    }
}
