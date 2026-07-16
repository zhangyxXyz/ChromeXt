package org.matrix.chromext.ui

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.matrix.chromext.UiLocalization

private data class LanguageOption(
    val tag: String,
    val nativeName: String,
    val secondaryName: String,
)

@Composable
fun LanguageSettingsScreen(controller: ChromeXtController) {
  controller.revision
  val chinese = controller.isChinese
  var query by remember { mutableStateOf("") }
  val current = controller.language()
  val options = remember { supportedLanguages() }
  val systemLocale = remember { Resources.getSystem().configuration.locales[0] }
  val systemOption =
      remember(systemLocale) {
        LanguageOption(
            systemLocale.toLanguageTag(),
            systemLocale.getDisplayName(systemLocale).replaceFirstChar {
              it.titlecase(systemLocale)
            },
            systemLocale.getDisplayName(Locale.ENGLISH),
        )
      }
  val filtered =
      options.filter {
        query.isBlank() ||
            it.nativeName.contains(query, true) ||
            it.secondaryName.contains(query, true) ||
            it.tag.contains(query, true)
      }

  LazyColumn(
      contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item {
      OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text(UiLocalization.text(chinese, "搜索语言", "Search languages")) },
          singleLine = true,
          leadingIcon = { Icon(Icons.Outlined.Search, null) },
          shape = RoundedCornerShape(24.dp),
      )
    }
    if (query.isBlank()) {
      item { LanguageSectionLabel(UiLocalization.text(chinese, "推荐", "Suggested")) }
      item {
        LanguageOptionRow(
            LanguageOption(
                ChromeXtController.LANGUAGE_SYSTEM,
                UiLocalization.text(chinese, "跟随系统", "System default"),
                UiLocalization.text(
                    chinese, "检测到 ${systemOption.nativeName}", "Detected ${systemOption.nativeName}"),
            ),
            current,
            controller::setLanguage,
        )
      }
      item { LanguageOptionRow(systemOption, current, controller::setLanguage) }
      item { LanguageSectionLabel(UiLocalization.text(chinese, "所有语言", "All languages")) }
    }
    items(filtered, key = { "all-${it.tag}" }) { option ->
      LanguageOptionRow(option, current, controller::setLanguage)
    }
  }
}

@Composable
private fun LanguageSectionLabel(text: String) {
  Text(
      text,
      Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
  )
}

@Composable
private fun LanguageOptionRow(
    option: LanguageOption,
    current: String,
    onSelect: (String) -> Unit,
) {
  val selected =
      if (option.tag == ChromeXtController.LANGUAGE_SYSTEM) {
        current == ChromeXtController.LANGUAGE_SYSTEM
      } else {
        current.equals(option.tag, true)
      }
  val selectLanguage = { if (!selected) onSelect(option.tag) }
  Card(
      modifier = Modifier.fillMaxWidth().clickable(onClick = selectLanguage),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.Language, null, tint = MaterialTheme.colorScheme.primary)
      Column(Modifier.padding(start = 16.dp).weight(1f)) {
        Text(option.nativeName, fontWeight = FontWeight.Medium)
        if (option.secondaryName.isNotBlank() && option.secondaryName != option.nativeName) {
          Text(
              option.secondaryName,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      RadioButton(selected, onClick = selectLanguage)
    }
  }
}

internal fun languageLabel(tag: String, chinese: Boolean): String =
    when (tag) {
      ChromeXtController.LANGUAGE_ZH -> "简体中文"
      ChromeXtController.LANGUAGE_ZH_TW -> "繁體中文"
      ChromeXtController.LANGUAGE_EN -> "English"
      else -> UiLocalization.text(chinese, "跟随系统", "System default")
    }

private fun supportedLanguages(): List<LanguageOption> =
    listOf(
            ChromeXtController.LANGUAGE_ZH,
            ChromeXtController.LANGUAGE_ZH_TW,
            ChromeXtController.LANGUAGE_EN,
        )
        .map { tag ->
          val locale = Locale.forLanguageTag(tag)
          LanguageOption(
              tag,
              locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) },
              locale.getDisplayName(Locale.ENGLISH),
          )
        }
        .sortedBy { it.nativeName.lowercase() }
