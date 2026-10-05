package org.librehu.dialer.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun Heading(
    title: String,
    subtitle: String? = null,
) {
    Column(Modifier.padding(start = 4.dp, bottom = 10.dp, top = 2.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = DialerColors.Text)
        if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = DialerColors.Muted)
    }
}

/** Contact picture, or initials on the raised colour. */
@Composable
fun Avatar(
    initials: String,
    size: Int,
    photo: Uri? = null,
) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, photo) {
        value =
            photo?.let { uri ->
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                    }.getOrNull()
                }
            }
    }
    Box(Modifier.size(size.dp).clip(CircleShape).background(DialerColors.Raised), contentAlignment = Alignment.Center) {
        val img = image
        if (img != null) {
            Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size.dp))
        } else {
            Text(initials, color = DialerColors.Accent, fontSize = (size / 2.8).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** List row: avatar, two lines, a call button. */
@Composable
fun PersonRow(
    title: String,
    subtitle: String,
    initials: String,
    photo: Uri? = null,
    leading: (@Composable () -> Unit)? = null,
    titleColor: Color = DialerColors.Text,
    onClick: () -> Unit,
    onCall: () -> Unit,
    callLabel: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(initials, 52, photo)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = titleColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Text(subtitle, color = DialerColors.Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        RoundButton(Icons.Default.Call, callLabel, DialerColors.Raised, DialerColors.Accent, 56, onCall)
    }
}

@Composable
fun RoundButton(
    icon: ImageVector,
    label: String,
    background: Color,
    tint: Color,
    size: Int,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size((size * 0.45).dp))
    }
}

/** Round button with its label under it (in-call actions). */
@Composable
fun CallAction(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    background: Color? = null,
    size: Int = 72,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundButton(
            icon,
            label,
            background ?: if (selected) DialerColors.Accent else DialerColors.Raised,
            if (background != null) {
                Color.White
            } else if (selected) {
                DialerColors.OnAccent
            } else {
                DialerColors.Text
            },
            size,
            onClick,
        )
        Text(label, color = DialerColors.Muted, fontSize = 14.sp)
    }
}
