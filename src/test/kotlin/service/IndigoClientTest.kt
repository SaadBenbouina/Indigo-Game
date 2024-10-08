package service

import edu.udo.cs.sopra.ntf.TilePlacedMessage
import service.networkservice.NetworkConfig

import tools.aqua.bgw.net.common.notification.PlayerJoinedNotification
import tools.aqua.bgw.net.common.response.CreateGameResponse
import tools.aqua.bgw.net.common.response.CreateGameResponseStatus
import tools.aqua.bgw.net.common.response.JoinGameResponse
import java.util.Random

import kotlin.test.*

import java.util.concurrent.TimeUnit
import java.util.concurrent.Semaphore

/** test cases for callbacks generated by [IndigoClient] */
class IndigoClientTest {
    /** make sure that creating a game will cause [MessageHandler.onCreateGame] to be called */
    @Test
    fun createGameTest() {
        // bgw-net is asynchronous. Without a semaphore the test will exit
        // before the server response arrives at the client.
        val semaphore = Semaphore(0)

        val client = IndigoClient(object: MessageHandler {
            override fun onCreateGame(client: IndigoClient, resp: CreateGameResponse) {
                assertEquals(resp.status, CreateGameResponseStatus.SUCCESS)
                semaphore.release()
            }
        }, "Alice")

        assert(client.connect()) { "cannot connect to bgw server" }
        client.createGame("Indigo", "Hello World")

        assert(semaphore.tryAcquire(NetworkConfig.TEST_TIMEOUT, TimeUnit.MILLISECONDS)) {
            "waiting for call to onCreateGame timed out"
        }
    }

    /**
     * make sure that joining a game will cause
     * [MessageHandler.onPlayerJoined] and [MessageHandler.onJoinGame]
     * to be called
     */
    @Test
    fun joinGameTest() {
        val hostSemaphore = Semaphore(0)
        val guestSemaphore = Semaphore(0)

        val sessionID = Random().nextInt().toString()

        val host = IndigoClient(object: MessageHandler {
            override fun onCreateGame(client: IndigoClient, resp: CreateGameResponse) {
                hostSemaphore.release()
            }

            override fun onPlayerJoined(client: IndigoClient, player: PlayerJoinedNotification) {
                assertEquals(player.sender, "Bob")
                hostSemaphore.release()
            }
        }, "Alice")

        val guest = IndigoClient(object: MessageHandler {
            override fun onJoinGame(client: IndigoClient, resp: JoinGameResponse) {
                guestSemaphore.release()
            }
        }, "Bob")

        assert(host.connect()) { "cannot connect to bgw server" }
        assert(guest.connect()) { "cannot connect to bgw server"}

        host.createGame("Indigo", sessionID, "Hello World")

        // wait until the game is created
        assert(hostSemaphore.tryAcquire(NetworkConfig.TEST_TIMEOUT, TimeUnit.MILLISECONDS)) {
            "waiting for call to onCreateGame timed out"
        }

        guest.joinGame(sessionID, "Hello World")

        // wait until playerJoined and joinGame are called
        assert(hostSemaphore.tryAcquire(NetworkConfig.TEST_TIMEOUT, TimeUnit.MILLISECONDS)) {
            "waiting for call to onPlayerJoined timed out"
        }

        assert(guestSemaphore.tryAcquire(NetworkConfig.TEST_TIMEOUT, TimeUnit.MILLISECONDS)) {
            "waiting for call to onJoinGame timed out"
        }
    }

    /**
     * Make sure that [TilePlacedMessage] objects can be sent and received
     * by the [IndigoClient]. This tests depends on the [TilePlacedMessage]
     * workaround, because [edu.udo.cs.sopra.ntf.TilePlacedMessage] is
     * currently broken.
     */
    @Test
    fun sendTilePlacedTest() {
        val semaphore = Semaphore(0)

        val host = IndigoClient(object: MessageHandler {
            override fun onCreateGame(client: IndigoClient, resp: CreateGameResponse) {
                semaphore.release()
            }
        }, "Alice")

        val guest = IndigoClient(object: MessageHandler {
            override fun onJoinGame(client: IndigoClient, resp: JoinGameResponse) {
                semaphore.release()
            }

            override fun onTilePlaced(client: IndigoClient, tilePlacedMessage: TilePlacedMessage, sender: String) {
                assertEquals(tilePlacedMessage.rotation, 0)
                assertEquals(tilePlacedMessage.qcoordinate, 1)
                assertEquals(tilePlacedMessage.rcoordinate, 0)
                semaphore.release()
            }
        }, "Bob")

        assert(host.connect()) { "cannot connect to bgw server" }
        assert(guest.connect()) { "cannot connect to bgw server"}

        val sessionID = Random().nextInt().toString()

        host.createGame("Indigo", sessionID, "Hello, World")

        assert(semaphore.tryAcquire(NetworkConfig.TEST_TIMEOUT, TimeUnit.MILLISECONDS)) {
            "waiting for call to onCreateGame timed out"
        }

        guest.joinGame(sessionID, "Hello, World!")

        assert(semaphore.tryAcquire(NetworkConfig.TEST_TIMEOUT, TimeUnit.MILLISECONDS)) {
            "waiting for call to joinGame timed out"
        }

        val message = TilePlacedMessage(0, 1, 0)
        host.sendGameActionMessage(message)

        assert(semaphore.tryAcquire(NetworkConfig.TEST_TIMEOUT, TimeUnit.MILLISECONDS)) {
            "waiting for call to sendGameActionMessage timed out"
        }
    }

    /** make sure that exception capturing works */
    @Test
    fun captureExceptionTest() {
        val callbackReceived = Semaphore(0)

        val client = IndigoClient(object : MessageHandler {
            override fun onCreateGame(client: IndigoClient, resp: CreateGameResponse) {
                callbackReceived.release()
                check(false) { "should not cause a failure when calling create game" }
            }
        }, "Alice", captureCallbackFailures = true)

        assertTrue(client.connect())
        client.createGame("Indigo", Random().nextInt().toString(), "Hello, World!")

        check(callbackReceived.tryAcquire(1, TimeUnit.SECONDS)) { "timed out" }
    }
}