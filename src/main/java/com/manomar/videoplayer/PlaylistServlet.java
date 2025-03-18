package com.manomar.videoplayer;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URLEncoder;
import java.sql.*;


public class PlaylistServlet extends HttpServlet {

    public static JSONArray getAllPlaylists() {
        JSONArray playlistsArray = new JSONArray();
        String sql = "SELECT file_name, size, last_modified FROM media WHERE file_name LIKE '%.txt'";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            long currentTime = System.currentTimeMillis();

            while (rs.next()) {
                JSONObject playlistObject = new JSONObject();
                String fileName = rs.getString("file_name");

                playlistObject.put("fileName", fileName);
                playlistObject.put("fileExtension", "txt");

                double sizeInKB = rs.getDouble("size") * 1024;
                playlistObject.put("size", String.format("%.2f KB", sizeInKB));


                Timestamp timestamp = rs.getTimestamp("last_modified");
                long lastModified = (timestamp != null) ? timestamp.getTime() : System.currentTimeMillis();


                long daysAgo = (currentTime - lastModified) / (1000L * 60 * 60 * 24);
                playlistObject.put("daysAgo", Math.max(daysAgo, 1) + " day" + (daysAgo > 1 ? "s" : "") + " ago");

                playlistsArray.put(playlistObject);
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return playlistsArray;
    }

    public static void returnPlaylistVideos(HttpServletResponse response, String playlistName) {
        JSONArray videoArray = new JSONArray();
        JSONObject responseJson = new JSONObject();

        String playlistIdSQL = "SELECT id FROM media WHERE file_name = ?";
        int playlistId = -1;

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(playlistIdSQL)) {

            stmt.setString(1, playlistName);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                playlistId = rs.getInt("id");
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        String sql = "SELECT m.id, m.file_name, m.size, m.last_modified, v.subtitle_available " +
                "FROM playlist_videos pv " +
                "JOIN media m ON pv.video_id = m.id " +
                "LEFT JOIN videos v ON m.id = v.media_id " +
                "WHERE pv.playlist_id = ?";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, playlistId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JSONObject videoObject = new JSONObject();
                videoObject.put("fileName", rs.getString("file_name"));
                videoObject.put("size", String.format("%.2f MB", rs.getDouble("size")));

                Timestamp timestamp = rs.getTimestamp("last_modified");
                long lastModified = (timestamp != null) ? timestamp.getTime() : System.currentTimeMillis();
                videoObject.put("lastModified", lastModified);

                long daysAgo = (System.currentTimeMillis() - lastModified) / (1000L * 60 * 60 * 24);
                daysAgo = Math.max(daysAgo, 1);
                videoObject.put("createdAgo", daysAgo + " day" + (daysAgo > 1 ? "s" : "") + " ago");

                String fileName = rs.getString("file_name");
                String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
                videoObject.put("url", AppConfig.VIDEO_API + encodedFileName);

                boolean subtitleAvailable = rs.getObject("subtitle_available") != null && rs.getBoolean("subtitle_available");
                videoObject.put("subtitleUrl", subtitleAvailable ? AppConfig.SUBTITLE_API + encodedFileName : JSONObject.NULL);

                videoArray.put(videoObject);
            }
        } catch (SQLException | ClassNotFoundException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        try {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            responseJson.put("playlist_id", playlistId);
            responseJson.put("videos", videoArray);

            response.getWriter().write(responseJson.toString());
            response.getWriter().flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void processPlaylistFile(File playlistFile) {
        String playlistName = playlistFile.getName().trim().toLowerCase();
        double fileSizeKB = (playlistFile.length() / 1024.0);
        long lastModified = playlistFile.lastModified();

        int playlistId = getOrCreatePlaylist(playlistName, fileSizeKB, lastModified);
        System.out.println("Playlist ID for " + playlistName + ": " + playlistId);

        if (playlistId == -1) {
            System.err.println("Unable to process playlist: " + playlistName);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(playlistFile))) {
            String videoName;
            while ((videoName = reader.readLine()) != null) {
                videoName = videoName.trim();
                int videoId = getVideoIdByName(videoName);
                System.out.println("Checking video: " + videoName + " -> Video ID: " + videoId);

                if (videoId != -1) {
                    addVideoToPlaylist(playlistId, videoId);
                    System.out.println(" Added to playlist: " + videoName);
                } else {
                    System.err.println("Video not found in DB: " + videoName);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int getOrCreatePlaylist(String playlistName, double size, long lastModified) {

        String selectSQL = "SELECT id FROM media WHERE LOWER(file_name) = ?";

        String insertSQL = "INSERT INTO media (file_name, size, last_modified) VALUES (?, ?, ?)";

        String updateSQL = "UPDATE media SET size = ?, last_modified = ? WHERE id = ?";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSQL);
             PreparedStatement insertStmt = conn.prepareStatement(insertSQL, PreparedStatement.RETURN_GENERATED_KEYS);
             PreparedStatement updateStmt = conn.prepareStatement(updateSQL)) {

            selectStmt.setString(1, playlistName);
            ResultSet rs = selectStmt.executeQuery();

            if (rs.next()) {
                int id = rs.getInt("id");

                updateStmt.setDouble(1, size);
                updateStmt.setTimestamp(2, convertToTimestamp(lastModified));
                updateStmt.setInt(3, id);
                updateStmt.executeUpdate();

                return id;
            }

            insertStmt.setString(1, playlistName);
            insertStmt.setDouble(2, size);
            insertStmt.setTimestamp(3, convertToTimestamp(lastModified));
            insertStmt.executeUpdate();

            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static int getVideoIdByName(String fileName) {
        String sql = "SELECT id FROM media WHERE LOWER(file_name) = LOWER(?)";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fileName);
            System.out.println("Searching for video in DB: " + fileName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int videoId = rs.getInt("id");
                System.out.println("Found video: " + fileName + " -> ID: " + videoId);
                return videoId;
            } else {
                System.err.println("Video not found in media table: " + fileName);
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static void addVideoToPlaylist(int playlistId, int videoId) {
        String checkSQL = "SELECT 1 FROM playlist_videos WHERE playlist_id = ? AND video_id = ? LIMIT 1";
        String insertSQL = "INSERT INTO playlist_videos (playlist_id, video_id) VALUES (?, ?)";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSQL);
             PreparedStatement insertStmt = conn.prepareStatement(insertSQL)) {

            checkStmt.setInt(1, playlistId);
            checkStmt.setInt(2, videoId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                System.out.println("Video already exists in playlist: Video ID = " + videoId);
                return;
            }

            insertStmt.setInt(1, playlistId);
            insertStmt.setInt(2, videoId);
            int rowsAffected = insertStmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Successfully inserted Video ID " + videoId + " into Playlist ID " + playlistId);
            } else {
                System.err.println("Failed to insert Video ID " + videoId + " into Playlist ID " + playlistId);
            }

        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static Timestamp convertToTimestamp(long milliseconds) {
        return new Timestamp(milliseconds);
    }


    private JSONArray searchPlaylists(String searchQuery) {
        JSONArray playlistsArray = new JSONArray();
        String sql = "SELECT playlist_name, size, last_modified FROM playlists WHERE LOWER(playlist_name) LIKE ?";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + searchQuery.toLowerCase() + "%"); // Search in lowercase
            ResultSet rs = stmt.executeQuery();

            long currentTime = System.currentTimeMillis();

            while (rs.next()) {
                JSONObject playlistObject = new JSONObject();
                playlistObject.put("fileName", rs.getString("playlist_name"));
                playlistObject.put("fileExtension", "txt");

                double sizeInKB = rs.getDouble("size") * 1024;
                playlistObject.put("size", String.format("%.2f KB", sizeInKB));

                long lastModified = rs.getLong("last_modified");
                long daysAgo = (currentTime - lastModified) / (1000 * 60 * 60 * 24);
                playlistObject.put("daysAgo", Math.max(daysAgo, 1));

                playlistsArray.put(playlistObject);
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return playlistsArray;
    }


    private void enableCORS(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }




}
