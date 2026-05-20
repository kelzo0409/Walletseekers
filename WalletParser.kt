package com.hackerai.walletseeker.data.parser

import com.google.gson.JsonParser
import com.hackerai.walletseeker.data.model.*

class WalletParser {

    fun parseFromJson(jsonString: String, fileName: String = ""): WalletModel? {
        return try {
            val json = JsonParser.parseString(jsonString).asJsonObject

            val address = json.get("address")?.asString
            val pk = json.get("privateKey")?.asString
                ?: json.get("private_key")?.asString
                ?: json.get("secret")?.asString
            val mnemonic = json.get("mnemonic")?.asString
                ?: json.get("seed")?.asString
                ?: json.get("phrase")?.asString

            val isEncrypted = json.has("crypto") || json.has("Crypto") || json.has("encrypted")

            when {
                isEncrypted -> WalletModel(
                    fileName = fileName,
                    fullPath = "",
                    walletType = WalletType.ETHEREUM_KEYSTORE,
                    cryptoType = CryptoType.ETH,
                    keystoreJson = jsonString,
                    address = if (address?.startsWith("0x") == true) address else "0x$address",
                    isEncrypted = true,
                    encryptionType = EncryptionType.ETHEREUM_KEYSTORE,
                    status = WalletStatus.PROTECTED
                )
                pk != null -> {
                    val cryptoType = when {
                        pk.startsWith("0x") -> CryptoType.ETH
                        pk.matches(Regex("""[5KL][1-9A-HJ-NP-Za-km-z]{51}""")) -> CryptoType.BTC
                        else -> CryptoType.ETH
                    }
                    WalletModel(
                        fileName = fileName,
                        fullPath = "",
                        walletType = WalletType.PRIVATE_KEY,
                        cryptoType = cryptoType,
                        privateKey = pk,
                        address = address,
                        status = WalletStatus.PENDING
                    )
                }
                mnemonic != null -> WalletModel(
                    fileName = fileName,
                    fullPath = "",
                    walletType = WalletType.MNEMONIC,
                    mnemonic = mnemonic,
                    status = WalletStatus.PENDING
                )
                address != null -> WalletModel(
                    fileName = fileName,
                    fullPath = "",
                    walletType = WalletType.CUSTOM,
                    cryptoType = CryptoType.ETH,
                    address = address,
                    status = WalletStatus.PENDING
                )
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseWalletTypeFromContent(content: String): WalletType {
        return when {
            content.contains("encrypted_master") || content.contains("wallet.dat") -> WalletType.BITCOIN_CORE
            content.contains("\"crypto\"") || content.contains("\"Crypto\"") -> WalletType.ETHEREUM_KEYSTORE
            content.contains("mnemonic") || content.contains("seed") -> WalletType.MNEMONIC
            content.startsWith("0x") && content.length == 66 -> WalletType.PRIVATE_KEY
            content.matches(Regex("""[5KL][1-9A-HJ-NP-Za-km-z]{51}""")) -> WalletType.PRIVATE_KEY
            else -> WalletType.UNKNOWN
        }
    }
}