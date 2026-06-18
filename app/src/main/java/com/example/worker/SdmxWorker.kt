package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.LocalDatabase
import com.example.data.PreferencesManager
import com.example.network.SdmxApiService
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SdmxWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val notificationHelper = NotificationHelper(applicationContext)
        val prefs = PreferencesManager(applicationContext)
        val api = SdmxApiService()
        val db = LocalDatabase(applicationContext)

        try {
            setForeground(ForegroundInfo(1001, notificationHelper.getForegroundNotification()))
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val user = prefs.userSdmx.first()
        val pass = prefs.passSdmx.first()

        if (user.isNullOrEmpty() || pass.isNullOrEmpty()) {
            notificationHelper.showError("Credenciales no configuradas.")
            return Result.failure()
        }

        Log.d("SdmxWorker", "Iniciando ciclo para usuario: $user")

        val loginSuccess = api.login(user, pass)
        if (!loginSuccess) {
            notificationHelper.showError("Login fallido para $user")
            return Result.failure()
        }

        db.loadData()
        val users = db.users.value
        
        if (users.isEmpty()) {
            notificationHelper.showError("Base de datos local vacía.")
            return Result.failure()
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val hoy = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val vigentes = users.filter { 
            try {
                val fechaLimpia = it.vencimiento.trim().substringBefore("T")
                val fecha = sdf.parse(fechaLimpia)
                fecha != null && !fecha.before(hoy)
            } catch (e: Exception) {
                false
            }
        }
        
        Log.d("SdmxWorker", "📋 Total: ${users.size} | Vigentes: ${vigentes.size} | No vigentes: ${users.size - vigentes.size}")

        var procesados = 0
        for (userToRenew in vigentes) {
            // Delete
            if (userToRenew.id.isNotEmpty()) {
                val delOk = api.deleteLine(userToRenew.id)
                Log.d("SdmxWorker", "🗑️ Eliminado: ${userToRenew.usuario} (id: ${userToRenew.id}) - Result: $delOk")
            }
            
            // Create
            val createOk = api.createLine(
                username = userToRenew.usuario,
                pass = userToRenew.password,
                expDate = userToRenew.vencimiento,
                adultos = userToRenew.adultos
            )
            Log.d("SdmxWorker", "✅ Creado: ${userToRenew.usuario} - Result: $createOk")
            if (createOk) procesados++
        }

        // Get new IDs
        val newTableIds = api.getTableIds()
        var updatedIdsCount = 0
        
        val updatedUsers = users.map { oldUser ->
            if (vigentes.contains(oldUser)) {
                val newId = newTableIds[oldUser.usuario]
                if (newId != null) {
                    updatedIdsCount++
                    Log.d("SdmxWorker", "📝 ID actualizado: ${oldUser.usuario} → nuevo id: $newId")
                    oldUser.copy(id = newId)
                } else {
                    oldUser
                }
            } else {
                oldUser
            }
        }

        db.saveUsers(updatedUsers)

        
        val nextHours = prefs.intervalHours.first().toIntOrNull() ?: 24
        
        val successMsg = "🎉 Ciclo completado. $procesados usuarios renovados. Próxima ejecución en: $nextHours horas."
        Log.d("SdmxWorker", successMsg)
        notificationHelper.showSuccess("Ciclo completado. $procesados usuarios renovados. Próxima ejecución en $nextHours horas.")
        
        // update last run time
        prefs.saveInterval(nextHours.toString())

        return Result.success()
    }
    
    companion object {
        fun schedule(context: Context, hours: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .build()

            val request = PeriodicWorkRequestBuilder<SdmxWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "SdmxAutoRenewWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
