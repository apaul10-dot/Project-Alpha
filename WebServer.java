import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class WebServer {
    private static final int WEB_PORT = 3000;
    private static final List<PrintWriter> sseClients = new CopyOnWriteArrayList<>();
    private static final List<String> messageHistory = new CopyOnWriteArrayList<>();
    private static final Map<String, String> userProfiles = new HashMap<>();
    private static ServerSocket httpServerSocket;
    private static String NETWORK_IP;
    
    // Settings
    private static boolean darkMode = true;
    private static boolean soundEnabled = true;
    private static boolean notificationsEnabled = true;
    private static String fontSize = "medium";

    public static void main(String[] args) {
        System.out.println("Starting AlphaChat Web Server...");
        
        try {
            NETWORK_IP = getNetworkIP();
        } catch (Exception e) {
            NETWORK_IP = "localhost";
        }
        
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
                System.out.println("Web server running on port " + WEB_PORT);
                
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
        String html = getModernProfilePage();
        writeText(out, 200, "OK", "text/html; charset=utf-8", html);
    }

    private static void serveSettingsPage(PrintWriter out) {
        String html = getModernSettingsPage();
        writeText(out, 200, "OK", "text/html; charset=utf-8", html);
    }

    private static void serveConnectionHelper(PrintWriter out) {
        String url = "http://" + NETWORK_IP + ":" + WEB_PORT + "/";
        String html = getModernConnectionPage(url);
        writeText(out, 200, "OK", "text/html; charset=utf-8", html);
    }

    private static void serveIndex(PrintWriter out) {
        String url = "http://" + NETWORK_IP + ":" + WEB_PORT + "/";
        String html = getModernIndexPage(url);
        writeText(out, 200, "OK", "text/html; charset=utf-8", html);
    }

    private static String getModernIndexPage(String url) {
        return "<!doctype html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">" +
                "<meta name=\"theme-color\" content=\"#1a1a2e\">" +
                "<title>AlphaChat - Phone to Desktop Messaging</title>" +
                "<style>" + getModernCSS() + "</style>" +
                "</head><body>" +
                "<div class=\"app-container\">" +
                "<nav class=\"navbar\">" +
                "<div class=\"nav-brand\">" +
                "<i class=\"fas fa-mobile-alt\"></i>" +
                "<span>AlphaChat</span>" +
                "</div>" +
                "<div class=\"nav-links\">" +
                "<a href=\"/profile\" class=\"nav-link\" title=\"Profile\">" +
                "<i class=\"fas fa-user-circle\"></i>" +
                "</a>" +
                "<a href=\"/settings\" class=\"nav-link\" title=\"Settings\">" +
                "<i class=\"fas fa-cog\"></i>" +
                "</a>" +
                "<a href=\"/connect\" class=\"nav-link\" title=\"Connect\">" +
                "<i class=\"fas fa-wifi\"></i>" +
                "</a>" +
                "</div>" +
                "</nav>" +
                "<div class=\"chat-container\">" +
                "<div class=\"welcome-content\">" +
                "<div class=\"welcome-header\">" +
                "<div class=\"welcome-icon\">" +
                "<i class=\"fas fa-comments\"></i>" +
                "</div>" +
                "<h1>Welcome to AlphaChat</h1>" +
                "<p>Seamlessly connect your phone to your desktop for instant messaging</p>" +
                "</div>" +
                "<div class=\"welcome-options\">" +
                "<div class=\"option-card primary\">" +
                "<div class=\"option-icon\">" +
                "<i class=\"fas fa-qrcode\"></i>" +
                "</div>" +
                "<h3>Start Chatting</h3>" +
                "<p>Your phone is ready to connect to the desktop application</p>" +
                "<button onclick=\"startChat()\" class=\"btn btn-primary\">" +
                "<i class=\"fas fa-comments\"></i> Start Chat" +
                "</button>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "<div class=\"inputbar\">" +
                "<input id=\"text\" placeholder=\"Type a message\" autocomplete=\"off\" />" +
                "<button class=\"emoji-btn\" onclick=\"toggleEmojiPicker()\">😀</button>" +
                "<button id=\"send\">Send</button>" +
                "</div>" +
                "<div class=\"emoji-picker\" id=\"emojiPicker\">" +
                "😀😃😄😁😆😅😂🤣😊😇🙂🙃😉😌😍🥰😘😗😙😚😋😛😝😜🤪🤨🧐🤓😎🤩🥳😏😒😞😔😟😕🙁☹️😣😖😫😩🥺😢😭😤😠😡🤬🤯😳🥵🥶😱😨😰😥😓🤗🤔🤭🤫🤥😶😐😑😬🙄😯😦😧😮😲🥱😴🤤😪😵🤐🥴🤢🤮🤧😷🤒🤕🤑🤠😈👿👹👺🤡💩👻💀☠️👽👾🤖🎃😺😸😹😻😼😽🙀😿😾" +
                "</div>" +
                "<script>" + getModernJavaScript() + "</script>" +
                "</body></html>";
    }

    private static String getModernCSS() {
        return "*{margin:0;padding:0;box-sizing:border-box;font-family:\"Inter\",-apple-system,BlinkMacSystemFont,\"Segoe UI\",Roboto,sans-serif}" +
                "body{background:linear-gradient(135deg,#0f0f23 0%,#1a1a2e 100%);color:#f8fafc;line-height:1.6;min-height:100vh;overflow-x:hidden}" +
                ".app-container{min-height:100vh;display:flex;flex-direction:column}" +
                ".navbar{background:rgba(30,41,59,0.8);backdrop-filter:blur(20px);border-bottom:1px solid #334155;padding:1rem 1.5rem;display:flex;justify-content:space-between;align-items:center;position:sticky;top:0;z-index:100}" +
                ".nav-brand{display:flex;align-items:center;gap:0.5rem;font-size:1.25rem;font-weight:700;color:#f8fafc}" +
                ".nav-brand i{color:#6366f1;font-size:1.5rem}" +
                ".nav-links{display:flex;gap:0.5rem}" +
                ".nav-link{display:flex;align-items:center;justify-content:center;width:40px;height:40px;border-radius:0.75rem;background:#1e293b;color:#cbd5e1;text-decoration:none;transition:all 150ms ease-in-out;border:1px solid #334155}" +
                ".nav-link:hover{background:#6366f1;color:#f8fafc;transform:translateY(-2px);box-shadow:0 10px 15px -3px rgba(0,0,0,0.1)}" +
                ".chat-container{flex:1;display:flex;flex-direction:column;max-width:1200px;margin:0 auto;width:100%;padding:1.5rem}" +
                ".welcome-content{display:flex;flex-direction:column;align-items:center;justify-content:center;flex:1;text-align:center;padding:3rem 1.5rem}" +
                ".welcome-header{margin-bottom:3rem}" +
                ".welcome-icon{width:80px;height:80px;background:linear-gradient(135deg,#6366f1,#06b6d4);border-radius:50%;display:flex;align-items:center;justify-content:center;margin:0 auto 1.5rem;box-shadow:0 25px 50px -12px rgba(0,0,0,0.25)}" +
                ".welcome-icon i{font-size:2rem;color:white}" +
                ".welcome-header h1{font-size:2.5rem;font-weight:700;margin-bottom:1rem;background:linear-gradient(135deg,#f8fafc,#60a5fa);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}" +
                ".welcome-header p{font-size:1.125rem;color:#cbd5e1;max-width:600px;margin:0 auto}" +
                ".welcome-options{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:2rem;width:100%;max-width:800px}" +
                ".option-card{background:#1e293b;border:1px solid #334155;border-radius:1.5rem;padding:3rem;text-align:center;transition:all 250ms ease-in-out;position:relative;overflow:hidden}" +
                ".option-card::before{content:'';position:absolute;top:0;left:0;right:0;height:4px;background:linear-gradient(90deg,#6366f1,#06b6d4);transform:scaleX(0);transition:transform 250ms ease-in-out}" +
                ".option-card:hover::before{transform:scaleX(1)}" +
                ".option-card:hover{transform:translateY(-8px);box-shadow:0 25px 50px -12px rgba(0,0,0,0.25);border-color:#6366f1}" +
                ".option-card.primary{background:linear-gradient(135deg,#1e293b,rgba(99,102,241,0.1))}" +
                ".option-icon{width:60px;height:60px;background:#16213e;border-radius:50%;display:flex;align-items:center;justify-content:center;margin:0 auto 1.5rem;border:2px solid #334155}" +
                ".option-card.primary .option-icon{background:linear-gradient(135deg,#6366f1,#4f46e5);border-color:#6366f1}" +
                ".option-icon i{font-size:1.5rem;color:white}" +
                ".option-card h3{font-size:1.5rem;font-weight:600;margin-bottom:1rem;color:#f8fafc}" +
                ".option-card p{color:#cbd5e1;margin-bottom:2rem;line-height:1.6}" +
                ".btn{display:inline-flex;align-items:center;justify-content:center;gap:0.5rem;padding:1rem 2rem;border:none;border-radius:0.75rem;font-weight:600;font-size:1rem;cursor:pointer;transition:all 150ms ease-in-out;text-decoration:none;position:relative;overflow:hidden}" +
                ".btn-primary{background:linear-gradient(135deg,#6366f1,#4f46e5);color:white;box-shadow:0 4px 6px -1px rgba(0,0,0,0.1)}" +
                ".btn-primary:hover{transform:translateY(-2px);box-shadow:0 20px 25px -5px rgba(0,0,0,0.1)}" +
                ".inputbar{position:sticky;bottom:0;background:#0f0f23e6;padding:12px;border-top:1px solid #1f2937;display:flex;gap:8px;backdrop-filter:saturate(140%) blur(6px)}" +
                "input{flex:1;padding:12px 14px;border-radius:12px;border:1px solid #334155;background:#0f0f23;color:#e2e8f0}" +
                "button{padding:12px 16px;border:0;background:linear-gradient(90deg,#6366f1,#06b6d4);color:white;border-radius:12px;font-weight:600}" +
                ".emoji-btn{background:#334155;color:#e2e8f0;border:none;padding:8px 12px;border-radius:8px;cursor:pointer;margin-left:8px}" +
                ".emoji-picker{position:fixed;bottom:80px;right:20px;background:#1f2937;border:1px solid #334155;border-radius:12px;padding:15px;display:none;grid-template-columns:repeat(6,1fr);gap:8px;max-width:200px}" +
                ".emoji-item{font-size:20px;cursor:pointer;padding:5px;border-radius:6px;text-align:center}" +
                ".emoji-item:hover{background:#334155}" +
                ".msg{margin:10px 0;display:flex}" +
                ".bubble{max-width:76%;padding:10px 14px;border-radius:14px;box-shadow:0 2px 6px rgba(0,0,0,.25);word-wrap:break-word;white-space:pre-wrap}" +
                ".me{justify-content:flex-end}.me .bubble{background:linear-gradient(90deg,#6366f1,#06b6d4);color:#fff;border-bottom-right-radius:6px}" +
                ".you{justify-content:flex-start}.you .bubble{background:#1f2937;color:#e2e8f0;border-bottom-left-radius:6px}" +
                ".meta{font-size:12px;color:#94a3b8;margin:0 2px 6px 2px;display:flex;align-items:center;gap:8px}" +
                ".avatar{width:20px;height:20px;border-radius:50%;object-fit:cover}" +
                ".wrap{max-width:680px;margin:0 auto;padding:16px}" +
                ".log{min-height:400px;max-height:60vh;overflow-y:auto;padding:10px 0}" +
                "@media (max-width:768px){.chat-container{padding:1rem}.welcome-header h1{font-size:2rem}.welcome-options{grid-template-columns:1fr;gap:1.5rem}.option-card{padding:2rem}.emoji-picker{width:280px;right:-10px}}";
    }

    private static String getModernJavaScript() {
        return "const log=document.getElementById('log');" +
                "let userAvatar=null;" +
                "let sessionId='session_'+Date.now();" +
                "function add(sender,text,avatar=null){" +
                "const row=document.createElement('div');" +
                "row.className='msg '+(sender==='desktop'?'you':'me');" +
                "const box=document.createElement('div');" +
                "box.style.display='flex';" +
                "box.style.flexDirection='column';" +
                "const meta=document.createElement('div');" +
                "meta.className='meta';" +
                "if(avatar){" +
                "const avatarImg=document.createElement('img');" +
                "avatarImg.src='/assets/'+avatar+'.jpg';" +
                "avatarImg.className='avatar';" +
                "meta.appendChild(avatarImg);" +
                "}" +
                "const senderName=document.createElement('span');" +
                "senderName.textContent=(sender==='desktop'?'Desktop':'Me')+' • '+new Date().toLocaleTimeString();" +
                "meta.appendChild(senderName);" +
                "const bubble=document.createElement('div');" +
                "bubble.className='bubble';" +
                "bubble.textContent=text;" +
                "box.appendChild(meta);" +
                "box.appendChild(bubble);" +
                "row.appendChild(box);" +
                "log.appendChild(row);" +
                "window.scrollTo(0,document.body.scrollHeight);" +
                "}" +
                "const ev=new EventSource('/events');" +
                "ev.onmessage=e=>{" +
                "try{" +
                "const m=JSON.parse(e.data);" +
                "if(m.sender!=='system'){" +
                "add(m.sender,m.text,m.avatar);" +
                "}" +
                "}catch(_){}" +
                "};" +
                "const input=document.getElementById('text');" +
                "const btn=document.getElementById('send');" +
                "function send(){" +
                "const t=input.value.trim();" +
                "if(!t)return;" +
                "const formData=new FormData();" +
                "formData.append('text',t);" +
                "if(userAvatar){" +
                "formData.append('avatar',userAvatar);" +
                "}" +
                "fetch('/send',{" +
                "method:'POST'," +
                "body:formData" +
                "});" +
                "add('phone',t,userAvatar);" +
                "input.value='';" +
                "}" +
                "function startChat(){" +
                "document.querySelector('.welcome-content').style.display='none';" +
                "document.querySelector('.inputbar').style.display='flex';" +
                "document.querySelector('.log').style.display='block';" +
                "add('system','Welcome to AlphaChat! Start typing to send messages to the desktop app.', '#60a5fa');" +
                "}" +
                "function toggleEmojiPicker(){" +
                "const picker=document.getElementById('emojiPicker');" +
                "picker.style.display=picker.style.display==='none'?'grid':'none';" +
                "}" +
                "function addEmoji(emoji){" +
                "input.value+=emoji;" +
                "input.focus();" +
                "document.getElementById('emojiPicker').style.display='none';" +
                "}" +
                "document.addEventListener('DOMContentLoaded',function(){" +
                "const emojiItems=document.querySelectorAll('.emoji-item');" +
                "emojiItems.forEach(item=>{" +
                "item.addEventListener('click',function(){" +
                "addEmoji(this.textContent);" +
                "});" +
                "});" +
                "});" +
                "btn.addEventListener('click',send);" +
                "input.addEventListener('keydown',e=>{if(e.key==='Enter')send();});" +
                "window.onload=()=>{" +
                "const savedAvatar=localStorage.getItem('userAvatar');" +
                "if(savedAvatar){" +
                "userAvatar=savedAvatar;" +
                "}" +
                "};";
    }

    private static String getModernProfilePage() {
        return "<!doctype html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>Profile Setup - AlphaChat</title>" +
                "<style>" + getModernCSS() + "</style>" +
                "</head><body>" +
                "<div class=\"app-container\">" +
                "<nav class=\"navbar\">" +
                "<div class=\"nav-brand\">" +
                "<i class=\"fas fa-mobile-alt\"></i>" +
                "<span>AlphaChat</span>" +
                "</div>" +
                "</nav>" +
                "<div class=\"chat-container\">" +
                "<div class=\"welcome-content\">" +
                "<h1>Profile Setup</h1>" +
                "<p>Customize your chat experience</p>" +
                "<div class=\"option-card\">" +
                "<h3>Profile Settings</h3>" +
                "<p>Your profile is automatically synced with the desktop app</p>" +
                "<button onclick=\"window.location.href='/'\">Back to Chat</button>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "</body></html>";
    }

    private static String getModernSettingsPage() {
        return "<!doctype html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>Settings - AlphaChat</title>" +
                "<style>" + getModernCSS() + "</style>" +
                "</head><body>" +
                "<div class=\"app-container\">" +
                "<nav class=\"navbar\">" +
                "<div class=\"nav-brand\">" +
                "<i class=\"fas fa-mobile-alt\"></i>" +
                "<span>AlphaChat</span>" +
                "</div>" +
                "</nav>" +
                "<div class=\"chat-container\">" +
                "<div class=\"welcome-content\">" +
                "<h1>Settings</h1>" +
                "<p>Customize your AlphaChat experience</p>" +
                "<div class=\"option-card\">" +
                "<h3>App Settings</h3>" +
                "<p>Settings are automatically synced with the desktop app</p>" +
                "<button onclick=\"window.location.href='/'\">Back to Chat</button>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "</body></html>";
    }

    private static String getModernConnectionPage(String url) {
        return "<!doctype html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>Connect to AlphaChat</title>" +
                "<style>" + getModernCSS() + "</style>" +
                "</head><body>" +
                "<div class=\"app-container\">" +
                "<nav class=\"navbar\">" +
                "<div class=\"nav-brand\">" +
                "<i class=\"fas fa-mobile-alt\"></i>" +
                "<span>AlphaChat</span>" +
                "</div>" +
                "</nav>" +
                "<div class=\"chat-container\">" +
                "<div class=\"welcome-content\">" +
                "<h1>Connect Your Phone</h1>" +
                "<p>Your phone is already connected to the desktop app</p>" +
                "<div class=\"option-card\">" +
                "<h3>Connection Info</h3>" +
                "<p>Server: " + url + "</p>" +
                "<p>Status: Connected</p>" +
                "<button onclick=\"window.location.href='/'\">Start Chatting</button>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "</div>" +
                "</body></html>";
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
        try { return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8.name()); } catch (Exception e) { return s; }
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

    private static String getNetworkIP() throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("8.8.8.8", 80));
            return socket.getLocalAddress().getHostAddress();
        }
    }
}
