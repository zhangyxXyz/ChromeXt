package org.matrix.chromext.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun SettingGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
      if (title.isNotBlank()) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
      }
      content()
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    helpMarkdown: String? = null,
    iconContainerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconContentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    descriptionMaxLines: Int = 2,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    titleTrailingContent: @Composable () -> Unit = {},
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
  val alpha = if (enabled) 1f else .45f
  val interaction =
      when {
        onLongClick != null ->
            Modifier.combinedClickable(
                enabled = enabled,
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick,
            )
        onClick != null -> Modifier.combinedClickable(enabled = enabled, onClick = onClick)
        else -> Modifier
      }
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .then(interaction)
              .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      SettingLeadingIcon(icon, iconContainerColor, iconContentColor, alpha)
    }
    Column(
        Modifier.padding(start = if (icon == null) 0.dp else 14.dp, end = 8.dp).weight(1f),
    ) {
      SettingTitleWithHelp(
          title = title,
          helpMarkdown = helpMarkdown,
          enabled = enabled,
          trailingContent = titleTrailingContent)
      if (!description.isNullOrBlank()) {
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            maxLines = descriptionMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
      }
      if (!value.isNullOrBlank()) {
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (trailingContent != null) {
      Row(
          Modifier.alpha(alpha),
          verticalAlignment = Alignment.CenterVertically,
          content = trailingContent,
      )
    }
  }
}

@Composable
fun NavigationSettingItem(
    title: String,
    description: String? = null,
    value: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    helpMarkdown: String? = null,
    descriptionMaxLines: Int = 2,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
  SettingItem(
      title = title,
      description = description,
      value = value,
      icon = icon,
      enabled = enabled,
      helpMarkdown = helpMarkdown,
      descriptionMaxLines = descriptionMaxLines,
      onClick = onClick,
      onLongClick = onLongClick,
      trailingContent = {
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
  )
}

@Composable
fun SwitchSettingItem(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    enabled: Boolean = true,
    helpMarkdown: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
  SettingItem(
      title = title,
      description = description,
      icon = icon,
      enabled = enabled,
      helpMarkdown = helpMarkdown,
      onClick = { onCheckedChange(!checked) },
      trailingContent = {
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
      },
  )
}

@Composable
fun SettingLeadingIcon(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    alpha: Float = 1f,
) {
  Surface(shape = RoundedCornerShape(12.dp), color = containerColor.copy(alpha = alpha)) {
    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
      Icon(
          icon,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = contentColor.copy(alpha = alpha),
      )
    }
  }
}
