import com.github.sahariardev.proxy.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class ServerTest {

    private final int port = 7080;
    private final String serverHost = "localhost";
    private final int serverPort = 7081;
    private final String key = "7080:localhost:7081";

    private ServerSocket serverSocket;
    private Server server;
    private Socket clientSocket;

    private final String message = "hello";

    @BeforeEach
    void setup() throws IOException, InterruptedException {
        serverSocket = new ServerSocket(serverPort);

        Thread serverSocketThread = new Thread(() -> {
            try (Socket clientSocket = serverSocket.accept();
                 OutputStream outputStream = clientSocket.getOutputStream();
                 PrintWriter writer = new PrintWriter(outputStream, true)) {

                writer.println(message);
                writer.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                reader.readLine();

            } catch (IOException ignore) {
            }
        });

        serverSocketThread.start();

        server = new Server(port, serverHost, serverPort, key);

        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException ignore) {
            }
        });

        serverThread.start();

        Thread.sleep(500);

        clientSocket = new Socket("localhost", port);
    }

    @AfterEach
    void tearDown() throws IOException {
        serverSocket.close();
        server.stop();
        clientSocket.close();
    }

    @Test
    void testConnectivity() throws IOException, InterruptedException {
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
        bufferedWriter.write("Hello\n");
        bufferedWriter.flush();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String received = bufferedReader.readLine();
        assertEquals(message, received);

        bufferedWriter.close();
        bufferedReader.close();
        clientSocket.close();
    }
}
