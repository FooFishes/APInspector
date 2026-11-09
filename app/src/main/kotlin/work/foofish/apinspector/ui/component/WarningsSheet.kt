package work.foofish.apinspector.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.ParseWarning
import work.foofish.apinspector.ui.text.message
import work.foofish.apinspector.ui.theme.codeSmall

/** 列出解析时被忽略的内容及其位置。 */
@Composable
fun WarningsSheet(
    warnings: ImmutableList<ParseWarning>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Text(
            text = stringResource(R.string.warnings_title),
            modifier = Modifier.padding(horizontal = SheetPadding),
            style = MaterialTheme.typography.titleLarge,
        )
        LazyColumn(
            contentPadding = PaddingValues(SheetPadding),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            itemsIndexed(warnings) { _, warning ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = warning.pointer,
                        style = MaterialTheme.typography.codeSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = warning.message(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private val SheetPadding = 24.dp
private val ItemSpacing = 12.dp
