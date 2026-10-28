package component.user

import model.User
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ContactDirectoryTest {
    private val users = listOf(User(0, "Assistant"), User(7, "Me"), User(21, "Zoe"), User(21, "Walking", conversationKind = model.ConversationKind.GROUP), User(22, "Alice"))

    @Test fun directorySeparatesFriendsAndGroupsAndExcludesSelfAndAssistant() {
        val directory = buildContactDirectory(users, 7, Locale.ENGLISH)
        assertEquals(listOf(22, 21), directory.friends.map { it.id })
        assertEquals(listOf(21), directory.groups.map { it.id })
        assertEquals(21, directory.groups.single().asContactGroup()?.id)
        assertNull(directory.friends.first().asContactGroup())
    }

    @Test fun searchMatchesNamesAndPositiveGroupIdsWithoutChangingSourceOrder() {
        assertEquals(listOf(22), buildContactDirectory(users, 7, Locale.ENGLISH).filtered(" ALI ").friends.map { it.id })
        val directory = buildContactDirectory(users, 7, Locale.ENGLISH).filtered("21")
        assertEquals(listOf(21), directory.friends.map { it.id })
        assertEquals(listOf(21), directory.groups.map { it.id })
        assertEquals(listOf(0, 7, 21, 21, 22), users.map { it.id })
        assertEquals(0, buildContactDirectory(users.take(2), 7, Locale.ENGLISH).size)
    }

    @Test fun cachedDirectoryFilteringRetainsOrderAndBlankSearchReusesTheDirectory() {
        val directory = buildContactDirectory(users, 7, Locale.ENGLISH)
        assertSame(directory, directory.filtered("  "))
        assertEquals(listOf(22,21), directory.filtered("  ").friends.map { it.id })
        assertEquals(listOf(22), directory.filtered("ALI").friends.map { it.id })
        assertEquals(listOf(21), directory.filtered("21").groups.map { it.id })
        assertEquals(listOf(22,21), directory.friends.map { it.id })
    }

    @Test fun conversationRankingReadsEachActivityOnceAndKeepsTiesAndKindsDistinct() {
        val peers = users.drop(2)
        val calls = mutableListOf<model.ConversationKey>()
        val sorted = sortConversationUsers(peers) { key ->
            calls.add(key)
            if (key == model.ConversationKey.group(21)) 10L to 1 else 0L to 0
        }
        assertEquals(peers.map { it.conversationKey }, calls)
        assertEquals(listOf(peers[1],peers[0],peers[2]), sorted)
    }
}
