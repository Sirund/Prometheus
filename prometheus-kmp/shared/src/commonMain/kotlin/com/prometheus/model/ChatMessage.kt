package com.prometheus.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class ChatMessage(
    val id: String = Random.nextLong().toString(),
    val text: String,
    val isUser: Boolean,
    val imagePath: String? = null
)
