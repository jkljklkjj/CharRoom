package component.user

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatlite.i18n.LocalStrings
import component.QingliaoColors
import component.UserAvatar
import component.sidebarHeaderBrush
import model.User

/** Stateless presentation shared by the live list and the offline design preview. */
@Composable
fun PaperConversationHeader(
    currentUser: User?,
    onlineSummary: String,
    hasPendingApplications: Boolean,
    onOpenProfile: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenApplications: () -> Unit,
    onOpenSettings: () -> Unit,
    sectionLabel: String = LocalStrings.current["paper.conversations"]
) {
    val s = LocalStrings.current
    val p = QingliaoColors
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(sidebarHeaderBrush(!MaterialTheme.colors.isLight), RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(8.dp))
            Text(s["paper.brand"], style = MaterialTheme.typography.subtitle1, color = p.action, modifier = Modifier.weight(1f))
            if (currentUser != null) UserAvatar(currentUser, size = 44.dp, onClick = onOpenProfile)
            else IconButton(onClick = onOpenProfile, modifier = Modifier.size(48.dp)) {
                Text(s["chat.me"], style = MaterialTheme.typography.caption, color = p.action)
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Settings, s["settings.title"], tint = p.muted, modifier = Modifier.size(21.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(s["paper.greeting"], style = MaterialTheme.typography.h5, color = p.ink)
        Spacer(Modifier.height(8.dp))
        Text(onlineSummary, style = MaterialTheme.typography.caption, color = p.muted)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(sectionLabel, style = MaterialTheme.typography.caption, color = p.muted, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSearch, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.PersonAdd, s["contact.search.add"], tint = p.action, modifier = Modifier.size(22.dp))
            }
            Box {
                IconButton(onClick = onOpenApplications, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Notifications, s["contact.applications"], tint = p.muted, modifier = Modifier.size(21.dp))
                }
                if (hasPendingApplications) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(6.dp).background(p.action, RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
fun PaperSidebarTabs(selectedTab: SidebarTab, onTabSelected: (SidebarTab) -> Unit) {
    val p = QingliaoColors
    val s = LocalStrings.current
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SidebarTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val background by animateColorAsState(if (selected) p.apricot else Color.Transparent)
            Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(background)
                .selectable(selected = selected, role = Role.Tab, onClick = { onTabSelected(tab) })
                .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(s[if (tab == SidebarTab.MESSAGES) "contacts.tab.messages" else "contacts.tab.contacts"],
                    style = MaterialTheme.typography.subtitle2, color = if (selected) p.onApricot else p.muted)
            }
        }
    }
}

@Composable
fun PaperContactSection(title: String, count: Int) {
    Text("$title · $count", Modifier.padding(start = 12.dp, top = 16.dp, bottom = 6.dp),
        style = MaterialTheme.typography.caption, color = QingliaoColors.muted)
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PaperConversationRow(
    user: User,
    subtitle: String,
    time: String,
    unreadCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onAvatarClick: (() -> Unit)? = null
) {
    val p = QingliaoColors
    val s = LocalStrings.current
    val background by animateColorAsState(if (selected) p.apricot else Color.Transparent)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(background).semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(if (onAvatarClick != null) Modifier.clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClickLabel = s["user.avatar"], onClick = onAvatarClick, onLongClick = onAvatarClick) else Modifier) {
            UserAvatar(user, size = 48.dp)
            user.online?.let { online ->
                val status = s[if (online) "chat.status.online" else "chat.status.offline"]
                Box(Modifier.align(Alignment.BottomEnd).size(12.dp).background(p.paper, RoundedCornerShape(6.dp)).padding(3.dp)
                    .background(if (online) Color(0xFF56845E) else p.muted, RoundedCornerShape(4.dp)).semantics { contentDescription = status })
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(user.username, style = MaterialTheme.typography.subtitle2, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.body2, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (time.isNotBlank() || unreadCount > 0) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (time.isNotBlank()) Text(time, style = MaterialTheme.typography.overline, color = p.muted)
            if (unreadCount > 0) Surface(color = p.action, contentColor = p.onAction, shape = RoundedCornerShape(6.dp)) {
                Text(if (unreadCount > 99) "99+" else unreadCount.toString(), Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.overline)
            }
        }
    }
}

@Composable
fun PaperDiscoveryEntry(onClick: () -> Unit) {
    val s = LocalStrings.current
    val p = QingliaoColors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.apricot)
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Explore, null, tint = p.onApricot)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(s["social.discover"], style = MaterialTheme.typography.subtitle2, color = p.onApricot)
            Text(s["polish.discoverySubtitle"], style = MaterialTheme.typography.caption, color = p.muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.onApricot)
    }
}
