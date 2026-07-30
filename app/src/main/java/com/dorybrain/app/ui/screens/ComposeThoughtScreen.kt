package com.dorybrain.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dorybrain.app.ui.CaptureState
import com.dorybrain.app.ui.NoteListViewModel
import com.dorybrain.app.ui.speech.Waveform
import com.dorybrain.app.ui.speech.rememberSpeechInput
import com.dorybrain.shared.ui.accentColor
import com.dorybrain.shared.ui.components.AppCard
import com.dorybrain.shared.ui.components.IconTile
import com.dorybrain.shared.ui.icon
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeThoughtScreen(
    viewModel: NoteListViewModel,
    startDictation: Boolean,
    onDone: () -> Unit,
    onRefine: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var draftBeforeDictation by remember { mutableStateOf("") }
    val captureState by viewModel.captureState.collectAsState()
    val draftOverride by viewModel.draftOverride.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        viewModel.resetCapture()
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // The controller hands back the whole session transcript, so the field is
    // rebuilt from the pre-dictation text each time rather than appended to.
    val speech = rememberSpeechInput(
        onTranscript = { spoken -> draft = appendSpoken(draftBeforeDictation, spoken) },
        onError = { message -> viewModel.postMessage(message) }
    )

    // Coming back from "Edit Manually" with the AI's wording in hand.
    LaunchedEffect(draftOverride) {
        draftOverride?.let { returned ->
            draft = returned
            draftBeforeDictation = returned
            viewModel.consumeDraftOverride()
            focusRequester.requestFocus()
        }
    }

    // Arriving from "Tap to speak" starts listening straight away.
    LaunchedEffect(startDictation) {
        if (startDictation) {
            draftBeforeDictation = ""
            speech.start()
        } else if (draftOverride == null) {
            focusRequester.requestFocus()
        }
    }

    // Once the bucket comes back, show it briefly then return to Home.
    LaunchedEffect(captureState) {
        if (captureState is CaptureState.Saved) {
            delay(850)
            viewModel.resetCapture()
            onDone()
        }
    }

    val isSaving = captureState !is CaptureState.Editing
    val isListening = speech.isListening

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("New Thought", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isListening) {
                        // While dictating, the useful action is bailing out —
                        // the mic button below is what finishes.
                        TextButton(onClick = { speech.stop() }) {
                            Text("Cancel", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                keyboard?.hide()
                                viewModel.addNote(draft)
                            },
                            enabled = draft.isNotBlank() && !isSaving
                        ) {
                            Text("Send", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (isListening) {
                ListeningPane(
                    transcript = draft,
                    levels = speech.levels,
                    isHearingSpeech = speech.isHearingSpeech,
                    modifier = Modifier.weight(1f)
                )
            } else {
                EditingPane(
                    draft = draft,
                    onDraftChange = { draft = it },
                    focusRequester = focusRequester,
                    enabled = !isSaving,
                    captureState = captureState,
                    isSaving = isSaving,
                    onRefine = {
                        keyboard?.hide()
                        if (viewModel.refineDraft(draft)) onRefine()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            CaptureControls(
                isListening = isListening,
                enabled = !isSaving,
                onKeyboard = {
                    if (isListening) speech.stop()
                    focusRequester.requestFocus()
                    keyboard?.show()
                },
                onToggleMic = {
                    if (!isListening) draftBeforeDictation = draft
                    keyboard?.hide()
                    speech.toggle()
                },
                modifier = Modifier.padding(bottom = 28.dp)
            )
        }
    }
}

/** Joins dictated text onto whatever was already in the field. */
private fun appendSpoken(existing: String, spoken: String): String =
    if (existing.isBlank()) spoken else "${existing.trimEnd()} $spoken"

/**
 * The dictation state: waveform, status, and what's been heard so far. The
 * transcript is kept on screen deliberately — with continuous dictation you
 * may speak several sentences, and hiding them makes it impossible to tell
 * whether anything was captured.
 */
@Composable
private fun ListeningPane(
    transcript: String,
    levels: List<Float>,
    isHearingSpeech: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (transcript.isNotBlank()) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = transcript,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Waveform(
            levels = levels,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
        )

        Text(
            text = if (isHearingSpeech) "Listening..." else "Mic is on",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp)
        )

        Text(
            text = if (isHearingSpeech) {
                "Tap to stop"
            } else {
                "Take as long as you like — tap to stop"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )

        Box(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun EditingPane(
    draft: String,
    onDraftChange: (String) -> Unit,
    focusRequester: FocusRequester,
    enabled: Boolean,
    captureState: CaptureState,
    isSaving: Boolean,
    onRefine: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    "Dump whatever's on your mind...",
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.medium,
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        AnimatedVisibility(visible = draft.isNotBlank() && !isSaving) {
            TextButton(onClick = onRefine, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text("  Refine with AI", style = MaterialTheme.typography.labelLarge)
            }
        }

        AnimatedVisibility(visible = isSaving) {
            CategorizingCard(
                state = captureState,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Box(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CategorizingCard(
    state: CaptureState,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (state is CaptureState.Saved) "Sorted" else "Categorizing...",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            when (state) {
                is CaptureState.Saved -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconTile(
                                icon = state.category.icon,
                                tint = state.category.accentColor,
                                size = 30.dp
                            )
                            Text(
                                text = "Saved to ${state.category.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "AI is sorting your thought",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureControls(
    isListening: Boolean,
    enabled: Boolean,
    onKeyboard: () -> Unit,
    onToggleMic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val micColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        label = "micColor"
    )

    // Halo rings that breathe while the mic is open.
    val haloScale by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.45f else 1f,
        animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Reverse),
        label = "haloScale"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircleButton(
            onClick = onKeyboard,
            enabled = enabled,
            modifier = Modifier.size(46.dp)
        ) {
            Icon(
                Icons.Filled.Keyboard,
                contentDescription = "Show keyboard",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        Box(
            modifier = Modifier.padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(haloScale)
                        .alpha(0.18f)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(micColor)
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(1f + (haloScale - 1f) * 0.5f)
                        .alpha(0.28f)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(micColor)
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(micColor)
                    .alpha(if (enabled) 1f else 0.5f),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onToggleMic, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = if (isListening) "Stop dictation" else "Dictate",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Balances the row against the keyboard button on the left.
        Box(modifier = Modifier.size(46.dp))
    }
}

@Composable
private fun CircleButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
