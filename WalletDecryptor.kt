package com.hackerai.walletseeker.data.decrypt

import com.hackerai.walletseeker.data.model.*
import com.hackerai.walletseeker.domain.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.web3j.crypto.CipherException
import org.web3j.crypto.WalletUtils
import java.io.File

class WalletDecryptor(private val config: AppConfig = AppConfig()) {

    data class DecryptResult(
        val wallet: WalletModel,
        val password: String?,
        val success: Boolean,
        val message: String
    )

    // Rozszerzona lista haseł
    private val commonPasswords = listOf(
        "", "password", "123456", "12345678", "qwerty", "admin",
        "bitcoin", "ethereum", "crypto", "wallet", "seed", "secret",
        "trust", "metamask", "ledger", "trezor", "1234", "2023", "2024",
        "password1", "password123", "Passw0rd", "P@ssw0rd",
        "aaa", "1111", "test", "abc123", "0000", "1q2w3e4r",
        "qwerty123", "abcd", "pass", "pass123", "changeme",
        "root", "toor", "letmein", "welcome", "monkey",
        "dragon", "master", "login", "princess", "shadow",
        "sunshine", "trustwallet", "metamask123", "123456789",
        "password!", "qwerty12345", "abc123456", "1234567890",
        "111111", "121212", "654321", "987654", "qazwsx",
        "1qaz2wsx", "3edc4rfv", "zaq1xsw2", "xsw2zaq1",
        "", " ", "  ", "   ", "    ",
        "pass1234", "pass12345", "P@ssword", "Pa\$\$w0rd",
        "test123", "test1234", "admin123", "Admin123",
        "Wallet", "wallet1", "Wallet1", "WALLET",
        "Crypto", "crypto1", "CRYPTO", "crypto123",
        "ETH", "eth", "BTC", "btc", "SOL", "sol",
        "MyWallet", "mywallet", "my_wallet",
        "backup", "Backup", "BACKUP",
        "recovery", "Recovery", "RECOVERY",
        "seed", "Seed", "SEED", "seed123",
        "mnemonic", "Mnemonic", "MNEMONIC",
        "private", "Private", "PRIVATE",
        "key", "Key", "KEY", "keys", "Keys"
    )

    fun decryptWallet(wallet: WalletModel): Flow<DecryptResult> = flow {
        when (wallet.walletType) {
            WalletType.ETHEREUM_KEYSTORE -> {
                emitAll(decryptKeystore(wallet))
            }
            WalletType.ENCRYPTED_MNEMONIC -> {
                emit(
                    DecryptResult(
                        wallet, null, false,
                        "Encrypted mnemonic requires manual decryption"
                    )
                )
            }
            WalletType.BITCOIN_CORE -> {
                emit(
                    DecryptResult(
                        wallet, null, false,
                        "Bitcoin Core wallet.dat requires external tools"
                    )
                )
            }
            else -> {
                emit(
                    DecryptResult(
                        wallet, null, false,
                        "No automatic decryption for ${wallet.walletType.name}"
                    )
                )
            }
        }
    }

    private suspend fun decryptKeystore(wallet: WalletModel): Flow<DecryptResult> = flow {
        val keystoreJson = wallet.keystoreJson ?: return@flow

        // 1. Najpierw podane hasło
        if (!wallet.keystorePassword.isNullOrBlank()) {
            try {
                val creds = WalletUtils.loadJsonCredentials(
                    wallet.keystorePassword, keystoreJson
                )
                emit(
                    DecryptResult(
                        wallet.copy(
                            privateKey = creds.ecKeyPair.privateKey
                                .toString(16).padStart(64, '0'),
                            address = creds.address,
                            status = WalletStatus.PENDING,
                            keystorePassword = wallet.keystorePassword
                        ),
                        wallet.keystorePassword,
                        true,
                        "Decrypted with provided password"
                    )
                )
                return@flow
            } catch (_: CipherException) { }
            catch (e: Exception) {
                emit(
                    DecryptResult(
                        wallet, wallet.keystorePassword, false,
                        "Error with provided password: ${e.message}"
                    )
                )
                return@flow
            }
        }

        // 2. Brute-force z listy common passwords
        var attempts = 0
        for (password in commonPasswords) {
            attempts++
            if (attempts > config.maxDecryptAttempts) break

            try {
                val creds = WalletUtils.loadJsonCredentials(password, keystoreJson)
                emit(
                    DecryptResult(
                        wallet.copy(
                            privateKey = creds.ecKeyPair.privateKey
                                .toString(16).padStart(64, '0'),
                            address = creds.address,
                            status = WalletStatus.PENDING,
                            keystorePassword = password
                        ),
                        password,
                        true,
                        "Decrypted after $attempts attempts, password: $password"
                    )
                )
                return@flow
            } catch (_: CipherException) { }
        }

        // 3. Spróbuj załadować własny wordlist
        if (config.bruteForcePasswords) {
            try {
                val wordlistFile = File(config.customWordlistPath)
                if (wordlistFile.exists()) {
                    wordlistFile.useLines { lines ->
                        lines.forEach { line ->
                            val password = line.trim()
                            if (password.isNotBlank()) {
                                attempts++
                                if (attempts > config.maxDecryptAttempts) return@useLines

                                try {
                                    val creds = WalletUtils.loadJsonCredentials(
                                        password, keystoreJson
                                    )
                                    emit(
                                        DecryptResult(
                                            wallet.copy(
                                                privateKey = creds.ecKeyPair.privateKey
                                                    .toString(16).padStart(64, '0'),
                                                address = creds.address,
                                                status = WalletStatus.PENDING,
                                                keystorePassword = password
                                            ),
                                            password,
                                            true,
                                            "Decrypted with wordlist password: $password"
                                        )
                                    )
                                    return@flow
                                } catch (_: CipherException) { }
                            }
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        emit(
            DecryptResult(
                wallet, null, false,
                "Password not found after $attempts attempts"
            )
        )
    }
}