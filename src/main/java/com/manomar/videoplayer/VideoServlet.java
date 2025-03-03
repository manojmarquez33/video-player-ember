package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/VideoServlet")
public class VideoServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        checkFile();
        enableCORS(response);

        String fileName = request.getParameter("video");
        String playlistFile = request.getParameter("playlist");
        String metadataOnly = request.getParameter("metadata");
        String searchQuery = request.getParameter("search");

        if (searchQuery != null && !searchQuery.isEmpty()) {
            searchVideos(response, searchQuery);
            return;
        }
        if (playlistFile != null && !playlistFile.isEmpty()) {
            returnPlaylistVideos(response, playlistFile);
            return;
        }

        if (fileName != null && !fileName.isEmpty()) {
            if ("true".equals(metadataOnly)) {
                returnSingleVideoMetadata(response, fileName);
                return;
            }
            streamVideo(response, fileName);
            return;
        }

        getVideoDetails(response);
    }


    private void returnSingleVideoMetadata(HttpServletResponse response, String fileName) throws IOException {
        JSONObject videoObject = VideoMetaData.getVideoByName(fileName);

        if (videoObject == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video metadata not found");
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(videoObject.toString());
    }

    private void getVideoDetails(HttpServletResponse response) throws IOException {
        JSONArray videoArray = VideoMetaData.getAllVideoDetails();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(videoArray.toString());
    }

    private void streamVideo(HttpServletResponse response, String fileName) throws IOException {

        String videoPath = AppConfig.VIDEO_DIRECTORY + fileName;

//        System.out.println("Trying to serve video: " + fileName);
//        System.out.println("Resolved path: " + videoPath);

        File videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            System.out.println("File does not exist on disk: " + videoPath);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video file not found on disk");
            return;
        }

        response.setContentType("video/mp4");
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentLengthLong(videoFile.length());

        try (FileInputStream fis = new FileInputStream(videoFile);
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                os.flush();
            }
        }
    }

    private void searchVideos(HttpServletResponse response, String searchQuery) throws IOException {
        JSONArray videoArray = new JSONArray();
        String sql = "SELECT * FROM videos WHERE file_name LIKE ?";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + searchQuery + "%");
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JSONObject videoObject = new JSONObject();
                videoObject.put("fileName", rs.getString("file_name"));
                videoObject.put("fileExtension", rs.getString("file_extension"));
                videoObject.put("size", String.format("%.2f MB", rs.getDouble("size")));
                videoObject.put("daysAgo", (System.currentTimeMillis() - rs.getLong("last_modified")) / (1000 * 3600 * 24));

                videoObject.put("url", AppConfig.VIDEO_API + URLEncoder.encode(rs.getString("file_name"), "UTF-8"));

                if (rs.getBoolean("subtitle_available")) {
                    videoObject.put("subtitleUrl", AppConfig.SUBTITLE_API + URLEncoder.encode(rs.getString("file_name"), "UTF-8"));
                } else {
                    videoObject.put("subtitleUrl", JSONObject.NULL);
                }

                videoArray.put(videoObject);
            }
        } catch (SQLException | ClassNotFoundException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database search error");
            e.printStackTrace();
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        System.out.println("Sending JSON response: " + videoArray.toString());

        response.getWriter().write(videoArray.toString());


    }

    private void insertVideoMetadata(String fileName, double size, long lastModified, boolean subtitleAvailable) {
        String sql = "INSERT INTO videos (file_name, file_extension, size, last_modified, subtitle_available) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, fileName);
            stmt.setString(2, fileName.substring(fileName.lastIndexOf(".") + 1));
            stmt.setDouble(3, size);
            stmt.setLong(4, lastModified);
            stmt.setBoolean(5, subtitleAvailable);

            stmt.executeUpdate();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void checkFile() {
        File directory = new File(AppConfig.VIDEO_DIRECTORY);
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                double videoSize = file.length() / (1024.0 * 1024.0);
                long lastModified = file.lastModified();


                if (!isVideoInDatabase(fileName)) {
                    boolean subtitleAvailable = new File(AppConfig.VIDEO_DIRECTORY + fileName.replace(".mp4", ".vtt")).exists();
                    insertVideoMetadata(fileName, videoSize, lastModified, subtitleAvailable);
                }else{
                    System.out.println("file is already present");
                }

            }
        }
    }

    private boolean isVideoInDatabase(String fileName) {
        String sql = "SELECT COUNT(*) FROM videos WHERE file_name = ?";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, fileName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void returnPlaylistVideos(HttpServletResponse response, String playlistFile) throws IOException {
        File file = new File(AppConfig.VIDEO_DIRECTORY + playlistFile);
        JSONArray videoArray = new JSONArray();

        if (!file.exists() || !file.getName().endsWith(".txt")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Playlist file not found");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file));
             Connection conn = DatabaseConnect.getConnection()) {

            String line;
            while ((line = reader.readLine()) != null) {
                String videoName = line.trim();

                String sql = "SELECT * FROM videos WHERE file_name = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, videoName);
                    ResultSet rs = stmt.executeQuery();

                    if (rs.next()) {
                        JSONObject videoObject = new JSONObject();
                        videoObject.put("fileName", rs.getString("file_name"));
                        videoObject.put("fileExtension", rs.getString("file_extension"));
                        videoObject.put("size", String.format("%.2f MB", rs.getDouble("size")));
                        videoObject.put("daysAgo", (System.currentTimeMillis() - rs.getLong("last_modified")) / (1000 * 3600 * 24));
                        videoObject.put("url", AppConfig.VIDEO_API + URLEncoder.encode(videoName, "UTF-8"));


                        if (rs.getBoolean("subtitle_available")) {
                            videoObject.put("subtitleUrl", AppConfig.SUBTITLE_API + URLEncoder.encode(videoName, "UTF-8"));

                        } else {
                            videoObject.put("subtitleUrl", JSONObject.NULL);
                        }

                        videoArray.put(videoObject);
                    } else {
                        System.out.println("Warning: Video metadata not found in DB - " + videoName);
                    }
                }
            }
        } catch (IOException | SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing playlist");
            e.printStackTrace();
            return;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(videoArray.toString());
    }

    private void enableCORS(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }


}