package com.hackerai.walletseeker.data.rpc

import com.hackerai.walletseeker.data.model.*
import com.hackerai.walletseeker.domain.AppConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Convert
import org.web3j.crypto.Credentials
import org.web3j.crypto.ECKeyPair
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class BalanceChecker(private val config: AppConfig) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(config.requestTimeoutSeconds.toLong(), TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", config.userAgent)
                .apply {
                    config.customHeaders.forEach { (k, v) -> header(k, v) }
                }
                .build()
            chain.proceed(request)
        }
        .build()

    suspend fun checkAllBalances(wallets: List<WalletModel>): Flow<WalletModel> = flow {
        if (wallets.isEmpty()) return@flow

        val semaphore = Semaphore(config.parallelChecks)
        val mutex = Mutex()

        coroutineScope {
            val jobs = wallets.map { wallet ->
                launch(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        val result = checkWallet(wallet)
                        mutex.withLock { emit(result) }
                    } finally {
                        semaphore.release()
                    }
                }
            }
            jobs.joinAll()
        }
    }

    suspend fun checkSingleWallet(wallet: WalletModel): WalletModel {
        return withContext(Dispatchers.IO) {
            checkWallet(wallet)
        }
    }

    private suspend fun checkWallet(wallet: WalletModel): WalletModel {
        return try {
            val result = when {
                wallet.status == WalletStatus.TRANSFERRED -> wallet
                wallet.status == WalletStatus.ERROR -> wallet
                wallet.walletType == WalletType.ETHEREUM_KEYSTORE -> wallet.copy(
                    status = WalletStatus.PROTECTED,
                    cryptoType = CryptoType.ETH
                )
                wallet.walletType == WalletType.MNEMONIC -> checkMnemonic(wallet)
                wallet.walletType == WalletType.ENCRYPTED_MNEMONIC -> wallet.copy(
                    status = WalletStatus.PROTECTED
                )
                wallet.walletType == WalletType.PRIVATE_KEY -> checkPrivateKey(wallet)
                wallet.walletType == WalletType.BITCOIN_CORE -> wallet.copy(
                    status = WalletStatus.PROTECTED
                )
                wallet.walletType == WalletType.EXCHANGE_EXPORT -> checkByAddress(wallet)
                wallet.address != null -> checkByAddress(wallet)
                else -> wallet.copy(
                    status = WalletStatus.ERROR,
                    errorMessage = "Cannot check: no address or key"
                )
            }
            result.copy(lastCheckedAt = java.util.Date())
        } catch (e: Exception) {
            wallet.copy(
                status = WalletStatus.ERROR,
                errorMessage = e.localizedMessage ?: e.message
            )
        }
    }

    private suspend fun checkPrivateKey(wallet: WalletModel): WalletModel {
        val pk = wallet.privateKey ?: return wallet.copy(
            status = WalletStatus.ERROR,
            errorMessage = "No private key"
        )

        return try {
            when {
                pk.startsWith("0x") || pk.length == 64 -> {
                    val creds = try {
                        val key = if (pk.startsWith("0x")) pk else "0x$pk"
                        Credentials.create(key)
                    } catch (e: Exception) {
                        return wallet.copy(
                            status = WalletStatus.INVALID_KEY,
                            errorMessage = "Invalid ETH key"
                        )
                    }
                    checkEthereumAddress(
                        wallet.copy(address = creds.address, cryptoType = CryptoType.ETH),
                        CryptoType.ETH
                    )
                }
                pk.matches(Regex("""[5KL][1-9A-HJ-NP-Za-km-z]{51}""")) -> {
                    checkBitcoinAddress(wallet)
                }
                else -> {
                    // Spróbuj jako ETH
                    val creds = try {
                        Credentials.create("0x$pk")
                    } catch (e: Exception) {
                        return wallet.copy(
                            status = WalletStatus.INVALID_KEY,
                            errorMessage = "Unrecognized key format"
                        )
                    }
                    checkEthereumAddress(
                        wallet.copy(address = creds.address, cryptoType = CryptoType.ETH),
                        CryptoType.ETH
                    )
                }
            }
        } catch (e: Exception) {
            wallet.copy(
                status = WalletStatus.ERROR,
                errorMessage = e.message
            )
        }
    }

    private suspend fun checkMnemonic(wallet: WalletModel): WalletModel {
        val mnemonic = wallet.mnemonic ?: return wallet.copy(
            status = WalletStatus.ERROR,
            errorMessage = "No mnemonic"
        )

        val derived = deriveWalletsFromMnemonic(mnemonic, 5)
        if (derived.isEmpty()) {
            return wallet.copy(
                status = WalletStatus.ERROR,
                errorMessage = "Failed to derive addresses"
            )
        }

        var bestResult = wallet.copy(address = derived.first().address)

        for (dw in derived) {
            val result = checkEthereumAddress(
                bestResult.copy(
                    address = dw.address,
                    privateKey = dw.privateKey,
                    cryptoType = CryptoType.ETH
                ),
                CryptoType.ETH
            )
            if (result.status == WalletStatus.HAS_FUNDS ||
                result.status == WalletStatus.HAS_TOKENS) {
                bestResult = result
                break
            }
            if (result.status != WalletStatus.ERROR) {
                bestResult = result
            }
        }

        return bestResult
    }

    private suspend fun checkEthereumAddress(
        wallet: WalletModel,
        chain: CryptoType
    ): WalletModel {
        val address = wallet.address ?: return wallet.copy(
            status = WalletStatus.ERROR,
            errorMessage = "No address"
        )

        val rpc = config.rpcEndpoints[chain.name]
            ?: return wallet.copy(
                status = WalletStatus.ERROR,
                errorMessage = "No RPC for ${chain.name}"
            )

        return try {
            val web3j = Web3j.build(HttpService(rpc))

            val balanceWei = web3j.ethGetBalance(
                address,
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST
            ).send().balance

            val balanceEth = Convert.fromWei(
                balanceWei.toString(),
                Convert.Unit.ETHER
            ).toDouble()

            if (balanceEth > 0.000001) {
                val usdPrice = getUsdPrice("ethereum")
                wallet.copy(
                    balance = balanceEth,
                    balanceUsd = balanceEth * usdPrice,
                    cryptoType = chain,
                    status = WalletStatus.HAS_FUNDS
                )
            } else {
                // Sprawdź tokeny
                val tokenBalances = checkErc20Tokens(address, web3j)
                if (tokenBalances.isNotEmpty()) {
                    wallet.copy(
                        balance = 0.0,
                        tokens = tokenBalances,
                        cryptoType = chain,
                        status = WalletStatus.HAS_TOKENS,
                        balanceUsd = tokenBalances.sumOf { it.balanceUsd }
                    )
                } else {
                    wallet.copy(
                        balance = 0.0,
                        cryptoType = chain,
                        status = WalletStatus.EMPTY
                    )
                }
            }
        } catch (e: Exception) {
            wallet.copy(
                status = WalletStatus.ERROR,
                errorMessage = "RPC error: ${e.message}"
            )
        }
    }

    private suspend fun checkBitcoinAddress(wallet: WalletModel): WalletModel {
        return try {
            val wif = wallet.privateKey ?: return wallet.copy(
                status = WalletStatus.ERROR,
                errorMessage = "No WIF key"
            )

            val ecKey = org.bitcoinj.core.DumpedPrivateKey.fromBase58(
                org.bitcoinj.params.MainNetParams.get(), wif
            ).key
            val address = ecKey.toAddress(
                org.bitcoinj.params.MainNetParams.get()
            ).toString()

            // Użyj blockchain.info API
            val url = "${config.rpcEndpoints["BTC"] ?: "https://blockchain.info"}/balance?active=$address"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).await()
            val body = response.body?.string() ?: "{}"

            val json = JsonParser.parseString(body).asJsonObject
            val balance = json.getAsJsonObject(address)?.get("final_balance")?.asLong
                ?.let { it / 100_000_000.0 } ?: 0.0

            wallet.copy(
                address = address,
                balance = balance,
                cryptoType = CryptoType.BTC,
                status = if (balance > 0) WalletStatus.HAS_FUNDS else WalletStatus.EMPTY
            )
        } catch (e: Exception) {
            wallet.copy(
                status = WalletStatus.ERROR,
                errorMessage = "BTC check error: ${e.message}"
            )
        }
    }

    private suspend fun checkByAddress(wallet: WalletModel): WalletModel {
        val address = wallet.address ?: return wallet.copy(
            status = WalletStatus.ERROR,
            errorMessage = "No address"
        )

        // Sprawdź ETH i EVM chainy
        val evmChains = listOf(
            CryptoType.ETH, CryptoType.BSC, CryptoType.POLYGON,
            CryptoType.AVAX, CryptoType.FTM
        )

        for (chain in evmChains) {
            val result = checkEthereumAddress(
                wallet.copy(cryptoType = chain),
                chain
            )
            if (result.status == WalletStatus.HAS_FUNDS ||
                result.status == WalletStatus.HAS_TOKENS) {
                return result
            }
        }

        return wallet.copy(status = WalletStatus.EMPTY)
    }

    private suspend fun checkErc20Tokens(
        address: String,
        web3j: Web3j
    ): List<TokenBalance> {
        // W pełnej wersji: query Etherscan/BSCscan API
        // Uproszczenie: zwracamy pustą listę
        return emptyList()
    }

    private fun deriveWalletsFromMnemonic(
        mnemonic: String,
        count: Int = 5
    ): List<WalletModel> {
        val wallets = mutableListOf<WalletModel>()

        try {
            val seed = io.github.novacrypto.bip39.SeedCalculator()
                .withMnemonic(mnemonic, "")
                .calculate()

            val masterKey = org.bitcoinj.crypto.DeterministicKey.fromSeed(
                seed,
                org.bitcoinj.crypto.DeterministicKey.CHILD_NUM
            )

            for (i in 0 until count) {
                try {
                    val key = org.bitcoinj.crypto.HDKeyDerivation.deriveChildKey(
                        masterKey, i
                    )
                    val ethKey = ECKeyPair.create(key.privKeyBytes)
                    val address = "0x" + Numeric.toHexStringNoPrefix(
                        ethKey.publicKey
                    ).substring(24)

                    wallets.add(
                        WalletModel(
                            fileName = "derived_$i",
                            fullPath = "m/44'/60'/0'/0/$i",
                            walletType = WalletType.MNEMONIC,
                            privateKey = ethKey.privateKey.toString(16)
                                .padStart(64, '0'),
                            address = address,
                            cryptoType = CryptoType.ETH,
                            status = WalletStatus.PENDING
                        )
                    )
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }

        return wallets
    }

    private suspend fun getUsdPrice(coinId: String): Double {
        return try {
            val url = "https://api.coingecko.com/api/v3/simple/price?ids=$coinId&vs_currencies=usd"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).await()
            val body = response.body?.string() ?: "{}"
            val json = JsonParser.parseString(body).asJsonObject
            json.getAsJsonObject(coinId)?.get("usd")?.asDouble ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }
}