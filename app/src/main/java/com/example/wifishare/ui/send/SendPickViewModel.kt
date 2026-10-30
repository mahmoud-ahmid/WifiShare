package com.example.wifishare.ui.send

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.wifishare.data.send.SendItem
import com.example.wifishare.data.send.SendRepository

class SendPickViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SendRepository(app)
    val selection = SelectionState()

    suspend fun loadCategory(cat: SendItem.Category): List<SendItem> = repo.load(cat)
    suspend fun listDir(path: String): List<SendItem> = repo.listDir(path)

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SendPickViewModel::class.java))
            return SendPickViewModel(app) as T
        }
    }
}
