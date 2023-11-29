package com.arn.scrobble.edits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arn.scrobble.db.PanoDb
import com.arn.scrobble.db.SimpleEdit
import com.arn.scrobble.db.SimpleEditsDao.Companion.insertReplaceLowerCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SimpleEditsVM : ViewModel() {
    private val dao = PanoDb.db.getSimpleEditsDao()
    private val _simpleEditsFiltered = MutableStateFlow<List<SimpleEdit>?>(null)
    val simpleEditsFiltered = _simpleEditsFiltered.asStateFlow()
    private val _searchTerm = MutableStateFlow("")
    val count = dao.count().shareIn(viewModelScope, SharingStarted.Lazily, 1)

    init {
        _searchTerm
            .debounce(500)
            .flatMapLatest { term ->
                withContext(Dispatchers.IO) {
                    if (term.isBlank())
                        dao.allFlow()
                    else
                        dao.searchPartial(term)
                }
            }
            .onEach { _simpleEditsFiltered.emit(it) }
            .launchIn(viewModelScope)
    }

    fun setFilter(searchTerm: String) {
        viewModelScope.launch {
            _searchTerm.emit(searchTerm.trim())
        }
    }

    fun upsert(simpleEdit: SimpleEdit) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertReplaceLowerCase(simpleEdit)
        }
    }

    fun delete(simpleEdit: SimpleEdit) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(simpleEdit)
        }
    }

}