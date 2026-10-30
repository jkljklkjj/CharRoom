package core

import com.chatlite.i18n.currentStrings
import java.io.File

object DesktopLocalChatHistoryStore :
    FileLocalChatHistoryStore(
        { File(System.getProperty("user.home"), ".qingliao/history") },
        { currentStrings["contact.unknown"] },
    )
