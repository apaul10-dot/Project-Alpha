#!/bin/bash

# AlphaChat Desktop Launcher Script
echo "Starting AlphaChat Desktop Application..."

# Set Java path
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"

# Change to project directory
cd "$(dirname "$0")"

# Compile if needed
if [ ! -f "AlphaChatDesktop.class" ] || [ "AlphaChatDesktop.java" -nt "AlphaChatDesktop.class" ]; then
    echo "Compiling AlphaChat Desktop..."
    javac -cp ".:jSerialComm-2.9.3.jar" AlphaChatDesktop.java
    if [ $? -ne 0 ]; then
        echo "Compilation failed!"
        exit 1
    fi
fi

if [ ! -f "WebServer.class" ] || [ "WebServer.java" -nt "WebServer.class" ]; then
    echo "Compiling Web Server..."
    javac -cp ".:jSerialComm-2.9.3.jar" WebServer.java
    if [ $? -ne 0 ]; then
        echo "Compilation failed!"
        exit 1
    fi
fi

# Get network IP
NETWORK_IP=$(ifconfig | grep "inet " | grep -v 127.0.0.1 | awk '{print $2}' | head -1)

echo "==============================================="
echo "AlphaChat Desktop - Phone to Desktop Messaging"
echo "==============================================="
echo ""
echo "Desktop Application: Starting..."
echo "Web Server will be available at: http://$NETWORK_IP:3000"
echo ""
echo "To connect from your phone:"
echo "1. Make sure your phone is on the same WiFi network"
echo "2. Open a web browser on your phone"
echo "3. Go to: http://$NETWORK_IP:3000"
echo "4. Start chatting!"
echo ""
echo "==============================================="

# Run the desktop application
java -cp ".:jSerialComm-2.9.3.jar" AlphaChatDesktop
