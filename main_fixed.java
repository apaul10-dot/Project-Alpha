import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URI;
import java.awt.Desktop;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

public class Main extends Application {

    private static final int WEB_PORT = 3000;

    private BorderPane root;
    private VBox chatMessages;
    private ScrollPane scrollPane;
    private boolean darkMode = true;

    // SSE client connections (writers kept open)
    private final List<PrintWriter> sseClients = new CopyOnWriteArrayList<>();

    private ServerSocket httpServerSocket;

    @Override
    public void start(Stage primaryStage) {
        root = new BorderPane();
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #0f172a, #111827);");

        // UI
        VBox chatLayout = new VBox(10);
        chatLayout.setPadding(new Insets(10));
        chatLayout.setStyle("-fx-background-color: transparent;");
        chatMessages = new VBox(8);
        chatMessages.setFillWidth(true);
        scrollPane = new ScrollPane(chatMessages);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        TextField input = new TextField();
        input.setPromptText("Type a message");
        input.setStyle("-fx-background-radius: 12; -fx-background-color: #0b1220; -fx-text-fill: white; -fx-border-color: #334155; -fx-border-radius: 12; -fx-prompt-text-fill: #94a3b8;");
        Button sendBtn = new Button("Send");
        sendBtn.setStyle(primaryButtonStyle());
        sendBtn.setOnAction(e -> {
            String text = input.getText().trim();
            if (!text.isEmpty()) {
                addMessage("You", text);
                broadcastEvent("desktop", text);
                input.clear();
            }
        });
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox inputBar = new HBox(8, input, sendBtn);
        inputBar.setPadding(new Insets(8, 0, 0, 0));
        chatLayout.getChildren().addAll(scrollPane, inputBar);
        root.setCenter(chatLayout);

        // Header with connection info + actions
        String url = "http://" + getLocalIpAddress() + ":" + WEB_PORT + "/";
        Label title = new Label("LAN Chat");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        Label info = new Label(url);
        info.setStyle("-fx-text-fill: #93c5fd; -fx-font-size: 12px;");

        Button copyBtn = new Button("Copy Link");
        copyBtn.setStyle(secondaryButtonStyle());
        copyBtn.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(url);
            Clipboard.getSystemClipboard().setContent(content);
            addSystem("Copied link to clipboard");
        });

        Button openBtn = new Button("Open");
        openBtn.setStyle(secondaryButtonStyle());
        openBtn.setOnAction(e -> {
            try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI(url)); } catch (Exception ignored) {}
        });

        Button themeBtn = new Button("Theme");
        themeBtn.setStyle(secondaryButtonStyle());
        themeBtn.setOnAction(e -> toggleTheme());

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox topBar = new HBox(12, title, info, spacer, copyBtn, openBtn, themeBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12));
        topBar.setStyle("-fx-background-color: rgba(15,23,42,0.8); -fx-border-color: #1f2937; -fx-border-width: 0 0 1 0;");
        root.setTop(topBar);

        Scene scene = new Scene(root, 460, 720);
        primaryStage.setScene(scene);
        primaryStage.setTitle("LAN Chat (Desktop ↔ Phone)");
        primaryStage.show();

        // Start HTTP server
        startHttpServer();

        Platform.runLater(() -> addSystem("Server running at " + url));
    }

    private String primaryButtonStyle() {
        return "-fx-background-color: linear-gradient(to right, #4f46e5, #06b6d4); -fx-text-fill: white; -fx-background-radius: 12; -fx-font-weight: bold;" +
               "-fx-padding: 8 14 8 14; -fx-border-color: transparent;";
    }

    private String secondaryButtonStyle() {
        return "-fx-background-color: #0b1220; -fx-text-fill: #e2e8f0; -fx-background-radius: 10; -fx-border-color: #334155; -fx-border-radius: 10; -fx-padding: 6 10 6 10;";
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        if (darkMode) {
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #0f172a, #111827);");
        } else {
            root.setStyle("-fx-background-color: linear-gradient(to bottom, #ffffff, #f8fafc);");
        }
    }

    private void addSystem(String text) {
        addMessage("System", text);
    }

    private void addMessage(String sender, String text) {
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        boolean sentByYou = "You".equals(sender);
        boolean isSystem = "System".equals(sender);

        HBox row = new HBox();
        row.setMaxWidth(Double.MAX_VALUE);
        row.setPadding(new Insets(2, 0, 2, 0));

        VBox bubbleBox = new VBox(2);
        Label meta = new Label((isSystem ? "" : (sentByYou ? "You" : sender)) + (isSystem ? "" : ("  •  " + time)));
        meta.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(320);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.setStyle(isSystem
                ? "-fx-text-fill: #94a3b8;"
                : sentByYou
                    ? "-fx-background-color: linear-gradient(to right, #4f46e5, #06b6d4); -fx-text-fill: white; -fx-background-radius: 14;"
                    : "-fx-background-color: #e5e7eb; -fx-text-fill: #111827; -fx-background-radius: 14;");

        bubbleBox.getChildren().addAll(meta, bubble);

        if (isSystem) {
            row.setAlignment(Pos.CENTER);
            row.getChildren().add(bubbleBox);
        } else if (sentByYou) {
            row.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().add(bubbleBox);
        } else {
            row.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(bubbleBox);
        }

        chatMessages.getChildren().add(row);
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void startHttpServer() {
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(WEB_PORT)) {
                httpServerSocket = serverSocket;
                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleHttpConnection(client), "http-" + client.getPort()).start();
                }
            } catch (IOException e) {
                Platform.runLater(() -> addSystem("HTTP server stopped: " + e.getMessage()));
            }
        }, "http-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleHttpConnection(Socket socket) {
        try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             OutputStream rawOut = socket.getOutputStream();
             PrintWriter out = new PrintWriter(new OutputStreamWriter(rawOut, StandardCharsets.UTF_8), true)) {

            // Read request line and headers
            List<String> headers = new ArrayList<>();
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String line;
            int contentLength = 0;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                headers.add(line);
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-length:")) {
                    try { contentLength = Integer.parseInt(lower.split(":", 2)[1].trim()); } catch (Exception ignored) {}
                }
            }

            String method = requestLine.split(" ")[0];
            String path = requestLine.split(" ")[1];

            if ("GET".equals(method) && "/".equals(path)) {
                serveIndex(out);
            } else if ("GET".equals(method) && "/events".equals(path)) {
                handleSse(rawOut, out);
            } else if ("POST".equals(method) && "/send".equals(path)) {
                byte[] body = readBody(in, contentLength);
                String form = new String(body, StandardCharsets.UTF_8);
                String text = parseFormField(form, "text");
                if (text == null) text = "";
                String decoded = urlDecode(text);
                if (!decoded.isEmpty()) {
                    String finalDecoded = decoded;
                    Platform.runLater(() -> addMessage("Phone", finalDecoded));
                    broadcastEvent("phone", decoded);
                }
                writeNoContent(out);
            } else if ("GET".equals(method) && "/health".equals(path)) {
                writeText(out, 200, "OK", "text/plain", "ok");
            } else {
                writeText(out, 404, "Not Found", "text/plain", "Not Found");
            }
        } catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void serveIndex(PrintWriter out) {
        String html = "" +
                "<!doctype html>\n" +
                "<html><head><meta name=\\"viewport\\" content=\\"width=device-width, initial-scale=1, viewport-fit=cover\\">" +
                "<meta name=\\"theme-color\\" content=\\"#0b1220\\">" +
                "<title>LAN Chat</title>" +
                "<style>body{font-family:-apple-system,Segoe UI,Roboto,Inter,Helvetica,Arial,sans-serif;margin:0;background:#0b1220;color:#e2e8f0}" +
                ".wrap{max-width:680px;margin:0 auto;padding:16px}" +
                ".header{position:sticky;top:0;background:#0b1220e6;color:#e2e8f0;padding:16px;border-bottom:1px solid #1f2937;backdrop-filter:saturate(140%) blur(6px)}" +
                ".msg{margin:10px 0;display:flex}" +
                ".bubble{max-width:76%;padding:10px 14px;border-radius:14px;box-shadow:0 2px 6px rgba(0,0,0,.25);word-wrap:break-word;white-space:pre-wrap}" +
                ".me{justify-content:flex-end}.me .bubble{background:linear-gradient(90deg,#4f46e5,#06b6d4);color:#fff;border-bottom-right-radius:6px}" +
                ".you{justify-content:flex-start}.you .bubble{background:#1f2937;color:#e2e8f0;border-bottom-left-radius:6px}" +
                ".meta{font-size:12px;color:#94a3b8;margin:0 2px 6px 2px}" +
                ".inputbar{position:sticky;bottom:0;background:#0b1220e6;padding:12px;border-top:1px solid #1f2937;display:flex;gap:8px;backdrop-filter:saturate(140%) blur(6px)}" +
                "input{flex:1;padding:12px 14px;border-radius:12px;border:1px solid #334155;background:#0b1220;color:#e2e8f0}button{padding:12px 16px;border:0;background:linear-gradient(90deg,#4f46e5,#06b6d4);color:white;border-radius:12px;font-weight:600}" +
                "</style></head><body>" +
                "<div class=\\"header\\"><div class=\\"wrap\\"><div style=\\"font-weight:700;font-size:18px\\">LAN Chat</div><div style=\\"font-size:12px;color:#93c5fd\\">Connected to desktop</div></div></div>" +
                "<div class=\\"wrap\\" id=\\"wrap\\">" +
                "  <div id=\\"log\\"></div>" +
                "</div>" +
                "<div class=\\"inputbar\\">" +
                "  <input id=\\"text\\" placeholder=\\"Type a message\\" autocomplete=\\"off\\" />" +
                "  <button id=\\"send\\">Send</button>" +
                "</div>" +
                "<script>\n" +
                "const log = document.getElementById('log');\n" +
                "function add(sender,text){const row=document.createElement('div');row.className='msg '+(sender==='desktop'?'you':'me');\n" +
                "  const box=document.createElement('div');box.style.display='flex';box.style.flexDirection='column';\n" +
                "  const meta=document.createElement('div');meta.className='meta';meta.textContent=(sender==='desktop'?'Desktop':'Me')+' • '+new Date().toLocaleTimeString();\n" +
                "  const bubble=document.createElement('div');bubble.className='bubble';bubble.textContent=text;\n" +
                "  box.appendChild(meta);box.appendChild(bubble);row.appendChild(box);log.appendChild(row);window.scrollTo(0,document.body.scrollHeight);}\n" +
                "const ev=new EventSource('/events');ev.onmessage=e=>{try{const m=JSON.parse(e.data);add(m.sender,m.text);}catch(_){}};\n" +
                "const input=document.getElementById('text');const btn=document.getElementById('send');\n" +
                "function send(){const t=input.value.trim();if(!t)return;fetch('/send',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'text='+encodeURIComponent(t)});add('phone',t);input.value='';}\n" +
                "btn.addEventListener('click',send);input.addEventListener('keydown',e=>{if(e.key==='Enter')send();});\n" +
                "</script>" +
                "</body></html>";
        writeText(out, 200, "OK", "text/html; charset=utf-8", html);
    }

    private void handleSse(OutputStream rawOut, PrintWriter headerOut) {
        // Write SSE headers
        headerOut.print("HTTP/1.1 200 OK\r\n");
        headerOut.print("Content-Type: text/event-stream\r\n");
        headerOut.print("Cache-Control: no-cache\r\n");
        headerOut.print("Connection: keep-alive\r\n\r\n");
        headerOut.flush();

        PrintWriter eventWriter = new PrintWriter(new OutputStreamWriter(rawOut, StandardCharsets.UTF_8), true);
        sseClients.add(eventWriter);
        // Send a hello event so client knows it's connected
        sendSse(eventWriter, "{\"sender\":\"system\",\"text\":\"connected\"}");
    }

    private void broadcastEvent(String sender, String text) {
        String json = "{\"sender\":\"" + escapeJson(sender) + "\",\"text\":\"" + escapeJson(text) + "\"}";
        // Remove dead clients while iterating
        Iterator<PrintWriter> it = sseClients.iterator();
        while (it.hasNext()) {
            PrintWriter w = it.next();
            if (!sendSse(w, json)) {
                it.remove();
            }
        }
    }

    private boolean sendSse(PrintWriter w, String data) {
        try {
            w.print("data: ");
            w.print(data);
            w.print("\n\n");
            w.flush();
            return !w.checkError();
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeNoContent(PrintWriter out) {
        out.print("HTTP/1.1 204 No Content\r\n");
        out.print("Date: " + httpDate() + "\r\n");
        out.print("Content-Length: 0\r\n\r\n");
        out.flush();
    }

    private static void writeText(PrintWriter out, int code, String reason, String contentType, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        out.print("HTTP/1.1 " + code + " " + reason + "\r\n");
        out.print("Date: " + httpDate() + "\r\n");
        out.print("Content-Type: " + contentType + "\r\n");
        out.print("Content-Length: " + bytes.length + "\r\n\r\n");
        out.flush();
        try {
            out.write(body);
            out.flush();
        } catch (Exception ignored) {}
    }

    private static String httpDate() {
        SimpleDateFormat fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        return fmt.format(new Date());
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = in.read();
            if (b == -1) break;
            if (prev == '\r' && b == '\n') break;
            if (b != '\r') buf.write(b);
            prev = b;
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static byte[] readBody(InputStream in, int length) throws IOException {
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int r = in.read(body, read, length - read);
            if (r == -1) break;
            read += r;
        }
        return body;
    }

    private static String parseFormField(String form, String key) {
        String[] parts = form.split("&");
        for (String p : parts) {
            int idx = p.indexOf('=');
            if (idx > 0) {
                String k = p.substring(0, idx);
                String v = p.substring(idx + 1);
                if (k.equals(key)) return v;
            }
        }
        return null;
    }

    private static String urlDecode(String s) {
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8.name()); } catch (UnsupportedEncodingException e) { return s; }
    }

    private static String getLocalIpAddress() {
        try {
            InetAddress local = InetAddress.getLocalHost();
            return local.getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int)c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    @Override
    public void stop() {
        // Close SSE clients
        for (PrintWriter w : sseClients) {
            try { w.close(); } catch (Exception ignored) {}
        }
        sseClients.clear();
        try { if (httpServerSocket != null) httpServerSocket.close(); } catch (IOException ignored) {}
    }

    public static void main(String[] args) {
        launch(args);
    }
} 