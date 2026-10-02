package com.appao

data class Comment(
    val user: String,
    val text: String,
    val avatar: String,
    val date: String,
    val reactions: MutableMap<String, Int> = mutableMapOf()
)
