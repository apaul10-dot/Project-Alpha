# AlphaChat Desktop - Phone to Desktop Messaging

A modern Java desktop application that enables seamless messaging between your phone and desktop computer.

## Features

✨ **Modern GUI** - Beautiful dark-themed desktop application built with Java Swing
📱 **Phone Integration** - Connect your phone via web browser for instant messaging
🎨 **Emoji Support** - Full emoji picker with categories
⚙️ **Customizable Settings** - Dark mode, sound effects, notifications, and font size
👤 **Profile Management** - Set display names and avatars
🌐 **Cross-Platform** - Works on Windows, macOS, and Linux
🔒 **Local Network** - All communication stays on your local WiFi network

## Quick Start

### Method 1: Using the Launcher Script (Recommended)
```bash
./run.sh
```

### Method 2: Manual Compilation and Execution
```bash
# Set Java path (macOS with Homebrew)
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"

# Compile the applications
javac -cp ".:jSerialComm-2.9.3.jar" AlphaChatDesktop.java
javac -cp ".:jSerialComm-2.9.3.jar" WebServer.java

# Run the desktop application
java -cp ".:jSerialComm-2.9.3.jar" AlphaChatDesktop
```

## How to Connect Your Phone

1. **Start the Application**: Run `./run.sh` or use the manual method above
2. **Note the IP Address**: The application will display your network IP address
3. **Connect Your Phone**:
   - Make sure your phone is on the same WiFi network as your computer
   - Open a web browser on your phone
   - Navigate to `http://YOUR_IP_ADDRESS:3000`
   - Start chatting!

## Application Components

### Desktop Application (`AlphaChatDesktop.java`)
- **Modern Swing GUI** with dark theme
- **Real-time messaging** display
- **Emoji picker** with 8 categories
- **Settings dialog** for customization
- **Profile management** for display names
- **Connection information** dialog
- **Auto-saves settings** to `alphachat.properties`

### Web Server (`WebServer.java`)
- **Embedded HTTP server** for phone connections
- **Modern mobile-optimized interface**
- **Real-time messaging** via Server-Sent Events (SSE)
- **Responsive design** that works on all phone sizes
- **Emoji support** for mobile users

## System Requirements

- **Java 17 or later**
- **Network connection** (WiFi recommended for phone connectivity)
- **Operating System**: Windows, macOS, or Linux

### Installing Java (macOS)
```bash
# Using Homebrew
brew install openjdk@17

# Add to your shell profile
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
```

## Features in Detail

### 🎨 Modern User Interface
- Dark theme optimized for long usage
- Smooth animations and transitions
- Responsive layout that adapts to window size
- Professional color scheme with accent colors

### 📱 Phone Integration
- **No app installation required** on phone
- **Web-based interface** works with any browser
- **Automatic connection** detection
- **Real-time synchronization** between devices

### 🎭 Emoji System
- **100+ emojis** organized in categories:
  - Smileys & Emotion
  - People & Body
  - Animals & Nature
  - Food & Drink
  - Activities
  - Travel & Places
  - Objects
  - Symbols
- **Quick access** emoji picker
- **Cross-platform compatibility**

### ⚙️ Settings & Customization
- **Dark Mode**: Toggle between light and dark themes
- **Sound Effects**: Enable/disable notification sounds
- **Notifications**: Control system notifications
- **Font Size**: Small, Medium, or Large text
- **Profile Settings**: Set display name and avatar

### 🔧 Technical Features
- **Multi-threaded server** for handling multiple connections
- **Server-Sent Events (SSE)** for real-time updates
- **Automatic IP detection** for easy setup
- **Error handling** and connection recovery
- **Settings persistence** across sessions

## File Structure

```
Project-Alpha/
├── AlphaChatDesktop.java    # Main desktop application
├── WebServer.java           # Web server for phone connections
├── run.sh                   # Launcher script
├── README.md               # This file
├── jSerialComm-2.9.3.jar   # Serial communication library
├── assets/                 # Avatar images
│   ├── avatar_2.jpg
│   ├── avatar_4.jpg
│   └── ...
└── alphachat.properties    # Settings file (auto-generated)
```

## Troubleshooting

### Application Won't Start
- **Check Java Version**: Ensure Java 17 or later is installed
- **Check Permissions**: Make sure `run.sh` is executable (`chmod +x run.sh`)
- **Check Network**: Ensure your computer is connected to WiFi

### Phone Can't Connect
- **Same Network**: Ensure phone and computer are on the same WiFi network
- **Firewall**: Check if firewall is blocking port 3000
- **IP Address**: Verify the IP address shown in the application
- **Browser**: Try a different browser on your phone

### Messages Not Appearing
- **Connection Status**: Check the connection indicator in the app
- **Refresh**: Try refreshing the web page on your phone
- **Restart**: Close and restart the desktop application

## Development

### Building from Source
```bash
# Clone or download the project
cd Project-Alpha

# Compile
javac -cp ".:jSerialComm-2.9.3.jar" *.java

# Run
java -cp ".:jSerialComm-2.9.3.jar" AlphaChatDesktop
```

### Architecture
- **Desktop App**: Java Swing GUI with embedded HTTP server
- **Web Interface**: Embedded HTML/CSS/JavaScript served by Java
- **Communication**: HTTP POST for messages, SSE for real-time updates
- **Data Storage**: In-memory with optional file persistence

## License

This project is open source and available under the MIT License.

## Support

For issues, questions, or feature requests, please check the troubleshooting section above or create an issue in the project repository.

---

**AlphaChat Desktop v1.0** - Bringing your phone and desktop together! 📱💻