package com.prometheus

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
actual fun loadFixture(name: String): String {
    val parts = name.split(".")
    val resourceName = parts.dropLast(1).joinToString(".")
    val ext = parts.lastOrNull()
    val path = NSBundle.mainBundle.pathForResource(resourceName, ext)
        ?: error("Fixture not found: $name")
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) as String
}
