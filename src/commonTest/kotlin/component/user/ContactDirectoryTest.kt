package component.user

import model.User
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactDirectoryTest {
    private val users = listOf(User(0, "Assistant"), User(7, "Me"), User(21, "Zoe"), User(-21, "Walking"), User(22, "Alice"))

    @Test fun directorySeparatesFriendsAndGroupsAndExcludesSelfAndAssistant() {
        val directory = buildContactDirectory(users, 7, "", Locale.ENGLISH)
        assertEquals(listOf(22, 21), directory.friends.map { it.id })
        assertEquals(listOf(-21), directory.groups.map { it.id })
        assertEquals(21, directory.groups.single().asContactGroup()?.id)
        assertNull(directory.friends.first().asContactGroup())
    }

    @Test fun searchMatchesNamesAndPositiveGroupIdsWithoutChangingSourceOrder() {
        assertEquals(listOf(22), buildContactDirectory(users, 7, " ALI ", Locale.ENGLISH).friends.map { it.id })
        val directory = buildContactDirectory(users, 7, "21", Locale.ENGLISH)
        assertEquals(listOf(21), directory.friends.map { it.id })
        assertEquals(listOf(-21), directory.groups.map { it.id })
        assertEquals(listOf(0, 7, 21, -21, 22), users.map { it.id })
        assertEquals(0, buildContactDirectory(users.take(2), 7, "", Locale.ENGLISH).size)
    }
}
