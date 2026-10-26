package com.mrboomdev.scrollix

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
