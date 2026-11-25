package com.mrboomdev.scrollix.features.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Owns the settings navigation: the search query, the highlighted row of a
 * search result and the open section for both the one-pane and two-pane
 * layouts. The settings values themselves live in [AppSettingsStore].
 */
internal class SettingsScreenViewModel : ViewModel() {
    var categoryQuery by mutableStateOf("")
        private set

    var highlightTitle by mutableStateOf<String?>(null)
        private set

    var selectedCategoryName by mutableStateOf(SettingsCategory.ChatList.name)
        private set

    var openedCategoryName by mutableStateOf("")
        private set

    val selectedCategory: SettingsCategory
        get() = SettingsCategory.entries
            .firstOrNull { category -> category.name == selectedCategoryName }
            ?: SettingsCategory.ChatList

    val openedCategory: SettingsCategory?
        get() = SettingsCategory.entries.firstOrNull { it.name == openedCategoryName }

    fun updateQuery(value: String) {
        categoryQuery = value
    }

    /** A search result carries the row to highlight after the section opens. */
    fun openCategory(category: SettingsCategory, entry: SettingsEntry?) {
        selectedCategoryName = category.name
        openedCategoryName = category.name
        highlightTitle = entry?.title
    }

    fun closeCategory() {
        openedCategoryName = ""
    }

    fun clearHighlight() {
        highlightTitle = null
    }
}
