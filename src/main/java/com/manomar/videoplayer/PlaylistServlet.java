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

@WebServlet("/PlaylistServlet")
public class PlaylistServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);

        String playlistName = request.getParameter("playlist");

        String searchQuery = request.getParameter("search");

        JSONArray resultArray;

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            resultArray = searchPlaylists(searchQuery);
        }else if (playlistName == null || playlistName.trim().isEmpty()) {
            resultArray = getAllPlaylists();
        } else {
            playlistName = playlistName.trim().toLowerCase();
            resultArray = returnPlaylistVideos(playlistName);
        }

        if (resultArray == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error retrieving playlists");
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(resultArray.toString());
    }


    private JSONArray getAllPlaylists() {
        JSONArray playlistsArray = new JSONArray();
        String sql = "SELECT playlist_name, size, last_modified FROM playlists";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            long currentTime = System.currentTimeMillis();

            while (rs.next()) {
                JSONObject playlistObject = new JSONObject();
                playlistObject.put("fileName", rs.getString("playlist_name"));
                playlistObject.put("fileExtension", "txt");

                double sizeInKB = rs.getDouble("size") * 1024;
                playlistObject.put("size", String.format("%.2f KB", sizeInKB));

                long lastModified = rs.getLong("last_modified");
                long daysAgo = (currentTime - lastModified) / (1000 * 60 * 60 * 24);

                // Ensure at least "1 day ago" is shown if the value is 0
                playlistObject.put("daysAgo", Math.max(daysAgo, 1));

                playlistsArray.put(playlistObject);
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return playlistsArray;
    }

    private JSONArray returnPlaylistVideos(String playlistName) {
        JSONArray videoArray = new JSONArray();
        String sql = "SELECT v.* FROM videos v " +
                "INNER JOIN playlist_videos pv ON v.id = pv.video_id " +
                "INNER JOIN playlists p ON pv.playlist_id = p.id " +
                "WHERE LOWER(p.playlist_name) = ?";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, playlistName.toLowerCase());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JSONObject videoObject = new JSONObject();
                videoObject.put("fileName", rs.getString("file_name"));
                videoObject.put("fileExtension", rs.getString("file_extension"));
                videoObject.put("size", String.format("%.2f MB", rs.getDouble("size")));
                videoObject.put("url", AppConfig.VIDEO_API + rs.getString("file_name"));

                long lastModified = rs.getLong("last_modified");
                long daysAgo = (System.currentTimeMillis() - lastModified) / (1000 * 60 * 60 * 24);
                videoObject.put("daysAgo", Math.max(daysAgo, 1));

                videoArray.put(videoObject);
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        return videoArray;
    }


    public static void processPlaylistFile(File playlistFile) {
        
        String playlistName = playlistFile.getName().replace(".txt", "").trim().toLowerCase();
        double fileSizeKB = (playlistFile.length() / 1024.0); 
        long lastModified = playlistFile.lastModified();
        
        
        int playlistId = getCreatePlaylist(playlistName, fileSizeKB, lastModified);
        if (playlistId == -1) {
            System.err.println("unble to process playlist " + playlistName);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(playlistFile))) {
            String videoName;
            while ((videoName = reader.readLine()) != null) {
                videoName = videoName.trim();
                int videoId = getVideoIdByName(videoName);
                if (videoId != -1) {
                    addVideoToPlaylist(playlistId, videoId);
                } else {
                    System.err.println("not found in DB: " + videoName);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int getCreatePlaylist(String playlistName, double size, long lastModified) {
       
        String selectSQL = "SELECT id FROM playlists WHERE LOWER(playlist_name) = ?";
        
        String insertSQL = "INSERT INTO playlists (playlist_name, size, last_modified) VALUES (?, ?, ?)";
        
        String updateSQL = "UPDATE playlists SET size = ?, last_modified = ? WHERE id = ?";

        try (Connection conn = DatabaseConnect.getConnection();
             
             PreparedStatement selectStmt = conn.prepareStatement(selectSQL);
             PreparedStatement insertStmt = conn.prepareStatement(insertSQL, PreparedStatement.RETURN_GENERATED_KEYS);
             PreparedStatement updateStmt = conn.prepareStatement(updateSQL)) {

            selectStmt.setString(1, playlistName);
            ResultSet rs = selectStmt.executeQuery();

            if (rs.next()) {
                int id = rs.getInt("id");
                
                updateStmt.setDouble(1, size);
                updateStmt.setLong(2, lastModified);
                updateStmt.setInt(3, id);
                updateStmt.executeUpdate();

                return id;
            }

           // System.out.println("creating new list: " + playlistName);
            
            insertStmt.setString(1, playlistName);
            insertStmt.setDouble(2, size);
            insertStmt.setLong(3, lastModified);
            insertStmt.executeUpdate();

            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                int newId = generatedKeys.getInt(1);
                return newId;
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static int getVideoIdByName(String fileName) {
        String sql = "SELECT id FROM videos WHERE file_name = ?";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fileName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
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
                //System.out.println("duplicate found" + playlistId + " -> Video " + videoId);
                return;
            }
            
            insertStmt.setInt(1, playlistId);
            insertStmt.setInt(2, videoId);
            insertStmt.executeUpdate();
            
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
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
