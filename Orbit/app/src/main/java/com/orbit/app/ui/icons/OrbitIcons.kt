package com.orbit.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small built-in vector set so Orbit doesn't need a large icon pack dependency. */
object OrbitIcons {
    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitHome",
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

    val Explore: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitExplore",
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
                moveTo(12f, 3f)
                curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f)
                curveTo(3f, 16.97f, 7.03f, 21f, 12f, 21f)
                curveTo(16.97f, 21f, 21f, 16.97f, 21f, 12f)
                curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f)
                close()
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(16f, 8f)
                lineTo(13.5f, 13.5f)
                lineTo(8f, 16f)
                lineTo(10.5f, 10.5f)
                close()
            }
        }.build()
    }

    val Bookmark: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitBookmark",
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

    val Person: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitPerson",
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
}