package org.matrix.chromext.ui

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.appcompat.widget.AppCompatEditText
import org.matrix.chromext.UiLocalization
import org.matrix.chromext.R
import org.matrix.chromext.ui.common.BrowserTargetSelector
import org.matrix.chromext.ui.common.MarkdownHelpDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.bridge.BrowserBridgeService
import org.matrix.chromext.backup.ScriptTransferManager
import org.matrix.chromext.utils.Log

private data class NativeScript(
    val id: String,
    val name: String,
    val namespace: String,
    val version: String,
    val disabled: Boolean,
    val matchCount: Int,
    val grantCount: Int,
    val installUrl: String,
)

internal data class ScriptMetadataDraft(
    val matches: String = "",
    val includes: String = "",
    val excludes: String = "",
    val grants: List<String> = emptyList(),
) {
  val matchRules: List<String>
    get() = normalizedRuleLines(matches)

  val includeRules: List<String>
    get() = normalizedRuleLines(includes)

  val excludeRules: List<String>
    get() = normalizedRuleLines(excludes)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NativeScriptsScreen(controller: ChromeXtController) {
  controller.revision
  val chinese = controller.isChinese
  val scope = rememberCoroutineScope()
  val allTargets = controller.browserTargets
  val targets = controller.connectedBrowserTargets()
  var targetPackage by remember { mutableStateOf<String?>(null) }
  var showTargetPicker by remember { mutableStateOf(false) }
  var showManagerHelp by remember { mutableStateOf(false) }
  var openBrowserTarget by remember { mutableStateOf<BrowserTarget?>(null) }
  var pendingConnectionTarget by remember { mutableStateOf<String?>(null) }
  var scripts by remember { mutableStateOf<List<NativeScript>>(emptyList()) }
  var query by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var editingId by remember { mutableStateOf<String?>(null) }
  var editingName by remember { mutableStateOf("") }
  var source by remember { mutableStateOf("") }
  var editorMeta by remember { mutableStateOf("") }
  var editorDraft by remember { mutableStateOf(ScriptMetadataDraft()) }
  var editorDisabled by remember { mutableStateOf(false) }
  var editorLoading by remember { mutableStateOf(false) }
  var editorLoadingSource by remember { mutableStateOf(false) }
  var editorSourceVisible by remember { mutableStateOf(false) }
  var editorSourceLoaded by remember { mutableStateOf(false) }
  var deleteId by remember { mutableStateOf<String?>(null) }
  var showUrlInstaller by remember { mutableStateOf(false) }
  var installUrlValue by remember { mutableStateOf("") }
  var oversizedSource by remember { mutableStateOf<Pair<String, Int>?>(null) }
  var editorHighlight by remember { mutableStateOf(false) }
  var editorLineNumbers by remember { mutableStateOf(true) }
  var transferTargetPackage by remember { mutableStateOf<String?>(null) }
  val transferManager = remember { ScriptTransferManager(controller.context.applicationContext) }

  suspend fun request(action: String, payload: JSONObject = JSONObject()): JSONObject {
    val target = targetPackage ?: throw IllegalStateException("No connected browser")
    val response = BrowserBridgeService.Registry.request(target, action, payload.toString())
    val json = JSONObject(response)
    json.optString("error")
        .takeIf(String::isNotBlank)
        ?.let { throw IllegalStateException(it) }
    return json
  }

  suspend fun loadScripts() {
    val array = request("list").getJSONArray("scripts")
    scripts =
        (0 until array.length()).map { index ->
          val item = array.getJSONObject(index)
          NativeScript(
              item.getString("id"),
              item.getString("name"),
              item.optString("namespace"),
              item.optString("version"),
              item.optBoolean("disabled"),
              item.optInt("matchCount"),
              item.optInt("grantCount"),
              item.optString("installUrl"),
          )
        }
  }

  fun runAction(successMessage: String? = null, block: suspend () -> Unit) {
    busy = true
    scope.launch {
      try {
        block()
        successMessage?.let { Log.toast(controller.context, it) }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Throwable) {
        error =
            UiLocalization.error(
                chinese,
                failure.localizedMessage,
                "脚本操作失败",
                "Script operation failed",
            )
        Log.toast(controller.context, error.orEmpty())
      } finally {
        busy = false
      }
    }
  }

  fun openEditor(script: NativeScript) {
    val id = script.id
    editingId = id
    editingName = script.name
    editorDisabled = script.disabled
    editorLoading = true
    editorLoadingSource = false
    editorSourceVisible = false
    editorSourceLoaded = false
    editorHighlight = false
    editorLineNumbers = true
    source = ""
    editorMeta = ""
    editorDraft = ScriptMetadataDraft()
    scope.launch {
      try {
        val detail = request("details", JSONObject().put("id", id))
        val meta = detail.getString("meta")
        val draft = withContext(Dispatchers.Default) { parseMetadataDraft(meta) }
        if (editingId == id) {
          editingName = detail.optString("name", script.name)
          editorDisabled = detail.optBoolean("disabled", script.disabled)
          editorMeta = meta
          editorDraft = draft
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Throwable) {
        if (editingId == id) editingId = null
        error =
            UiLocalization.error(
                chinese,
                failure.localizedMessage,
                "脚本详情加载失败",
                "Could not load script details",
            )
      } finally {
        if (editingId == id) editorLoading = false
      }
    }
  }

  fun showSourceEditor() {
    val id = editingId ?: return
    if (editorSourceLoaded) {
      editorSourceVisible = true
      return
    }
    editorLoading = true
    editorLoadingSource = true
    scope.launch {
      try {
        val detail = request("read", JSONObject().put("id", id))
        val currentMeta =
            withContext(Dispatchers.Default) { replaceMetadataRules(editorMeta, editorDraft) }
        val loadedSource = detail.getString("source")
        if (loadedSource.length > MAX_NATIVE_EDITOR_SOURCE_LENGTH) {
          oversizedSource = id to loadedSource.length
          return@launch
        }
        source =
            withContext(Dispatchers.Default) {
              replaceMetadataBlock(loadedSource, currentMeta)
            }
        editorMeta = currentMeta
        editorSourceLoaded = true
        editorSourceVisible = true
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Throwable) {
        error =
            UiLocalization.error(
                chinese,
                failure.localizedMessage,
                "源码加载失败",
                "Could not load source",
            )
      } finally {
        editorLoading = false
        editorLoadingSource = false
      }
    }
  }

  fun showDetailsEditor() {
    if (!editorSourceLoaded) {
      editorSourceVisible = false
      return
    }
    editorLoading = true
    editorLoadingSource = false
    scope.launch {
      try {
        val meta = withContext(Dispatchers.Default) { extractMetadataBlock(source) }
        val draft = withContext(Dispatchers.Default) { parseMetadataDraft(meta) }
        editorMeta = meta
        editorDraft = draft
        editorSourceVisible = false
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Throwable) {
        error =
            UiLocalization.error(
                chinese,
                failure.localizedMessage,
                "元数据解析失败",
                "Could not parse metadata",
            )
      } finally {
        editorLoading = false
      }
    }
  }

  val importPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = transferTargetPackage
        transferTargetPackage = null
        if (uri != null && target != null) {
          runAction {
            val result = transferManager.import(target, uri)
            loadScripts()
            Log.toast(
                controller.context,
                ns(
                    chinese,
                    "已导入 ${result.imported} 个脚本，${result.failed} 个失败",
                    "Imported ${result.imported} scripts; ${result.failed} failed",
                ),
            )
          }
        }
      }
  val exportPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
          uri: Uri? ->
        val target = transferTargetPackage
        transferTargetPackage = null
        if (uri != null && target != null) {
          runAction {
            val file = transferManager.export(target, uri)
            Log.toast(
                controller.context,
                ns(
                    chinese,
                    "已导出 ${file.scriptCount} 个脚本到 ${file.name}",
                    "Exported ${file.scriptCount} scripts to ${file.name}",
                ),
            )
          }
        }
      }

  fun requestImport() {
    val target = targetPackage ?: return
    transferTargetPackage = target
    importPicker.launch(
        arrayOf("application/json", "text/javascript", "application/javascript", "text/plain"))
  }

  fun requestExport() {
    val target = targetPackage ?: return
    val location = transferManager.storageLocation()
    if (!location.configured) {
      transferTargetPackage = target
      exportPicker.launch(transferManager.newExportName())
      return
    }
    runAction {
      val file = transferManager.export(target)
      val path = "${location.displayPath}/${file.relativePath}"
      Log.toast(
          controller.context,
          ns(
              chinese,
              "已导出 ${file.scriptCount} 个脚本到 $path（${if (location.isDefault) "默认目录" else "自定义目录"}）",
              "Exported ${file.scriptCount} scripts to $path (${if (location.isDefault) "default folder" else "custom folder"})",
          ),
      )
    }
  }

  LaunchedEffect(targets) {
    pendingConnectionTarget
        ?.takeIf { pending -> pending in targets.map(BrowserTarget::packageName) }
        ?.let { connectedTarget ->
          targetPackage = connectedTarget
          pendingConnectionTarget = null
        }
    if (targetPackage !in targets.map(BrowserTarget::packageName)) {
      targetPackage = targets.firstOrNull()?.packageName
    }
  }
  LaunchedEffect(targetPackage) {
    if (targetPackage != null) {
      try {
        loadScripts()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Throwable) {
        error =
            UiLocalization.error(
                chinese,
                failure.localizedMessage,
                "脚本加载失败",
                "Could not load scripts",
            )
      }
    } else {
      scripts = emptyList()
    }
  }

  if (editingId != null) {
    BackHandler { editingId = null }
    ScriptEditor(
        chinese = chinese,
        id = editingId.orEmpty(),
        name = editingName,
        source = source,
        draft = editorDraft,
        disabled = editorDisabled,
        loading = editorLoading,
        loadingSource = editorLoadingSource,
        sourceVisible = editorSourceVisible,
        busy = busy,
        canReinstall = scripts.find { it.id == editingId }?.installUrl?.isNotBlank() == true,
        canDelete = editingId != NEW_SCRIPT_ID,
        syntaxHighlight = editorHighlight,
        showLineNumbers = editorLineNumbers,
        onSourceChange = { source = it },
        onDraftChange = { editorDraft = it },
        onSyntaxHighlightChange = { editorHighlight = it },
        onShowLineNumbersChange = { editorLineNumbers = it },
        onBack = { editingId = null },
        onShowDetails = ::showDetailsEditor,
        onShowSource = ::showSourceEditor,
        onEnabledChange = { enabled ->
          val id = editingId ?: return@ScriptEditor
          if (id == NEW_SCRIPT_ID) return@ScriptEditor
          editorDisabled = !enabled
          runAction(ns(chinese, if (enabled) "脚本已启用" else "脚本已停用", if (enabled) "Script enabled" else "Script disabled")) {
            request(
                "toggle",
                JSONObject().apply {
                  put("id", id)
                  put("disabled", !enabled)
                },
            )
            loadScripts()
          }
        },
        onSave = {
          val currentId = editingId.orEmpty()
          val previousId = currentId.takeUnless { it == NEW_SCRIPT_ID }.orEmpty()
          runAction(ns(chinese, "脚本已保存", "Script saved")) {
            val saved = if (!editorSourceVisible && currentId != NEW_SCRIPT_ID) {
              val meta =
                  withContext(Dispatchers.Default) {
                    replaceMetadataRules(editorMeta, editorDraft)
                  }
              editorMeta = meta
              request(
                  "saveMeta",
                  JSONObject().apply {
                    put("id", currentId)
                    put("meta", meta)
                  },
              )
            } else {
              request(
                  "save",
                  JSONObject().apply {
                    put("previousId", previousId)
                    put("source", source)
                  })
            }
            editingId = saved.getString("id")
            editingName = saved.optString("name")
            loadScripts()
          }
        },
        onDelete = {
          editingId?.takeUnless { it == NEW_SCRIPT_ID }?.let { deleteId = it }
        },
        onReinstall = {
          val id = editingId ?: return@ScriptEditor
          runAction(ns(chinese, "脚本重新安装成功", "Script reinstalled")) {
            request("reinstall", JSONObject().put("ids", JSONArray(listOf(id))))
            val detail = request("details", JSONObject().put("id", id))
            val meta = detail.getString("meta")
            editorMeta = meta
            editorDraft = withContext(Dispatchers.Default) { parseMetadataDraft(meta) }
            editorDisabled = detail.optBoolean("disabled")
            editorSourceLoaded = false
            editorSourceVisible = false
            source = ""
            loadScripts()
          }
        },
    )
  } else {
    val filtered =
        scripts.filter {
          query.isBlank() ||
              it.name.contains(query, ignoreCase = true) ||
              it.namespace.contains(query, ignoreCase = true) ||
              it.id.contains(query, ignoreCase = true)
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 10.dp, 18.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Column(Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Code, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
              Text(
                  stringResource(R.string.native_script_manager),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold)
              Text(
                  targets.find { it.packageName == targetPackage }?.label
                      ?: ns(chinese, "等待浏览器连接", "Waiting for browser"),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showManagerHelp = true }) {
              Icon(
                  Icons.AutoMirrored.Rounded.MenuBook,
                  stringResource(R.string.more_information))
            }
            IconButton(
                onClick = {
                  controller.openScriptManager(allTargets.find { it.packageName == targetPackage })
                },
                enabled = targetPackage != null,
            ) {
              Icon(
                  Icons.AutoMirrored.Rounded.OpenInNew,
                  ns(chinese, "打开浏览器管理页", "Open browser manager"))
            }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else
                IconButton(onClick = { runAction { loadScripts() } }) {
                  Icon(Icons.Rounded.Refresh, ns(chinese, "刷新", "Refresh"))
                }
          }
          BrowserTargetSelector(
              targets = allTargets,
              selectedPackage = targetPackage ?: allTargets.firstOrNull()?.packageName,
              onSelect = { target ->
                if (target.packageName in targets.map(BrowserTarget::packageName)) {
                  targetPackage = target.packageName
                  editingId = null
                  scripts = emptyList()
                } else {
                  openBrowserTarget = target
                }
              })
          Row(
              Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                      modifier = Modifier.height(38.dp),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                      enabled = targetPackage != null,
                      onClick = {
                        editorHighlight = false
                        editorLineNumbers = true
                        editingId = NEW_SCRIPT_ID
                        editingName = ns(chinese, "新脚本", "New script")
                        source = NEW_SCRIPT_TEMPLATE
                        editorMeta = extractMetadataBlock(NEW_SCRIPT_TEMPLATE)
                        editorDraft = parseMetadataDraft(editorMeta)
                        editorDisabled = false
                        editorLoading = false
                        editorLoadingSource = false
                        editorSourceLoaded = true
                        editorSourceVisible = true
                      }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(ns(chinese, "新建", "New"))
                      }
                    FilledTonalButton(
                      modifier = Modifier.height(38.dp),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                      enabled = targetPackage != null && !busy,
                      onClick = { showUrlInstaller = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(ns(chinese, "URL 安装", "URL install"))
                      }
                    FilledTonalButton(
                      modifier = Modifier.height(38.dp),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                      enabled = targetPackage != null && !busy,
                      onClick = ::requestImport,
                  ) {
                    Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(ns(chinese, "导入", "Import"))
                  }
                    FilledTonalButton(
                      modifier = Modifier.height(38.dp),
                      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                      enabled = targetPackage != null && scripts.isNotEmpty() && !busy,
                      onClick = ::requestExport,
                  ) {
                    Icon(Icons.Rounded.FileDownload, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(ns(chinese, "导出", "Export"))
                  }
            }
          Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.Info,
                null,
                Modifier.padding(top = 2.dp).size(17.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                ns(
                    chinese,
                    "在此编辑前需先打开对应浏览器建立通信，也可以直接在浏览器管理页中操作",
                    "Open the target browser first to connect, or manage scripts in the browser page"),
                Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      if (targets.isEmpty()) {
        item {
          Card(
              shape = RoundedCornerShape(24.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                      Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(38.dp))
                      Text(
                          ns(chinese, "浏览器尚未连接", "Browser not connected"),
                          Modifier.padding(top = 10.dp),
                          fontWeight = FontWeight.Bold)
                      Text(
                          ns(
                              chinese,
                          "先打开作用域内的浏览器 ChromeXt 会自动建立安全连接",
                          "Open a scoped browser first ChromeXt will connect automatically"),
                          Modifier.padding(top = 5.dp))
                      Button(
                          onClick = { controller.openScriptManager() },
                          modifier = Modifier.padding(top = 14.dp)) {
                            Text(ns(chinese, "打开浏览器", "Open browser"))
                          }
                    }
              }
        }
      } else {
        item {
          OutlinedTextField(
              query,
              { query = it },
              Modifier.fillMaxWidth(),
              leadingIcon = { Icon(Icons.Rounded.Search, null) },
              label = { Text(ns(chinese, "搜索脚本", "Search scripts")) },
              singleLine = true,
              shape = RoundedCornerShape(20.dp))
        }
        items(filtered, key = NativeScript::id) { script ->
          NativeScriptCard(
              script = script,
              chinese = chinese,
              onOpen = { openEditor(script) },
              onLongPress = {
                val installUrl = script.installUrl.trim()
                if (installUrl.isBlank()) {
                  Log.toast(
                      controller.context,
                      ns(chinese, "该脚本没有可复制的安装链接", "This script has no install link"))
                } else {
                  (controller.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                      .setPrimaryClip(
                          ClipData.newPlainText("ChromeXt install link: ${script.name}", installUrl))
                  Log.toast(
                      controller.context,
                      ns(chinese, "已复制脚本安装链接", "Script install link copied"))
                }
              },
              onToggle = { disabled ->
                runAction(ns(chinese, if (disabled) "脚本已停用" else "脚本已启用", if (disabled) "Script disabled" else "Script enabled")) {
                  request(
                      "toggle",
                      JSONObject().apply {
                        put("id", script.id)
                        put("disabled", disabled)
                      })
                  loadScripts()
                }
              })
        }
        if (filtered.isEmpty()) {
          item {
            Text(
                if (scripts.isEmpty()) ns(chinese, "还没有安装用户脚本", "No userscripts installed")
                else ns(chinese, "没有匹配的脚本", "No matching scripts"),
                Modifier.fillMaxWidth().padding(30.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }

  error?.let { message ->
    AlertDialog(
        onDismissRequest = { error = null },
        title = { Text(ns(chinese, "脚本管理", "Script manager")) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
  }
  deleteId?.let { id ->
    AlertDialog(
        onDismissRequest = { deleteId = null },
        title = { Text(ns(chinese, "删除脚本？", "Delete script?")) },
        text = { Text(ns(chinese, "此操作无法撤销。", "This action cannot be undone.")) },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text(ns(chinese, "取消", "Cancel")) } },
        confirmButton = {
          Button(
              onClick = {
                deleteId = null
                runAction(ns(chinese, "脚本已删除", "Script deleted")) {
                  request("delete", JSONObject().put("ids", JSONArray(listOf(id))))
                  editingId = null
                  loadScripts()
                }
              }) {
                Text(ns(chinese, "删除", "Delete"))
              }
        })
  }
  if (showUrlInstaller) {
    AlertDialog(
        onDismissRequest = {
          if (!busy) showUrlInstaller = false
        },
        icon = { Icon(Icons.Rounded.Code, null) },
        title = { Text(ns(chinese, "从 URL 安装脚本", "Install script from URL")) },
        text = {
          Column {
            Text(
                ns(
                    chinese,
                    "脚本将下载并安装到当前选择的浏览器。",
                    "The script will be downloaded and installed in the selected browser."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = installUrlValue,
                onValueChange = { installUrlValue = it },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                label = { Text(ns(chinese, "用户脚本地址", "Userscript URL")) },
                placeholder = { Text("https://example.com/script.user.js") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
          }
        },
        dismissButton = {
          TextButton(onClick = { showUrlInstaller = false }, enabled = !busy) {
            Text(ns(chinese, "取消", "Cancel"))
          }
        },
        confirmButton = {
          Button(
              enabled =
                  !busy &&
                      (installUrlValue.trim().startsWith("https://") ||
                          installUrlValue.trim().startsWith("http://")),
              onClick = {
                val url = installUrlValue.trim()
                showUrlInstaller = false
                runAction(ns(chinese, "脚本安装成功", "Script installed")) {
                  request("installUrl", JSONObject().put("url", url))
                  installUrlValue = ""
                  loadScripts()
                }
              }) {
            Text(ns(chinese, "安装", "Install"))
          }
        },
    )
  }
  if (showManagerHelp) {
    MarkdownHelpDialog(
        title = stringResource(R.string.native_script_manager),
        markdown = stringResource(R.string.help_browser_connection),
        onDismiss = { showManagerHelp = false },
    )
  }
  oversizedSource?.let { (id, length) ->
    AlertDialog(
        onDismissRequest = { oversizedSource = null },
        icon = { Icon(Icons.Rounded.ErrorOutline, null) },
        title = { Text(ns(chinese, "源码过大", "Source is too large")) },
        text = {
          Text(
              ns(
                  chinese,
                  "当前源码约 ${length / 1024} KB。为避免原生编辑器无响应，请前往网页端编辑。",
                  "This source is about ${length / 1024} KB. Edit it in the browser page to keep the native editor responsive."))
        },
        dismissButton = {
          TextButton(onClick = { oversizedSource = null }) {
            Text(ns(chinese, "取消", "Cancel"))
          }
        },
        confirmButton = {
          Button(
              onClick = {
                oversizedSource = null
                controller.openScriptManager(
                    allTargets.find { it.packageName == targetPackage },
                    sourceScriptId = id,
                )
              }) {
            Text(ns(chinese, "前往网页端编辑", "Edit in browser"))
          }
        },
    )
  }
  if (showTargetPicker) {
    val connectedPackages = targets.map { it.packageName }.toSet()
    AlertDialog(
        onDismissRequest = { showTargetPicker = false },
        icon = { Icon(Icons.Rounded.Code, null) },
        title = { Text(ns(chinese, "切换脚本数据库", "Switch script database")) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            allTargets.forEach { target ->
              val connected = target.packageName in connectedPackages
              val selected = target.packageName == targetPackage
              Surface(
                  modifier =
                      Modifier.fillMaxWidth().clickable {
                          if (!connected) {
                            showTargetPicker = false
                            openBrowserTarget = target
                          } else if (selected) {
                            showTargetPicker = false
                            runAction { loadScripts() }
                          } else {
                            targetPackage = target.packageName
                            editingId = null
                            scripts = emptyList()
                            showTargetPicker = false
                          }
                      },
                  shape = RoundedCornerShape(18.dp),
                  color =
                      if (selected) MaterialTheme.colorScheme.secondaryContainer
                      else MaterialTheme.colorScheme.surfaceContainer,
              ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(
                      if (connected) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                      null,
                      tint =
                          if (connected) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(target.label, fontWeight = FontWeight.Medium)
                    Text(
                        if (connected) {
                          ns(chinese, "已连接 · 点击切换", "Connected · tap to switch")
                        } else {
                          ns(chinese, "未连接 · 请先打开浏览器", "Not connected · open the browser first")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  if (selected) Text("●", color = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        },
        confirmButton = {},
        dismissButton = {
          TextButton(onClick = { showTargetPicker = false }) {
            Text(ns(chinese, "取消", "Cancel"))
          }
        },
    )
  }
  openBrowserTarget?.let { target ->
    AlertDialog(
        onDismissRequest = { openBrowserTarget = null },
        icon = { Icon(Icons.Rounded.ErrorOutline, null) },
        title = { Text(ns(chinese, "浏览器尚未连接", "Browser not connected")) },
        text = {
          Text(
              ns(
                  chinese,
                  "${target.label} 尚未打开，ChromeXt 无法读取它的脚本数据库。是否打开该浏览器并进入脚本管理页？",
                  "${target.label} is not open, so ChromeXt cannot read its script database. Open it and go to the script manager?"))
        },
        dismissButton = {
          TextButton(onClick = { openBrowserTarget = null }) {
            Text(ns(chinese, "取消", "Cancel"))
          }
        },
        confirmButton = {
          Button(
              onClick = {
                pendingConnectionTarget = target.packageName
                openBrowserTarget = null
                controller.openScriptManager(target)
              }) {
            Text(ns(chinese, "打开浏览器", "Open browser"))
          }
        },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NativeScriptCard(
    script: NativeScript,
    chinese: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
  Card(
      modifier =
          Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onLongPress),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
              Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Rounded.Code, null, Modifier.padding(11.dp).size(22.dp))
              }
              Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(script.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail =
                    buildList {
                          script.version.takeIf(String::isNotBlank)?.let { add("v$it") }
                          script.namespace.takeIf(String::isNotBlank)?.let(::add)
                          add(ns(chinese, "${script.matchCount} 条匹配", "${script.matchCount} matches"))
                        }
                        .joinToString(" · ")
                Text(detail, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              Switch(checked = !script.disabled, onCheckedChange = { onToggle(!it) })
            }
      }
}

@Composable
private fun CompactEditorAction(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
  TextButton(
      onClick = onClick,
      enabled = enabled,
      modifier = Modifier.height(36.dp),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
      colors =
          androidx.compose.material3.ButtonDefaults.textButtonColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
              contentColor = MaterialTheme.colorScheme.primary,
              disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
              disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
          ),
      shape = RoundedCornerShape(18.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
  }
}

@Composable
private fun RowScope.EditorToolToggle(
    label: String,
    active: Boolean,
    momentary: Boolean = false,
    onClick: () -> Unit,
) {
  Surface(
      onClick = onClick,
      modifier = Modifier.weight(1f).height(36.dp),
      shape = RoundedCornerShape(18.dp),
      color =
          if (active) MaterialTheme.colorScheme.primaryContainer
          else MaterialTheme.colorScheme.surfaceContainerHighest,
      border =
          BorderStroke(
              1.dp,
              if (active) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          Text(
              label,
              Modifier.weight(1f),
              style = MaterialTheme.typography.labelMedium,
              color =
                  if (active || momentary) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis)
          if (momentary) {
            Icon(
                Icons.AutoMirrored.Rounded.FormatAlignLeft,
                null,
                Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary)
          } else {
            EditorToggleTrack(active)
          }
        }
  }
}

@Composable
private fun EditorToggleTrack(active: Boolean) {
  val offset by animateDpAsState(if (active) 13.dp else 1.dp, label = "editorToggle")
  Box(
      Modifier.width(30.dp)
          .height(18.dp)
          .clip(CircleShape)
          .background(
              if (active) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceVariant)) {
        Box(
            Modifier.offset(x = offset, y = 1.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.outline))
      }
}

@Composable
private fun ChromeXtEditorLoading(chinese: Boolean, loadingSource: Boolean) {
  Box(Modifier.fillMaxWidth().fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(68.dp), strokeWidth = 4.dp)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
              "</>",
              Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
      Text(
          if (loadingSource) ns(chinese, "正在异步装载源码", "Loading source in background")
          else ns(chinese, "正在读取脚本详情", "Loading script details"),
          Modifier.padding(top = 18.dp),
          fontWeight = FontWeight.SemiBold,
      )
      Text(
          ns(chinese, "大脚本不会阻塞页面切换", "Large scripts will not block page navigation"),
          Modifier.padding(top = 5.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ScriptDetailsEditor(
    chinese: Boolean,
    draft: ScriptMetadataDraft,
    onDraftChange: (ScriptMetadataDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
  LazyColumn(
      modifier,
      contentPadding = PaddingValues(bottom = 18.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item {
      MetadataRuleCard(
          title = ns(chinese, "匹配规则", "Match rules"),
          emptyHint = "https://example.com/*",
          value = draft.matches,
          onValueChange = { onDraftChange(draft.copy(matches = it)) },
      )
    }
    item {
      MetadataRuleCard(
          title = ns(chinese, "包含规则", "Include rules"),
          emptyHint = "*://*/*",
          value = draft.includes,
          onValueChange = { onDraftChange(draft.copy(includes = it)) },
      )
    }
    item {
      MetadataRuleCard(
          title = ns(chinese, "排除规则", "Exclude rules"),
          emptyHint = ns(chinese, "每行一条 @exclude", "One @exclude per line"),
          value = draft.excludes,
          onValueChange = { onDraftChange(draft.copy(excludes = it)) },
      )
    }
    item {
      Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
      ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp)) {
          Text(
              ns(chinese, "脚本权限", "Script grants"),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          MetadataPreviewRow(
              values = draft.grants,
              emptyText = ns(chinese, "没有 @grant", "No @grant"),
              modifier = Modifier.padding(top = 8.dp),
              showAll = true,
          )
        }
      }
    }
  }
}

@Composable
private fun MetadataRuleCard(
    title: String,
    emptyHint: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
  val rules = remember(value) { normalizedRuleLines(value) }
  Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp)) {
      Text(
          title,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      MetadataPreviewRow(
          values = rules,
          emptyText = emptyHint,
          modifier = Modifier.padding(top = 8.dp),
      )
      OutlinedTextField(
          value = value,
          onValueChange = onValueChange,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          placeholder = { Text(emptyHint) },
          minLines = 1,
          maxLines = 4,
          textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
          shape = RoundedCornerShape(14.dp),
      )
    }
  }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MetadataPreviewRow(
    values: List<String>,
    emptyText: String,
    modifier: Modifier = Modifier,
    showAll: Boolean = false,
) {
  if (showAll) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (values.isEmpty()) {
        Text(
            emptyText,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 5.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        values.forEach { value ->
          Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHighest,
          ) {
            Text(
                value,
                Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            )
          }
        }
      }
    }
    return
  }
  Row(
      modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    if (values.isEmpty()) {
      Text(
          emptyText,
          modifier = Modifier.padding(horizontal = 2.dp, vertical = 5.dp),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      values.take(2).forEach { value ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
          Text(
              value,
              Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
              maxLines = 1,
          )
        }
      }
      if (values.size > 2) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
              "+${values.size - 2}",
              Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
          )
        }
      }
    }
  }
}

@Composable
private fun ScriptEditor(
    chinese: Boolean,
    id: String,
    name: String,
    source: String,
    draft: ScriptMetadataDraft,
    disabled: Boolean,
    loading: Boolean,
    loadingSource: Boolean,
    sourceVisible: Boolean,
    busy: Boolean,
    canReinstall: Boolean,
    canDelete: Boolean,
    syntaxHighlight: Boolean,
    showLineNumbers: Boolean,
    onSourceChange: (String) -> Unit,
    onDraftChange: (ScriptMetadataDraft) -> Unit,
    onSyntaxHighlightChange: (Boolean) -> Unit,
    onShowLineNumbersChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShowDetails: () -> Unit,
    onShowSource: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onReinstall: () -> Unit,
) {
  val verticalScroll = rememberScrollState()
  val toolbarHorizontalScroll = rememberScrollState()
  val codeHorizontalScroll = rememberScrollState()
  val codeStyle =
      MaterialTheme.typography.bodySmall.copy(
          color = MaterialTheme.colorScheme.onSurface,
          fontFamily = FontFamily.Monospace,
      )
  val keywordColor = MaterialTheme.colorScheme.primary
  val literalColor = MaterialTheme.colorScheme.tertiary
  val numberColor = MaterialTheme.colorScheme.error
  val commentColor = MaterialTheme.colorScheme.onSurfaceVariant
  val highlighter =
      remember(
          keywordColor,
          literalColor,
          numberColor,
          commentColor,
      ) {
        JavaScriptHighlightTransformation(
            keyword = keywordColor,
            literal = literalColor,
            number = numberColor,
            comment = commentColor,
        )
      }
  val lineCount = remember(source) { source.lineSequence().count().coerceAtLeast(1) }
  val usePlatformEditor = shouldUsePlatformEditor(source.length, lineCount)
  val highlightActive =
      syntaxHighlight && !usePlatformEditor && source.length <= MAX_HIGHLIGHT_SOURCE_LENGTH
  val contentHeight = if (usePlatformEditor) 400.dp else editorContentHeight(lineCount)
  var riskAction by remember { mutableStateOf<EditorRiskAction?>(null) }

  fun perform(action: EditorRiskAction) {
    when (action) {
      EditorRiskAction.Highlight -> onSyntaxHighlightChange(true)
      EditorRiskAction.LineNumbers -> onShowLineNumbersChange(true)
      EditorRiskAction.Format -> onSourceChange(formatJavaScript(source))
    }
  }

  fun request(action: EditorRiskAction) {
    when (action) {
      EditorRiskAction.Highlight -> {
        if (syntaxHighlight) onSyntaxHighlightChange(false)
        else if (usePlatformEditor || source.length > EDITOR_RISK_WARNING_LENGTH) riskAction = action
        else perform(action)
      }
      EditorRiskAction.LineNumbers -> {
        if (showLineNumbers) onShowLineNumbersChange(false)
        else if (source.length > EDITOR_RISK_WARNING_LENGTH) riskAction = action
        else perform(action)
      }
      EditorRiskAction.Format -> {
        if (source.length > EDITOR_RISK_WARNING_LENGTH) riskAction = action
        else perform(action)
      }
    }
  }

  Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
      Row(
          Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Rounded.ArrowBack, ns(chinese, "返回脚本列表", "Back to scripts"))
        }
        Column(Modifier.weight(1f)) {
          Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
              if (id == NEW_SCRIPT_ID) ns(chinese, "新脚本", "New script") else id,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
          )
        }
        Switch(
            checked = !disabled,
            enabled = id != NEW_SCRIPT_ID && !busy && !loading,
            onCheckedChange = onEnabledChange,
        )
        if (busy) CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(20.dp), strokeWidth = 2.dp)
        IconButton(onClick = onSave, enabled = !busy && !loading) {
          Icon(Icons.Rounded.Save, ns(chinese, "保存", "Save"))
        }
      }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
      Row(
          Modifier.fillMaxWidth().horizontalScroll(toolbarHorizontalScroll).padding(horizontal = 6.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        CompactEditorAction("${draft.matchRules.size + draft.includeRules.size} ${ns(chinese, "匹配", "matches")}", enabled = false) {}
        CompactEditorAction("${draft.grants.size} ${ns(chinese, "权限", "grants")}", enabled = false) {}
        CompactEditorAction(
            if (sourceVisible) ns(chinese, "详情", "Details") else ns(chinese, "源码", "Source"),
            enabled = !loading,
            onClick = if (sourceVisible) onShowDetails else onShowSource,
        )
        CompactEditorAction(
            ns(chinese, "重新安装", "Reinstall"),
            enabled = canReinstall && !busy && !loading,
            onClick = onReinstall,
        )
        FilledTonalButton(
            onClick = onDelete,
            enabled = canDelete && !busy && !loading,
            modifier = Modifier.height(36.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            colors =
                androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
        ) {
          Text(ns(chinese, "删除", "Delete"), style = MaterialTheme.typography.labelMedium)
        }
      }
    }

    when {
      loading -> ChromeXtEditorLoading(chinese, loadingSource)
      !sourceVisible ->
          ScriptDetailsEditor(
              chinese = chinese,
              draft = draft,
              onDraftChange = onDraftChange,
              modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
          )
      else -> {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
          Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              EditorToolToggle(
                  label = ns(chinese, "语法高亮", "Highlight"),
                  active = syntaxHighlight,
              ) {
                request(EditorRiskAction.Highlight)
              }
              EditorToolToggle(
                  label = ns(chinese, "行号", "Lines"),
                  active = showLineNumbers,
              ) {
                request(EditorRiskAction.LineNumbers)
              }
              EditorToolToggle(
                  label = ns(chinese, "格式化", "Format"),
                  active = false,
                  momentary = true,
              ) {
                request(EditorRiskAction.Format)
              }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (syntaxHighlight && !highlightActive) {
              Text(
                  ns(
                      chinese,
                      "脚本过大，原生语法高亮不可用，以避免界面无响应。",
                      "Native highlighting is unavailable for this large script to keep the editor responsive."),
                  Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error,
              )
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            if (usePlatformEditor) {
              val textColor = MaterialTheme.colorScheme.onSurface
              val gutterColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
              val dividerColor = MaterialTheme.colorScheme.outlineVariant
              AndroidView(
                  factory = { context -> LineNumberEditText(context) },
                  update = { editor ->
                    editor.onSourceChange = onSourceChange
                    editor.showLineNumbers = showLineNumbers
                    editor.updateColors(textColor.toArgb(), gutterColor.toArgb(), dividerColor.toArgb())
                    editor.updateSource(source)
                  },
                  modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp),
              )
            } else {
              Row(
                  Modifier.fillMaxWidth().weight(1f).verticalScroll(verticalScroll).padding(vertical = 14.dp),
                  verticalAlignment = Alignment.Top,
              ) {
                if (showLineNumbers) {
                  Text(
                      text = lineNumberText(lineCount),
                      modifier = Modifier.padding(start = 12.dp, end = 10.dp),
                      style = codeStyle,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                      textAlign = androidx.compose.ui.text.style.TextAlign.End,
                  )
                  Surface(
                      modifier = Modifier.width(1.dp).height(contentHeight),
                      color = MaterialTheme.colorScheme.outlineVariant,
                  ) {}
                }
                BasicTextField(
                    value = source,
                    onValueChange = onSourceChange,
                    modifier =
                        Modifier.weight(1f)
                            .padding(horizontal = 12.dp)
                            .horizontalScroll(codeHorizontalScroll)
                            .height(contentHeight),
                    textStyle = codeStyle,
                    cursorBrush = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)),
                    visualTransformation = if (highlightActive) highlighter else VisualTransformation.None,
                )
              }
            }
          }
        }
      }
    }
  }

  riskAction?.let { action ->
    val highlightBlocked =
        action == EditorRiskAction.Highlight &&
            (usePlatformEditor || source.length > MAX_HIGHLIGHT_SOURCE_LENGTH)
    AlertDialog(
        onDismissRequest = { riskAction = null },
        icon = { Icon(Icons.Rounded.ErrorOutline, null) },
        title = { Text(ns(chinese, "大型脚本性能提示", "Large-script performance warning")) },
        text = {
          Text(
              if (highlightBlocked) {
                ns(
                    chinese,
                    "当前脚本约 ${source.length / 1024} KB。为避免再次出现界面无响应，原生编辑器不会为该脚本开启语法高亮；仍可显示行号、手动格式化或在浏览器管理页编辑。",
                    "This script is about ${source.length / 1024} KB. Native highlighting is unavailable to prevent another freeze; line numbers, manual formatting, and browser-page editing remain available.")
              } else {
                ns(
                    chinese,
                    "当前脚本约 ${source.length / 1024} KB，执行此操作可能产生明显卡顿。是否仍要继续？",
                    "This script is about ${source.length / 1024} KB. This action may cause noticeable lag. Continue?")
              })
        },
        dismissButton = {
          if (!highlightBlocked) {
            TextButton(onClick = { riskAction = null }) {
              Text(ns(chinese, "取消", "Cancel"))
            }
          }
        },
        confirmButton = {
          Button(
              onClick = {
                riskAction = null
                if (!highlightBlocked) perform(action)
              }) {
            Text(
                if (highlightBlocked) ns(chinese, "知道了", "Got it")
                else ns(chinese, "仍要继续", "Continue"))
          }
        },
    )
  }
}

internal fun parseMetadataDraft(meta: String): ScriptMetadataDraft {
  val values = mutableMapOf<String, MutableList<String>>()
  meta.lineSequence().forEach { line ->
    val match = META_VALUE_LINE.matchEntire(line) ?: return@forEach
    val key = match.groupValues[1]
    val value = match.groupValues[2].trim()
    if (value.isNotEmpty()) values.getOrPut(key) { mutableListOf() }.add(value)
  }
  return ScriptMetadataDraft(
      matches = values["match"].orEmpty().joinToString("\n"),
      includes = values["include"].orEmpty().joinToString("\n"),
      excludes = values["exclude"].orEmpty().joinToString("\n"),
      grants = values["grant"].orEmpty(),
  )
}

internal fun replaceMetadataRules(meta: String, draft: ScriptMetadataDraft): String {
  val normalized = meta.replace("\r\n", "\n").replace('\r', '\n')
  val lines = normalized.lines().filterNot(META_EDITABLE_RULE_LINE::matches).toMutableList()
  while (lines.lastOrNull()?.isEmpty() == true) lines.removeAt(lines.lastIndex)
  val endIndex = lines.indexOfLast { it.trim() == "// ==/UserScript==" }
  require(endIndex >= 0) { "Invalid userscript metadata" }
  val additions =
      buildList {
        draft.matchRules.forEach { add("// @match $it") }
        draft.includeRules.forEach { add("// @include $it") }
        draft.excludeRules.forEach { add("// @exclude $it") }
      }
  lines.addAll(endIndex, additions)
  return lines.joinToString("\n").trimEnd() + "\n"
}

internal fun extractMetadataBlock(source: String): String {
  val range = metadataBlockRange(source)
  return source.substring(0, range.last + 1)
}

internal fun replaceMetadataBlock(source: String, meta: String): String {
  val range = metadataBlockRange(source)
  return meta.trimEnd() + "\n" + source.substring(range.last + 1)
}

private fun metadataBlockRange(source: String): IntRange {
  val start = source.indexOf("// ==UserScript==")
  val markerEnd = source.indexOf("// ==/UserScript==", start.coerceAtLeast(0))
  require(start >= 0 && markerEnd >= start) { "Invalid userscript metadata" }
  val lineEnd = source.indexOf('\n', markerEnd).let { if (it < 0) source.lastIndex else it }
  return 0..lineEnd
}

internal fun normalizedRuleLines(value: String): List<String> =
    value.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()

private fun lineNumberText(lineCount: Int): String = (1..lineCount).joinToString("\n")

private fun editorContentHeight(lineCount: Int) =
    (lineCount * 20 + 16).coerceAtLeast(400).dp

internal fun shouldUsePlatformEditor(sourceLength: Int, lineCount: Int): Boolean =
    sourceLength > MAX_COMPOSE_EDITOR_SOURCE_LENGTH || lineCount > MAX_COMPOSE_EDITOR_LINES

private enum class EditorRiskAction {
  Highlight,
  LineNumbers,
  Format,
}

private class LineNumberEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatEditText(context, attrs) {
  var onSourceChange: ((String) -> Unit)? = null
  var firstLineNumber: Int = 1
    set(value) {
      if (field == value) return
      field = value
      updateGutter()
      invalidate()
    }
  var showLineNumbers: Boolean = true
    set(value) {
      if (field == value) return
      field = value
      updateGutter()
      invalidate()
    }

  private val density = resources.displayMetrics.density
  private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
  private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = density }
  private var gutterWidth = 0
  private var suppressChanges = false

  init {
    gravity = Gravity.TOP or Gravity.START
    typeface = android.graphics.Typeface.MONOSPACE
    textSize = 13f
    includeFontPadding = true
    background = null
    setHorizontallyScrolling(true)
    isVerticalScrollBarEnabled = true
    isHorizontalScrollBarEnabled = true
    overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
    numberPaint.textSize = textSize * 0.86f
    addTextChangedListener(
        object : TextWatcher {
          override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit

          override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) = Unit

          override fun afterTextChanged(value: Editable?) {
            updateGutter()
            if (!suppressChanges) onSourceChange?.invoke(value?.toString().orEmpty())
          }
        })
    updateGutter()
  }

  fun updateSource(value: String) {
    if (text?.toString() == value) return
    val selection = selectionStart.coerceAtLeast(0).coerceAtMost(value.length)
    suppressChanges = true
    setText(value)
    setSelection(selection)
    suppressChanges = false
    updateGutter()
  }

  fun updateColors(textArgb: Int, gutterArgb: Int, dividerArgb: Int) {
    setTextColor(textArgb)
    numberPaint.color = gutterArgb
    dividerPaint.color = dividerArgb
    invalidate()
  }

  private fun updateGutter() {
    val lines = text?.count { it == '\n' }?.plus(1) ?: 1
    val digits = (firstLineNumber + lines - 1).toString().length.coerceAtLeast(2)
    gutterWidth =
        if (showLineNumbers) {
          (numberPaint.measureText("9".repeat(digits)) + 24f * density).toInt()
        } else {
          (12f * density).toInt()
        }
    setPadding(gutterWidth, (12f * density).toInt(), (12f * density).toInt(), (12f * density).toInt())
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (!showLineNumbers) return
    val textLayout = layout ?: return
    if (textLayout.lineCount == 0) return
    val firstLine = textLayout.getLineForVertical(scrollY.coerceAtLeast(0))
    val lastLine =
        textLayout
            .getLineForVertical((scrollY + height).coerceAtMost(textLayout.height))
            .coerceAtMost(textLayout.lineCount - 1)
    val fixedLeft = scrollX.toFloat()
    val fixedTop = scrollY.toFloat()
    val dividerX = fixedLeft + gutterWidth - 8f * density
    canvas.drawLine(dividerX, fixedTop, dividerX, fixedTop + height, dividerPaint)
    for (line in firstLine..lastLine) {
      val baseline = textLayout.getLineBaseline(line).toFloat() + totalPaddingTop
      canvas.drawText(
          (firstLineNumber + line).toString(),
          dividerX - 8f * density,
          baseline,
          numberPaint,
      )
    }
  }
}

private class JavaScriptHighlightTransformation(
    private val keyword: Color,
    private val literal: Color,
    private val number: Color,
    private val comment: Color,
) : VisualTransformation {
  private var cachedSource: String? = null
  private var cachedResult: TransformedText? = null

  override fun filter(text: AnnotatedString): TransformedText {
    val source = text.text
    if (source == cachedSource) cachedResult?.let { return it }
    val highlighted = buildAnnotatedString {
      append(source)
      KEYWORDS.findAll(source).forEach {
        addStyle(SpanStyle(color = keyword, fontWeight = FontWeight.SemiBold), it.range.first, it.range.last + 1)
      }
      NUMBERS.findAll(source).forEach {
        addStyle(SpanStyle(color = number), it.range.first, it.range.last + 1)
      }
      STRINGS.findAll(source).forEach {
        addStyle(SpanStyle(color = literal), it.range.first, it.range.last + 1)
      }
      COMMENTS.findAll(source).forEach {
        addStyle(SpanStyle(color = comment, fontStyle = FontStyle.Italic), it.range.first, it.range.last + 1)
      }
      METADATA.findAll(source).forEach {
        addStyle(SpanStyle(color = keyword, fontWeight = FontWeight.Medium), it.range.first, it.range.last + 1)
      }
    }
    return TransformedText(highlighted, OffsetMapping.Identity).also {
      cachedSource = source
      cachedResult = it
    }
  }
}

internal fun formatJavaScript(source: String): String {
  val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
  var indent = 0
  var inBlockComment = false
  return normalized.lineSequence().joinToString("\n") { rawLine ->
    val line = rawLine.trim()
    if (line.isEmpty()) return@joinToString ""
    val delta = braceDelta(line, inBlockComment).also { inBlockComment = it.second }.first
    val preDedent = if (line.firstOrNull() in listOf('}', ']', ')')) 1 else 0
    val formatted = "  ".repeat((indent - preDedent).coerceAtLeast(0)) + line
    indent = (indent + delta).coerceAtLeast(0)
    formatted
  }
}

private fun braceDelta(line: String, startsInBlockComment: Boolean): Pair<Int, Boolean> {
  var delta = 0
  var quote: Char? = null
  var escaped = false
  var inBlockComment = startsInBlockComment
  var index = 0
  while (index < line.length) {
    val current = line[index]
    val next = line.getOrNull(index + 1)
    if (inBlockComment) {
      if (current == '*' && next == '/') {
        inBlockComment = false
        index++
      }
    } else if (quote != null) {
      if (escaped) escaped = false
      else if (current == '\\') escaped = true
      else if (current == quote) quote = null
    } else {
      when {
        current == '/' && next == '/' -> break
        current == '/' && next == '*' -> {
          inBlockComment = true
          index++
        }
        current == '\'' || current == '"' || current == '`' -> quote = current
        current == '{' || current == '[' || current == '(' -> delta++
        current == '}' || current == ']' || current == ')' -> delta--
      }
    }
    index++
  }
  return delta to inBlockComment
}

private val KEYWORDS =
    Regex("\\b(?:async|await|break|case|catch|class|const|continue|debugger|default|delete|do|else|export|extends|finally|for|from|function|get|if|import|in|instanceof|let|new|of|return|set|static|super|switch|throw|try|typeof|var|void|while|with|yield)\\b")
private val NUMBERS = Regex("\\b(?:0[xX][0-9a-fA-F]+|0[bB][01]+|\\d+(?:\\.\\d+)?)\\b")
private val STRINGS = Regex("(?:'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"|`(?:\\\\.|[^`\\\\])*`)")
private val COMMENTS = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
private val METADATA = Regex("(?m)^\\s*//\\s*@[^\\n]*$")
private val META_VALUE_LINE = Regex("^//\\s+@(match|include|exclude|grant)\\s+(.+)$")
private val META_EDITABLE_RULE_LINE = Regex("^//\\s+@(match|include|exclude)\\s+.*$")

private const val EDITOR_RISK_WARNING_LENGTH = 32 * 1024
private const val MAX_HIGHLIGHT_SOURCE_LENGTH = 96 * 1024
internal const val MAX_COMPOSE_EDITOR_SOURCE_LENGTH = 48 * 1024
internal const val MAX_COMPOSE_EDITOR_LINES = 800
private const val MAX_NATIVE_EDITOR_SOURCE_LENGTH = 192 * 1024
private const val NEW_SCRIPT_ID = "__new__"
private const val NEW_SCRIPT_TEMPLATE =
    """// ==UserScript==
// @name New Script
// @namespace ChromeXt
// @version 1.0.0
// @match https://*/*
// @grant none
// ==/UserScript==

console.log('Hello from ChromeXt');
"""

private fun ns(chinese: Boolean, zh: String, en: String): String =
    UiLocalization.text(chinese, zh, en)
