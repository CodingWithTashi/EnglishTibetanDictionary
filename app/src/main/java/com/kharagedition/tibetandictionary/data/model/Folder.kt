package com.kharagedition.tibetandictionary.data.model

/** A user folder of saved words; [count] is the number of saved entries in it. */
data class Folder(
    val id: Long,
    val name: String,
    val position: Int,
    val count: Int,
)
