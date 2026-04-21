package com.chatlite.charroom

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

object ChatUi {
    @Composable
    fun AuthCard(title: String, content: @Composable ColumnScope.() -> Unit) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                elevation = 8.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = title, style = MaterialTheme.typography.h5)
                    Spacer(modifier = Modifier.height(16.dp))
                    content()
                }
            }
        }
    }

    @Composable
    fun AuthInputField(
        value: String,
        label: String,
        onValueChange: (String) -> Unit,
        visualTransformation: VisualTransformation = VisualTransformation.None
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth()
        )
    }

    @Composable
    fun PrimaryActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text)
        }
    }

    @Composable
    fun SecondaryActionText(text: String, onClick: () -> Unit) {
        TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text)
        }
    }

    @Composable
    fun FormMessage(message: String) {
        if (message.isNotBlank()) {
            Text(text = message, color = MaterialTheme.colors.error)
        }
    }

    @Composable
    fun ScreenTopBar(title: String, navigationIcon: (@Composable () -> Unit)? = null) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = navigationIcon,
            backgroundColor = MaterialTheme.colors.primary,
            contentColor = MaterialTheme.colors.onPrimary
        )
    }

    @Composable
    fun BackButton(onBack: () -> Unit) {
        IconButton(onClick = onBack) {
            Text("←")
        }
    }

    @Composable
    fun LoadingState() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colors.primary)
        }
    }

    @Composable
    fun ErrorState(message: String) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = message, color = MaterialTheme.colors.error)
        }
    }

    @Composable
    fun EmptyState(message: String) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = message, style = MaterialTheme.typography.body1)
        }
    }
}

