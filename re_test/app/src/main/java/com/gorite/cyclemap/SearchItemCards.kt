package com.gorite.cyclemap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gorite.cyclemap.data.FavoriteSpot
import java.util.Locale

@Composable
internal fun SearchResultItemCard(
    result: SearchResult,
    isFavorite: Boolean,
    onResultSelected: (SearchResult) -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onResultSelected(result) },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8F9FA),
        border = BorderStroke(1.dp, Color(0xFFE8EAED)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(result.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF202124))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatCategoryLabel(result.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5F6368),
                        modifier = Modifier.weight(1f),
                    )
                    result.distanceMeters?.let { dist ->
                        val distStr = if (dist >= 1000.0) {
                            String.format(Locale.US, "%.1f km", dist / 1000.0)
                        } else {
                            String.format(Locale.US, "%.0f m", dist)
                        }
                        Text(
                            distStr,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painterResource(if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                    contentDescription = if (isFavorite) "お気に入り解除" else "お気に入り追加",
                    tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
internal fun FavoriteSpotItemCard(
    spot: FavoriteSpot,
    onSpotSelected: (FavoriteSpot) -> Unit,
    onRemoveFavorite: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onSpotSelected(spot) },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8F9FA),
        border = BorderStroke(1.dp, Color(0xFFE8EAED)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(spot.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF202124))
                Text(
                    spot.address ?: formatCategoryLabel(spot.category),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5F6368),
                )
            }
            IconButton(
                onClick = onRemoveFavorite,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_star_filled),
                    contentDescription = "お気に入り解除",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
