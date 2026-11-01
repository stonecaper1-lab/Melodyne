package org.melodyne.app.ui.genre

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.melodyne.app.data.repo.CloudBuilder
import org.melodyne.app.data.repo.CloudTag
import org.melodyne.app.ui.Amoled
import org.melodyne.app.ui.LocalAccent
import org.melodyne.app.ui.Txt

/** A Last.fm-style tag cloud: the bigger and brighter the word, the more the world uses that tag. Tap a word to open it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreCloud(tags: List<CloudTag>, onClick: (String) -> Unit) {
    val accent = LocalAccent.current
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        tags.forEachIndexed { i, t ->
            Txt(
                t.name,
                Modifier.clickable { onClick(t.name) }.padding(vertical = 4.dp),
                size = CloudBuilder.sizeSp(t.weight).sp,
                weight = if (t.weight > 0.6f) FontWeight.Bold else FontWeight.Medium,
                color = if (i % 5 == 0) accent else Amoled.OnBg.copy(alpha = 0.45f + t.weight * 0.5f),
            )
        }
    }
}
