import java.io.*;
import java.net.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

public class PerfectChatServer {
    private static final int WEB_PORT = 3000;
    private static final List<PrintWriter> sseClients = new CopyOnWriteArrayList<>();
    private static final List<String> messageHistory = new CopyOnWriteArrayList<>();
    private static final Map<String, String> userProfiles = new HashMap<>();
    private static ServerSocket httpServerSocket;
    private static final String NETWORK_IP = "10.0.0.88";
    
    // Settings
    private static boolean darkMode = true;
    private static boolean soundEnabled = true;
    private static boolean notificationsEnabled = true;
    private static String fontSize = "medium";

    public static void main(String[] args) {
        System.out.println("Starting Final LAN Chat Server...");
        
        String url = "http://" + NETWORK_IP + ":" + WEB_PORT + "/";
        System.out.println("PHONE CONNECTION OPTIONS:");
        System.out.println("1. Direct URL: " + url);
        System.out.println("2. Connection Helper: " + url + "connect");
        System.out.println("3. Profile Setup: " + url + "profile");
        System.out.println("4. Settings: " + url + "settings");
        System.out.println("Messages will appear here when sent from phone");
        
        startHttpServer();
        
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Server stopped.");
        }
    }

    private static void addMessage(String sender, String text) {
        String time = new SimpleDateFormat("HH:mm").format(new Date());
        String message = "[" + time + "] " + sender + ": " + text;
        messageHistory.add(message);
        System.out.println(message);
    }

    private static void startHttpServer() {
        Thread serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(WEB_PORT)) {
                httpServerSocket = serverSocket;
                System.out.println("Server running on port " + WEB_PORT);
                
                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleHttpConnection(client), "http-" + client.getPort()).start();
                }
            } catch (IOException e) {
                System.err.println("HTTP server error: " + e.getMessage());
            }
        }, "http-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private static void handleHttpConnection(Socket socket) {
        try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
             OutputStream rawOut = socket.getOutputStream();
             PrintWriter out = new PrintWriter(new OutputStreamWriter(rawOut, StandardCharsets.UTF_8), true)) {

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
                String avatar = parseFormField(form, "avatar");
                String name = parseFormField(form, "name");
                if (text == null) text = "";
                String decoded = urlDecode(text);
                if (!decoded.isEmpty()) {
                    String displayName = name != null ? name : "Phone";
                    addMessage(displayName, decoded);
                    broadcastEvent("phone", decoded, avatar, name);
                }
                writeNoContent(out);
            } else if ("POST".equals(method) && "/profile".equals(path)) {
                byte[] body = readBody(in, contentLength);
                String form = new String(body, StandardCharsets.UTF_8);
                String avatar = parseFormField(form, "avatar");
                String name = parseFormField(form, "name");
                String sessionId = parseFormField(form, "sessionId");
                if (avatar != null && name != null && sessionId != null) {
                    userProfiles.put(sessionId, avatar + ":" + name);
                    writeText(out, 200, "OK", "application/json", "{\"success\":true}");
                } else {
                    writeText(out, 400, "Bad Request", "application/json", "{\"success\":false}");
                }
            } else if ("POST".equals(method) && "/settings".equals(path)) {
                byte[] body = readBody(in, contentLength);
                String form = new String(body, StandardCharsets.UTF_8);
                String setting = parseFormField(form, "setting");
                String value = parseFormField(form, "value");
                if (setting != null && value != null) {
                    updateSetting(setting, value);
                    writeText(out, 200, "OK", "application/json", "{\"success\":true}");
                } else {
                    writeText(out, 400, "Bad Request", "application/json", "{\"success\":false}");
                }
            } else if ("GET".equals(method) && "/health".equals(path)) {
                writeText(out, 200, "OK", "text/plain", "ok");
            } else if ("GET".equals(method) && "/connect".equals(path)) {
                serveConnectionHelper(out);
            } else if ("GET".equals(method) && "/profile".equals(path)) {
                serveProfilePage(out);
            } else if ("GET".equals(method) && "/settings".equals(path)) {
                serveSettingsPage(out);
            } else if (path.startsWith("/assets/")) {
                serveAsset(path, rawOut);
            } else {
                writeText(out, 404, "Not Found", "text/plain", "Not Found");
            }
        } catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void updateSetting(String setting, String value) {
        switch (setting) {
            case "darkMode":
                darkMode = "true".equals(value);
                break;
            case "soundEnabled":
                soundEnabled = "true".equals(value);
                break;
            case "notificationsEnabled":
                notificationsEnabled = "true".equals(value);
                break;
            case "fontSize":
                fontSize = value;
                break;
        }
    }

    private static void serveAsset(String path, OutputStream rawOut) throws IOException {
        String fileName = path.substring("/assets/".length());
        File assetFile = new File("assets/" + fileName);
        
        if (assetFile.exists() && assetFile.isFile()) {
            String contentType = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") ? "image/jpeg" : "application/octet-stream";
            
            PrintWriter headerOut = new PrintWriter(new OutputStreamWriter(rawOut, StandardCharsets.UTF_8), true);
            headerOut.print("HTTP/1.1 200 OK\r\n");
            headerOut.print("Content-Type: " + contentType + "\r\n");
            headerOut.print("Content-Length: " + assetFile.length() + "\r\n");
            headerOut.print("Cache-Control: public, max-age=3600\r\n\r\n");
            headerOut.flush();
            
            try (FileInputStream fis = new FileInputStream(assetFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    rawOut.write(buffer, 0, bytesRead);
                }
                rawOut.flush();
            }
        } else {
            PrintWriter headerOut = new PrintWriter(new OutputStreamWriter(rawOut, StandardCharsets.UTF_8), true);
            writeText(headerOut, 404, "Not Found", "text/plain", "Asset not found");
        }
    }

    private static void serveProfilePage(PrintWriter out) {
        String html = "" +
                "<!doctype html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>Profile Setup - AlphaChat</title>" +
                "<style>" +
                "*{margin:0;padding:0;box-sizing:border-box;font-family:\"Inter\",-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif}" +
                "body{background:linear-gradient(135deg,#0f0f23 0%,#1a1a2e 100%);color:#f8fafc;line-height:1.6;min-height:100vh;overflow-x:hidden}" +
                ".app-container{min-height:100vh;display:flex;flex-direction:column}" +
                ".navbar{background:rgba(30,41,59,0.8);backdrop-filter:blur(20px);border-bottom:1px solid #334155;padding:1rem 1.5rem;display:flex;justify-content:space-between;align-items:center;position:sticky;top:0;z-index:100}" +
                ".nav-brand{display:flex;align-items:center;gap:0.5rem;font-size:1.25rem;font-weight:700;color:#f8fafc}" +
                ".nav-brand i{color:#6366f1;font-size:1.5rem}" +
                ".nav-links{display:flex;gap:0.5rem}" +
                ".nav-link{display:flex;align-items:center;justify-content:center;width:40px;height:40px;border-radius:0.75rem;background:#1e293b;color:#cbd5e1;text-decoration:none;transition:all 150ms ease-in-out;border:1px solid #334155}" +
                ".nav-link:hover{background:#6366f1;color:#f8fafc;transform:translateY(-2px);box-shadow:0 10px 15px -3px rgba(0,0,0,0.1)}" +
                ".container{max-width:800px;margin:2rem auto;background:#1e293b;padding:3rem;border-radius:2rem;border:1px solid #334155;box-shadow:0 25px 50px -12px rgba(0,0,0,0.25)}" +
                ".header{text-align:center;margin-bottom:3rem}" +
                ".header h1{font-size:2.5rem;font-weight:700;margin-bottom:1rem;background:linear-gradient(135deg,#f8fafc,#60a5fa);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}" +
                ".header p{font-size:1.125rem;color:#cbd5e1}" +
                ".profile-section{margin:2rem 0;padding:2rem;background:#0f172a;border-radius:1.5rem;border:1px solid #334155}" +
                ".section-title{font-size:1.5rem;font-weight:600;margin-bottom:1.5rem;color:#f8fafc;display:flex;align-items:center;gap:0.5rem}" +
                ".section-title::before{content:'👤';font-size:1.25rem}" +
                ".name-input-group{margin-bottom:2rem}" +
                ".input-label{display:block;font-size:1rem;font-weight:500;color:#cbd5e1;margin-bottom:0.5rem}" +
                ".name-input{width:100%;padding:1rem 1.25rem;border:2px solid #334155;border-radius:0.75rem;background:#0f172a;color:#f8fafc;font-size:1rem;transition:all 150ms ease-in-out}" +
                ".name-input:focus{outline:none;border-color:#6366f1;box-shadow:0 0 0 3px rgba(99,102,241,0.1)}" +
                ".name-input::placeholder{color:#64748b}" +
                ".current-profile{display:flex;align-items:center;gap:1.5rem;padding:1.5rem;background:#1e293b;border-radius:1rem;border:1px solid #334155;margin-bottom:2rem}" +
                ".current-avatar{width:80px;height:80px;border-radius:50%;object-fit:cover;border:3px solid #334155;transition:all 150ms ease-in-out}" +
                ".current-avatar:hover{border-color:#6366f1;transform:scale(1.05)}" +
                ".current-info h3{font-size:1.25rem;font-weight:600;color:#f8fafc;margin-bottom:0.25rem}" +
                ".current-info p{color:#cbd5e1;font-size:0.875rem}" +
                ".avatar-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(120px,1fr));gap:1rem;margin:1.5rem 0}" +
                ".avatar-option{background:#0f172a;border:2px solid #334155;border-radius:1rem;padding:1rem;text-align:center;cursor:pointer;transition:all 250ms ease-in-out;position:relative;overflow:hidden}" +
                ".avatar-option::before{content:'';position:absolute;top:0;left:0;right:0;height:3px;background:linear-gradient(90deg,#6366f1,#06b6d4);transform:scaleX(0);transition:transform 250ms ease-in-out}" +
                ".avatar-option:hover{border-color:#6366f1;background:#1e293b;transform:translateY(-4px);box-shadow:0 10px 25px -5px rgba(0,0,0,0.1)}" +
                ".avatar-option:hover::before{transform:scaleX(1)}" +
                ".avatar-option.selected{border-color:#06b6d4;background:linear-gradient(135deg,#1e293b,rgba(6,182,212,0.1));box-shadow:0 0 0 3px rgba(6,182,212,0.2)}" +
                ".avatar-option.selected::before{transform:scaleX(1)}" +
                ".avatar-img{width:60px;height:60px;border-radius:50%;object-fit:cover;margin:0 auto 0.75rem;border:2px solid #334155;transition:all 150ms ease-in-out}" +
                ".avatar-option:hover .avatar-img{border-color:#6366f1;transform:scale(1.1)}" +
                ".avatar-name{font-size:0.875rem;font-weight:500;color:#cbd5e1;margin-bottom:0.25rem}" +
                ".avatar-description{font-size:0.75rem;color:#64748b}" +
                ".btn{display:inline-flex;align-items:center;justify-content:center;gap:0.5rem;padding:1rem 2rem;border:none;border-radius:0.75rem;font-weight:600;font-size:1rem;cursor:pointer;transition:all 150ms ease-in-out;text-decoration:none;position:relative;overflow:hidden;min-width:150px}" +
                ".btn-primary{background:linear-gradient(135deg,#6366f1,#4f46e5);color:white;box-shadow:0 4px 6px -1px rgba(0,0,0,0.1)}" +
                ".btn-primary:hover{transform:translateY(-2px);box-shadow:0 20px 25px -5px rgba(0,0,0,0.1)}" +
                ".btn-secondary{background:#334155;color:#cbd5e1;border:1px solid #475569}" +
                ".btn-secondary:hover{background:#475569;color:#f8fafc}" +
                ".btn:disabled{opacity:0.5;cursor:not-allowed;transform:none;box-shadow:none}" +
                ".btn-group{display:flex;gap:1rem;margin-top:2rem;flex-wrap:wrap}" +
                ".status-message{padding:1rem;border-radius:0.75rem;margin:1rem 0;text-align:center;font-weight:500;display:none}" +
                ".status-success{background:rgba(16,185,129,0.1);color:#10b981;border:1px solid rgba(16,185,129,0.2)}" +
                ".status-error{background:rgba(239,68,68,0.1);color:#ef4444;border:1px solid rgba(239,68,68,0.2)}" +
                ".character-count{font-size:0.75rem;color:#64748b;text-align:right;margin-top:0.25rem}" +
                ".character-count.warning{color:#f59e0b}" +
                ".character-count.error{color:#ef4444}" +
                "@media (max-width:768px){.container{padding:2rem;margin:1rem}.header h1{font-size:2rem}.avatar-grid{grid-template-columns:repeat(auto-fit,minmax(100px,1fr))}.btn-group{flex-direction:column}.current-profile{flex-direction:column;text-align:center}}" +
                "</style></head><body>" +
                "<div class=\"app-container\">" +
                "<nav class=\"navbar\">" +
                "<div class=\"nav-brand\">" +
                "<span>🎭</span>" +
                "<span>AlphaChat Profile</span>" +
                "</div>" +
                "<div class=\"nav-links\">" +
                "<a href=\"/\" class=\"nav-link\" title=\"Back to Chat\">💬</a>" +
                "<a href=\"/settings\" class=\"nav-link\" title=\"Settings\">⚙️</a>" +
                "</div>" +
                "</nav>" +
                "<div class=\"container\">" +
                "<div class=\"header\">" +
                "<h1>Customize Your Profile</h1>" +
                "<p>Choose your avatar and display name to personalize your chat experience</p>" +
                "</div>" +
                
                "<div class=\"profile-section\">" +
                "<h2 class=\"section-title\">Display Name</h2>" +
                "<div class=\"name-input-group\">" +
                "<label class=\"input-label\" for=\"displayName\">What should others call you?</label>" +
                "<input type=\"text\" id=\"displayName\" class=\"name-input\" placeholder=\"Enter your display name...\" maxlength=\"20\" />" +
                "<div class=\"character-count\" id=\"charCount\">0/20 characters</div>" +
                "</div>" +
                "</div>" +
                
                "<div class=\"profile-section\">" +
                "<h2 class=\"section-title\">Choose Your Avatar</h2>" +
                "<div class=\"current-profile\">" +
                "<div id=\"currentAvatar\" class=\"current-avatar\" style=\"background:#334155;display:flex;align-items:center;justify-content:center;color:#94a3b8;font-size:2rem;\">👤</div>" +
                "<div class=\"current-info\">" +
                "<h3 id=\"currentName\">Select an avatar below</h3>" +
                "<p id=\"currentDescription\">Your avatar will appear in chat messages</p>" +
                "</div>" +
                "</div>" +
                
                "<div class=\"avatar-grid\" id=\"avatarGrid\">" +
                generateEnhancedAvatarOptions() +
                "</div>" +
                "</div>" +
                
                "<div class=\"status-message\" id=\"statusMessage\"></div>" +
                
                "<div class=\"btn-group\">" +
                "<button id=\"saveProfile\" class=\"btn btn-primary\" disabled onclick=\"saveProfile()\">💾 Save Profile</button>" +
                "<a href=\"/\" class=\"btn btn-secondary\">← Back to Chat</a>" +
                "</div>" +
                "</div>" +
                "</div>" +
                
                "<script>" +
                "let selectedAvatar = null;" +
                "let selectedName = '';" +
                "let sessionId = 'session_' + Date.now();" +
                "const maxNameLength = 20;" +
                
                "const nameInput = document.getElementById('displayName');" +
                "const charCount = document.getElementById('charCount');" +
                "const saveBtn = document.getElementById('saveProfile');" +
                "const statusMsg = document.getElementById('statusMessage');" +
                
                "// Load saved profile on page load" +
                "window.onload = () => {" +
                "  const savedAvatar = localStorage.getItem('userAvatar');" +
                "  const savedName = localStorage.getItem('userName');" +
                "  " +
                "  if (savedAvatar) {" +
                "    selectAvatar(savedAvatar, getAvatarName(savedAvatar));" +
                "  }" +
                "  if (savedName) {" +
                "    nameInput.value = savedName;" +
                "    selectedName = savedName;" +
                "    updateCharCount();" +
                "    updateSaveButton();" +
                "  }" +
                "};" +
                
                "// Name input handling" +
                "nameInput.addEventListener('input', function() {" +
                "  selectedName = this.value.trim();" +
                "  updateCharCount();" +
                "  updateSaveButton();" +
                "});" +
                
                "function updateCharCount() {" +
                "  const count = selectedName.length;" +
                "  charCount.textContent = count + '/' + maxNameLength + ' characters';" +
                "  charCount.className = 'character-count';" +
                "  " +
                "  if (count > maxNameLength * 0.8) {" +
                "    charCount.classList.add('warning');" +
                "  }" +
                "  if (count > maxNameLength) {" +
                "    charCount.classList.add('error');" +
                "  }" +
                "}" +
                
                "function updateSaveButton() {" +
                "  const isValid = selectedAvatar && selectedName.length > 0 && selectedName.length <= maxNameLength;" +
                "  saveBtn.disabled = !isValid;" +
                "}" +
                
                "function selectAvatar(avatarId, avatarName) {" +
                "  selectedAvatar = avatarId;" +
                "  const currentAvatar = document.getElementById('currentAvatar');" +
                "  const currentName = document.getElementById('currentName');" +
                "  const currentDesc = document.getElementById('currentDescription');" +
                "  " +
                "  currentAvatar.innerHTML = '<img src=\"/assets/' + avatarId + '.jpg\" class=\"current-avatar\" />';" +
                "  currentName.textContent = avatarName;" +
                "  currentDesc.textContent = 'Your avatar will appear in chat messages';" +
                "  " +
                "  // Update selection visual" +
                "  document.querySelectorAll('.avatar-option').forEach(opt => opt.classList.remove('selected'));" +
                "  document.querySelectorAll('.avatar-option').forEach(opt => {" +
                "    if (opt.getAttribute('data-avatar') === avatarId) opt.classList.add('selected');" +
                "  });" +
                "  " +
                "  updateSaveButton();" +
                "}" +
                
                "function getAvatarName(avatarId) {" +
                "  const avatarNames = {" +
                "    'avatar_2': 'Cool Cat', 'avatar_4': 'Tech Wizard', 'avatar_5': 'Mystic Owl'," +
                "    'avatar_6': 'Cyber Fox', 'avatar_7': 'Neon Tiger', 'avatar_8': 'Pixel Panda'," +
                "    'avatar_9': 'Digital Dragon', 'avatar_10': 'Quantum Quail', 'avatar_11': 'Matrix Mouse'," +
                "    'avatar_12': 'Binary Bear', 'avatar_13': 'Code Coyote', 'avatar_14': 'Data Deer'," +
                "    'avatar_15': 'Logic Lion', 'avatar_16': 'Algorithm Ant', 'avatar_17': 'Function Falcon'," +
                "    'avatar_18': 'Variable Vulture', 'avatar_19': 'Loop Lemur', 'avatar_20': 'Array Armadillo'," +
                "    'avatar_21': 'String Squirrel', 'avatar_22': 'Object Octopus'" +
                "  };" +
                "  return avatarNames[avatarId] || 'Unknown Avatar';" +
                "}" +
                
                "function showStatus(message, type = 'success') {" +
                "  statusMsg.textContent = message;" +
                "  statusMsg.className = 'status-message status-' + type;" +
                "  statusMsg.style.display = 'block';" +
                "  setTimeout(() => {" +
                "    statusMsg.style.display = 'none';" +
                "  }, 3000);" +
                "}" +
                
                "function saveProfile() {" +
                "  if (!selectedAvatar || !selectedName) {" +
                "    showStatus('Please select an avatar and enter a name!', 'error');" +
                "    return;" +
                "  }" +
                "  " +
                "  if (selectedName.length > maxNameLength) {" +
                "    showStatus('Name is too long! Maximum 20 characters.', 'error');" +
                "    return;" +
                "  }" +
                "  " +
                "  const formData = new FormData();" +
                "  formData.append('avatar', selectedAvatar);" +
                "  formData.append('name', selectedName);" +
                "  formData.append('sessionId', sessionId);" +
                "  " +
                "  fetch('/profile', {" +
                "    method: 'POST'," +
                "    body: formData" +
                "  }).then(response => response.json())" +
                "    .then(data => {" +
                "      if (data.success) {" +
                "        localStorage.setItem('userAvatar', selectedAvatar);" +
                "        localStorage.setItem('userName', selectedName);" +
                "        showStatus('Profile saved successfully! Your avatar and name will appear in chat messages.', 'success');" +
                "        setTimeout(() => {" +
                "          window.location.href = '/';" +
                "        }, 1500);" +
                "      } else {" +
                "        showStatus('Failed to save profile. Please try again.', 'error');" +
                "      }" +
                "    }).catch(error => {" +
                "      showStatus('Network error. Please check your connection.', 'error');" +
                "    });" +
                "}" +
                "</script>" +
                "</body></html>";
        writeText(out, 200, "OK", "text/html; charset=utf-8", html);
    }

    private static void serveSettingsPage(PrintWriter out) {
        String html = "" +
                "<!doctype html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>Settings - LAN Chat</title>" +
                "<style>body{font-family:sans-serif;margin:0;background:#0b1220;color:#e2e8f0;padding:20px}" +
                ".container{max-width:500px;margin:0 auto;background:#1f2937;padding:30px;border-radius:20px}" +
                "h1{color:#4f46e5;margin-bottom:20px;text-align:center}" +
                ".setting-group{margin:20px 0;padding:20px;background:#0b1220;border-radius:12px}" +
                ".setting-group h3{color:#93c5fd;margin-top:0}" +
                ".setting-item{display:flex;justify-content:space-between;align-items:center;margin:15px 0}" +
                ".setting-label{flex:1}" +
                ".toggle{position:relative;width:50px;height:24px;background:#334155;border-radius:12px;cursor:pointer;transition:background 0.3s}" +
                ".toggle.active{background:#4f46e5}" +
                ".toggle::after{content:'';position:absolute;top:2px;left:2px;width:20px;height:20px;background:white;border-radius:50%;transition:transform 0.3s}" +
                ".toggle.active::after{transform:translateX(26px)}" +
                ".select{background:#334155;color:#e2e8f0;border:none;padding:8px 12px;border-radius:6px}" +
                ".btn{background:linear-gradient(90deg,#4f46e5,#06b6d4);color:white;padding:12px 24px;border:none;border-radius:10px;margin:10px;cursor:pointer;text-decoration:none;display:inline-block;width:100%;text-align:center}" +
                ".status{text-align:center;margin:20px 0;padding:10px;background:#059669;border-radius:8px;display:none}" +
                "</style></head><body>" +
                "<div class=\"container\">" +
                "<h1>Settings</h1>" +
                
                "<div class=\"setting-group\">" +
                "<h3>Appearance</h3>" +
                "<div class=\"setting-item\">" +
                "<div class=\"setting-label\">Dark Mode</div>" +
                "<div class=\"toggle active\" id=\"darkModeToggle\" onclick=\"toggleSetting('darkMode')\"></div>" +
                "</div>" +
                "<div class=\"setting-item\">" +
                "<div class=\"setting-label\">Font Size</div>" +
                "<select class=\"select\" id=\"fontSizeSelect\" onchange=\"updateSetting('fontSize', this.value)\">" +
                "<option value=\"small\">Small</option>" +
                "<option value=\"medium\" selected>Medium</option>" +
                "<option value=\"large\">Large</option>" +
                "</select>" +
                "</div>" +
                "</div>" +
                
                "<div class=\"setting-group\">" +
                "<h3>Audio</h3>" +
                "<div class=\"setting-item\">" +
                "<div class=\"setting-label\">Sound Effects</div>" +
                "<div class=\"toggle active\" id=\"soundToggle\" onclick=\"toggleSetting('soundEnabled')\"></div>" +
                "</div>" +
                "</div>" +
                
                "<div class=\"setting-group\">" +
                "<h3>Notifications</h3>" +
                "<div class=\"setting-item\">" +
                "<div class=\"setting-label\">Push Notifications</div>" +
                "<div class=\"toggle active\" id=\"notificationsToggle\" onclick=\"toggleSetting('notificationsEnabled')\"></div>" +
                "</div>" +
                "</div>" +
                
                "<div class=\"status\" id=\"status\">Settings saved!</div>" +
                
                "<a href=\"/\" class=\"btn\">Back to Chat</a>" +
                "<a href=\"/profile\" class=\"btn\">Change Profile</a>" +
                "</div>" +
                
                "<script>" +
                "function toggleSetting(setting) {" +
                "  const toggle = document.getElementById(setting + 'Toggle');" +
                "  const isActive = toggle.classList.contains('active');" +
                "  " +
                "  if (isActive) {" +
                "    toggle.classList.remove('active');" +
                "    updateSetting(setting, 'false');" +
                "  } else {" +
                "    toggle.classList.add('active');" +
                "    updateSetting(setting, 'true');" +
                "  }" +
                "}" +
                
                "function updateSetting(setting, value) {" +
                "  const formData = new FormData();" +
                "  formData.append('setting', setting);" +
                "  formData.append('value', value);" +
                "  " +
                "  fetch('/settings', {" +
                "    method: 'POST'," +
                "    body: formData" +
                "  }).then(response => response.json())" +
                "    .then(data => {" +
                "      if (data.success) {" +
                "        showStatus('Setting updated!');" +
                "      }" +
                "    });" +
                "}" +
                
                "function showStatus(message) {" +
                "  const status = document.getElementById('status');" +
                "  status.textContent = message;" +
                "  status.style.display = 'block';" +
                "  setTimeout(() => {" +
                "    status.style.display = 'none';" +
                "  }, 2000);" +
                "}" +
                "</script>" +
                "</body></html>";
        writeText(out, 200, "OK", "text/html; charset=utf-8", html);
    }

    private static String generateAvatarOptions() {
        StringBuilder options = new StringBuilder();
        String[] avatarNames = {
            "Cool Cat", "Tech Wizard", "Mystic Owl", "Cyber Fox", "Neon Tiger",
            "Pixel Panda", "Digital Dragon", "Quantum Quail", "Matrix Mouse", "Binary Bear",
            "Code Coyote", "Data Deer", "Logic Lion", "Algorithm Ant", "Function Falcon",
            "Variable Vulture", "Loop Lemur", "Array Armadillo", "String Squirrel", "Object Octopus"
        };
        
        int nameIndex = 0;
        for (int i = 2; i <= 22; i++) {
            if (i == 3) continue;
            String avatarId = "avatar_" + i;
            String avatarName = avatarNames[nameIndex];
            options.append("<div class=\"avatar-option\" onclick=\"selectAvatar('").append(avatarId).append("', '").append(avatarName).append("')\">");
            options.append("<img src=\"/assets/").append(avatarId).append(".jpg\" class=\"avatar-img\" />");
            options.append("<div class=\"avatar-name\">").append(avatarName).append("</div>");
            options.append("</div>");
            nameIndex++;
        }
        
        return options.toString();
    }

    private static String generateEnhancedAvatarOptions() {
        StringBuilder options = new StringBuilder();
        String[] avatarNames = {
            "Cool Cat", "Tech Wizard", "Mystic Owl", "Cyber Fox", "Neon Tiger",
            "Pixel Panda", "Digital Dragon", "Quantum Quail", "Matrix Mouse", "Binary Bear",
            "Code Coyote", "Data Deer", "Logic Lion", "Algorithm Ant", "Function Falcon",
            "Variable Vulture", "Loop Lemur", "Array Armadillo", "String Squirrel", "Object Octopus"
        };
        
        String[] descriptions = {
            "Friendly and adventurous", "Master of technology", "Wise and mysterious", "Sly and clever", "Bold and energetic",
            "Cute and cuddly", "Powerful and magical", "Quick and agile", "Small but mighty", "Strong and reliable",
            "Clever and resourceful", "Graceful and swift", "Bold and confident", "Hardworking and persistent", "Sharp and focused",
            "Patient and observant", "Playful and curious", "Organized and methodical", "Flexible and adaptable", "Complex and intelligent"
        };
        
        int nameIndex = 0;
        for (int i = 2; i <= 22; i++) {
            if (i == 3) continue;
            String avatarId = "avatar_" + i;
            String avatarName = avatarNames[nameIndex];
            String description = descriptions[nameIndex];
            options.append("<div class=\"avatar-option\" data-avatar=\"").append(avatarId).append("\" onclick=\"selectAvatar('").append(avatarId).append("', '").append(avatarName).append("')\">");
            options.append("<img src=\"/assets/").append(avatarId).append(".jpg\" class=\"avatar-img\" />");
            options.append("<div class=\"avatar-name\">").append(avatarName).append("</div>");
            options.append("<div class=\"avatar-description\">").append(description).append("</div>");
            options.append("</div>");
            nameIndex++;
        }
        
        return options.toString();
    }

    private static void serveConnectionHelper(PrintWriter out) {
        String url = "http://" + NETWORK_IP + ":" + WEB_PORT + "/";
        String html = "" +
                "<!doctype html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>Connect to Chat</title>" +
                "<style>body{font-family:sans-serif;margin:0;background:#0b1220;color:#e2e8f0;text-align:center;padding:20px}" +
                ".container{max-width:500px;margin:0 auto;background:#1f2937;padding:30px;border-radius:20px}" +
                "h1{color:#4f46e5;margin-bottom:20px}" +
                ".option{background:#0b1220;padding:20px;border-radius:15px;margin:15px 0;border:1px solid #334155}" +
                ".url{background:#1f2937;padding:15px;border-radius:10px;margin:10px 0;word-break:break-all;font-family:monospace;font-size:14px}" +
                ".btn{background:linear-gradient(90deg,#4f46e5,#06b6d4);color:white;padding:12px 24px;border:none;border-radius:10px;margin:10px;cursor:pointer;text-decoration:none;display:inline-block}" +
                ".copy-btn{background:#059669;font-size:12px;padding:8px 16px}" +
                "</style></head><body>" +
                "<div class=\"container\">" +
                "<h1>Connect Your Phone</h1>" +
                
                "<div class=\"option\">" +
                "<h3>Direct Link</h3>" +
                "<div class=\"url\">" + url + "</div>" +
                "<button class=\"copy-btn\" onclick=\"navigator.clipboard.writeText('" + url + "')\">Copy URL</button>" +
                "</div>" +
                
                "<div class=\"option\">" +
                "<h3>Manual Entry</h3>" +
                "<p>Type this in your phone's browser:</p>" +
                "<div class=\"url\">" + NETWORK_IP + ":3000</div>" +
                "</div>" +
                
                "<a href=\"/\" class=\"btn\">Open Chat</a>" +
                "<a href=\"/profile\" class=\"btn\">Setup Profile</a>" +
                "<a href=\"/settings\" class=\"btn\">Settings</a>" +
                "</div>" +
                "</body></html>";
        writeText(out, 200, "OK", "text/html; charset=utf-8", html);
    }

    private static void serveIndex(PrintWriter out) {
        String url = "http://" + NETWORK_IP + ":" + WEB_PORT + "/";
        String html = "" +
                "<!doctype html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">" +
                "<meta name=\"theme-color\" content=\"#1a1a2e\">" +
                "<title>AlphaChat - Phone to Desktop Messaging</title>" +
                "<style>" +
                "*{margin:0;padding:0;box-sizing:border-box;font-family:\"Inter\",-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif}" +
                "body{background:linear-gradient(135deg,#0f0f23 0%,#1a1a2e 100%);color:#f8fafc;line-height:1.6;min-height:100vh;overflow-x:hidden}" +
                ".app-container{min-height:100vh;display:flex;flex-direction:column}" +
                ".navbar{background:rgba(30,41,59,0.8);backdrop-filter:blur(20px);border-bottom:1px solid #334155;padding:1rem 1.5rem;display:flex;justify-content:space-between;align-items:center;position:sticky;top:0;z-index:100}" +
                ".nav-brand{display:flex;align-items:center;gap:0.5rem;font-size:1.25rem;font-weight:700;color:#f8fafc}" +
                ".nav-brand i{color:#6366f1;font-size:1.5rem}" +
                ".nav-links{display:flex;gap:0.5rem}" +
                ".nav-link{display:flex;align-items:center;justify-content:center;width:40px;height:40px;border-radius:0.75rem;background:#1e293b;color:#cbd5e1;text-decoration:none;transition:all 150ms ease-in-out;border:1px solid #334155}" +
                ".nav-link:hover{background:#6366f1;color:#f8fafc;transform:translateY(-2px);box-shadow:0 10px 15px -3px rgba(0,0,0,0.1)}" +
                ".chat-container{flex:1;display:flex;flex-direction:column;max-width:1200px;margin:0 auto;width:100%;padding:1.5rem}" +
                ".messages-area{flex:1;background:#1e293b;border-radius:1.5rem;border:1px solid #334155;padding:1.5rem;margin-bottom:1rem;overflow-y:auto;max-height:60vh;min-height:400px}" +
                ".msg{margin:1rem 0;display:flex;animation:fadeInUp 0.3s ease-out}" +
                "@keyframes fadeInUp{from{opacity:0;transform:translateY(20px)}to{opacity:1;transform:translateY(0)}}" +
                ".bubble{max-width:76%;padding:1rem 1.25rem;border-radius:1.25rem;box-shadow:0 4px 12px rgba(0,0,0,0.15);word-wrap:break-word;white-space:pre-wrap;position:relative}" +
                ".me{justify-content:flex-end}.me .bubble{background:linear-gradient(135deg,#6366f1,#4f46e5);color:#fff;border-bottom-right-radius:0.5rem}" +
                ".you{justify-content:flex-start}.you .bubble{background:#0f172a;color:#e2e8f0;border:1px solid #334155;border-bottom-left-radius:0.5rem}" +
                ".meta{font-size:0.75rem;color:#94a3b8;margin:0 0.5rem 0.5rem 0.5rem;display:flex;align-items:center;gap:0.5rem}" +
                ".avatar{width:24px;height:24px;border-radius:50%;object-fit:cover;border:2px solid #334155}" +
                ".me .avatar{border-color:#6366f1}" +
                ".you .avatar{border-color:#06b6d4}" +
                ".inputbar{position:sticky;bottom:0;background:rgba(15,15,35,0.95);padding:1rem;border-radius:1rem;border:1px solid #334155;display:flex;gap:0.75rem;backdrop-filter:saturate(140%) blur(10px);margin-top:auto}" +
                "input{flex:1;padding:1rem 1.25rem;border-radius:0.75rem;border:2px solid #334155;background:#0f172a;color:#e2e8f0;font-size:1rem;transition:all 150ms ease-in-out}" +
                "input:focus{outline:none;border-color:#6366f1;box-shadow:0 0 0 3px rgba(99,102,241,0.1)}" +
                "input::placeholder{color:#64748b}" +
                "button{padding:1rem 1.5rem;border:0;background:linear-gradient(135deg,#6366f1,#4f46e5);color:white;border-radius:0.75rem;font-weight:600;cursor:pointer;transition:all 150ms ease-in-out}" +
                "button:hover{transform:translateY(-2px);box-shadow:0 8px 25px -5px rgba(99,102,241,0.3)}" +
                ".emoji-btn{background:#334155;color:#e2e8f0;border:2px solid #475569;padding:0.75rem;border-radius:0.75rem;cursor:pointer;transition:all 150ms ease-in-out}" +
                ".emoji-btn:hover{background:#475569;border-color:#6366f1}" +
                ".emoji-picker{position:fixed;bottom:100px;right:20px;background:#1e293b;border:1px solid #334155;border-radius:1rem;padding:1rem;display:none;grid-template-columns:repeat(8,1fr);gap:0.5rem;max-width:300px;box-shadow:0 25px 50px -12px rgba(0,0,0,0.25);backdrop-filter:blur(10px)}" +
                ".emoji-item{font-size:1.25rem;cursor:pointer;padding:0.5rem;border-radius:0.5rem;text-align:center;transition:all 150ms ease-in-out}" +
                ".emoji-item:hover{background:#334155;transform:scale(1.1)}" +
                ".welcome-message{text-align:center;padding:3rem;color:#cbd5e1}" +
                ".welcome-message h2{font-size:1.5rem;margin-bottom:1rem;color:#f8fafc}" +
                ".welcome-message p{margin-bottom:0.5rem}" +
                ".connection-status{display:flex;align-items:center;gap:0.5rem;padding:0.5rem 1rem;background:rgba(16,185,129,0.1);border:1px solid rgba(16,185,129,0.2);border-radius:0.75rem;margin-bottom:1rem;color:#10b981;font-size:0.875rem}" +
                ".status-dot{width:8px;height:8px;background:#10b981;border-radius:50%;animation:pulse 2s infinite}" +
                "@keyframes pulse{0%,100%{opacity:1}50%{opacity:0.5}}" +
                "@media (max-width:768px){.chat-container{padding:1rem}.messages-area{padding:1rem;max-height:50vh}.inputbar{padding:0.75rem;gap:0.5rem}.emoji-picker{width:280px;right:10px;grid-template-columns:repeat(6,1fr)}}" +
                "</style></head><body>" +
                "<div class=\"app-container\">" +
                "<nav class=\"navbar\">" +
                "<div class=\"nav-brand\">" +
                "<span>💬</span>" +
                "<span>AlphaChat</span>" +
                "</div>" +
                "<div class=\"nav-links\">" +
                "<a href=\"/profile\" class=\"nav-link\" title=\"Profile\">👤</a>" +
                "<a href=\"/settings\" class=\"nav-link\" title=\"Settings\">⚙️</a>" +
                "<a href=\"/connect\" class=\"nav-link\" title=\"Connect\">🔗</a>" +
                "</div>" +
                "</nav>" +
                "<div class=\"chat-container\">" +
                "<div class=\"connection-status\">" +
                "<div class=\"status-dot\"></div>" +
                "<span>Connected to Desktop - Messages will appear here</span>" +
                "</div>" +
                "<div class=\"messages-area\" id=\"log\">" +
                "<div class=\"welcome-message\">" +
                "<h2>Welcome to AlphaChat! 🎉</h2>" +
                "<p>Your phone is connected to the desktop application.</p>" +
                "<p>Start typing to send messages that will appear on the desktop.</p>" +
                "<p>Customize your profile to add your name and avatar!</p>" +
                "</div>" +
                "</div>" +
                "<div class=\"inputbar\">" +
                "<input id=\"text\" placeholder=\"Type a message...\" autocomplete=\"off\" />" +
                "<button class=\"emoji-btn\" onclick=\"toggleEmojiPicker()\" title=\"Emojis\">😀</button>" +
                "<button id=\"send\">Send</button>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "<div class=\"emoji-picker\" id=\"emojiPicker\">" +
                "😀😃😄😁😆😅😂🤣😊😇🙂🙃😉😌😍🥰😘😗😙😚😋😛😝😜🤪🤨🧐🤓😎🤩🥳😏😒😞😔😟😕🙁☹️😣😖😫😩🥺😢😭😤😠😡🤬🤯😳🥵🥶😱😨😰😥😓🤗🤔🤭🤫🤥😶😐😑😬🙄😯😦😧😮😲🥱😴🤤😪😵🤐🥴🤢🤮🤧😷🤒🤕🤑🤠😈👿👹👺🤡💩👻💀☠️👽👾🤖🎃😺😸😹😻😼😽🙀😿😾" +
                "</div>" +
                "<script>" +
                "const log = document.getElementById('log');" +
                "let userAvatar = null;" +
                "let userName = null;" +
                "let sessionId = 'session_' + Date.now();" +
                "let messageCount = 0;" +
                
                "function add(sender, text, avatar = null, name = null) {" +
                "  messageCount++;" +
                "  if (messageCount === 1) {" +
                "    log.innerHTML = '';" +
                "  }" +
                "  " +
                "  const row = document.createElement('div');" +
                "  row.className = 'msg ' + (sender === 'desktop' ? 'you' : 'me');" +
                "  " +
                "  const box = document.createElement('div');" +
                "  box.style.display = 'flex';" +
                "  box.style.flexDirection = 'column';" +
                "  " +
                "  const meta = document.createElement('div');" +
                "  meta.className = 'meta';" +
                "  " +
                "  if (avatar) {" +
                "    const avatarImg = document.createElement('img');" +
                "    avatarImg.src = '/assets/' + avatar + '.jpg';" +
                "    avatarImg.className = 'avatar';" +
                "    avatarImg.onerror = function() {" +
                "      this.style.display = 'none';" +
                "    };" +
                "    meta.appendChild(avatarImg);" +
                "  }" +
                "  " +
                "  const senderName = document.createElement('span');" +
                "  const displayName = name || (sender === 'desktop' ? 'Desktop' : (userName || 'Me'));" +
                "  senderName.textContent = displayName + ' • ' + new Date().toLocaleTimeString();" +
                "  meta.appendChild(senderName);" +
                "  " +
                "  const bubble = document.createElement('div');" +
                "  bubble.className = 'bubble';" +
                "  bubble.textContent = text;" +
                "  " +
                "  box.appendChild(meta);" +
                "  box.appendChild(bubble);" +
                "  row.appendChild(box);" +
                "  log.appendChild(row);" +
                "  " +
                "  log.scrollTop = log.scrollHeight;" +
                "}" +
                
                "const ev = new EventSource('/events');" +
                "ev.onmessage = e => {" +
                "  try {" +
                "    const m = JSON.parse(e.data);" +
                "    if (m.sender !== 'system') {" +
                "      add(m.sender, m.text, m.avatar, m.name);" +
                "    }" +
                "  } catch (_) {}" +
                "};" +
                
                "const input = document.getElementById('text');" +
                "const btn = document.getElementById('send');" +
                
                "function send() {" +
                "  const t = input.value.trim();" +
                "  if (!t) return;" +
                "  " +
                "  const formData = new FormData();" +
                "  formData.append('text', t);" +
                "  if (userAvatar) {" +
                "    formData.append('avatar', userAvatar);" +
                "  }" +
                "  if (userName) {" +
                "    formData.append('name', userName);" +
                "  }" +
                "  " +
                "  fetch('/send', {" +
                "    method: 'POST'," +
                "    body: formData" +
                "  });" +
                "  " +
                "  add('phone', t, userAvatar, userName);" +
                "  input.value = '';" +
                "}" +
                
                "function toggleEmojiPicker() {" +
                "  const picker = document.getElementById('emojiPicker');" +
                "  picker.style.display = picker.style.display === 'none' ? 'grid' : 'none';" +
                "}" +
                
                "function addEmoji(emoji) {" +
                "  input.value += emoji;" +
                "  input.focus();" +
                "  document.getElementById('emojiPicker').style.display = 'none';" +
                "}" +
                
                "document.addEventListener('DOMContentLoaded', function() {" +
                "  const emojiItems = document.querySelectorAll('.emoji-item');" +
                "  emojiItems.forEach(item => {" +
                "    item.addEventListener('click', function() {" +
                "      addEmoji(this.textContent);" +
                "    });" +
                "  });" +
                "});" +
                
                "btn.addEventListener('click', send);" +
                "input.addEventListener('keydown', e => {" +
                "  if (e.key === 'Enter') send();" +
                "});" +
                
                "window.onload = () => {" +
                "  const savedAvatar = localStorage.getItem('userAvatar');" +
                "  const savedName = localStorage.getItem('userName');" +
                "  if (savedAvatar) userAvatar = savedAvatar;" +
                "  if (savedName) userName = savedName;" +
                "};" +
                "</script>" +
                "</body></html>";
        writeText(out, 200, "OK", "text/html; charset=utf-8", html);
    }

    private static void handleSse(OutputStream rawOut, PrintWriter headerOut) {
        headerOut.print("HTTP/1.1 200 OK\r\n");
        headerOut.print("Content-Type: text/event-stream\r\n");
        headerOut.print("Cache-Control: no-cache\r\n");
        headerOut.print("Connection: keep-alive\r\n\r\n");
        headerOut.flush();

        PrintWriter eventWriter = new PrintWriter(new OutputStreamWriter(rawOut, StandardCharsets.UTF_8), true);
        sseClients.add(eventWriter);
    }

    private static void broadcastEvent(String sender, String text) {
        broadcastEvent(sender, text, null, null);
    }

    private static void broadcastEvent(String sender, String text, String avatar, String name) {
        StringBuilder json = new StringBuilder();
        json.append("{\"sender\":\"").append(escapeJson(sender)).append("\",\"text\":\"").append(escapeJson(text)).append("\"");
        if (avatar != null) {
            json.append(",\"avatar\":\"").append(escapeJson(avatar)).append("\"");
        }
        if (name != null) {
            json.append(",\"name\":\"").append(escapeJson(name)).append("\"");
        }
        json.append("}");
        
        List<PrintWriter> toRemove = new ArrayList<>();
        for (PrintWriter w : sseClients) {
            if (!sendSse(w, json.toString())) {
                toRemove.add(w);
            }
        }
        sseClients.removeAll(toRemove);
    }

    private static boolean sendSse(PrintWriter w, String data) {
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
}
