package app.arthik.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.arthik.ui.BottomBarSpace

/** Standard screen: title row + a lazy column with 16dp gutters that clears the floating bottom bar. */
@Composable
fun ScreenList(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    actions: @Composable () -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomBarSpace),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "__title", contentType = "title") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(ArthikIcons.Back, "Back") }
                Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                actions()
            }
        }
        content()
    }
}
