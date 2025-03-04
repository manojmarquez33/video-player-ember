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

    @Override
    public void init() throws ServletException {
        checkFile();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        enableCORS(response);

        String fileName = request.getParameter("video");
        String metadataOnly = request.getParameter("metadata");
        String searchQuery = request.getParameter("search");

        if (searchQuery != null && !searchQuery.isEmpty()) {
            searchVideos(response, searchQuery);
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

    private void checkFile() {

        File directory = new File(AppConfig.VIDEO_DIRECTORY);
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();

                if (fileName.endsWith(".mp4")) {
                    double videoSize = file.length() / (1024.0 * 1024.0);
                    long lastModified = file.lastModified();

                    if (!VideoMetaData.isVideoInDatabase(fileName)) {
                        boolean subtitleAvailable = new File(AppConfig.VIDEO_DIRECTORY + fileName.replace(".mp4", ".vtt")).exists();
                        //System.out.println("Insert new video: " + fileName);
                        VideoMetaData.insertVideoMetadata(fileName, videoSize, lastModified, subtitleAvailable);
                    } else {
                        //System.out.println("skipiing duplicate video: " + fileName);
                    }
                } else if (fileName.endsWith(".txt")) {
                    //System.out.println("file found: " + fileName);
                    PlaylistServlet.processPlaylistFile(file);
                }
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

                videoArray.put(videoObject);
            }
        } catch (SQLException | ClassNotFoundException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database search error");
            e.printStackTrace();
            return;
        }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(videoArray.toString());
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
        File videoFile = new File(videoPath);
        if (!videoFile.exists()) {
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

    private void enableCORS(HttpServletResponse response) {

        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }
}
