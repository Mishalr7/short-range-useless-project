package com.shortrange.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortrange.app.ui.components.IndustrialHeader
import com.shortrange.app.ui.components.IndustrialOutlineButton
import com.shortrange.app.ui.components.IndustrialPanel
import com.shortrange.app.ui.components.IndustrialPrimaryButton
import com.shortrange.app.ui.theme.IndustrialDataMono
import com.shortrange.app.ui.theme.IndustrialLabelMono
import com.shortrange.app.ui.theme.InstrumentGrey
import com.shortrange.app.ui.theme.LightBorder
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.TechnicalWhite
import com.shortrange.app.ui.theme.TelemetryGreen

@Composable
fun CreateSessionScreen(
    onBackClick: () -> Unit,
    onParticipantJoined: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sessionCode = "SR-4821"
    var participant2Connected by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TechnicalWhite)
    ) {
        IndustrialHeader(
            title = "CREATE SESSION",
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
                    text = "ESTABLISH A TEMPORARY\nCOMMUNICATION LINK.",
                    style = IndustrialLabelMono.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack,
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Session code card
                IndustrialPanel {
                    Text(
                        text = "SESSION CODE",
                        style = IndustrialLabelMono
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sessionCode,
                            style = IndustrialDataMono.copy(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = PrimaryBlack
                        )

                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(sessionCode))
                                Toast.makeText(context, "Session code copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy code",
                                tint = PrimaryBlack
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "SHARE THIS CODE WITH\nTHE OTHER PARTICIPANT.",
                        style = IndustrialLabelMono.copy(fontSize = 11.sp, lineHeight = 14.sp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Participants status
                IndustrialPanel {
                    Text(
                        text = "PARTICIPANTS",
                        style = IndustrialLabelMono
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Participant 01
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(TelemetryGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PARTICIPANT 01",
                                style = IndustrialDataMono.copy(fontSize = 12.sp),
                                color = PrimaryBlack
                            )
                            Text(
                                text = "CONNECTED (THIS DEVICE)",
                                style = IndustrialLabelMono.copy(fontSize = 10.sp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Participant 02
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (participant2Connected) TelemetryGreen else InstrumentGrey,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PARTICIPANT 02",
                                style = IndustrialDataMono.copy(fontSize = 12.sp),
                                color = PrimaryBlack
                            )
                            Text(
                                text = if (participant2Connected) "CONNECTED" else "WAITING...",
                                style = IndustrialLabelMono.copy(fontSize = 10.sp)
                            )
                        }
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IndustrialPrimaryButton(
                    text = if (participant2Connected) "PROCEED TO CALIBRATION" else "SIMULATE PARTICIPANT JOIN",
                    onClick = {
                        if (!participant2Connected) {
                            participant2Connected = true
                        } else {
                            onParticipantJoined()
                        }
                    },
                    showArrow = true
                )

                IndustrialOutlineButton(
                    text = "CANCEL SESSION",
                    onClick = onBackClick
                )
            }
        }
    }
}
