        package com.manomar.videoplayer;

        import org.json.JSONArray;
        import org.json.JSONObject;

        import java.io.UnsupportedEncodingException;
        import java.net.URLEncoder;
        import java.sql.*;

        import static com.manomar.videoplayer.PlaylistServlet.convertToTimestamp;
        
        
        public class VideoMetaData {

            public static JSONObject getVideoByName(String fileName) {
                String sql = "SELECT m.*, COALESCE(v.subtitle_available, 0) AS subtitle_available " +
                        "FROM media m " +
                        "LEFT JOIN videos v ON m.id = v.media_id " +
                        "WHERE m.file_name = ? AND m.file_name LIKE '%.mp4'";

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
                String sql = "SELECT m.*, COALESCE(v.subtitle_available, 0) AS subtitle_available " +
                        "FROM media m " +
                        "LEFT JOIN videos v ON m.id = v.media_id " +
                        "WHERE m.file_name LIKE '%.mp4'";

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
                int id = rs.getInt("id");
                video.put("id", id);
                video.put("fileName", fileName);
                video.put("size", String.format("%.2f MB", rs.getDouble("size")));


                Timestamp timestamp = rs.getTimestamp("last_modified");
                long lastModified = (timestamp != null) ? timestamp.getTime() : System.currentTimeMillis();
                video.put("lastModified", lastModified);


                long daysAgo = (System.currentTimeMillis() - lastModified) / (1000L * 60 * 60 * 24);
                daysAgo = Math.max(daysAgo, 1);
                video.put("createdAgo", daysAgo + " day" + (daysAgo > 1 ? "s" : "") + " ago");

                try {
                    video.put("url", AppConfig.VIDEO_API + URLEncoder.encode(fileName, "UTF-8").replace("+", "%20"));

                    boolean subtitleAvailable = rs.getBoolean("subtitle_available");
                    video.put("subtitleUrl", subtitleAvailable ? AppConfig.SUBTITLE_API + URLEncoder.encode(fileName, "UTF-8") : JSONObject.NULL);
                } catch (UnsupportedEncodingException e) {
                    video.put("url", JSONObject.NULL);
                    video.put("subtitleUrl", JSONObject.NULL);
                }

                return video;
            }

            public static boolean isVideoInDatabase(String fileName) {
                String sql = "SELECT COUNT(*) FROM media WHERE file_name = ?";
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

                String sql = "INSERT INTO media (file_name, size, last_modified) " +
                        "VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE size = VALUES(size), last_modified = VALUES(last_modified)";

                String sql1 = "INSERT INTO videos (media_id, subtitle_available) " +
                        "VALUES ((SELECT id FROM media WHERE file_name = ?), ?) " +
                        "ON DUPLICATE KEY UPDATE subtitle_available = VALUES(subtitle_available)";

                String sql2 = "INSERT INTO likes (user_id, media_id, like_status) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE like_status = VALUES(like_status)";

                try (Connection conn = DatabaseConnect.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql);
                     PreparedStatement stmt2 = conn.prepareStatement(sql2);
                     PreparedStatement stmt1 = conn.prepareStatement(sql1)) {


                    stmt.setString(1, fileName);
                    stmt.setDouble(2, size);
                    stmt.setTimestamp(3, convertToTimestamp(lastModified));

                    int rowsAffected = stmt.executeUpdate();
                    if (rowsAffected > 0) {
                        System.out.println(" Successfully inserted/updated video in DB: " + fileName);
                    } else {
                        System.err.println("No rows inserted for: " + fileName);
                    }

                    stmt1.setString(1, fileName);
                    stmt1.setBoolean(2, subtitleAvailable);

                    int subtitleRows = stmt1.executeUpdate();
                    if (subtitleRows > 0) {
                        System.out.println("Subtitle information updated for: " + fileName);
                    } else {
                        System.err.println("No subtitle update for: " + fileName);
                    }

                    stmt2.setString(1, fileName);
                    stmt2.setBoolean(2, subtitleAvailable);

                    int likeRows = stmt1.executeUpdate();
                    if (likeRows > 0) {
                        System.out.println("like status updated for: " + fileName);
                    } else {
                        System.err.println("No like status update for: " + fileName);
                    }

                } catch (SQLException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }

        }
        
