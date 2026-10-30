package component.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import component.QingliaoColors
import component.UserAvatar
import component.chat.shouldSubmitComposer
import data.repository.SocialRepository
import java.util.UUID
import kotlinx.coroutines.launch
import model.*
import presentation.viewmodel.SocialViewModel

@Composable
fun SocialScreen(
    token: String,
    userId: String,
    onBack: () -> Unit,
    onChat: (User) -> Unit,
    initialProfileId: String? = null,
) {
    val vm = remember(token, userId) { SocialViewModel(SocialRepository(token), userId) }
    DisposableEffect(vm) { onDispose { vm.close() } }
    LaunchedEffect(vm, initialProfileId) { initialProfileId?.let(vm::profile) }
    val state by vm.state.collectAsState()
    val s = LocalStrings.current
    var query by remember(vm) { mutableStateOf(vm.filters.query) }
    var tag by remember(vm) { mutableStateOf(vm.filters.tag) }
    var channel by remember(vm) { mutableStateOf(vm.filters.channel) }
    val tabs = listOf("people", "encounter", "daily", "me")
    val pageKey = "${state.tab}:${state.profile?.id.orEmpty()}:${vm.filters}"
    val listState =
        remember(vm, pageKey) {
            val position = vm.position(pageKey)
            androidx.compose.foundation.lazy.LazyListState(position.index, position.offset)
        }
    DisposableEffect(vm, pageKey, listState) {
        onDispose {
            vm.rememberPosition(
                pageKey,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }
    component.BackHandler(enabled = state.profile != null && initialProfileId == null) {
        vm.closeProfile()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
        Box(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            if (state.profile != null && initialProfileId == null) vm.closeProfile()
                            else onBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, s["social.back"])
                    }
                    Text(
                        s[if (initialProfileId != null) "chat.info" else "social.discover"],
                        style = MaterialTheme.typography.h6,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (initialProfileId == null)
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, s["social.close"])
                        }
                }
                if (initialProfileId == null)
                    TabRow(
                        tabs.indexOf(state.tab).coerceAtLeast(0),
                        backgroundColor = MaterialTheme.colors.background,
                        contentColor = MaterialTheme.colors.primary,
                    ) {
                        tabs.forEach { tab ->
                            Tab(
                                selected = state.tab == tab,
                                onClick = { vm.tab(tab) },
                                text = { Text(s["social.$tab"], maxLines = 1) },
                            )
                        }
                    }
                // Reserve the progress line so loading does not shift the content.
                Box(Modifier.fillMaxWidth().height(3.dp)) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    if (state.error.isNotBlank())
                        item {
                            Surface(
                                color = MaterialTheme.colors.error.copy(alpha = .08f),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        s["social.failed"],
                                        style = MaterialTheme.typography.subtitle2,
                                        color = MaterialTheme.colors.error,
                                    )
                                    Text(state.error, style = MaterialTheme.typography.body2)
                                }
                            }
                        }
                    val profile = state.profile
                    if (profile != null) {
                        item {
                            SocialProfilePanel(
                                profile,
                                state.busy || !state.profileReady,
                                vm,
                                onChat,
                            )
                        }
                        if (!state.profileReady && !state.busy)
                            item {
                                TextButton(onClick = { vm.profile(profile.id) }) {
                                    Text(s["social.retry"])
                                }
                            }
                        if (profile.relation == "SELF" && state.blocked.isNotEmpty()) {
                            item {
                                Text(
                                    s["social.blockedList"],
                                    style = MaterialTheme.typography.subtitle1,
                                )
                            }
                            items(state.blocked, key = { it.id }) { person ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        person.username,
                                        Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    TextButton(
                                        onClick = { vm.unblock(person.id) },
                                        enabled = !state.busy,
                                    ) {
                                        Text(s["social.unblock"])
                                    }
                                }
                            }
                        }
                    } else
                        when (state.tab) {
                            "people" -> {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedTextField(
                                            query,
                                            { query = it.take(60) },
                                            label = { Text(s["social.search"]) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            leadingIcon = { Icon(Icons.Default.Search, null) },
                                            trailingIcon =
                                                if (query.isNotEmpty()) {
                                                    {
                                                        IconButton(onClick = { query = "" }) {
                                                            Icon(
                                                                Icons.Default.Close,
                                                                s["polish.clearSearch"],
                                                            )
                                                        }
                                                    }
                                                } else null,
                                            keyboardOptions =
                                                KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions =
                                                KeyboardActions(
                                                    onSearch = { vm.search(query, tag, channel) }
                                                ),
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            OutlinedTextField(
                                                tag,
                                                { tag = it.take(24) },
                                                label = { Text(s["social.tags"]) },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true,
                                            )
                                            Button(
                                                onClick = { vm.search(query, tag, channel) },
                                                enabled = !state.busy,
                                                modifier = Modifier.height(48.dp),
                                            ) {
                                                Text(s["polish.searchAction"])
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("all", "mutual", "city").forEach { value ->
                                                OutlinedButton(
                                                    onClick = {
                                                        channel = value
                                                        vm.search(query, tag, channel)
                                                    },
                                                    enabled = !state.busy,
                                                    border =
                                                        BorderStroke(
                                                            1.dp,
                                                            if (channel == value)
                                                                MaterialTheme.colors.primary
                                                            else
                                                                MaterialTheme.colors.onSurface.copy(
                                                                    alpha = .12f
                                                                ),
                                                        ),
                                                    colors =
                                                        ButtonDefaults.outlinedButtonColors(
                                                            backgroundColor =
                                                                if (channel == value)
                                                                    MaterialTheme.colors.primary
                                                                        .copy(alpha = .08f)
                                                                else MaterialTheme.colors.surface
                                                        ),
                                                    contentPadding =
                                                        PaddingValues(
                                                            horizontal = 12.dp,
                                                            vertical = 10.dp,
                                                        ),
                                                ) {
                                                    Text(
                                                        s["social.$value"],
                                                        style = MaterialTheme.typography.caption,
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            s["social.discoveryHint"],
                                            style = MaterialTheme.typography.caption,
                                            color =
                                                MaterialTheme.colors.onSurface.copy(alpha = .6f),
                                        )
                                    }
                                }
                                val emptyResult = state.people.isEmpty() && !state.busy
                                if (emptyResult && state.error.isBlank())
                                    item {
                                        SocialEmptyState(s["polish.noPeople"], s["social.empty"]) {
                                            TextButton(onClick = { vm.tab("me") }) {
                                                Text(s["social.me"])
                                            }
                                        }
                                    }
                                items(state.people, key = { it.id }) { person ->
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = 0.dp,
                                        border =
                                            BorderStroke(
                                                1.dp,
                                                MaterialTheme.colors.onSurface.copy(alpha = .08f),
                                            ),
                                    ) {
                                        Column(
                                            Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                UserAvatar(person.toUser(), size = 48.dp)
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        person.username,
                                                        style = MaterialTheme.typography.subtitle1,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    person.signature
                                                        ?.takeIf { it.isNotBlank() }
                                                        ?.let {
                                                            Text(
                                                                it,
                                                                style =
                                                                    MaterialTheme.typography.body2,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis,
                                                                color =
                                                                    MaterialTheme.colors.onSurface
                                                                        .copy(alpha = .65f),
                                                            )
                                                        }
                                                }
                                            }
                                            if (!person.interests.isNullOrEmpty())
                                                Text(
                                                    person.interests.joinToString(" · "),
                                                    style = MaterialTheme.typography.caption,
                                                    color = MaterialTheme.colors.primary,
                                                )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    s[
                                                        "social.reason.${person.reason ?: "EXPLORE"}"],
                                                    Modifier.weight(1f),
                                                    style = MaterialTheme.typography.caption,
                                                    color =
                                                        MaterialTheme.colors.onSurface.copy(
                                                            alpha = .6f
                                                        ),
                                                )
                                                OutlinedButton(
                                                    onClick = { vm.profile(person.id) },
                                                    enabled = !state.busy,
                                                ) {
                                                    Text(s["social.view"])
                                                }
                                            }
                                        }
                                    }
                                }
                                if (state.cursor != null)
                                    item {
                                        OutlinedButton(
                                            onClick = {
                                                vm.search(
                                                    vm.filters.query,
                                                    vm.filters.tag,
                                                    vm.filters.channel,
                                                    true,
                                                )
                                            },
                                            enabled = !state.busy,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Text(s["social.more"])
                                        }
                                    }
                            }
                            "encounter" -> item { EncounterPanel(state.room, state.busy, vm) }
                            "daily" ->
                                state.daily?.let { daily ->
                                    item {
                                        DailyEditor(
                                            daily,
                                            state.answerDraft,
                                            state.busy,
                                            vm::editAnswer,
                                            vm::answer,
                                        )
                                    }
                                    items(daily.answers, key = { it.id }) { answer ->
                                        Card(
                                            Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            elevation = 0.dp,
                                            border =
                                                BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colors.onSurface.copy(
                                                        alpha = .08f
                                                    ),
                                                ),
                                        ) {
                                            Column(
                                                Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                Text(
                                                    answer.content,
                                                    style = MaterialTheme.typography.body1,
                                                )
                                                TextButton(
                                                    onClick = { vm.profile(answer.author.id) }
                                                ) {
                                                    Text(
                                                        answer.author.username +
                                                            " · " +
                                                            s["social.view"]
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (daily.nextCursor != null)
                                        item {
                                            OutlinedButton(
                                                onClick = { vm.daily(true) },
                                                enabled = !state.busy,
                                            ) {
                                                Text(s["social.more"])
                                            }
                                        }
                                    if (daily.answers.isEmpty())
                                        item {
                                            SocialEmptyState(
                                                s["social.daily"],
                                                s["social.noAnswers"],
                                            )
                                        }
                                }
                        }
                }
            }
        }
    }
}

@Composable
private fun SocialEmptyState(title: String, message: String, action: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.Explore,
            null,
            Modifier.size(36.dp),
            tint = MaterialTheme.colors.primary.copy(alpha = .65f),
        )
        Text(title, style = MaterialTheme.typography.subtitle1)
        Text(
            message,
            style = MaterialTheme.typography.body2,
            color = MaterialTheme.colors.onSurface.copy(alpha = .6f),
            textAlign = TextAlign.Center,
        )
        action()
    }
}

@Composable
private fun SocialProfilePanel(
    profile: SocialProfile,
    busy: Boolean,
    vm: SocialViewModel,
    onChat: (User) -> Unit,
) {
    val s = LocalStrings.current
    var editing by remember(profile) { mutableStateOf(false) }
    var requesting by remember(profile.id) { mutableStateOf(false) }
    var reporting by remember(profile.id) { mutableStateOf(false) }
    var reason by remember(profile.id) { mutableStateOf("") }
    var audience by remember(profile.id) { mutableStateOf("self") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val palette = QingliaoColors
        val coverColors =
            when (profile.cover) {
                "ocean" -> listOf(palette.surface, palette.apricot)
                "forest" -> listOf(palette.apricot, palette.paper)
                else -> listOf(palette.message, palette.apricot, palette.paper)
            }
        Box(Modifier.fillMaxWidth().height(142.dp)) {
            Box(
                Modifier.fillMaxWidth()
                    .height(96.dp)
                    .background(Brush.horizontalGradient(coverColors), RoundedCornerShape(22.dp))
            ) {
                Text(
                    s["paper.brand"],
                    Modifier.align(Alignment.TopEnd).padding(20.dp),
                    style = MaterialTheme.typography.subtitle2,
                    color = palette.onApricot,
                )
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colors.background,
                    border = BorderStroke(3.dp, MaterialTheme.colors.background),
                ) {
                    UserAvatar(profile.toUser(), size = 72.dp)
                }
                Text(
                    profile.username,
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(bottom = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            s["social.account"] +
                " " +
                profile.id +
                " · " +
                s["social.relation.${profile.relation}"],
            style = MaterialTheme.typography.caption,
            color = palette.muted,
        )
        profile.signature?.takeIf { it.isNotBlank() }?.let { Text(it) }
        if (editing) {
            ProfileEditor(profile, busy, vm::save) { editing = false }
        } else {
            if (profile.relation == "SELF") {
                Text(s["social.preview"])
                Row {
                    listOf("self", "stranger", "friends").forEach { value ->
                        TextButton(
                            onClick = { audience = value },
                            colors =
                                ButtonDefaults.textButtonColors(
                                    contentColor =
                                        if (audience == value) MaterialTheme.colors.primary
                                        else MaterialTheme.colors.onSurface.copy(alpha = .55f)
                                ),
                        ) {
                            Text(s["social.$value"])
                        }
                    }
                }
            }
            val visible =
                profile.detailsVisible &&
                    (profile.relation != "SELF" ||
                        audience == "self" ||
                        profile.settings?.visibility == "PUBLIC" ||
                        (audience == "friends" && profile.settings?.visibility == "FRIENDS"))
            if (visible) {
                profile.bio
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Text(s["social.bio"], style = MaterialTheme.typography.subtitle1)
                        Text(it)
                    }
                Text(
                    profile.interests.orEmpty().joinToString(" · "),
                    color = MaterialTheme.colors.primary,
                )
                profile.city
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Text(s["social.city"] + " · " + it) }
                profile.intent
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        Text(s["social.intent"], style = MaterialTheme.typography.subtitle1)
                        Text(it)
                    }
            } else Text(s["social.hidden"])
            when (profile.relation) {
                "SELF" ->
                    Button(onClick = { editing = true }, enabled = !busy) { Text(s["social.edit"]) }
                "FRIEND" ->
                    Button(onClick = { onChat(profile.toUser()) }) { Text(s["social.chat"]) }
                "PENDING" -> Text(s["social.pending"])
                else ->
                    Button(onClick = { requesting = !requesting }, enabled = !busy) {
                        Text(s["social.add"])
                    }
            }
            if (requesting && profile.relation != "PENDING") {
                OutlinedTextField(
                    reason,
                    { reason = it.take(120) },
                    label = { Text(s["social.requestReason"]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.friend(reason) }, enabled = !busy) {
                    Text(s["social.sendRequest"])
                }
            }
            if (profile.relation != "SELF") {
                Row {
                    TextButton(onClick = vm::block, enabled = !busy) { Text(s["social.block"]) }
                    TextButton(onClick = { reporting = !reporting }) { Text(s["social.report"]) }
                }
                if (reporting) ReportEditor(busy) { vm.report(it) }
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    profile: SocialProfile,
    busy: Boolean,
    onSave: (SocialEdit) -> Unit,
    onCancel: () -> Unit,
) {
    val s = LocalStrings.current
    val settings = profile.settings ?: SocialSettings()
    var form by
        remember(profile) {
            mutableStateOf(
                SocialEdit(
                    profile.username,
                    profile.signature.orEmpty(),
                    settings.bio,
                    settings.interests,
                    settings.city,
                    settings.intent,
                    settings.cover,
                    settings.visibility,
                    settings.discoverable,
                    settings.mutualDiscovery,
                )
            )
        }
    var tags by remember(profile) { mutableStateOf(settings.interests.joinToString("、")) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(s["polish.identity"], style = MaterialTheme.typography.subtitle1)
        OutlinedTextField(
            form.username,
            { form = form.copy(username = it.take(32)) },
            label = { Text(s["social.username"]) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            form.signature,
            { form = form.copy(signature = it.take(120)) },
            label = { Text(s["social.signature"]) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            form.bio,
            { form = form.copy(bio = it.take(300)) },
            label = { Text(s["social.bio"]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${form.bio.length}/300",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = .55f),
            modifier = Modifier.align(Alignment.End),
        )
        Divider(
            Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = .08f),
        )
        Text(s["polish.socialInfo"], style = MaterialTheme.typography.subtitle1)
        OutlinedTextField(
            tags,
            { tags = it },
            label = { Text(s["social.tagsHint"]) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            form.city,
            { form = form.copy(city = it.take(40)) },
            label = { Text(s["social.city"]) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            form.intent,
            { form = form.copy(intent = it.take(120)) },
            label = { Text(s["social.intent"]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(s["social.cover"])
        Row {
            listOf("sunset", "ocean", "forest").forEach { value ->
                TextButton(
                    onClick = { form = form.copy(cover = value) },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor =
                                if (form.cover == value) MaterialTheme.colors.primary
                                else MaterialTheme.colors.onSurface.copy(alpha = .55f)
                        ),
                ) {
                    Text(s["social.$value"])
                }
            }
        }
        Divider(
            Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colors.onSurface.copy(alpha = .08f),
        )
        Text(s["social.visibility"])
        listOf("PUBLIC" to "public", "FRIENDS" to "friends", "SELF" to "self").forEach {
            (value, key) ->
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = form.visibility == value,
                        enabled = !busy,
                        role = Role.RadioButton,
                        onClick = { form = form.copy(visibility = value) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(form.visibility == value, null)
                Text(s["social.$key"], Modifier.padding(start = 12.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = form.discoverable,
                    enabled = !busy,
                    role = Role.Checkbox,
                    onValueChange = { form = form.copy(discoverable = it) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(form.discoverable, null)
            Text(s["social.discoverable"], Modifier.padding(start = 12.dp))
        }
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = form.mutualDiscovery,
                    enabled = !busy,
                    role = Role.Checkbox,
                    onValueChange = { form = form.copy(mutualDiscovery = it) },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(form.mutualDiscovery, null)
            Text(s["social.mutualDiscovery"], Modifier.padding(start = 12.dp))
        }
        Text(s["social.privacyHint"], style = MaterialTheme.typography.caption)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    onSave(
                        form.copy(
                            interests =
                                tags
                                    .split('、', ',', '，')
                                    .map(String::trim)
                                    .filter(String::isNotBlank)
                        )
                    )
                },
                enabled = !busy && form.username.isNotBlank(),
            ) {
                Text(s[if (busy) "polish.saving" else "social.save"])
            }
            TextButton(onClick = onCancel, enabled = !busy) { Text(s["social.cancel"]) }
        }
    }
}

@Composable
private fun DailyEditor(
    daily: DailyQuestion,
    answer: String,
    busy: Boolean,
    onEdit: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    val s = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(daily.question, style = MaterialTheme.typography.h6)
        Text(daily.day + " · " + s["social.dailyHint"], style = MaterialTheme.typography.caption)
        OutlinedTextField(
            answer,
            { onEdit(it.take(500)) },
            label = { Text(s["social.yourAnswer"]) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSave(answer) }, enabled = !busy) { Text(s["social.publish"]) }
        Text(s["social.answerPrivacy"], style = MaterialTheme.typography.caption)
    }
}

@Composable
private fun EncounterPanel(room: EncounterRoom, busy: Boolean, vm: SocialViewModel) {
    val s = LocalStrings.current
    var draft by remember(room.roomId) { mutableStateOf("") }
    var pending by remember(room.roomId) { mutableStateOf<Pair<String, String>?>(null) }
    var reporting by remember(room.roomId) { mutableStateOf(false) }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    var received by remember { mutableStateOf(System.currentTimeMillis()) }
    val messageList = androidx.compose.foundation.lazy.rememberLazyListState()
    var following by remember(room.roomId) { mutableStateOf(true) }
    var unseen by remember(room.roomId) { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(messageList) {
        snapshotFlow {
            messageList.isScrollInProgress to
                messageList.layoutInfo.let {
                    it.totalItemsCount == 0 ||
                        (it.visibleItemsInfo.lastOrNull()?.index == it.totalItemsCount - 1 &&
                            (it.visibleItemsInfo.lastOrNull()?.let { last ->
                                last.offset + last.size <= it.viewportEndOffset + 64
                            } ?: true))
                }
        }
            .collect { (scrolling, atEnd) ->
                if (scrolling) following = atEnd
                if (atEnd) unseen = 0
            }
    }
    LaunchedEffect(room.roomId, room.messages.lastOrNull()?.seq) {
        if (room.messages.isNotEmpty()) {
            if (following) messageList.scrollToItem(room.messages.lastIndex) else unseen++
        }
    }
    LaunchedEffect(room.serverTime) { received = System.currentTimeMillis() }
    LaunchedEffect(Unit) {
        while (true) {
            clock = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(room.messages) {
        if (pending != null && room.messages.any { it.messageId == pending?.first }) {
            if (draft.trim() == pending?.second) draft = ""
            pending = null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(s["social.encounterTitle"], style = MaterialTheme.typography.h6)
        Text(s["social.encounterHint"], style = MaterialTheme.typography.caption)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colors.surface,
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = .08f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s["social.room.${room.state}"],
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.subtitle2,
                )
                if (room.state == "ACTIVE") {
                    val seconds =
                        ((room.endsAt - room.serverTime - (clock - received)) / 1000).coerceAtLeast(
                            0
                        )
                    Text(
                        "${seconds/60}:${(seconds%60).toString().padStart(2,'0')}",
                        style = MaterialTheme.typography.h6,
                    )
                }
            }
        }
        when (room.state) {
            "IDLE",
            "ENDED" ->
                Button(onClick = { vm.act("join") }, enabled = !busy) {
                    Text(s["social.startMatch"])
                }
            "QUEUED" -> {
                Text(s["social.matchWait"])
                TextButton(onClick = { vm.act("cancel") }, enabled = !busy) {
                    Text(s["social.cancelMatch"])
                }
            }
            "PREPARING" ->
                Button(onClick = { vm.act("ready") }, enabled = !busy && !room.myReady) {
                    Text(s[if (room.myReady) "social.waitOther" else "social.ready"])
                }
        }
        if (room.reason.isNotBlank()) Text(s["social.end.${room.reason}"])
        if (room.roomId != null) {
            Text(room.question, color = MaterialTheme.colors.primary)
            if (room.messages.isEmpty())
                Text(
                    s[if (room.state == "PREPARING") "polish.readyHint" else "polish.firstMessage"],
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = .6f),
                )
            else
                LazyColumn(
                    state = messageList,
                    modifier = Modifier.fillMaxWidth().height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(room.messages, key = { it.seq }) { message ->
                        val mine = message.author == room.mySlot
                        Box(
                            Modifier.fillMaxWidth(),
                            contentAlignment =
                                if (mine) Alignment.CenterEnd else Alignment.CenterStart,
                        ) {
                            Surface(
                                Modifier.fillMaxWidth(.85f),
                                shape = RoundedCornerShape(12.dp),
                                color =
                                    if (mine) MaterialTheme.colors.primary.copy(alpha = .1f)
                                    else MaterialTheme.colors.surface,
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        s[if (mine) "social.you" else "social.other"],
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = .6f),
                                    )
                                    SelectionContainer { Text(message.content) }
                                }
                            }
                        }
                    }
                }
            if (unseen > 0)
                TextButton(
                    onClick = {
                        following = true
                        unseen = 0
                        scope.launch { messageList.animateScrollToItem(room.messages.lastIndex) }
                    }
                ) {
                    Text(s["polish.latestMessages"] + " ↓")
                }
            if (room.state == "ACTIVE") {
                var input by remember(room.roomId) { mutableStateOf(TextFieldValue(draft)) }
                SideEffect {
                    if (input.text != draft) input = TextFieldValue(draft, TextRange(draft.length))
                }
                val send = {
                    if (!busy && draft.isNotBlank()) {
                        val next = pending ?: (UUID.randomUUID().toString() to draft.trim())
                        pending = next
                        following = true
                        vm.act("send", next.first, next.second)
                    }
                }
                OutlinedTextField(
                    input,
                    { next ->
                        if (next.text.length <= 1000) {
                            input = next
                            draft = next.text
                            if (draft.trim() != pending?.second) pending = null
                        }
                    },
                    label = { Text(s["social.message"]) },
                    modifier =
                        Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                            val enterPressed =
                                event.type == KeyEventType.KeyDown && event.key == Key.Enter
                            val canSend = !busy && draft.isNotBlank()
                            if (
                                enterPressed &&
                                    shouldSubmitComposer(
                                        canSend,
                                        input.composition != null,
                                        event.isShiftPressed,
                                        event.isCtrlPressed,
                                        event.isAltPressed,
                                        event.isMetaPressed,
                                    )
                            ) {
                                send()
                                true
                            } else false
                        },
                    maxLines = 4,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${draft.length}/1000",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = .55f),
                    )
                    Button(onClick = send, enabled = !busy && draft.isNotBlank()) {
                        Text(s[if (busy) "polish.sending" else "social.send"])
                    }
                }
                if (room.extensions == 0)
                    TextButton(
                        onClick = { vm.act("extend") },
                        enabled = !busy && !room.extendRequested,
                    ) {
                        Text(s[if (room.extendRequested) "social.waitOther" else "social.extend"])
                    }
            }
            if (room.state == "ACTIVE" || room.reason == "EXPIRED")
                TextButton(
                    onClick = { vm.act("exchange") },
                    enabled = !busy && !room.exchangeRequested,
                ) {
                    Text(
                        s[
                            if (room.exchanged) "social.exchanged"
                            else if (room.exchangeRequested) "social.waitOther"
                            else "social.exchange"]
                    )
                }
            room.peer?.let { peer ->
                Button(onClick = { vm.profile(peer.id) }) {
                    Text(s["social.view"] + " · " + peer.username)
                }
            }
            if (room.state == "ACTIVE" || room.state == "PREPARING")
                TextButton(onClick = { vm.act("leave") }, enabled = !busy) {
                    Text(s["social.leave"])
                }
            TextButton(onClick = { reporting = !reporting }) { Text(s["social.reportAndLeave"]) }
            if (reporting) ReportEditor(busy) { vm.report(it) }
        }
    }
}

@Composable
private fun ReportEditor(busy: Boolean, onSubmit: (String) -> Unit) {
    val s = LocalStrings.current
    var reason by remember { mutableStateOf("") }
    OutlinedTextField(
        reason,
        { reason = it.take(500) },
        label = { Text(s["social.reportReason"]) },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onSubmit(reason) }, enabled = !busy && reason.isNotBlank()) {
        Text(s["social.submit"])
    }
}
