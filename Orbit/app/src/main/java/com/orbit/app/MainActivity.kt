package com.orbit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import com.orbit.app.ui.icons.OrbitIcons
import com.orbit.app.ui.theme.OrbitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrbitTheme {
                OrbitApp()
            }
        }
    }
}

private enum class OrbitDestination(
    val label: String,
    val title: String,
    val description: String,
    val heroTitle: String,
    val heroDescription: String,
    val icon: ImageVector,
) {
    Home(
        label = "Início",
        title = "Orbit",
        description = "O seu espaço para descobrir, guardar e organizar ideias.",
        heroTitle = "Tudo em órbita.",
        heroDescription = "Um ponto de partida simples para tudo o que importa.",
        icon = OrbitIcons.Home,
    ),
    Explore(
        label = "Explorar",
        title = "Explorar",
        description = "Encontre novas ideias e caminhos para seguir.",
        heroTitle = "Novas possibilidades.",
        heroDescription = "Deixe a curiosidade apontar o próximo destino.",
        icon = OrbitIcons.Explore,
    ),
    Saved(
        label = "Guardados",
        title = "Guardados",
        description = "Os seus conteúdos importantes ficam sempre por perto.",
        heroTitle = "Guarde o que importa.",
        heroDescription = "Uma coleção simples, sempre ao seu alcance.",
        icon = OrbitIcons.Bookmark,
    ),
    Profile(
        label = "Perfil",
        title = "Perfil",
        description = "Um espaço pessoal, claro e fácil de usar.",
        heroTitle = "A sua órbita.",
        heroDescription = "Uma experiência feita para acompanhar o seu ritmo.",
        icon = OrbitIcons.Person,
    ),
}

@Composable
private fun OrbitApp() {
    var selectedDestination by rememberSaveable { mutableStateOf(OrbitDestination.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            OrbitBottomBar(
                selectedDestination = selectedDestination,
                onDestinationSelected = { selectedDestination = it },
            )
        },
    ) { innerPadding ->
        Crossfade(
            targetState = selectedDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "orbit-destination-transition",
        ) { destination ->
            OrbitDestinationScreen(destination)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OrbitBottomBar(
    selectedDestination: OrbitDestination,
    onDestinationSelected: (OrbitDestination) -> Unit,
) {
    ShortNavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        arrangement = ShortNavigationBarArrangement.EqualWeight,
    ) {
        OrbitDestination.entries.forEach { destination ->
            ShortNavigationBarItem(
                selected = selectedDestination == destination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun OrbitDestinationScreen(destination: OrbitDestination) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "ORBIT  /  ESPAÇO PESSOAL",
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                letterSpacing = 1.6.sp,
            )
            Text(
                text = destination.title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
            )
            Text(
                text = destination.description,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(252.dp)
                    .padding(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(0.72f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "UM NOVO PONTO DE PARTIDA",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.78f),
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = destination.heroTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onPrimaryContainer,
                    )
                    Text(
                        text = destination.heroDescription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.82f),
                    )
                }
                OrbitOrbGraphic(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(104.dp),
                )
            }
        }

        Text(
            text = "Acesso rápido",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onBackground,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FeatureTile(
                modifier = Modifier.weight(1f),
                icon = OrbitIcons.Explore,
                title = "Descobrir",
                subtitle = "Novas ideias",
            )
            FeatureTile(
                modifier = Modifier.weight(1f),
                icon = OrbitIcons.Bookmark,
                title = "Coleção",
                subtitle = "Os seus guardados",
            )
        }
    }
}

@Composable
private fun FeatureTile(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.secondaryContainer,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.onSecondaryContainer,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OrbitOrbGraphic(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        drawCircle(
            color = primary.copy(alpha = 0.08f),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.5f),
            radius = radius * 0.72f,
            center = center,
            style = Stroke(width = 1.8.dp.toPx()),
        )
        drawCircle(
            color = tertiary.copy(alpha = 0.78f),
            radius = radius * 0.20f,
            center = center,
        )
        drawCircle(
            color = primary,
            radius = radius * 0.09f,
            center = Offset(center.x + radius * 0.58f, center.y - radius * 0.43f),
        )
        drawCircle(
            color = primary.copy(alpha = 0.76f),
            radius = radius * 0.065f,
            center = Offset(center.x - radius * 0.62f, center.y + radius * 0.40f),
        )
    }
}
