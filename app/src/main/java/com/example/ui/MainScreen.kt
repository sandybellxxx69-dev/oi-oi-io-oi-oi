package com.example.ui

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LocalDatabase
import com.example.data.UserModel
import com.example.ui.theme.*
import com.example.worker.SdmxWorker
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val userSdmx by viewModel.userSdmx.collectAsState()
    val intervalHours by viewModel.intervalHours.collectAsState()
    val users by viewModel.users.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val json = inputStream.bufferedReader().readText()
                    val type = object : TypeToken<List<UserModel>>() {}.type
                    val list: List<UserModel>? = Gson().fromJson(json, type)
                    if (list != null) {
                        viewModel.replaceUsers(list)
                        Toast.makeText(context, "Importados ${list.size} usuarios", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Formato JSON inválido", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error al importar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeoBackground)
    ) {
        // App Header
        Surface(
            color = GeoSurface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GeoPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "RA",
                                color = GeoSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "SDMX Auto-Renew",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = GeoOnBackground
                            )
                            Text(
                                "Admin: $userSdmx",
                                fontSize = 12.sp,
                                color = GeoOnSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(GeoSecondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(GeoOnSecondaryContainer)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "ACTIVE",
                                color = GeoOnSecondaryContainer,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Info Block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(GeoSurfaceVariant)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { expanded = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "INTERVAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeoOnSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Every $intervalHours Hours",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GeoOnBackground
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("1", "2", "6", "12", "24").forEach { h ->
                                DropdownMenuItem(
                                    text = { Text("Every $h Hours") },
                                    onClick = {
                                        viewModel.saveInterval(h)
                                        SdmxWorker.schedule(context, h.toInt())
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(GeoOutline)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "NEXT RUN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeoOnSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                        Text(
                            "In ~${intervalHours}h",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GeoPrimary
                        )
                    }
                }
            }
        }

        // Content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            Text(
                "ACTIVE USERS (${users.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GeoOnSurfaceVariant,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(users) { user ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(GeoSurface)
                            .border(1.dp, GeoOutline, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        user.usuario,
                                        fontWeight = FontWeight.Bold,
                                        color = GeoOnBackground,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (user.adultos) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(GeoAdultBg)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "🔞 ADULT",
                                                color = GeoAdultText,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(GeoStandardBg)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "STANDARD",
                                                color = GeoStandardText,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "ID: ${user.id} | Exp: ${user.vencimiento}",
                                    fontSize = 12.sp,
                                    color = GeoOnSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isLoading) GeoPrimaryContainer else GeoSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = GeoOnPrimaryContainer,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("●", color = GeoOnSurfaceVariant, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log output
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(GeoLogBg)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SYSTEM LOG",
                        color = GeoLogGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "sdmx_users.json",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp
                    )
                }
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(logs) { logMsg ->
                        val isSuccess = logMsg.contains("✅") || logMsg.contains("🎉")
                        Text(
                            logMsg,
                            color = if (isSuccess) GeoLogGreen else GeoLogText.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Footer Actions
        Surface(
            color = GeoSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GeoOutline)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { exportDb(context) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeoPrimaryContainer,
                            contentColor = GeoOnPrimaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("📤 Export DB", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { importLauncher.launch("application/json") },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeoSurfaceVariant,
                            contentColor = GeoOnBackground
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("📥 Import DB", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.runManualCycle() },
                        enabled = !isLoading,
                        modifier = Modifier.weight(2f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeoPrimary,
                            contentColor = GeoSurface
                        ),
                        shape = RoundedCornerShape(24.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Text("EXECUTE NOW", fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
                    }
                    Button(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GeoOnBackground,
                            contentColor = GeoSurface
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add User", modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddUserDialog(
            onDismissRequest = { showAddDialog = false },
            onAddUser = { user, pass, meses, adultos ->
                viewModel.addUser(user, pass, meses, adultos)
                showAddDialog = false
            }
        )
    }

    LaunchedEffect(intervalHours) {
        val h = intervalHours.toIntOrNull() ?: 24
        SdmxWorker.schedule(context, h)
        viewModel.loadData()
    }
}

private fun exportDb(context: Context) {
    try {
        val source = LocalDatabase(context).getFile()
        if (!source.exists()) {
            Toast.makeText(context, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateStr = sdf.format(Date())
        val targetFile = File(downloadsFolder, "sdmx_backup_$dateStr.json")
        
        source.copyTo(targetFile, overwrite = true)
        Toast.makeText(context, "Exportado a: ${targetFile.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
