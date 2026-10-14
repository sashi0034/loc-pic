package jp.example.locpic.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.example.locpic.model.DeliveryStatus
import jp.example.locpic.model.PhotoRecord
import jp.example.locpic.model.PhotoText

private data class AppTab(val label: String, val emoji: String)

private val tabs = listOf(
    AppTab("撮影", "📷"),
    AppTab("履歴", "🗂"),
    AppTab("再送", "↻"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocPicApp(viewModel: AppViewModel) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val records by viewModel.records.collectAsStateWithLifecycle()

    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF176B5B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC6F0E5),
            surface = Color(0xFFF8FAF8),
            background = Color(0xFFF5F8F6),
            error = Color(0xFFBA1A1A),
        ),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("LocPic", fontWeight = FontWeight.Bold)
                            Text("場所と記憶を、一緒に残す", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { index, tab ->
                        val badge = if (index == 2) {
                            records.count { it.status == DeliveryStatus.FAILED }
                        } else {
                            0
                        }
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Text(tab.emoji) },
                            label = { Text(if (badge > 0) "${tab.label} ($badge)" else tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            when (selectedTab) {
                0 -> CaptureScreen(viewModel, Modifier.padding(padding))
                1 -> HistoryScreen(records, Modifier.padding(padding))
                else -> RetryScreen(viewModel, records, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun CaptureScreen(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    var note by rememberSaveable { mutableStateOf("") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(2.dp))
        if (hasCameraPermission) {
            CameraPreview(
                onReady = {
                    imageCapture = it
                    cameraError = null
                },
                onError = { cameraError = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black),
            )
        } else {
            PermissionPanel(
                onRequest = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
        }

        cameraError?.let { InlineWarning(it) }
        if (hasCameraPermission && !hasLocationPermission) {
            InlineWarning("位置情報の許可がないため、写真に場所を付けられません")
        }
        if (!viewModel.isSlackConfigured) {
            InlineWarning("Slack設定が未入力です。撮影は保存され、再送一覧に入ります")
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(200) },
            label = { Text("写真の内容・メモ") },
            placeholder = { Text("例: 駅前の看板、現場入口") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { imageCapture?.let { viewModel.captureAndPost(it, note) } },
            enabled = imageCapture != null && !captureState.isWorking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (captureState.isWorking) captureState.stage else "撮影してSlackへ投稿")
        }

        if (captureState.isWorking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        captureState.notice?.let { NoticeCard(it) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PermissionPanel(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📷", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text("撮影にはカメラの許可が必要です", fontWeight = FontWeight.Bold)
            Text("場所も許可するとGoogle Mapsリンクを添付します")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest) { Text("権限を許可する") }
        }
    }
}

@Composable
private fun HistoryScreen(records: List<PhotoRecord>, modifier: Modifier = Modifier) {
    if (records.isEmpty()) {
        EmptyState("まだ写真がありません", "撮影した写真はここで場所・時刻と一緒に見返せます", modifier)
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("撮影履歴", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${records.size} 件の写真")
        }
        items(records, key = PhotoRecord::id) { record -> PhotoRecordCard(record) }
    }
}

@Composable
private fun RetryScreen(
    viewModel: AppViewModel,
    records: List<PhotoRecord>,
    modifier: Modifier = Modifier,
) {
    val retryState by viewModel.retryState.collectAsStateWithLifecycle()
    val failed = records.filter { it.status == DeliveryStatus.FAILED }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("未送信", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (failed.isEmpty()) "未送信の写真はありません" else "${failed.size} 件を端末に保存中")
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::retryAll,
                enabled = failed.isNotEmpty() && !retryState.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (retryState.isWorking) "再送中…" else "未送信を一括再送")
            }
            if (retryState.isWorking) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = {
                        if (retryState.total == 0) 0f
                        else retryState.completed.toFloat() / retryState.total
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${retryState.completed} / ${retryState.total}")
            }
            retryState.notice?.let {
                Spacer(Modifier.height(8.dp))
                NoticeCard(it)
            }
        }
        items(failed, key = PhotoRecord::id) { record ->
            PhotoRecordCard(record, showError = true)
        }
    }
}

@Composable
private fun PhotoRecordCard(record: PhotoRecord, showError: Boolean = false) {
    val context = LocalContext.current
    val mapUrl = PhotoText.mapsUrl(record.latitude, record.longitude)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            PhotoThumbnail(
                path = record.filePath,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    record.note.ifBlank { "（メモなし）" },
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(PhotoText.capturedAt(record.capturedAtMillis), style = MaterialTheme.typography.bodySmall)
                Text(
                    when (record.status) {
                        DeliveryStatus.POSTED -> "● Slack投稿済み"
                        DeliveryStatus.FAILED -> "● 未送信"
                        DeliveryStatus.SENDING -> "● 送信中"
                    },
                    color = if (record.status == DeliveryStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                if (mapUrl != null) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, mapUrl.toUri()))
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text("Google Mapsで見る")
                    }
                } else {
                    Text("位置情報なし", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (showError && !record.lastError.isNullOrBlank()) {
            HorizontalDivider()
            Text(
                "前回のエラー: ${record.lastError}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun NoticeCard(notice: UiNotice) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notice.isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(notice.title, fontWeight = FontWeight.Bold)
            Text(notice.details, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InlineWarning(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun EmptyState(title: String, description: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🗺️", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
