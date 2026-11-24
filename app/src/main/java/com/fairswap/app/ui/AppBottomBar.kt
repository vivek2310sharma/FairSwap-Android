package com.fairswap.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairswap.core.designsystem.theme.FairSwapDeepGreen
import com.fairswap.core.designsystem.R as DesignR

private val MintGreenSelected = Color(0xFFE6F4EA)
private val InactiveSlateGray = Color(0xFF64748B)

@Composable
fun AppBottomBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    hasUnreadChats: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Home
                FloatingTabItem(
                    selected = currentTab == AppTab.HOME,
                    label = stringResource(id = DesignR.string.nav_home),
                    selectedIcon = Icons.Default.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    onClick = { onTabSelected(AppTab.HOME) }
                )

                // 2. Saved
                FloatingTabItem(
                    selected = currentTab == AppTab.SAVED,
                    label = stringResource(id = DesignR.string.nav_saved),
                    selectedIcon = Icons.Default.Favorite,
                    unselectedIcon = Icons.Outlined.FavoriteBorder,
                    onClick = { onTabSelected(AppTab.SAVED) }
                )

                // 3. Sell (Raised Camera + Action)
                FloatingSellTabItem(
                    selected = currentTab == AppTab.SELL,
                    label = stringResource(id = DesignR.string.nav_sell),
                    onClick = { onTabSelected(AppTab.SELL) }
                )

                // 4. Chats
                FloatingTabItem(
                    selected = currentTab == AppTab.CHATS,
                    label = stringResource(id = DesignR.string.nav_chats),
                    selectedIcon = Icons.Default.ChatBubble,
                    unselectedIcon = Icons.Outlined.ChatBubbleOutline,
                    hasBadge = hasUnreadChats,
                    onClick = { onTabSelected(AppTab.CHATS) }
                )

                // 5. You
                FloatingTabItem(
                    selected = currentTab == AppTab.YOU,
                    label = stringResource(id = DesignR.string.nav_you),
                    selectedIcon = Icons.Default.Person,
                    unselectedIcon = Icons.Outlined.PersonOutline,
                    onClick = { onTabSelected(AppTab.YOU) }
                )
            }
        }
    }
}

@Composable
private fun FloatingTabItem(
    selected: Boolean,
    label: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    hasBadge: Boolean = false,
    onClick: () -> Unit,
) {
    val pillColor by animateColorAsState(
        targetValue = if (selected) MintGreenSelected else Color.Transparent,
        label = "pillColor"
    )
    val iconColor = if (selected) FairSwapDeepGreen else InactiveSlateGray
    val textColor = if (selected) MaterialTheme.colorScheme.onSurface else InactiveSlateGray

    Column(
        modifier = Modifier
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(pillColor)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box {
                Icon(
                    imageVector = if (selected) selectedIcon else unselectedIcon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
                if (hasBadge) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
private fun FloatingSellTabItem(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val textColor = if (selected) FairSwapDeepGreen else InactiveSlateGray

    Column(
        modifier = Modifier
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clip(CircleShape)
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false)
            )
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = FairSwapDeepGreen,
            shadowElevation = 6.dp,
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
