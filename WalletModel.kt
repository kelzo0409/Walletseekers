package com.hackerai.walletseeker.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date
import java.util.UUID

@Parcelize
data class WalletModel(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fullPath: String,
    val fileSizeBytes: Long = 0,
    val walletType: WalletType,
    val cryptoType: CryptoType = CryptoType.UNKNOWN,
    val mnemonic: String? = null,
    val privateKey: String? = null,
    val keystoreJson: String? = null,
    val keystorePassword: String? = null,
    val address: String? = null,
    val balance: Double = 0.0,
    val balanceUsd: Double = 0.0,
    val tokens: List<TokenBalance> = emptyList(),
    val nfts: List<String> = emptyList(),
    val isEncrypted: Boolean = false,
    val encryptionType: EncryptionType = EncryptionType.NONE,
    val createdAt: Date = Date(),
    val lastCheckedAt: Date? = null,
    val lastTransferAt: Date? = null,
    val status: WalletStatus = WalletStatus.PENDING,
    val errorMessage: String? = null
) : Parcelable

@Parcelize
data class TokenBalance(
    val contractAddress: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val balance: Double,
    val balanceUsd: Double = 0.0
) : Parcelable

enum class WalletType {
    BITCOIN_CORE,
    ETHEREUM_KEYSTORE,
    MNEMONIC,
    ENCRYPTED_MNEMONIC,
    PRIVATE_KEY,
    MULTI_SIG,
    SEED_FILE,
    BROWSER_EXTENSION,
    MOBILE_BACKUP,
    EXCHANGE_EXPORT,
    HARDWARE_BACKUP,
    PAPER_WALLET,
    CUSTOM,
    UNKNOWN
}

enum class CryptoType(val displayName: String) {
    BTC("Bitcoin"),
    ETH("Ethereum"),
    BSC("Binance Smart Chain"),
    POLYGON("Polygon"),
    SOL("Solana"),
    TRX("TRON"),
    AVAX("Avalanche"),
    FTM("Fantom"),
    ARB("Arbitrum"),
    OP("Optimism"),
    MATIC("Polygon (old)"),
    ATOM("Cosmos"),
    DOT("Polkadot"),
    ADA("Cardano"),
    XRP("XRP"),
    LTC("Litecoin"),
    BCH("Bitcoin Cash"),
    XMR("Monero"),
    ZEC("Zcash"),
    DASH("Dash"),
    DOGE("Dogecoin"),
    UNKNOWN("Unknown");

    override fun toString() = displayName
}

enum class WalletStatus {
    PENDING,
    CHECKING,
    DECRYPTING,
    HAS_FUNDS,
    HAS_TOKENS,
    HAS_NFTS,
    EMPTY,
    TRANSFERRED,
    ERROR,
    SWEEP_FAILED,
    INVALID_KEY,
    PROTECTED
}

enum class EncryptionType {
    NONE,
    AES_256,
    ETHEREUM_KEYSTORE,
    BITCOIN_WALLET_DAT,
    GPG,
    VERACRYPT,
    PASSWORD_PROTECTED,
    UNKNOWN
}