package com.mrboomdev.scrollix

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplay.popTransitionSpec
import androidx.navigation3.ui.NavDisplay.predictivePopTransitionSpec
import androidx.navigation3.ui.NavDisplay.transitionSpec
import androidx.savedstate.serialization.SavedStateConfiguration
import com.mrboomdev.scrollix.data.api.ApiManager
import com.mrboomdev.scrollix.ui.icons.account_circle_icon
import com.mrboomdev.scrollix.ui.icons.home_icon
import com.mrboomdev.scrollix.ui.icons.home_icon_filled
import com.mrboomdev.scrollix.ui.icons.search_icon
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import com.mrboomdev.scrollix.ui.navigation.Route
import com.mrboomdev.scrollix.ui.navigation.rememberAppEntryProvider
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@OptIn(ExperimentalSerializationApi::class)
@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclassesOfSealed<Route>()
//                    subclass(Route.Home::class, Route.Home.serializer())
//                    subclass(Route.Login::class, Route.Login.serializer())
                }
            }
        }, Route.Home
    )
    
    LaunchedEffect(Unit) {
        ApiManager.init()
    }
    
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color.Black,
            surfaceContainer = Color(0xff101010)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                NavDisplay(
                    modifier = Modifier.weight(1f),
                    backStack = backStack,
                    entryProvider = rememberAppEntryProvider(backStack),

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

                ShortNavigationBar(
                    containerColor = MaterialTheme.colorScheme.background
                ) { 
                    val itemColors = ShortNavigationBarItemDefaults.colors(
                        selectedIndicatorColor = MaterialTheme.colorScheme.primary,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    )
                    
                    ShortNavigationBarItem(
                        selected = true,
                        onClick = {},
                        colors = itemColors,
                        
                        icon = {
                            Icon(
                                imageVector = home_icon_filled,
                                contentDescription = null
                            )
                        },
                        
                        label = {
                            Text("Home")
                        }
                    )

//                    ShortNavigationBarItem(
//                        selected = false,
//                        onClick = {},
//                        colors = itemColors,
//
//                        icon = {
//                            Icon(
//                                imageVector = search_icon,
//                                contentDescription = null
//                            )
//                        },
//
//                        label = {
//                            Text("Feed")
//                        }
//                    )

                    ShortNavigationBarItem(
                        selected = false,
                        onClick = {},
                        colors = itemColors,

                        icon = {
                            Icon(
                                imageVector = account_circle_icon,
                                contentDescription = null
                            )
                        },

                        label = {
                            Text("Profile")
                        }
                    )
                }
            }
        }
    }
}