package com.mrboomdev.scrollix

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mrboomdev.scrollix.resources.Res
import com.mrboomdev.scrollix.resources.compose_multiplatform
import com.mrboomdev.scrollix.ui.icons.account_circle_icon
import com.mrboomdev.scrollix.ui.icons.add_icon
import com.mrboomdev.scrollix.ui.icons.brand_awareness_icon
import com.mrboomdev.scrollix.ui.icons.check_icon
import com.mrboomdev.scrollix.ui.icons.group_icon
import com.mrboomdev.scrollix.ui.icons.keep_icon
import org.jetbrains.compose.reload.DevelopmentEntryPoint

import com.mrboomdev.scrollix.ui.icons.search_icon
import com.mrboomdev.scrollix.ui.icons.smart_toy_icon
import org.jetbrains.compose.resources.painterResource

@Composable
@Preview
@DevelopmentEntryPoint
fun App() {
    var selectedTab by remember { mutableStateOf(0) }
    var expandCreationMenu by remember { mutableStateOf(false) }
    
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
            Column { 
                // App header
                Surface(
                    color = MaterialTheme.colorScheme.background
                ) { 
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp, 8.dp, 8.dp, 0.dp)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                            ))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),

                                onClick = {

                                }
                            ) {
                                Icon(
                                    imageVector = search_icon,
                                    contentDescription = null
                                )
                            }

                            Text(
                                text = "Scrollix",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Box(
                                modifier = Modifier,
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    tint = MaterialTheme.colorScheme.primary,
                                    imageVector = account_circle_icon,
                                    contentDescription = null
                                )

                                Button(
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                        .size(IconButtonDefaults.smallContainerSize()),
                                    colors = ButtonDefaults.textButtonColors(),
                                    onClick = {

                                    }
                                ) {}
                            }
                        }

                        // Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val chipColors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                            )
                            
                            FilterChip(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text("DM") },
                                colors = chipColors,
                                leadingIcon = {
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        imageVector = smart_toy_icon,
                                        contentDescription = null
                                    )
                                }
                            )

                            FilterChip(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text("Groups") },
                                colors = chipColors,
                                leadingIcon = {
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        imageVector = group_icon,
                                        contentDescription = null
                                    )
                                }
                            )

                            FilterChip(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                label = { Text("Channels") },
                                colors = chipColors,
                                leadingIcon = {
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        imageVector = brand_awareness_icon,
                                        contentDescription = null
                                    )
                                }
                            )

                            FilterChip(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                label = { Text("Bots") },
                                colors = chipColors,
                                leadingIcon = { 
                                    Icon(
                                        modifier = Modifier.size(20.dp),
                                        imageVector = smart_toy_icon, 
                                        contentDescription = null
                                    ) 
                                }
                            )
                        }
                    }
                }
                
                // App content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(99) {
                            DialogItem()
                        }
                    }
                    
                    FloatingActionButtonMenu(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        expanded = expandCreationMenu,
                        button = {
                            ToggleFloatingActionButton(
                                checked = expandCreationMenu,
                                onCheckedChange = { expandCreationMenu = !expandCreationMenu }
                            ) {
                                Icon(
                                    imageVector = add_icon,
                                    contentDescription = null
                                )
                            }
                        }
                    ) { 
                        FloatingActionButtonMenuItem(
                            icon = {
                                
                            },
                            
                            text = {
                                Text("New group")
                            },
                            
                            onClick = {
                                
                            }
                        )

                        FloatingActionButtonMenuItem(
                            icon = {

                            },

                            text = {
                                Text("New secret chat")
                            },

                            onClick = {

                            }
                        )

                        FloatingActionButtonMenuItem(
                            icon = {

                            },

                            text = {
                                Text("New contact")
                            },

                            onClick = {

                            }
                        )

                        FloatingActionButtonMenuItem(
                            icon = {

                            },

                            text = {
                                Text("New channel")
                            },

                            onClick = {

                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogItem() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        onClick = {
            
        }
    ) {  
        Row(
            modifier = Modifier.padding(8.dp)
        ) {
            Image(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .size(48.dp),
                painter = painterResource(Res.drawable.compose_multiplatform),
                contentDescription = null
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) { 
                Text(
                    text = "Dialog name"
                )
                
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    text = "Last message"
                )
            }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.End
            ) { 
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { 
                    Icon(
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                        imageVector = check_icon,
                        contentDescription = null
                    )
                    
                    Text(
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        text = "12:00"
                    )
                }
                
                Icon(
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                    imageVector = keep_icon,
                    contentDescription = null
                )
            }
        }
    }
}