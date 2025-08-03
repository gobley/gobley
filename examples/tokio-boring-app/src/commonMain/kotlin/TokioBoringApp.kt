/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package gobley.uniffi.examples.tokioboringapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun App() {
    MaterialTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        Scaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) {
                    Snackbar(
                        modifier = Modifier.padding(12.dp),
                        content = {
                            Text(
                                text = it.visuals.message,
                                maxLines = 1,
                            )
                        }
                    )
                }
            },
            modifier = Modifier.displayCutoutPadding(),
        ) {
            var url by remember { mutableStateOf("https://example.com") }
            var response by remember { mutableStateOf<String?>(null) }
            val coroutineScope = rememberCoroutineScope()
            val retrieveFromUrl: () -> Unit = {
                coroutineScope.launch {
                    try {
                        response = null
                        response = retrieveFrom(url)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        println(e)
                        response = "Failed: ${e.message}"
                    }
                }
            }

            LaunchedEffect(Unit) {
                retrieveFromUrl()
            }

            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("URL") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions { retrieveFromUrl() },
                    singleLine = true,
                )
                Button(retrieveFromUrl) {
                    Text("Retrieve using Tokio with Boring SSL")
                }
                Text(
                    text = response ?: "Loading...",
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
