package it.unina.sistemiembedded.net;

import com.fazecast.jSerialComm.SerialPort;
import it.unina.sistemiembedded.boarddriver.COMDriver;
import it.unina.sistemiembedded.net.file.SocketFileHelper;
import it.unina.sistemiembedded.utility.Constants;
import it.unina.sistemiembedded.utility.SystemHelper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;

public class Client
{
    private final Scanner scanner = new Scanner(System.in);

    private String serverIpAddress = "";
    private int serverPort = 1234;

    private Socket socket;

    private DataInputStream dis;
    private DataOutputStream dos;

    private COMDriver comDriver;

    private boolean running = false;

    //

    public static List<SerialPort> listAvailableCOMPorts() {
        return COMDriver.listPorts();
    }

    public Socket getSocket() {
        return socket;
    }

    public DataInputStream getDis() {
        return dis;
    }

    public DataOutputStream getDos() {
        return dos;
    }

    public Client(String serverIpAddress, int serverPort) {

        this.serverPort = serverPort;
        this.serverIpAddress = serverIpAddress;

    }

    public void startClient(int comPortNumber) throws IOException {

        try {

            this.socket = new Socket(InetAddress.getByName(this.serverIpAddress), this.serverPort);

            this.dis = new DataInputStream(socket.getInputStream());
            this.dos = new DataOutputStream(socket.getOutputStream());

            comDriver = new COMDriver(COMDriver.listPorts().get(comPortNumber));

        } catch (IOException e) {

            System.err.println("Errore di connessione verso il server.");

            throw e;

        } catch (IllegalArgumentException e) {

            System.err.println("COM Port non connessa !");

            if(this.socket!=null && !this.socket.isClosed()) {
                try {
                    this.socket.close();
                } catch (IOException ignored) {}
            }

            throw e;

        }

        if(this.socket == null) {
            throw new IllegalArgumentException("Impossibile stabilire una connessione al server " + serverIpAddress + ":" + serverPort);
        }

        System.out.println("Connessione avviata con socket: " + this.socket);

        this.running = true;

        // Thread for handshake
        new Thread(() -> {

            String msg;

            System.out.println("Inserisci il tuo nome: ");
            msg = scanner.nextLine();
            if (socket.isConnected()) {
                try {
                    dos.writeUTF(msg);
                } catch (IOException e) {
                    //e.printStackTrace();
                }
            } else {
                System.err.println("Socket non connessa");
                stopClient();
            }

        }).start();

        // readMessage thread
        // read the message sent to this client
        waitForMessagesAsync();

    }

    public void stopClient() {
        this.running = false;
        this.comDriver.closeCommunication();
    }

    public boolean isRunning() {return this.running;}

    private void consumeAndSendCOMBufferAsync() {
        new Thread(this::consumeAndSendCOMBuffer).start();
    }

    private void consumeAndSendCOMBuffer() {

        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        comDriver.consumeAllAvailableMessages().forEach(m -> {
            try {
                this.dos.writeUTF(m);
            } catch (IOException ignored) {
            }
        });

    }

    private void waitForMessagesAsync() {
        new Thread(() -> {

            while (socket != null && socket.isConnected()) {
                try {
                    // read the message sent to this client
                    String msg = dis.readUTF();

                    if (msg.equals(Constants.BEGIN_OF_REMOTE_FLASH)) {

                        System.out.println("Il server ha richiesto un flash remoto...");
                        String receivedFile = SocketFileHelper.receiveFile(dis, ".elf");
                        System.out.println("...trasferimento file ELF completato.");

                        SystemHelper.runCommandAndPrintOutput(
                                ".\\tools\\STM32CubeProgrammer\\bin\\STM32_Programmer_CLI.exe -c port=SWD -d "
                                        + receivedFile + " --start"
                        );

                        dos.writeUTF(Constants.END_OF_REMOTE_FLASH);

                        consumeAndSendCOMBufferAsync();

                    } else {

                        System.out.println("Ho ricevuto: " + msg);

                        comDriver.writeLn(msg);

                        consumeAndSendCOMBufferAsync();

                    }


                } catch (IOException e) {
                    System.err.println("Connessione al server interrotta");
                    try {
                        dos.writeUTF("");
                    } catch (IOException ignored) {
                    }
                    break;
                } catch (NullPointerException e) {
                    break;
                }
            }

            stopClient();

        }).start();
    }

}