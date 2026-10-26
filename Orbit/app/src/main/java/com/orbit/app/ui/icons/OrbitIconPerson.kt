package com.orbit.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ícone "Perfil" — outline (inativo) e filled (ativo). */
object OrbitIconPerson {

    val Outline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitPersonOutline",
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
                moveTo(12f, 12f)
                curveTo(14.49f, 12f, 16.5f, 9.99f, 16.5f, 7.5f)
                curveTo(16.5f, 5.01f, 14.49f, 3f, 12f, 3f)
                curveTo(9.51f, 3f, 7.5f, 5.01f, 7.5f, 7.5f)
                curveTo(7.5f, 9.99f, 9.51f, 12f, 12f, 12f)
                close()
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 21f)
                curveTo(4f, 17.7f, 7.35f, 15f, 12f, 15f)
                curveTo(16.65f, 15f, 20f, 17.7f, 20f, 21f)
            }
        }.build()
    }

    val Filled: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitPersonFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 12f)
                curveTo(14.49f, 12f, 16.5f, 9.99f, 16.5f, 7.5f)
                curveTo(16.5f, 5.01f, 14.49f, 3f, 12f, 3f)
                curveTo(9.51f, 3f, 7.5f, 5.01f, 7.5f, 7.5f)
                curveTo(7.5f, 9.99f, 9.51f, 12f, 12f, 12f)
                close()
            }
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4f, 21f)
                curveTo(4f, 17.7f, 7.35f, 15f, 12f, 15f)
                curveTo(16.65f, 15f, 20f, 17.7f, 20f, 21f)
                curveTo(20f, 22.1f, 18.9f, 23f, 17.5f, 23f)
                lineTo(6.5f, 23f)
                curveTo(5.1f, 23f, 4f, 22.1f, 4f, 21f)
                close()
            }
        }.build()
    }
}