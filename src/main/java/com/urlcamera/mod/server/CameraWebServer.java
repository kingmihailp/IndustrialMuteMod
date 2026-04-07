package com.urlcamera.mod.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class CameraWebServer {

    private static final Logger LOGGER = LogManager.getLogger("urlcamera/webserver");
    private static final int PORT = 8765;

    static final AtomicBoolean running = new AtomicBoolean(false);
    private static Thread serverThread;

    // Thread-safe storage: UUID -> latest JPEG bytes
    static final ConcurrentHashMap<UUID, byte[]> cameraFrames = new ConcurrentHashMap<>();

    // Camera metadata: UUID -> CameraInfo
    static final ConcurrentHashMap<UUID, CameraInfo> cameraMetadata = new ConcurrentHashMap<>();

    public static class CameraInfo {
        public final UUID uuid;
        public final double x, y, z;
        public final String mode;

        public CameraInfo(UUID uuid, double x, double y, double z, String mode) {
            this.uuid = uuid;
            this.x = x;
            this.y = y;
            this.z = z;
            this.mode = mode;
        }
    }

    public static void start() {
        if (running.get()) return;
        running.set(true);

        serverThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                serverSocket.setReuseAddress(true);
                LOGGER.info("[URL Camera] Web server listening on port " + PORT);

                while (running.get()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        Thread clientThread = new Thread(() -> handleClient(clientSocket));
                        clientThread.setDaemon(true);
                        clientThread.start();
                    } catch (IOException e) {
                        if (running.get()) {
                            LOGGER.warn("[URL Camera] Accept error: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.error("[URL Camera] Server socket error: " + e.getMessage());
            }
        }, "urlcamera-webserver");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    public static void stop() {
        running.set(false);
        if (serverThread != null) {
            serverThread.interrupt();
        }
    }

    public static void updateFrame(UUID uuid, byte[] jpegBytes) {
        cameraFrames.put(uuid, jpegBytes);
    }

    public static void registerCamera(UUID uuid, double x, double y, double z, String mode) {
        cameraMetadata.put(uuid, new CameraInfo(uuid, x, y, z, mode));
    }

    public static void unregisterCamera(UUID uuid) {
        cameraMetadata.remove(uuid);
        cameraFrames.remove(uuid);
    }

    private static void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Read HTTP request line
            StringBuilder requestLine = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                char c = (char) b;
                if (c == '\n') break;
                if (c != '\r') requestLine.append(c);
            }

            // Consume remaining headers
            StringBuilder headers = new StringBuilder();
            int prev = 0;
            while ((b = in.read()) != -1) {
                char c = (char) b;
                headers.append(c);
                if (c == '\n' && prev == '\n') break;
                if (c == '\n') prev = '\n';
                else if (c != '\r') prev = 0;
            }

            String requestStr = requestLine.toString().trim();
            String[] parts = requestStr.split(" ");
            if (parts.length < 2) {
                socket.close();
                return;
            }

            String method = parts[0];
            String path = parts[1];

            if (!method.equals("GET")) {
                sendSimpleResponse(out, 405, "text/plain", "Method Not Allowed");
                socket.close();
                return;
            }

            routeRequest(path, socket, out);

        } catch (IOException e) {
            // Client disconnected or timeout
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void routeRequest(String path, Socket socket, OutputStream out) throws IOException {
        if (path.equals("/")) {
            serveIndex(out);
        } else if (path.equals("/api/cameras")) {
            serveApiCameras(out);
        } else if (path.startsWith("/camera/")) {
            String remainder = path.substring("/camera/".length());
            if (remainder.endsWith("/stream")) {
                String uuidStr = remainder.substring(0, remainder.length() - "/stream".length());
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    serveMjpegStream(uuid, socket, out);
                } catch (IllegalArgumentException e) {
                    sendSimpleResponse(out, 400, "text/plain", "Invalid UUID");
                }
            } else {
                try {
                    UUID uuid = UUID.fromString(remainder);
                    serveCameraPage(uuid, out);
                } catch (IllegalArgumentException e) {
                    sendSimpleResponse(out, 400, "text/plain", "Invalid UUID");
                }
            }
        } else {
            sendSimpleResponse(out, 404, "text/plain", "Not Found");
        }
    }

    private static void serveIndex(OutputStream out) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>URL Camera - Cameras</title>");
        html.append("<style>body{font-family:monospace;background:#111;color:#eee;padding:20px;}");
        html.append("a{color:#4af;}h1{color:#4fa;}table{border-collapse:collapse;width:100%;}");
        html.append("td,th{padding:8px 12px;border:1px solid #333;text-align:left;}");
        html.append("th{background:#222;}tr:hover{background:#1a1a1a;}</style></head><body>");
        html.append("<h1>URL Camera - Live Feeds</h1>");

        if (cameraMetadata.isEmpty()) {
            html.append("<p>No cameras registered yet. Place a camera in-game!</p>");
        } else {
            html.append("<table><tr><th>Camera UUID</th><th>Position</th><th>Mode</th><th>View</th></tr>");
            for (Map.Entry<UUID, CameraInfo> entry : cameraMetadata.entrySet()) {
                CameraInfo info = entry.getValue();
                String uuid = info.uuid.toString();
                html.append("<tr>");
                html.append("<td>").append(uuid).append("</td>");
                html.append("<td>").append(String.format("%.1f, %.1f, %.1f", info.x, info.y, info.z)).append("</td>");
                html.append("<td>").append(info.mode).append("</td>");
                html.append("<td><a href='/camera/").append(uuid).append("'>View</a></td>");
                html.append("</tr>");
            }
            html.append("</table>");
        }
        html.append("</body></html>");

        sendSimpleResponse(out, 200, "text/html; charset=utf-8", html.toString());
    }

    private static void serveCameraPage(UUID uuid, OutputStream out) throws IOException {
        String uuidStr = uuid.toString();
        CameraInfo info = cameraMetadata.get(uuid);
        String posStr = info != null
                ? String.format("%.1f, %.1f, %.1f", info.x, info.y, info.z)
                : "Unknown";
        String modeStr = info != null ? info.mode : "Unknown";

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<title>Camera ").append(uuidStr, 0, 8).append("...</title>");
        html.append("<style>body{font-family:monospace;background:#111;color:#eee;padding:20px;margin:0;}");
        html.append("img{max-width:100%;border:2px solid #4af;display:block;margin:10px 0;}");
        html.append("a{color:#4af;}.info{background:#222;padding:10px;margin:10px 0;border-radius:4px;}");
        html.append("h1{color:#4fa;}</style></head><body>");
        html.append("<h1>Camera Feed</h1>");
        html.append("<div class='info'>");
        html.append("<b>UUID:</b> ").append(uuidStr).append("<br>");
        html.append("<b>Position:</b> ").append(posStr).append("<br>");
        html.append("<b>Mode:</b> ").append(modeStr).append("<br>");
        html.append("</div>");
        html.append("<img src='/camera/").append(uuidStr).append("/stream' alt='Camera Stream'>");
        html.append("<p><a href='/'>Back to all cameras</a></p>");
        html.append("</body></html>");

        sendSimpleResponse(out, 200, "text/html; charset=utf-8", html.toString());
    }

    private static void serveApiCameras(OutputStream out) throws IOException {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<UUID, CameraInfo> entry : cameraMetadata.entrySet()) {
            CameraInfo info = entry.getValue();
            if (!first) json.append(",");
            first = false;
            json.append("{");
            json.append("\"uuid\":\"").append(info.uuid).append("\",");
            json.append("\"x\":").append(info.x).append(",");
            json.append("\"y\":").append(info.y).append(",");
            json.append("\"z\":").append(info.z).append(",");
            json.append("\"mode\":\"").append(info.mode).append("\"");
            json.append("}");
        }
        json.append("]");

        sendSimpleResponse(out, 200, "application/json", json.toString());
    }

    private static void serveMjpegStream(UUID uuid, Socket socket, OutputStream rawOut) throws IOException {
        socket.setSoTimeout(0); // No timeout for streaming

        PrintWriter headerWriter = new PrintWriter(new OutputStreamWriter(rawOut), false);
        headerWriter.print("HTTP/1.1 200 OK\r\n");
        headerWriter.print("Content-Type: multipart/x-mixed-replace; boundary=mjpegstream\r\n");
        headerWriter.print("Cache-Control: no-cache\r\n");
        headerWriter.print("Access-Control-Allow-Origin: *\r\n");
        headerWriter.print("\r\n");
        headerWriter.flush();

        byte[] lastSentFrame = null;
        while (running.get() && !socket.isClosed()) {
            byte[] frame = cameraFrames.get(uuid);
            if (frame != null && frame != lastSentFrame) {
                try {
                    rawOut.write("--mjpegstream\r\n".getBytes());
                    rawOut.write("Content-Type: image/jpeg\r\n".getBytes());
                    rawOut.write(("Content-Length: " + frame.length + "\r\n").getBytes());
                    rawOut.write("\r\n".getBytes());
                    rawOut.write(frame);
                    rawOut.write("\r\n".getBytes());
                    rawOut.flush();
                    lastSentFrame = frame;
                } catch (IOException e) {
                    break; // Client disconnected
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static void sendSimpleResponse(OutputStream out, int statusCode, String contentType, String body) throws IOException {
        byte[] bodyBytes = body.getBytes("UTF-8");
        String statusText = switch (statusCode) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Internal Server Error";
        };

        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out), false);
        writer.print("HTTP/1.1 " + statusCode + " " + statusText + "\r\n");
        writer.print("Content-Type: " + contentType + "\r\n");
        writer.print("Content-Length: " + bodyBytes.length + "\r\n");
        writer.print("Access-Control-Allow-Origin: *\r\n");
        writer.print("Connection: close\r\n");
        writer.print("\r\n");
        writer.flush();

        out.write(bodyBytes);
        out.flush();
    }
}
