package com.prometheus.model

import kotlin.random.Random

data class ChatMessage(
    val id: String = Random.nextLong().toString(),
    val text: String,
    val isUser: Boolean
)
