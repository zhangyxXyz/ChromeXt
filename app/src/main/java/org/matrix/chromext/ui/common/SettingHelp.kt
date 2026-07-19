package org.matrix.chromext.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.matrix.chromext.R

@Composable
fun SettingTitleWithHelp(
    title: String,
    helpMarkdown: String? = null,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    trailingContent: @Composable () -> Unit = {},
) {
  var showHelp by remember { mutableStateOf(false) }
  Row(
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        title,
        modifier = Modifier.weight(1f, fill = false),
        style = textStyle,
        color = textColor.copy(alpha = if (enabled) 1f else .45f),
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (!helpMarkdown.isNullOrBlank()) {
      IconButton(onClick = { showHelp = true }, modifier = Modifier.size(20.dp)) {
        Icon(
            Icons.AutoMirrored.Rounded.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .55f),
        )
      }
    }
    trailingContent()
  }
  if (showHelp) MarkdownHelpDialog(title, helpMarkdown.orEmpty()) { showHelp = false }
}

@Composable
fun MarkdownHelpDialog(title: String, markdown: String, onDismiss: () -> Unit) {
  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(title) },
      text = {
        SelectionContainer {
          Box(
              Modifier.fillMaxWidth()
                  .heightIn(max = 440.dp)
                  .verticalScroll(rememberScrollState())) {
                Markdown(
                    markdown,
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                    typography =
                        markdownTypography(
                            h1 = MaterialTheme.typography.titleLarge,
                            h2 = MaterialTheme.typography.titleMedium,
                            h3 = MaterialTheme.typography.titleSmall,
                            h4 = MaterialTheme.typography.titleSmall,
                            h5 = MaterialTheme.typography.titleSmall,
                            h6 = MaterialTheme.typography.titleSmall,
                            text = MaterialTheme.typography.bodyMedium,
                            paragraph = MaterialTheme.typography.bodyMedium,
                            ordered = MaterialTheme.typography.bodyMedium,
                            bullet = MaterialTheme.typography.bodyMedium,
                            list = MaterialTheme.typography.bodyMedium,
                        ),
                )
              }
        }
      },
      confirmButton = {
        TextButton(onDismiss) {
          Text(stringResource(R.string.dialog_ok))
        }
      },
  )
}
