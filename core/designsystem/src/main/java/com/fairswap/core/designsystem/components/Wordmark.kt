package com.fairswap.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fairswap.core.designsystem.R

@Composable
fun FairSwapWordmark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.fairswap_wordmark_light_background),
        contentDescription = "FairSwap",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(40.dp)
    )
}
