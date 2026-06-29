package com.example

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

// Top-level property for Preferences DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "motocare_preferences")

const val APP_VERSION = "1.0.1"

// Component Constant IDs
const val COMPONENT_OLI_MESIN = "oli_mesin"
const val COMPONENT_OLI_GARDAN = "oli_gardan"
const val COMPONENT_V_BELT = "v_belt"
const val COMPONENT_KAMPAS_REM = "kampas_rem"
const val COMPONENT_FILTER_UDARA = "filter_udara"
const val COMPONENT_BUSI = "busi"
const val COMPONENT_RANTAI_GIR = "rantai_gir"
const val COMPONENT_KAMPAS_KOPLING = "kampas_kopling"

val COMPONENT_IDS = listOf(
    COMPONENT_OLI_MESIN,
    COMPONENT_OLI_GARDAN,
    COMPONENT_V_BELT,
    COMPONENT_KAMPAS_REM,
    COMPONENT_FILTER_UDARA,
    COMPONENT_BUSI,
    COMPONENT_RANTAI_GIR,
    COMPONENT_KAMPAS_KOPLING
)

data class ServiceHistoryEntry(
    val componentId: String,
    val odo: Int,
    val dateMillis: Long,
    val cost: Int = 0,
    val notes: String = ""
)

// Data class to represent current application state
data class MotoState(
    val currentOdometer: Int = 0,
    val motorModel: String = "",
    val jakartaModeEnabled: Boolean = true,
    val lastServiceOdos: Map<String, Int> = emptyMap(),
    val lastServiceDates: Map<String, Long> = emptyMap(),
    val hasCompletedOnboarding: Boolean = false,
    val userName: String = "",
    val initialOdometer: Int = 0,
    val serviceHistory: List<ServiceHistoryEntry> = emptyList()
)

// Preferences Keys helper
object PreferencesKeys {
    val CURRENT_ODOMETER = intPreferencesKey("current_odometer")
    val MOTOR_MODEL = stringPreferencesKey("motor_model")
    val JAKARTA_MODE_ENABLED = booleanPreferencesKey("jakarta_mode_enabled")
    val FIRST_LAUNCH_INITIALIZED = booleanPreferencesKey("first_launch_initialized")
    val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
    val USER_NAME = stringPreferencesKey("user_name")
    val INITIAL_ODOMETER = intPreferencesKey("initial_odometer")
    val SERVICE_HISTORY = stringPreferencesKey("service_history_log")

    fun lastServiceOdoKey(id: String) = intPreferencesKey("last_service_odo_$id")
    fun lastServiceDateKey(id: String) = longPreferencesKey("last_service_date_$id")
}

// Categories of motorcycle
enum class MotoCategory {
    MATIC_BESAR,
    MATIC_KECIL,
    BEBEK,
    SPORT,
    DEFAULT
}

// Substring check for auto-detect motor
fun detectMotoCategory(model: String): MotoCategory {
    val lower = model.lowercase(Locale.getDefault())
    return when {
        // Matic Besar
        lower.contains("pcx") || lower.contains("nmax") || lower.contains("aerox") || 
        lower.contains("n-max") || lower.contains("xmax") || lower.contains("forza") || 
        lower.contains("lexi 155") || lower.contains("vario 160") -> {
            MotoCategory.MATIC_BESAR
        }
        // Matic Kecil
        lower.contains("beat") || lower.contains("scoopy") || lower.contains("vario") || 
        lower.contains("mio") || lower.contains("fazzio") || lower.contains("filano") || 
        lower.contains("genio") || lower.contains("address") || lower.contains("nex") -> {
            MotoCategory.MATIC_KECIL
        }
        // Bebek (Underbone)
        lower.contains("supra") || lower.contains("jupiter") || lower.contains("vega") || 
        lower.contains("revo") || lower.contains("mx king") || lower.contains("satria") || 
        lower.contains("shogun") || lower.contains("smash") || lower.contains("astrea") || 
        lower.contains("bebek") || lower.contains("grand") || lower.contains("force") ||
        lower.contains("sonic") || lower.contains("gtr") -> {
            MotoCategory.BEBEK
        }
        // Sport
        lower.contains("cb150") || lower.contains("cbr") || lower.contains("vixion") || 
        lower.contains("r15") || lower.contains("gsx") || lower.contains("ninja") || 
        lower.contains("crf") || lower.contains("wr155") || lower.contains("klx") || 
        lower.contains("verza") || lower.contains("byson") || lower.contains("megapro") || 
        lower.contains("tiger") || lower.contains("sport") || lower.contains("r25") || 
        lower.contains("mt-15") || lower.contains("mt-25") || lower.contains("binter") ||
        lower.contains("thunder") || lower.contains("rx king") || lower.contains("rx-king") -> {
            MotoCategory.SPORT
        }
        else -> {
            MotoCategory.DEFAULT
        }
    }
}

// Get service intervals based on motor category and Jakarta mode
fun getInterval(componentId: String, category: MotoCategory, jakartaMode: Boolean): Pair<Int, Int> {
    return when (category) {
        MotoCategory.MATIC_BESAR -> {
            when (componentId) {
                COMPONENT_OLI_MESIN -> Pair(if (jakartaMode) 2400 else 4000, 2)
                COMPONENT_OLI_GARDAN -> Pair(8000, 6)
                COMPONENT_V_BELT -> Pair(24000, 24)
                COMPONENT_BUSI -> Pair(8000, 12)
                COMPONENT_KAMPAS_REM -> Pair(10000, 12)
                COMPONENT_FILTER_UDARA -> Pair(12000, 12)
                else -> Pair(2000, 2)
            }
        }
        MotoCategory.BEBEK -> {
            when (componentId) {
                COMPONENT_OLI_MESIN -> Pair(if (jakartaMode) 2000 else 3000, 2)
                COMPONENT_KAMPAS_KOPLING -> Pair(if (jakartaMode) 15000 else 20000, 24)
                COMPONENT_RANTAI_GIR -> Pair(if (jakartaMode) 12000 else 15000, 18)
                COMPONENT_BUSI -> Pair(8000, 12)
                COMPONENT_KAMPAS_REM -> Pair(8000, 12)
                COMPONENT_FILTER_UDARA -> Pair(10000, 12)
                else -> Pair(2000, 2)
            }
        }
        MotoCategory.SPORT -> {
            when (componentId) {
                COMPONENT_OLI_MESIN -> Pair(if (jakartaMode) 2500 else 4000, 3)
                COMPONENT_KAMPAS_KOPLING -> Pair(if (jakartaMode) 15000 else 20000, 24)
                COMPONENT_RANTAI_GIR -> Pair(if (jakartaMode) 12000 else 15000, 12)
                COMPONENT_BUSI -> Pair(8000, 12)
                COMPONENT_KAMPAS_REM -> Pair(10000, 12)
                COMPONENT_FILTER_UDARA -> Pair(12000, 12)
                else -> Pair(2000, 2)
            }
        }
        else -> { // MATIC_KECIL and DEFAULT
            when (componentId) {
                COMPONENT_OLI_MESIN -> Pair(if (jakartaMode) 2000 else 3000, 2)
                COMPONENT_OLI_GARDAN -> Pair(8000, 6)
                COMPONENT_V_BELT -> Pair(20000, 24)
                COMPONENT_BUSI -> Pair(8000, 12)
                COMPONENT_KAMPAS_REM -> Pair(8000, 12)
                COMPONENT_FILTER_UDARA -> Pair(10000, 12)
                else -> Pair(2000, 2)
            }
        }
    }
}

// Helper to format remaining duration beautifully in Indonesian
fun formatRemainingTime(remainingDays: Int): String {
    return if (remainingDays >= 0) {
        if (remainingDays >= 30) {
            val months = remainingDays / 30
            val days = remainingDays % 30
            if (days == 0) "Sisa $months Bulan" else "Sisa $months Bln $days Hari"
        } else {
            "Sisa $remainingDays Hari"
        }
    } else {
        val overdays = -remainingDays
        if (overdays >= 30) {
            val months = overdays / 30
            val days = overdays % 30
            if (days == 0) "Telat $months Bulan" else "Telat $months Bln $days Hari"
        } else {
            "Telat $overdays Hari"
        }
    }
}

// Component Metadata representation
data class ComponentMetadata(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val description: String
)

fun getComponentMetadataListForCategory(category: MotoCategory): List<ComponentMetadata> {
    return when (category) {
        MotoCategory.MATIC_BESAR, MotoCategory.MATIC_KECIL, MotoCategory.DEFAULT -> {
            listOf(
                ComponentMetadata(
                    id = COMPONENT_OLI_MESIN,
                    name = "Oli Mesin",
                    icon = Icons.Default.OilBarrel,
                    description = "Melumasi bagian dalam mesin motor agar tidak aus."
                ),
                ComponentMetadata(
                    id = COMPONENT_OLI_GARDAN,
                    name = "Oli Gardan",
                    icon = Icons.Default.Settings,
                    description = "Melumasi transmisi otomatis (CVT) matic."
                ),
                ComponentMetadata(
                    id = COMPONENT_V_BELT,
                    name = "V-Belt / Roller",
                    icon = Icons.Default.Sync,
                    description = "Mentransmisikan tenaga mesin ke roda belakang."
                ),
                ComponentMetadata(
                    id = COMPONENT_KAMPAS_REM,
                    name = "Kampas Rem",
                    icon = Icons.Default.StopCircle,
                    description = "Menahan putaran piringan rem untuk menghentikan motor."
                ),
                ComponentMetadata(
                    id = COMPONENT_FILTER_UDARA,
                    name = "Filter Udara",
                    icon = Icons.Default.Air,
                    description = "Menyaring udara bersih sebelum masuk ruang bakar."
                ),
                ComponentMetadata(
                    id = COMPONENT_BUSI,
                    name = "Busi",
                    icon = Icons.Default.FlashOn,
                    description = "Memantik percikan api untuk pembakaran bensin."
                )
            )
        }
        MotoCategory.BEBEK, MotoCategory.SPORT -> {
            listOf(
                ComponentMetadata(
                    id = COMPONENT_OLI_MESIN,
                    name = "Oli Mesin",
                    icon = Icons.Default.OilBarrel,
                    description = "Melumasi bagian dalam mesin motor agar tidak aus."
                ),
                ComponentMetadata(
                    id = COMPONENT_KAMPAS_KOPLING,
                    name = "Kampas Kopling",
                    icon = Icons.Default.Layers,
                    description = "Mengatur transfer tenaga mesin ke transmisi manual."
                ),
                ComponentMetadata(
                    id = COMPONENT_RANTAI_GIR,
                    name = "Rantai & Gir",
                    icon = Icons.Default.Link,
                    description = "Mentransmisikan tenaga mesin ke roda belakang melalui rantai logam."
                ),
                ComponentMetadata(
                    id = COMPONENT_KAMPAS_REM,
                    name = "Kampas Rem",
                    icon = Icons.Default.StopCircle,
                    description = "Menahan putaran piringan rem untuk menghentikan motor."
                ),
                ComponentMetadata(
                    id = COMPONENT_FILTER_UDARA,
                    name = "Filter Udara",
                    icon = Icons.Default.Air,
                    description = "Menyaring udara bersih sebelum masuk ruang bakar."
                ),
                ComponentMetadata(
                    id = COMPONENT_BUSI,
                    name = "Busi",
                    icon = Icons.Default.FlashOn,
                    description = "Memantik percikan api untuk pembakaran bensin."
                )
            )
        }
    }
}

// ViewModel for managing State and DataStore operations
class MotoViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.dataStore

    private val _state = MutableStateFlow(MotoState())
    val state: StateFlow<MotoState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Continuous subscription to preferences change
            dataStore.data.collect { prefs ->
                val hasOnboarded = prefs[PreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: (prefs[PreferencesKeys.FIRST_LAUNCH_INITIALIZED] ?: false)
                val currentOdo = prefs[PreferencesKeys.CURRENT_ODOMETER] ?: 0
                val motorModel = prefs[PreferencesKeys.MOTOR_MODEL] ?: ""
                val jakartaMode = prefs[PreferencesKeys.JAKARTA_MODE_ENABLED] ?: true
                val userName = prefs[PreferencesKeys.USER_NAME] ?: ""
                val initialOdo = prefs[PreferencesKeys.INITIAL_ODOMETER] ?: currentOdo

                val historyStr = prefs[PreferencesKeys.SERVICE_HISTORY] ?: ""
                val historyList = historyStr.split("\n")
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        val parts = line.split("|")
                        if (parts.size >= 3) {
                            val id = parts[0]
                            val odo = parts[1].toIntOrNull() ?: 0
                            val date = parts[2].toLongOrNull() ?: 0L
                            val cost = if (parts.size >= 4) parts[3].toIntOrNull() ?: 0 else 0
                            val notes = if (parts.size >= 5) parts[4] else ""
                            ServiceHistoryEntry(id, odo, date, cost, notes)
                        } else {
                            null
                        }
                    }

                val odos = mutableMapOf<String, Int>()
                val dates = mutableMapOf<String, Long>()

                COMPONENT_IDS.forEach { id ->
                    odos[id] = prefs[PreferencesKeys.lastServiceOdoKey(id)] ?: currentOdo
                    dates[id] = prefs[PreferencesKeys.lastServiceDateKey(id)] ?: System.currentTimeMillis()
                }

                _state.value = MotoState(
                    currentOdometer = currentOdo,
                    motorModel = motorModel,
                    jakartaModeEnabled = jakartaMode,
                    lastServiceOdos = odos,
                    lastServiceDates = dates,
                    hasCompletedOnboarding = hasOnboarded,
                    userName = userName,
                    initialOdometer = initialOdo,
                    serviceHistory = historyList
                )
            }
        }
    }

    private suspend fun initializeDefaultData() {
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.FIRST_LAUNCH_INITIALIZED] = true
            prefs[PreferencesKeys.CURRENT_ODOMETER] = 12500
            prefs[PreferencesKeys.MOTOR_MODEL] = "Honda PCX 150"
            prefs[PreferencesKeys.JAKARTA_MODE_ENABLED] = true

            // Oli Mesin: 10600 KM (1900 KM ago), 45 days ago (Urgent soon under Jakarta mode)
            prefs[PreferencesKeys.lastServiceOdoKey(COMPONENT_OLI_MESIN)] = 10600
            prefs[PreferencesKeys.lastServiceDateKey(COMPONENT_OLI_MESIN)] = now - (45L * 24 * 3600 * 1000)

            // Oli Gardan: 10000 KM (2500 KM ago), 160 days ago (Yellow warning for time)
            prefs[PreferencesKeys.lastServiceOdoKey(COMPONENT_OLI_GARDAN)] = 10000
            prefs[PreferencesKeys.lastServiceDateKey(COMPONENT_OLI_GARDAN)] = now - (160L * 24 * 3600 * 1000)

            // V-Belt: 2000 KM (10500 KM ago), 365 days ago (Green safe)
            prefs[PreferencesKeys.lastServiceOdoKey(COMPONENT_V_BELT)] = 2000
            prefs[PreferencesKeys.lastServiceDateKey(COMPONENT_V_BELT)] = now - (365L * 24 * 3600 * 1000)

            // Kampas Rem: 2300 KM (10200 KM ago), 380 days ago (Red overdue for KM and time)
            prefs[PreferencesKeys.lastServiceOdoKey(COMPONENT_KAMPAS_REM)] = 2300
            prefs[PreferencesKeys.lastServiceDateKey(COMPONENT_KAMPAS_REM)] = now - (380L * 24 * 3600 * 1000)

            // Filter Udara: 5000 KM (7500 KM ago), 90 days ago (Green safe)
            prefs[PreferencesKeys.lastServiceOdoKey(COMPONENT_FILTER_UDARA)] = 5000
            prefs[PreferencesKeys.lastServiceDateKey(COMPONENT_FILTER_UDARA)] = now - (90L * 24 * 3600 * 1000)

            // Busi: 12100 KM (400 KM ago), 15 days ago (Green safe)
            prefs[PreferencesKeys.lastServiceOdoKey(COMPONENT_BUSI)] = 12100
            prefs[PreferencesKeys.lastServiceDateKey(COMPONENT_BUSI)] = now - (15L * 24 * 3600 * 1000)

            // Rantai & Gir: 3000 KM (9500 KM ago), 120 days ago (Green safe)
            prefs[PreferencesKeys.lastServiceOdoKey(COMPONENT_RANTAI_GIR)] = 3000
            prefs[PreferencesKeys.lastServiceDateKey(COMPONENT_RANTAI_GIR)] = now - (120L * 24 * 3600 * 1000)

            // Kampas Kopling: 4000 KM (8500 KM ago), 180 days ago (Green safe)
            prefs[PreferencesKeys.lastServiceOdoKey(COMPONENT_KAMPAS_KOPLING)] = 4000
            prefs[PreferencesKeys.lastServiceDateKey(COMPONENT_KAMPAS_KOPLING)] = now - (180L * 24 * 3600 * 1000)
        }
    }

    private fun getDefaultOdo(id: String): Int {
        return when (id) {
            COMPONENT_OLI_MESIN -> 10600
            COMPONENT_OLI_GARDAN -> 10000
            COMPONENT_V_BELT -> 2000
            COMPONENT_KAMPAS_REM -> 2300
            COMPONENT_FILTER_UDARA -> 5000
            COMPONENT_BUSI -> 12100
            COMPONENT_RANTAI_GIR -> 3000
            COMPONENT_KAMPAS_KOPLING -> 4000
            else -> 0
        }
    }

    private fun getDefaultDate(id: String): Long {
        val now = System.currentTimeMillis()
        val daysAgo = when (id) {
            COMPONENT_OLI_MESIN -> 45L
            COMPONENT_OLI_GARDAN -> 160L
            COMPONENT_V_BELT -> 365L
            COMPONENT_KAMPAS_REM -> 380L
            COMPONENT_FILTER_UDARA -> 90L
            COMPONENT_BUSI -> 15L
            COMPONENT_RANTAI_GIR -> 120L
            COMPONENT_KAMPAS_KOPLING -> 180L
            else -> 0L
        }
        return now - (daysAgo * 24 * 3600 * 1000)
    }

    fun triggerServiceNotificationsIfNecessary() {
        val currentOdo = _state.value.currentOdometer
        val category = detectMotoCategory(_state.value.motorModel)
        
        val urgentList = mutableListOf<String>()
        getComponentMetadataListForCategory(category).forEach { compMetadata ->
            val (intervalKm, intervalMonths) = getInterval(compMetadata.id, category, _state.value.jakartaModeEnabled)
            val lastOdo = _state.value.lastServiceOdos[compMetadata.id] ?: 0
            val lastDate = _state.value.lastServiceDates[compMetadata.id] ?: System.currentTimeMillis()
            val kmElapsed = maxOf(0, currentOdo - lastOdo)
            val kmPercent = (kmElapsed.toFloat() / intervalKm.toFloat()) * 100f
            
            val millisElapsed = maxOf(0L, System.currentTimeMillis() - lastDate)
            val daysElapsed = (millisElapsed / (1000 * 60 * 60 * 24)).toInt()
            val maxDays = intervalMonths * 30
            val timePercent = (daysElapsed.toFloat() / maxDays.toFloat()) * 100f
            
            val overallPercent = maxOf(kmPercent, timePercent)
            if (overallPercent >= 100f) {
                urgentList.add(compMetadata.name)
            }
        }
        
        if (urgentList.isNotEmpty()) {
            sendSystemNotification(getApplication(), urgentList)
        }
    }

    fun updateCurrentOdometer(km: Int) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.CURRENT_ODOMETER] = km
            }
            delay(500)
            triggerServiceNotificationsIfNecessary()
        }
    }

    fun updateMotorModel(model: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.MOTOR_MODEL] = model
            }
        }
    }

    fun updateJakartaMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.JAKARTA_MODE_ENABLED] = enabled
            }
        }
    }

    fun resetComponent(id: String, cost: Int = 0, notes: String = "") {
        val currentOdo = _state.value.currentOdometer
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.lastServiceOdoKey(id)] = currentOdo
                prefs[PreferencesKeys.lastServiceDateKey(id)] = now

                // Append to history
                val existingHistory = prefs[PreferencesKeys.SERVICE_HISTORY] ?: ""
                val safeNotes = notes.replace("|", " ").replace("\n", " ")
                val entryLine = "$id|$currentOdo|$now|$cost|$safeNotes"
                val newHistory = if (existingHistory.isBlank()) {
                    entryLine
                } else {
                    "$existingHistory\n$entryLine"
                }
                prefs[PreferencesKeys.SERVICE_HISTORY] = newHistory
            }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs.clear()
            }
            initializeDefaultData()
        }
    }

    fun resetAllComponents() {
        val currentOdo = _state.value.currentOdometer
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val newEntries = mutableListOf<String>()
                COMPONENT_IDS.forEach { id ->
                    prefs[PreferencesKeys.lastServiceOdoKey(id)] = currentOdo
                    prefs[PreferencesKeys.lastServiceDateKey(id)] = now
                    newEntries.add("$id|$currentOdo|$now")
                }

                // Append all to history
                val existingHistory = prefs[PreferencesKeys.SERVICE_HISTORY] ?: ""
                val newEntriesStr = newEntries.joinToString("\n")
                val newHistory = if (existingHistory.isBlank()) {
                    newEntriesStr
                } else {
                    "$existingHistory\n$newEntriesStr"
                }
                prefs[PreferencesKeys.SERVICE_HISTORY] = newHistory
            }
        }
    }

    fun completeOnboarding(name: String, model: String, odo: Int) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.USER_NAME] = name
                prefs[PreferencesKeys.MOTOR_MODEL] = model
                prefs[PreferencesKeys.CURRENT_ODOMETER] = odo
                prefs[PreferencesKeys.INITIAL_ODOMETER] = odo
                prefs[PreferencesKeys.FIRST_LAUNCH_INITIALIZED] = true
                prefs[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = true
                prefs[PreferencesKeys.SERVICE_HISTORY] = "" // Starts empty on onboarding
                
                // Set all component service histories to this initial odometer & now
                COMPONENT_IDS.forEach { id ->
                    prefs[PreferencesKeys.lastServiceOdoKey(id)] = odo
                    prefs[PreferencesKeys.lastServiceDateKey(id)] = now
                }
            }
        }
    }

    fun changeMotorAndReset(newModel: String, newOdo: Int) {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.MOTOR_MODEL] = newModel
                prefs[PreferencesKeys.CURRENT_ODOMETER] = newOdo
                prefs[PreferencesKeys.INITIAL_ODOMETER] = newOdo
                prefs[PreferencesKeys.FIRST_LAUNCH_INITIALIZED] = true
                prefs[PreferencesKeys.HAS_COMPLETED_ONBOARDING] = true
                prefs[PreferencesKeys.SERVICE_HISTORY] = "" // Clear history log when switching motor
                
                // Completely reset all components' service history to the new starting odometer & now
                COMPONENT_IDS.forEach { id ->
                    prefs[PreferencesKeys.lastServiceOdoKey(id)] = newOdo
                    prefs[PreferencesKeys.lastServiceDateKey(id)] = now
                }
            }
        }
    }
}

// Color Palette Definition (Sleek Garage Aesthetic)
private val MotoDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8A2BE2),       // Deep Violet
    secondary = Color(0xFFFF6F00),     // Neon Orange
    background = Color(0xFF121212),    // Dark Charcoal / Hitam
    surface = Color(0xFF1E1E1E),       // Card Background
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFE0E0E0),
    primaryContainer = Color(0xFF2C1A4D),
    onPrimaryContainer = Color(0xFFEADBFF),
    secondaryContainer = Color(0xFF3E1D00),
    onSecondaryContainer = Color(0xFFFFDCC3)
)

@Composable
fun MotoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MotoDarkColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotoTheme {
                val viewModel: MotoViewModel = viewModel()
                val state by viewModel.state.collectAsState()

                if (!state.hasCompletedOnboarding) {
                    OnboardingScreen(
                        onComplete = { name, model, odo ->
                            viewModel.completeOnboarding(name, model, odo)
                        }
                    )
                } else {
                    var showOdoDialog by remember { mutableStateOf(false) }
                    var showSettingsDialog by remember { mutableStateOf(false) }
                    var showConfirmResetAll by remember { mutableStateOf(false) }
                    var resetComponentTarget by remember { mutableStateOf<ComponentMetadata?>(null) }
                    var selectedTab by remember { mutableStateOf(0) }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color(0xFF121212),
                        bottomBar = {
                            Surface(
                                color = Color(0xFF1E1E26),
                                tonalElevation = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        BorderStroke(1.dp, Color(0xFF2D2D3D)),
                                        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                                    )
                                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .height(76.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Garage
                                    val garageSelected = selectedTab == 0
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { selectedTab = 0 },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Home,
                                                contentDescription = "Garage",
                                                tint = if (garageSelected) MaterialTheme.colorScheme.secondary else Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Garage",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (garageSelected) MaterialTheme.colorScheme.secondary else Color.Gray
                                            )
                                        }
                                    }

                                    // 2. Edit KM (Add/Plus action)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { showOdoDialog = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(18.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = "Edit KM",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Edit KM",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    // 3. Riwayat
                                    val riwayatSelected = selectedTab == 1
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { selectedTab = 1 },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = "Riwayat",
                                                tint = if (riwayatSelected) MaterialTheme.colorScheme.secondary else Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Riwayat",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (riwayatSelected) MaterialTheme.colorScheme.secondary else Color.Gray
                                            )
                                        }
                                    }

                                    // 4. Profil
                                    val profilSelected = selectedTab == 2
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { selectedTab = 2 },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Profil",
                                                tint = if (profilSelected) MaterialTheme.colorScheme.secondary else Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Profil",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (profilSelected) MaterialTheme.colorScheme.secondary else Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            val containerWidth = maxWidth
                            if (selectedTab == 2) {
                                ProfileScreenContent(
                                    state = state,
                                    viewModel = viewModel,
                                    containerWidth = containerWidth
                                )
                            } else if (selectedTab == 1) {
                                HistoryScreenContent(
                                    state = state,
                                    containerWidth = containerWidth
                                )
                            } else {
                                // Calculate stats dynamically
                                val category = detectMotoCategory(state.motorModel)
                                var amanCount = 0
                                var bersiapCount = 0
                                var urgenCount = 0

                        getComponentMetadataListForCategory(category).forEach { compMetadata ->
                            val (intervalKm, intervalMonths) = getInterval(compMetadata.id, category, state.jakartaModeEnabled)
                            val lastOdo = state.lastServiceOdos[compMetadata.id] ?: 0
                            val lastDate = state.lastServiceDates[compMetadata.id] ?: System.currentTimeMillis()
                            val kmElapsed = maxOf(0, state.currentOdometer - lastOdo)
                            val kmPercent = (kmElapsed.toFloat() / intervalKm.toFloat()) * 100f
                            
                            val millisElapsed = maxOf(0L, System.currentTimeMillis() - lastDate)
                            val daysElapsed = (millisElapsed / (1000 * 60 * 60 * 24)).toInt()
                            val maxDays = intervalMonths * 30
                            val timePercent = (daysElapsed.toFloat() / maxDays.toFloat()) * 100f
                            
                            val overallPercent = maxOf(kmPercent, timePercent)
                            when {
                                overallPercent < 75f -> amanCount++
                                overallPercent < 100f -> bersiapCount++
                                else -> urgenCount++
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 960.dp)
                                .align(Alignment.TopCenter)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // Top Settings Row (To keep access to Settings without breaking the beautiful design)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(
                                    onClick = { showSettingsDialog = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(18.dp))
                                        .testTag("settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Pengaturan",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Title & Subtitle Section
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .testTag("motor_model_input"),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = state.motorModel.ifEmpty { "HONDA PCX" }.uppercase(Locale.getDefault()),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    color = Color.White,
                                    letterSpacing = 1.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "GARASI DIGITAL • ${state.userName.ifBlank { "PENGGUNA" }.uppercase(Locale.getDefault())}",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 1.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Distance Traveled (Odometer display - Read Only)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .testTag("update_km_button"),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "JARAK TEMPUH",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    val formattedOdo = String.format(Locale.US, "%,d", state.currentOdometer).replace(",", ".")
                                    Text(
                                        text = formattedOdo,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 44.sp,
                                        color = Color.White,
                                        letterSpacing = (-1).sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "KM",
                                        color = Color(0xFFFF6F00),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.align(Alignment.Bottom).padding(bottom = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Jakarta Mode Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFFF6F00).copy(alpha = 0.08f), shape = RoundedCornerShape(20.dp))
                                        .border(1.dp, Color(0xFFFF6F00).copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .clickable { showSettingsDialog = true }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFFFF6F00), shape = RoundedCornerShape(3.dp))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (state.jakartaModeEnabled) "Jakarta Mode Aktif" else "Mode Standar",
                                            color = Color(0xFFFF6F00),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = if (state.jakartaModeEnabled) "Optimasi Stop-and-Go Aktif" else "Rekomendasi Pabrik Aktif",
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Status Summary Bar Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0F0F14))
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFF00E676), shape = RoundedCornerShape(3.dp))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Aman",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$amanCount",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(14.dp)
                                        .background(Color.White.copy(alpha = 0.1f))
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1.2f),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFFFFD600), shape = RoundedCornerShape(3.dp))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Servis Bersiap",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$bersiapCount",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(14.dp)
                                        .background(Color.White.copy(alpha = 0.1f))
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1.2f),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFFFF1744), shape = RoundedCornerShape(3.dp))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Urgen/Telat",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$urgenCount",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Pusat Pengingat Servis
                            val activeAlerts = remember(state.currentOdometer, state.lastServiceOdos, state.lastServiceDates, state.jakartaModeEnabled) {
                                val alerts = mutableListOf<Pair<ComponentMetadata, String>>()
                                val category = detectMotoCategory(state.motorModel)
                                getComponentMetadataListForCategory(category).forEach { compMetadata ->
                                    val (intervalKm, intervalMonths) = getInterval(compMetadata.id, category, state.jakartaModeEnabled)
                                    val lastOdo = state.lastServiceOdos[compMetadata.id] ?: 0
                                    val lastDate = state.lastServiceDates[compMetadata.id] ?: System.currentTimeMillis()
                                    val kmElapsed = maxOf(0, state.currentOdometer - lastOdo)
                                    val kmPercent = (kmElapsed.toFloat() / intervalKm.toFloat()) * 100f
                                    
                                    val millisElapsed = maxOf(0L, System.currentTimeMillis() - lastDate)
                                    val daysElapsed = (millisElapsed / (1000 * 60 * 60 * 24)).toInt()
                                    val maxDays = intervalMonths * 30
                                    val timePercent = (daysElapsed.toFloat() / maxDays.toFloat()) * 100f
                                    
                                    val overallPercent = maxOf(kmPercent, timePercent)
                                    val remainingKm = intervalKm - kmElapsed
                                    val remainingDays = maxDays - daysElapsed
                                    
                                    if (overallPercent >= 75f) {
                                        val message = when {
                                            remainingKm < 0 -> "Sudah lewat ${String.format(Locale.US, "%,d", -remainingKm).replace(",", ".")} KM!"
                                            remainingDays < 0 -> "Sudah lewat ${-remainingDays} hari!"
                                            remainingKm < 500 -> "Harus diganti dalam ${String.format(Locale.US, "%,d", remainingKm).replace(",", ".")} KM lagi"
                                            remainingDays < 14 -> "Harus diganti dalam $remainingDays hari lagi"
                                            else -> "Kondisi bersiap servis berkala"
                                        }
                                        alerts.add(Pair(compMetadata, message))
                                    }
                                }
                                alerts
                            }

                            Text(
                                text = "🔔 PUSAT PENGINGAT SERVIS",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    if (activeAlerts.isEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Semua Suku Cadang Prima",
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Semua sparepart aman & prima! Terus pantau kondisi berkendara Anda.",
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Ada ${activeAlerts.size} komponen yang butuh perhatian khusus:",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            activeAlerts.forEach { (comp, alertMsg) ->
                                                val isOverdue = alertMsg.contains("lewat")
                                                val alertColor = if (isOverdue) Color(0xFFFF1744) else Color(0xFFFFD600)
                                                
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(30.dp)
                                                                .background(alertColor.copy(alpha = 0.1f), RoundedCornerShape(6.dp)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = comp.icon,
                                                                contentDescription = comp.name,
                                                                tint = alertColor,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text(
                                                                text = comp.name,
                                                                color = Color.White,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = alertMsg,
                                                                color = alertColor,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                    
                                                    Button(
                                                        onClick = { resetComponentTarget = comp },
                                                        colors = ButtonDefaults.buttonColors(containerColor = alertColor),
                                                        shape = RoundedCornerShape(6.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Text(
                                                            text = "Servis",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = if (isOverdue) Color.White else Color.Black
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // SPAREPART Status section header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "STATUS SPAREPART & OLI",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Reset Semua",
                                    color = Color(0xFFFF6F00),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { showConfirmResetAll = true }
                                        .testTag("reset_all_button")
                                )
                            }

                            // Dynamic Responsive Grid of Cards
                            val columnsCount = when {
                                containerWidth < 360.dp -> 1
                                containerWidth < 720.dp -> 2
                                else -> 3
                            }

                            val chunks = getComponentMetadataListForCategory(category).chunked(columnsCount)
                            chunks.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            ComponentCard(
                                                metadata = item,
                                                state = state,
                                                onResetClick = { resetComponentTarget = item }
                                            )
                                        }
                                    }
                                    val remaining = columnsCount - rowItems.size
                                    if (remaining > 0) {
                                        repeat(remaining) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

                // DIALOG: Update Current Odometer KM
                if (showOdoDialog) {
                    var kmInput by remember { mutableStateOf(state.currentOdometer.toString()) }
                    var errorMsg by remember { mutableStateOf<String?>(null) }

                    Dialog(onDismissRequest = { showOdoDialog = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF2D2D3D)),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Update Odometer KM",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = kmInput,
                                    onValueChange = {
                                        kmInput = it
                                        val validKm = it.toIntOrNull()
                                        errorMsg = when {
                                            validKm == null -> "Masukkan angka KM yang valid!"
                                            validKm <= 0 -> "Odometer tidak boleh 0 atau kurang!"
                                            validKm < state.initialOdometer -> "Odometer tidak boleh di bawah odometer awal (${state.initialOdometer} KM)!"
                                            else -> null
                                        }
                                    },
                                    label = { Text("KM Odometer Saat Ini") },
                                    isError = errorMsg != null,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                errorMsg?.let { msg ->
                                    Text(
                                        text = msg,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 12.sp,
                                        modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { showOdoDialog = false }) {
                                        Text("Batal", color = Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val validKm = kmInput.toIntOrNull()
                                            val err = when {
                                                validKm == null -> "Masukkan angka KM yang valid!"
                                                validKm <= 0 -> "Odometer tidak boleh 0 atau kurang!"
                                                validKm < state.initialOdometer -> "Odometer tidak boleh di bawah odometer awal (${state.initialOdometer} KM)!"
                                                else -> null
                                            }
                                            if (err == null && validKm != null) {
                                                viewModel.updateCurrentOdometer(validKm)
                                                showOdoDialog = false
                                            } else {
                                                errorMsg = err
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary
                                        ),
                                        modifier = Modifier.testTag("save_button_km")
                                    ) {
                                        Text("Simpan", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }



                // DIALOG: Reset Service with Cost and Notes
                if (resetComponentTarget != null) {
                    val comp = resetComponentTarget!!
                    var costInput by remember { mutableStateOf("") }
                    var notesInput by remember { mutableStateOf("") }

                    Dialog(onDismissRequest = { resetComponentTarget = null }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF2D2D3D)),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = comp.icon,
                                        contentDescription = comp.name,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Catat Servis: ${comp.name}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Mencatat penggantian baru pada ${String.format(Locale.US, "%,d", state.currentOdometer).replace(",", ".")} KM",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = costInput,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() }) {
                                            costInput = input
                                        }
                                    },
                                    label = { Text("Biaya Servis (Rp)") },
                                    placeholder = { Text("Contoh: 50000 (Isi 0 jika gratis)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    prefix = { Text("Rp ", color = Color.LightGray) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = notesInput,
                                    onValueChange = { notesInput = it },
                                    label = { Text("Catatan Tambahan (Opsional)") },
                                    placeholder = { Text("Contoh: Ganti Oli Mesin Shell Advance") },
                                    singleLine = false,
                                    maxLines = 3,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                        unfocusedBorderColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { resetComponentTarget = null }) {
                                        Text("Batal", color = Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val cost = costInput.toIntOrNull() ?: 0
                                            viewModel.resetComponent(comp.id, cost, notesInput)
                                            resetComponentTarget = null
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Simpan Servis", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }



                // DIALOG: Settings & System Status
                if (showSettingsDialog) {
                    var showConfirmReset by remember { mutableStateOf(false) }

                    Dialog(onDismissRequest = { showSettingsDialog = false }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF2D2D3D)),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Pengaturan",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color.White
                                    )
                                    IconButton(onClick = { showSettingsDialog = false }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup", tint = Color.Gray)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                // Setting Toggle: Jakarta Mode
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto-Detect Jakarta Mode",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Mengurangi interval Oli Mesin 40% (kemacetan Stop-and-Go).",
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                    Switch(
                                        checked = state.jakartaModeEnabled,
                                        onCheckedChange = { viewModel.updateJakartaMode(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.secondary,
                                            checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider(color = Color(0xFF2C2C35))
                                Spacer(modifier = Modifier.height(20.dp))

                                // Current Setup Status Info
                                val category = detectMotoCategory(state.motorModel)
                                val categoryName = when (category) {
                                    MotoCategory.MATIC_BESAR -> "Matic Besar (PCX, NMax, Aerox)"
                                    MotoCategory.MATIC_KECIL -> "Matic Kecil (Beat, Scoopy, Vario, Mio)"
                                    MotoCategory.BEBEK -> "Bebek / Underbone (Supra, Vega, MX King)"
                                    MotoCategory.SPORT -> "Motor Sport (CB150R, Ninja, Vixion)"
                                    MotoCategory.DEFAULT -> "Standar / Fallback"
                                }
                                Text(
                                    text = "Status Deteksi Motor:",
                                    fontSize = 12.sp,
                                    color = Color.LightGray,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Kategori: $categoryName",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Reset Button
                                Button(
                                    onClick = { showConfirmReset = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Reset",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Reset Semua Data ke Default", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Confirm Reset Alert
                    if (showConfirmReset) {
                        AlertDialog(
                            onDismissRequest = { showConfirmReset = false },
                            title = { Text("Reset Semua Data?", color = Color.White, fontWeight = FontWeight.Bold) },
                            text = { Text("Tindakan ini akan mengembalikan semua data odometer dan tanggal servis ke kondisi demo awal. Anda yakin?", color = Color.LightGray) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.resetAllData()
                                        showConfirmReset = false
                                        showSettingsDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Ya, Reset", color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirmReset = false }) {
                                    Text("Batal", color = Color.Gray)
                                }
                            },
                            containerColor = Color(0xFF1E1E26)
                        )
                    }

                }

                // Confirm Reset All Components Alert
                if (showConfirmResetAll) {
                    AlertDialog(
                        onDismissRequest = { showConfirmResetAll = false },
                        title = { Text("Reset Semua Servis?", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = { Text("Tindakan ini akan mereset status semua komponen perawatan ke 100% aman (0 KM). Apakah Anda yakin?", color = Color.LightGray) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.resetAllComponents()
                                    showConfirmResetAll = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                            ) {
                                Text("Ya, Reset Semua", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmResetAll = false }) {
                                Text("Batal", color = Color.Gray)
                            }
                        },
                        containerColor = Color(0xFF1E1E26)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: (String, String, Int) -> Unit) {
    var nameInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf("") }
    var odoInput by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf(false) }
    var odoError by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .systemBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header visual
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), shape = RoundedCornerShape(40.dp))
                    .border(2.dp, MaterialTheme.colorScheme.secondary, shape = RoundedCornerShape(40.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "SELAMAT DATANG DI GARASI",
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                color = Color.White,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Silakan isi nama dan data motor Anda untuk memulai pemantauan berkala sparepart & oli secara akurat.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Name Input Field
            OutlinedTextField(
                value = nameInput,
                onValueChange = {
                    nameInput = it
                    nameError = false
                },
                label = { Text("Nama Pengguna") },
                placeholder = { Text("Contoh: Andi, Budi") },
                isError = nameError,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().testTag("onboarding_name_input")
            )
            if (nameError) {
                Text(
                    text = "Nama pengguna wajib diisi!",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Start).padding(horizontal = 4.dp)
                )
            }

            // Model Input Field
            OutlinedTextField(
                value = modelInput,
                onValueChange = {
                    modelInput = it
                    modelError = false
                },
                label = { Text("Model Motor Anda") },
                placeholder = { Text("Contoh: Honda Beat, Yamaha NMax") },
                isError = modelError,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().testTag("onboarding_model_input")
            )
            if (modelError) {
                Text(
                    text = "Model motor wajib diisi!",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Start).padding(horizontal = 4.dp)
                )
            }

            // Dynamic Category Detection Label
            val detected = detectMotoCategory(modelInput)
            val detectedLabel = when (detected) {
                MotoCategory.MATIC_BESAR -> "Matic Besar (e.g. PCX, NMax)"
                MotoCategory.MATIC_KECIL -> "Matic Kecil (e.g. Beat, Vario)"
                MotoCategory.BEBEK -> "Bebek / Underbone (e.g. Supra, Vega)"
                MotoCategory.SPORT -> "Motor Sport (e.g. CB150, Ninja)"
                MotoCategory.DEFAULT -> "Matic Umum / Default"
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kategori Terdeteksi: ",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp
                )
                Text(
                    text = detectedLabel,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Odometer Input Field
            OutlinedTextField(
                value = odoInput,
                onValueChange = {
                    odoInput = it
                    odoError = false
                },
                label = { Text("Odometer Terakhir (KM)") },
                placeholder = { Text("Contoh: 12500") },
                isError = odoError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().testTag("onboarding_odo_input")
            )
            if (odoError) {
                Text(
                    text = "Odometer wajib diisi dengan angka non-negatif!",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Start).padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick popular motor selectors
            val popularMotos = listOf("Honda Beat", "Yamaha NMax", "Honda PCX", "Supra X 125", "Yamaha MX King", "Honda CB150R")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "PILIH CEPAT MOTOR POPULER:",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    popularMotos.take(3).forEach { model ->
                        SuggestionChip(
                            onClick = { 
                                modelInput = model
                                modelError = false
                            },
                            label = { Text(model, fontSize = 11.sp, color = Color.White) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    popularMotos.drop(3).forEach { model ->
                        SuggestionChip(
                            onClick = { 
                                modelInput = model
                                modelError = false
                            },
                            label = { Text(model, fontSize = 11.sp, color = Color.White) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Start application button
            Button(
                onClick = {
                    val odoVal = odoInput.toIntOrNull()
                    if (nameInput.isBlank()) {
                        nameError = true
                    }
                    if (modelInput.isBlank()) {
                        modelError = true
                    }
                    if (odoVal == null || odoVal < 0) {
                        odoError = true
                    }
                    if (nameInput.isNotBlank() && modelInput.isNotBlank() && odoVal != null && odoVal >= 0) {
                        onComplete(nameInput.trim(), modelInput.trim(), odoVal)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("onboarding_start_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MULAI PEMANTAUAN",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenContent(
    state: MotoState,
    viewModel: MotoViewModel,
    containerWidth: androidx.compose.ui.unit.Dp
) {
    var newModelInput by remember { mutableStateOf("") }
    var newOdoInput by remember { mutableStateOf("") }
    var modelError by remember { mutableStateOf(false) }
    var odoError by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Header
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
                .border(2.dp, MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(45.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(45.dp)
            )
        }

        Text(
            text = "PROFIL GARASI ${state.userName.ifBlank { "ANDA" }.uppercase(Locale.getDefault())}",
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = Color.White,
            letterSpacing = 1.5.sp
        )

        // Current Motor Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2D2D3D)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "MOTOR SAAT INI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.motorModel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        val category = detectMotoCategory(state.motorModel)
                        val categoryLabel = when (category) {
                            MotoCategory.MATIC_BESAR -> "Matic Besar"
                            MotoCategory.MATIC_KECIL -> "Matic Kecil"
                            MotoCategory.BEBEK -> "Bebek / Underbone"
                            MotoCategory.SPORT -> "Motor Sport"
                            MotoCategory.DEFAULT -> "Standar"
                        }
                        Text(
                            text = "Kategori: $categoryLabel",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        val formattedOdo = String.format(Locale.US, "%,d", state.currentOdometer).replace(",", ".")
                        Text(
                            text = "$formattedOdo KM",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)

                // Jakarta Mode toggle inside Profile
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Jakarta Mode (Stop-and-Go)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Mengoptimalkan interval servis untuk rute padat perkotaan.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Switch(
                        checked = state.jakartaModeEnabled,
                        onCheckedChange = { viewModel.updateJakartaMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.secondary,
                            checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.testTag("jakarta_mode_switch_profile")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Change motor form section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "GANTI DAN UBAH MOTOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "Isi formulir di bawah ini untuk mengganti motor Anda dengan model yang baru.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                // New Motor Model
                OutlinedTextField(
                    value = newModelInput,
                    onValueChange = {
                        newModelInput = it
                        modelError = false
                    },
                    label = { Text("Model Motor Baru") },
                    placeholder = { Text("Contoh: Yamaha NMax, Supra X 125") },
                    isError = modelError,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("profile_new_model_input")
                )
                if (modelError) {
                    Text(
                        text = "Model motor baru wajib diisi!",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }

                // New Odometer
                OutlinedTextField(
                    value = newOdoInput,
                    onValueChange = {
                        newOdoInput = it
                        odoError = false
                    },
                    label = { Text("Odometer Mulai (KM)") },
                    placeholder = { Text("Contoh: 0 atau 15000") },
                    isError = odoError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("profile_new_odo_input")
                )
                if (odoError) {
                    Text(
                        text = "Odometer wajib diisi dengan angka non-negatif!",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }

                // Quick selectors
                val popularMotos = listOf("Honda Beat", "Yamaha NMax", "Honda PCX", "Supra X 125", "Yamaha MX King", "Honda CB150R")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("PILIH CEPAT MOTOR POPULER:", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        popularMotos.take(3).forEach { model ->
                            SuggestionChip(
                                onClick = { 
                                    newModelInput = model
                                    modelError = false
                                },
                                label = { Text(model, fontSize = 10.sp, color = Color.White) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        popularMotos.drop(3).forEach { model ->
                            SuggestionChip(
                                onClick = { 
                                    newModelInput = model
                                    modelError = false
                                },
                                label = { Text(model, fontSize = 10.sp, color = Color.White) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Warning/Reset Notification Card (Crucial request detail)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE53935).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PERINGATAN: Mengubah motor akan sepenuhnya MERESET semua data riwayat servis sebelumnya ke kondisi baru (0 KM).",
                        fontSize = 11.sp,
                        color = Color(0xFFFF8A80),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action Button
                Button(
                    onClick = {
                        val odoVal = newOdoInput.toIntOrNull()
                        if (newModelInput.isBlank()) {
                            modelError = true
                        }
                        if (odoVal == null || odoVal < 0) {
                            odoError = true
                        }
                        if (newModelInput.isNotBlank() && odoVal != null && odoVal >= 0) {
                            showResetConfirmDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("profile_change_motor_button")
                ) {
                    Text(
                        text = "UBAH & RESET DATA MOTOR",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Versi Aplikasi: v$APP_VERSION",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.4f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }

    // Confirmation dialog before changing motor and resetting everything
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(
                    text = "Konfirmasi Reset Motor",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Apakah Anda 100% yakin ingin mengubah motor ke '$newModelInput' dan odometer '$newOdoInput KM'?\n\nSeluruh riwayat servis sebelumnya akan di-reset total dan tidak dapat dikembalikan.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val odoVal = newOdoInput.toIntOrNull() ?: 0
                        viewModel.changeMotorAndReset(newModelInput.trim(), odoVal)
                        newModelInput = ""
                        newOdoInput = ""
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Ya, Reset & Ubah", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Batal", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E26)
        )
    }
}

@Composable
fun ComponentCard(
    metadata: ComponentMetadata,
    state: MotoState,
    onResetClick: () -> Unit
) {
    val category = detectMotoCategory(state.motorModel)
    val (intervalKm, intervalMonths) = getInterval(metadata.id, category, state.jakartaModeEnabled)

    val lastOdo = state.lastServiceOdos[metadata.id] ?: 0
    val lastDate = state.lastServiceDates[metadata.id] ?: System.currentTimeMillis()

    val now = System.currentTimeMillis()
    val kmElapsed = maxOf(0, state.currentOdometer - lastOdo)
    val kmPercent = (kmElapsed.toFloat() / intervalKm.toFloat()) * 100f

    val millisElapsed = maxOf(0L, now - lastDate)
    val daysElapsed = (millisElapsed / (1000 * 60 * 60 * 24)).toInt()
    val maxDays = intervalMonths * 30
    val timePercent = (daysElapsed.toFloat() / maxDays.toFloat()) * 100f

    val overallPercent = maxOf(kmPercent, timePercent)

    val remainingKm = intervalKm - kmElapsed
    val remainingDays = maxDays - daysElapsed

    val (statusLabel, urgencyColor) = when {
        overallPercent < 75f -> Pair("Aman", Color(0xFF00E676)) // vibrant green
        overallPercent < 100f -> Pair("Servis Bersiap", Color(0xFFFFD600)) // vibrant yellow
        else -> Pair("Urgen/Telat", Color(0xFFFF1744)) // vibrant red
    }

    // Format interval KM like 2,4k KM or 8k KM or standard
    val intervalFormatted = if (intervalKm % 1000 == 0) {
        "${intervalKm / 1000}k"
    } else {
        "${String.format(Locale.US, "%.1f", intervalKm.toFloat() / 1000f).replace(".", ",")}k"
    }

    val sdfLast = SimpleDateFormat("dd/MM/yy", Locale.US)
    val formattedLastDate = sdfLast.format(Date(lastDate))

    // Hitung rata-rata KM per hari sejak servis terakhir secara dinamis
    val kmPerDay = if (daysElapsed > 0) {
        val calculatedRate = kmElapsed.toFloat() / daysElapsed.toFloat()
        if (calculatedRate < 1f) 15f else calculatedRate // default minimal 15 KM/hari agar estimasi realistis
    } else {
        if (kmElapsed > 0) kmElapsed.toFloat() else 15f // jika di-update di hari yang sama
    }

    // Hari tersisa berdasarkan sisa KM
    val daysUntilKmLimit = if (remainingKm > 0) {
        (remainingKm.toFloat() / kmPerDay).toLong()
    } else {
        0L
    }

    // Hari tersisa berdasarkan sisa waktu
    val daysUntilTimeLimit = maxOf(0L, remainingDays.toLong())

    // Prediksi penggantian adalah batas mana yang tercapai lebih dulu
    val finalRemainingDays = minOf(daysUntilKmLimit, daysUntilTimeLimit)

    val predictionTimestamp = if (overallPercent >= 100f) {
        now
    } else {
        now + (finalRemainingDays * 24L * 60L * 60L * 1000L)
    }

    val sdfPred = SimpleDateFormat("dd MMM yy", Locale.forLanguageTag("id-ID"))
    val formattedPredDate = if (overallPercent >= 100f) {
        "Segera Ganti"
    } else {
        try {
            sdfPred.format(Date(predictionTimestamp))
        } catch (e: Exception) {
            SimpleDateFormat("dd MMM yy", Locale.US).format(Date(predictionTimestamp))
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0F)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_${metadata.id}")
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            // Top row: Circle with icon, Name, and Status Dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp))
                            .border(0.5.dp, Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = metadata.icon,
                            contentDescription = metadata.name,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = metadata.name.uppercase(Locale.getDefault()),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Status Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(urgencyColor, shape = RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Middle: e.g., "0 / 2,4k KM"
            val progressVal = (overallPercent / 100f).coerceIn(0f, 1f)
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$kmElapsed",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " / $intervalFormatted KM",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar (Green/Yellow/Red indicator)
            LinearProgressIndicator(
                progress = { progressVal },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = urgencyColor,
                trackColor = Color.White.copy(alpha = 0.05f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Info rows: Sisa, Prediksi Ganti, Servis Terakhir
            val sisaText = if (remainingKm >= 0) {
                "Sisa: ${String.format(Locale.US, "%,d", remainingKm).replace(",", ".")} KM"
            } else {
                "Telat: ${String.format(Locale.US, "%,d", -remainingKm).replace(",", ".")} KM"
            }
            Text(
                text = sisaText,
                color = urgencyColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Prediksi Ganti: $formattedPredDate",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Servis Terakhir: $formattedLastDate",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom action: RESET SERVIS
            OutlinedButton(
                onClick = onResetClick,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .testTag("reset_button_${metadata.id}")
            ) {
                Text(
                    text = "RESET SERVIS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
}

@Composable
fun HistoryScreenContent(
    state: MotoState,
    containerWidth: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // History Header
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = "Riwayat Servis",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(54.dp)
            )
        }

        Text(
            text = "RIWAYAT SERVIS MOTOR",
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = Color.White,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Daftar penggantian sparepart & oli berkala yang tercatat di garasi Anda.",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Expense Tracker Financial Summary
        val totalAllTimeCost = state.serviceHistory.sumOf { it.cost }
        val currentCalendar = Calendar.getInstance()
        val currentMonth = currentCalendar.get(Calendar.MONTH)
        val currentYear = currentCalendar.get(Calendar.YEAR)
        
        val totalThisMonthCost = state.serviceHistory.filter { entry ->
            val cal = Calendar.getInstance().apply { timeInMillis = entry.dateMillis }
            cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
        }.sumOf { it.cost }
        
        val averageCost = if (state.serviceHistory.isNotEmpty()) {
            totalAllTimeCost / state.serviceHistory.size
        } else {
            0
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161D)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📊 RINGKASAN BIAYA (EXPENSE TRACKER)",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Total Pengeluaran Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Total Biaya",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Rp ${String.format(Locale.US, "%,d", totalAllTimeCost).replace(",", ".")}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Bulan Ini Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Bulan Ini",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Rp ${String.format(Locale.US, "%,d", totalThisMonthCost).replace(",", ".")}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Rata-rata Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Rata-rata",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Rp ${String.format(Locale.US, "%,d", averageCost).replace(",", ".")}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.LightGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.serviceHistory.isEmpty()) {
            // Empty State Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26)),
                border = BorderStroke(1.dp, Color(0xFF2D2D3D)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Belum Ada Riwayat",
                        tint = Color.Gray,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Belum Ada Riwayat Servis",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Riwayat penggantian baru akan tercatat secara otomatis ketika Anda melakukan reset atau mengonfirmasi penggantian sparepart di menu utama.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            // History list, newest first
            val sortedHistory = state.serviceHistory.reversed()
            val category = detectMotoCategory(state.motorModel)
            val allCompMetadata = getComponentMetadataListForCategory(category)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sortedHistory.forEach { entry ->
                    val metadata = allCompMetadata.find { it.id == entry.componentId }
                    val componentName = metadata?.name ?: entry.componentId.replace("_", " ").replaceFirstChar { it.uppercase() }
                    val componentIcon = metadata?.icon ?: Icons.Default.Settings
                    val formattedDate = try {
                        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))
                        sdf.format(Date(entry.dateMillis))
                    } catch (e: Exception) {
                        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
                        sdf.format(Date(entry.dateMillis))
                    }
                    val formattedOdo = String.format(Locale.US, "%,d", entry.odo).replace(",", ".")

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26)),
                        border = BorderStroke(1.dp, Color(0xFF2D2D3D)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Icon Box
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = componentIcon,
                                    contentDescription = componentName,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Info Column
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = componentName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    if (entry.cost > 0) {
                                        Text(
                                            text = "Rp ${String.format(Locale.US, "%,d", entry.cost).replace(",", ".")}",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    } else {
                                        Text(
                                            text = "Rp 0 / Reset",
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Text(
                                    text = "Selesai di $formattedOdo KM",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (entry.notes.isNotBlank()) {
                                    Text(
                                        text = "📝 \"${entry.notes}\"",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    text = formattedDate,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun sendSystemNotification(context: Context, urgentList: List<String>) {
    val channelId = "service_reminder_channel"
    val channelName = "Pengingat Servis MotoCare"
    
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Menampilkan pengingat servis suku cadang motor"
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    )
    
    val partsStr = urgentList.joinToString(", ")
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("Peringatan Servis Garasi Moto!")
        .setContentText("Suku cadang perlu servis: $partsStr")
        .setStyle(NotificationCompat.BigTextStyle().bigText("Suku cadang berikut telah melewati batas penggantian berkala: $partsStr. Ketuk untuk membuka aplikasi dan mencatat servis."))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        
    try {
        notificationManager.notify(1001, builder.build())
    } catch (e: Exception) {
        // Safe catch
    }
}


