package app.arthik.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.arthik.AppContainer
import app.arthik.container

/** `val vm = arthikViewModel { DashboardViewModel(it) }` — ViewModels get the AppContainer. */
@Composable
inline fun <reified VM : ViewModel> arthikViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
