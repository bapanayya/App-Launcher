package com.cleanlauncher.app.ui.screens

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import com.cleanlauncher.app.data.model.AppCategory
import com.cleanlauncher.app.data.model.AppItem
import com.cleanlauncher.app.data.model.AppReminder
import com.cleanlauncher.app.ui.LauncherUiState
import com.cleanlauncher.app.ui.LauncherViewModel
import com.cleanlauncher.app.ui.ViewMode
import java.util.UUID

@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    onRequestSetDefaultLauncher: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedAppForMenu by remember { mutableStateOf<AppItem?>(null) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showReorderCategoriesDialog by remember { mutableStateOf(false) }

    // Intercept back button:
    // If in drawer, close drawer back to ultra-clean home screen.
    // If searching or in a filtered category, clear that first.
    BackHandler(enabled = state.isAppDrawerOpen || state.searchQuery.isNotEmpty() || state.selectedCategory.id != AppCategory.ALL.id) {
        if (state.searchQuery.isNotEmpty()) {
            viewModel.clearSearch()
        } else if (state.selectedCategory.id != AppCategory.ALL.id) {
            viewModel.onCategorySelected(AppCategory.ALL)
        } else if (state.isAppDrawerOpen) {
            viewModel.closeAppDrawer()
        }
    }

    val drawerBackgroundScrim by animateColorAsState(
        targetValue = if (state.isAppDrawerOpen) {
            MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 300),
        label = "DrawerScrimTransition"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            LauncherDock(
                dockApps = state.dockApps,
                onAppClick = { viewModel.launchApp(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(drawerBackgroundScrim)
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Note: Date & Time banner removed as requested for an ultra-clean look

            // 1. Prompt to set as default app launcher
            if (state.shouldShowDefaultBanner) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Set as Default App Launcher",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { 
                                onRequestSetDefaultLauncher()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Set Default", style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { viewModel.dismissDefaultBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Screen Content: Either Ultra-Clean Home Screen or Categorized App Drawer
            AnimatedContent(
                targetState = state.isAppDrawerOpen,
                label = "HomeToDrawerTransition",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { isDrawerOpen ->
                if (!isDrawerOpen) {
                    // ═══════════════════════════════════════════════════════════
                    // LAYER 1: ULTRA-CLEAN HOME SCREEN (No loose apps, 1 slide)
                    // ═══════════════════════════════════════════════════════════
                    UltraCleanHomeScreen(
                        homeApps = state.homeScreenApps,
                        onOpenAppDrawer = { viewModel.openAppDrawer() },
                        onAppClick = { viewModel.launchApp(it) },
                        onAppLongClick = { selectedAppForMenu = it }
                    )
                } else {
                    // ═══════════════════════════════════════════════════════════
                    // LAYER 2: CATEGORIZED APP DRAWER (All Apps Categorized)
                    // ═══════════════════════════════════════════════════════════
                    CategorizedAppDrawer(
                        state = state,
                        onCloseDrawer = { viewModel.closeAppDrawer() },
                        onSearchChange = { viewModel.onSearchQueryChanged(it) },
                        onClearSearch = { viewModel.clearSearch() },
                        onLayoutSelect = { viewModel.setViewMode(it) },
                        onSelectCategory = { targetCategory ->
                            if (state.selectedCategory.id == targetCategory.id) {
                                viewModel.onCategorySelected(AppCategory.ALL)
                            } else {
                                viewModel.onCategorySelected(targetCategory)
                            }
                        },
                        onAddCategoryClick = { showCreateCategoryDialog = true },
                        onReorderCategoriesClick = { showReorderCategoriesDialog = true },
                        onAppClick = { viewModel.launchApp(it) },
                        onAppLongClick = { selectedAppForMenu = it }
                    )
                }
            }
        }
        }

        // App contextual bottom sheet
        selectedAppForMenu?.let { app ->
            AppActionBottomSheet(
                app = app,
                allCategories = state.allCategories,
                reminders = viewModel.getRemindersForApp(app.packageName),
                onDismiss = { selectedAppForMenu = null },
                onMoveCategory = { newCategory ->
                    viewModel.moveAppToCategory(app, newCategory)
                    selectedAppForMenu = null
                },
                onCreateCategory = { title ->
                    val newCat = viewModel.createCustomCategory(title)
                    viewModel.moveAppToCategory(app, newCat)
                    selectedAppForMenu = null
                },
                onSaveReminder = { reminder ->
                    viewModel.saveReminder(reminder)
                    Toast.makeText(context, "Alarm set for ${reminder.formattedTime}", Toast.LENGTH_SHORT).show()
                },
                onDeleteReminder = { reminderId ->
                    viewModel.deleteReminder(reminderId)
                    Toast.makeText(context, "Alarm removed", Toast.LENGTH_SHORT).show()
                },
                onOpenInfo = {
                    viewModel.openAppInfo(app)
                    selectedAppForMenu = null
                },
                onUninstall = {
                    viewModel.uninstallApp(app)
                    selectedAppForMenu = null
                }
            )
        }

        // Dialog for creating a custom category
        if (showCreateCategoryDialog) {
            CreateCategoryDialog(
                onDismiss = { showCreateCategoryDialog = false },
                onConfirm = { title ->
                    viewModel.createCustomCategory(title)
                    showCreateCategoryDialog = false
                }
            )
        }

        // Dialog for moving categories up and down
        if (showReorderCategoriesDialog) {
            ReorderCategoriesDialog(
                categories = state.allCategories,
                onMoveUp = { viewModel.moveCategoryUp(it) },
                onMoveDown = { viewModel.moveCategoryDown(it) },
                onDismiss = { showReorderCategoriesDialog = false }
            )
        }
    }
}

/**
 * Ultra-Clean Home Screen.
 * Contains only:
 * 1. App Launcher (tap to open categorized apps)
 * 2. Settings
 * 3. Play Store
 * 4. Gallery / Photos
 * All other apps disappear from this single home screen!
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UltraCleanHomeScreen(
    homeApps: List<AppItem>,
    onOpenAppDrawer: () -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.4f))

        // Center 4-Icon Clean Space Card (Translucent Frosted Glass)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Black.copy(alpha = 0.38f),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. App Launcher Hub Icon
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenAppDrawer() }
                            .padding(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "App Launcher",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "App Launcher",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    blurRadius = 4f
                                )
                            ),
                            color = Color.White
                        )
                    }

                    // 2, 3, 4: Settings, Play Store, Gallery
                    homeApps.forEach { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = { onAppClick(app) },
                                    onLongClick = { onAppLongClick(app) }
                                )
                                .padding(6.dp)
                        ) {
                            AppIconImage(
                                drawable = app.icon,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.8f),
                                        blurRadius = 4f
                                    )
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

/**
 * Categorized App Drawer containing Search, Category Chips, and All Apps.
 */
@Composable
fun CategorizedAppDrawer(
    state: LauncherUiState,
    onCloseDrawer: () -> Unit,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onLayoutSelect: (ViewMode) -> Unit,
    onSelectCategory: (AppCategory) -> Unit,
    onAddCategoryClick: () -> Unit,
    onReorderCategoriesClick: () -> Unit,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Drawer Header with Close Button and Search Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = onCloseDrawer,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            SearchBar(
                query = state.searchQuery,
                onQueryChange = onSearchChange,
                onClear = onClearSearch,
                modifier = Modifier.weight(1f)
            )
        }

        // Layout Selector: Sections | Grid | Minimal List
        LayoutSelectionRow(
            currentMode = state.viewMode,
            onSelectMode = onLayoutSelect
        )

        // Category Selector Chips + Reorder & Create Options
        CategoryChipsRow(
            selectedCategory = state.selectedCategory,
            onSelectCategory = onSelectCategory,
            allCategories = state.allCategories,
            categoryCounts = state.categoryCounts,
            totalAppsCount = state.allApps.size,
            onAddCategoryClick = onAddCategoryClick,
            onReorderCategoriesClick = onReorderCategoriesClick
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Categorized App List / Grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (state.filteredApps.isEmpty()) {
                EmptySearchPlaceholder(query = state.searchQuery)
            } else {
                CategorizedAppsContent(
                    state = state,
                    onAppClick = onAppClick,
                    onAppLongClick = onAppLongClick
                )
            }
        }
    }
}

/**
 * Modern Segmented Layout Selector for choosing between Sections, Grid, and Minimal List.
 */
@Composable
fun LayoutSelectionRow(
    currentMode: ViewMode,
    onSelectMode: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LayoutOptionTab(
                label = "Sections",
                icon = Icons.Default.ViewAgenda,
                isSelected = currentMode == ViewMode.SECTIONS,
                onClick = { onSelectMode(ViewMode.SECTIONS) },
                modifier = Modifier.weight(1f)
            )
            LayoutOptionTab(
                label = "Grid",
                icon = Icons.Default.GridView,
                isSelected = currentMode == ViewMode.GRID,
                onClick = { onSelectMode(ViewMode.GRID) },
                modifier = Modifier.weight(1f)
            )
            LayoutOptionTab(
                label = "Minimal List",
                icon = Icons.Default.ViewList,
                isSelected = currentMode == ViewMode.MINIMAL_LIST,
                onClick = { onSelectMode(ViewMode.MINIMAL_LIST) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun LayoutOptionTab(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Pixel-perfect Search Bar using BasicTextField to prevent any letter clipping.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
        modifier = modifier.height(46.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                "Search apps or categories...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChipsRow(
    selectedCategory: AppCategory,
    onSelectCategory: (AppCategory) -> Unit,
    allCategories: List<AppCategory>,
    categoryCounts: Map<AppCategory, Int>,
    totalAppsCount: Int,
    onAddCategoryClick: () -> Unit,
    onReorderCategoriesClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(allCategories, key = { it.id }) { category ->
            val count = if (category.id == AppCategory.ALL.id) {
                totalAppsCount
            } else {
                categoryCounts[category] ?: 0
            }

            if (count > 0 || category.id == AppCategory.ALL.id || category.isCustom) {
                val isSelected = selectedCategory.id == category.id

                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCategory(category) },
                    label = {
                        Text(
                            text = "${category.title} ($count)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        // Reorder Categories Chip
        item {
            SuggestionChip(
                onClick = onReorderCategoriesClick,
                label = { Text("↕ Reorder", style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(16.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }

        // Add Category Chip
        item {
            SuggestionChip(
                onClick = onAddCategoryClick,
                label = { Text("+ Category", style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(16.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun CategorizedAppsContent(
    state: LauncherUiState,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem) -> Unit
) {
    when (state.viewMode) {
        ViewMode.SECTIONS -> {
            if (state.selectedCategory.id != AppCategory.ALL.id || state.searchQuery.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.filteredApps, key = { it.id }) { app ->
                        AppGridItem(
                            app = app,
                            onClick = { onAppClick(app) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    state.appsByCategory.forEach { (category, apps) ->
                        if (apps.isNotEmpty()) {
                            item(key = category.id) {
                                CategorySection(
                                    category = category,
                                    apps = apps,
                                    onAppClick = onAppClick,
                                    onAppLongClick = onAppLongClick
                                )
                            }
                        }
                    }
                }
            }
        }
        ViewMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.filteredApps, key = { it.id }) { app ->
                    AppGridItem(
                        app = app,
                        onClick = { onAppClick(app) },
                        onLongClick = { onAppLongClick(app) }
                    )
                }
            }
        }
        ViewMode.MINIMAL_LIST -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.filteredApps, key = { it.id }) { app ->
                    MinimalAppListItem(
                        app = app,
                        onClick = { onAppClick(app) },
                        onLongClick = { onAppLongClick(app) }
                    )
                }
            }
        }
    }
}

/**
 * Clean, compact row item for Minimal List layout.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MinimalAppListItem(
    app: AppItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconImage(
                drawable = app.icon,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Subtle Category badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
            ) {
                Text(
                    text = app.category.title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun CategorySection(
    category: AppCategory,
    apps: List<AppItem>,
    onAppClick: (AppItem) -> Unit,
    onAppLongClick: (AppItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${apps.size} apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        val rows = apps.chunked(4)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    rowApps.forEach { app ->
                        Box(modifier = Modifier.weight(1f)) {
                            AppGridItem(
                                app = app,
                                onClick = { onAppClick(app) },
                                onLongClick = { onAppLongClick(app) }
                            )
                        }
                    }
                    repeat(4 - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppGridItem(
    app: AppItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp)
    ) {
        AppIconImage(
            drawable = app.icon,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun AppIconImage(
    drawable: Drawable?,
    modifier: Modifier = Modifier
) {
    if (drawable != null) {
        val bitmap = remember(drawable) {
            drawable.toBitmap(width = 96, height = 96)
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun LauncherDock(
    dockApps: List<AppItem>,
    onAppClick: (AppItem) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            dockApps.forEach { app ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAppClick(app) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    AppIconImage(
                        drawable = app.icon,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.75f),
                                blurRadius = 4f
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppActionBottomSheet(
    app: AppItem,
    allCategories: List<AppCategory>,
    reminders: List<AppReminder>,
    onDismiss: () -> Unit,
    onMoveCategory: (AppCategory) -> Unit,
    onCreateCategory: (String) -> Unit,
    onSaveReminder: (AppReminder) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onOpenInfo: () -> Unit,
    onUninstall: () -> Unit
) {
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showInlineCreateCategoryDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        if (!showCategoryPicker) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(
                        drawable = app.icon,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Category: ${app.category.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Set App Alert & Voice Alarm
                ListItem(
                    headlineContent = { Text("Set App Alert & Voice Alarm") },
                    supportingContent = { 
                        if (reminders.isNotEmpty()) {
                            Text("${reminders.size} active alert(s): ${reminders.joinToString { it.formattedTime }}")
                        } else {
                            Text("Buzzer alarm + custom voice announcement")
                        }
                    },
                    leadingContent = { 
                        Icon(
                            imageVector = Icons.Default.Alarm, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary 
                        ) 
                    },
                    modifier = Modifier.clickable { showReminderDialog = true }
                )

                // 2. Move to Category
                ListItem(
                    headlineContent = { Text("Move to Another Category") },
                    supportingContent = { Text("Currently in ${app.category.title}") },
                    leadingContent = { 
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DriveFileMove, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary 
                        ) 
                    },
                    modifier = Modifier.clickable { showCategoryPicker = true }
                )

                // 3. App Info & Permissions
                ListItem(
                    headlineContent = { Text("App Info & Permissions") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenInfo() }
                )

                // 4. Uninstall option
                if (!app.isSystemApp) {
                    ListItem(
                        headlineContent = {
                            Text(
                                "Uninstall",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        modifier = Modifier.clickable { onUninstall() }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            // Category picker sub-view
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showCategoryPicker = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Move \"${app.label}\" to",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // + Create New Category Action
                ListItem(
                    headlineContent = { 
                        Text(
                            "+ Create New Category", 
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        ) 
                    },
                    leadingContent = {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showInlineCreateCategoryDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                val assignableCategories = allCategories.filter { it.id != AppCategory.ALL.id }
                assignableCategories.forEach { category ->
                    val isCurrent = app.category.id == category.id
                    ListItem(
                        headlineContent = {
                            Text(
                                text = category.title,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingContent = {
                            if (isCurrent) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onMoveCategory(category)
                            }
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showInlineCreateCategoryDialog) {
        CreateCategoryDialog(
            onDismiss = { showInlineCreateCategoryDialog = false },
            onConfirm = { title ->
                onCreateCategory(title)
                showInlineCreateCategoryDialog = false
            }
        )
    }

    if (showReminderDialog) {
        AppReminderDialog(
            app = app,
            existingReminders = reminders,
            onDismiss = { showReminderDialog = false },
            onSaveReminder = { reminder ->
                onSaveReminder(reminder)
                showReminderDialog = false
            },
            onDeleteReminder = { reminderId ->
                onDeleteReminder(reminderId)
            }
        )
    }
}

/**
 * App Alert & Voice Alarm Dialog.
 * Allows setting time (Hour:Minute), Daily repeat, and custom voice text.
 */
@Composable
fun AppReminderDialog(
    app: AppItem,
    existingReminders: List<AppReminder>,
    onDismiss: () -> Unit,
    onSaveReminder: (AppReminder) -> Unit,
    onDeleteReminder: (String) -> Unit
) {
    var hourText by remember { mutableStateOf("09") }
    var minuteText by remember { mutableStateOf("00") }
    var isDaily by remember { mutableStateOf(true) }
    var voiceText by remember { mutableStateOf("You Need to Post Attendance") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Alert for ${app.label}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Time input row (24h format: HH : MM)
                Text(
                    text = "Time (24-Hour Format)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { if (it.length <= 2) hourText = it },
                        label = { Text("HH (00-23)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp)
                    )
                    Text(" : ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { if (it.length <= 2) minuteText = it },
                        label = { Text("MM (00-59)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp)
                    )
                }

                // Daily Repeat Switch
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isDaily) "Repeat: Every Day" else "Repeat: Once",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isDaily,
                        onCheckedChange = { isDaily = it }
                    )
                }

                // Spoken Voice Alert Text
                OutlinedTextField(
                    value = voiceText,
                    onValueChange = { voiceText = it },
                    label = { Text("Spoken Voice Message (TTS)") },
                    placeholder = { Text("e.g. You Need to Post Attendance") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // List existing reminders with delete option
                if (existingReminders.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Active Alerts:", style = MaterialTheme.typography.labelSmall)
                    existingReminders.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⏰ ${r.formattedTime} (${if (r.isDaily) "Daily" else "Once"})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDeleteReminder(r.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = hourText.toIntOrNull()?.coerceIn(0, 23) ?: 9
                    val m = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    val reminder = AppReminder(
                        id = UUID.randomUUID().toString(),
                        packageName = app.packageName,
                        appName = app.label,
                        hour = h,
                        minute = m,
                        isDaily = isDaily,
                        customVoiceText = if (voiceText.isNotBlank()) voiceText.trim() else "You need to open ${app.label}"
                    )
                    onSaveReminder(reminder)
                }
            ) {
                Text("Set Alarm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog to reorder categories up and down.
 */
@Composable
fun ReorderCategoriesDialog(
    categories: List<AppCategory>,
    onMoveUp: (AppCategory) -> Unit,
    onMoveDown: (AppCategory) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reorder Categories") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                val reorderable = categories.filter { it.id != AppCategory.ALL.id }
                items(reorderable, key = { it.id }) { cat ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = cat.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onMoveUp(cat) }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                        }
                        IconButton(onClick = { onMoveDown(cat) }) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Category Name") },
                placeholder = { Text("e.g. Study, Banking, Travel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text.trim())
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EmptySearchPlaceholder(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No apps found for \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
