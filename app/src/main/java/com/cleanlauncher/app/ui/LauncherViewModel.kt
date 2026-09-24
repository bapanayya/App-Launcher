package com.cleanlauncher.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanlauncher.app.data.model.AppCategory
import com.cleanlauncher.app.data.model.AppItem
import com.cleanlauncher.app.data.model.AppReminder
import com.cleanlauncher.app.data.repository.AppRepository
import com.cleanlauncher.app.reminder.AppReminderManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ViewMode(val label: String) {
    SECTIONS("Sections"),         // Sleek vertical scroll with collapsible categorized sections
    GRID("Grid"),                 // Fast 4-column categorized grid
    MINIMAL_LIST("Minimal List")  // Minimalist text + subtle icon list
}

data class LauncherUiState(
    val isLoading: Boolean = true,
    val allApps: List<AppItem> = emptyList(),
    val filteredApps: List<AppItem> = emptyList(),
    val appsByCategory: Map<AppCategory, List<AppItem>> = emptyMap(),
    val allCategories: List<AppCategory> = AppCategory.DEFAULT_CATEGORIES,
    val categoryCounts: Map<AppCategory, Int> = emptyMap(),
    val searchQuery: String = "",
    val selectedCategory: AppCategory = AppCategory.ALL,
    val viewMode: ViewMode = ViewMode.SECTIONS,
    val favoriteApps: List<AppItem> = emptyList(),
    val dockApps: List<AppItem> = emptyList(),
    val homeScreenApps: List<AppItem> = emptyList(),
    val isAppDrawerOpen: Boolean = false,
    val isDefaultLauncher: Boolean = true,
    val isDefaultBannerDismissed: Boolean = false
) {
    val shouldShowDefaultBanner: Boolean
        get() = !isDefaultLauncher && !isDefaultBannerDismissed
}

class LauncherViewModel(
    private val repository: AppRepository,
    private val reminderManager: AppReminderManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    init {
        val savedMode = try {
            ViewMode.valueOf(repository.getSavedViewMode())
        } catch (_: Exception) {
            ViewMode.SECTIONS
        }
        _uiState.update { it.copy(viewMode = savedMode) }
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = repository.getInstalledApps()
            val categories = repository.getAllCategories()
            val dock = repository.resolveDockApps(apps)
            val homeApps = repository.resolveHomeScreenApps(apps)
            val isDefault = repository.isDefaultLauncher()

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    allApps = apps,
                    allCategories = categories,
                    dockApps = dock,
                    homeScreenApps = homeApps,
                    isDefaultLauncher = isDefault
                )
            }
            recomputeFilteredState()
        }
    }

    fun openAppDrawer() {
        _uiState.update { it.copy(isAppDrawerOpen = true) }
    }

    fun closeAppDrawer() {
        _uiState.update { it.copy(isAppDrawerOpen = false, searchQuery = "") }
        recomputeFilteredState()
    }

    fun requestSetDefaultLauncher() {
        _uiState.update { it.copy(isDefaultBannerDismissed = true) }
        repository.requestSetDefaultLauncher()
    }

    fun dismissDefaultBanner() {
        _uiState.update { it.copy(isDefaultBannerDismissed = true) }
    }

    fun createCustomCategory(title: String): AppCategory {
        val newCategory = repository.addCustomCategory(title)
        val updatedCategories = repository.getAllCategories()
        _uiState.update { it.copy(allCategories = updatedCategories) }
        recomputeFilteredState()
        return newCategory
    }

    /**
     * Moves a category up in the display order.
     */
    fun moveCategoryUp(category: AppCategory) {
        val list = _uiState.value.allCategories.toMutableList()
        val index = list.indexOfFirst { it.id == category.id }
        if (index > 1) { // 0 is always ALL
            val temp = list[index]
            list[index] = list[index - 1]
            list[index - 1] = temp
            repository.saveCategoryOrder(list)
            _uiState.update { it.copy(allCategories = list) }
            recomputeFilteredState()
        }
    }

    /**
     * Moves a category down in the display order.
     */
    fun moveCategoryDown(category: AppCategory) {
        val list = _uiState.value.allCategories.toMutableList()
        val index = list.indexOfFirst { it.id == category.id }
        if (index in 1 until list.size - 1) {
            val temp = list[index]
            list[index] = list[index + 1]
            list[index + 1] = temp
            repository.saveCategoryOrder(list)
            _uiState.update { it.copy(allCategories = list) }
            recomputeFilteredState()
        }
    }

    // --- App Reminders and Alarms ---
    fun saveReminder(reminder: AppReminder) {
        reminderManager.saveReminder(reminder)
    }

    fun deleteReminder(reminderId: String) {
        reminderManager.deleteReminder(reminderId)
    }

    fun getRemindersForApp(packageName: String): List<AppReminder> {
        return reminderManager.getRemindersForApp(packageName)
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        recomputeFilteredState()
    }

    fun onCategorySelected(category: AppCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
        recomputeFilteredState()
    }

    fun moveAppToCategory(app: AppItem, newCategory: AppCategory) {
        viewModelScope.launch {
            repository.setAppCategory(app.packageName, newCategory)
            val updatedApps = _uiState.value.allApps.map { item ->
                if (item.packageName == app.packageName) {
                    item.copy(category = newCategory)
                } else {
                    item
                }
            }
            _uiState.update { it.copy(allApps = updatedApps) }
            recomputeFilteredState()
        }
    }

    fun setViewMode(mode: ViewMode) {
        repository.saveViewMode(mode.name)
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun launchApp(app: AppItem) {
        repository.launchApp(app)
    }

    fun openAppInfo(app: AppItem) {
        repository.openAppInfo(app)
    }

    fun uninstallApp(app: AppItem) {
        repository.uninstallApp(app)
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        recomputeFilteredState()
    }

    private fun recomputeFilteredState() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val currentCategory = _uiState.value.selectedCategory
        val all = _uiState.value.allApps
        val categoryOrder = _uiState.value.allCategories.map { it.id }

        // 1. Filter by search query first across all apps
        val queryMatchingApps = all.filter { app ->
            query.isEmpty() ||
            app.label.lowercase().contains(query) ||
            app.packageName.lowercase().contains(query)
        }

        // 2. Compute counts for each category from the matching apps
        val counts = queryMatchingApps.groupBy { it.category }
            .mapValues { it.value.size }

        // 3. Group all matching apps by category sorted according to user custom order
        val grouped = queryMatchingApps.groupBy { it.category }
            .toSortedMap(compareBy { cat ->
                val idx = categoryOrder.indexOf(cat.id)
                if (idx != -1) idx else 999
            })

        // 4. Determine items to display based on selected category
        val filteredList = if (currentCategory.id == AppCategory.ALL.id) {
            queryMatchingApps
        } else {
            queryMatchingApps.filter { it.category.id == currentCategory.id }
        }

        _uiState.update {
            it.copy(
                filteredApps = filteredList,
                appsByCategory = grouped,
                categoryCounts = counts
            )
        }
    }
}
