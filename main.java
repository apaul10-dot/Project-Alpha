import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class ChatApp extends Application {

    private BorderPane root;

    @Override
    public void start(Stage primaryStage) {
        root = new BorderPane();

        // Navigation Bar on the bottom of the page
        HBox navBar = new HBox(10);
        navBar.setPadding(new Insets(10));
        Button chatBtn = new Button("Text");
        Button profileBtn = new Button("Profile");
        Button settingsBtn = new Button("Settings");

        navBar.getChildren().addAll(chatBtn, profileBtn, settingsBtn);
        root.setBottom(navBar);

        showChatPage();
        // Switch to page when the button is clicked
        chatBtn.setOnAction(e -> showChatPage());
        profileBtn.setOnAction(e -> showProfilePage());
        settingsBtn.setOnAction(e -> showSettingsPage());

        Scene scene = new Scene(root, 400, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Alpha Texting");
        primaryStage.show();
    }

    // Chat Page 
    private void showChatPage() {
        VBox chatLayout = new VBox(10);
        chatLayout.setPadding(new Insets(10));

        Label received = new Label("Hello! 👋");  // Could parse with Jemoji
        received.setStyle("-fx-background-color: lightblue; -fx-padding: 10; -fx-background-radius: 15;");

        Label sent = new Label("Hey there 😎");
        sent.setStyle("-fx-background-color: lightgreen; -fx-padding: 10; -fx-background-radius: 15;");

        TextField input = new TextField();
        input.setPromptText("Type a message");

        Button sendBtn = new Button("Send");

        HBox inputBar = new HBox(5, input, sendBtn);

        chatLayout.getChildren().addAll(received, sent, inputBar);
        root.setCenter(chatLayout);

        Button blockUser = new Button("Block User");
        Button deleteChat = new Button("Delete Chat");
        Button clearChat = new Button("Clear Chat");
        Button exportChat = new Button("Export Chat");
        Button reportUser = new Button("Report User");
    }

    // Profile Page 
    private void showProfilePage() {
        VBox profileLayout = new VBox(10);
        profileLayout.setPadding(new Insets(10));

        Label avatarLabel = new Label("Your Avatar:");
        ImageView avatar = new ImageView(new Image("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQfMOvkk0KwwYRrgi2zF2PT_vxwoh9GcB_1NQ&s"));
        avatar.setFitWidth(100);
        avatar.setFitHeight(100);

        Button changeAvatar= new Button("Change Avatar");

        profileLayout.getChildren().addAll(avatarLabel, avatar, changeAvatarBtn);
        root.setCenter(profileLayout);
    }

    // Settings Page
    private void SettingsPage() {
        VBox settingsLayout = new VBox(10);
        settingsLayout.setPadding(new Insets(10));

        // Basic Settings and Functionality of the app
        CheckBox pauseNotifications= new CheckBox("Pause Notifications");
        CheckBox showStatsus= new CheckBox("Active Status");
        CheckBox enableNotifications = new CheckBox("Enable Notifications");
        CheckBox darkMode = new CheckBox("Enable Dark Mode");
        CheckBox readReceipts = new CheckBox("Enable Read Receipts");
        
        //Advanced Buttons that are present in most apps
        Button deleteAccount = new Button("Delete Account");
        Button logout = new Button("Log Out");

        settingsLayout.getChildren().addAll(new Label("Settings:"), pauseNotifications, readReceipts, showStatus, enableNotifications, darkMode, deleteAccount, logout);
        root.setCenter(settingsLayout);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
 main {
    
}
