package org.matrix.chromext.ui.common

import android.widget.ImageView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.matrix.chromext.ui.BrowserTarget

@Composable
fun BrowserTargetSelector(
    targets: List<BrowserTarget>,
    selectedPackage: String?,
    modifier: Modifier = Modifier,
    onSelect: (BrowserTarget) -> Unit,
) {
  if (targets.isEmpty()) return
  val context = LocalContext.current
  Row(
      modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    targets.forEach { target ->
      val selected = target.packageName == selectedPackage
      FilterChip(
          selected = selected,
          onClick = { onSelect(target) },
          label = { Text(target.label, maxLines = 1) },
          leadingIcon = {
            AndroidView(
                factory = { viewContext ->
                  ImageView(viewContext).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                },
                update = { view ->
                  view.setImageDrawable(
                      runCatching {
                            context.packageManager.getApplicationIcon(target.packageName)
                          }
                          .getOrNull())
                },
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(7.dp)),
            )
          },
          shape = RoundedCornerShape(18.dp),
          colors =
              FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
      )
    }
  }
}
