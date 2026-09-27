package com.osr.ps5debugger.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osr.ps5debugger.ui.theme.PS5ThemeColors
import com.osr.ps5debugger.ui.common.PS5Icons
import com.osr.ps5debugger.ui.state.MainState
import com.osr.ps5debugger.domain.model.MemoryRange

@Composable
internal fun OpenedRegionsBar(state: MainState) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(PS5ThemeColors.SecondaryBg)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        state.activeMaps.forEach { map ->
            MemoryRegionTab(
                map = map,
                isSelected = state.activeMap?.start == map.start,
                onClick = { state.activeMap = map },
                onClose = {
                    val index = state.activeMaps.indexOf(map)
                    state.activeMaps.remove(map)
                    if (state.activeMap?.start == map.start) {
                        state.activeMap = if (state.activeMaps.isNotEmpty()) {
                            if (index < state.activeMaps.size) state.activeMaps[index] else state.activeMaps.last()
                        } else {
                            null
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun MemoryRegionTab(
    map: com.osr.ps5debugger.domain.model.MemoryRange,
    isSelected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val backgroundColor = if (isSelected) PS5ThemeColors.Surface else Color.Transparent
    val contentColor = if (isSelected) PS5ThemeColors.AccentCyan else PS5ThemeColors.TextMuted
    val borderColor = if (isSelected) PS5ThemeColors.BorderColor else Color.Transparent

    Box(
        modifier = Modifier
            .height(26.dp)
            .background(backgroundColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val titleIdSuffix = if (!map.titleId.isNullOrEmpty()) " [${map.titleId}]" else ""
            Text(
                text = (if (map.name.isEmpty()) "0x${map.start.toString(16).uppercase()}" else map.name) + titleIdSuffix,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Icon(
                imageVector = PS5Icons.Close,
                contentDescription = "Close",
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(12.dp)
                    .clickable { onClose() }
            )
        }
    }
}


