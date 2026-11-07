package com.downloadchecker.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.downloadchecker.app.data.ApiClient
import com.downloadchecker.app.service.InstallMonitorService
import com.downloadchecker.app.ui.theme.AppTheme
import com.downloadchecker.app.util.InstallTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val api = ApiClient()
    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                var screen by remember {
                    mutableStateOf(if (App.instance.session.isLoggedIn()) "home" else "login")
                }
                var status by remember { mutableStateOf("") }
                var loading by remember { mutableStateOf(false) }
                var progress by remember {
                    mutableIntStateOf(
                        when (App.instance.session.stage) {
                            "TASK2" -> InstallTracker.progress(2)
                            else -> InstallTracker.progress(1)
                        }
                    )
                }
                var stage by remember { mutableStateOf(App.instance.session.stage) }

                fun refreshProgress() {
                    stage = App.instance.session.stage
                    progress = when (stage) {
                        "TASK2" -> InstallTracker.progress(2)
                        "COMPLETED" -> 5
                        else -> InstallTracker.progress(1)
                    }
                }

                DisposableEffect(Unit) {
                    val filter = IntentFilter("com.downloadchecker.app.PROGRESS")
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(c: Context?, i: Intent?) {
                            refreshProgress()
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 33) {
                        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        @Suppress("UnspecifiedRegisterReceiverFlag")
                        registerReceiver(receiver, filter)
                    }
                    onDispose { runCatching { unregisterReceiver(receiver) } }
                }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        screen == "login" -> LoginScreen(loading, status) { id ->
                            loading = true
                            status = ""
                            lifecycleScope.launch {
                                try {
                                    withContext(Dispatchers.IO) { api.requestCode(id) }
                                    status = "Telegram သို့ code ပို့ပြီးပါပြီ"
                                    screen = "code:$id"
                                } catch (e: Exception) {
                                    status = e.message ?: "Error"
                                } finally {
                                    loading = false
                                }
                            }
                        }
                        screen == "home" -> HomeScreen(
                            stage = stage,
                            progress = progress,
                            reward1 = App.instance.session.rewardTask1,
                            reward2 = App.instance.session.rewardTask2,
                            username = App.instance.session.username,
                            status = status,
                            loading = loading,
                            onClaim = {
                                val st = App.instance.session
                                val task = if (st.stage == "TASK2") 2 else 1
                                val pkgs = st.counted(task).toList()
                                if (pkgs.size < 5) {
                                    status = "App ${pkgs.size}/5 — ၅ ခုပြည့်မှ claim လုပ်နိုင်သည်"
                                    return@HomeScreen
                                }
                                loading = true
                                status = ""
                                lifecycleScope.launch {
                                    try {
                                        val day = Calendar.getInstance().let {
                                            "${it.get(Calendar.YEAR)}${it.get(Calendar.DAY_OF_YEAR)}"
                                        }
                                        val res = withContext(Dispatchers.IO) {
                                            api.claimTask(
                                                token = st.sessionToken ?: "",
                                                taskType = task,
                                                packages = pkgs.take(5),
                                                deviceId = st.deviceId(this@MainActivity),
                                                idempotencyKey = "${st.telegramId}-t$task-$day"
                                            )
                                        }
                                        val next = res.optString("stage", if (task == 1) "TASK2" else "COMPLETED")
                                        st.stage = next
                                        status = if (task == 1) {
                                            st.clearCounted(2)
                                            "Task 1 ပြီးပါပြီ! Task 2 သို့ ရောက်ပါပြီ"
                                        } else {
                                            "Task 2 ပြီးပါပြီ! Reward ရရှိပါပြီ"
                                        }
                                        refreshProgress()
                                    } catch (e: Exception) {
                                        status = e.message ?: "Claim failed"
                                    } finally {
                                        loading = false
                                    }
                                }
                            },
                            onLogout = {
                                App.instance.session.clearSession()
                                screen = "login"
                            },
                            onRefresh = { refreshProgress() }
                        )
                        screen.startsWith("code:") -> {
                            val id = screen.removePrefix("code:")
                            CodeScreen(loading, status, onVerify = { code ->
                                loading = true
                                status = ""
                                lifecycleScope.launch {
                                    try {
                                        val res = withContext(Dispatchers.IO) {
                                            api.verifyCode(
                                                id,
                                                code,
                                                App.instance.session.deviceId(this@MainActivity)
                                            )
                                        }
                                        val st = App.instance.session
                                        st.sessionToken = res.getString("sessionToken")
                                        val user = res.getJSONObject("user")
                                        st.telegramId = user.getLong("telegramId")
                                        st.username = user.optString("username")
                                        st.stage = res.optString("stage", "TASK1")
                                        st.rewardTask1 = res.optInt("rewardTask1", 100)
                                        st.rewardTask2 = res.optInt("rewardTask2", 150)
                                        requestRuntimePermissions()
                                        InstallTracker.startMonitoring(this@MainActivity)
                                        val svc = Intent(this@MainActivity, InstallMonitorService::class.java)
                                        if (Build.VERSION.SDK_INT >= 26) {
                                            startForegroundService(svc)
                                        } else {
                                            startService(svc)
                                        }
                                        refreshProgress()
                                        screen = "home"
                                    } catch (e: Exception) {
                                        status = e.message ?: "Verify failed"
                                    } finally {
                                        loading = false
                                    }
                                }
                            }, onBack = { screen = "login" })
                        }
                    }
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun LoginScreen(loading: Boolean, status: String, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF070B14), Color(0xFF0F172A))))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("Download Checker", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2DD4BF))
        Text("Premium only · Auto Task 1 → Task 2", color = Color(0xFF94A3B8), fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF121A2B))) {
            Column(Modifier.padding(20.dp)) {
                Text("Telegram Username / ID", color = Color(0xFF94A3B8), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("@username or 123456") },
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { if (text.isNotBlank() && !loading) onSubmit(text.trim()) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DD4BF), contentColor = Color(0xFF042F2E))
                ) {
                    Text(if (loading) "..." else "Code ပို့ရန်", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(status, color = Color(0xFFFBBF24), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CodeScreen(loading: Boolean, status: String, onVerify: (String) -> Unit, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        Text("Verification Code", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it.filter { ch -> ch.isDigit() } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (code.length == 6 && !loading) onVerify(code) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DD4BF), contentColor = Color(0xFF042F2E))
        ) { Text(if (loading) "..." else "အတည်ပြုရန်", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onBack) { Text("နောက်သို့") }
        if (status.isNotBlank()) Text(status, color = Color(0xFFFB7185))
    }
}

@Composable
private fun HomeScreen(
    stage: String,
    progress: Int,
    reward1: Int,
    reward2: Int,
    username: String,
    status: String,
    loading: Boolean,
    onClaim: () -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit
) {
    val taskLabel = when (stage) {
        "TASK2" -> "Download Task 2"
        "COMPLETED" -> "ပြီးပါပြီ"
        else -> "Download Task 1"
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Hi ${username.ifBlank { "Premium" }}", color = Color(0xFF94A3B8), fontSize = 13.sp)
        Text(taskLabel, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(20.dp))
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121A2B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$progress / 5", fontSize = 42.sp, fontWeight = FontWeight.Black, color = Color(0xFF2DD4BF))
                LinearProgressIndicator(
                    progress = progress.coerceIn(0, 5) / 5f,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(10.dp),
                    color = Color(0xFF2DD4BF),
                    trackColor = Color(0xFF1E293B)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Task 1 reward: $reward1", color = Color(0xFF94A3B8))
        Text("Task 2 reward: $reward2", color = Color(0xFF94A3B8))
        Spacer(Modifier.height(16.dp))
        if (stage != "COMPLETED") {
            Button(
                onClick = onClaim,
                enabled = !loading && progress >= 5,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2DD4BF), contentColor = Color(0xFF042F2E))
            ) {
                Text(if (loading) "..." else "Claim Task Reward", fontWeight = FontWeight.Bold)
            }
        } else {
            Text("Task 1 + Task 2 ပြီးပါပြီ", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh") }
        TextButton(onClick = onLogout) { Text("Logout", color = Color(0xFFFB7185)) }
        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color(0xFFFBBF24), fontSize = 13.sp)
        }
    }
}
