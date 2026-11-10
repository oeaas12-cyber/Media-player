package com.prism.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.prism.player.data.AppSettings
import com.prism.player.data.Profile
import com.prism.player.ui.appContainer
import com.prism.player.ui.components.Avatar
import com.prism.player.ui.components.Avatars
import com.prism.player.ui.components.GlassBackdrop
import com.prism.player.ui.components.GlassButton
import com.prism.player.ui.components.GlassChip
import com.prism.player.ui.components.GlassIconButton
import com.prism.player.ui.components.glass
import com.prism.player.ui.components.glassClickable
import com.prism.player.ui.theme.LocalIsTv
import com.prism.player.ui.theme.Palette
import kotlinx.coroutines.launch

/** "Who's watching?" — pick a profile, add one, or manage them. */
@Composable
fun ProfilePickerScreen(onChosen: () -> Unit, onEdit: (String) -> Unit, onBack: (() -> Unit)? = null) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val tv = LocalIsTv.current
    var manage by rememberSaveable { mutableStateOf(false) }
    if (onBack != null) BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(null)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (manage) "Manage profiles" else "Who's watching?",
                style = if (tv) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                color = Color.White, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (tv) 40.dp else 28.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(if (tv) 36.dp else 22.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(settings.profiles, key = { it.id }) { p ->
                    ProfileTile(p, size = if (tv) 140.dp else 96.dp, manage = manage) {
                        if (manage) onEdit(p.id)
                        else scope.launch { container.switchProfile(p.id); onChosen() }
                    }
                }
                if (settings.profiles.size < 8) {
                    item(key = "add") { AddTile(size = if (tv) 140.dp else 96.dp) { onEdit("new") } }
                }
            }
            Spacer(Modifier.height(if (tv) 40.dp else 28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton(if (manage) "Done" else "Manage profiles") { manage = !manage }
                if (onBack != null && !manage) GlassButton("Cancel", onClick = onBack)
            }
        }
    }
}

@Composable
private fun ProfileTile(p: Profile, size: androidx.compose.ui.unit.Dp, manage: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier.glassClickable(interaction, focusScale = 1.1f, onClick = onClick).width(size + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Avatar(p.name, p.avatar, size, focused = focused)
            if (manage) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(size * 0.3f).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, null, tint = Color(0xFF0B1020), modifier = Modifier.size(size * 0.18f)) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            p.name, color = if (focused) Color.White else Palette.TextSecondary,
            style = MaterialTheme.typography.titleMedium, maxLines = 1, textAlign = TextAlign.Center,
        )
        if (p.isKids) Text("Kids", color = Palette.Accent, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AddTile(size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier.glassClickable(interaction, focusScale = 1.1f, onClick = onClick).width(size + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(size).glass(CircleShape, strength = if (focused) 2.5f else 1f, highlighted = focused),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, "Add profile", tint = Color.White, modifier = Modifier.size(size * 0.4f)) }
        Spacer(Modifier.height(10.dp))
        Text("Add profile", color = if (focused) Color.White else Palette.TextSecondary, style = MaterialTheme.typography.titleMedium)
    }
}

/** Create or edit a profile: name, avatar colour, kids mode, delete. */
@Composable
fun ProfileEditScreen(profileId: String, onDone: () -> Unit) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val tv = LocalIsTv.current
    val existing = settings.profiles.firstOrNull { it.id == profileId }
    val isNew = profileId == "new"
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var avatar by rememberSaveable(existing?.id) { mutableStateOf(existing?.avatar ?: (settings.profiles.size % Avatars.palette.size)) }
    var kids by rememberSaveable(existing?.id) { mutableStateOf(existing?.isKids ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onDone)

    Box(Modifier.fillMaxSize()) {
        GlassBackdrop(null)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = if (tv) 120.dp else 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onDone)
                Text(if (isNew) "New profile" else "Edit profile", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            }
            Column(
                Modifier.glass().padding(24.dp).widthIn(max = 720.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Avatar(name.ifBlank { "?" }, avatar, 88.dp)
                    OutlinedTextField(
                        value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text("Colour", color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(Avatars.palette.indices.toList()) { i ->
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        Box(
                            Modifier.glassClickable(interaction, focusScale = 1.15f) { avatar = i }
                                .size(44.dp).clip(CircleShape).background(Avatars.brush(i))
                                .border(if (i == avatar || focused) 3.dp else 0.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == avatar) Icon(Icons.Filled.Check, null, tint = Color.White)
                        }
                    }
                }
                Text("Type", color = Palette.TextSecondary, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Standard", !kids) { kids = false }
                    GlassChip("Kids (family titles only)", kids) { kids = true }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassButton("Save", primary = true) {
                        if (name.isBlank()) return@GlassButton
                        scope.launch {
                            if (isNew) {
                                val p = container.settings.addProfile(name, avatar, kids)
                                container.switchProfile(p.id)
                            } else if (existing != null) {
                                container.settings.updateProfile(existing.copy(name = name.trim(), avatar = avatar, isKids = kids))
                            }
                            onDone()
                        }
                    }
                    if (!isNew && existing != null && settings.profiles.size > 1) {
                        if (confirmDelete) {
                            GlassButton("Delete “${existing.name}”?") {
                                scope.launch {
                                    container.progress.deleteProfileData(existing.id)
                                    container.settings.removeProfile(existing.id)
                                    val next = container.settings.current().activeProfile
                                    if (next != null) container.switchProfile(next.id)
                                    onDone()
                                }
                            }
                        } else {
                            GlassButton("Delete") { confirmDelete = true }
                        }
                    }
                }
                if (!isNew && settings.profiles.size <= 1) {
                    Text("The last profile can't be deleted.", color = Palette.TextTertiary)
                }
            }
        }
    }
}
