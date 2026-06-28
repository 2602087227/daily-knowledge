package com.dailyknowledge.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dailyknowledge.ui.screen.*
import com.dailyknowledge.ui.viewmodel.*

/**
 * 底部导航栏目标定义
 */
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Today : BottomNavItem("today", "今日知识", Icons.Default.Today)
    data object Library : BottomNavItem("library", "知识库", Icons.Default.LibraryBooks)
    data object Favorites : BottomNavItem("favorites", "收藏", Icons.Default.Star)
    data object Search : BottomNavItem("search", "搜索", Icons.Default.Search)
}

val bottomNavItems = listOf(
    BottomNavItem.Today,
    BottomNavItem.Library,
    BottomNavItem.Favorites,
    BottomNavItem.Search
)

/**
 * 主应用导航 — 底部导航栏 + NavHost
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // 共享 ViewModel 实例（在同一 NavBackStack 范围内）
    val todayViewModel: TodayViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    val favoritesViewModel: FavoritesViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true

                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route) {
                                // 避免在回退栈中创建大量实例
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Today.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Today.route) {
                TodayKnowledgeScreen(
                    viewModel = todayViewModel,
                    onNavigateToImport = {
                        // 切换到知识库 Tab 并触发导入
                        navController.navigate(BottomNavItem.Library.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(BottomNavItem.Library.route) {
                KnowledgeLibraryScreen(
                    viewModel = libraryViewModel,
                    onNavigateToImport = { /* 已在知识库页内处理导入 */ }
                )
            }
            composable(BottomNavItem.Favorites.route) {
                FavoritesScreen(viewModel = favoritesViewModel)
            }
            composable(BottomNavItem.Search.route) {
                SearchScreen(viewModel = searchViewModel)
            }
        }
    }
}
