package org.librehu.dialer.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import java.util.Locale

data class LabeledNumber(
    val number: String,
    val label: String,
)

data class Contact(
    val id: Long,
    val name: String,
    val numbers: List<LabeledNumber>,
    val starred: Boolean,
    val photo: Uri?,
) {
    val initials get() = initialsFor(name)
    val primary get() = numbers.first()
}

enum class RecentType { INCOMING, OUTGOING, MISSED, REJECTED, OTHER }

/** Consecutive calls with the same number and type are grouped ([count]). */
data class RecentCall(
    val number: String,
    /** Cached contact name, empty when unknown. */
    val name: String,
    val type: RecentType,
    val date: Long,
    val durationS: Long,
    val count: Int = 1,
) {
    val title get() = name.ifBlank { number }
}

/**
 * Contacts and call log of Android's providers. On a head unit they come from the phone: Android's PBAP client
 * downloads its phone book and call history there (Jancar's btservice and LibreHU-service read them back too).
 */
object PhoneBook {
    fun canReadContacts(context: Context) = granted(context, Manifest.permission.READ_CONTACTS)

    fun canReadCallLog(context: Context) = granted(context, Manifest.permission.READ_CALL_LOG)

    fun contacts(context: Context): List<Contact> {
        if (!canReadContacts(context)) return emptyList()
        val byId = LinkedHashMap<Long, Contact>()
        val projection =
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.STARRED,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            )
        runCatching {
            context.contentResolver
                .query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val number = c.getString(2)?.trim().orEmpty()
                        if (number.isBlank()) continue
                        val id = c.getLong(0)
                        val label =
                            ContactsContract.CommonDataKinds.Phone
                                .getTypeLabel(context.resources, c.getInt(3), c.getString(4))
                                .toString()
                        val n = LabeledNumber(number, label)
                        val old = byId[id]
                        byId[id] =
                            if (old == null) {
                                Contact(
                                    id = id,
                                    name =
                                        c
                                            .getString(1)
                                            ?.trim()
                                            .orEmpty()
                                            .ifBlank { number },
                                    numbers = listOf(n),
                                    starred = c.getInt(5) != 0,
                                    photo = c.getString(6)?.let(Uri::parse),
                                )
                            } else if (old.numbers.none { same(it.number, number) }) {
                                old.copy(numbers = old.numbers + n)
                            } else {
                                old
                            }
                    }
                }
        }
        return byId.values.toList()
    }

    fun recents(
        context: Context,
        limit: Int = 300,
    ): List<RecentCall> {
        if (!canReadCallLog(context)) return emptyList()
        val out = mutableListOf<RecentCall>()
        val projection =
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
        runCatching {
            context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC")?.use { c ->
                var rows = 0
                while (c.moveToNext() && rows++ < limit) {
                    val number = c.getString(0)?.trim().orEmpty()
                    val type =
                        when (c.getInt(2)) {
                            CallLog.Calls.INCOMING_TYPE -> RecentType.INCOMING
                            CallLog.Calls.OUTGOING_TYPE -> RecentType.OUTGOING
                            CallLog.Calls.MISSED_TYPE -> RecentType.MISSED
                            CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> RecentType.REJECTED
                            else -> RecentType.OTHER
                        }
                    val call = RecentCall(number, c.getString(1)?.trim().orEmpty(), type, c.getLong(3), c.getLong(4))
                    val last = out.lastOrNull()
                    if (last != null && last.type == type && same(last.number, number)) {
                        out[out.size - 1] = last.copy(count = last.count + 1)
                    } else {
                        out += call
                    }
                }
            }
        }
        return out
    }

    /** Contact name of [number] (Android's PhoneLookup), empty when unknown. */
    fun lookupName(
        context: Context,
        number: String,
    ): String {
        if (number.isBlank() || !canReadContacts(context)) return ""
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0).orEmpty() else ""
            }
        }.getOrNull().orEmpty()
    }

    /** Most called numbers of the log, for the favourites when no contact is starred. */
    fun frequent(
        recents: List<RecentCall>,
        count: Int,
    ): List<RecentCall> =
        recents
            .filter { it.number.isNotBlank() && it.type != RecentType.MISSED }
            .groupBy { normalize(it.number) }
            .values
            .sortedByDescending { group -> group.sumOf { it.count } }
            .take(count)
            .map { it.first() }

    fun same(
        a: String,
        b: String,
    ): Boolean = normalize(a) == normalize(b) || PhoneNumberUtils.compare(a, b)

    fun normalize(n: String): String = n.filter { it.isDigit() || it == '+' }

    private fun granted(
        context: Context,
        permission: String,
    ) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

fun initialsFor(name: String): String =
    name
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it.first().isLetter() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "#" }
        .uppercase(Locale.getDefault())
