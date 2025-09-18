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

public class FinalChatServer {
    private static final int WEB_PORT = 3000;
    private static final List<PrintWriter> sseClients = new CopyOnWriteArrayList<>();
    private static final List<String> messageHistory = new CopyOnWriteArrayList<>();
    private static final Map<String, String> userProfiles = new HashMap<>();
    private static ServerSocket httpServerSocket;
    private static final String NETWORK_IP = "10.0.0.95";
    
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
                if (text == null) text = "";
                String decoded = urlDecode(text);
                if (!decoded.isEmpty()) {
                    addMessage("Phone", decoded);
                    broadcastEvent("phone", decoded);
                }
                writeNoContent(out);
            } else if ("POST".equals(method) && "/profile".equals(path)) {
                byte[] body = readBody(in, contentLength);
                String form = new String(body, StandardCharsets.UTF_8);
                String avatar = parseFormField(form, "avatar");
                String sessionId = parseFormField(form, "sessionId");
                if (avatar != null && sessionId != null) {
                    userProfiles.put(sessionId, avatar);
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
                "<title>Profile Setup - LAN Chat</title>" +
                "<style>body{font-family:sans-serif;margin:0;background:#0b1220;color:#e2e8f0;padding:20px}" +
                ".container{max-width:600px;margin:0 auto;background:#1f2937;padding:30px;border-radius:20px}" +
                "h1{color:#4f46e5;margin-bottom:20px;text-align:center}" +
                ".avatar-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(80px,1fr));gap:15px;margin:20px 0}" +
                ".avatar-option{background:#0b1220;border:2px solid #334155;border-radius:12px;padding:10px;text-align:center;cursor:pointer;transition:all 0.3s}" +
                ".avatar-option:hover{border-color:#4f46e5;background:#1f2937}" +
                ".avatar-option.selected{border-color:#06b6d4;background:#1e40af}" +
                ".avatar-img{width:60px;height:60px;border-radius:50%;object-fit:cover;margin-bottom:8px}" +
                ".avatar-name{font-size:12px;color:#94a3b8}" +
                ".btn{background:linear-gradient(90deg,#4f46e5,#06b6d4);color:white;padding:12px 24px;border:none;border-radius:10px;margin:10px;cursor:pointer;text-decoration:none;display:inline-block;width:100%;text-align:center}" +
                ".btn:disabled{opacity:0.5;cursor:not-allowed}" +
                ".current-profile{margin:20px 0;padding:15px;background:#0b1220;border-radius:10px;text-align:center}" +
                ".current-avatar{width:80px;height:80px;border-radius:50%;object-fit:cover;margin:0 auto 10px;display:block}" +
                "</style></head><body>" +
                "<div class=\"container\">" +
                "<h1>Choose Your Profile</h1>" +
                
                "<div class=\"current-profile\">" +
                "<div id=\"currentAvatar\" class=\"current-avatar\" style=\"background:#334155;display:flex;align-items:center;justify-content:center;color:#94a3b8;\">No Avatar</div>" +
                "<div id=\"currentName\">Select an avatar below</div>" +
                "</div>" +
                
                "<div class=\"avatar-grid\" id=\"avatarGrid\">" +
                generateAvatarOptions() +
                "</div>" +
                
                "<button id=\"saveProfile\" class=\"btn\" disabled onclick=\"saveProfile()\">Save Profile</button>" +
                "<a href=\"/\" class=\"btn\">Back to Chat</a>" +
                "</div>" +
                
                "<script>" +
                "let selectedAvatar = null;" +
                "let sessionId = 'session_' + Date.now();" +
                
                "function selectAvatar(avatarId, avatarName) {" +
                "  selectedAvatar = avatarId;" +
                "  document.getElementById('currentAvatar').innerHTML = '<img src=\"/assets/' + avatarId + '.jpg\" class=\"current-avatar\" />';" +
                "  document.getElementById('currentName').textContent = avatarName;" +
                "  document.getElementById('saveProfile').disabled = false;" +
                "  " +
                "  document.querySelectorAll('.avatar-option').forEach(opt => opt.classList.remove('selected'));" +
                "  event.target.closest('.avatar-option').classList.add('selected');" +
                "}" +
                
                "function saveProfile() {" +
                "  if (!selectedAvatar) return;" +
                "  " +
                "  const formData = new FormData();" +
                "  formData.append('avatar', selectedAvatar);" +
                "  formData.append('sessionId', sessionId);" +
                "  " +
                "  fetch('/profile', {" +
                "    method: 'POST'," +
                "    body: formData" +
                "  }).then(response => response.json())" +
                "    .then(data => {" +
                "      if (data.success) {" +
                "        localStorage.setItem('userAvatar', selectedAvatar);" +
                "        alert('Profile saved! Your avatar will appear in chat messages.');" +
                "        window.location.href = '/';" +
                "      } else {" +
                "        alert('Failed to save profile. Please try again.');" +
                "      }" +
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
                "<meta name=\"theme-color\" content=\"#0b1220\">" +
                "<title>LAN Chat</title>" +
                "<style>body{font-family:-apple-system,Segoe UI,Roboto,Inter,Helvetica,Arial,sans-serif;margin:0;background:#0b1220;color:#e2e8f0}" +
                ".wrap{max-width:680px;margin:0 auto;padding:16px}" +
                ".header{position:sticky;top:0;background:#0b1220e6;color:#e2e8f0;padding:16px;border-bottom:1px solid #1f2937;backdrop-filter:saturate(140%) blur(6px);display:flex;justify-content:space-between;align-items:center}" +
                ".msg{margin:10px 0;display:flex}" +
                ".bubble{max-width:76%;padding:10px 14px;border-radius:14px;box-shadow:0 2px 6px rgba(0,0,0,.25);word-wrap:break-word;white-space:pre-wrap}" +
                ".me{justify-content:flex-end}.me .bubble{background:linear-gradient(90deg,#4f46e5,#06b6d4);color:#fff;border-bottom-right-radius:6px}" +
                ".you{justify-content:flex-start}.you .bubble{background:#1f2937;color:#e2e8f0;border-bottom-left-radius:6px}" +
                ".meta{font-size:12px;color:#94a3b8;margin:0 2px 6px 2px;display:flex;align-items:center;gap:8px}" +
                ".avatar{width:20px;height:20px;border-radius:50%;object-fit:cover}" +
                ".inputbar{position:sticky;bottom:0;background:#0b1220e6;padding:12px;border-top:1px solid #1f2937;display:flex;gap:8px;backdrop-filter:saturate(140%) blur(6px)}" +
                "input{flex:1;padding:12px 14px;border-radius:12px;border:1px solid #334155;background:#0b1220;color:#e2e8f0}button{padding:12px 16px;border:0;background:linear-gradient(90deg,#4f46e5,#06b6d4);color:white;border-radius:12px;font-weight:600}" +
                ".nav-link{color:#93c5fd;text-decoration:none;font-size:14px;font-weight:600;padding:8px 12px;border-radius:8px;background:#1f2937}" +
                ".nav-link:hover{background:#334155}" +
                ".emoji-btn{background:#334155;color:#e2e8f0;border:none;padding:8px 12px;border-radius:8px;cursor:pointer;margin-left:8px}" +
                ".emoji-picker{position:fixed;bottom:80px;right:20px;background:#1f2937;border:1px solid #334155;border-radius:12px;padding:15px;display:none;grid-template-columns:repeat(6,1fr);gap:8px;max-width:200px}" +
                ".emoji-item{font-size:20px;cursor:pointer;padding:5px;border-radius:6px;text-align:center}" +
                ".emoji-item:hover{background:#334155}" +
                "</style></head><body>" +
                "<div class=\"header\">" +
                "<div class=\"wrap\" style=\"display:flex;justify-content:space-between;align-items:center;\">" +
                "<div style=\"font-weight:700;font-size:18px\">LAN Chat</div>" +
                "<div style=\"display:flex;gap:8px;\">" +
                "<a href=\"/profile\" class=\"nav-link\">Profile</a>" +
                "<a href=\"/settings\" class=\"nav-link\">Settings</a>" +
                "<a href=\"/connect\" class=\"nav-link\">Connect</a>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "<div class=\"wrap\" id=\"wrap\">" +
                "  <div id=\"log\"></div>" +
                "</div>" +
                "<div class=\"inputbar\">" +
                "  <input id=\"text\" placeholder=\"Type a message\" autocomplete=\"off\" />" +
                "  <button class=\"emoji-btn\" onclick=\"toggleEmojiPicker()\">😀</button>" +
                "  <button id=\"send\">Send</button>" +
                "</div>" +
                "<div class=\"emoji-picker\" id=\"emojiPicker\">" +
                "😀😃😄😁😆😅😂🤣😊😇🙂🙃😉😌😍🥰😘😗😙😚😋😛😝😜🤪🤨🧐🤓😎🤩🥳😏😒😞😔😟😕🙁☹️😣😖😫😩🥺😢😭😤😠😡🤬🤯😳🥵🥶😱😨😰😥😓🤗🤔🤭🤫🤥😶😐😑😬🙄😯😦😧😮😲🥱😴🤤😪😵🤐🥴🤢🤮🤧😷🤒🤕🤑🤠😈👿👹👺🤡💩👻💀☠️👽👾🤖🎃😺😸😹😻😼😽🙀😿😾" +
                "</div>" +
                "<script>\n" +
                "const log = document.getElementById('log');\n" +
                "let userAvatar = null;\n" +
                "let sessionId = 'session_' + Date.now();\n" +
                "\n" +
                "function add(sender,text,avatar=null){\n" +
                "  const row=document.createElement('div');\n" +
                "  row.className='msg '+(sender==='desktop'?'you':'me');\n" +
                "  const box=document.createElement('div');\n" +
                "  box.style.display='flex';\n" +
                "  box.style.flexDirection='column';\n" +
                "  const meta=document.createElement('div');\n" +
                "  meta.className='meta';\n" +
                "  if(avatar){\n" +
                "    const avatarImg=document.createElement('img');\n" +
                "    avatarImg.src='/assets/'+avatar+'.jpg';\n" +
                "    avatarImg.className='avatar';\n" +
                "    meta.appendChild(avatarImg);\n" +
                "  }\n" +
                "  const senderName=document.createElement('span');\n" +
                "  senderName.textContent=(sender==='desktop'?'Desktop':'Me')+' • '+new Date().toLocaleTimeString();\n" +
                "  meta.appendChild(senderName);\n" +
                "  const bubble=document.createElement('div');\n" +
                "  bubble.className='bubble';\n" +
                "  bubble.textContent=text;\n" +
                "  box.appendChild(meta);\n" +
                "  box.appendChild(bubble);\n" +
                "  row.appendChild(box);\n" +
                "  log.appendChild(row);\n" +
                "  window.scrollTo(0,document.body.scrollHeight);\n" +
                "}\n" +
                "\n" +
                "const ev=new EventSource('/events');\n" +
                "ev.onmessage=e=>{\n" +
                "  try{\n" +
                "    const m=JSON.parse(e.data);\n" +
                "    if(m.sender!=='system'){\n" +
                "      add(m.sender,m.text,m.avatar);\n" +
                "    }\n" +
                "  }catch(_){}\n" +
                "};\n" +
                "\n" +
                "const input=document.getElementById('text');\n" +
                "const btn=document.getElementById('send');\n" +
                "\n" +
                "function send(){\n" +
                "  const t=input.value.trim();\n" +
                "  if(!t)return;\n" +
                "  \n" +
                "  const formData = new FormData();\n" +
                "  formData.append('text', t);\n" +
                "  if(userAvatar){\n" +
                "    formData.append('avatar', userAvatar);\n" +
                "  }\n" +
                "  \n" +
                "  fetch('/send',{\n" +
                "    method:'POST',\n" +
                "    body:formData\n" +
                "  });\n" +
                "  \n" +
                "  add('phone',t,userAvatar);\n" +
                "  input.value='';\n" +
                "}\n" +
                "\n" +
                "function toggleEmojiPicker() {\n" +
                "  const picker = document.getElementById('emojiPicker');\n" +
                "  picker.style.display = picker.style.display === 'none' ? 'grid' : 'none';\n" +
                "}\n" +
                "\n" +
                "function addEmoji(emoji) {\n" +
                "  input.value += emoji;\n" +
                "  input.focus();\n" +
                "  document.getElementById('emojiPicker').style.display = 'none';\n" +
                "}\n" +
                "\n" +
                "document.addEventListener('DOMContentLoaded', function() {\n" +
                "  const emojiItems = document.querySelectorAll('.emoji-item');\n" +
                "  emojiItems.forEach(item => {\n" +
                "    item.addEventListener('click', function() {\n" +
                "      addEmoji(this.textContent);\n" +
                "    });\n" +
                "  });\n" +
                "});\n" +
                "\n" +
                "btn.addEventListener('click',send);\n" +
                "input.addEventListener('keydown',e=>{if(e.key==='Enter')send();});\n" +
                "\n" +
                "window.onload = () => {\n" +
                "  const savedAvatar = localStorage.getItem('userAvatar');\n" +
                "  if(savedAvatar){\n" +
                "    userAvatar = savedAvatar;\n" +
                "  }\n" +
                "};\n" +
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
        String json = "{\"sender\":\"" + escapeJson(sender) + "\",\"text\":\"" + escapeJson(text) + "\"}";
        List<PrintWriter> toRemove = new ArrayList<>();
        for (PrintWriter w : sseClients) {
            if (!sendSse(w, json)) {
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
