package org.matrix.chromext.ui

import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.matrix.chromext.UiLocalization
import org.matrix.chromext.ui.common.BrowserTargetSelector
import org.matrix.chromext.ui.common.NavigationSettingItem
import org.matrix.chromext.ui.common.SettingGroup
import org.matrix.chromext.ui.common.SwitchSettingItem
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
  val allTargets = controller.browserTargets
  val connected = controller.connectedBrowserTargets()
  var targetPackage by remember { mutableStateOf<String?>(null) }
  var pendingConnectionTarget by remember { mutableStateOf<String?>(null) }
  var openBrowserTarget by remember { mutableStateOf<BrowserTarget?>(null) }
  var busy by remember { mutableStateOf(false) }
  var resultMessage by remember { mutableStateOf<String?>(null) }
  var showWebDavEditor by remember { mutableStateOf(false) }
  var showPasswordEditor by remember { mutableStateOf(false) }
  var showBackupModeDialog by remember { mutableStateOf(false) }
  var pendingBackupMode by remember { mutableStateOf<BackupMode?>(null) }
  var retentionTarget by remember { mutableStateOf<String?>(null) }
  var localChoices by remember { mutableStateOf<List<LocalBackup>?>(null) }
  var remoteChoices by remember { mutableStateOf<List<RemoteBackup>?>(null) }

  LaunchedEffect(connected) {
    pendingConnectionTarget
        ?.takeIf { pending -> pending in connected.map(BrowserTarget::packageName) }
        ?.let { connectedTarget ->
          targetPackage = connectedTarget
          pendingConnectionTarget = null
        }
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
      val target =
          allTargets.find {
            it.packageName ==
                (openBrowserTarget?.packageName
                    ?: pendingConnectionTarget
                    ?: allTargets.firstOrNull()?.packageName)
          }
      if (target != null) {
        openBrowserTarget = target
      } else {
        resultMessage =
            bt(
                chinese,
                "没有找到已安装且处于作用域内的支持浏览器",
                "No installed supported browser was found in scope")
      }
    } else if (mode.includesLocal && settings.localTreeUri.isNullOrBlank()) {
      pendingBackupMode = mode
      treePicker.launch(BackupManager.defaultDocumentsUri())
    } else {
      performBackup(mode)
    }
  }

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      BackupDashboardCard(
          localReady = settings.localTreeUri != null,
          cloudReady = settings.webDav.isConfigured,
          targetReady = targetPackage != null,
          targets = allTargets,
          selectedPackage =
              targetPackage
                  ?: openBrowserTarget?.packageName
                  ?: pendingConnectionTarget
                  ?: allTargets.firstOrNull()?.packageName,
          busy = busy,
          chinese = chinese,
          onSelectTarget = { target ->
            if (target.packageName in connected.map(BrowserTarget::packageName)) {
              targetPackage = target.packageName
            } else {
              openBrowserTarget = target
            }
          },
          onClick = { requestBackup(BackupMode.LOCAL_AND_REMOTE) },
          onLongClick = { showBackupModeDialog = true },
      )
    }
    item {
      val location = manager.localStorageLocation()
      val browserPath =
          targetPackage?.let { "${location.displayPath}/$it" } ?: location.displayPath
      BackupStoragePanel(
          path =
              if (location.configured) {
                bt(
                    chinese,
                    "$browserPath · ${if (location.isDefault) "默认目录" else "自定义目录"}",
                    "$browserPath · ${if (location.isDefault) "Default folder" else "Custom folder"}")
              } else {
                bt(
                    chinese,
                    "$browserPath · 默认建议（尚未授权）",
                    "$browserPath · Recommended default (not granted)")
              },
          retention = retentionLabel(settings.localRetentionCount, chinese),
          enabled = targetPackage != null,
          chinese = chinese,
          onChooseFolder = { treePicker.launch(BackupManager.defaultDocumentsUri()) },
          onCopyFolder = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("backup folder", browserPath))
            Toast.makeText(
                    context,
                    bt(chinese, "备份路径已复制", "Backup path copied"),
                    Toast.LENGTH_SHORT)
                .show()
          },
          onRestore = {
            targetPackage?.let { target ->
              runAction { localChoices = manager.localBackups(target) }
            }
          },
          onRestoreOther = {
            archivePicker.launch(
                arrayOf("application/zip", "application/json", "text/json", "application/octet-stream"))
          },
          onRetention = { retentionTarget = "local" },
      )
    }
    item {
      BackupCloudPanel(
          configured = settings.webDav.isConfigured,
          targetReady = targetPackage != null,
          preview =
              if (settings.webDav.isConfigured) {
                val directory =
                    listOf(settings.webDav.directory.trim('/'), targetPackage.orEmpty())
                        .filter(String::isNotBlank)
                        .joinToString("/")
                "${settings.webDav.url} · $directory"
              } else {
                bt(chinese, "请填写服务器、用户名和密码", "Enter server, username, and password")
              },
          retention = retentionLabel(settings.remoteRetentionCount, chinese),
          chinese = chinese,
          onConfigure = { showWebDavEditor = true },
          onTest = {
            save(settings)
            runAction(bt(chinese, "WebDAV 连接成功", "WebDAV connection succeeded")) {
              manager.testWebDav()
            }
          },
          onRestore = {
            save(settings)
            targetPackage?.let { target ->
              runAction { remoteChoices = manager.remoteBackups(target) }
            }
          },
          onRetention = { retentionTarget = "remote" },
      )
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
  openBrowserTarget?.let { target ->
    AlertDialog(
        onDismissRequest = { openBrowserTarget = null },
        icon = { Icon(Icons.Rounded.Code, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(bt(chinese, "浏览器尚未连接", "Browser not connected")) },
        text = {
          Text(
              bt(
                  chinese,
                  "请先打开 ${target.label} 建立连接，连接成功后会自动选中。",
                  "Open ${target.label} to connect. It will be selected automatically once connected."))
        },
        dismissButton = {
          TextButton(onClick = { openBrowserTarget = null }) {
            Text(bt(chinese, "取消", "Cancel"))
          }
        },
        confirmButton = {
          Button(
              onClick = {
                pendingConnectionTarget = target.packageName
                openBrowserTarget = null
                controller.openScriptManager(target)
              }) {
                Text(bt(chinese, "打开浏览器", "Open browser"))
              }
        })
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
  if (showBackupModeDialog) {
    AlertDialog(
        onDismissRequest = { showBackupModeDialog = false },
        icon = { Icon(Icons.Rounded.Backup, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(bt(chinese, "选择备份目标", "Choose backup destination")) },
        dismissButton = {
          TextButton(onClick = { showBackupModeDialog = false }) {
            Text(bt(chinese, "取消", "Cancel"))
          }
        },
        confirmButton = {},
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                bt(chinese, "选择本次备份写入的位置", "Choose where to save this backup"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                    Triple(BackupMode.LOCAL_AND_REMOTE, Icons.Rounded.CloudSync, bt(chinese, "备份到本地 + 云端", "Back up to local + cloud")),
                    Triple(BackupMode.LOCAL, Icons.Rounded.Folder, bt(chinese, "仅备份到本地", "Back up locally only")),
                    Triple(BackupMode.REMOTE, Icons.Rounded.CloudUpload, bt(chinese, "仅备份到云端", "Back up to cloud only")),
                )
                .forEach { (mode, icon, label) ->
                  val summary =
                      when (mode) {
                        BackupMode.LOCAL_AND_REMOTE -> bt(chinese, "同时保留设备与 WebDAV 副本", "Keep local and WebDAV copies")
                        BackupMode.LOCAL -> bt(chinese, "只写入已选择的本地目录", "Save only to the selected local folder")
                        BackupMode.REMOTE -> bt(chinese, "只上传到已配置的 WebDAV", "Upload only to the configured WebDAV")
                      }
                  Surface(
                      onClick = {
                        showBackupModeDialog = false
                        requestBackup(mode)
                      },
                      modifier = Modifier.fillMaxWidth(),
                      shape = RoundedCornerShape(18.dp),
                      color = MaterialTheme.colorScheme.surfaceContainer,
                  ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                      Surface(
                          shape = RoundedCornerShape(13.dp),
                          color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(
                                icon,
                                null,
                                Modifier.padding(10.dp).size(22.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                          }
                      Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                      }
                    }
                  }
                }
          }
        },
    )
  }
  retentionTarget?.let { target ->
    val current =
        if (target == "local") settings.localRetentionCount else settings.remoteRetentionCount
    RetentionChoiceDialog(
        title =
            if (target == "local") bt(chinese, "本地保留份数", "Local retention")
            else bt(chinese, "云端保留份数", "Remote retention"),
        selected = current,
        chinese = chinese,
        onDismiss = { retentionTarget = null },
    ) { value ->
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackupDashboardCard(
    localReady: Boolean,
    cloudReady: Boolean,
    targetReady: Boolean,
    targets: List<BrowserTarget>,
    selectedPackage: String?,
    busy: Boolean,
    chinese: Boolean,
    onSelectTarget: (BrowserTarget) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
  Card(
      modifier =
          Modifier.fillMaxWidth()
              .combinedClickable(
                  enabled = !busy, onClick = onClick, onLongClick = onLongClick),
      shape = RoundedCornerShape(28.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(Modifier.padding(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
          if (busy) CircularProgressIndicator(Modifier.size(27.dp), strokeWidth = 2.5.dp)
          else
              Icon(
                  Icons.Rounded.Backup,
                  null,
                  Modifier.size(29.dp),
                  tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(
              bt(chinese, "立即备份", "Back up now"),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold)
          Text(
              if (targetReady) {
                bt(chinese, "点击备份到本地和云端，长按选择目标", "Tap for local + cloud; long-press for destination")
              } else {
                bt(chinese, "点击打开浏览器并建立连接", "Tap to open the browser and connect")
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      BrowserTargetSelector(
          targets = targets,
          selectedPackage = selectedPackage,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          onSelect = onSelectTarget)
      Spacer(Modifier.height(18.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BackupReadyPill(
            Modifier.weight(1f),
            Icons.Rounded.Folder,
            bt(chinese, "本地", "Local"),
            localReady,
            chinese)
        BackupReadyPill(
            Modifier.weight(1f), Icons.Rounded.Cloud, "WebDAV", cloudReady, chinese)
      }
    }
  }
}

@Composable
private fun BackupReadyPill(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    ready: Boolean,
    chinese: Boolean,
) {
  Surface(
      modifier = modifier,
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
  ) {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(8.dp))
          Text(
              label,
              Modifier.weight(1f),
              style = MaterialTheme.typography.labelLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis)
          Icon(
              if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
              if (ready) bt(chinese, "已就绪", "Ready") else bt(chinese, "待设置", "Setup required"),
              Modifier.size(20.dp),
              tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
  }
}

@Composable
private fun BackupStoragePanel(
    path: String,
    retention: String,
    enabled: Boolean,
    chinese: Boolean,
    onChooseFolder: () -> Unit,
    onCopyFolder: () -> Unit,
    onRestore: () -> Unit,
    onRestoreOther: () -> Unit,
    onRetention: () -> Unit,
) {
  BackupPanel(bt(chinese, "本地备份", "Local backup"), Icons.Rounded.Storage) {
    @OptIn(ExperimentalFoundationApi::class)
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onChooseFolder, onLongClick = onCopyFolder)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(
                bt(chinese, "备份目录", "Backup folder"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium)
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis)
          }
        }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          BackupMiniAction(
              Modifier.weight(1f),
              Icons.Rounded.Restore,
              bt(chinese, "从本地恢复", "Restore local"),
              enabled,
              onRestore)
          BackupMiniAction(
              Modifier.weight(1f),
              Icons.Rounded.FolderZip,
              bt(chinese, "选择其他备份", "Other backup"),
              enabled,
              onRestoreOther)
        }
    BackupRetentionFooter(
        bt(chinese, "本地保留份数", "Local retention"), retention, onRetention)
  }
}

@Composable
private fun BackupCloudPanel(
    configured: Boolean,
    targetReady: Boolean,
    preview: String,
    retention: String,
    chinese: Boolean,
    onConfigure: () -> Unit,
    onTest: () -> Unit,
    onRestore: () -> Unit,
    onRetention: () -> Unit,
) {
  BackupPanel("WebDAV", Icons.Rounded.CloudQueue) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onConfigure).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Cloud, null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(
                bt(chinese, "WebDAV 配置", "WebDAV configuration"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium)
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis)
          }
        }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          BackupMiniAction(
              Modifier.weight(1f),
              Icons.Rounded.CloudDone,
              bt(chinese, "测试连接", "Test connection"),
              configured,
              onTest)
          BackupMiniAction(
              Modifier.weight(1f),
              Icons.Rounded.CloudDownload,
              bt(chinese, "从云端恢复", "Restore WebDAV"),
              configured && targetReady,
              onRestore)
        }
    BackupRetentionFooter(
        bt(chinese, "云端保留份数", "Remote retention"), retention, onRetention)
  }
}

@Composable
private fun BackupPanel(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
  Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Row(
        Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
          Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(10.dp))
          Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    content()
  }
}

@Composable
private fun BackupMiniAction(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
  Surface(
      modifier = modifier.clip(RoundedCornerShape(17.dp)).clickable(enabled = enabled, onClick = onClick),
      shape = RoundedCornerShape(17.dp),
      color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else .4f),
  ) {
    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.Start) {
      Icon(
          icon,
          null,
          Modifier.size(23.dp),
          tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 1f else .45f))
      Spacer(Modifier.height(10.dp))
      Text(
          title,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = if (enabled) 1f else .45f))
    }
  }
}

@Composable
private fun BackupRetentionFooter(label: String, value: String, onClick: () -> Unit) {
  Row(
      Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.History,
            null,
            Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(9.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest) {
              Text(
                  value,
                  Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary)
            }
      }
}

@Composable
private fun RetentionChoiceDialog(
    title: String,
    selected: Int,
    chinese: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
  AlertDialog(
      onDismissRequest = onDismiss,
      icon = { Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary) },
      title = { Text(title) },
      dismissButton = {
        TextButton(onClick = onDismiss) { Text(bt(chinese, "取消", "Cancel")) }
      },
      confirmButton = {},
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(
              bt(
                  chinese,
                  "成功备份后将自动清理超出数量的旧备份",
                  "Older backups are removed automatically after a successful backup"),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant)
          Row(
              Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupManager.RETENTION_VALUES.filter { it > 0 }.forEach { value ->
                  val isSelected = value == selected
                  Surface(
                      onClick = { onSelect(value) },
                      modifier = Modifier.weight(1f),
                      shape = RoundedCornerShape(16.dp),
                      color =
                          if (isSelected) MaterialTheme.colorScheme.primaryContainer
                          else MaterialTheme.colorScheme.surfaceContainer,
                      border =
                          BorderStroke(
                              1.dp,
                              if (isSelected) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.outlineVariant)) {
                        Column(
                            Modifier.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                              Text(
                                  value.toString(),
                                  style = MaterialTheme.typography.titleLarge,
                                  fontWeight = FontWeight.Bold,
                                  color =
                                      if (isSelected) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.onSurface)
                              Text(
                                  bt(chinese, "份", "backups"),
                                  style = MaterialTheme.typography.labelSmall,
                                  color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                      }
                }
              }
          val unlimitedSelected = selected == 0
          Surface(
              onClick = { onSelect(0) },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              color =
                  if (unlimitedSelected) MaterialTheme.colorScheme.primaryContainer
                  else MaterialTheme.colorScheme.surfaceContainer,
              border =
                  BorderStroke(
                      1.dp,
                      if (unlimitedSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant)) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                      Icon(
                          Icons.Rounded.AllInclusive,
                          null,
                          tint = MaterialTheme.colorScheme.primary)
                      Text(
                          bt(chinese, "全部保留", "Keep all"),
                          Modifier.padding(start = 12.dp).weight(1f),
                          style = MaterialTheme.typography.titleSmall)
                      if (unlimitedSelected) {
                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                      }
                    }
              }
        }
      })
}

@Composable
private fun BackupGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
  SettingGroup(title, content = content)
}

@Composable
private fun BackupRow(
    icon: ImageVector,
    title: String,
    detail: String,
    enabled: Boolean = true,
    helpMarkdown: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
  NavigationSettingItem(
      title = title,
      description = detail,
      icon = icon,
      enabled = enabled,
      helpMarkdown = helpMarkdown,
      onLongClick = onLongClick,
      onClick = onClick,
  )
}

@Composable
private fun BackupSwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
  SwitchSettingItem(
      title = title,
      description = detail,
      icon = icon,
      checked = checked,
      onCheckedChange = onChange,
  )
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
