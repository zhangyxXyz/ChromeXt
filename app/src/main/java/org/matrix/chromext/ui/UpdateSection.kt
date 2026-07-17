package org.matrix.chromext.ui

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import org.matrix.chromext.UiLocalization
import org.matrix.chromext.ui.common.NavigationSettingItem
import org.matrix.chromext.ui.common.SettingGroup
import org.matrix.chromext.ui.common.SettingItem
import java.io.File
import kotlinx.coroutines.launch
import org.matrix.chromext.BuildConfig
import org.matrix.chromext.update.AppRelease
import org.matrix.chromext.update.UpdateClient
import org.matrix.chromext.update.isNewerVersion
import org.matrix.chromext.update.isSameVersion

@Composable
fun UpdateSection(controller: ChromeXtController, onOpenReleaseHistory: () -> Unit) {
  val context = controller.context
  val chinese = controller.isChinese
  val client = remember { UpdateClient() }
  val scope = rememberCoroutineScope()
  var checking by remember { mutableStateOf(false) }
  var release by remember { mutableStateOf<AppRelease?>(null) }
  var message by remember { mutableStateOf<String?>(null) }
  var downloading by remember { mutableStateOf(false) }
  var downloaded by remember { mutableLongStateOf(0L) }
  var total by remember { mutableLongStateOf(0L) }

  fun checkUpdate() {
    checking = true
    scope.launch {
      client
          .latestRelease()
          .onSuccess {
            checking = false
            if (it == null) message = ut(chinese, "没有找到已发布的 Release", "No published release found")
            else release = it
          }
          .onFailure {
            checking = false
            message =
                UiLocalization.error(
                    chinese,
                    it.localizedMessage,
                    "检查更新失败",
                    "Update check failed",
                )
          }
    }
  }

  Column {
    Text(
        ut(chinese, "更新", "Updates"),
        Modifier.padding(start = 4.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
          Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
              Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                if (checking) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                else
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
              }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
              Text(
                  ut(chinese, "检查更新", "Check for updates"),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold)
              Text(
                  if (checking) ut(chinese, "正在检查 GitHub…", "Checking GitHub…")
                  else ut(chinese, "当前版本 ${BuildConfig.VERSION_NAME}", "Current version ${BuildConfig.VERSION_NAME}"),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          HorizontalDivider(
              Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant)
          Row(
              Modifier.fillMaxWidth().padding(12.dp),
              horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = ::checkUpdate,
                    enabled = !checking,
                    modifier = Modifier.weight(1f)) {
                      Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp))
                      Spacer(Modifier.width(7.dp))
                      Text(ut(chinese, "检查更新", "Check"))
                    }
                TextButton(onClick = onOpenReleaseHistory, modifier = Modifier.weight(1f)) {
                  Icon(Icons.Rounded.History, null, Modifier.size(18.dp))
                  Spacer(Modifier.width(7.dp))
                  Text(ut(chinese, "发布历史", "History"))
                }
              }
        }
  }

  release?.let { found ->
    val newer = isNewerVersion(found.tag, BuildConfig.VERSION_NAME)
    val same = isSameVersion(found.tag, BuildConfig.VERSION_NAME)
    AlertDialog(
        onDismissRequest = { if (!downloading) release = null },
        title = {
          Text(
              if (newer) ut(chinese, "发现新版本 ${found.tag}", "New version ${found.tag}")
              else ut(chinese, "已是当前版本", "Current version"))
        },
        text = {
          Column {
            Text(found.name, fontWeight = FontWeight.Bold)
            if (found.body.isNotBlank()) {
              Box(
                  Modifier.padding(top = 10.dp)
                      .heightIn(max = 260.dp)
                      .verticalScroll(rememberScrollState())) {
                    Markdown(
                        found.body,
                        typography = aboutMarkdownTypography(),
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    )
                  }
            }
            if (downloading) {
              val fraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
              if (total > 0) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
              else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
              Text(
                  if (total > 0) "${(fraction * 100).toInt()}% · ${Formatter.formatFileSize(context, downloaded)} / ${Formatter.formatFileSize(context, total)}"
                  else Formatter.formatFileSize(context, downloaded),
                  Modifier.padding(top = 8.dp),
                  style = MaterialTheme.typography.bodySmall)
            }
          }
        },
        dismissButton = {
          TextButton(enabled = !downloading, onClick = { release = null }) {
            Text(ut(chinese, "取消", "Cancel"))
          }
        },
        confirmButton = {
          Button(
              enabled = !downloading,
              onClick = {
                val asset = found.apk
                if (asset == null || (!newer && !same)) {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(found.pageUrl)))
                } else {
                  downloading = true
                  scope.launch {
                    val target = File(context.cacheDir, "updates/${asset.name}")
                    client
                        .download(asset, target) { copied, length ->
                          downloaded = copied
                          total = length
                        }
                        .onSuccess {
                          downloading = false
                          runCatching { UpdateClient.validateAndInstall(context, it) }
                              .onFailure { error ->
                                message =
                                    UiLocalization.error(
                                        chinese,
                                        error.localizedMessage,
                                        "安装更新失败",
                                        "Could not install update",
                                    )
                              }
                        }
                        .onFailure {
                          downloading = false
                          message =
                              UiLocalization.error(
                                  chinese,
                                  it.localizedMessage,
                                  "下载失败",
                                  "Download failed",
                              )
                        }
                  }
                }
              }) {
                Text(
                    when {
                      found.apk == null -> ut(chinese, "查看 Release", "View release")
                      newer -> ut(chinese, "下载并安装", "Download and install")
                      else -> ut(chinese, "重新下载并安装", "Download again")
                    })
              }
        })
  }
  message?.let {
    AlertDialog(
        onDismissRequest = { message = null },
        title = { Text(ut(chinese, "更新", "Update")) },
        text = { Text(it) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
  }
}

private fun ut(chinese: Boolean, zh: String, en: String): String =
    UiLocalization.text(chinese, zh, en)
