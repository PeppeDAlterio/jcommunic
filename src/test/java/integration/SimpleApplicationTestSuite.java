package integration;

import it.unina.sistemiembedded.client.Client;
import it.unina.sistemiembedded.client.impl.ClientImpl;
import it.unina.sistemiembedded.exception.BoardAlreadyExistsException;
import it.unina.sistemiembedded.exception.BoardNotAvailableException;
import it.unina.sistemiembedded.model.Board;
import it.unina.sistemiembedded.server.impl.ServerImpl;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SimpleApplicationTestSuite {

    private ServerImpl server;

    private Board testBoard1, testBoard2;

    @BeforeEach
    void init() throws IOException, BoardAlreadyExistsException {

        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG");
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");

        server = new ServerImpl("Mio server");

        testBoard1 = new Board("board 1", "serialNumberBoard1");
        testBoard2 = new Board("board 2", "serialNumberBoard2");
        server.addBoards(testBoard1, testBoard2);
        server.start();
        assertTrue(server.isRunning());

        assertEquals(2, server.listBoards().size());

        server.getBoards().forEach((k, v) -> {
            if(k.equals("serialNumberBoard1")) {
                assertSame(v, testBoard1);
            } else if(k.equals("serialNumberBoard2")) {
                assertSame(v, testBoard2);
            }
        });

    }

    @AfterEach
    void cleanup() throws IOException {
        this.server.stop();
    }

    @Test
    @DisplayName("Client connection")
    void clientConnectTest1() {

        ClientImpl client;

        assertEquals(0, server.getClientHandlers().size());

        client = new ClientImpl("My name");
        assertDoesNotThrow( () -> client.connect("127.0.0.1") );

        assertEquals(1, server.getClientHandlers().size());

    }

    @Test
    @DisplayName("Two clients requesting 2 boards")
    void requestBoardTest1() throws IOException {

        ClientImpl client1, client2;

        client1 = new ClientImpl("Test client 1");
        client2 = new ClientImpl("Test client 2");

        client1.getBoard();
        client2.getBoard();

        client1.connect("127.0.0.1");
        client2.connect("127.0.0.1");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client1.requestBoard(testBoard1.getSerialNumber());
        client2.requestBoard(testBoard2.getSerialNumber());
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        executorService.execute(() -> client2.requestBoard(testBoard1.getSerialNumber()));
        executorService.execute(() -> client1.requestBoard(testBoard2.getSerialNumber()));
        executorService.execute(() -> client1.requestBoard(testBoard2.getSerialNumber()));
        executorService.execute(() -> client2.requestBoard(testBoard1.getSerialNumber()));

        executorService.shutdown();

        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(testBoard1, client1.getBoard());
        assertEquals(testBoard2, client2.getBoard());

    }

    @Test
    @DisplayName("Client requesting non-existing board")
    void requestBoardTest2() throws IOException {

        ClientImpl client1;

        client1 = new ClientImpl("Test client 1");

        client1.connect("127.0.0.1");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client1.requestBoard(testBoard1.getSerialNumber()+"bla bla");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertNull(client1.getBoard());

    }

    @Test
    @DisplayName("Client request a board held by a disconnected client.")
    void requestBoardTest3() throws IOException {

        ClientImpl client1, client2;

        client1 = new ClientImpl("Test client 1");
        client2 = new ClientImpl("Test client 2");

        client1.connect("127.0.0.1");
        client2.connect("127.0.0.1");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client1.requestBoard(testBoard1.getSerialNumber());

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client1.disconnect();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client2.requestBoard(testBoard1.getSerialNumber());

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(testBoard1, client2.getBoard());
        assertNull(client1.getBoard());

    }

    @Test
    @DisplayName("Client requests a board then release it.")
    void releaseBoardTest1() throws IOException {

        ClientImpl client1;

        client1 = new ClientImpl("Test client 1");

        client1.connect("127.0.0.1");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client1.requestBoard(testBoard1.getSerialNumber());

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client1.requestReleaseBoard();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client1.requestBoard(testBoard2.getSerialNumber());

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client1.requestReleaseBoard();

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertNull(client1.getBoard());

        assertFalse(testBoard1.isInUse());
        assertFalse(testBoard2.isInUse());

    }

    @Test
    @DisplayName("Flash file transfer test")
    public void flashRequestTest1() throws IOException {

        Client client = new ClientImpl("Client");

        client.connect("127.0.0.1");

        client.requestBoard(testBoard1.getSerialNumber());

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertDoesNotThrow( () -> client.requestFlash("src/main/resources/testfile.elf"));

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertTrue(new File("received/" + testBoard1.getSerialNumber() + "/testfile.elf").exists());
        assertTrue(new File("received/" + testBoard1.getSerialNumber() + "/testfile.elf").delete());

        //noinspection ResultOfMethodCallIgnored
        new File("received/" + testBoard1.getSerialNumber()).delete();


    }

    @Test
    @DisplayName("Flash without a board")
    public void flashRequestTest2() throws IOException {

        Client client = new ClientImpl("Client");

        client.connect("127.0.0.1");

        assertThrows( BoardNotAvailableException.class, () -> client.requestFlash("src/main/resources/testfile.elf"));

    }

    @Test
    @DisplayName("Client request server's board list in blocking mode")
    public void blockingBoardListRequestTest1() throws IOException {

        Client client = new ClientImpl("Client");
        client.connect("127.0.0.1");

        assertEquals(server.listBoards(), client.requestBlockingServerBoardList());

    }

    @Test
    @DisplayName("Client request server's board list in non-blocking mode")
    public void boardListRequestTest2() throws IOException {

        Client client = new ClientImpl("Client");
        client.connect("127.0.0.1");

        client.requestServerBoardList();

        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Test
    @DisplayName("Server name post connect")
    public void handshakeTest1() throws IOException {

        Client client = new ClientImpl("Client");
        client.connect("127.0.0.1");

        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals(server.getName(), client.getServerName());

    }

    @Test @Disabled
    @DisplayName("Test manuale per debug su scheda di Giuseppe")
    void debugTest1() throws IOException, BoardAlreadyExistsException {

        Client client = new ClientImpl("Client");
        client.connect("127.0.0.1");

        Board myBoard = new Board("Nucleo 64", "066FFF494849887767185233");
        server.addBoards(myBoard);

        client.requestBoard(myBoard.getSerialNumber());

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        client.requestDebug(6789);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

}

