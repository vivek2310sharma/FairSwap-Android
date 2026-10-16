package com.fairswap.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fairswap.core.designsystem.R as DesignR

@Composable
fun AppBottomBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        val colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        NavigationBarItem(
            selected = currentTab == AppTab.HOME,
            onClick = { onTabSelected(AppTab.HOME) },
            icon = { Icon(Icons.Default.Home, contentDescription = stringResource(id = DesignR.string.nav_home)) },
            label = { Text(stringResource(id = DesignR.string.nav_home)) },
            colors = colors
        )
        NavigationBarItem(
            selected = currentTab == AppTab.SAVED,
            onClick = { onTabSelected(AppTab.SAVED) },
            icon = { Icon(Icons.Default.Favorite, contentDescription = stringResource(id = DesignR.string.nav_saved)) },
            label = { Text(stringResource(id = DesignR.string.nav_saved)) },
            colors = colors
        )
        NavigationBarItem(
            selected = currentTab == AppTab.SELL,
            onClick = { onTabSelected(AppTab.SELL) },
            icon = { Icon(Icons.Default.Add, contentDescription = stringResource(id = DesignR.string.nav_sell)) },
            label = { Text(stringResource(id = DesignR.string.nav_sell)) },
            colors = colors
        )
        NavigationBarItem(
            selected = currentTab == AppTab.CHATS,
            onClick = { onTabSelected(AppTab.CHATS) },
            icon = { Icon(Icons.Default.Email, contentDescription = stringResource(id = DesignR.string.nav_chats)) },
            label = { Text(stringResource(id = DesignR.string.nav_chats)) },
            colors = colors
        )
        NavigationBarItem(
            selected = currentTab == AppTab.YOU,
            onClick = { onTabSelected(AppTab.YOU) },
            icon = { Icon(Icons.Default.Person, contentDescription = stringResource(id = DesignR.string.nav_you)) },
            label = { Text(stringResource(id = DesignR.string.nav_you)) },
            colors = colors
        )
    }
}
