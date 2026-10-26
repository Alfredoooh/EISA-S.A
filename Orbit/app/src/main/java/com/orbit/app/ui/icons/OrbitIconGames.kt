package com.orbit.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Ícone "Jogos" — comando/gamepad simplificado.
 * Substitui o antigo ícone "Explorar".
 */
object OrbitIconGames {

    val Outline: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitGamesOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Corpo do comando
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(6.5f, 7f)
                lineTo(17.5f, 7f)
                curveTo(19.43f, 7f, 21f, 8.57f, 21f, 10.5f)
                lineTo(21f, 15.5f)
                curveTo(21f, 17.16f, 19.66f, 18.5f, 18f, 18.5f)
                curveTo(17.16f, 18.5f, 16.37f, 18.14f, 15.82f, 17.5f)
                lineTo(14.6f, 16.08f)
                curveTo(14.22f, 15.64f, 13.67f, 15.39f, 13.09f, 15.39f)
                lineTo(10.91f, 15.39f)
                curveTo(10.33f, 15.39f, 9.78f, 15.64f, 9.4f, 16.08f)
                lineTo(8.18f, 17.5f)
                curveTo(7.63f, 18.14f, 6.84f, 18.5f, 6f, 18.5f)
                curveTo(4.34f, 18.5f, 3f, 17.16f, 3f, 15.5f)
                lineTo(3f, 10.5f)
                curveTo(3f, 8.57f, 4.57f, 7f, 6.5f, 7f)
                close()
            }
            // Cruz direcional
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7.5f, 10.2f)
                lineTo(7.5f, 13.4f)
            }
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.9f, 11.8f)
                lineTo(9.1f, 11.8f)
            }
            // Botões
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(16.3f, 10f)
                curveTo(16.86f, 10f, 17.3f, 10.45f, 17.3f, 11f)
                curveTo(17.3f, 11.55f, 16.86f, 12f, 16.3f, 12f)
                curveTo(15.75f, 12f, 15.3f, 11.55f, 15.3f, 11f)
                curveTo(15.3f, 10.45f, 15.75f, 10f, 16.3f, 10f)
                close()
            }
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(18.7f, 12.4f)
                curveTo(19.25f, 12.4f, 19.7f, 12.85f, 19.7f, 13.4f)
                curveTo(19.7f, 13.95f, 19.25f, 14.4f, 18.7f, 14.4f)
                curveTo(18.14f, 14.4f, 17.7f, 13.95f, 17.7f, 13.4f)
                curveTo(17.7f, 12.85f, 18.14f, 12.4f, 18.7f, 12.4f)
                close()
            }
        }.build()
    }

    val Filled: ImageVector by lazy {
        ImageVector.Builder(
            name = "OrbitGamesFilled",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            // Corpo preenchido
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(6.5f, 7f)
                lineTo(17.5f, 7f)
                curveTo(19.43f, 7f, 21f, 8.57f, 21f, 10.5f)
                lineTo(21f, 15.5f)
                curveTo(21f, 17.16f, 19.66f, 18.5f, 18f, 18.5f)
                curveTo(17.16f, 18.5f, 16.37f, 18.14f, 15.82f, 17.5f)
                lineTo(14.6f, 16.08f)
                curveTo(14.22f, 15.64f, 13.67f, 15.39f, 13.09f, 15.39f)
                lineTo(10.91f, 15.39f)
                curveTo(10.33f, 15.39f, 9.78f, 15.64f, 9.4f, 16.08f)
                lineTo(8.18f, 17.5f)
                curveTo(7.63f, 18.14f, 6.84f, 18.5f, 6f, 18.5f)
                curveTo(4.34f, 18.5f, 3f, 17.16f, 3f, 15.5f)
                lineTo(3f, 10.5f)
                curveTo(3f, 8.57f, 4.57f, 7f, 6.5f, 7f)
                close()
            }
            // Cruz direcional "vazada" (cor de fundo simulada com traço branco fino)
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7.5f, 10.2f)
                lineTo(7.5f, 13.4f)
            }
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5.9f, 11.8f)
                lineTo(9.1f, 11.8f)
            }
            // Botões
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(16.3f, 10f)
                curveTo(16.86f, 10f, 17.3f, 10.45f, 17.3f, 11f)
                curveTo(17.3f, 11.55f, 16.86f, 12f, 16.3f, 12f)
                curveTo(15.75f, 12f, 15.3f, 11.55f, 15.3f, 11f)
                curveTo(15.3f, 10.45f, 15.75f, 10f, 16.3f, 10f)
                close()
            }
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(18.7f, 12.4f)
                curveTo(19.25f, 12.4f, 19.7f, 12.85f, 19.7f, 13.4f)
                curveTo(19.7f, 13.95f, 19.25f, 14.4f, 18.7f, 14.4f)
                curveTo(18.14f, 14.4f, 17.7f, 13.95f, 17.7f, 13.4f)
                curveTo(17.7f, 12.85f, 18.14f, 12.4f, 18.7f, 12.4f)
                close()
            }
        }.build()
    }
}