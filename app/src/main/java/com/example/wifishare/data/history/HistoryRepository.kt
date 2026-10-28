package com.example.wifishare.data.history

import android.content.Context
import com.example.wifishare.core.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class HistoryRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("wifishare_history", Context.MODE_PRIVATE)

    private val _records = MutableStateFlow<List<TransferRecord>>(emptyList())
    val records: StateFlow<List<TransferRecord>> = _records.asStateFlow()

    init { load() }

    fun add(record: TransferRecord) {
        val updated = (listOf(record) + _records.value).take(MAX_RECORDS)
        _records.value = updated
        persist(updated)
    }

    fun clear() {
        _records.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = ArrayList<TransferRecord>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    TransferRecord(
                        id = o.getString("id"),
                        fileName = o.getString("fileName"),
                        sizeBytes = o.getLong("sizeBytes"),
                        direction = o.getString("direction"),
                        peerName = o.optString("peerName", ""),
                        timestampMs = o.getLong("timestampMs"),
                        success = o.getBoolean("success"),
                        savedPath = o.optString("savedPath").takeIf { it.isNotBlank() }
                    )
                )
            }
            _records.value = list
        } catch (e: Exception) {
            Logger.w("History parse failed: ${e.message}")
        }
    }

    private fun persist(list: List<TransferRecord>) {
        try {
            val arr = JSONArray()
            list.forEach { r ->
                arr.put(JSONObject().apply {
                    put("id", r.id)
                    put("fileName", r.fileName)
                    put("sizeBytes", r.sizeBytes)
                    put("direction", r.direction)
                    put("peerName", r.peerName)
                    put("timestampMs", r.timestampMs)
                    put("success", r.success)
                    put("savedPath", r.savedPath ?: "")
                })
            }
            prefs.edit().putString(KEY, arr.toString()).apply()
        } catch (e: Exception) {
            Logger.w("History persist failed: ${e.message}")
        }
    }

    private companion object {
        const val KEY = "records"
        const val MAX_RECORDS = 200
    }
}
