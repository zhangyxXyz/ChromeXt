package org.matrix.chromext.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.matrix.chromext.BuildConfig
import org.matrix.chromext.UiLocalization
import org.matrix.chromext.update.UpdateClient
import org.matrix.chromext.utils.Log

private data class AboutDocument(val title: String, val content: String)

@Composable
fun AboutSettingsScreen(controller: ChromeXtController) {
  val context = controller.context
  val chinese = controller.isChinese
  var document by remember { mutableStateOf<AboutDocument?>(null) }
  val privacy =
      aboutText(
          chinese,
          """## 本地数据

- 用户脚本、设置、备份记录和界面偏好保存在设备本地。
- ChromeXt 不包含广告、统计分析或用户行为追踪。

## 联网用途

只有使用相关功能时才会联网：

- WebDAV 连接、备份与恢复
- GitHub Release 更新检查、更新包下载及项目链接
- 用户脚本自身声明的网络访问

## 凭据与备份

- WebDAV 凭据仅在你保存配置后保存在模块设置中。
- WebDAV 配置写入备份前会明确提示；加密备份的安全性取决于你设置的密码。
- 恢复操作会在确认后替换对应本地数据。

## 第三方服务

WebDAV 服务器、浏览器、用户脚本目标网站和 GitHub 会依据各自的服务条款与隐私政策处理请求。连接服务或安装脚本前，请先核实来源和政策。""",
          """## Local data

- Userscripts, settings, backup records, and appearance preferences are stored on this device.
- ChromeXt does not include advertising, analytics, or user-behavior tracking.

## Network access

Network access occurs only when you use a related feature:

- WebDAV connection, backup, and restore
- GitHub Release checks, update downloads, and project links
- Network access declared by installed userscripts

## Credentials and backups

- WebDAV credentials are stored in module settings only after you save the configuration.
- The app prompts before including WebDAV configuration in a backup; encrypted-backup safety depends on your password.
- Restore replaces the corresponding local data only after confirmation.

## Third-party services

WebDAV servers, browsers, userscript target sites, and GitHub process requests under their own terms and privacy policies. Verify the source and policies before connecting a service or installing a script.""",
      )
  val disclaimer =
      aboutText(
          chinese,
          """# 免责声明

ChromeXt 是开源软件，按“原样”提供，不附带任何形式的保证。

## 用户责任

- 你应自行确认用户脚本、网站、WebDAV 服务、备份文件及更新包来源的合法性、准确性、安全性与可用性。
- 恢复备份、安装脚本或更新前，请独立保存重要数据并核验内容。
- 用户脚本可以在浏览器页面中执行代码；仅安装你信任的脚本。

## 项目责任

项目维护者不托管用户内容，也无法保证第三方脚本、网站、浏览器或网络服务的可用性及行为。在适用法律允许的范围内，维护者不对使用本应用造成的数据丢失、服务中断、安全问题或其他损失承担责任。""",
          """# Disclaimer

ChromeXt is open-source software and is provided “as is”, without warranty of any kind.

## User responsibility

- You are responsible for the legality, accuracy, security, and availability of userscripts, websites, WebDAV services, backup files, and update packages.
- Keep an independent copy of important data and verify content before restoring a backup or installing a script or update.
- Userscripts can execute code in browser pages; install only scripts you trust.

## Project responsibility

The maintainers do not host user content and cannot guarantee the availability or behavior of third-party scripts, websites, browsers, or network services. To the extent permitted by law, the maintainers are not liable for data loss, interruption, security incidents, or other damages arising from use of this app.""",
      )

  LazyColumn(
      Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Card(
          shape = RoundedCornerShape(28.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
            AndroidView(
                factory = { viewContext ->
                  ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(
                        viewContext.packageManager.getApplicationIcon(viewContext.packageName))
                  }
                },
                modifier = Modifier.padding(10.dp).size(62.dp),
            )
          }
          Text(
              "ChromeXt",
              Modifier.padding(top = 14.dp),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
          )
          Text(
              "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
              Modifier.padding(top = 5.dp),
              color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .74f),
          )
          Text(
              aboutText(chinese, "Chromium/WebView 用户脚本与开发工具", "Userscripts and developer tools for Chromium/WebView"),
              Modifier.padding(top = 12.dp),
              style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
    item { UpdateSection(controller) }
    item {
      AboutGroup(aboutText(chinese, "项目", "Project")) {
        AboutRow(
            Icons.Rounded.Code,
            aboutText(chinese, "源代码", "Source code"),
            "zhangyxXyz/ChromeXt",
        ) {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateClient.PROJECT_URL)))
        }
        AboutRow(
            Icons.Rounded.BugReport,
            aboutText(chinese, "问题反馈", "Feedback and issues"),
            aboutText(chinese, "前往 GitHub Issues", "Open GitHub Issues"),
        ) {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${UpdateClient.PROJECT_URL}/issues")))
        }
        AboutRow(
            Icons.Rounded.Palette,
            "Material Design 3",
            aboutText(chinese, "界面设计体系与规范", "Interface design system and guidelines"),
        ) {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://m3.material.io/")))
        }
      }
    }
    item {
      AboutGroup(aboutText(chinese, "法律与详情", "Legal and details")) {
        AboutRow(
            Icons.Rounded.PrivacyTip,
            aboutText(chinese, "隐私政策", "Privacy policy"),
            aboutText(chinese, "了解数据与网络使用方式", "How data and network access are used"),
        ) {
          document = AboutDocument(aboutText(chinese, "隐私政策", "Privacy policy"), privacy)
        }
        AboutRow(
            Icons.Rounded.Gavel,
            aboutText(chinese, "开源许可", "Open-source license"),
            aboutText(chinese, "查看完整 GNU GPL v3 许可", "Read the complete GNU GPL v3 license"),
        ) {
          val license =
              runCatching {
                    context.assets.open("LICENSE.md").bufferedReader().use { it.readText() }
                  }
                  .getOrElse { "GNU General Public License v3.0\n\n${UpdateClient.PROJECT_URL}" }
          document = AboutDocument(aboutText(chinese, "开源许可", "Open-source license"), license)
        }
        AboutRow(
            Icons.AutoMirrored.Rounded.Article,
            aboutText(chinese, "免责声明", "Disclaimer"),
            aboutText(chinese, "使用边界与责任说明", "Usage boundaries and responsibility statement"),
        ) {
          document = AboutDocument(aboutText(chinese, "免责声明", "Disclaimer"), disclaimer)
        }
        AboutRow(
            Icons.Rounded.Info,
            aboutText(chinese, "应用详情", "App details"),
            BuildConfig.APPLICATION_ID,
        ) {
          (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
              .setPrimaryClip(ClipData.newPlainText("package", BuildConfig.APPLICATION_ID))
          Log.toast(context, aboutText(chinese, "包名已复制", "Package name copied"))
        }
      }
    }
  }

  document?.let { AboutDocumentSheet(it, chinese) { document = null } }
}

@Composable
private fun AboutGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
  Card(
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
      Text(
          title,
          Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
      )
      content()
    }
  }
}

@Composable
private fun AboutRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
  Row(
      Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
      Icon(
          icon,
          null,
          Modifier.padding(9.dp).size(20.dp),
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Column(Modifier.padding(start = 14.dp, end = 8.dp).weight(1f)) {
      Text(title)
      if (detail.isNotBlank()) {
        Text(
            detail,
            Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Icon(Icons.Rounded.ChevronRight, null)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutDocumentSheet(
    document: AboutDocument,
    chinese: Boolean,
    onDismiss: () -> Unit,
) {
  val content =
      remember(document) {
        val lines = document.content.lines()
        if (lines.firstOrNull()?.removePrefix("# ")?.trim() == document.title) {
          lines.drop(1).joinToString("\n").trimStart()
        } else {
          document.content
        }
      }
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal = 22.dp)) {
      Text(
          document.title,
          Modifier.fillMaxWidth().padding(bottom = 18.dp),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
      )
      SelectionContainer(Modifier.weight(1f)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
          Markdown(
              content,
              typography = aboutMarkdownTypography(),
              modifier = Modifier.fillMaxWidth().wrapContentHeight(),
          )
        }
      }
      Button(
          onClick = onDismiss,
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      ) {
        Text(aboutText(chinese, "确定", "OK"))
      }
    }
  }
}

@Composable
internal fun aboutMarkdownTypography() =
    markdownTypography(
        h1 = MaterialTheme.typography.headlineMedium,
        h2 = MaterialTheme.typography.titleLarge,
        h3 = MaterialTheme.typography.titleMedium,
        h4 = MaterialTheme.typography.titleSmall,
        h5 = MaterialTheme.typography.titleSmall,
        h6 = MaterialTheme.typography.titleSmall,
        text = MaterialTheme.typography.bodyMedium,
        paragraph = MaterialTheme.typography.bodyMedium,
        ordered = MaterialTheme.typography.bodyMedium,
        bullet = MaterialTheme.typography.bodyMedium,
        list = MaterialTheme.typography.bodyMedium,
    )

private fun aboutText(chinese: Boolean, zh: String, en: String): String =
    UiLocalization.text(chinese, zh, en)
