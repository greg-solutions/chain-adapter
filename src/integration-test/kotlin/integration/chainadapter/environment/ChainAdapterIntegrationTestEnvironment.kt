package integration.chainadapter.environment

import com.d3.chainadapter.CHAIN_ADAPTER_SERVICE_NAME
import com.d3.chainadapter.adapter.ChainAdapter
import com.d3.chainadapter.provider.FileBasedLastReadBlockProvider
import com.d3.chainadapter.provider.LastReadBlockProvider
import com.d3.commons.config.RMQConfig
import com.d3.commons.model.IrohaCredential
import com.d3.commons.sidechain.iroha.IrohaChainListener
import com.d3.commons.util.createPrettySingleThreadPool
import integration.chainadapter.helper.ChainAdapterConfigHelper
import io.grpc.ManagedChannelBuilder
import iroha.protocol.BlockOuterClass
import iroha.protocol.Commands
import iroha.protocol.Primitive
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import jp.co.soramitsu.iroha.java.Transaction
import jp.co.soramitsu.iroha.java.TransactionStatusObserver
import jp.co.soramitsu.iroha.testcontainers.IrohaContainer
import jp.co.soramitsu.iroha.testcontainers.PeerConfig
import jp.co.soramitsu.iroha.testcontainers.detail.GenesisBlockBuilder
import org.testcontainers.containers.FixedHostPortGenericContainer
import java.io.Closeable
import java.util.*
import kotlin.math.absoluteValue


private val random = Random()
private const val DEFAULT_RMQ_PORT = 5672

/**
 * Chain adapter test environment
 */
class ChainAdapterIntegrationTestEnvironment : Closeable {

    private val peerKeyPair = Ed25519Sha3().generateKeypair()

    private val rmqKeyPair = Ed25519Sha3().generateKeypair()

    private val dummyClientKeyPair = Ed25519Sha3().generateKeypair()

    // Random dummy value
    private val dummyValue = random.nextInt().absoluteValue.toString()

    private val chainAdapterConfigHelper = ChainAdapterConfigHelper()

    private val irohaContainer = IrohaContainer().withPeerConfig(getPeerConfig())

    private val rmq = KGenericContainer("rabbitmq:3-management").withExposedPorts(DEFAULT_RMQ_PORT)
        .withFixedExposedPort(DEFAULT_RMQ_PORT, DEFAULT_RMQ_PORT)

    private lateinit var irohaAPI: IrohaAPI

    init {
        rmq.start()
        // I don't want to see nasty Iroha logs
        irohaContainer.withLogger(null)
        irohaContainer.start()
        irohaAPI=irohaContainer.api
    }

    /**
     * Returns Iroha peer config
     */
    private fun getPeerConfig(): PeerConfig {
        val config = PeerConfig.builder()
            .genesisBlock(getGenesisBlock())
            .build()
        config.withPeerKeyPair(peerKeyPair)
        return config
    }

    /**
     * Creates test genesis block
     */
    private fun getGenesisBlock(): BlockOuterClass.Block {
        return GenesisBlockBuilder().addTransaction(
            Transaction.builder("")
                .addPeer("0.0.0.0:10001", peerKeyPair.public)
                .createRole("none", emptyList())
                .createRole(
                    "client",
                    listOf(
                        Primitive.RolePermission.can_set_detail
                    )
                ).createRole(
                    "rmq", listOf(
                        Primitive.RolePermission.can_get_blocks
                    )
                )
                .createDomain("notary", "none")
                .createDomain("d3", "client")
                .createAccount("rmq@notary", rmqKeyPair.public)
                .createAccount("client@d3", dummyClientKeyPair.public)
                .appendRole("rmq@notary", "rmq")
                .build()
                .build()
        ).build()
    }

    /**
     * Creates ChainAdapter
     */
    fun createAdapter(): OpenChainAdapter {
        val rmqConfig = chainAdapterConfigHelper.createRmqConfig(rmq.containerIpAddress)
        val irohaAPI = irohaAPI()
        val lastReadBlockProvider = FileBasedLastReadBlockProvider(rmqConfig)
        val queryAPI =
            QueryAPI(
                irohaAPI,
                rmqConfig.irohaCredential.accountId,
                rmqKeyPair
            )
        val irohaChainListener = IrohaChainListener(
            irohaAPI,
            IrohaCredential(rmqConfig.irohaCredential.accountId, rmqKeyPair)
        )
        return OpenChainAdapter(
            rmqConfig,
            queryAPI,
            irohaChainListener,
            lastReadBlockProvider
        )
    }

    /**
     * It's essential to handle blocks in this service one-by-one.
     * This is why we explicitly set single threaded executor.
     */
    private fun irohaAPI(): IrohaAPI {
        val api = irohaContainer.api
        val irohaAddress = irohaContainer.toriiAddress
        api.setChannelForStreamingQueryStub(
            ManagedChannelBuilder.forAddress(
                irohaAddress.host, irohaAddress.port
            ).executor(
                createPrettySingleThreadPool(
                    CHAIN_ADAPTER_SERVICE_NAME,
                    "iroha-chain-listener"
                )
            ).usePlaintext().build()
        )
        return api
    }

    /**
     * Creates dummy transaction
     */
    fun createDummyTransaction(testKey: String = dummyValue) {
        val transactionBuilder = Transaction
            .builder("client@d3")
            .setAccountDetail("client@d3", testKey, dummyValue)
            .sign(dummyClientKeyPair)
        irohaAPI.transaction(transactionBuilder.build())
            .blockingSubscribe(
                TransactionStatusObserver.builder()
                    .onError { ex -> throw ex }
                    .onTransactionFailed { tx -> throw Exception("${tx.txHash} has failed") }
                    .onRejected { tx -> throw Exception("${tx.txHash} has been rejected") }
                    .build()
            )

    }

    /**
     * Checks if command is dummy
     */
    fun isDummyCommand(command: Commands.Command): Boolean {
        return command.hasSetAccountDetail() && command.setAccountDetail.value == dummyValue
    }

    override fun close() {
        irohaAPI.close()
        irohaContainer.close()
        rmq.close()
    }
}

/**
 * The GenericContainer class is not very friendly to Kotlin.
 * So the following class was created as a workaround.
 */
private class KGenericContainer(imageName: String) : FixedHostPortGenericContainer<KGenericContainer>(imageName)

/**
 * This ChainAdapter implementation is used to expose values
 * that are private in the original class
 */
class OpenChainAdapter(
    val rmqConfig: RMQConfig,
    queryAPI: QueryAPI,
    irohaChainListener: IrohaChainListener,
    val lastReadBlockProvider: LastReadBlockProvider
) : ChainAdapter(rmqConfig, queryAPI, irohaChainListener, lastReadBlockProvider)