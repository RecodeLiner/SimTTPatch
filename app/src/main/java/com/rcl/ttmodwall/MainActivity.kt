package com.rcl.ttmodwall

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyFrameworkInitializer
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.android.internal.telephony.ICarrierConfigLoader
import com.rcl.ttmodwall.ui.theme.SimTTPatchTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

class MainActivity : ComponentActivity() {
    private val permissionState = MutableStateFlow<State>(getCurrentState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        HiddenApiBypass.addHiddenApiExemptions("I", "L")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SimTTPatchTheme {
                val state by permissionState.collectAsState()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        PermissionButton(
                            state = state,
                            onUpdateState = { permissionState.value = getCurrentState() }
                        )
                    }
                }
            }
        }
    }

    private fun checkPermission(code: Int) : Boolean {
        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            return false
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            return true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
            return false
        } else {
            // Request the permission
            Shizuku.requestPermission(code)
            return false
        }
    }


    fun getCurrentState() : State {
        if (Shizuku.getBinder() == null) return State.Uninitialized
        return if (checkPermission(0)) State.Enabled else State.Disabled
    }
    sealed interface State {
        object Uninitialized : State
        object Enabled : State
        object Disabled : State
    }
}

@Composable
fun PermissionButton(
    state: MainActivity.State,
    onUpdateState: () -> Unit
) {
    when(state) {
        is MainActivity.State.Uninitialized -> {
            Text("Shizuku is not initialized")
        }
        is MainActivity.State.Disabled -> {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)){
                Text("Module disabled")
                Button(
                    onClick = {
                        Shizuku.requestPermission(0)
                        onUpdateState()
                    }
                ) {
                    Text("Enable")
                }
            }
        }
        is MainActivity.State.Enabled -> {
            Text("Module enabled")
            val first = SubscriptionManager.getSubscriptionId(0)
            val second = SubscriptionManager.getSubscriptionId(1)

            if (first != -1) {
                overrideCarrierConfig(first)
            }
            if (second != -1) {
                overrideCarrierConfig(second)
            }
        }
    }
}

private fun overrideCarrierConfig(subId: Int) {
    val carrierConfigLoader = ICarrierConfigLoader.Stub.asInterface(
        ShizukuBinderWrapper(
            TelephonyFrameworkInitializer
                .getTelephonyServiceManager()
                .carrierConfigServiceRegisterer
                .get()
        )
    )
    val bundle = PersistableBundle()
    //enable VoLte by pixel-volte-enabler
    bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
    //change country to Belarus
    bundle.putString(CarrierConfigManager.KEY_SIM_COUNTRY_ISO_OVERRIDE_STRING, "by")
    carrierConfigLoader.overrideConfig(subId, bundle, true)
}