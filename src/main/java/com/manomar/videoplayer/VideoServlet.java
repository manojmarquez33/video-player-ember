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
import java.sql.*;

import static com.manomar.videoplayer.PlaylistServlet.returnPlaylistVideos;


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
        String playlistName = request.getParameter("playlist");

        if (request.getParameter("likeStatus") != null) {
            if (fileName == null || fileName.trim().isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing video parameter for likeStatus");
                return;
            }
            returnLikeStatus(response, fileName);
            return;
        }


        if (searchQuery != null && !searchQuery.isEmpty()) {
            searchVideos(response, searchQuery);
            return;
        }

        if (fileName != null && !fileName.isEmpty()) {
            if ("true".equals(metadataOnly)) {
                returnSingleVideoMetadata(response, fileName);
            } else {
                streamVideo(response, fileName);
            }
            return;
        }

        if (playlistName != null && !playlistName.isEmpty()) {
            returnPlaylistVideos(response,playlistName);
            return;
        }

        //getVideoDetails(response);

        JSONArray resultArray = fetchAllVideosAndPlaylists();
        if (resultArray != null) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(resultArray.toString());
        } else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error retrieving videos and playlists");
        }
    }

    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        try {
            BufferedReader reader = request.getReader();
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
            JSONObject jsonObject = new JSONObject(requestBody.toString());

            String fileName = jsonObject.getString("video");
            int likeStatus = jsonObject.getInt("likeStatus");

            updateLikeStatus(fileName, likeStatus);

            response.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid request");
        }
    }

    private void updateLikeStatus(String fileName, int likeStatus) {
        String sql = "UPDATE media SET like_status = ? WHERE file_name = ?";
        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, likeStatus);
            stmt.setString(2, fileName);
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

                if (fileName.endsWith(".mp4")) {
                    double videoSize = file.length() / (1024.0 * 1024.0);
                    long lastModified = file.lastModified();

                    if (!VideoMetaData.isVideoInDatabase(fileName)) {
                        boolean subtitleAvailable = new File(AppConfig.VIDEO_DIRECTORY + fileName.replace(".mp4", ".vtt")).exists();
                        System.out.println("Insert new video: " + fileName);
                        VideoMetaData.insertVideoMetadata(fileName, videoSize, lastModified, subtitleAvailable);
                    } else {
                        System.out.println("skipiing duplicate video: " + fileName);
                    }
                } else if (fileName.endsWith(".txt")) {
                    //System.out.println("file found: " + fileName);
                    PlaylistServlet.processPlaylistFile(file);
                }
            }
        }
    }

    private void returnLikeStatus(HttpServletResponse response, String fileName) throws IOException {
        if (fileName == null || fileName.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing video/playlist parameter");
            return;
        }

        String sql = "SELECT like_status FROM media WHERE file_name = ?";
        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, fileName);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int likeStatus = rs.getInt("like_status");
                JSONObject result = new JSONObject();
                result.put("likeStatus", likeStatus);
                response.setContentType("application/json");
                response.getWriter().write(result.toString());
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video/Playlist not found");
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }


    private void searchVideos(HttpServletResponse response, String searchQuery) throws IOException {
        JSONArray resultArray = new JSONArray();

        String sql = "SELECT m.id, m.file_name, m.size, m.last_modified, " +
                "COALESCE(v.subtitle_available, 0) AS subtitle_available " +
                "FROM media m " +
                "LEFT JOIN videos v ON m.id = v.media_id " +
                "WHERE LOWER(m.file_name) LIKE ? " +
                "ORDER BY m.id;";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + searchQuery.toLowerCase() + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject item = new JSONObject();
                    item.put("id", rs.getInt("id"));
                    item.put("fileName", rs.getString("file_name"));
                    item.put("size", String.format("%.2f MB", rs.getDouble("size")));


                    Timestamp lastModifiedTimestamp = rs.getTimestamp("last_modified");
                    long lastModified = (lastModifiedTimestamp != null) ? lastModifiedTimestamp.getTime() : 0;
                    item.put("lastModified", lastModified);

                    String fileName = rs.getString("file_name");
                    String type = determineFileType(fileName);
                    item.put("type", type);


                    long daysAgo = (System.currentTimeMillis() - lastModified) / (1000L * 60 * 60 * 24);
                    item.put("createdAgo", Math.max(daysAgo, 1) + " day" + (daysAgo > 1 ? "s" : "") + " ago");

                    if ("video".equals(type)) {
                        item.put("url", AppConfig.VIDEO_API + URLEncoder.encode(fileName, "UTF-8"));

                        boolean subtitleAvailable = rs.getBoolean("subtitle_available");
                        item.put("subtitleUrl", subtitleAvailable ? AppConfig.SUBTITLE_API + URLEncoder.encode(fileName, "UTF-8") : JSONObject.NULL);
                    }

                    resultArray.put(item);
                }
            }

        } catch (SQLException | ClassNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database search error");
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(resultArray.toString());
    }

    private void returnSingleVideoMetadata(HttpServletResponse response, String fileName) throws IOException {
        System.out.println("Fetching metadata for: " + fileName);

        try {
            fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error decoding filename: " + e.getMessage());
        }

        JSONObject videoObject = VideoMetaData.getVideoByName(fileName);

        if (videoObject == null) {
            System.err.println("Video metadata not found in DB for: " + fileName);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
           // response.getWriter().write("{\"error\": \"Video metadata not found\"}");
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(videoObject.toString());
    }

    public static JSONArray fetchAllVideosAndPlaylists() {
        JSONArray resultArray = new JSONArray();

        String sql = "SELECT m.id, m.file_name, m.size, m.last_modified, " +
                "COALESCE(v.subtitle_available, 0) AS subtitle_available " +
                "FROM media m " +
                "LEFT JOIN videos v ON m.id = v.media_id " +
                "ORDER BY m.id;";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("id", rs.getInt("id"));
                item.put("fileName", rs.getString("file_name"));
                item.put("size", String.format("%.2f MB", rs.getDouble("size")));
                item.put("lastModified", rs.getTimestamp("last_modified"));


                String fileName = rs.getString("file_name");
                String type = determineFileType(fileName);
                item.put("type", type);

                item.put("subtitleAvailable", rs.getInt("subtitle_available"));
                item.put("url", AppConfig.VIDEO_API + URLEncoder.encode(fileName, "UTF-8"));
                resultArray.put(item);
            }

        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            System.err.println("Database Query Error in fetchAllVideosAndPlaylists()");
            return null;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        return resultArray;
    }


    private static String determineFileType(String fileName) {
        if (fileName == null) return "unknown";
        fileName = fileName.toLowerCase();

        if (fileName.endsWith(".mp4") || fileName.endsWith(".mkv")) {
            return "video";
        } else if (fileName.endsWith(".txt")) {
            return "playlist";
        }
        return "unknown";
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
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }

}
