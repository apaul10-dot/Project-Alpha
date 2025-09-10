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

public class ChatAppWithArduino extends Application {

    private BorderPane root;
    private VBox chatMessages;
    private SerialPort comPort;

    @Override
    public void start(Stage primaryStage) {
        root = new BorderPane();

        // Connect to Arduino
        setupArduino();

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
        primaryStage.setTitle("Texting App with Arduino");
        primaryStage.show();
    }

            
            new Thread(() -> {
                while (true) {
                    if (comPort.bytesAvailable() > 0) {
                        byte[] buffer = new byte[comPort.bytesAvailable()];
                        int numRead = comPort.readBytes(buffer, buffer.length);
                        String received = new String(buffer, 0, numRead).trim();

                        if (!received.isEmpty()) {
                            Platform.runLater(() -> addMessage(received, false));
                        }
                    }
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
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
        Button voiceNoteBtn= new Button("Voice Note");


        sendBtn.setOnAction(e -> {
            String msg = input.getText().trim();
            if (!msg.isEmpty()) {
                addMessage(msg, true);
                sendToArduino("NEW_MESSAGE\n"); 
                input.clear();
            }
        
        voiceNoteBtn.setOnAction(e -> {
                AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();
                AudioInputStream ais = new AudioInputStream(line);
                File outputFile = new File("voice_note.wav");
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, outputFile);
                voiceNoteBtn.setOnAction(e -> {
                        line.stop();
                        line.close();
                        ais.close();
                })
        })
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
            msg.setStyle("-fx-background-color: lightgreen; -fx-padding: 8; -fx-background-radius: 12;");
            msg.setTranslateX(100); // push to right
        } else {
            msg.setStyle("-fx-background-color: lightred; -fx-padding: 8; -fx-background-radius: 12;");
        }

        chatMessages.getChildren().add(msg);
    }

    // Send a string to Arduino
    private void sendToArduino(String data) {
        if (comPort != null && comPort.isOpen()) {
            comPort.writeBytes(data.getBytes(), data.length());
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
        Button LogOutBtn = New Button("Log Out");

        settingsLayout.getChildren().addAll(new Label("Settings:"), parentalControls, readReceipts, ActivityStatus, ShowNotifications, DarkMode, DeleteAccountBtn, LogOutBtn);
        root.setCenter(settingsLayout);
    }

    @Override
    public void stop() {
        if (comPort != null && comPort.isOpen()) {
            comPort.closePort();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
