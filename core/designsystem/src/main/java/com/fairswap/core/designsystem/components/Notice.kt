package com.fairswap.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fairswap.core.designsystem.theme.LocalExtendedColors

enum class NoticeType {
    WARNING, ERROR, SUCCESS
}

@Composable
fun FairSwapNotice(
    message: String,
    modifier: Modifier = Modifier,
    type: NoticeType = NoticeType.WARNING,
    icon: @Composable (() -> Unit)? = null
) {
    val extendedColors = LocalExtendedColors.current
    val bgColor = when (type) {
        NoticeType.WARNING -> extendedColors.warningBg
        NoticeType.ERROR -> extendedColors.errorBg
        NoticeType.SUCCESS -> extendedColors.successBg
    }
    val borderColor = when (type) {
        NoticeType.WARNING -> extendedColors.warningBorder
        NoticeType.ERROR -> extendedColors.errorBorder
        NoticeType.SUCCESS -> extendedColors.successBorder
    }
    val textColor = when (type) {
        NoticeType.WARNING -> extendedColors.warningText
        NoticeType.ERROR -> extendedColors.errorText
        NoticeType.SUCCESS -> extendedColors.successText
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = message,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
