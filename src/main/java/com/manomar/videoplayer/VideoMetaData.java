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

            public static void insertVideoMetadata(int userId, String fileName, double size, long lastModified, boolean subtitleAvailable, String hashtags) {
                String insertMediaSQL = "INSERT INTO media (user_id, file_name, size, last_modified) " +
                        "VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE size = VALUES(size), last_modified = VALUES(last_modified)";

                String insertHashtagSQL = "INSERT INTO hashtags (hashtag) VALUES (?) ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)";

                String insertMediaHashtagSQL = "INSERT INTO media_hashtags (media_id, hashtag_id) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE media_id = VALUES(media_id), hashtag_id = VALUES(hashtag_id)";

                String insertVideoSQL = "INSERT INTO videos (media_id, subtitle_available) " +
                        "VALUES ((SELECT id FROM media WHERE file_name = ?), ?) " +
                        "ON DUPLICATE KEY UPDATE subtitle_available = VALUES(subtitle_available)";

                try (Connection conn = DatabaseConnect.getConnection();
                     PreparedStatement mediaStmt = conn.prepareStatement(insertMediaSQL, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement hashtagStmt = conn.prepareStatement(insertHashtagSQL, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement mediaHashtagStmt = conn.prepareStatement(insertMediaHashtagSQL);
                     PreparedStatement videoStmt = conn.prepareStatement(insertVideoSQL)) {

                    mediaStmt.setInt(1, userId);
                    mediaStmt.setString(2, fileName);
                    mediaStmt.setDouble(3, size);
                    mediaStmt.setTimestamp(4, convertToTimestamp(lastModified));
                    mediaStmt.executeUpdate();

                    int mediaId = -1;
                    try (ResultSet rs = mediaStmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            mediaId = rs.getInt(1);
                        }
                    }

                    if (hashtags != null && !hashtags.isEmpty() && mediaId > 0) {
                        String[] hashtagArray = hashtags.split(",");

                        for (String hashtag : hashtagArray) {
                            hashtag = hashtag.trim();
                            if (!hashtag.isEmpty()) {
                                hashtagStmt.setString(1, hashtag);
                                hashtagStmt.executeUpdate();

                                int hashtagId = -1;
                                try (ResultSet rs = hashtagStmt.getGeneratedKeys()) {
                                    if (rs.next()) {
                                        hashtagId = rs.getInt(1);
                                    }
                                }

                                if (hashtagId > 0) {
                                    mediaHashtagStmt.setInt(1, mediaId);
                                    mediaHashtagStmt.setInt(2, hashtagId);
                                    mediaHashtagStmt.executeUpdate();
                                }
                            }
                        }
                    }
                    videoStmt.setString(1, fileName);
                    videoStmt.setBoolean(2, subtitleAvailable);
                    videoStmt.executeUpdate();

                } catch (SQLException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }

        }
        
