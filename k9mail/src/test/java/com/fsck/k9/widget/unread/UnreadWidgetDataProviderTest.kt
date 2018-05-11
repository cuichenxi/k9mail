package com.fsck.k9.widget.unread

import android.content.Context
import com.fsck.k9.Account
import com.fsck.k9.AccountStats
import com.fsck.k9.Preferences
import com.fsck.k9.RobolectricTest
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.search.SearchAccount
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.robolectric.RuntimeEnvironment


class UnreadWidgetDataProviderTest : RobolectricTest() {
    val context: Context = RuntimeEnvironment.application
    val account = createAccount()
    val preferences = createPreferences()
    val messagingController = createMessagingController()
    val provider = UnreadWidgetDataProvider(context, preferences, messagingController)


    @Test
    fun unifiedInbox() {
        val configuration = UnreadWidgetConfiguration(
                appWidgetId = 1, accountUuid = SearchAccount.UNIFIED_INBOX, folderServerId = null)

        val widgetData = provider.loadUnreadWidgetData(configuration)

        with(widgetData!!) {
            assertThat(title).isEqualTo("Unified Inbox")
            assertThat(unreadCount).isEqualTo(SEARCH_ACCOUNT_UNREAD_COUNT)
        }
    }

    @Test
    fun allMessages() {
        val configuration = UnreadWidgetConfiguration(
                appWidgetId = 2, accountUuid = SearchAccount.ALL_MESSAGES, folderServerId = null)

        val widgetData = provider.loadUnreadWidgetData(configuration)

        with(widgetData!!) {
            assertThat(title).isEqualTo("All messages")
            assertThat(unreadCount).isEqualTo(SEARCH_ACCOUNT_UNREAD_COUNT)
        }
    }

    @Test
    fun regularAccount() {
        val configuration = UnreadWidgetConfiguration(
                appWidgetId = 3, accountUuid = ACCOUNT_UUID, folderServerId = null)

        val widgetData = provider.loadUnreadWidgetData(configuration)

        with(widgetData!!) {
            assertThat(title).isEqualTo(ACCOUNT_DESCRIPTION)
            assertThat(unreadCount).isEqualTo(ACCOUNT_UNREAD_COUNT)
        }
    }

    @Test
    fun folder() {
        val configuration = UnreadWidgetConfiguration(
                appWidgetId = 4, accountUuid = ACCOUNT_UUID, folderServerId = FOLDER_SERVER_ID)

        val widgetData = provider.loadUnreadWidgetData(configuration)

        with(widgetData!!) {
            assertThat(title).isEqualTo("$ACCOUNT_DESCRIPTION - $FOLDER_SERVER_ID")
            assertThat(unreadCount).isEqualTo(FOLDER_UNREAD_COUNT)
        }
    }

    @Test
    fun nonExistentAccount_shouldReturnNull() {
        val configuration = UnreadWidgetConfiguration(appWidgetId = 3, accountUuid = "invalid", folderServerId = null)

        val widgetData = provider.loadUnreadWidgetData(configuration)

        assertThat(widgetData).isNull()
    }


    fun createAccount(): Account = mock {
        on { uuid } doReturn ACCOUNT_UUID
        on { description } doReturn ACCOUNT_DESCRIPTION
    }

    fun createPreferences(): Preferences = mock {
        on { getAccount(ACCOUNT_UUID) } doReturn account
    }

    fun createMessagingController(): MessagingController = mock {
        on { getSearchAccountStatsSynchronous(any(), anyOrNull()) } doReturn SEARCH_ACCOUNT_STATS
        on { getAccountStats(account) } doReturn ACCOUNT_STATS
        on { getFolderUnreadMessageCount(eq(account), eq(FOLDER_SERVER_ID)) } doReturn FOLDER_UNREAD_COUNT
    }

    companion object {
        const val ACCOUNT_UUID = "00000000-0000-0000-0000-000000000000"
        const val ACCOUNT_DESCRIPTION = "Test account"
        const val FOLDER_SERVER_ID = "[folderServerId]"
        const val SEARCH_ACCOUNT_UNREAD_COUNT = 1
        const val ACCOUNT_UNREAD_COUNT = 2
        const val FOLDER_UNREAD_COUNT = 3

        val SEARCH_ACCOUNT_STATS = AccountStats().apply {
            unreadMessageCount = SEARCH_ACCOUNT_UNREAD_COUNT
        }
        val ACCOUNT_STATS = AccountStats().apply {
            unreadMessageCount = ACCOUNT_UNREAD_COUNT
        }
    }
}
