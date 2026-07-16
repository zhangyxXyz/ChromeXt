package org.matrix.chromext.ui

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.matrix.chromext.UiLocalization
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import org.matrix.chromext.backup.BackupManager
import org.matrix.chromext.backup.BackupMode
import org.matrix.chromext.backup.BackupSettings
import org.matrix.chromext.backup.LocalBackup
import org.matrix.chromext.backup.RemoteBackup
import org.matrix.chromext.backup.WebDavConfig

@Composable
fun BackupSettingsScreen(controller: ChromeXtController) {
  controller.revision
  val context = controller.context
  val chinese = controller.isChinese
  val manager = remember { BackupManager(context) }
  val scope = rememberCoroutineScope()
  var settings by remember { mutableStateOf(manager.settings()) }
  val connected = controller.connectedBrowserTargets()
  var targetPackage by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  var resultMessage by remember { mutableStateOf<String?>(null) }
  var showWebDavEditor by remember { mutableStateOf(false) }
  var showPasswordEditor by remember { mutableStateOf(false) }
  var showTargetChooser by remember { mutableStateOf(false) }
  var showBackupModeDialog by remember { mutableStateOf(false) }
  var pendingBackupMode by remember { mutableStateOf<BackupMode?>(null) }
  var retentionTarget by remember { mutableStateOf<String?>(null) }
  var localChoices by remember { mutableStateOf<List<LocalBackup>?>(null) }
  var remoteChoices by remember { mutableStateOf<List<RemoteBackup>?>(null) }

  LaunchedEffect(connected) {
    if (targetPackage !in connected.map(BrowserTarget::packageName)) {
      targetPackage = connected.firstOrNull()?.packageName
    }
  }

  fun save(value: BackupSettings) {
    settings = value
    manager.saveSettings(value)
  }

  fun runAction(success: String? = null, block: suspend () -> Unit) {
    busy = true
    scope.launch {
      try {
        block()
        success?.let { resultMessage = it }
        controller.refresh()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Throwable) {
        resultMessage =
            UiLocalization.error(chinese, failure.localizedMessage, "操作失败", "Operation failed")
      } finally {
        busy = false
      }
    }
  }

  fun performBackup(mode: BackupMode) {
    val target = targetPackage ?: return
    runAction {
      val result = manager.createBackup(target, mode)
      val remoteError =
          UiLocalization.error(
              chinese,
              result.remoteError,
              "WebDAV 备份失败",
              "WebDAV backup failed",
          )
      resultMessage =
          when (mode) {
            BackupMode.LOCAL -> bt(chinese, "本地备份完成", "Local backup completed")
            BackupMode.REMOTE ->
                if (result.remoteUploaded) {
                  bt(chinese, "云端备份完成", "Cloud backup completed")
                } else {
                  bt(chinese, "云端备份失败：$remoteError", "Cloud backup failed: $remoteError")
                }
            BackupMode.LOCAL_AND_REMOTE ->
                if (result.remoteUploaded) {
                  bt(chinese, "本地和云端备份完成", "Local and cloud backup completed")
                } else {
                  bt(chinese, "本地备份完成，云端失败：$remoteError", "Local backup completed; cloud failed: $remoteError")
                }
          }
    }
  }

  val treePicker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { manager.setLocalTree(uri) }
            .onSuccess {
              settings = manager.settings()
              pendingBackupMode?.let(::performBackup)
            }
            .onFailure {
              resultMessage =
                  UiLocalization.error(chinese, it.localizedMessage, "目录选择失败", "Could not select folder")
            }
        pendingBackupMode = null
      }
  val archivePicker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = targetPackage
        if (uri != null && target != null) {
          runAction(bt(chinese, "恢复请求已发送到浏览器", "Restore sent to browser")) {
            manager.restore(uri, target)
          }
        }
      }

  fun requestBackup(mode: BackupMode) {
    if (targetPackage == null) {
      showTargetChooser = true
    } else if (mode.includesLocal && settings.localTreeUri.isNullOrBlank()) {
      pendingBackupMode = mode
      treePicker.launch(null)
    } else {
      performBackup(mode)
    }
  }

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(18.dp, 10.dp, 18.dp, 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Card(
          shape = RoundedCornerShape(28.dp),
          colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
              Icon(Icons.Rounded.Backup, null, Modifier.size(36.dp))
              Text(
                  bt(chinese, "ChromeXt 完整备份", "Complete ChromeXt backup"),
                  Modifier.padding(top = 14.dp),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold)
              Text(
                  bt(
                      chinese,
                      "包含脚本源码、脚本存储、规则、运行时设置和界面设置。",
                      "Includes script sources, script storage, rules, runtime settings, and appearance."),
                  Modifier.padding(top = 7.dp),
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .78f))
            }
          }
    }
    item {
      BackupGroup(bt(chinese, "目标浏览器", "Target browser")) {
        BackupRow(
            Icons.Rounded.Settings,
            bt(chinese, "备份目标", "Backup target"),
            connected.find { it.packageName == targetPackage }?.let {
              "${it.label} · ${it.packageName}"
            }
                ?: bt(chinese, "请先打开一个已 Hook 的浏览器", "Open a hooked browser first"),
            enabled = connected.isNotEmpty(),
        ) {
          showTargetChooser = true
        }
      }
    }
    item {
      BackupGroup(bt(chinese, "备份操作", "Backup actions")) {
        BackupRow(
            Icons.Rounded.Backup,
            bt(chinese, "立即备份", "Back up now"),
            bt(chinese, "点击备份到本地和云端，长按选择目标", "Tap for local + cloud; long-press to choose a destination"),
            enabled = targetPackage != null,
            onLongClick = { showBackupModeDialog = true },
        ) {
          requestBackup(BackupMode.LOCAL_AND_REMOTE)
        }
      }
    }
    item {
      BackupGroup(bt(chinese, "本地备份", "Local backup")) {
        BackupRow(
            Icons.Rounded.Folder,
            bt(chinese, "备份目录", "Backup folder"),
            settings.localTreeUri?.let(::displayPath)
                ?: bt(chinese, "尚未选择目录", "No folder selected"),
        ) {
          treePicker.launch(null)
        }
        BackupRow(
            Icons.Rounded.Restore,
            bt(chinese, "从本地备份恢复", "Restore local backup"),
            bt(chinese, "选择目录中的 ChromeXt ZIP 备份", "Choose a ChromeXt ZIP backup from the folder"),
            enabled = targetPackage != null,
        ) {
          runAction {
            localChoices = manager.localBackups()
          }
        }
        BackupRow(
            Icons.Rounded.FolderZip,
            bt(chinese, "选择其他备份恢复", "Restore another backup"),
            bt(chinese, "支持完整 ZIP 与旧版脚本 JSON", "Supports complete ZIP and legacy script JSON"),
            enabled = targetPackage != null,
        ) {
          archivePicker.launch(
              arrayOf("application/zip", "application/json", "text/json", "application/octet-stream"))
        }
        BackupRow(
            Icons.Rounded.FolderZip,
            bt(chinese, "本地保留份数", "Local retention"),
            retentionLabel(settings.localRetentionCount, chinese),
        ) {
          retentionTarget = "local"
        }
      }
    }
    item {
      BackupGroup("WebDAV") {
        BackupRow(
            Icons.Rounded.Cloud,
            bt(chinese, "WebDAV 配置", "WebDAV configuration"),
            if (settings.webDav.isConfigured) {
              "${settings.webDav.url} · ${settings.webDav.directory}"
            } else {
              bt(chinese, "请填写服务器、用户名和密码", "Enter server, username, and password")
            },
        ) {
          showWebDavEditor = true
        }
        BackupRow(
            Icons.Rounded.CheckCircle,
            bt(chinese, "测试连接", "Test connection"),
            if (settings.webDav.isConfigured) {
              bt(chinese, "验证登录并创建远端目录", "Verify credentials and create the remote folder")
            } else {
              bt(chinese, "配置完整后可用", "Available after configuration is complete")
            },
            enabled = settings.webDav.isConfigured,
        ) {
          save(settings)
          runAction(bt(chinese, "WebDAV 连接成功", "WebDAV connection succeeded")) {
            manager.testWebDav()
          }
        }
        BackupRow(
            Icons.Rounded.CloudDownload,
            bt(chinese, "从 WebDAV 恢复", "Restore from WebDAV"),
            if (settings.webDav.isConfigured) {
              bt(chinese, "列出云端备份并选择恢复", "List remote backups and choose one to restore")
            } else {
              bt(chinese, "配置完整后可用", "Available after configuration is complete")
            },
            enabled = settings.webDav.isConfigured && targetPackage != null,
        ) {
          save(settings)
          runAction { remoteChoices = manager.remoteBackups() }
        }
        BackupRow(
            Icons.Rounded.CloudQueue,
            bt(chinese, "云端保留份数", "Remote retention"),
            retentionLabel(settings.remoteRetentionCount, chinese),
        ) {
          retentionTarget = "remote"
        }
      }
    }
    item {
      BackupGroup(bt(chinese, "安全", "Security")) {
        BackupRow(
            Icons.Rounded.Key,
            bt(chinese, "备份加密密码", "Backup encryption password"),
            if (settings.encryptionPassword.isBlank()) {
              bt(chinese, "未加密", "Not encrypted")
            } else {
              bt(chinese, "已设置 AES-256 密码", "AES-256 password configured")
            },
        ) {
          showPasswordEditor = true
        }
        BackupSwitchRow(
            Icons.Rounded.Lock,
            bt(chinese, "包含 WebDAV 配置", "Include WebDAV configuration"),
            bt(chinese, "仅建议在已设置加密密码时开启", "Recommended only for encrypted backups"),
            settings.includeWebDavConfig,
        ) {
          save(settings.copy(includeWebDavConfig = it))
        }
      }
    }
  }

  if (busy) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(bt(chinese, "正在处理", "Working")) },
        text = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(26.dp))
            Text(bt(chinese, "请稍候…", "Please wait…"), Modifier.padding(start = 14.dp))
          }
        })
  }
  resultMessage?.let { message ->
    AlertDialog(
        onDismissRequest = { resultMessage = null },
        confirmButton = {
          TextButton(onClick = { resultMessage = null }) {
            Text(bt(chinese, "确定", "OK"))
          }
        },
        title = { Text(bt(chinese, "备份与恢复", "Backup and restore")) },
        text = { Text(message) })
  }
  if (showWebDavEditor) {
    WebDavEditor(
        initial = settings.webDav,
        chinese = chinese,
        onDismiss = { showWebDavEditor = false },
    ) {
      save(settings.copy(webDav = it))
      showWebDavEditor = false
    }
  }
  if (showPasswordEditor) {
    PasswordEditor(
        initial = settings.encryptionPassword,
        chinese = chinese,
        onDismiss = { showPasswordEditor = false },
    ) {
      save(settings.copy(encryptionPassword = it))
      showPasswordEditor = false
    }
  }
  if (showTargetChooser) {
    SimpleChoiceDialog(
        bt(chinese, "目标浏览器", "Target browser"),
        connected.map { target ->
          "${target.label}\n${target.packageName} · ${bt(chinese, "已连接", "Connected")}"
        },
        connected.indexOfFirst { it.packageName == targetPackage }.coerceAtLeast(0),
        { showTargetChooser = false },
    ) {
      targetPackage = connected[it].packageName
      showTargetChooser = false
    }
  }
  if (showBackupModeDialog) {
    AlertDialog(
        onDismissRequest = { showBackupModeDialog = false },
        title = { Text(bt(chinese, "选择备份目标", "Choose backup destination")) },
        confirmButton = {},
        text = {
          Column {
            listOf(
                    Triple(BackupMode.LOCAL_AND_REMOTE, Icons.Rounded.CloudSync, bt(chinese, "备份到本地 + 云端", "Back up to local + cloud")),
                    Triple(BackupMode.LOCAL, Icons.Rounded.Folder, bt(chinese, "仅备份到本地", "Back up locally only")),
                    Triple(BackupMode.REMOTE, Icons.Rounded.CloudUpload, bt(chinese, "仅备份到云端", "Back up to cloud only")),
                )
                .forEach { (mode, icon, label) ->
                  Row(
                      Modifier.fillMaxWidth()
                          .clickable {
                            showBackupModeDialog = false
                            requestBackup(mode)
                          }
                          .padding(vertical = 14.dp),
                      verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    Text(label, Modifier.padding(start = 16.dp))
                  }
                }
          }
        },
    )
  }
  retentionTarget?.let { target ->
    val values = BackupManager.RETENTION_VALUES
    val current =
        if (target == "local") settings.localRetentionCount else settings.remoteRetentionCount
    SimpleChoiceDialog(
        if (target == "local") bt(chinese, "本地保留份数", "Local retention")
        else bt(chinese, "云端保留份数", "Remote retention"),
        values.map { retentionLabel(it, chinese) },
        values.indexOf(current).coerceAtLeast(0),
        { retentionTarget = null },
    ) { index ->
      val value = values[index]
      save(
          if (target == "local") settings.copy(localRetentionCount = value)
          else settings.copy(remoteRetentionCount = value))
      retentionTarget = null
    }
  }
  localChoices?.let { choices ->
    BackupChoiceDialog(
        title = bt(chinese, "选择本地备份", "Choose local backup"),
        names = choices.map(LocalBackup::name),
        details = choices.map { backupDetail(it.modifiedAt, it.size, context, chinese) },
        chinese = chinese,
        onDismiss = { localChoices = null },
    ) { index ->
      val target = targetPackage ?: return@BackupChoiceDialog
      val choice = choices[index]
      localChoices = null
      runAction(bt(chinese, "恢复请求已发送到浏览器", "Restore sent to browser")) {
        manager.restore(choice, target)
      }
    }
  }
  remoteChoices?.let { choices ->
    BackupChoiceDialog(
        title = bt(chinese, "选择云端备份", "Choose remote backup"),
        names = choices.map(RemoteBackup::name),
        details = choices.map { backupDetail(it.modifiedAt, it.size, context, chinese) },
        chinese = chinese,
        onDismiss = { remoteChoices = null },
    ) { index ->
      val target = targetPackage ?: return@BackupChoiceDialog
      val choice = choices[index]
      remoteChoices = null
      runAction(bt(chinese, "恢复请求已发送到浏览器", "Restore sent to browser")) {
        manager.restore(choice, target)
      }
    }
  }
}

@Composable
private fun BackupGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
  Card(
      Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
          Text(title, Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          content()
        }
      }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackupRow(
    icon: ImageVector,
    title: String,
    detail: String,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
  val alpha = if (enabled) 1f else .45f
  val interaction =
      if (onLongClick == null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
      } else {
        Modifier.combinedClickable(
            enabled = enabled, onClick = onClick, onLongClick = onLongClick)
      }
  Row(
      Modifier.fillMaxWidth().then(interaction).padding(horizontal = 16.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically) {
        BackupIcon(icon, alpha)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
          Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
          Text(detail, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
      }
}

@Composable
private fun BackupSwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
  Row(
      Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically) {
        BackupIcon(icon)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
          Text(title)
          Text(detail, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
      }
}

@Composable
private fun BackupIcon(icon: ImageVector, alpha: Float = 1f) {
  Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha)) {
    Icon(icon, null, Modifier.padding(10.dp).size(21.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = alpha))
  }
}

@Composable
private fun WebDavEditor(
    initial: WebDavConfig,
    chinese: Boolean,
    onDismiss: () -> Unit,
    onSave: (WebDavConfig) -> Unit,
) {
  var url by remember { mutableStateOf(initial.url) }
  var user by remember { mutableStateOf(initial.username) }
  var password by remember { mutableStateOf(initial.password) }
  var directory by remember { mutableStateOf(initial.directory) }
  var device by remember { mutableStateOf(initial.deviceName) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(bt(chinese, "WebDAV 配置", "WebDAV configuration")) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
          OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("URL") }, singleLine = true)
          OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), label = { Text(bt(chinese, "用户名", "Username")) }, singleLine = true)
          OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(bt(chinese, "密码", "Password")) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
          OutlinedTextField(directory, { directory = it }, Modifier.fillMaxWidth(), label = { Text(bt(chinese, "远端目录", "Remote folder")) }, singleLine = true)
          OutlinedTextField(device, { device = it }, Modifier.fillMaxWidth(), label = { Text(bt(chinese, "设备名称", "Device name")) }, singleLine = true)
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(bt(chinese, "取消", "Cancel")) } },
      confirmButton = {
        Button(onClick = { onSave(WebDavConfig(url, user, password, directory, device)) }) {
          Text(bt(chinese, "保存", "Save"))
        }
      })
}

@Composable
private fun PasswordEditor(
    initial: String,
    chinese: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
  var value by remember { mutableStateOf(initial) }
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(bt(chinese, "备份加密密码", "Backup encryption password")) },
      text = {
        OutlinedTextField(
            value,
            { value = it },
            Modifier.fillMaxWidth(),
            label = { Text(bt(chinese, "留空表示不加密", "Leave blank for no encryption")) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true)
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(bt(chinese, "取消", "Cancel")) } },
      confirmButton = { Button(onClick = { onSave(value) }) { Text(bt(chinese, "保存", "Save")) } })
}

@Composable
private fun SimpleChoiceDialog(
    title: String,
    labels: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = {
        Column {
          labels.forEachIndexed { index, label ->
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = {
                  if (index == selected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                  else Spacer(Modifier.size(24.dp))
                },
                modifier = Modifier.clickable { onSelect(index) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
          }
        }
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = onDismiss) { Text("OK") } })
}

@Composable
private fun BackupChoiceDialog(
    title: String,
    names: List<String>,
    details: List<String>,
    chinese: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = {
        if (names.isEmpty()) {
          Text(bt(chinese, "没有找到备份", "No backups found"))
        } else {
          LazyColumn(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(names.indices.toList()) { index ->
              Surface(
                  Modifier.fillMaxWidth().clickable { onSelect(index) },
                  shape = RoundedCornerShape(16.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(14.dp)) {
                      Text(names[index], fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                      Text(details[index], Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = { TextButton(onClick = onDismiss) { Text(bt(chinese, "关闭", "Close")) } })
}

private fun displayPath(uri: String): String =
    runCatching { Uri.parse(uri).lastPathSegment?.replace(':', '/') ?: uri }.getOrDefault(uri)

private fun retentionLabel(value: Int, chinese: Boolean): String =
    if (value == 0) bt(chinese, "全部保留", "Keep all")
    else bt(chinese, "保留最近 $value 份", "Keep latest $value")

private fun backupDetail(
    modifiedAt: Long,
    size: Long,
    context: android.content.Context,
    chinese: Boolean,
): String {
  val time =
      if (modifiedAt > 0L) DateFormat.getDateTimeInstance().format(Date(modifiedAt))
      else bt(chinese, "时间未知", "Unknown time")
  return "$time · ${Formatter.formatFileSize(context, size)}"
}

private fun bt(chinese: Boolean, zh: String, en: String): String =
    UiLocalization.text(chinese, zh, en)
