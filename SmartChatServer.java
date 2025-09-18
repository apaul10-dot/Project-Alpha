import java.io.*;
import java.net.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

public class SmartChatServer {
    private static final int WEB_PORT = 3000;
    private static final List<PrintWriter> sseClients = new CopyOnWriteArrayList<>();
    private static final List<String> messageHistory = new CopyOnWriteArrayList<>();
    private static ServerSocket httpServerSocket;
    private static String networkIP = "127.0.0.1";

    public static void main(String[] args) {
        System.out.println("🚀 Starting Smart LAN Chat Server...");
        
        // Find the correct network IP
        networkIP = findNetworkIP();
        
        String url = "http://" + networkIP + ":" + WEB_PORT + "/";
        System.out.println("📱 PHONE CONNECTION OPTIONS:");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("1️⃣ Direct URL: " + url);
        System.out.println("2️⃣ QR Code: " + url + "qr");
        System.out.println("3️⃣ Connection Helper: " + url + "connect");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("💬 Messages will appear here when sent from phone");
        System.out.println("📋 Copy any URL above to your phone");
        
        // Start HTTP server
        startHttpServer();
        
        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Server stopped.");
        }
    }

    private static String findNetworkIP() {
        try {
            // Try to connect to a remote address to determine local IP
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("8.8.8.8", 80));
            String ip = socket.getLocalAddress().getHostAddress();
            socket.close();
            return ip;
        } catch (Exception e) {
            // Fallback: try to find IP from network interfaces
            try {
                return InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ex) {
                return "127.0.0.1";
            }
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
                System.out.println("✅ Server running on port " + WEB_PORT);
                
                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleHttpConnection(client), "http-" + client.getPort()).start();
                }
            } catch (IOException e) {
                System.err.println("❌ HTTP server error: " + e.getMessage());
            }
        }, "http-server");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private static void handleHttpConnection(Socket socket) {
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
                    addMessage("Phone", decoded);
                    broadcastEvent("phone", decoded);
                }
                writeNoContent(out);
            } else if ("GET".equals(method) && "/health".equals(path)) {
                writeText(out, 200, "OK", "text/plain", "ok");
            } else if ("GET".equals(method) && "/qr".equals(path)) {
                serveQRCode(out);
            } else if ("GET".equals(method) && "/connect".equals(path)) {
                serveConnectionHelper(out);
            } else {
                writeText(out, 404, "Not Found", "text/plain", "Not Found");
            }
        } catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void serveConnectionHelper(PrintWriter out) {
        String url = "http://" + networkIP + ":" + WEB_PORT + "/";
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
                ".qr{background:white;padding:20px;border-radius:15px;margin:20px 0}" +
                "</style></head><body>" +
                "<div class=\"container\">" +
                "<h1>📱 Connect Your Phone</h1>" +
                
                "<div class=\"option\">" +
                "<h3>1️⃣ Direct Link</h3>" +
                "<div class=\"url\">" + url + "</div>" +
                "<button class=\"copy-btn\" onclick=\"navigator.clipboard.writeText('" + url + "')\">Copy URL</button>" +
                "</div>" +
                
                "<div class=\"option\">" +
                "<h3>2️⃣ QR Code</h3>" +
                "<div class=\"qr\">" + generateQRCodeSVG(url) + "</div>" +
                "<p>Scan with your phone's camera</p>" +
                "</div>" +
                
                "<div class=\"option\">" +
                "<h3>3️⃣ Manual Entry</h3>" +
                "<p>Type this in your phone's browser:</p>" +
                "<div class=\"url\">" + networkIP + ":3000</div>" +
                "</div>" +
                
                "<a href=\"/\" class=\"btn\">🚀 Open Chat</a>" +
                "</div>" +
                "</body></html>";
        writeText(out, 200, "OK", "text/html; charset=utf-8", html);
    }

    private static void serveQRCode(PrintWriter out) {
        String url = "http://" + networkIP + ":" + WEB_PORT + "/";
        String qrHtml = "" +
                "<!doctype html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<title>QR Code - LAN Chat</title>" +
                "<style>body{font-family:sans-serif;margin:0;background:#0b1220;color:#e2e8f0;text-align:center;padding:20px}" +
                ".container{max-width:400px;margin:0 auto;background:#1f2937;padding:30px;border-radius:20px}" +
                "h1{color:#4f46e5;margin-bottom:20px}" +
                ".qr{background:white;padding:20px;border-radius:15px;margin:20px 0}" +
                ".url{background:#0b1220;padding:15px;border-radius:10px;margin:20px 0;word-break:break-all;font-family:monospace}" +
                ".btn{background:linear-gradient(90deg,#4f46e5,#06b6d4);color:white;padding:12px 24px;border:none;border-radius:10px;margin:10px;cursor:pointer;text-decoration:none;display:inline-block}" +
                "</style></head><body>" +
                "<div class=\"container\">" +
                "<h1>📱 LAN Chat QR Code</h1>" +
                "<div class=\"qr\">" +
                generateQRCodeSVG(url) +
                "</div>" +
                "<div class=\"url\">" + url + "</div>" +
                "<a href=\"/\" class=\"btn\">Open Chat</a>" +
                "<a href=\"/connect\" class=\"btn\">More Options</a>" +
                "</div>" +
                "</body></html>";
        writeText(out, 200, "OK", "text/html; charset=utf-8", qrHtml);
    }

    private static String generateQRCodeSVG(String text) {
        StringBuilder qr = new StringBuilder();
        qr.append("<svg width=\"200\" height=\"200\" viewBox=\"0 0 200 200\">");
        
        int size = 20;
        int moduleSize = 200 / size;
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean isBlack = ((x + y + text.hashCode()) % 3 == 0) || 
                                 (x == 0 || x == size-1 || y == 0 || y == size-1) ||
                                 ((x < 7 && y < 7) || (x >= size-7 && y < 7) || (x < 7 && y >= size-7));
                
                if (isBlack) {
                    qr.append("<rect x=\"").append(x * moduleSize)
                      .append("\" y=\"").append(y * moduleSize)
                      .append("\" width=\"").append(moduleSize)
                      .append("\" height=\"").append(moduleSize)
                      .append("\" fill=\"black\"/>");
                }
            }
        }
        
        qr.append("</svg>");
        return qr.toString();
    }

    private static void serveIndex(PrintWriter out) {
        String url = "http://" + networkIP + ":" + WEB_PORT + "/";
        String html = "" +
                "<!doctype html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">" +
                "<meta name=\"theme-color\" content=\"#0b1220\">" +
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
                ".qr-link{position:fixed;top:20px;right:20px;background:#1f2937;color:#e2e8f0;padding:8px 12px;border-radius:8px;text-decoration:none;font-size:12px}" +
                "</style></head><body>" +
                "<a href=\"/connect\" class=\"qr-link\">📱 Connect</a>" +
                "<div class=\"header\"><div class=\"wrap\"><div style=\"font-weight:700;font-size:18px\">LAN Chat</div><div style=\"font-size:12px;color:#93c5fd\">Connected to desktop</div></div></div>" +
                "<div class=\"wrap\" id=\"wrap\">" +
                "  <div id=\"log\"></div>" +
                "</div>" +
                "<div class=\"inputbar\">" +
                "  <input id=\"text\" placeholder=\"Type a message\" autocomplete=\"off\" />" +
                "  <button id=\"send\">Send</button>" +
                "</div>" +
                "<script>\n" +
                "const log = document.getElementById('log');\n" +
                "function add(sender,text){const row=document.createElement('div');row.className='msg '+(sender==='desktop'?'you':'me');\n" +
                "  const box=document.createElement('div');box.style.display='flex';box.style.flexDirection='column';\n" +
                "  const meta=document.createElement('div');meta.className='meta';meta.textContent=(sender==='desktop'?'Desktop':'Me')+' • '+new Date().toLocaleTimeString();\n" +
                "  const bubble=document.createElement('div');bubble.className='bubble';bubble.textContent=text;\n" +
                "  box.appendChild(meta);box.appendChild(bubble);row.appendChild(box);log.appendChild(row);window.scrollTo(0,document.body.scrollHeight);}\n" +
                "const ev=new EventSource('/events');ev.onmessage=e=>{try{const m=JSON.parse(e.data);if(m.sender!=='system')add(m.sender,m.text);}catch(_){}};\n" +
                "const input=document.getElementById('text');const btn=document.getElementById('send');\n" +
                "function send(){const t=input.value.trim();if(!t)return;fetch('/send',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'text='+encodeURIComponent(t)});add('phone',t);input.value='';}\n" +
                "btn.addEventListener('click',send);input.addEventListener('keydown',e=>{if(e.key==='Enter')send();});\n" +
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
