package presentation.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import model.EncounterMessage
import model.EncounterRoom

/** Merge the current room only; a blocked or replaced room cannot inherit old messages. */
internal class EncounterRoomState(private val mutable: MutableStateFlow<SocialUiState>) {
    fun merge(room: EncounterRoom) {
        mutable.update { old ->
            val messages =
                if (room.roomId == old.room.roomId && room.reason != "BLOCKED")
                    (old.room.messages + room.messages)
                        .distinctBy(EncounterMessage::seq)
                        .sortedBy(EncounterMessage::seq)
                else room.messages
            old.copy(room = room.copy(messages = messages))
        }
    }
}
