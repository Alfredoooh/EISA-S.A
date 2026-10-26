package com.orbit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.icons.OrbitIconBookmark
import com.orbit.app.ui.icons.OrbitIconGames
import com.orbit.app.ui.icons.OrbitIconHome
import com.orbit.app.ui.icons.OrbitIconPerson
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
    val iconOutline: ImageVector,
    val iconFilled: ImageVector,
) {
    Home(
        label = "Início",
        title = "Orbit",
        iconOutline = OrbitIconHome.Outline,
        iconFilled = OrbitIconHome.Filled,
    ),
    Games(
        label = "Jogos",
        title = "Jogos",
        iconOutline = OrbitIconGames.Outline,
        iconFilled = OrbitIconGames.Filled,
    ),
    Saved(
        label = "Guardados",
        title = "Guardados",
        iconOutline = OrbitIconBookmark.Outline,
        iconFilled = OrbitIconBookmark.Filled,
    ),
    Profile(
        label = "Perfil",
        title = "Perfil",
        iconOutline = OrbitIconPerson.Outline,
        iconFilled = OrbitIconPerson.Filled,
    ),
}

@Composable
private fun OrbitApp() {
    var selectedDestination by rememberSaveable { mutableStateOf(OrbitDestination.Home) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(selectedDestination.title) },
            )
        },
        bottomBar = {
            OrbitBottomBar(
                selectedDestination = selectedDestination,
                onDestinationSelected = { selectedDestination = it },
            )
        },
    ) { innerPadding ->
        // Sem Crossfade e sem conteúdo: apenas espaço reservado, leve e instantâneo.
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding))
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
            val selected = selectedDestination == destination
            ShortNavigationBarItem(
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.iconFilled else destination.iconOutline,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(destination.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}