package com.gorite.cyclemap.ui.layout

import com.gorite.cyclemap.*
import com.gorite.cyclemap.utils.*
import com.gorite.cyclemap.navigation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MapDrawerContent(
    selectedMenu: String,
    onClose: () -> Unit,
    onSearchClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onLicenseClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDeveloperOptionsClick: () -> Unit,
    onVersionClick: () -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "CycleMap",
                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(52.dp),
            ) {
                Text("メニューを閉じる", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("目的地検索") },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_lucide_search),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                selected = selectedMenu == "検索",
                onClick = onSearchClick,
            )
            NavigationDrawerItem(
                label = { Text("地図ダウンロード（47都道府県）") },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_lucide_download),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                selected = selectedMenu == "ダウンロード",
                onClick = onDownloadClick,
            )
            NavigationDrawerItem(
                label = { Text("ライセンス") },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_lucide_scale),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                selected = selectedMenu == "ライセンス",
                onClick = onLicenseClick,
            )
            NavigationDrawerItem(
                label = { Text("設定") },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_lucide_settings),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                selected = selectedMenu == "設定",
                onClick = onSettingsClick,
            )
            NavigationDrawerItem(
                label = { Text("開発者オプション") },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_lucide_wrench),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                selected = selectedMenu == "開発者",
                onClick = onDeveloperOptionsClick,
            )
            NavigationDrawerItem(
                label = { Text("バージョン情報") },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_lucide_info),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                selected = selectedMenu == "バージョン情報",
                onClick = onVersionClick,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
