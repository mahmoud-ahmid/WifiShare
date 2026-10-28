package com.example.wifishare.ui.files

import android.net.Uri

/**
 * مدير اختيار بسيط لكل MediaListFragment.
 * يُفعّل بالضغط المطوّل، ويسمح بتحديد عدة عناصر ثم تنفيذ إجراء جماعي.
 */
class ItemSelection {
    private val _selected = linkedSetOf<Uri>()
    val selected: Set<Uri> get() = _selected
    val isEmpty: Boolean get() = _selected.isEmpty()
    val size: Int get() = _selected.size

    fun toggle(uri: Uri) {
        if (_selected.contains(uri)) _selected.remove(uri) else _selected.add(uri)
    }

    fun add(uri: Uri) { _selected.add(uri) }
    fun addAll(uris: Collection<Uri>) { _selected.addAll(uris) }
    fun isSelected(uri: Uri): Boolean = _selected.contains(uri)
    fun clear() = _selected.clear()
}
