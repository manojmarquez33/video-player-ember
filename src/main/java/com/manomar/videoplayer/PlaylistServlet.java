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

    public static void processPlaylistFile(int userId, File playlistFile, String hashtags) {
        System.out.println("Processing file: " + playlistFile.getAbsolutePath());
        if (!playlistFile.exists()) {
            System.err.println("Playlist file not found: " + playlistFile.getAbsolutePath());
            return;
        }
        if (!playlistFile.canRead()) {
            System.err.println("Cannot read playlist file: " + playlistFile.getAbsolutePath());
            return;
        }

        String playlistName = playlistFile.getName().trim().toLowerCase();
        System.out.println("Processing Playlist: " + playlistName);

        double fileSizeKB = playlistFile.length() / 1024.0;
        long lastModified = playlistFile.lastModified();

        int playlistId = getOrCreatePlaylist(userId, playlistName, fileSizeKB, lastModified, hashtags);
        System.out.println("Playlist ID for " + playlistName + ": " + playlistId);

        if (playlistId == -1) {
            System.err.println("Unable to create or find playlist: " + playlistName);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(playlistFile))) {
            String videoName;
            while ((videoName = reader.readLine()) != null) {
                videoName = videoName.trim();
                System.out.println("     Checking video: " + videoName);

                int videoId = getVideoIdByName(videoName);
                if (videoId != -1) {
                    addVideoToPlaylist(playlistId, videoId);
                    System.out.println("Added to playlist: " + videoName);
                } else {
                    System.err.println("Video not found in DB: " + videoName);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int getOrCreatePlaylist(int userId, String playlistName, double size, long lastModified, String hashtags) {
        String selectSQL = "SELECT id FROM media WHERE LOWER(file_name) = ?";
        String insertSQL = "INSERT INTO media (user_id, file_name, size, last_modified) VALUES (?, ?, ?, ?)";
        String updateSQL = "UPDATE media SET size = ?, last_modified = ? WHERE id = ?";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSQL);
             PreparedStatement insertStmt = conn.prepareStatement(insertSQL, PreparedStatement.RETURN_GENERATED_KEYS);
             PreparedStatement updateStmt = conn.prepareStatement(updateSQL)) {

            selectStmt.setString(1, playlistName);
            ResultSet rs = selectStmt.executeQuery();

            int mediaId;
            if (rs.next()) {
                mediaId = rs.getInt("id");

                updateStmt.setDouble(1, size);
                updateStmt.setTimestamp(2, convertToTimestamp(lastModified));
                updateStmt.setInt(3, mediaId);
                updateStmt.executeUpdate();
            } else {
                insertStmt.setInt(1, userId);
                insertStmt.setString(2, playlistName);
                insertStmt.setDouble(3, size);
                insertStmt.setTimestamp(4, convertToTimestamp(lastModified));
                insertStmt.executeUpdate();

                ResultSet generatedKeys = insertStmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    mediaId = generatedKeys.getInt(1);
                } else {
                    return -1;
                }
            }

            if (hashtags != null && !hashtags.isEmpty()) {
                linkHashtagsToMedia(conn, mediaId, hashtags);
            }

            return mediaId;
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static void linkHashtagsToMedia(Connection conn, int mediaId, String hashtags) throws SQLException {
        String insertHashtagSQL = "INSERT IGNORE INTO hashtags (hashtag) VALUES (?)";
        String selectHashtagSQL = "SELECT id FROM hashtags WHERE hashtag = ?";
        String insertMediaHashtagSQL = "INSERT IGNORE INTO media_hashtags (media_id, hashtag_id) VALUES (?, ?)";

        String[] hashtagArray = hashtags.split(",");
        for (String hashtag : hashtagArray) {
            hashtag = hashtag.trim().toLowerCase();
            if (hashtag.isEmpty()) continue;

            int hashtagId = -1;

            try (PreparedStatement selectStmt = conn.prepareStatement(selectHashtagSQL)) {
                selectStmt.setString(1, hashtag);
                ResultSet rs = selectStmt.executeQuery();
                if (rs.next()) {
                    hashtagId = rs.getInt("id");
                }
            }

            if (hashtagId == -1) {
                try (PreparedStatement insertStmt = conn.prepareStatement(insertHashtagSQL, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    insertStmt.setString(1, hashtag);
                    insertStmt.executeUpdate();
                    ResultSet generatedKeys = insertStmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        hashtagId = generatedKeys.getInt(1);
                    }
                }
            }
            if (hashtagId != -1) {
                try (PreparedStatement insertMediaHashtagStmt = conn.prepareStatement(insertMediaHashtagSQL)) {
                    insertMediaHashtagStmt.setInt(1, mediaId);
                    insertMediaHashtagStmt.setInt(2, hashtagId);
                    insertMediaHashtagStmt.executeUpdate();
                }
            }
        }
    }

    private static int getVideoIdByName(String fileName) {
        String sql = "SELECT id FROM media WHERE LOWER(file_name) = LOWER(?)";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fileName);
            System.out.println("Searching for video: " + fileName);

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
                System.out.println("Successfully added Video ID " + videoId + " to Playlist ID " + playlistId);
            } else {
                System.err.println("Failed to insert Video ID " + videoId);
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

            stmt.setString(1, "%" + searchQuery.toLowerCase() + "%");
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
