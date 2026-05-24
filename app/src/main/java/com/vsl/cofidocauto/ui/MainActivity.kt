package com.vsl.cofidocauto.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vsl.cofidocauto.model.LoginRequest
import com.vsl.cofidocauto.model.Ride
import com.vsl.cofidocauto.network.RetrofitClient
import com.vsl.cofidocauto.service.CofidocAutoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CofidocAutoApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CofidocAutoApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("vsl_prefs", Context.MODE_PRIVATE)

    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var password by remember { mutableStateOf("") }
    var isLoggedIn by remember { mutableStateOf(token.isNotEmpty()) }
    var rides by remember { mutableStateOf<List<Ride>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var currentIndex by remember { mutableStateOf(0) }
    var isAccessibilityEnabled by remember { mutableStateOf(false) }

    // Vérifie si le service d'accessibilité est activé
    LaunchedEffect(Unit) {
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
        if (isLoggedIn) {
            loadTodayRides(token) { result, error ->
                if (result != null) rides = result
                else statusMsg = error ?: "Erreur chargement"
            }
        }
    }

    // Mise à jour du statut depuis le service
    CofidocAutoService.onStatusUpdate = { msg -> statusMsg = msg }
    CofidocAutoService.onFinished = { statusMsg = "✅ Toutes les courses traitées !" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VSL → Cofidoc Auto", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1565C0),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            if (!isLoggedIn) {
                // ─── ÉCRAN LOGIN ───────────────────────────────
                Text("Connexion VSL-Mobile", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    RetrofitClient.api.login(LoginRequest(email, password))
                                }
                                token = response.token
                                prefs.edit().putString("token", token).putString("email", email).apply()
                                isLoggedIn = true
                                loadTodayRides(token) { result, error ->
                                    if (result != null) rides = result
                                    else statusMsg = error ?: "Erreur"
                                }
                            } catch (e: Exception) {
                                statusMsg = "❌ Erreur login : ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    else Text("Se connecter")
                }

            } else {
                // ─── ÉCRAN PRINCIPAL ───────────────────────────

                // Alerte accessibilité
                if (!isAccessibilityEnabled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF57F17))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Service d'accessibilité non activé", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                TextButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }) {
                                    Text("Activer maintenant →", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Barre de statut
                if (statusMsg.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            statusMsg,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // En-tête avec stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${rides.size} course(s) aujourd'hui",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = {
                        scope.launch {
                            loadTodayRides(token) { result, error ->
                                if (result != null) rides = result
                                else statusMsg = error ?: "Erreur"
                            }
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rafraîchir")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Liste des courses
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(rides) { index, ride ->
                        RideItem(
                            ride = ride,
                            index = index,
                            isCurrent = index == currentIndex,
                            isProcessed = index < currentIndex
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Boutons d'action
                if (rides.isNotEmpty() && isAccessibilityEnabled) {
                    if (!CofidocAutoService.isRunning && currentIndex < rides.size) {
                        Button(
                            onClick = {
                                CofidocAutoService.rides = rides
                                CofidocAutoService.currentRideIndex = currentIndex
                                CofidocAutoService.step = 0
                                CofidocAutoService.isRunning = true
                                statusMsg = "🚀 Démarrage pour ${rides[currentIndex].patientName}..."
                                // Ouvre Cofidoc
                                val intent = context.packageManager
                                    .getLaunchIntentForPackage("fr.cofidoc.mobile")
                                if (intent != null) {
                                    context.startActivity(intent)
                                } else {
                                    statusMsg = "❌ Cofidoc non trouvé. Vérifiez l'installation."
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (currentIndex == 0) "▶ Démarrer la facturation"
                                else "▶ Course suivante (${currentIndex + 1}/${rides.size})"
                            )
                        }
                    }

                    if (CofidocAutoService.step == 7) {
                        // L'user a rempli sécu + naissance, passe à la suivante
                        Button(
                            onClick = {
                                currentIndex++
                                CofidocAutoService.nextRide()
                                statusMsg = if (currentIndex < rides.size)
                                    "➡️ Course ${currentIndex + 1}/${rides.size} : ${rides[currentIndex].patientName}"
                                else "✅ Toutes les courses traitées !"
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("✅ Sécu + Naissance saisis → Course suivante")
                        }
                    }
                }

                // Déconnexion
                TextButton(
                    onClick = {
                        prefs.edit().remove("token").apply()
                        token = ""; isLoggedIn = false; rides = emptyList()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Déconnexion", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun RideItem(ride: Ride, index: Int, isCurrent: Boolean, isProcessed: Boolean) {
    val bgColor = when {
        isProcessed -> Color(0xFFE8F5E9)
        isCurrent -> Color(0xFFE3F2FD)
        else -> Color(0xFFF5F5F5)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Numéro
            Text(
                "${index + 1}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) Color(0xFF1565C0) else Color.Gray,
                modifier = Modifier.width(30.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(ride.patientName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${ride.type} · ${ride.realDistance?.toInt() ?: 0} km", fontSize = 13.sp, color = Color.Gray)
                Text(
                    "📍 ${ride.startLocation.take(35)}...",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            // Statut facturation
            if (isProcessed) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
            } else if (isCurrent) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF1565C0))
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains("com.vsl.cofidocauto/.service.CofidocAutoService")
}

private fun loadTodayRides(
    token: String,
    callback: (List<Ride>?, String?) -> Unit
) {
    kotlinx.coroutines.GlobalScope.launch {
        try {
            val rides = withContext(Dispatchers.IO) {
                RetrofitClient.api.getTodayRides("Bearer $token")
            }
            withContext(Dispatchers.Main) { callback(rides, null) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { callback(null, "❌ ${e.message}") }
        }
    }
}
