package com.kuaforumcepte.app.feature.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.kuaforumcepte.app.core.common.UiText
import com.kuaforumcepte.app.core.data.ErrorMessages
import com.kuaforumcepte.app.data.booking.SalonDirectoryRepository
import com.kuaforumcepte.app.data.booking.SalonListQuery
import com.kuaforumcepte.app.data.booking.SalonSort
import com.kuaforumcepte.app.data.location.City
import com.kuaforumcepte.app.data.location.LocationRepository
import com.kuaforumcepte.app.data.salon.SalonProfile
import com.kuaforumcepte.domain.model.SalonType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SalonListUi(
    val query: SalonListQuery = SalonListQuery(),
    val searchInput: String = "",
    val items: List<SalonProfile> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: UiText? = null,
    val cities: List<City> = emptyList(),
) {
    val districts: List<String> get() = cities.firstOrNull { it.name == query.city }?.districts.orEmpty()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SalonListViewModel @Inject constructor(
    private val directory: SalonDirectoryRepository,
    private val locations: LocationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SalonListUi())
    val ui: StateFlow<SalonListUi> = _ui.asStateFlow()
    private var cursor: DocumentSnapshot? = null
    private var job: Job? = null

    init {
        viewModelScope.launch { _ui.update { it.copy(cities = locations.cities()) } }
        // Arama kutusu: 350 ms debounce, yalnızca değişen anahtar sorgulanır.
        viewModelScope.launch {
            _ui.map { it.searchInput }.distinctUntilChanged().drop(1).debounce(350).collect { text ->
                setQuery(_ui.value.query.copy(search = text))
            }
        }
        reload()
    }

    fun onSearch(text: String) = _ui.update { it.copy(searchInput = text.take(60)) }
    fun onSort(sort: SalonSort) = setQuery(_ui.value.query.copy(sort = sort))
    fun onType(type: SalonType?) = setQuery(_ui.value.query.copy(type = type))
    fun onCity(city: City?) = setQuery(_ui.value.query.copy(city = city?.name, district = null))
    fun onDistrict(d: String?) = setQuery(_ui.value.query.copy(district = d))
    fun clearFilters() = setQuery(SalonListQuery(search = _ui.value.query.search, sort = _ui.value.query.sort))

    private fun setQuery(q: SalonListQuery) {
        if (q == _ui.value.query && !_ui.value.loading) return
        _ui.update { it.copy(query = q) }
        reload()
    }

    fun reload() {
        job?.cancel()
        cursor = null
        _ui.update { it.copy(loading = true, error = null, items = emptyList(), hasMore = false) }
        job = viewModelScope.launch {
            runCatching { directory.page(_ui.value.query, null) }
                .onSuccess { p -> cursor = p.cursor; _ui.update { it.copy(loading = false, items = p.items, hasMore = p.hasMore) } }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = ErrorMessages.of(e)) } }
        }
    }

    fun loadMore() {
        val s = _ui.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        _ui.update { it.copy(loadingMore = true) }
        job = viewModelScope.launch {
            runCatching { directory.page(s.query, cursor) }
                .onSuccess { p -> cursor = p.cursor; _ui.update { it.copy(loadingMore = false, items = (it.items + p.items).distinctBy { x -> x.id }, hasMore = p.hasMore) } }
                .onFailure { e -> _ui.update { it.copy(loadingMore = false, error = ErrorMessages.of(e)) } }
        }
    }
}
