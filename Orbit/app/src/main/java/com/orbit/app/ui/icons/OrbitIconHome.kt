package com.orbit.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Ícone "Início" — versões outline (inativo) e filled (ativo). */
object OrbitIconHome {

    val Outline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitHomeOutline",
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
                moveTo(3f, 11f)
                lineTo(12f, 3f)
                lineTo(21f, 11f)
                lineTo(21f, 20f)
                lineTo(15f, 20f)
                lineTo(15f, 14f)
                lineTo(9f, 14f)
                lineTo(9f, 20f)
                lineTo(3f, 20f)
                close()
            }
        }.build()
    }

    val Filled: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitHomeFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(3f, 11f)
                lineTo(12f, 3f)
                lineTo(21f, 11f)
                lineTo(21f, 20f)
                lineTo(15f, 20f)
                lineTo(15f, 14f)
                lineTo(9f, 14f)
                lineTo(9f, 20f)
                lineTo(3f, 20f)
                close()
            }
        }.build()
    }
}