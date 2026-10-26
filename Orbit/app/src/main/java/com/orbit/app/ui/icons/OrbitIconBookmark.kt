package com.orbit.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ícone "Guardados" — outline (inativo) e filled (ativo). */
object OrbitIconBookmark {

    val Outline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitBookmarkOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(6f, 4f)
                curveTo(6f, 3.45f, 6.45f, 3f, 7f, 3f)
                lineTo(17f, 3f)
                curveTo(17.55f, 3f, 18f, 3.45f, 18f, 4f)
                lineTo(18f, 21f)
                lineTo(12f, 17f)
                lineTo(6f, 21f)
                close()
            }
        }.build()
    }

    val Filled: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitBookmarkFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(6f, 4f)
                curveTo(6f, 3.45f, 6.45f, 3f, 7f, 3f)
                lineTo(17f, 3f)
                curveTo(17.55f, 3f, 18f, 3.45f, 18f, 4f)
                lineTo(18f, 21f)
                lineTo(12f, 17f)
                lineTo(6f, 21f)
                close()
            }
        }.build()
    }
}