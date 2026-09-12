package br.com.navalbattle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Callout
import br.com.navalbattle.game.Tone
import kotlinx.coroutines.delay

@Composable
fun HudLabel(text: String, color: Color = Naval.muted, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = NavalType.monoSmall, color = color, modifier = modifier)
}

@Composable
fun PrimaryButton(
    text: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (enabled) Naval.amber else Naval.surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text.uppercase(),
            style = NavalType.button,
            color = if (enabled) Naval.amberInk else Naval.muted
        )
        subtitle?.let {
            Text(it, style = NavalType.monoSmall, color = if (enabled) Naval.amberInk.copy(alpha = 0.7f) else Naval.muted)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(BorderStroke(1.dp, Naval.line))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text.uppercase(),
            style = NavalType.button,
            color = if (enabled) Naval.ink else Naval.muted
        )
        subtitle?.let { Text(it, style = NavalType.monoSmall, color = Naval.muted) }
    }
}

@Composable
fun ModeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selected) Naval.surface3 else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) Naval.amber else Naval.line))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.uppercase(),
            style = NavalType.mono,
            color = if (selected) Naval.amberStrong else Naval.muted
        )
    }
}

@Composable
fun CalloutBanner(callout: Callout?, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<Callout?>(null) }

    LaunchedEffect(callout?.id) {
        if (callout != null) {
            current = callout
            visible = true
            delay(1900)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut(),
        modifier = modifier
    ) {
        val c = current
        if (c != null) {
            val accent = when (c.tone) {
                Tone.HIT -> Naval.amberStrong
                Tone.SUNK -> Naval.danger
                Tone.SCAN -> Naval.greenBright
                Tone.MISS -> Naval.inkSoft
                Tone.INFO -> Naval.inkSoft
            }
            Column(
                modifier = Modifier
                    .background(Naval.bg.copy(alpha = 0.92f))
                    .border(BorderStroke(1.dp, accent))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(c.main.uppercase(), style = NavalType.title, color = accent, textAlign = TextAlign.Center)
                Text(c.sub, style = NavalType.monoSmall, color = Naval.inkSoft, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun AbilityButton(
    code: String,
    name: String,
    enabled: Boolean,
    selected: Boolean,
    cooldown: Int,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(if (selected) Naval.surface3 else Color.Transparent)
                .border(BorderStroke(1.5.dp, if (enabled) Naval.green else Naval.line))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                code,
                style = NavalType.mono,
                color = when {
                    selected -> Naval.amberStrong
                    enabled -> Naval.greenBright
                    else -> Naval.muted
                }
            )
            if (cooldown > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(18.dp)
                        .background(Naval.bg)
                        .border(BorderStroke(1.dp, Naval.muted)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$cooldown", style = NavalType.monoSmall, color = Naval.muted)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(name.uppercase(), style = NavalType.monoSmall, color = if (enabled) Naval.inkSoft else Naval.muted)
    }
}
