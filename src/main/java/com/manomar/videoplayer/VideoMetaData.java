package com.manomar.videoplayer;

import com.manomar.videoplayer.DatabaseConnect;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.net.URLEncoder;
import java.sql.*;
import java.io.UnsupportedEncodingException;


public class VideoMetaData {

    public static JSONObject getVideoByName(String fileName) {
        String sql = "SELECT * FROM videos WHERE file_name = ?";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fileName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return getSingleVideo(rs);
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static JSONArray getAllVideoDetails() {
        JSONArray videoArray = new JSONArray();
        String sql = "SELECT * FROM videos";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                videoArray.put(getSingleVideo(rs));
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return videoArray;
    }

    private static JSONObject getSingleVideo(ResultSet rs) throws SQLException {
        JSONObject video = new JSONObject();
        String fileName = rs.getString("file_name");

        video.put("fileName", fileName);
        video.put("fileExtension", rs.getString("file_extension"));
        video.put("size", String.format("%.2f MB", rs.getDouble("size")));
        video.put("daysAgo", (System.currentTimeMillis() - rs.getLong("last_modified")) / (1000 * 3600 * 24));

        try {
            video.put("url", AppConfig.VIDEO_API + URLEncoder.encode(fileName, "UTF-8"));

            if (rs.getBoolean("subtitle_available")) {
                video.put("subtitleUrl", AppConfig.SUBTITLE_API + URLEncoder.encode(fileName, "UTF-8"));
            } else {
                video.put("subtitleUrl", JSONObject.NULL);
            }
        } catch (java.io.UnsupportedEncodingException e) {
            video.put("url", JSONObject.NULL);
            video.put("subtitleUrl", JSONObject.NULL);
        }

        return video;
    }

    public static boolean isVideoInDatabase(String fileName) {
        String sql = "SELECT COUNT(*) FROM videos WHERE file_name = ?";
        boolean exists = false;

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, fileName);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                exists = rs.getInt(1) > 0;
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println("Video exists in DB (" + fileName + "): " + exists);
        return exists;
    }

    public static void insertVideoMetadata(String fileName, double size, long lastModified, boolean subtitleAvailable) {

//        String sql = "INSERT INTO videos (file_name, file_extension, size, last_modified, subtitle_available) " +
//                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE size = VALUES(size), last_modified = VALUES(last_modified)";

        String sql = "INSERT INTO videos VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE size = VALUES(size), last_modified = VALUES(last_modified)";

        System.out.println("Executing SQL: " + sql);
        System.out.println("Inserting Video: " + fileName + " | Size: " + size + "MB | Last Modified: " + lastModified);

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, fileName);
            stmt.setString(2, fileName.substring(fileName.lastIndexOf(".") + 1));
            stmt.setDouble(3, size);
            stmt.setLong(4, lastModified);
            stmt.setBoolean(5, subtitleAvailable);

        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

}

