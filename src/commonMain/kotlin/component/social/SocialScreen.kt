package component.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import data.repository.SocialRepository
import model.*
import presentation.viewmodel.SocialViewModel
import java.util.UUID
import component.UserAvatar

@Composable
fun SocialScreen(token: String, userId: String, onBack: () -> Unit, onChat: (User) -> Unit, initialProfileId: String? = null) {
    val vm=remember(token,userId) { SocialViewModel(SocialRepository(token),userId) }
    DisposableEffect(vm) { onDispose { vm.close() } }
    LaunchedEffect(vm,initialProfileId) { initialProfileId?.let(vm::profile) }
    val state by vm.state.collectAsState()
    val s=LocalStrings.current
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("all") }
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colors.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text(s["social.discover"],style=MaterialTheme.typography.h6)
                TextButton(onClick=onBack) { Text(s["social.close"]) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp)) {
                listOf("people","encounter","daily","me").forEach { tab ->
                    TextButton(onClick={ vm.tab(tab) },modifier=Modifier.weight(1f)) { Text(s["social.$tab"],color=if(state.tab==tab)MaterialTheme.colors.primary else MaterialTheme.colors.onSurface) }
                }
            }
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                if(state.error.isNotBlank()) item { Text(s["social.failed"]+" "+state.error,color=MaterialTheme.colors.error) }
                val profile=state.profile
                if(profile!=null) {
                    item { TextButton(onClick=vm::closeProfile) { Text(s["social.back"]) } }
                    item { SocialProfilePanel(profile,state.busy,vm,onChat) }
                    if(profile.relation=="SELF" && state.blocked.isNotEmpty()) {
                        item { Text(s["social.blockedList"]) }
                        items(state.blocked,key={it.id}) { person -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(person.username);TextButton(onClick={vm.unblock(person.id)},enabled=!state.busy) { Text(s["social.unblock"]) } } }
                    }
                } else when(state.tab) {
                    "people" -> {
                        item {
                            OutlinedTextField(query,{query=it},label={Text(s["social.search"])},modifier=Modifier.fillMaxWidth(),singleLine=true)
                            OutlinedTextField(tag,{tag=it},label={Text(s["social.tags"])},modifier=Modifier.fillMaxWidth(),singleLine=true)
                            Row { listOf("all","mutual","city").forEach { value -> TextButton(onClick={channel=value;vm.search(query,tag,channel)}) { Text(s["social.$value"],color=if(channel==value)MaterialTheme.colors.primary else MaterialTheme.colors.onSurface) } } }
                            Button(onClick={vm.search(query,tag,channel)},enabled=!state.busy) { Text(s["social.search"]) }
                            Text(s["social.discoveryHint"],style=MaterialTheme.typography.caption)
                        }
                        if(state.people.isEmpty()&&!state.busy)item { Text(s["social.empty"]) }
                        items(state.people,key={it.id}) { person ->
                            Card(Modifier.fillMaxWidth(),elevation=1.dp) { Column(Modifier.padding(16.dp)) {
                                Text(person.username,style=MaterialTheme.typography.h6)
                                person.signature?.let { Text(it) }
                                Text(person.interests.orEmpty().joinToString(" · "),style=MaterialTheme.typography.caption)
                                Text(s["social.reason.${person.reason ?: "EXPLORE"}"],style=MaterialTheme.typography.caption)
                                TextButton(onClick={vm.profile(person.id)}) { Text(s["social.view"]) }
                            } }
                        }
                        if(state.cursor!=null)item { TextButton(onClick={vm.search(query,tag,channel,true)},enabled=!state.busy) { Text(s["social.more"]) } }
                    }
                    "encounter" -> item { EncounterPanel(state.room,state.busy,vm) }
                    "daily" -> {
                        val daily=state.daily
                        if(daily!=null) {
                            item { DailyEditor(daily,state.busy,vm::answer) }
                            items(daily.answers,key={it.id}) { answer -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                                Text(answer.content);TextButton(onClick={vm.profile(answer.author.id)}) { Text(answer.author.username+" · "+s["social.view"]) }
                            } } }
                            if(daily.nextCursor!=null)item { TextButton(onClick={vm.daily(true)}) { Text(s["social.more"]) } }
                            if(daily.answers.isEmpty())item { Text(s["social.noAnswers"]) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialProfilePanel(profile: SocialProfile,busy: Boolean,vm: SocialViewModel,onChat: (User)->Unit) {
    val s=LocalStrings.current
    var editing by remember(profile) { mutableStateOf(false) }
    var requesting by remember(profile.id) { mutableStateOf(false) }
    var reporting by remember(profile.id) { mutableStateOf(false) }
    var reason by remember(profile.id) { mutableStateOf("") }
    var audience by remember(profile.id) { mutableStateOf("self") }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        val coverColors=when(profile.cover) {
            "ocean" -> listOf(MaterialTheme.colors.primaryVariant,MaterialTheme.colors.primary)
            "forest" -> listOf(MaterialTheme.colors.secondaryVariant,MaterialTheme.colors.secondary)
            else -> listOf(MaterialTheme.colors.primary,MaterialTheme.colors.secondary)
        }
        Box(Modifier.fillMaxWidth().height(70.dp).background(Brush.horizontalGradient(coverColors)))
        Text(profile.username,style=MaterialTheme.typography.h5)
        UserAvatar(profile.asUser(),size=64.dp)
        Text(s["social.account"]+" "+profile.id+" · "+s["social.relation.${profile.relation}"],style=MaterialTheme.typography.caption)
        profile.signature?.takeIf { it.isNotBlank() }?.let { Text(it) }
        if(editing) {
            ProfileEditor(profile,busy,vm::save) { editing=false }
        } else {
            if(profile.relation=="SELF") {
                Text(s["social.preview"])
                Row { listOf("self","stranger","friends").forEach { value -> TextButton(onClick={audience=value}) { Text(s["social.$value"]) } } }
            }
            val visible=profile.detailsVisible && (profile.relation!="SELF" || audience=="self" || profile.settings?.visibility=="PUBLIC" || (audience=="friends"&&profile.settings?.visibility=="FRIENDS"))
            if(visible) {
                profile.bio?.takeIf { it.isNotBlank() }?.let { Text(s["social.bio"],style=MaterialTheme.typography.subtitle1);Text(it) }
                Text(profile.interests.orEmpty().joinToString(" · "),color=MaterialTheme.colors.primary)
                profile.city?.takeIf { it.isNotBlank() }?.let { Text(s["social.city"]+" · "+it) }
                profile.intent?.takeIf { it.isNotBlank() }?.let { Text(s["social.intent"],style=MaterialTheme.typography.subtitle1);Text(it) }
            } else Text(s["social.hidden"])
            when(profile.relation) {
                "SELF" -> Button(onClick={editing=true}) { Text(s["social.edit"]) }
                "FRIEND" -> Button(onClick={onChat(profile.asUser())}) { Text(s["social.chat"]) }
                "PENDING" -> Text(s["social.pending"])
                else -> Button(onClick={requesting=!requesting},enabled=!busy) { Text(s["social.add"]) }
            }
            if(requesting&&profile.relation!="PENDING") {
                OutlinedTextField(reason,{reason=it.take(120)},label={Text(s["social.requestReason"])},modifier=Modifier.fillMaxWidth())
                Button(onClick={vm.friend(reason)},enabled=!busy) { Text(s["social.sendRequest"]) }
            }
            if(profile.relation!="SELF") {
                Row { TextButton(onClick=vm::block,enabled=!busy) { Text(s["social.block"]) };TextButton(onClick={reporting=!reporting}) { Text(s["social.report"]) } }
                if(reporting) ReportEditor(busy) { vm.report(it) }
            }
        }
    }
}

@Composable
private fun ProfileEditor(profile: SocialProfile,busy: Boolean,onSave: (SocialEdit)->Unit,onCancel: ()->Unit) {
    val s=LocalStrings.current
    val settings=profile.settings ?: SocialSettings()
    var form by remember(profile) { mutableStateOf(SocialEdit(profile.username,profile.signature.orEmpty(),settings.bio,settings.interests,settings.city,settings.intent,settings.cover,settings.visibility,settings.discoverable,settings.mutualDiscovery)) }
    var tags by remember(profile) { mutableStateOf(settings.interests.joinToString("、")) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(form.username,{form=form.copy(username=it.take(32))},label={Text(s["social.username"])},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(form.signature,{form=form.copy(signature=it.take(120))},label={Text(s["social.signature"])},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(form.bio,{form=form.copy(bio=it.take(300))},label={Text(s["social.bio"])},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(tags,{tags=it},label={Text(s["social.tagsHint"])},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(form.city,{form=form.copy(city=it.take(40))},label={Text(s["social.city"])},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(form.intent,{form=form.copy(intent=it.take(120))},label={Text(s["social.intent"])},modifier=Modifier.fillMaxWidth())
        Text(s["social.cover"])
        Row { listOf("sunset","ocean","forest").forEach { value -> TextButton(onClick={form=form.copy(cover=value)}) { Text(s["social.$value"]) } } }
        Text(s["social.visibility"])
        listOf("PUBLIC" to "public","FRIENDS" to "friends","SELF" to "self").forEach { (value,key) -> Row { RadioButton(form.visibility==value,{form=form.copy(visibility=value)});Text(s["social.$key"],Modifier.padding(top=12.dp)) } }
        Row { Checkbox(form.discoverable,{form=form.copy(discoverable=it)});Text(s["social.discoverable"],Modifier.padding(top=12.dp)) }
        Row { Checkbox(form.mutualDiscovery,{form=form.copy(mutualDiscovery=it)});Text(s["social.mutualDiscovery"],Modifier.padding(top=12.dp)) }
        Text(s["social.privacyHint"],style=MaterialTheme.typography.caption)
        Row { Button(onClick={onSave(form.copy(interests=tags.split('、',',','，').map(String::trim).filter(String::isNotBlank)))},enabled=!busy&&form.username.isNotBlank()) { Text(s["social.save"]) };TextButton(onClick=onCancel) { Text(s["social.cancel"]) } }
    }
}

@Composable
private fun DailyEditor(daily: DailyQuestion,busy: Boolean,onSave: (String)->Unit) {
    val s=LocalStrings.current
    var answer by remember(daily.day,daily.myAnswer) { mutableStateOf(daily.myAnswer) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(daily.question,style=MaterialTheme.typography.h6)
        Text(daily.day+" · "+s["social.dailyHint"],style=MaterialTheme.typography.caption)
        OutlinedTextField(answer,{answer=it.take(500)},label={Text(s["social.yourAnswer"])},modifier=Modifier.fillMaxWidth())
        Button(onClick={onSave(answer)},enabled=!busy) { Text(s["social.publish"]) }
        Text(s["social.answerPrivacy"],style=MaterialTheme.typography.caption)
    }
}

@Composable
private fun EncounterPanel(room: EncounterRoom,busy: Boolean,vm: SocialViewModel) {
    val s=LocalStrings.current
    var draft by remember(room.roomId) { mutableStateOf("") }
    var pending by remember(room.roomId) { mutableStateOf<Pair<String,String>?>(null) }
    var reporting by remember(room.roomId) { mutableStateOf(false) }
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    var received by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(room.serverTime) { received=System.currentTimeMillis() }
    LaunchedEffect(Unit) { while(true) { clock=System.currentTimeMillis();kotlinx.coroutines.delay(1000) } }
    LaunchedEffect(room.messages) { if(pending!=null&&room.messages.any { it.messageId==pending?.first }) { if(draft.trim()==pending?.second)draft="";pending=null } }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(s["social.encounterTitle"],style=MaterialTheme.typography.h6)
        Text(s["social.encounterHint"],style=MaterialTheme.typography.caption)
        Text(s["social.room.${room.state}"])
        if(room.state=="ACTIVE") { val seconds=((room.endsAt-room.serverTime-(clock-received))/1000).coerceAtLeast(0);Text("${seconds/60}:${(seconds%60).toString().padStart(2,'0')}") }
        when(room.state) {
            "IDLE","ENDED" -> Button(onClick={vm.act("join")},enabled=!busy) { Text(s["social.startMatch"]) }
            "QUEUED" -> { Text(s["social.matchWait"]);TextButton(onClick={vm.act("cancel")},enabled=!busy) { Text(s["social.cancelMatch"]) } }
            "PREPARING" -> Button(onClick={vm.act("ready")},enabled=!busy&&!room.myReady) { Text(s[if(room.myReady)"social.waitOther" else "social.ready"]) }
        }
        if(room.reason.isNotBlank())Text(s["social.end.${room.reason}"])
        if(room.roomId!=null) {
            Text(room.question,color=MaterialTheme.colors.primary)
            room.messages.forEach { message -> Card(Modifier.fillMaxWidth(),backgroundColor=if(message.author==room.mySlot)MaterialTheme.colors.primary.copy(alpha=.12f) else MaterialTheme.colors.surface) { Column(Modifier.padding(12.dp)) { Text(s[if(message.author==room.mySlot)"social.you" else "social.other"],style=MaterialTheme.typography.caption);Text(message.content) } } }
            if(room.state=="ACTIVE") {
                OutlinedTextField(draft,{draft=it.take(1000);if(it.trim()!=pending?.second)pending=null},label={Text(s["social.message"])},modifier=Modifier.fillMaxWidth())
                Button(onClick={val next=pending ?: (UUID.randomUUID().toString() to draft.trim());pending=next;vm.act("send",next.first,next.second)},enabled=!busy&&draft.isNotBlank()) { Text(s["social.send"]) }
                if(room.extensions==0)TextButton(onClick={vm.act("extend")},enabled=!busy&&!room.extendRequested) { Text(s[if(room.extendRequested)"social.waitOther" else "social.extend"]) }
            }
            if(room.state=="ACTIVE" || room.reason=="EXPIRED")TextButton(onClick={vm.act("exchange")},enabled=!busy&&!room.exchangeRequested) { Text(s[if(room.exchanged)"social.exchanged" else if(room.exchangeRequested)"social.waitOther" else "social.exchange"]) }
            room.peer?.let { peer -> Button(onClick={vm.profile(peer.id)}) { Text(s["social.view"]+" · "+peer.username) } }
            if(room.state=="ACTIVE" || room.state=="PREPARING")TextButton(onClick={vm.act("leave")},enabled=!busy) { Text(s["social.leave"]) }
            TextButton(onClick={reporting=!reporting}) { Text(s["social.reportAndLeave"]) }
            if(reporting)ReportEditor(busy) { vm.report(it) }
        }
    }
}

@Composable
private fun ReportEditor(busy: Boolean,onSubmit: (String)->Unit) {
    val s=LocalStrings.current
    var reason by remember { mutableStateOf("") }
    OutlinedTextField(reason,{reason=it.take(500)},label={Text(s["social.reportReason"])},modifier=Modifier.fillMaxWidth())
    Button(onClick={onSubmit(reason)},enabled=!busy&&reason.isNotBlank()) { Text(s["social.submit"]) }
}
