package com.hackerai.walletseeker.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerai.walletseeker.data.model.*
import com.hackerai.walletseeker.data.rpc.BalanceChecker
import com.hackerai.walletseeker.data.scanner.FileScanner
import com.hackerai.walletseeker.data.transfer.TransferManager
import com.hackerai.walletseeker.data.decrypt.WalletDecryptor
import com.hackerai.walletseeker.domain.AppConfig
import com.hackerai.walletseeker.service.ScanWorker
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.util.Locale

data class Stats(
    val total: Int,
    val funds: Int,
    val protected: Int,
    val empty: Int,
    val transferred: Int,
    val errors: Int,
    val totalBalanceUsd: Double
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    onNavigateToConfig: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(AppConfig()) }
    var wallets by remember { mutableStateOf<List<WalletModel>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0) }
    var currentStatus by remember { mutableStateOf("Ready") }
    var showTransferDialog by remember { mutableStateOf(false) }
    var showDecryptDialog by remember { mutableStateOf(false) }
    var showSweepAllDialog by remember { mutableStateOf(false) }
    var selectedWalletForAction by remember { mutableStateOf<WalletModel?>(null) }
    var snackbarHostState by remember { mutableStateOf(SnackbarHostState()) }
    var filterOption by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }

    val usdFormat = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val cryptoFormat = remember {
        NumberFormat.getInstance(Locale.US).apply {
            maximumFractionDigits = 8
            minimumFractionDigits = 0
        }
    }

    // Load config
    LaunchedEffect(Unit) {
        val configFile = File(context.filesDir, "config.json")
        config = AppConfig.load(configFile)
    }

    // Oblicz statystyki
    val stats = remember(wallets) {
        Stats(
            total = wallets.size,
            funds = wallets.count { it.status == WalletStatus.HAS_FUNDS || it.status == WalletStatus.HAS_TOKENS },
            protected = wallets.count { it.status == WalletStatus.PROTECTED },
            empty = wallets.count { it.status == WalletStatus.EMPTY },
            transferred = wallets.count { it.status == WalletStatus.TRANSFERRED },
            errors = wallets.count { it.status == WalletStatus.ERROR || it.status == WalletStatus.INVALID_KEY },
            totalBalanceUsd = wallets.sumOf { it.balanceUsd }
        )
    }

    // Filtrowanie i sortowanie
    val filteredWallets = remember(wallets, filterOption, searchQuery) {
        var result = when (filterOption) {
            "funds" -> wallets.filter { it.status == WalletStatus.HAS_FUNDS || it.status == WalletStatus.HAS_TOKENS }
            "protected" -> wallets.filter { it.status == WalletStatus.PROTECTED }
            "empty" -> wallets.filter { it.status == WalletStatus.EMPTY }
            "transferred" -> wallets.filter { it.status == WalletStatus.TRANSFERRED }
            "errors" -> wallets.filter { it.status == WalletStatus.ERROR || it.status == WalletStatus.INVALID_KEY }
            else -> wallets
        }

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            result = result.filter {
                it.fileName.lowercase().contains(query) ||
                it.address?.lowercase()?.contains(query) == true ||
                it.privateKey?.lowercase()?.contains(query) == true ||
                it.walletType.name.lowercase().contains(query)
            }
        }

        result.sortedByDescending { it.balance }
    }

    // Całkowite saldo
    val totalBalance = remember(wallets) {
        wallets.filter { it.status == WalletStatus.HAS_FUNDS }
            .sumOf { it.balance }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Wallet Seeker", fontWeight = FontWeight.Bold)
                        if (isScanning) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                            Text(
                                "Scanning: $scanProgress files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                actions = {
                    if (totalBalance > 0) {
                        Text(
                            "$${String.format("%.2f", stats.totalBalanceUsd)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onNavigateToConfig) {
                        Icon(Icons.Default.Settings, "Configuration")
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Schedule scan (24h)") },
                            leadingIcon = { Icon(Icons.Default.Schedule, null) },
                            onClick = {
                                ScanWorker.schedule(context)
                                showMenu = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Scan scheduled every 24 hours"
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel schedule") },
                            leadingIcon = { Icon(Icons.Default.CancelScheduleSend, null) },
                            onClick = {
                                ScanWorker.cancel(context)
                                showMenu = false
                            }
                        )
                        if (stats.funds > 0) {
                            DropdownMenuItem(
                                text = { Text("Sweep all (${stats.funds} wallets)") },
                                leadingIcon = { Icon(Icons.Default.Send, null) },
                                onClick = {
                                    showSweepAllDialog = true
                                    showMenu = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Request permissions") },
                            leadingIcon = { Icon(Icons.Default.Security, null) },
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                    )
                                }
                                showMenu = false
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isScanning) {
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            isScanning = true
                            currentStatus = "Scanning files..."
                            val configFile = File(context.filesDir, "config.json")
                            config = AppConfig.load(configFile)

                            val scanner = FileScanner(context, config)
                            val results = mutableListOf<WalletModel>()

                            // Faza 1: skanowanie
                            scanner.scanForWallets().collect { progress ->
                                results.clear()
                                results.addAll(progress.wallets)
                                scanProgress = progress.totalFilesScanned
                                wallets = results.toList()
                            }

                            currentStatus = "Checking ${results.size} wallets..."
                            val checker = BalanceChecker(config)
                            val checked = mutableListOf<WalletModel>()

                            // Faza 2: sprawdzanie sald
                            checker.checkAllBalances(results).collect { checkedWallet ->
                                val idx = checked.indexOfFirst { it.id == checkedWallet.id }
                                if (idx >= 0) checked[idx] = checkedWallet
                                else checked.add(checkedWallet)
                                wallets = checked.toList()
                            }

                            wallets = checked.toList()
                            isScanning = false

                            val fundsFound = wallets.count {
                                it.status == WalletStatus.HAS_FUNDS || it.status == WalletStatus.HAS_TOKENS
                            }
                            currentStatus = "Done - $fundsFound wallets with funds"

                            if (fundsFound > 0 && config.notificationOnFundsFound) {
                                snackbarHostState.showSnackbar(
                                    "Found $fundsFound wallet(s) with funds! Total: ${
                                        usdFormat.format(stats.totalBalanceUsd)
                                    }"
                                )
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Search, null) },
                    text = { Text("Start Scan") }
                )
            } else {
                FloatingActionButton(onClick = { /* Można dodać cancel */ }) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status bar
            if (currentStatus.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentStatus,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Statystyki
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("Total", stats.total.toString(), Color.Gray)
                        StatItem("\uD83D\uDFE2 Funds", stats.funds.toString(), Color(0xFF00E676))
                        StatItem("\uD83D\uDD12 Locked", stats.protected.toString(), Color(0xFFFF9800))
                        StatItem("\u26AA Empty", stats.empty.toString(), Color(0xFF9E9E9E))
                        StatItem("\u2705 Sent", stats.transferred.toString(), Color(0xFF00BFA5))
                        StatItem("\u274C Errors", stats.errors.toString(), Color(0xFFFF1744))
                    }
                    if (stats.totalBalanceUsd > 0) {
                        Text(
                            "Total value: ${usdFormat.format(stats.totalBalanceUsd)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp)
                        )
                    }
                }
            }

            // Filtry i wyszukiwarka
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Search
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        placeholder = { Text("Search wallets...") },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))

                    // Filter chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("all", "funds", "protected", "empty", "transferred", "errors").forEach { filter ->
                            FilterChip(
                                selected = filterOption == filter,
                                onClick = { filterOption = filter },
                                label = {
                                    Text(
                                        filter.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }

            // Lista portfeli
            if (filteredWallets.isEmpty() && !isScanning) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            when {
                                wallets.isEmpty() -> "No wallets found. Tap Start Scan."
                                searchQuery.isNotBlank() -> "No wallets matching \"$searchQuery\""
                                else -> "No wallets in this category"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = filteredWallets,
                        key = { it.id }
                    ) { wallet ->
                        WalletCard(
                            wallet = wallet,
                            onClick = { onNavigateToDetail(wallet.id) },
                            onSweep = {
                                selectedWalletForAction = wallet
                                showTransferDialog = true
                            },
                            onDecrypt = {
                                selectedWalletForAction = wallet
                                showDecryptDialog = true
                            }
                        )
                    }

                    // Footer
                    if (filteredWallets.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(80.dp)) // space for FAB
                        }
                    }
                }
            }
        }
    }

    // === DIALOGI ===

    // Transfer dialog
    if (showTransferDialog && selectedWalletForAction != null) {
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Sweep Wallet", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Send all funds to destination wallet:")
                    if (config.destinationWallet.isNotBlank()) {
                        Text(
                            config.destinationWallet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "Not configured - go to Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("File: ${selectedWalletForAction?.fileName}")
                    Text("Type: ${selectedWalletForAction?.walletType?.name}")
                    if (selectedWalletForAction?.address != null) {
                        Text("Address: ${selectedWalletForAction?.address?.take(10)}...")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Balance: ${cryptoFormat.format(selectedWalletForAction?.balance)} ${selectedWalletForAction?.cryptoType?.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (selectedWalletForAction?.balanceUsd ?: 0.0 > 0) {
                        Text(
                            "≈ ${usdFormat.format(selectedWalletForAction?.balanceUsd)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val wallet = selectedWalletForAction ?: return@launch
                            val configFile = File(context.filesDir, "config.json")
                            config = AppConfig.load(configFile)
                            val tm = TransferManager(config)
                            val result = tm.sweepWallet(wallet)

                            if (result.success) {
                                snackbarHostState.showSnackbar(
                                    "Sent ${cryptoFormat.format(result.amountSent)} ${wallet.cryptoType.name} - TX: ${result.txHash?.take(12)}..."
                                )
                                wallets = wallets.map {
                                    if (it.id == wallet.id) it.copy(
                                        status = WalletStatus.TRANSFERRED,
                                        lastTransferAt = java.util.Date()
                                    ) else it
                                }
                            } else {
                                snackbarHostState.showSnackbar(
                                    "Transfer failed: ${result.errorMessage}"
                                )
                            }
                            showTransferDialog = false
                        }
                    },
                    enabled = config.destinationWallet.isNotBlank()
                ) { Text("SWEEP") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTransferDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Decrypt dialog
    if (showDecryptDialog && selectedWalletForAction != null) {
        var passwordInput by remember { mutableStateOf("") }
        var isDecrypting by remember { mutableStateOf(false) }
        var decryptResult by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                if (!isDecrypting) showDecryptDialog = false
            },
            title = { Text("Decrypt Wallet", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("File: ${selectedWalletForAction?.fileName}")
                    Text("Type: ${selectedWalletForAction?.walletType?.name}")
                    if (selectedWalletForAction?.walletType == WalletType.ETHEREUM_KEYSTORE) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Will try common passwords first",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isDecrypting) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text("Decrypting...", style = MaterialTheme.typography.bodySmall)
                    }

                    if (decryptResult != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            decryptResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (decryptResult!!.contains("Decrypted"))
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                if (!isDecrypting) {
                    Button(
                        onClick = {
                            scope.launch {
                                isDecrypting = true
                                decryptResult = null
                                val wallet = selectedWalletForAction ?: return@launch
                                val walletWithPw = if (passwordInput.isNotBlank())
                                    wallet.copy(keystorePassword = passwordInput)
                                else wallet

                                val decryptor = WalletDecryptor(config)
                                decryptor.decryptWallet(walletWithPw).collect { result ->
                                    if (result.success) {
                                        wallets = wallets.map {
                                            if (it.id == wallet.id) result.wallet else it
                                        }
                                        decryptResult = "Decrypted: ${result.password}"
                                        snackbarHostState.showSnackbar(
                                            "Wallet decrypted!"
                                        )
                                        showDecryptDialog = false
                                    } else {
                                        decryptResult = result.message
                                    }
                                }
                                isDecrypting = false
                            }
                        }
                    ) { Text("Decrypt") }
                }
            },
            dismissButton = {
                if (!isDecrypting) {
                    OutlinedButton(onClick = { showDecryptDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Sweep all dialog
    if (showSweepAllDialog) {
        AlertDialog(
            onDismissRequest = { showSweepAllDialog = false },
            title = { Text("Sweep All Wallets") },
            text = {
                Column {
                    Text("Send all funds from ${stats.funds} wallets to:")
                    Text(
                        config.destinationWallet.ifBlank { "NOT CONFIGURED" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Total value: ${usdFormat.format(stats.totalBalanceUsd)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This will sweep all wallets with funds.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            showSweepAllDialog = false
                            val configFile = File(context.filesDir, "config.json")
                            config = AppConfig.load(configFile)
                            val tm = TransferManager(config)

                            val fundWallets = wallets.filter {
                                it.status == WalletStatus.HAS_FUNDS
                            }

                            var successCount = 0
                            var failCount = 0

                            for (w in fundWallets) {
                                val result = tm.sweepWallet(w)
                                if (result.success) {
                                    successCount++
                                    wallets = wallets.map {
                                        if (it.id == w.id) it.copy(
                                            status = WalletStatus.TRANSFERRED,
                                            lastTransferAt = java.util.Date()
                                        ) else it
                                    }
                                } else {
                                    failCount++
                                }
                            }

                            snackbarHostState.showSnackbar(
                                "Swept $successCount wallets, $failCount failed"
                            )
                        }
                    },
                    enabled = config.destinationWallet.isNotBlank()
                ) { Text("SWEEP ALL") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSweepAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontWeight = FontWeight.Bold,
            color = color,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun WalletCard(
    wallet: WalletModel,
    onClick: () -> Unit,
    onSweep: () -> Unit,
    onDecrypt: () -> Unit
) {
    val statusColor = when (wallet.status) {
        WalletStatus.HAS_FUNDS -> Color(0xFF00E676)
        WalletStatus.HAS_TOKENS -> Color(0xFF00BCD4)
        WalletStatus.HAS_NFTS -> Color(0xFFE040FB)
        WalletStatus.TRANSFERRED -> Color(0xFF00BFA5)
        WalletStatus.PROTECTED -> Color(0xFFFF9800)
        WalletStatus.ERROR -> Color(0xFFFF1744)
        WalletStatus.INVALID_KEY -> Color(0xFFFF6D00)
        WalletStatus.PENDING -> Color(0xFF9E9E9E)
        WalletStatus.EMPTY -> Color(0xFF616161)
        WalletStatus.DECRYPTING -> Color(0xFF448AFF)
        WalletStatus.CHECKING -> Color(0xFF448AFF)
        WalletStatus.SWEEP_FAILED -> Color(0xFFFF1744)
        else -> Color(0xFF9E9E9E)
    }

    val statusEmoji = when (wallet.status) {
        WalletStatus.HAS_FUNDS -> "\uD83D\uDFE2"
        WalletStatus.HAS_TOKENS -> "\uD83D\uDD35"
        WalletStatus.TRANSFERRED -> "\u2705"
        WalletStatus.PROTECTED -> "\uD83D\uDD12"
        WalletStatus.EMPTY -> "\u26AA"
        WalletStatus.ERROR -> "\u274C"
        WalletStatus.INVALID_KEY -> "\u26A0\uFE0F"
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (wallet.status) {
                WalletStatus.HAS_FUNDS -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                WalletStatus.TRANSFERRED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Surface(
                modifier = Modifier.size(12.dp),
                shape = MaterialTheme.shapes.extraSmall,
                color = statusColor
            ) {}
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        wallet.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (wallet.isEncrypted) {
                        Spacer(Modifier.width(4.dp))
                        Text("\uD83D\uDD12", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row {
                    Text(
                        wallet.walletType.name.replace("_", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    if (wallet.address != null) {
                        Text(
                            " \u2022 ${wallet.address.take(8)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${cryptoFormat.format(wallet.balance)} ${wallet.cryptoType.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (wallet.balance > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                if (wallet.balanceUsd > 0) {
                    Text(
                        usdFormat.format(wallet.balanceUsd),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$statusEmoji ${wallet.status.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            // Action buttons
            if (wallet.status == WalletStatus.HAS_FUNDS) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onSweep,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        "Sweep",
                        modifier = Modifier