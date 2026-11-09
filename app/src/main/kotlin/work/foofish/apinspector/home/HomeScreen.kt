package work.foofish.apinspector.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import work.foofish.apinspector.R
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.theme.APInspectorTheme

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = painterResource(R.drawable.ic_api),
                title = stringResource(R.string.home_empty_title),
                body = stringResource(R.string.home_empty_body),
            )
        }
    }
}

/** 双栏布局下尚未选中 API 文档时的详情栏占位。 */
@Composable
fun NoSpecSelectedPane(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            icon = painterResource(R.drawable.ic_list_alt),
            title = stringResource(R.string.home_no_spec_selected_title),
            body = stringResource(R.string.home_no_spec_selected_body),
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreview() {
    APInspectorTheme {
        HomeScreen()
    }
}
