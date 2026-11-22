package com.nextz.kanomtts.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage

private val StatsCream = Color(0xFFF5EBDD)
private val StatsBrown = Color(0xFF493326)
private val StatsGold = Color(0xFF9A7024)
private val StatsTrack = Color(0xFFD9C7AA)

@Composable
fun ReadingStatisticsCard(
    state: ReadingStatisticsUiState,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, StatsGold, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StatsCream),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoStories, contentDescription = null, tint = StatsBrown)
                Spacer(Modifier.width(8.dp))
                Text("สถิติการอ่าน", color = StatsBrown, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onMore) {
                    Text("ดูข้อมูลเพิ่มเติม", color = StatsBrown)
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = StatsBrown, modifier = Modifier.size(16.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.latestBook?.coverPath != null) {
                    AsyncImage(
                        model = state.latestBook.coverPath,
                        contentDescription = "ปกหนังสือเล่มล่าสุด",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 48.dp, height = 66.dp).clip(RoundedCornerShape(5.dp)),
                    )
                } else {
                    Box(Modifier.size(width = 48.dp, height = 66.dp).clip(RoundedCornerShape(5.dp)).background(StatsTrack), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoStories, contentDescription = null, tint = StatsBrown)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("เล่มล่าสุดที่เปิดอ่าน", color = StatsBrown.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    Text(state.latestBook?.title ?: "ยังไม่มีประวัติการเปิดหนังสือ", color = StatsBrown, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (state.latestBook != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { state.latestBook.percent },
                                modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(4.dp)),
                                color = StatsGold,
                                trackColor = StatsTrack,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${(state.latestBook.percent * 100).toInt()}%", color = StatsBrown, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MetricText("วันนี้", formatReadingDuration(state.todayReadingMs))
                MetricText("สัปดาห์นี้", "${state.daysReadThisWeek}/7 วัน")
                MetricText("อ่านจบแล้ว", "${state.completedCount} เล่ม")
            }
            ReadingDaysChart(days = (0..6).mapNotNull { index -> state.chartDays.takeLast(7).getOrNull(index) }, compact = true)
        }
    }
}

@Composable
private fun MetricText(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = StatsBrown.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = StatsBrown, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ReadingStatisticsScreen(
    onBack: () -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(),
    viewModel: ReadingStatisticsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsState().value
    Column(
        Modifier.fillMaxSize().padding(contentPadding).background(MaterialTheme.colorScheme.background),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "กลับ") }
            Text("สถิติการอ่าน", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatisticsRange.entries.forEach { range ->
                        FilterChip(
                            selected = state.range == range,
                            onClick = { viewModel.setRange(range) },
                            label = { Text(range.label) },
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile("เวลาอ่านวันนี้", formatReadingDuration(state.todayReadingMs), Modifier.weight(1f))
                    SummaryTile("วันที่อ่านในสัปดาห์นี้", "${state.daysReadThisWeek} / 7 วัน", Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryTile("เวลาอ่าน 7 วัน", formatReadingDuration(state.weekReadingMs), Modifier.weight(1f))
                    SummaryTile("หนังสือที่อ่านจบ", "${state.completedCount} เล่ม", Modifier.weight(1f))
                }
            }
            item {
                state.mostProgressedBook?.let { book ->
                    SectionTitle("เล่มที่คืบหน้ามากที่สุด")
                    StatisticsBookRow(book)
                }
            }
            item {
                SectionTitle("เวลาอ่านรายวัน")
                ReadingDaysChart(state.chartDays, compact = false)
            }
            item { SectionTitle("กำลังอ่าน") }
            if (state.currentlyReading.isEmpty()) {
                item { Text("ยังไม่มีหนังสือที่มีความคืบหน้าในการอ่าน", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.currentlyReading, key = { it.id }) { book -> StatisticsBookRow(book) }
            }
            item { SectionTitle("อ่านจบแล้ว") }
            if (state.completedBooks.isEmpty()) {
                item { Text("ยังไม่มีหนังสือที่บันทึกว่าอ่านจบ", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.completedBooks.take(20), key = { it.id }) { book -> StatisticsBookRow(book) }
            }
        }
    }
}

@Composable
private fun SummaryTile(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = StatsCream), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = StatsBrown, style = MaterialTheme.typography.labelMedium)
            Text(value, color = StatsBrown, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
}

@Composable
private fun ReadingDaysChart(days: List<ReadingDay>, compact: Boolean) {
    val maxDuration = (days.maxOfOrNull { it.durationMs } ?: 0L).coerceAtLeast(1L)
    Row(
        Modifier
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()))
            .height(if (compact) 62.dp else 130.dp)
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val fraction = day.durationMs.toFloat() / maxDuration.toFloat()
            Column(
                Modifier.width(if (compact) 34.dp else 32.dp).height(if (compact) 62.dp else 126.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth(if (compact) 0.72f else 0.58f)
                        .height((if (compact) 34f else 92f).times(fraction.coerceAtLeast(if (day.durationMs > 0) 0.08f else 0f)).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(if (day.durationMs > 0) StatsGold else StatsTrack),
                )
                Text(
                    text = if (compact) day.date.dayOfMonth.toString() else formatReadingDay(day.date),
                    color = StatsBrown,
                    fontSize = if (compact) 9.sp else 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun StatisticsBookRow(book: BookCard) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (book.coverPath != null) {
            AsyncImage(
                model = book.coverPath,
                contentDescription = "ปก ${book.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 48.dp, height = 66.dp).clip(RoundedCornerShape(5.dp)),
            )
        } else {
            Box(Modifier.size(width = 48.dp, height = 66.dp).background(StatsTrack, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoStories, contentDescription = null, tint = StatsBrown)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(
                progress = { book.percent },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)),
                color = StatsGold,
                trackColor = StatsTrack,
            )
            Text("${(book.percent * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
