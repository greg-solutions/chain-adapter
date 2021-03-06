package com.d3.chainadapter.config

import com.d3.commons.config.IrohaConfig
import com.d3.commons.config.IrohaCredentialRawConfig

/**
 * Chain adapter configurations
 */
interface ChainAdapterConfig {
    // RMQ hostname
    val rmqHost: String
    // RMQ port
    val rmqPort: Int
    // Exchange that is used to broadcast Iroha blocks
    val irohaExchange: String
    // Account that is used to read blocks from Iroha
    val irohaCredential: IrohaCredentialRawConfig
    // Iroha configs
    val iroha: IrohaConfig
    // Path to the file, where the last read block number is saved
    val lastReadBlockFilePath: String
    // Drop last read block on startup
    val dropLastReadBlock: Boolean
}
