package com.mrboomdev.scrollix

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.window.core.layout.WindowSizeClass

expect fun Modifier.onSecondaryClick(action: () -> Unit): Modifier

@Composable
@Deprecated("Use currentAdaptiveLayoutInfo instead!")
fun isWideLayout(): Boolean {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    
    return windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    )
}

@Composable
@Deprecated("Use currentAdaptiveLayoutInfo instead!")
fun isTwoPaneLayout(): Boolean {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass

    return windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
    )
}

fun Any.toJsonStringByReflection(): String {
    return toJsonStringByReflection(
        depth = 0,
        tabSize = 4
    )
}

private fun Any?.toJsonStringByReflection(depth: Int, tabSize: Int): String {
    if(this == null) {
        return "null"
    }
    
    if(this is String) {
        return "\"$this\""
    }
    
    if(this is Boolean || this is Number) {
        return this.toString()
    }
    
    if(this is Collection<*>) {
        return this.toTypedArray().toJsonStringByReflection(depth, tabSize)
    }
    
    if(this is Array<*>) {
        if(this.isEmpty()) {
            return "[]"
        }
        
        val iterator = this.iterator()
        
        return buildString {
            append("[")

            while (iterator.hasNext()) {
                val item = iterator.next()
                appendLine()
                append(" ".repeat(tabSize).repeat(depth))
                append(item.toJsonStringByReflection(depth + 1, tabSize))

                if(iterator.hasNext()) {
                    append(",")
                }
            }

            append(" ".repeat(tabSize).repeat(depth))
            append("]")
        }
    }

    val clazz = this::class.java
    val fields = clazz.declaredFields
    
    if(fields.isEmpty()) {
        return "{}"
    }
    
    val iterator = fields.asIterable().iterator()
    
    return buildString {
        append("{")
        
        while(iterator.hasNext()) {
            val field = iterator.next()
            field.isAccessible = true
            appendLine()
            append(" ".repeat(tabSize).repeat(depth + 1))
            append("\"${field.name}\": ")
            append(field.get(this@toJsonStringByReflection).toJsonStringByReflection(depth + 1, tabSize))
            
            if(iterator.hasNext()) {
                append(", ")
            }
        }
        
        appendLine()
        append(" ".repeat(tabSize).repeat(depth))
        append("}")
    }
}