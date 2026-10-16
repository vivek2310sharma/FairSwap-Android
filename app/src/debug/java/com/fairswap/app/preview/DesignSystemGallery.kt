package com.fairswap.app.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fairswap.core.designsystem.components.*
import com.fairswap.core.designsystem.theme.FairSwapWebTheme
import com.fairswap.core.designsystem.theme.LocalExtendedColors

@Composable
fun Swatch(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color)
                .border(1.dp, Color.Gray)
        )
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignSystemGallery() {
    var chipSelected by remember { mutableStateOf(true) }
    var chipCount by remember { mutableStateOf(0) }
    var disCount by remember { mutableStateOf(0) }
    var btnLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Wordmark", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        FairSwapWordmark()

        Text("Swatches", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Column {
            Swatch("Primary", MaterialTheme.colorScheme.primary)
            Swatch("Background", MaterialTheme.colorScheme.background)
            Swatch("Surface", MaterialTheme.colorScheme.surface)
            Swatch("SurfaceContainer (Card)", MaterialTheme.colorScheme.surfaceContainer)
            Swatch("Error", MaterialTheme.colorScheme.error)
            Swatch("NoticeBg", LocalExtendedColors.current.warningBg)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Buttons", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.width(16.dp))
            Text("Loading:", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = btnLoading, onCheckedChange = { btnLoading = it })
        }
        
        // Intrinsic width tests
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FairSwapPrimaryButton(text = "Primary", isLoading = btnLoading, onClick = {})
            FairSwapOutlinedButton(text = "Outlined", isLoading = btnLoading, onClick = {})
            FairSwapTextButton(text = "Text", isLoading = btnLoading, onClick = {})
            FairSwapDestructiveButton(text = "Destructive", isLoading = btnLoading, onClick = {})
        }
        // Full width tests
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FairSwapPrimaryButton(text = "Primary Full", isLoading = btnLoading, onClick = {}, modifier = Modifier.fillMaxWidth())
            FairSwapOutlinedButton(text = "Outlined Full", isLoading = btnLoading, onClick = {}, modifier = Modifier.fillMaxWidth())
            FairSwapTextButton(text = "Text Full", isLoading = btnLoading, onClick = {}, modifier = Modifier.fillMaxWidth())
            FairSwapDestructiveButton(text = "Destructive Full", isLoading = btnLoading, onClick = {}, modifier = Modifier.fillMaxWidth())
        }
        
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FairSwapPrimaryButton(text = "Disabled", enabled = false, onClick = {})
            val closeIcon = ImageVector.Builder(
                name = "Close",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1.0f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Butt,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Bevel,
                strokeLineMiter = 1.0f,
                pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
            ) {
                moveTo(19f, 6.41f)
                lineTo(17.59f, 5f)
                lineTo(12f, 10.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(12f, 13.41f)
                lineTo(17.59f, 19f)
                lineTo(19f, 17.59f)
                lineTo(13.41f, 12f)
                close()
            }.build()
            FairSwapIconButton(icon = closeIcon, contentDescription = "Close", onClick = {})
        }

        Text("Data Display", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FairSwapBadge(text = "Active")
            FairSwapChip(text = "Static (no cb)") // explicitly static with no callback
            FairSwapChip(text = "Toggle (count: $chipCount)", selected = chipSelected, onClick = { 
                chipCount++
                chipSelected = !chipSelected 
            })
            FairSwapChip(text = "Disabled (count: $disCount)", enabled = false, onClick = { disCount++ })
        }
        FairSwapDivider()

        Text("Cards & Notices", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        FairSwapNotice(message = "This is a prominent warning notice.", type = NoticeType.WARNING)
        FairSwapNotice(message = "This is an error notice.", type = NoticeType.ERROR)
        FairSwapNotice(message = "This is a success notice.", type = NoticeType.SUCCESS)
        
        FairSwapCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Card Title", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text("Card body text mirroring Web styling.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Text Fields", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        FairSwapTextField(value = "Active input", onValueChange = {}, label = "Display Name", modifier = Modifier.fillMaxWidth())
        FairSwapTextField(value = "Invalid input", isError = true, onValueChange = {}, label = "City", supportingText = { Text("Error: invalid city") }, modifier = Modifier.fillMaxWidth())
        FairSwapTextField(value = "Disabled input", enabled = false, onValueChange = {}, label = "Disabled", modifier = Modifier.fillMaxWidth())
    }
}

@Preview(showBackground = true, name = "1. Gallery Light (360dp)", widthDp = 360)
@Composable
fun PreviewDesignSystemGalleryLight() {
    FairSwapWebTheme(darkTheme = false) {
        DesignSystemGallery()
    }
}

@Preview(showBackground = true, name = "2. Gallery Dark (412dp)", widthDp = 412)
@Composable
fun PreviewDesignSystemGalleryDark() {
    FairSwapWebTheme(darkTheme = true) {
        DesignSystemGallery()
    }
}

@Preview(showBackground = true, name = "3. Gallery Inverse (Dark in Light Mode)", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO, widthDp = 360)
@Composable
fun PreviewDesignSystemGalleryInverseDark() {
    FairSwapWebTheme(darkTheme = true) {
        DesignSystemGallery()
    }
}

@Preview(showBackground = true, name = "4. Gallery Inverse (Light in Dark Mode)", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, widthDp = 360)
@Composable
fun PreviewDesignSystemGalleryInverseLight() {
    FairSwapWebTheme(darkTheme = false) {
        DesignSystemGallery()
    }
}
