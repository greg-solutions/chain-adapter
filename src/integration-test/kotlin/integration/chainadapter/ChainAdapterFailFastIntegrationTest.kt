package integration.chainadapter

import integration.chainadapter.environment.ChainAdapterIntegrationTestEnvironment
import integration.chainadapter.environment.DEFAULT_RMQ_PORT
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.BindMode

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChainAdapterFailFastIntegrationTest {

    private val LAST_READ_BLOCK_FILE = "deploy/chain-adapter/last_read_block.txt"

    private val environment = ChainAdapterIntegrationTestEnvironment()
    private val chainAdapterContainer = environment.createChainAdapterContainer()

    @BeforeAll
    fun setUp() {
        // Mount Iroha keys
        chainAdapterContainer.addFileSystemBind(
            "${environment.userDir}/deploy/iroha/keys/",
            "/opt/chain-adapter/deploy/iroha/keys",
            BindMode.READ_ONLY
        )

        // Mount last read block file
        chainAdapterContainer.addFileSystemBind(
            LAST_READ_BLOCK_FILE,
            "/opt/chain-adapter/deploy/chain-adapter/last_read_block.txt",
            BindMode.READ_WRITE
        )

        // Set RMQ host
        chainAdapterContainer.addEnv("CHAIN-ADAPTER_RMQHOST", "localhost")
        chainAdapterContainer.addEnv(
            "CHAIN-ADAPTER_RMQPORT",
            environment.containerHelper.rmqContainer.getMappedPort(DEFAULT_RMQ_PORT).toString()
        )
        // Set Iroha host and port
        chainAdapterContainer.addEnv("CHAIN-ADAPTER_IROHA_HOSTNAME", "localhost")
        chainAdapterContainer.addEnv(
            "CHAIN-ADAPTER_IROHA_PORT",
            environment.irohaContainer.toriiAddress.port.toString()
        )
        chainAdapterContainer.start()
    }

    @AfterAll
    fun tearDown() {
        chainAdapterContainer.stop()
        environment.close()
    }

    /**
     * @given chain adapter and Iroha services being started
     * @when Iroha dies
     * @then chain adapter dies as well
     */
    @Test
    fun testFailFast() {
        // Let the service work a little
        Thread.sleep(15_000)
        assertTrue(environment.containerHelper.isServiceHealthy(chainAdapterContainer))
        // Kill Iroha
        environment.irohaContainer.stop()
        // Wait a little
        Thread.sleep(5_000)
        // Check that the service is dead
        assertTrue(environment.containerHelper.isServiceDead(chainAdapterContainer))
    }
}
