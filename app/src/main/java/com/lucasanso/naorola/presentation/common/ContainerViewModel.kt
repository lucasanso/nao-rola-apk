package com.lucasanso.naorola.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lucasanso.naorola.NaoRolaApp
import com.lucasanso.naorola.di.AppContainer

/** Creates a ViewModel from the app's [AppContainer] (manual dependency injection). */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as NaoRolaApp).container
    return viewModel { create(container) }
}
