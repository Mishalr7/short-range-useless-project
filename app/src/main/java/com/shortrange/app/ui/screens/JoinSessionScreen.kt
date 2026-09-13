package com.shortrange.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortrange.app.ui.components.IndustrialHeader
import com.shortrange.app.ui.components.IndustrialOutlineButton
import com.shortrange.app.ui.components.IndustrialPrimaryButton
import com.shortrange.app.ui.theme.CardSurface
import com.shortrange.app.ui.theme.IndustrialDataMono
import com.shortrange.app.ui.theme.IndustrialLabelMono
import com.shortrange.app.ui.theme.LightBorder
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.TechnicalWhite

@Composable
fun JoinSessionScreen(
    onBackClick: () -> Unit,
    onJoinSuccess: (String) -> Unit
) {
    var sessionCode by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TechnicalWhite)
    ) {
        IndustrialHeader(
            title = "JOIN SESSION",
            onBackClick = onBackClick,
            statusText = "SR/01"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "ENTER 6-CHARACTER SESSION CODE",
                    style = IndustrialLabelMono.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Input box for 6-character code (e.g. A83F21)
                BasicTextField(
                    value = sessionCode,
                    onValueChange = {
                        sessionCode = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6)
                        errorMessage = null
                    },
                    textStyle = IndustrialDataMono.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 4.sp,
                        color = PrimaryBlack
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(PrimaryBlack),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (sessionCode.length == 6 && !isJoining) {
                                isJoining = true
                                errorMessage = null
                                scope.launch {
                                    val result = com.shortrange.app.supabase.SupabaseManager.joinSession(sessionCode)
                                    isJoining = false
                                    result.onSuccess { resp ->
                                        onJoinSuccess(resp.session_code)
                                    }.onFailure { err ->
                                        errorMessage = err.message ?: "Failed to join session"
                                    }
                                }
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp)
                                .border(1.5.dp, LightBorder, RoundedCornerShape(2.dp))
                                .background(CardSurface, RoundedCornerShape(2.dp))
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (sessionCode.isEmpty()) {
                                Text(
                                    text = "A83F21",
                                    style = IndustrialDataMono.copy(
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        letterSpacing = 4.sp,
                                        color = PrimaryBlack.copy(alpha = 0.2f)
                                    )
                                )
                            } else {
                                innerTextField()
                            }
                        }
                    }
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ERROR: $errorMessage",
                        style = IndustrialLabelMono.copy(fontSize = 11.sp, color = com.shortrange.app.ui.theme.SignalRed)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                IndustrialPrimaryButton(
                    text = if (isJoining) "CONNECTING..." else "JOIN SESSION",
                    onClick = {
                        if (sessionCode.length == 6 && !isJoining) {
                            isJoining = true
                            errorMessage = null
                            scope.launch {
                                val result = com.shortrange.app.supabase.SupabaseManager.joinSession(sessionCode)
                                isJoining = false
                                result.onSuccess { resp ->
                                    onJoinSuccess(resp.session_code)
                                }.onFailure { err ->
                                    errorMessage = err.message ?: "Failed to join session"
                                }
                            }
                        }
                    },
                    enabled = sessionCode.length == 6 && !isJoining
                )

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "SESSION CODES ARE 6-CHARACTERS\nAND EXPIRE AFTER THE CALL.",
                    style = IndustrialLabelMono.copy(fontSize = 11.sp, lineHeight = 15.sp)
                )
            }

            IndustrialOutlineButton(
                text = "← BACK",
                onClick = onBackClick
            )
        }
    }
}
