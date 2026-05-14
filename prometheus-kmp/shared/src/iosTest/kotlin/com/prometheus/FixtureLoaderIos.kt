package com.prometheus

import platform.Foundation.*

actual fun loadFixture(name: String): String {
    val parts = name.split(".")
    val resourceName = parts.dropLast(1).joinToString(".")
    val ext = parts.lastOrNull()
    val path = NSBundle.bundleForClass(object {}.javaClass)
        .pathForResource(resourceName, ext)
        ?: error("Fixture not found: $name")
    return NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null) as String
}
