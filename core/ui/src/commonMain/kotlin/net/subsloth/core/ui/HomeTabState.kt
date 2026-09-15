package net.subsloth.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Remembers the Home screen's selected tab across navigation and process
 * death.
 *
 * The tab itself lives in `HomeViewModel`, but hosts may clear that ViewModel
 * when the Home entry leaves composition (it does not need to stay composed
 * while a detail screen is on top). Storing the tab name in the entry's
 * saveable state lets a recreated ViewModel restore it, and each host passes
 * the current [MutableState.value] into its ViewModel factory.
 */
@Composable
fun rememberHomeTabState(): MutableState<String> = rememberSaveable { mutableStateOf("") }
