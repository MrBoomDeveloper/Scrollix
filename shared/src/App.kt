package com.mrboomdev.scrollix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.ui.icons.account_circle_icon
import com.mrboomdev.scrollix.ui.icons.explore_icon
import com.mrboomdev.scrollix.ui.icons.home_icon
import com.mrboomdev.scrollix.ui.icons.home_icon_filled
import com.mrboomdev.scrollix.ui.icons.menu_icon
import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.components.MediaViewerOverlay
import com.mrboomdev.scrollix.ui.icons.settings_icon
import com.mrboomdev.scrollix.ui.theme.appTypography
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import com.mrboomdev.scrollix.ui.navigation.Route
import com.mrboomdev.scrollix.ui.navigation.rememberAppEntryProvider
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

private data class AppNavTab(
    val isActive: Boolean,
    val icon: ImageVector,
    val title: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalSerializationApi::class)
@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    val isExpanded = isWideLayout()
    val isTwoPaneScreen = isTwoPaneLayout()
    
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclassesOfSealed<Route>()
                }
            }
        }, Route.Home
    )
    
    LaunchedEffect(Unit) {
        ApiManager.init()
    }
    
    val selectedDialog = remember { mutableStateOf<Route.Dialog?>(null) }
    val currentRoute = backStack.lastOrNull()
    
    val showNavigation = remember(currentRoute, isTwoPaneScreen) {
        if(!isTwoPaneScreen && currentRoute is Route.Dialog) {
            return@remember false
        }
        
        currentRoute !is Route.Login
    }
    
    val tabs = remember(currentRoute) {
        val isHomeTab = currentRoute is Route.Home || currentRoute is Route.Dialog
        
        listOf(
            AppNavTab(
                isActive = isHomeTab,
                icon = if(isHomeTab) home_icon_filled else home_icon,
                title = "Home",
                onClick = {
                    selectedDialog.value = null

                    if (backStack.lastOrNull() != Route.Home) {
                        backStack.clear()
                        backStack.add(Route.Home)
                    }
                }
            ),

            AppNavTab(
                isActive = false,
                icon = explore_icon,
                title = "Explore",
                onClick = {

                }
            ),

            AppNavTab(
                isActive = false,
                icon = search_icon,
                title = "Search",
                onClick = {
                    
                }
            ),

            AppNavTab(
                isActive = currentRoute is Route.Accounts ||
                        currentRoute is Route.Login,
                icon = account_circle_icon,
                title = "Accounts",
                onClick = {
                    if (backStack.lastOrNull() != Route.Accounts) {
                        backStack.add(Route.Accounts)
                    }
                }
            )
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surfaceContainer = Color(0xff101010)
        ),
        typography = appTypography()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
            Row {
                AnimatedVisibility(showNavigation && isExpanded) {
                    Row {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.background,
                            header = {
                                Box {
                                    var expand by remember { mutableStateOf(false) }
                                    
                                    IconButton(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        onClick = {
                                            expand = !expand
                                        }
                                    ) {
                                        Icon(
                                            tint = MaterialTheme.colorScheme.secondary,
                                            imageVector = menu_icon,
                                            contentDescription = null
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = expand,
                                        onDismissRequest = { expand = false },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = settings_icon,
                                                    contentDescription = null
                                                )
                                            },

                                            text = {
                                                Text(
                                                    text = "Settings"
                                                )
                                            },

                                            onClick = {
                                                if (backStack.lastOrNull() != Route.Settings) {
                                                    backStack.add(Route.Settings)
                                                }
                                                
                                                expand = false
                                            }
                                        )
                                    }
                                }
                            }
                        ) {
                            tabs.forEach { tab ->
                                NavigationRailItem(
                                    selected = tab.isActive,
                                    onClick = tab.onClick,

                                    colors = NavigationRailItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.primary,
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary
                                    ),

                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = null
                                        )
                                    },

                                    label = {
                                        Text(
                                            text = tab.title
                                        )
                                    }
                                )
                            }
                        }

                        VerticalDivider(
                            color = MaterialTheme.colorScheme.surfaceContainer
                        )
                    }
                }
                
                Column(Modifier.fillMaxSize()) {
                    NavDisplay(
                        modifier = Modifier.weight(1f),
                        backStack = backStack,
                        
                        entryProvider = rememberAppEntryProvider(
                            backStack = backStack,
                            selectedDialog = selectedDialog,
                            onSelectDialog = { selectedDialog.value = it }
                        ),

                        transitionSpec = {
                            ContentTransform(
                                fadeIn() + expandIn(),
                                fadeOut() + shrinkOut()
                            )
                        },

                        popTransitionSpec = {
                            ContentTransform(
                                fadeIn() + expandIn(),
                                fadeOut() + shrinkOut()
                            )
                        },

                        predictivePopTransitionSpec = {
                            ContentTransform(
                                fadeIn(),
                                fadeOut()
                            )
                        },

                        entryDecorators = listOf(
                            rememberViewModelStoreNavEntryDecorator(),
                            rememberSaveableStateHolderNavEntryDecorator()
                        )
                    )

                    AnimatedVisibility(showNavigation && !isExpanded) {
                        ShortNavigationBar(
                            containerColor = MaterialTheme.colorScheme.background
                        ) {
                            tabs.forEach { tab ->
                                ShortNavigationBarItem(
                                    selected = tab.isActive,
                                    onClick = tab.onClick,

                                    colors = ShortNavigationBarItemDefaults.colors(
                                        selectedIndicatorColor = MaterialTheme.colorScheme.primary,
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary
                                    ),

                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = null
                                        )
                                    },

                                    label = {
                                        Text(
                                            text = tab.title
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

                MediaViewerOverlay()
            }
        }
    }
}