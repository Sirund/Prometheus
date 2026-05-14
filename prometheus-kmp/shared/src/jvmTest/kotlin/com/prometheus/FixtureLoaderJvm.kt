package com.prometheus

actual fun loadFixture(name: String): String =
    object {}.javaClass.getResource("/$name")?.readText()
        ?: error("Fixture not found: $name")
