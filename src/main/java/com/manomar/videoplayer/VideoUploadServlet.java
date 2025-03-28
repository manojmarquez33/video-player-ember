package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONObject;

import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.Date;

@WebServlet("/VideoUploadServlet")
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 10,
        maxFileSize = 1024 * 1024 * 500,
        maxRequestSize = 1024 * 1024 * 600)
public class VideoUploadServlet extends HttpServlet {

    private static final String VIDEO_DIRECTORY = "C:/Users/inc-5388/Desktop/ZoTube/videos/";

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");

        try {
            Part myFile = request.getPart("video");
            String hashtags = request.getParameter("hashtags");
            String scheduledTime = request.getParameter("scheduledTime");

            if (myFile == null || myFile.getSize() == 0) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No video file uploaded.");
                return;
            }

            String fileName = myFile.getSubmittedFileName();
            double fileSize = myFile.getSize() / (1024.0 * 1024.0);
            long lastModified = new Date().getTime();

            String username = getUsernameFromCookies(request);
            if (username == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not logged in.");
                return;
            }

            int userId = getUserIdFromDatabase(username);
            if (userId == -1) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid user.");
                return;
            }

            boolean subtitleAvailable = new File(VIDEO_DIRECTORY + fileName.replace(".mp4", ".vtt")).exists();
            File saveFile = new File(VIDEO_DIRECTORY, fileName);

            saveVideoFile(myFile, saveFile);

            if ("now".equals(scheduledTime)) {
                insertVideoMetadata(userId, fileName, fileSize, lastModified, subtitleAvailable, hashtags);
                sendResponse(response, "Video uploaded successfully..........");
            } else {
                scheduleUpload(saveFile, scheduledTime, userId, fileName, fileSize, lastModified, subtitleAvailable, hashtags);
                sendResponse(response, "Video sche  dulling upload at: " + scheduledTime);
            }

        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing video upload.");
        }
    }

    private void saveVideoFile(Part filePart, File saveFile) throws IOException {
        try (InputStream fileContent = filePart.getInputStream();
             FileOutputStream fos = new FileOutputStream(saveFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fileContent.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    private void scheduleUpload(File saveFile, String scheduledTime, int userId, String fileName,
                                double fileSize, long lastModified, boolean subtitleAvailable, String hashtags) {
        try {
            LocalDateTime scheduleDateTime = LocalDateTime.parse(scheduledTime);
            long delay = java.time.Duration.between(LocalDateTime.now(), scheduleDateTime).toMillis();

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        if (!saveFile.exists()) {
                            System.err.println("schedule failed....no file found..!");
                            return;
                        }
                        insertVideoMetadata(userId, fileName, fileSize, lastModified, subtitleAvailable, hashtags);
                        System.out.println("Scheduled video metadata inserted at: " + scheduledTime);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, delay);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void insertVideoMetadata(int userId, String fileName, double fileSize, long lastModified,
                                     boolean subtitleAvailable, String hashtags) {
        VideoMetaData.insertVideoMetadata(userId, fileName, fileSize, lastModified, subtitleAvailable, hashtags);
    }

    private void sendResponse(HttpServletResponse response, String message) throws IOException {
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("message", message);
        response.getWriter().write(jsonResponse.toString());
    }

    public static String getUsernameFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("username".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private int getUserIdFromDatabase(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private void enableCORS(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }
}
