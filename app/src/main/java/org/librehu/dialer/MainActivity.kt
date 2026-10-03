package org.librehu.dialer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF101112)
private val Card = Color(0xFF1D1F20)
private val Raised = Color(0xFF2A2D2F)
private val Lime = Color(0xFFC7F36B)
private val Muted = Color(0xFFA5A9AA)
private val Red = Color(0xFFFF5D61)

private enum class Tab(val title: String) { Favorites("Favorites"), Recents("Recents"), Contacts("Contacts"), Keypad("Keypad") }
private enum class CallState { None, Calling, Connected }
private data class Person(val name: String, val number: String, val initials: String, val detail: String)

private val demo = listOf(
    Person("Alex Morgan", "+33 6 12 34 56 78", "AM", "Mobile · 10:42"),
    Person("Camille Bernard", "+33 6 23 45 67 89", "CB", "Mobile · Yesterday"),
    Person("Sam Martin", "+33 7 34 56 78 90", "SM", "Mobile · Monday"),
    Person("Garage", "+33 4 90 00 12 34", "G", "Work · 12 Sep"),
    Person("Jordan Lee", "+33 6 45 67 89 01", "JL", "Mobile · 9 Sep")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16,17,18)
        window.navigationBarColor = android.graphics.Color.rgb(16,17,18)
        setContent { DialerApp() }
    }
}

@Composable
private fun DialerApp() {
    var tab by remember { mutableStateOf(Tab.Favorites) }
    var query by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Person?>(null) }
    var call by remember { mutableStateOf(CallState.None) }
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    MaterialTheme(colorScheme = darkColorScheme(background=Bg, surface=Card, primary=Lime, onPrimary=Color(0xFF17200D), onBackground=Color.White, onSurface=Color.White, secondary=Lime)) {
        Surface(Modifier.fillMaxSize(), color=Bg) {
            Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(Modifier.width(86.dp).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(Card).padding(vertical=12.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Lime), contentAlignment=Alignment.Center) { Icon(Icons.Default.Call, null, tint=Color(0xFF17200D)) }
                    Spacer(Modifier.height(12.dp))
                    Tab.values().forEach { item ->
                        val active = item == tab
                        Column(Modifier.fillMaxWidth().padding(horizontal=7.dp).clip(RoundedCornerShape(16.dp)).background(if(active) Raised else Color.Transparent).clickable { tab=item }.padding(vertical=13.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                            Icon(when(item) { Tab.Favorites -> Icons.Default.Star; Tab.Recents -> Icons.Default.History; Tab.Contacts -> Icons.Default.Contacts; Tab.Keypad -> Icons.Default.Dialpad }, item.title, tint=if(active) Lime else Muted, modifier=Modifier.size(23.dp))
                            Spacer(Modifier.height(5.dp))
                            Text(item.title, color=if(active) Color.White else Muted, fontSize=10.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.BluetoothDisabled, "Bluetooth disconnected", tint=Muted)
                    Text("PHONE", color=Muted, fontSize=9.sp, letterSpacing=1.2.sp)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(horizontal=20.dp, vertical=14.dp), verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Phone", fontSize=25.sp, fontWeight=FontWeight.SemiBold)
                            Text("Keep your focus on the road", color=Muted, fontSize=12.sp)
                        }
                        Row(Modifier.clip(CircleShape).background(Raised).padding(horizontal=12.dp, vertical=9.dp), verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(Muted))
                            Spacer(Modifier.width(8.dp)); Text("Phone not connected", color=Muted, fontSize=12.sp)
                        }
                        Spacer(Modifier.width(20.dp)); Text(time, fontSize=22.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (call != CallState.None) {
                        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Card).padding(24.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(6.dp)); Avatar(selected ?: Person("New number",number,"?",""),78)
                            Spacer(Modifier.height(12.dp)); Text(selected?.name ?: "New number", fontSize=26.sp, fontWeight=FontWeight.SemiBold)
                            Text(selected?.number ?: number, color=Muted)
                            Spacer(Modifier.height(8.dp)); Text(if(call==CallState.Calling) "Calling… (preview)" else "Connected (preview)", color=Lime)
                            Spacer(Modifier.weight(1f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.CenterVertically) {
                                CallAction(Icons.Default.MicOff,"Mute",{})
                                CallAction(Icons.Default.Dialpad,"Keypad",{tab=Tab.Keypad;call=CallState.None})
                                CallAction(Icons.Default.VolumeUp,"Audio",{})
                                CallAction(Icons.Default.CallEnd,"End",{call=CallState.None},Red)
                            }
                        }
                    } else when(tab) {
                        Tab.Favorites -> {
                            Heading("Favorites","Your people, one tap away")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                demo.take(4).forEach { p ->
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Card).clickable { selected=p;number=p.number }.padding(12.dp), horizontalAlignment=Alignment.CenterHorizontally) {
                                        Avatar(p,58); Spacer(Modifier.height(10.dp))
                                        Text(p.name, fontSize=12.sp, maxLines=1, overflow=TextOverflow.Ellipsis)
                                        Text("Mobile", fontSize=11.sp, color=Muted)
                                        Spacer(Modifier.height(8.dp))
                                        FilledTonalButton(onClick={selected=p;number=p.number;call=CallState.Calling}, colors=ButtonDefaults.filledTonalButtonColors(containerColor=Raised,contentColor=Lime)) { Icon(Icons.Default.Call,null); Spacer(Modifier.width(4.dp)); Text("Call") }
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp)); Heading("Recent calls","Your latest conversations")
                            LazyColumn(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Card), contentPadding=PaddingValues(8.dp)) {
                                items(demo.take(3)) { p -> PersonRow(p,{selected=p;number=p.number},{selected=p;number=p.number;call=CallState.Calling}) }
                            }
                        }
                        Tab.Recents -> ContactList("Recent calls","Incoming, outgoing and missed",demo,{selected=it;number=it.number},{selected=it;number=it.number;call=CallState.Calling})
                        Tab.Contacts -> {
                            Heading("Contacts","Find someone to call")
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(14.dp), verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Default.Search,null,tint=Muted); Spacer(Modifier.width(10.dp))
                                BasicTextField(value=query,onValueChange={query=it},singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(color=Color.White,fontSize=16.sp),modifier=Modifier.fillMaxWidth(),decorationBox={inner->if(query.isEmpty()) Text("Search name or number",color=Muted);inner()})
                            }
                            Spacer(Modifier.height(10.dp))
                            ContactList("All contacts","Demo data",demo.filter{it.name.contains(query,true)||it.number.contains(query)},{selected=it;number=it.number},{selected=it;number=it.number;call=CallState.Calling})
                        }
                        Tab.Keypad -> {
                            Row(Modifier.fillMaxSize(), horizontalArrangement=Arrangement.spacedBy(22.dp), verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(0.9f)) {
                                    Heading("Keypad","Enter a phone number")
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(8.dp), verticalAlignment=Alignment.CenterVertically) {
                                        Text(number.ifBlank{"Enter number"},color=if(number.isBlank()) Muted else Color.White,fontSize=19.sp,modifier=Modifier.weight(1f),maxLines=1)
                                        IconButton(onClick={if(number.isNotEmpty())number=number.dropLast(1)}){Icon(Icons.Default.Backspace,"Delete",tint=Muted)}
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Button(onClick={selected=Person("New number",number,number.take(2).ifBlank{"?"},"");call=CallState.Calling},enabled=number.isNotBlank(),modifier=Modifier.fillMaxWidth().height(54.dp),colors=ButtonDefaults.buttonColors(containerColor=Lime,contentColor=Color(0xFF17200D))) { Icon(Icons.Default.Call,null);Spacer(Modifier.width(8.dp));Text("Call",fontSize=17.sp) }
                                    TextButton(onClick={number=""},modifier=Modifier.align(Alignment.CenterHorizontally)){Text("Clear",color=Muted)}
                                }
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    listOf(listOf("1" to "", "2" to "ABC", "3" to "DEF"),listOf("4" to "GHI","5" to "JKL","6" to "MNO"),listOf("7" to "PQRS","8" to "TUV","9" to "WXYZ"),listOf("*" to "","0" to "+","#" to "")).forEach { row ->
                                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                            row.forEach { (digit,letters) ->
                                                Column(Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(16.dp)).background(Raised).clickable{number+=digit},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                                    Text(digit,fontSize=22.sp); if(letters.isNotEmpty()) Text(letters,fontSize=9.sp,color=Muted)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if(call==CallState.None) { Spacer(Modifier.height(5.dp)); Text("PREVIEW MODE · DEMO CONTACTS · NO REAL CALLS",color=Muted,fontSize=10.sp,letterSpacing=1.1.sp) }
                }
            }
        }
    }
}

@Composable private fun Heading(title:String,subtitle:String) {
    Column(Modifier.padding(start=4.dp,bottom=10.dp,top=2.dp)) { Text(title,fontSize=19.sp,fontWeight=FontWeight.SemiBold); Text(subtitle,fontSize=12.sp,color=Muted) }
}
@Composable private fun Avatar(p:Person,size:Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Raised),contentAlignment=Alignment.Center){Text(p.initials,color=Lime,fontSize=(size/3.4).sp,fontWeight=FontWeight.SemiBold)}
}
@Composable private fun PersonRow(p:Person,onSelect:()->Unit,onCall:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable{onSelect()}.padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Avatar(p,44); Column(Modifier.weight(1f)){Text(p.name,fontSize=15.sp,fontWeight=FontWeight.Medium);Text(p.detail,color=Muted,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
        IconButton(onClick=onCall){Icon(Icons.Default.Call,"Call",tint=Lime)}
    }
}
@Composable private fun ContactList(title:String,subtitle:String,people:List<Person>,onSelect:(Person)->Unit,onCall:(Person)->Unit) {
    Heading(title,subtitle)
    LazyColumn(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Card),contentPadding=PaddingValues(8.dp)){items(people){p->PersonRow(p,{onSelect(p)},{onCall(p)})}}
}
@Composable private fun CallAction(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,onClick:()->Unit,color:Color=Raised) {
    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
        FilledIconButton(onClick=onClick,modifier=Modifier.size(58.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=color,contentColor=if(color==Lime) Color(0xFF17200D) else Color.White)){Icon(icon,label,modifier=Modifier.size(24.dp))}
        Text(label,color=Muted,fontSize=11.sp)
    }
}
