package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.DecoyDummyData
import com.example.ui.VaultFilterTab

private val PitchBlackBg = Color(0xFF03070C)
private val DarkCapsuleBg = Color(0xFF0A131C)
private val CapsuleBorder = Color(0xFF132334)
private val BrightCyan = Color(0xFF00D2EF)
private val MutedText = Color(0xFF6C7A8E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoyVaultScreen(
    onLockClick: () -> Unit
) {
    val dummyItems = DecoyDummyData.dummyVaultItems
    var activeFilter by remember { mutableStateOf(VaultFilterTab.ALL) }
    var isGridView by remember { mutableStateOf(true) }

    val filteredDummyItems = remember(dummyItems, activeFilter) {
        dummyItems.filter { item ->
            when (activeFilter) {
                VaultFilterTab.ALL -> true
                VaultFilterTab.PHOTOS -> item.mimeType.startsWith("image/")
                VaultFilterTab.VIDEOS -> item.mimeType.startsWith("video/")
                VaultFilterTab.DOCUMENTS -> !item.mimeType.startsWith("image/") && !item.mimeType.startsWith("video/")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PitchBlackBg)
    ) {
        // Cyber Ambient Grid Overlay Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 40.dp.toPx()
            val gridColor = Color(0xFF00D2EF).copy(alpha = 0.03f)

            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += gridSpacing
            }

            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += gridSpacing
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "DECOY VAULT",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrightCyan,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = BrightCyan)
                        }
                        IconButton(onClick = { isGridView = !isGridView }) {
                            Icon(
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.GridView,
                                contentDescription = "Toggle Grid/List View",
                                tint = BrightCyan
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(DarkCapsuleBg)
                                .border(1.5.dp, BrightCyan, CircleShape)
                                .clickable { onLockClick() }
                                .testTag("lock_vault_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Vault",
                                tint = BrightCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PitchBlackBg.copy(alpha = 0.95f))
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                // Decoy Alert Warning Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F1805))
                        .border(1.dp, Color(0xFFFFB700), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Decoy Active",
                            tint = Color(0xFFFFB700),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "STEALTH DECOY MODE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB700),
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Displaying safe dummy data. Real vault is isolated.",
                                fontSize = 10.sp,
                                color = Color(0xFFD3A435)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Decoy Capsule Filter Tab Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(DarkCapsuleBg)
                        .border(1.dp, CapsuleBorder, RoundedCornerShape(30.dp))
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DecoyCapsuleFilterTabItem(
                            text = "ALL ITEMS",
                            isSelected = activeFilter == VaultFilterTab.ALL,
                            onClick = { activeFilter = VaultFilterTab.ALL },
                            modifier = Modifier.weight(1f)
                        )
                        DecoyCapsuleFilterTabItem(
                            text = "PHOTOS",
                            isSelected = activeFilter == VaultFilterTab.PHOTOS,
                            onClick = { activeFilter = VaultFilterTab.PHOTOS },
                            modifier = Modifier.weight(1f)
                        )
                        DecoyCapsuleFilterTabItem(
                            text = "VIDEOS",
                            isSelected = activeFilter == VaultFilterTab.VIDEOS,
                            onClick = { activeFilter = VaultFilterTab.VIDEOS },
                            modifier = Modifier.weight(1f)
                        )
                        DecoyCapsuleFilterTabItem(
                            text = "DOCS",
                            isSelected = activeFilter == VaultFilterTab.DOCUMENTS,
                            onClick = { activeFilter = VaultFilterTab.DOCUMENTS },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isGridView) 2 else 1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredDummyItems) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = DarkCapsuleBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CapsuleBorder)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(115.dp)
                                        .background(Color(0xFF050E17)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isVideo) Icons.Default.PlayCircle else Icons.Default.Image,
                                        contentDescription = null,
                                        tint = BrightCyan,
                                        modifier = Modifier.size(42.dp)
                                    )
                                }
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = item.originalName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${item.sizeBytes / 1024} KB",
                                        fontSize = 10.sp,
                                        color = MutedText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecoyCapsuleFilterTabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BrightCyan.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) BrightCyan else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) BrightCyan else MutedText,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}
