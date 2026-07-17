package org.matrix.chromext.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.matrix.chromext.BuildConfig
import org.matrix.chromext.UiLocalization
import org.matrix.chromext.update.AppRelease
import org.matrix.chromext.update.UpdateClient
import org.matrix.chromext.update.isSameVersion

private sealed interface ReleaseHistoryState {
  data object Loading : ReleaseHistoryState

  data class Ready(val releases: List<AppRelease>) : ReleaseHistoryState

  data class Error(val message: String) : ReleaseHistoryState
}

@Composable
fun ReleaseHistoryScreen(controller: ChromeXtController) {
  val context = controller.context
  val chinese = controller.isChinese
  val client = remember { UpdateClient() }
  var reload by remember { mutableIntStateOf(0) }
  var state by remember { mutableStateOf<ReleaseHistoryState>(ReleaseHistoryState.Loading) }

  LaunchedEffect(reload) {
    state = ReleaseHistoryState.Loading
    state =
        client
            .releases()
            .fold(
                onSuccess = { ReleaseHistoryState.Ready(it) },
                onFailure = {
                  ReleaseHistoryState.Error(
                      UiLocalization.error(
                          chinese,
                          it.localizedMessage,
                          "加载发布历史失败",
                          "Could not load release history",
                      ))
                },
            )
  }

  when (val current = state) {
    ReleaseHistoryState.Loading ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
    is ReleaseHistoryState.Error ->
        ReleaseHistoryMessage(
            chinese,
            UiLocalization.text(chinese, "加载失败", "Could not load releases"),
            current.message,
        ) {
          reload++
        }
    is ReleaseHistoryState.Ready ->
        if (current.releases.isEmpty()) {
          ReleaseHistoryMessage(
              chinese,
              UiLocalization.text(chinese, "暂无发布版本", "No releases found"),
              "GitHub Releases",
          ) {
            reload++
          }
        } else {
          LazyColumn(
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(current.releases, key = { it.tag }) { release ->
              ReleaseHistoryCard(release, chinese) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
              }
            }
          }
        }
  }
}

@Composable
private fun ReleaseHistoryMessage(
    chinese: Boolean,
    title: String,
    detail: String,
    onRetry: () -> Unit,
) {
  Column(
      Modifier.fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Icon(
        Icons.Rounded.NewReleases,
        null,
        Modifier.size(48.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
    Text(
        title,
        Modifier.padding(top = 14.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        detail,
        Modifier.padding(top = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilledTonalButton(onRetry, Modifier.padding(top = 16.dp)) {
      Icon(Icons.Rounded.Refresh, null)
      Spacer(Modifier.width(8.dp))
      Text(UiLocalization.text(chinese, "重试", "Retry"))
    }
  }
}

@Composable
private fun ReleaseHistoryCard(release: AppRelease, chinese: Boolean, onOpen: () -> Unit) {
  var expanded by rememberSaveable(release.tag) { mutableStateOf(false) }
  val current = isSameVersion(release.tag, BuildConfig.VERSION_NAME)
  val expandable = release.body.length > 120
  val bodyScrollState = rememberScrollState()
  Card(
      shape = RoundedCornerShape(22.dp),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
                  else MaterialTheme.colorScheme.surfaceContainer,
          ),
      border =
          if (current) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .5f))
          else null,
  ) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
              release.name.ifBlank { release.tag },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
          )
          Text(
              buildString {
                append(release.tag)
                release.publishedAt.take(10).takeIf(String::isNotBlank)?.let {
                  append(" · ").append(it)
                }
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        when {
          current ->
              AssistChip(
                  onClick = {},
                  enabled = false,
                  label = { Text(UiLocalization.text(chinese, "当前版本", "Current")) },
              )
          release.prerelease ->
              AssistChip(
                  onClick = {},
                  enabled = false,
                  label = { Text(UiLocalization.text(chinese, "预发布", "Prerelease")) },
              )
        }
      }
      if (release.body.isNotBlank()) {
        HorizontalDivider(
            Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Box(
            Modifier.fillMaxWidth()
                .then(
                    when {
                      !expandable -> Modifier
                      expanded -> Modifier.heightIn(max = 360.dp).verticalScroll(bodyScrollState)
                      else -> Modifier.heightIn(max = 112.dp).clipToBounds()
                    }),
        ) {
          Markdown(
              release.body,
              modifier = Modifier.fillMaxWidth(),
              typography = releaseCardMarkdownTypography(),
          )
        }
      }
      Row(
          Modifier.fillMaxWidth().padding(top = 12.dp),
          horizontalArrangement = Arrangement.End,
      ) {
        if (expandable) {
          TextButton({ expanded = !expanded }) {
            Text(
                UiLocalization.text(
                    chinese,
                    if (expanded) "收起" else "展开",
                    if (expanded) "Collapse" else "Expand",
                ))
          }
        }
        TextButton(onOpen) {
          Icon(Icons.AutoMirrored.Rounded.OpenInNew, null)
          Spacer(Modifier.width(6.dp))
          Text(UiLocalization.text(chinese, "查看 Release", "View release"))
        }
      }
    }
  }
}

@Composable
private fun releaseCardMarkdownTypography() =
    markdownTypography(
        h1 = MaterialTheme.typography.titleMedium,
        h2 = MaterialTheme.typography.titleSmall,
        h3 = MaterialTheme.typography.labelLarge,
        h4 = MaterialTheme.typography.labelLarge,
        h5 = MaterialTheme.typography.labelLarge,
        h6 = MaterialTheme.typography.labelLarge,
        text = MaterialTheme.typography.bodySmall,
        paragraph = MaterialTheme.typography.bodySmall,
        ordered = MaterialTheme.typography.bodySmall,
        bullet = MaterialTheme.typography.bodySmall,
        list = MaterialTheme.typography.bodySmall,
    )
