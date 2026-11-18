package com.example.erudito.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.erudito.domain.html.StructuredArticleHtmlProcessor
import com.example.erudito.domain.model.ArticleContentFormat

@Composable
fun ArticleBodyContent(
    content: String?,
    contentFormat: ArticleContentFormat,
    unextractableFormulaCount: Int,
    onOpenLink: (String) -> Unit,
    testTag: String,
    emptyContentText: String,
    modifier: Modifier = Modifier,
    htmlProcessor: StructuredArticleHtmlProcessor? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (content.isNullOrBlank()) {
            Text(
                text = emptyContentText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(testTag)
            )
            return@Column
        }

        if (unextractableFormulaCount > 0) {
            val noticeText = if (unextractableFormulaCount == 1) {
                "1 fórmula matemática deste artigo não pôde ser extraída da Wikipédia e é exibida como [Fórmula indisponível]."
            } else {
                "$unextractableFormulaCount fórmulas matemáticas deste artigo não puderam ser extraídas da Wikipédia e são exibidas como [Fórmula indisponível]."
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTag}_unavailable_formulas_notice")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = noticeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        when (contentFormat) {
            ArticleContentFormat.PLAIN_TEXT -> {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 28.sp,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag(testTag)
                )
            }

            ArticleContentFormat.STRUCTURED_HTML -> {
                StructuredArticleWebView(
                    html = content,
                    onOpenLink = onOpenLink,
                    htmlProcessor = htmlProcessor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                )
            }
        }
    }
}
