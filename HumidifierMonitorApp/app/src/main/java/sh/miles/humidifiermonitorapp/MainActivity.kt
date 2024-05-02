package sh.miles.humidifiermonitorapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import sh.miles.humidifiermonitorapp.ui.theme.HumidifierMonitorAppTheme
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

const val BOTTOM_IN_WATER = 1
const val BOTTOM_TWO_IN_WATER = 2
const val ALL_IN_WATER = 3

class ThreadedClient {

    companion object {
        val SERVICE = Executors.newScheduledThreadPool(2)
    }

    private var worker: Future<*>? = null
    private var onStart: (Socket, Exception?) -> Unit = { _, _ -> }
    private var onDataLoop: (Socket, BufferedReader) -> Unit = { _, _ -> }
    private val running: AtomicBoolean = AtomicBoolean(false)

    fun commandOnStart(function: (Socket, Exception?) -> Unit) {
        this.onStart = function
    }

    fun commandDataLoop(function: (Socket, BufferedReader) -> Unit) {
        this.onDataLoop = function
    }

    fun start(ip: String, port: Int, onLoop: (Socket, BufferedReader) -> Unit) {
        commandDataLoop(onLoop)
        start(ip, port)
    }

    fun start(ip: String, port: Int) {
        running.set(true)
        if (worker == null || worker!!.isDone) {
            worker = SERVICE.submit { runSynchronously(ip, port) }
        }
    }

    fun close() {
        running.set(false)
        if (worker != null) {
            worker!!.get()
        }
    }

    private fun runSynchronously(ip: String, port: Int) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(ip, port))
            onStart.invoke(socket, null)
        } catch (exception: IOException) {
            onStart.invoke(socket, exception)
        }
        val input = socket.getInputStream()

        BufferedReader(InputStreamReader(input)).use { reader ->
            while (running.get()) {
                onDataLoop.invoke(socket, reader)
            }
        }


        socket.close()
    }
}

class ArduinoCommunicationClient() : ViewModel() {

    private var client: ThreadedClient = ThreadedClient()
    lateinit var isOpen: MutableState<Boolean>
    var connectedIp: AtomicReference<String> = AtomicReference("")
        private set
    var connectedPort: AtomicInteger = AtomicInteger(0)
        private set
    lateinit var lastSensorReading: MutableState<Int>

    var notifiedRecently: AtomicBoolean = AtomicBoolean(false)
        private set
    private var notifiedRecentlySwitch: Future<*>? = null
    lateinit var notificationManager: NotificationManager
    lateinit var notification: Notification

    fun connect(ip: String?, port: Int?) {
        if ((ip == null || port == null) || ip.isBlank()) {
            return
        }

        client.commandOnStart { socket, exception ->
            if (exception != null) {
                exception.printStackTrace()
                throw RuntimeException(exception)
            }

            isOpen.value = true
            connectedIp.set(ip)
            connectedPort.set(port)
        }

        client.start(ip, port) { socket, reader ->
            // we are only reading 1 digit 0, 1, 2 or 3 so this is a safe thing to read
            if (!reader.ready()) {
                return@start
            }

            val inWaterLast = lastSensorReading.value != 0
            lastSensorReading.value = Character.getNumericValue(reader.read())

            if (!inWaterLast && lastSensorReading.value >= BOTTOM_IN_WATER) {
                notifiedRecentlySwitch?.cancel(true)
                notifiedRecently.set(false)
            }

            if (lastSensorReading.value < BOTTOM_IN_WATER && !notifiedRecently.get()) {
                notificationManager.notify(Random.nextInt(), notification)
                notifiedRecently.set(true)
                notifiedRecentlySwitch =
                    ThreadedClient.SERVICE.schedule({ notifiedRecently.set(false) }, 60, TimeUnit.SECONDS)
            }
        }
    }

    fun close() {
        client.close()
        isOpen.value = false
        connectedIp.set("")
        connectedPort.set(0)
        lastSensorReading.value = -1
    }

}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clientModel = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(ArduinoCommunicationClient::class.java)
        val notificationManager = this.baseContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    "humidifier_notification",
                    "humidifier",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val notification = NotificationCompat.Builder(this.baseContext, "humidifier_notification")
            .setContentTitle("Humidifier Status")
            .setContentText("Your humidifier is low on water time to change it!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .setAutoCancel(true)
            .build()
        clientModel.notification = notification
        clientModel.notificationManager = notificationManager
        setContent {
            val ipText: MutableState<String> = remember { mutableStateOf("") }
            val portValue: MutableState<Int> = remember { mutableStateOf(0) }
            val socketOpen: MutableState<Boolean> = remember { mutableStateOf(false) }
            val modelReader: MutableState<Int> = remember { mutableStateOf(-1) }
            clientModel.isOpen = socketOpen
            clientModel.lastSensorReading = modelReader

            HumidifierMonitorAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column {
                        SettingsStash(ipText, portValue)
                        ConnectStash(ipText = ipText, portValue = portValue, clientModel = clientModel)
                        DataAcknowledgementStash(modelReading = clientModel.lastSensorReading)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsStash(ipText: MutableState<String>, portValue: MutableState<Int>, modifier: Modifier = Modifier) {
    Column {
        Text(
            text = "Settings", fontSize = 16.sp, textAlign = TextAlign.Center, modifier = modifier.padding(8.dp)
        )
        Row {
            OutlinedTextField(value = ipText.value, onValueChange = {
                ipText.value = it
            }, label = { Text(text = "IP Address ") }, maxLines = 1
            )

            val portText = if (portValue.value == 0) "" else portValue.value
            OutlinedTextField(value = portValue.value.toString(), onValueChange = {
                if (it.toIntOrNull() != null) {
                    portValue.value = it.toInt();
                } else if (it.isBlank()) {
                    portValue.value = 0
                }
            }, label = { Text(text = "Port") }, maxLines = 1
            )
        }
    }
}

@Composable
fun ConnectStash(
    ipText: MutableState<String>,
    portValue: MutableState<Int>,
    clientModel: ArduinoCommunicationClient,
    modifier: Modifier = Modifier
) {
    Row {
        OutlinedButton(onClick = {
            if (clientModel.isOpen.value) {
                clientModel.close()
            } else {
                clientModel.connect(ipText.value, portValue.value)
            }
        }) {
            if (clientModel.isOpen.value) {
                Text("Disconnect")
            } else {
                Text("Connect")
            }
        }

        if (clientModel.isOpen.value) {
            Text(
                text = "Connected to ${clientModel.connectedIp} on port ${clientModel.connectedPort}",
                color = Color.Green,
                textAlign = TextAlign.Center,
                modifier = modifier
                    .padding(8.dp)
                    .padding(horizontal = 0.dp, vertical = 3.5.dp)
            )
        } else {
            Text(
                text = "Not Connected On Any Port",
                color = Color.Red,
                textAlign = TextAlign.Center,
                modifier = modifier
                    .padding(8.dp)
                    .padding(horizontal = 0.dp, vertical = 3.5.dp)
            )
        }
    }
}

@Composable
fun DataAcknowledgementStash(modelReading: MutableState<Int>) {
    Text(text = "Last Reading ${modelReading.value}")
}
