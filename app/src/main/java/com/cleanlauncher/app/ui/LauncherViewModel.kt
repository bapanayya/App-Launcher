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
import kotlinx.coroutines.Dispatchers

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
    val isDefaultBannerDismissed: Boolean = false,
    val allReminders: List<AppReminder> = emptyList()
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
        _uiState.update { it.copy(viewMode = savedMode, allReminders = reminderManager.getAllReminders()) }
        loadApps()
    }

    fun loadApps(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading && _uiState.value.allApps.isEmpty()) {
                _uiState.update { it.copy(isLoading = true) }
            }
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

    /**
     * Fast, flicker-free refresh when returning from another app.
     * Does NOT tear down the UI or show loading spinners.
     */
    fun refreshOnResume() {
        val isDefault = repository.isDefaultLauncher()
        _uiState.update { it.copy(isDefaultLauncher = isDefault) }
        if (_uiState.value.allApps.isEmpty()) {
            loadApps(showLoading = true)
        }
    }

    fun openAppDrawer() {
        _uiState.update { it.copy(isAppDrawerOpen = true) }
        recomputeFilteredState()
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
        if (index > 2) { // 0 is ALL, 1 is FREQUENT
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
        if (index in 2 until list.size - 1) {
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
        _uiState.update { it.copy(allReminders = reminderManager.getAllReminders()) }
    }

    fun deleteReminder(reminderId: String) {
        reminderManager.deleteReminder(reminderId)
        _uiState.update { it.copy(allReminders = reminderManager.getAllReminders()) }
    }

    fun getRemindersForApp(packageName: String): List<AppReminder> {
        return _uiState.value.allReminders.filter { it.packageName == packageName }
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
            val identifier = if (app.isShortcut) app.id else app.packageName
            repository.setAppCategory(identifier, newCategory)
            if (newCategory.id != AppCategory.FREQUENT.id) {
                repository.removeAppFromFrequent(identifier)
            } else {
                repository.addAppToFrequent(identifier)
            }
            val updatedApps = _uiState.value.allApps.map { item ->
                if (item.id == app.id) {
                    item.copy(category = newCategory)
                } else {
                    item
                }
            }
            _uiState.update { it.copy(allApps = updatedApps) }
            recomputeFilteredState()
        }
    }

    fun toggleAppFrequent(app: AppItem) {
        viewModelScope.launch {
            val identifier = if (app.isShortcut) app.id else app.packageName
            val isCurrentlyFrequent = repository.isAppInFrequent(app)
            if (isCurrentlyFrequent) {
                repository.removeAppFromFrequent(identifier)
            } else {
                repository.addAppToFrequent(identifier)
            }
            recomputeFilteredState()
        }
    }

    fun isAppInFrequent(app: AppItem): Boolean {
        return repository.isAppInFrequent(app)
    }

    fun setViewMode(mode: ViewMode) {
        repository.saveViewMode(mode.name)
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun launchApp(app: AppItem) {
        // 1. Launch the app immediately without blocking UI thread
        repository.launchApp(app)

        // 2. Asynchronously update frequent apps without freezing the app launch transition
        viewModelScope.launch(Dispatchers.IO) {
            val query = _uiState.value.searchQuery.trim().lowercase()
            val currentCategory = _uiState.value.selectedCategory
            val all = _uiState.value.allApps
            val categoryOrder = _uiState.value.allCategories.map { it.id }

            val queryMatchingApps = all.filter { a ->
                query.isEmpty() ||
                a.label.lowercase().contains(query) ||
                a.packageName.lowercase().contains(query)
            }
            val frequentApps = repository.getFrequentlyUsedApps(queryMatchingApps)
            val counts = queryMatchingApps.groupBy { it.category }
                .mapValues { it.value.size }
                .toMutableMap()
            counts[AppCategory.FREQUENT] = frequentApps.size

            val grouped = linkedMapOf<AppCategory, List<AppItem>>()
            if (frequentApps.isNotEmpty()) {
                grouped[AppCategory.FREQUENT] = frequentApps
            }
            val otherGrouped = queryMatchingApps.groupBy { it.category }
                .toSortedMap(compareBy { cat ->
                    val idx = categoryOrder.indexOf(cat.id)
                    if (idx != -1) idx else 999
                })
            for ((cat, appList) in otherGrouped) {
                if (cat.id != AppCategory.FREQUENT.id) {
                    grouped[cat] = appList
                }
            }
            val filteredList = when (currentCategory.id) {
                AppCategory.ALL.id -> queryMatchingApps
                AppCategory.FREQUENT.id -> frequentApps
                else -> queryMatchingApps.filter { it.category.id == currentCategory.id }
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

    fun openAppInfo(app: AppItem) {
        repository.openAppInfo(app)
    }

    fun uninstallApp(app: AppItem) {
        repository.uninstallApp(app)
        if (app.isShortcut) {
            loadApps()
        }
    }

    fun addWebShortcut(title: String, url: String) {
        val id = "web_" + java.util.UUID.randomUUID().toString().take(8)
        repository.saveWebShortcut(
            id = id,
            label = title,
            packageName = "com.cleanlauncher.web",
            url = url,
            intentUri = null,
            iconPath = null
        )
        loadApps()
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

        // 2. Identify frequent apps (used at least twice a day) in alphabetical order
        val frequentApps = repository.getFrequentlyUsedApps(queryMatchingApps)

        // 3. Compute counts for each category
        val counts = queryMatchingApps.groupBy { it.category }
            .mapValues { it.value.size }
            .toMutableMap()
        
        counts[AppCategory.FREQUENT] = frequentApps.size

        // 4. Group all matching apps by category: place FREQUENT at the very top
        val grouped = linkedMapOf<AppCategory, List<AppItem>>()
        if (frequentApps.isNotEmpty()) {
            grouped[AppCategory.FREQUENT] = frequentApps
        }

        val otherGrouped = queryMatchingApps.groupBy { it.category }
            .toSortedMap(compareBy { cat ->
                val idx = categoryOrder.indexOf(cat.id)
                if (idx != -1) idx else 999
            })

        for ((cat, apps) in otherGrouped) {
            if (cat.id != AppCategory.FREQUENT.id) {
                grouped[cat] = apps
            }
        }

        // 5. Determine items to display based on selected category
        val filteredList = when (currentCategory.id) {
            AppCategory.ALL.id -> queryMatchingApps
            AppCategory.FREQUENT.id -> frequentApps
            else -> queryMatchingApps.filter { it.category.id == currentCategory.id }
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