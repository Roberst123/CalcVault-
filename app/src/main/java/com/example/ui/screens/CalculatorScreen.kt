package com.example.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.ui.theme.CalcButtonBg
import com.example.ui.theme.CalcOperatorBg
import com.example.ui.theme.CalcSpecialBg
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.CalcVaultViewModel
import com.example.viewmodel.VaultScreenState

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CalculatorScreen(
    viewModel: CalcVaultViewModel,
    modifier: Modifier = Modifier
) {
    val screenState by viewModel.screenState.collectAsState()
    val calcState by viewModel.calculatorState.collectAsState()
    val lockoutSeconds by viewModel.lockoutCountdown.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    var showHistory by remember { mutableStateOf(false) }

    fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25)
            }
        } catch (_: Exception) { }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Disguised App Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calculator",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Discreet Biometric Quick Unlock (if enabled)
                    if (viewModel.authManager.isBiometricEnabled()) {
                        IconButton(
                            onClick = {
                                (context as? FragmentActivity)?.let { activity ->
                                    viewModel.triggerBiometricUnlock(activity)
                                }
                            },
                            modifier = Modifier.testTag("biometric_unlock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric Unlock",
                                tint = TextSecondary
                            )
                        }
                    }

                    // Calculation History toggle
                    IconButton(
                        onClick = { showHistory = !showHistory },
                        modifier = Modifier.testTag("calculator_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Calculation History",
                            tint = if (showHistory) GoldAccent else TextSecondary
                        )
                    }
                }
            }

            // Calculation History overlay
            AnimatedVisibility(visible = showHistory) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Calculation History",
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (calcState.history.isEmpty()) {
                            Text(
                                text = "No recent calculations",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            LazyColumn {
                                items(calcState.history) { (expr, res) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = expr, color = TextSecondary, fontSize = 14.sp)
                                        Text(text = "= $res", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Status message toast banner (if any)
            statusMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Display Screen Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = if (calcState.expression.isEmpty()) "0" else calcState.expression,
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.testTag("calculator_display")
                )

                if (calcState.resultPreview.isNotEmpty()) {
                    Text(
                        text = calcState.resultPreview,
                        style = MaterialTheme.typography.headlineSmall,
                        color = GoldAccent,
                        textAlign = TextAlign.End,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Keypad Layout (5 rows)
            val buttonSpacing = 10.dp

            // Row 1: AC, (, ), ÷
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(buttonSpacing)) {
                CalcKey(text = "AC", modifier = Modifier.weight(1f), bg = CalcSpecialBg, fg = GoldAccent) {
                    vibrate()
                    viewModel.onCalculatorClear()
                }
                CalcKey(text = "(", modifier = Modifier.weight(1f), bg = CalcOperatorBg, fg = TextPrimary) {
                    vibrate()
                    viewModel.onCalculatorDigit("(")
                }
                CalcKey(text = ")", modifier = Modifier.weight(1f), bg = CalcOperatorBg, fg = TextPrimary) {
                    vibrate()
                    viewModel.onCalculatorDigit(")")
                }
                CalcKey(text = "÷", modifier = Modifier.weight(1f), bg = CalcOperatorBg, fg = GoldAccent) {
                    vibrate()
                    viewModel.onCalculatorOperator("÷")
                }
            }

            Spacer(modifier = Modifier.height(buttonSpacing))

            // Row 2: 7, 8, 9, ×
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(buttonSpacing)) {
                CalcKey(text = "7", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit("7")
                }
                CalcKey(text = "8", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit("8")
                }
                CalcKey(text = "9", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit("9")
                }
                CalcKey(text = "×", modifier = Modifier.weight(1f), bg = CalcOperatorBg, fg = GoldAccent) {
                    vibrate()
                    viewModel.onCalculatorOperator("×")
                }
            }

            Spacer(modifier = Modifier.height(buttonSpacing))

            // Row 3: 4, 5, 6, -
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(buttonSpacing)) {
                CalcKey(text = "4", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit("4")
                }
                CalcKey(text = "5", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit("5")
                }
                CalcKey(text = "6", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit("6")
                }
                CalcKey(text = "-", modifier = Modifier.weight(1f), bg = CalcOperatorBg, fg = GoldAccent) {
                    vibrate()
                    viewModel.onCalculatorOperator("-")
                }
            }

            Spacer(modifier = Modifier.height(buttonSpacing))

            // Row 4: 1, 2, 3, +
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(buttonSpacing)) {
                CalcKey(text = "1", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit("1")
                }
                CalcKey(text = "2", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit("2")
                }
                CalcKey(text = "3", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit("3")
                }
                CalcKey(text = "+", modifier = Modifier.weight(1f), bg = CalcOperatorBg, fg = GoldAccent) {
                    vibrate()
                    viewModel.onCalculatorOperator("+")
                }
            }

            Spacer(modifier = Modifier.height(buttonSpacing))

            // Row 5: 0, ., ⌫, =
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(buttonSpacing)) {
                CalcKey(text = "0", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit("0")
                }
                CalcKey(text = ".", modifier = Modifier.weight(1f)) {
                    vibrate()
                    viewModel.onCalculatorDigit(".")
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(68.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CalcButtonBg)
                        .clickable {
                            vibrate()
                            viewModel.onCalculatorBackspace()
                        }
                        .testTag("calculator_backspace_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // The '=' key: stealth vault trigger!
                CalcKey(
                    text = "=",
                    modifier = Modifier.weight(1f),
                    bg = GoldAccent,
                    fg = ObsidianBlack,
                    isEqualsKey = true,
                    onLongClick = {
                        vibrate()
                        (context as? FragmentActivity)?.let { activity ->
                            viewModel.triggerBiometricUnlock(activity)
                        }
                    }
                ) {
                    vibrate()
                    viewModel.onCalculatorEquals()
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Overlay 1: Initial Setup Wizard (if PIN not configured)
        if (screenState == VaultScreenState.PIN_SETUP) {
            InitialPinSetupModal(viewModel = viewModel)
        }

        // Overlay 2: Lockout Screen (if rate-limited due to repeated failed attempts)
        if (screenState == VaultScreenState.RATE_LIMITED) {
            LockoutOverlay(countdownSeconds = lockoutSeconds)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CalcKey(
    text: String,
    modifier: Modifier = Modifier,
    bg: Color = CalcButtonBg,
    fg: Color = TextPrimary,
    isEqualsKey: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag(if (isEqualsKey) "calculator_equals_button" else "key_$text"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = if (text.length > 2) 18.sp else 26.sp,
            fontWeight = if (isEqualsKey) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun InitialPinSetupModal(viewModel: CalcVaultViewModel) {
    var step by remember { mutableStateOf(1) } // 1: Enter PIN, 2: Confirm PIN
    var pinFirst by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Security Setup",
                    tint = GoldAccent,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Welcome to CalcVault",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (step == 1)
                        "Create your private vault PIN (4 to 8 digits). Your photos and videos will be encrypted with hardware-backed AES-256-GCM."
                    else
                        "Re-enter your secret PIN to confirm and establish your private vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = if (step == 1) pinFirst else pinConfirm,
                    onValueChange = { input ->
                        if (input.length <= 8 && input.all { it.isDigit() }) {
                            if (step == 1) pinFirst = input else pinConfirm = input
                            errorMsg = null
                        }
                    },
                    label = { Text(if (step == 1) "Secret PIN (4-8 digits)" else "Confirm Secret PIN") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_pin_input")
                )

                errorMsg?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = err, color = CrimsonError, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (step == 1) {
                            if (pinFirst.length < 4) {
                                errorMsg = "PIN must be at least 4 digits"
                            } else {
                                step = 2
                            }
                        } else {
                            if (pinConfirm != pinFirst) {
                                errorMsg = "PINs do not match. Please try again."
                                step = 1
                                pinFirst = ""
                                pinConfirm = ""
                            } else {
                                viewModel.setupInitialPin(pinConfirm)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_setup_pin_button")
                ) {
                    Text(
                        text = if (step == 1) "Next" else "Save & Start Disguise",
                        color = ObsidianBlack,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Tip: When opening CalcVault later, enter this PIN and press '=' to unlock your private media.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun LockoutOverlay(countdownSeconds: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Temporary Lockout",
                    tint = CrimsonError,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Security Lockout Active",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Too many incorrect PIN attempts. Rate-limiting is enforced to prevent automated brute-force attacks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "$countdownSeconds",
                    style = MaterialTheme.typography.displayMedium,
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "seconds remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Zero-Knowledge Protection: There is no master bypass or secret recovery back door.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp
                )
            }
        }
    }
}
