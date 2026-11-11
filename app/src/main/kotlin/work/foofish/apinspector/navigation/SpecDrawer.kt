package work.foofish.apinspector.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.spec.SpecSummary

/**
 * Medium 及以上窗口中，打开某份 API 文档后首页不再可见，通过侧边导航切换 API 文档（PRD 1.2）。
 *
 * @param enabled 为 `false` 时（Compact 窗口或仍在首页）禁用手势，抽屉不会出现。
 */
@Composable
fun SpecDrawer(
    viewModel: SpecDrawerViewModel,
    drawerState: DrawerState,
    enabled: Boolean,
    selected: SpecId?,
    onSelect: (SpecId) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val specs by viewModel.specs.collectAsStateWithLifecycle()
    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet(drawerState = drawerState) {
                Text(
                    text = stringResource(R.string.operations_switch_spec),
                    modifier = Modifier.padding(DrawerPadding),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                LazyColumn {
                    items(specs, key = { it.id.value }) { spec ->
                        NavigationDrawerItem(
                            label = { Text(spec.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(painterResource(R.drawable.ic_api), contentDescription = null) },
                            selected = spec.id == selected,
                            onClick = { onSelect(spec.id) },
                            modifier = Modifier.padding(horizontal = ItemPadding),
                        )
                    }
                }
            }
        },
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = enabled,
        content = content,
    )
}

private val DrawerPadding = 24.dp
private val ItemPadding = 12.dp
