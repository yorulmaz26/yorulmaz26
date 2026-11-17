package com.kuaforumcepte.app.feature.legal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kuaforumcepte.app.core.designsystem.component.KcTopBar
import com.kuaforumcepte.app.core.designsystem.component.SkeletonCardList
import com.kuaforumcepte.app.core.designsystem.theme.Spacing
import com.kuaforumcepte.app.data.registration.LegalDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** assets/legal altındaki basit Markdown metnini (başlık, madde, paragraf) okunaklı gösterir. */
@Composable
fun LegalDocumentScreen(document: LegalDocument, onBack: () -> Unit) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(document) {
        lines = withContext(Dispatchers.IO) { context.assets.open(document.asset).bufferedReader().readLines() }
    }
    Scaffold(topBar = { KcTopBar(document.title, onBack) }) { padding ->
        val content = lines
        if (content == null) {
            SkeletonCardList(3, Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.screen)) {
            content.forEach { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() -> Text("", style = MaterialTheme.typography.bodySmall)
                    line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.m).semantics { heading() })
                    line.startsWith("> ") -> Text(line.removePrefix("> "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    line.startsWith("- ") -> Text("•  " + line.removePrefix("- ").replace("**", ""), style = MaterialTheme.typography.bodyMedium)
                    else -> Text(line.replace("**", ""), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
