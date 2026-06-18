package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LocalDatabase
import com.example.data.PreferencesManager
import com.example.data.UserModel
import com.example.network.SdmxApiService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val db = LocalDatabase(application)
    private val api = SdmxApiService()

    val userSdmx = prefs.userSdmx.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val passSdmx = prefs.passSdmx.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val intervalHours = prefs.intervalHours.stateIn(viewModelScope, SharingStarted.Eagerly, "24")
    
    val users = db.users

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadData() {
        db.loadData()
    }

    fun addLog(msg: String) {
        val lst = _logs.value.toMutableList()
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        val time = sdf.format(Date())
        lst.add(0, "[$time] $msg")
        if (lst.size > 100) lst.removeLast()
        _logs.value = lst
        Log.d("MainViewModel", msg)
    }

    fun saveCredentials(user: String, pass: String) = viewModelScope.launch {
        prefs.saveCredentials(user, pass)
    }

    fun saveInterval(hours: String) = viewModelScope.launch {
        prefs.saveInterval(hours)
    }

    fun runManualCycle() = viewModelScope.launch {
        if (_isLoading.value) return@launch
        _isLoading.value = true
        addLog("Iniciando ciclo manual...")

        val user = userSdmx.value
        val pass = passSdmx.value
        if (user.isNullOrEmpty() || pass.isNullOrEmpty()) {
            addLog("Error: Credenciales no configuradas.")
            _isLoading.value = false
            return@launch
        }

        val loginOk = api.login(user, pass)
        if (!loginOk) {
            addLog("Error: Login fallido.")
            _isLoading.value = false
            return@launch
        }
        addLog("Login exitoso.")

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        val allUsers = users.value
        val vigentes = allUsers.filter {
            try {
                val fechaLimpia = it.vencimiento.trim().substringBefore("T")
                val fecha = sdf.parse(fechaLimpia)
                fecha != null && !fecha.before(hoy)
            } catch (e: Exception) { false }
        }

        addLog("📋 Total: ${allUsers.size} | Vigentes: ${vigentes.size}")

        var procesados = 0
        for (u in vigentes) {
            if (u.id.isNotEmpty()) {
                val delOk = api.deleteLine(u.id)
                addLog("🗑️ Eliminado: ${u.usuario} (id: ${u.id}) - Result: $delOk")
            }
            val createOk = api.createLine(u.usuario, u.password, u.vencimiento, u.adultos)
            addLog("✅ Creado: ${u.usuario} - Result: $createOk")
            if (createOk) procesados++
        }

        val newIds = api.getTableIds()
        val updatedUsers = allUsers.map { u ->
            if (vigentes.contains(u) && newIds.containsKey(u.usuario)) {
                val newId = newIds[u.usuario]!!
                addLog("📝 ID actualizado: ${u.usuario} → $newId")
                u.copy(id = newId)
            } else u
        }

        db.saveUsers(updatedUsers)
        addLog("🎉 Ciclo manual completado. $procesados procesados.")
        _isLoading.value = false
    }

    fun addUser(username: String, pass: String, meses: Int, adultos: Boolean) = viewModelScope.launch {
        _isLoading.value = true
        val sdUser = userSdmx.value ?: return@launch
        val sdPass = passSdmx.value ?: return@launch
        addLog("Agregando nuevo usuario: $username...")

        if (!api.login(sdUser, sdPass)) {
            addLog("Error: Login fallido.")
            _isLoading.value = false
            return@launch
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val targetDate = Calendar.getInstance().apply {
            add(Calendar.MONTH, meses)
        }.time
        val expDateStr = sdf.format(targetDate)

        val createOk = api.createLine(username, pass, expDateStr, adultos)
        if (!createOk) {
            addLog("Error: No se pudo crear la línea.")
            _isLoading.value = false
            return@launch
        }

        val ids = api.getTableIds()
        val newId = ids[username] ?: ""
        
        val newUser = UserModel(
            id = newId,
            usuario = username,
            password = pass,
            vencimiento = expDateStr,
            adultos = adultos
        )
        db.addUser(newUser)
        addLog("✅ Creado: $username | id: $newId | vence: $expDateStr | adultos: $adultos")
        _isLoading.value = false
    }
    
    fun replaceUsers(newUsers: List<UserModel>) = viewModelScope.launch {
        db.saveUsers(newUsers)
        addLog("📥 BD importada: ${newUsers.size} usuarios cargados.")
    }
}
