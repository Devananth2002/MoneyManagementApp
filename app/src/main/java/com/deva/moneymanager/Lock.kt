@file:OptIn(ExperimentalMaterial3Api::class)

package com.deva.moneymanager

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.delay

// True while the lock screen is showing
var locked by mutableStateOf(false)
var lockInit = false

val PHONE_AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
val BIO_AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK

fun bioAvailable(ctx: Context, auth: Int): Boolean =
    BiometricManager.from(ctx).canAuthenticate(auth) == BiometricManager.BIOMETRIC_SUCCESS

fun showBiometric(activity: FragmentActivity, auth: Int, negative: String?, onOk: () -> Unit, onCancel: () -> Unit = {}) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onCancel() }
        })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock My Money")
        .setAllowedAuthenticators(auth)
        .apply { if (negative != null) setNegativeButtonText(negative) }
        .build()
    prompt.authenticate(info)
}

// Keeps the lock settings. The PIN itself is never stored, only a salted hash of it.
class Security(ctx: Context) {
    private val p = ctx.getSharedPreferences("mymoney_lock", Context.MODE_PRIVATE)

    var enabled by mutableStateOf(p.getBoolean("enabled", false))
        private set
    var method by mutableStateOf(p.getString("method", "pin") ?: "pin")
        private set
    var bio by mutableStateOf(p.getBoolean("bio", false))
        private set
    var pinLen by mutableStateOf(p.getInt("pinLen", 4))
        private set

    private fun hash(salt: ByteArray, pin: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 20000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }

    private fun save(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.edit().putString("salt", Base64.getEncoder().encodeToString(salt))
            .putString("hash", hash(salt, pin)).putInt("pinLen", pin.length).apply()
        pinLen = pin.length
    }

    fun enablePin(pin: String, useBio: Boolean) {
        save(pin)
        p.edit().putBoolean("enabled", true).putString("method", "pin").putBoolean("bio", useBio).apply()
        enabled = true; method = "pin"; bio = useBio
    }

    fun changePin(pin: String) { save(pin) }

    fun enablePhone() {
        p.edit().putBoolean("enabled", true).putString("method", "phone").apply()
        enabled = true; method = "phone"
    }

    fun setBio(on: Boolean) { p.edit().putBoolean("bio", on).apply(); bio = on }

    fun disable() { p.edit().clear().apply(); enabled = false; bio = false }

    fun check(pin: String): Boolean {
        if (!p.contains("salt")) return false
        val salt = Base64.getDecoder().decode(p.getString("salt", "") ?: "")
        return hash(salt, pin) == p.getString("hash", "")
    }
}

@Composable
fun LockScreen(sec: Security, activity: FragmentActivity, onUnlock: () -> Unit) {
    val phone = sec.method == "phone"
    val bioOk = remember { sec.bio && bioAvailable(activity, BIO_AUTH) }
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var fails by remember { mutableStateOf(0) }
    var blockedUntil by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var help by remember { mutableStateOf(false) }
    val blocked = now < blockedUntil

    fun prompt() {
        if (phone) showBiometric(activity, PHONE_AUTH, null, onUnlock)
        else if (bioOk) showBiometric(activity, BIO_AUTH, "Use PIN", onUnlock)
    }
    LaunchedEffect(Unit) { delay(350); prompt() }
    LaunchedEffect(blockedUntil) {
        while (System.currentTimeMillis() < blockedUntil) { now = System.currentTimeMillis(); delay(500) }
        now = System.currentTimeMillis()
    }
    fun press(k: String) {
        if (blocked || phone) return
        wrong = false
        entered += k
        if (entered.length == sec.pinLen) {
            if (sec.check(entered)) onUnlock()
            else {
                fails += 1; wrong = true; entered = ""
                if (fails >= 5) { blockedUntil = System.currentTimeMillis() + 30_000; now = System.currentTimeMillis(); fails = 0 }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🔒", fontSize = 44.sp)
        Spacer(Modifier.height(12.dp))
        Text("My Money is locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Muted(if (phone) "Use your fingerprint or phone screen lock" else "Enter your PIN")
        Spacer(Modifier.height(24.dp))
        if (phone) {
            Button(onClick = { prompt() }, shape = RoundedCornerShape(14.dp)) { Text("Unlock") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(sec.pinLen) { i ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(
                        if (i < entered.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                }
            }
            Spacer(Modifier.height(10.dp))
            val secs = ((blockedUntil - now) / 1000 + 1).coerceAtLeast(0)
            Text(
                if (blocked) "Too many tries. Wait $secs seconds" else if (wrong) "Wrong PIN, try again" else " ",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(14.dp))
            val keys = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
                listOf(if (bioOk) "bio" else "", "0", "del"))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                keys.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEach { k ->
                            PadKey(k, blocked) {
                                when (k) {
                                    "bio" -> prompt()
                                    "del" -> entered = entered.dropLast(1)
                                    "" -> {}
                                    else -> press(k)
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { help = !help }) { Text("Forgot PIN?") }
            if (help) Muted("Your data is only on this phone. Reinstall the app, then restore your backup file from More.")
        }
    }
}

@Composable
private fun PadKey(k: String, disabled: Boolean, onClick: () -> Unit) {
    if (k.isEmpty()) { Spacer(Modifier.size(72.dp)); return }
    val label = when (k) { "bio" -> "👆"; "del" -> "⌫"; else -> k }
    Surface(
        onClick = onClick, enabled = !disabled, shape = CircleShape, modifier = Modifier.size(72.dp),
        color = if (k == "bio" || k == "del") Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 24.sp) }
    }
}

@Composable
fun SetPinDialog(title: String, onDismiss: () -> Unit, onSet: (String) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val okLen = a.length in 4..6
    val match = a == b
    AppDialog(title, onDismiss, { onSet(a) }, emoji = "🔑", confirm = "Set PIN", confirmEnabled = okLen && match) {
        Muted("Choose 4 to 6 digits. If you forget it, you will need to reinstall the app and restore your backup.")
        OutlinedTextField(
            a, { v -> if (v.length <= 6 && v.all { c -> c.isDigit() }) a = v }, Modifier.fillMaxWidth(),
            label = { Text("New PIN") }, singleLine = true, shape = RoundedCornerShape(14.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        OutlinedTextField(
            b, { v -> if (v.length <= 6 && v.all { c -> c.isDigit() }) b = v }, Modifier.fillMaxWidth(),
            label = { Text("Confirm PIN") }, singleLine = true, shape = RoundedCornerShape(14.dp),
            isError = b.isNotEmpty() && !match, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
    }
}
