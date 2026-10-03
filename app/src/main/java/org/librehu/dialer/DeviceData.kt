package org.librehu.dialer

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun initialsFor(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "?" }

internal fun readDeviceContacts(context: Context): List<Person> {
    val result = mutableListOf<Person>()
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.TYPE
    )
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC"
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val typeIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
        while (cursor.moveToNext()) {
            val name = cursor.getString(nameIndex)?.trim().orEmpty().ifBlank { "Unknown contact" }
            val number = cursor.getString(numberIndex)?.trim().orEmpty()
            if (number.isNotBlank()) {
                val type = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, cursor.getInt(typeIndex), null).toString()
                result += Person(name, number, initialsFor(name), type)
            }
        }
    }
    return result
}

internal fun readDeviceCallLog(context: Context): List<Person> {
    val result = mutableListOf<Person>()
    val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION)
    context.contentResolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, CallLog.Calls.DATE + " DESC")?.use { cursor ->
        val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
        val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
        val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
        val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
        val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
        val dateFormat = SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault())
        while (cursor.moveToNext()) {
            val number = cursor.getString(numberIndex)?.trim().orEmpty().ifBlank { "Unknown number" }
            val name = cursor.getString(nameIndex)?.trim().orEmpty().ifBlank { number }
            val type = when (cursor.getInt(typeIndex)) {
                CallLog.Calls.INCOMING_TYPE -> "Incoming"
                CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                CallLog.Calls.MISSED_TYPE -> "Missed"
                CallLog.Calls.REJECTED_TYPE -> "Rejected"
                CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                CallLog.Calls.VOICEMAIL_TYPE -> "Voicemail"
                else -> "Call"
            }
            val date = dateFormat.format(Date(cursor.getLong(dateIndex)))
            val seconds = cursor.getLong(durationIndex)
            val duration = if (seconds >= 3600) String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
                else String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
            result += Person(name, number, initialsFor(name), "$type · $date · $duration")
        }
    }
    return result
}
