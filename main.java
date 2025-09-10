import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javax.sound.sampled.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Base64;

public class Main extends Application {

    private BorderPane root;
    private VBox chatMessages;
    private SerialPort comPort;

    // Socket chat
    private static final int CHAT_PORT = 5555;
    private ServerSocket serverSocket;
    private final List<ClientHandler> connectedClients = new CopyOnWriteArrayList<>();
    private Socket clientSocket;
    private BufferedReader clientReader;
    private BufferedWriter clientWriter;

    // Voice recording
    private TargetDataLine recordingLine;
    private boolean isRecording = false;

    @Override
    public void start(Stage primaryStage) {
        root = new BorderPane();

        // Start local chat server and connect client
        startLocalServer();
        connectClient("127.0.0.1", CHAT_PORT);

        // Bottom Navigation Bar
        HBox navBar = new HBox(10);
        navBar.setPadding(new Insets(10));
        Button chatBtn = new Button("Text");
        Button profileBtn = new Button("Profile");
        Button settingsBtn = new Button("Settings");

        navBar.getChildren().addAll(chatBtn, profileBtn, settingsBtn);
        root.setBottom(navBar);

        // Default Page: Chat
        showChatPage();

        // Navigation actions
        chatBtn.setOnAction(e -> showChatPage());
        profileBtn.setOnAction(e -> showProfilePage());
        settingsBtn.setOnAction(e -> showSettingsPage());

        Scene scene = new Scene(root, 400, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Texting App (Sockets + Voice Notes)");
        primaryStage.show();
    }

    // Initialize and read from Arduino on a background thread
    private void setupArduino() {
        // Kept for compatibility; not used for messaging anymore.
        try {
            SerialPort[] ports = SerialPort.getCommPorts();
            if (ports.length == 0) {
                return;
            }
            comPort = ports[0];
            comPort.setBaudRate(9600);
            if (!comPort.openPort()) {
                comPort = null;
                return;
            }
            Thread reader = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (comPort != null && comPort.isOpen()) {
                    try {
                        int available = comPort.bytesAvailable();
                        if (available > 0) {
                            if (available > buffer.length) available = buffer.length;
                            int numRead = comPort.readBytes(buffer, available);
                            if (numRead > 0) {
                                String received = new String(buffer, 0, numRead).trim();
                                if (!received.isEmpty()) {
                                    Platform.runLater(() -> addMessage(received, false));
                                }
                            }
                        }
                        Thread.sleep(20);
                    } catch (Exception ex) {
                        break;
                    }
                }
            }, "arduino-serial-reader");
            reader.setDaemon(true);
            reader.start();
        } catch (Exception ignored) {
        }
    }

    // Socket server to allow multi-instance chat over localhost
    private void startLocalServer() {
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(CHAT_PORT);
                while (!Thread.currentThread().isInterrupted()) {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket);
                    connectedClients.add(handler);
                    new Thread(handler, "chat-client-" + socket.getPort()).start();
                }
            } catch (IOException e) {
                // Server closed
            }
        }, "chat-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        }

        @Override
        public void run() {
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    broadcast(line, this);
                }
            } catch (IOException ignored) {
            } finally {
                connectedClients.remove(this);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        void send(String msg) {
            try {
                writer.write(msg);
                writer.write('\n');
                writer.flush();
            } catch (IOException ignored) {
            }
        }
    }

    private void broadcast(String msg, ClientHandler from) {
        for (ClientHandler client : connectedClients) {
            if (client != from) {
                client.send(msg);
            }
        }
    }

    // Socket client to send/receive
    private void connectClient(String host, int port) {
        try {
            clientSocket = new Socket(host, port);
            clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            clientWriter = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            Thread readerThread = new Thread(() -> {
                String line;
                try {
                    while ((line = safeReadLine(clientReader)) != null) {
                        final String incoming = line;
                        if (incoming.startsWith("VOICE|")) {
                            handleIncomingVoice(incoming);
                        } else {
                            Platform.runLater(() -> addMessage(incoming, false));
                        }
                    }
                } catch (Exception ignored) {
                }
            }, "chat-client-reader");
            readerThread.setDaemon(true);
            readerThread.start();
        } catch (IOException e) {
            addMessage("Failed to connect to chat server.", false);
        }
    }

    private String safeReadLine(BufferedReader r) {
        try {
            return r.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    private void sendTextOverSocket(String text) {
        if (clientWriter == null) return;
        try {
            clientWriter.write(text);
            clientWriter.write('\n');
            clientWriter.flush();
        } catch (IOException ignored) {
        }
    }

    private void sendVoiceFileOverSocket(File wavFile) {
        if (clientWriter == null) return;
        try {
            byte[] data = Files.readAllBytes(wavFile.toPath());
            String b64 = Base64.getEncoder().encodeToString(data);
            String headerName = wavFile.getName();
            String payload = "VOICE|" + headerName + "|" + b64;
            clientWriter.write(payload);
            clientWriter.write('\n');
            clientWriter.flush();
        } catch (IOException ignored) {
        }
    }

    private void handleIncomingVoice(String line) {
        // Format: VOICE|<name>|<base64>
        try {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) return;
            String name = parts[1];
            byte[] data = Base64.getDecoder().decode(parts[2]);
            File out = File.createTempFile("voice_", "_" + name);
            Files.write(out.toPath(), data);
            Platform.runLater(() -> addVoiceMessage(out, false));
        } catch (Exception ignored) {
        }
    }

    // Chat Page with a Blue/Green Theme 
    private void showChatPage() {
        VBox chatLayout = new VBox(10);
        chatLayout.setPadding(new Insets(10));

        chatMessages = new VBox(5);
        ScrollPane scrollPane = new ScrollPane(chatMessages);
        scrollPane.setFitToWidth(true);

        TextField input = new TextField();
        input.setPromptText("Type a message");
        Button sendBtn = new Button("Send");
        Button voiceNoteBtn = new Button("Voice Note");

        sendBtn.setOnAction(e -> {
            String msg = input.getText().trim();
            if (!msg.isEmpty()) {
                addMessage(msg, true);
                sendTextOverSocket(msg);
                input.clear();
            }
        });

        voiceNoteBtn.setOnAction(e -> {
            if (!isRecording) {
                startRecording(voiceNoteBtn);
            } else {
                stopRecordingAndSend(voiceNoteBtn);
            }
        });

        HBox inputBar = new HBox(5, input, sendBtn, voiceNoteBtn);
        chatLayout.getChildren().addAll(scrollPane, inputBar);
        root.setCenter(chatLayout);
    }

    // Add message bubble to chat
    private void addMessage(String text, boolean sentByUser) {
        Label msg = new Label(text);
        msg.setWrapText(true);
        msg.setMaxWidth(250);

        if (sentByUser) {
            msg.setStyle("-fx-background-color: #b2f2bb; -fx-padding: 8; -fx-background-radius: 12;");
            msg.setTranslateX(100);
        } else {
            msg.setStyle("-fx-background-color: #c5d6ff; -fx-padding: 8; -fx-background-radius: 12;");
        }

        chatMessages.getChildren().add(msg);
    }

    private void addVoiceMessage(File wavFile, boolean sentByUser) {
        HBox container = new HBox(5);
        Label label = new Label(sentByUser ? "You sent a voice note" : "Voice note received");
        Button play = new Button("Play");
        play.setOnAction(e -> playWav(wavFile));
        container.getChildren().addAll(label, play);
        if (sentByUser) {
            container.setStyle("-fx-background-color: #b2f2bb; -fx-padding: 8; -fx-background-radius: 12;");
            container.setTranslateX(60);
        } else {
            container.setStyle("-fx-background-color: #c5d6ff; -fx-padding: 8; -fx-background-radius: 12;");
        }
        chatMessages.getChildren().add(container);
    }

    private void playWav(File wavFile) {
        new Thread(() -> {
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile)) {
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.start();
            } catch (Exception ignored) {
            }
        }, "voice-playback").start();
    }

    private void startRecording(Button voiceBtn) {
        try {
            AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000, 16, 1, 2, 16000, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            recordingLine = (TargetDataLine) AudioSystem.getLine(info);
            recordingLine.open(format);
            recordingLine.start();
            isRecording = true;
            voiceBtn.setText("Stop");

            Thread capture = new Thread(() -> {
                try {
                    AudioInputStream ais = new AudioInputStream(recordingLine);
                    File tmp = new File(System.getProperty("java.io.tmpdir"), "voice_note_" + System.currentTimeMillis() + ".wav");
                    AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tmp);
                } catch (Exception ignored) {
                }
            }, "voice-capture");
            capture.setDaemon(true);
            capture.start();
        } catch (LineUnavailableException e) {
            addMessage("Microphone unavailable", false);
        }
    }

    private void stopRecordingAndSend(Button voiceBtn) {
        try {
            if (recordingLine != null) {
                recordingLine.stop();
                recordingLine.close();
            }
        } catch (Exception ignored) {
        }
        isRecording = false;
        voiceBtn.setText("Voice Note");

        // Find the latest recorded wav in tmp dir
        try {
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            File[] files = tmpDir.listFiles((dir, name) -> name.startsWith("voice_note_") && name.endsWith(".wav"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                File latest = files[0];
                addVoiceMessage(latest, true);
                sendVoiceFileOverSocket(latest);
            }
        } catch (Exception ignored) {
        }
    }

    // Profile Page (Avatar)
    private void showProfilePage() {
        VBox profileLayout = new VBox(10);
        profileLayout.setPadding(new Insets(10));

        Label avatarLabel = new Label("Your Avatar:");
        ImageView avatar = new ImageView(new Image("https://via.placeholder.com/100"));
        avatar.setFitWidth(100);
        avatar.setFitHeight(100);

        Button changeAvatarBtn = new Button("Change Avatar");

        profileLayout.getChildren().addAll(avatarLabel, avatar, changeAvatarBtn);
        root.setCenter(profileLayout);
    }

    // Settings Page
    private void showSettingsPage() {
        VBox settingsLayout = new VBox(10);
        settingsLayout.setPadding(new Insets(10));

        // Key Functions of the Settings Page
        CheckBox parentalControls = new CheckBox("Enable Parental Controls");
        CheckBox readReceipts = new CheckBox("Enable Read Receipts");
        CheckBox ActivityStatus= new CheckBox("Show If You Are Active");
        CheckBox ShowNotifications= new CheckBox("Allow Notifications");
        CheckBox DarkMode= new CheckBox("Enable Darkmode");

        Button DeleteAccountBtn = new Button("Delete Account");
        Button LogOutBtn = new Button("Log Out");

        settingsLayout.getChildren().addAll(new Label("Settings:"), parentalControls, readReceipts, ActivityStatus, ShowNotifications, DarkMode, DeleteAccountBtn, LogOutBtn);
        root.setCenter(settingsLayout);
    }

    @Override
    public void stop() {
        try {
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        if (comPort != null && comPort.isOpen()) {
            comPort.closePort();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
