package com.appao

import android.graphics.Color

/** Fluent primary palette adapted from the supplied light/dark pairs. */
data class FluentColorPair(val light: Int, val dark: Int)

object FluentColorPalette {
    const val microsoftBlueLight = 0xFF0F6CBD.toInt()
    const val microsoftBlueDark = 0xFF479EF5.toInt()
    val defaultPrimaryColor: Int get() = microsoftBlueLight

    val primaryColorPairs = listOf(
        FluentColorPair(Color.rgb(0x0F, 0x6C, 0xBD), Color.rgb(0x47, 0x9E, 0xF5)),
        FluentColorPair(Color.rgb(0x87, 0x64, 0xB8), Color.rgb(0xB4, 0xA0, 0xFF)),
        FluentColorPair(Color.rgb(0xC2, 0x39, 0xB3), Color.rgb(0xE6, 0x8A, 0xD8)),
        FluentColorPair(Color.rgb(0xD1, 0x34, 0x38), Color.rgb(0xF1, 0x70, 0x7B)),
        FluentColorPair(Color.rgb(0xCA, 0x50, 0x10), Color.rgb(0xFF, 0x8C, 0x5A)),
        FluentColorPair(Color.rgb(0x98, 0x6F, 0x0B), Color.rgb(0xFF, 0xCC, 0x66)),
        FluentColorPair(Color.rgb(0x0B, 0x6A, 0x0B), Color.rgb(0x6B, 0xCB, 0x6B)),
        FluentColorPair(Color.rgb(0x00, 0x76, 0x7A), Color.rgb(0x4D, 0xD0, 0xD6)),
        FluentColorPair(Color.rgb(0x03, 0x83, 0x87), Color.rgb(0x3F, 0xD9, 0xDE)),
        FluentColorPair(Color.rgb(0x51, 0x5C, 0x6B), Color.rgb(0x9B, 0xA7, 0xB4))
    )
}
