package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URLEncoder;
import java.sql.*;
import java.util.*;

import static com.manomar.videoplayer.PlaylistServlet.returnPlaylistVideos;
import static com.manomar.videoplayer.VideoUploadServlet.getUsernameFromCookies;
import static java.lang.System.out;


@WebServlet("/VideoServlet")
@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 10,
        maxFileSize = 1024 * 1024 * 500,
        maxRequestSize = 1024 * 1024 * 600)

public class VideoServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;


    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);

        String fileName = request.getParameter("video");
        String metadataOnly = request.getParameter("metadata");
        String searchQuery = request.getParameter("search");
        String playlistName = request.getParameter("playlist");
        String username = request.getParameter("username");
        String mediaIdParam = request.getParameter("mediaId");

        String deleteVideo = request.getParameter("delete");
        String viewHistoryParam = request.getParameter("viewHistory");

        if (username == null || username.trim().isEmpty()) {
            username = getUsernameFromCookies(request);
        }

        if ("true".equals(viewHistoryParam)) {
            try {
                int userId = getUserIdFromDatabase(username);
                System.out.println("user id: " + userId);
                if (userId == -1) {
                    System.out.println("ohh no user id not found");
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
                    return;
                }
                returnUserViewHistory(response, userId);
            } catch (SQLException | ClassNotFoundException e) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }


        if (mediaIdParam != null) {
            try {
                int mediaId = Integer.parseInt(mediaIdParam);
                int userId = getUserIdFromDatabase(username);

                if (userId == -1) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
                    return;
                }

                returnLikeStatusAndViewCount(response, userId, mediaId);
                return;

            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid media ID");
                return;
            }
        }

        if (fileName != null && !fileName.isEmpty() && "yes".equals(deleteVideo)) {
            deleteVideoFromDB(fileName);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"message\": \"Deleted Video Successfully\"}");
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

        if (searchQuery != null && !searchQuery.isEmpty()) {
            searchVideos(response, searchQuery);
            return;
        }

        if (playlistName != null && !playlistName.isEmpty()) {
            returnPlaylistVideos(response, playlistName);
            return;
        }

        if (username != null) {
            try {
                int userId = getUserIdFromDatabase(username);
                if (userId == -1) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
                    return;
                }

                JSONObject videoData = fetchRecommendedAndOtherVideos(userId);

                if (videoData == null || videoData.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write("{\"message\": \"No videos found.\"}");
                    return;
                }

                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(videoData.toString());

            } catch (SQLException | ClassNotFoundException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", "Database error: " + e.getMessage());
                response.getWriter().write(errorResponse.toString());
                e.printStackTrace();
            }
        } else {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing username parameter");
        }
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
            reader.close();

            try {
                JSONObject jsonObject = new JSONObject(requestBody.toString());

                String username = jsonObject.optString("username", "mano").trim();
                int mediaId = jsonObject.optInt("mediaId", -1);
                int likeStatus = jsonObject.optInt("likeStatus", 0);
                boolean removeRecommendation = jsonObject.optBoolean("removeRecommendation", false);
                boolean updateViewCount = jsonObject.optBoolean("updateViewCount", false);
                boolean updateViewHistory = jsonObject.optBoolean("updateViewHistory", false);

                if (username.isEmpty()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
                    return;
                }

                if (mediaId == -1) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid media ID");
                    return;
                }

                int userId = getUserIdFromDatabase(username);
                if (userId == -1) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
                    return;
                }

                try (Connection conn = DatabaseConnect.getConnection()) {
                    if (likeStatus != 0) {
                        if (checkIfLikeExists(userId, mediaId)) {
                            updateLikeStatus(userId, mediaId, likeStatus);
                        } else {
                            insertLikeStatus(userId, mediaId, likeStatus);
                        }
                    }

                    handleUserInterests(userId, mediaId, likeStatus, removeRecommendation);

                    JSONObject jsonResponse = new JSONObject();
                    jsonResponse.put("message", "Request processed successfully");
                    jsonResponse.put("likeCount", getLikeCount(conn, mediaId, 1));
                    jsonResponse.put("dislikeCount", getLikeCount(conn, mediaId, -1));
                    jsonResponse.put("userLikeStatus", likeStatus);

                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(jsonResponse.toString());
                } catch (SQLException | ClassNotFoundException e) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
                    e.printStackTrace();
                }

                if (updateViewCount) {
                    incrementViewCount(mediaId);
                }

                if (updateViewHistory) {
                    if (!hasUserWatchedBefore(userId, mediaId)) {
                        addToViewHistory(userId, mediaId);
                    }
                }
                // response.getWriter().write("{\"message\": \"Request processed successfully\"}");

            } catch (JSONException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format: " + e.getMessage());
            } catch (SQLException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

        } catch (IOException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error reading request: " + e.getMessage());
        }
    }

    private void returnLikeStatusAndViewCount(HttpServletResponse response, int userId, int mediaId) throws IOException {
        JSONObject jsonResponse = new JSONObject();

        try (Connection conn = DatabaseConnect.getConnection()) {

            String likeQuery = "SELECT " +
                    "(SELECT COUNT(*) FROM likes WHERE media_id = ? AND like_status = 1) AS likeCount, " +
                    "(SELECT COUNT(*) FROM likes WHERE media_id = ? AND like_status = -1) AS dislikeCount, " +
                    "(SELECT like_status FROM likes WHERE user_id = ? AND media_id = ?) AS userLikeStatus";

            try (PreparedStatement likeStmt = conn.prepareStatement(likeQuery)) {
                likeStmt.setInt(1, mediaId);
                likeStmt.setInt(2, mediaId);
                likeStmt.setInt(3, userId);
                likeStmt.setInt(4, mediaId);

                ResultSet rs = likeStmt.executeQuery();
                if (rs.next()) {
                    jsonResponse.put("likeCount", rs.getInt("likeCount"));
                    jsonResponse.put("dislikeCount", rs.getInt("dislikeCount"));
                    jsonResponse.put("userLikeStatus", rs.getObject("userLikeStatus") != null ? rs.getInt("userLikeStatus") : 0);
                } else {
                    jsonResponse.put("likeCount", 0);
                    jsonResponse.put("dislikeCount", 0);
                    jsonResponse.put("userLikeStatus", 0);
                }
            }

            String viewQuery = "SELECT views FROM media WHERE id = ?";
            try (PreparedStatement viewStmt = conn.prepareStatement(viewQuery)) {
                viewStmt.setInt(1, mediaId);
                ResultSet rs = viewStmt.executeQuery();
                int viewsCount = rs.next() ? rs.getInt("views") : 0;
                jsonResponse.put("viewsCount", viewsCount);
            }

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(jsonResponse.toString());
        } catch (SQLException | ClassNotFoundException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleUserInterests(int userId, int mediaId, int likeStatus, boolean removeRecommendation) {
        String fetchHashtags_SQL = """
                SELECT h.hashtag 
                FROM media_hashtags mh
                JOIN hashtags h ON mh.hashtag_id = h.id
                WHERE mh.media_id = ?""";

        String fetchInterestId_SQL = "SELECT id FROM available_interests WHERE LOWER(interest_name) = LOWER(?)";

        String insertUserInterest_SQL = """
                    INSERT INTO user_interests (user_id, interest_id)
                    SELECT ?, ? WHERE NOT EXISTS (
                        SELECT 1 FROM user_interests WHERE user_id = ? AND interest_id = ?
                    )
                """;

        String deleteUserInterest_SQL = "DELETE FROM user_interests WHERE user_id = ? AND interest_id = ?";
        String deleteUserAllInterests_SQL = "DELETE FROM user_interests WHERE user_id = ?";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement fetchHashtagsStmt = conn.prepareStatement(fetchHashtags_SQL)) {

            fetchHashtagsStmt.setInt(1, mediaId);
            ResultSet rs = fetchHashtagsStmt.executeQuery();

            List<String> hashtags = new ArrayList<>();
            while (rs.next()) {
                hashtags.add(rs.getString("hashtag").toLowerCase().trim());
            }

            if (!hashtags.isEmpty()) {
                for (String hashtag : hashtags) {
                    try (PreparedStatement fetchInterestStmt = conn.prepareStatement(fetchInterestId_SQL)) {
                        fetchInterestStmt.setString(1, hashtag);

                        ResultSet interestRs = fetchInterestStmt.executeQuery();

                        if (interestRs.next()) {
                            int interestId = interestRs.getInt("id");

                            if (likeStatus == 1) {
                                try (PreparedStatement insertStmt = conn.prepareStatement(insertUserInterest_SQL)) {
                                    insertStmt.setInt(1, userId);
                                    insertStmt.setInt(2, interestId);
                                    insertStmt.setInt(3, userId);
                                    insertStmt.setInt(4, interestId);
                                    insertStmt.executeUpdate();
                                    //  updateLikeStatus(userId,mediaId,likeStatus);
                                }
                            } else if (likeStatus == -1) {
                                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteUserInterest_SQL)) {
                                    deleteStmt.setInt(1, userId);
                                    deleteStmt.setInt(2, interestId);
                                    deleteStmt.executeUpdate();
                                }
                            }

                        }
                    }
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public JSONObject fetchRecommendedAndOtherVideos(int userId) throws SQLException, ClassNotFoundException {
        JSONObject response = new JSONObject();
        JSONArray recommendedVideos = new JSONArray();
        JSONArray otherVideos = new JSONArray();

        Connection conn = DatabaseConnect.getConnection();
        try {
            String interestQuery = """
                        SELECT i.interest_name FROM user_interests ui 
                        JOIN available_interests i ON ui.interest_id = i.id 
                        WHERE ui.user_id = ?
                    """;
            PreparedStatement interestStmt = conn.prepareStatement(interestQuery);
            interestStmt.setInt(1, userId);
            ResultSet interestRs = interestStmt.executeQuery();

            Set<String> userInterests = new HashSet<>();
            JSONArray interestArray = new JSONArray();

            while (interestRs.next()) {
                String interest = interestRs.getString("interest_name").toLowerCase();
                userInterests.add(interest);
                interestArray.put(interest);
            }
            response.put("userInterests", interestArray);
            interestStmt.close();

            String videoQuery = """
                        SELECT m.id, m.file_name, m.size, m.last_modified, 
                               GROUP_CONCAT(h.hashtag ORDER BY h.hashtag SEPARATOR ', ') AS hashtags 
                        FROM media m
                        LEFT JOIN media_hashtags mh ON m.id = mh.media_id
                        LEFT JOIN hashtags h ON mh.hashtag_id = h.id
                        GROUP BY m.id
                    """;
            PreparedStatement videoStmt = conn.prepareStatement(videoQuery);
            ResultSet videoRs = videoStmt.executeQuery();

            Map<JSONObject, Integer> recommendedVideoMap = new LinkedHashMap<>();

            while (videoRs.next()) {
                JSONObject video = new JSONObject();
                int videoId = videoRs.getInt("id");
                String fileName = videoRs.getString("file_name");
                String hashtags = videoRs.getString("hashtags");

                video.put("id", videoId);
                video.put("fileName", fileName);
                video.put("size", String.format("%.2f MB", videoRs.getDouble("size")));
                video.put("lastModified", videoRs.getTimestamp("last_modified"));
                video.put("hashtags", hashtags != null ? hashtags : "");

                String type = determineFileType(fileName);
                video.put("type", type);

                try {
                    video.put("url", AppConfig.VIDEO_API + URLEncoder.encode(fileName, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    video.put("url", AppConfig.VIDEO_API + fileName);
                }


                int matchCount = 0;
                if (hashtags != null) {
                    for (String hashtag : hashtags.toLowerCase().split(",\\s*")) {
                        if (userInterests.contains(hashtag.trim())) {
                            matchCount++;
                        }
                    }
                }

                if (matchCount > 0) {
                    recommendedVideoMap.put(video, matchCount);
                } else {
                    otherVideos.put(video);
                }
            }
            videoStmt.close();

            recommendedVideoMap.entrySet().stream()
                    .sorted((a, b) -> {
                        int matchCompare = Integer.compare(b.getValue(), a.getValue());
                        if (matchCompare == 0) {
                            Timestamp lastModifiedA = (Timestamp) a.getKey().get("lastModified");
                            Timestamp lastModifiedB = (Timestamp) b.getKey().get("lastModified");
                            return lastModifiedB.compareTo(lastModifiedA);
                        }
                        return matchCompare;
                    })
                    .forEach(entry -> recommendedVideos.put(entry.getKey()));

            response.put("recommendedVideos", recommendedVideos);
            response.put("otherVideos", otherVideos);

        } finally {
            conn.close();
        }

        return response;
    }

    private boolean checkIfLikeExists(int userId, int mediaId) {
        String sql = "SELECT 1 FROM likes WHERE user_id = ? AND media_id = ?";
        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, mediaId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void updateLikeStatus(int userId, int mediaId, int likeStatus) {
        String sql = "UPDATE likes SET like_status = ? WHERE user_id = ? AND media_id = ?";
        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, likeStatus);
            stmt.setInt(2, userId);
            stmt.setInt(3, mediaId);
            stmt.executeUpdate();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void insertLikeStatus(int userId, int mediaId, int likeStatus) {
        String sql = "INSERT INTO likes (user_id, media_id, like_status) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, mediaId);
            stmt.setInt(3, likeStatus);
            stmt.executeUpdate();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    private int getLikeCount(Connection conn, int mediaId, int status) throws SQLException {
        String sql = "SELECT COUNT(*) FROM likes WHERE media_id = ? AND like_status = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mediaId);
            stmt.setInt(2, status);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }



    private void returnLikeStatus(HttpServletResponse response, int userId, int mediaId) throws IOException {

        String sql = "SELECT " + "(SELECT COUNT(*) FROM likes WHERE media_id = ? AND like_status = 1) AS likeCount, " +
                "(SELECT COUNT(*) FROM likes WHERE media_id = ? AND like_status = -1) AS dislikeCount, " +
                "(SELECT like_status FROM likes WHERE user_id = ? AND media_id = ?) AS userLikeStatus";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, mediaId);
            stmt.setInt(2, mediaId);
            stmt.setInt(3, userId);
            stmt.setInt(4, mediaId);

            ResultSet rs = stmt.executeQuery();

            JSONObject responseObject = new JSONObject();
            if (rs.next()) {
                responseObject.put("likeCount", rs.getInt("likeCount"));
                responseObject.put("dislikeCount", rs.getInt("dislikeCount"));
                responseObject.put("userLikeStatus", rs.getObject("userLikeStatus") != null ? rs.getInt("userLikeStatus") : 0);
            } else {
                responseObject.put("likeCount", 0);
                responseObject.put("dislikeCount", 0);
                responseObject.put("userLikeStatus", 0);
            }

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(responseObject.toString());

        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error while fetching like status.");
        }
    }


    private void returnUserViewHistory(HttpServletResponse response, int userId) throws SQLException, IOException, ClassNotFoundException {

        String sql = "SELECT m.id, m.file_name, m.size, m.last_modified, m.views " +
                "FROM media m " +
                "INNER JOIN viewHistory vh ON m.id = vh.media_id " +
                "WHERE vh.user_id = ?";

        try (Connection connection = DatabaseConnect.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            JSONArray historyArray = new JSONArray();

            while (rs.next()) {
                String fileName = rs.getString("file_name");
                JSONObject video = new JSONObject();
                video.put("id", rs.getInt("id"));
                video.put("file_name", rs.getString("file_name"));
                video.put("size", rs.getInt("size"));
                video.put("last_modified", rs.getTimestamp("last_modified"));
                video.put("views", rs.getInt("views"));
                video.put("url", AppConfig.VIDEO_API + URLEncoder.encode(fileName, "UTF-8"));
                historyArray.put(video);
            }

            response.setContentType("application/json");
            response.getWriter().write(historyArray.toString());
        }
    }

    private boolean hasUserWatchedBefore(int userId, int mediaId) throws SQLException, ClassNotFoundException {
        String sql = "SELECT COUNT(*) FROM viewHistory WHERE user_id = ? AND media_id = ?";
        out.println("checking if user watched before "  );
        try (Connection connection = DatabaseConnect.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, mediaId);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private void addToViewHistory(int userId, int mediaId) throws SQLException, ClassNotFoundException {
        if (hasUserWatchedBefore(userId, mediaId)) {
            return;
        }

        String sql = "INSERT INTO viewHistory (user_id, media_id) VALUES (?, ?)";

        try (Connection connection = DatabaseConnect.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, mediaId);
            stmt.executeUpdate();
        }
    }

    private void incrementViewCount(int mediaId) throws SQLException, ClassNotFoundException {
        String sql = "UPDATE media SET views = views + 1 WHERE id = ?";

        try (Connection connection = DatabaseConnect.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, mediaId);

            int update = stmt.executeUpdate();
        }
    }

    private void deleteVideoFromDB(String fileName) {

        String sql = "DELETE FROM media WHERE file_name = ?";

        try (Connection connection = DatabaseConnect.getConnection()) {
            PreparedStatement deleteStm = connection.prepareStatement(sql);
            deleteStm.setString(1, fileName);

            int delete = deleteStm.executeUpdate();


            //int delete = deleteStm.executeQuery();

        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    private void returnViewsCount(HttpServletResponse response, int mediaId) throws IOException {
        JSONObject jsonResponse = new JSONObject();

        try (Connection conn = DatabaseConnect.getConnection()) {
            String query = "SELECT views FROM media WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, mediaId);
                ResultSet rs = stmt.executeQuery();
                int viewsCount = rs.next() ? rs.getInt("views") : 0;
                jsonResponse.put("viewsCount", viewsCount);
            }

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(jsonResponse.toString());

        } catch (SQLException | ClassNotFoundException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int getViewCount(Connection conn, int mediaId) throws SQLException {
        String sql = "SELECT views FROM media WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, mediaId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("views");
            }
        }
        return 0;
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

    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

       /* private void checkFile() {
            File directory = new File(AppConfig.VIDEO_DIRECTORY);

            if (!directory.exists() || !directory.isDirectory()) {
                System.err.println("Error: Video directory not found: " + AppConfig.VIDEO_DIRECTORY);
                return;
            }

            File[] files = directory.listFiles();
            if (files == null) {
                System.err.println("Error: Unable to list files in directory.");
                return;
            }

            System.out.println("Checking files in directory...");

            for (File file : files) {
                String fileName = file.getName();

                if (fileName.toLowerCase().endsWith(".mp4")) {
                    double videoSize = file.length() / (1024.0 * 1024.0);
                    long lastModified = file.lastModified();


                    File subtitleFile = new File(AppConfig.VIDEO_DIRECTORY, fileName.replace(".mp4", ".vtt"));
                    boolean subtitleAvailable = subtitleFile.exists();


                    if (!VideoMetaData.isVideoInDatabase(fileName)) {
                        System.out.println("Inserting new video: " + fileName);

                        int defaultUserId = getUsernameFromCookies(request);
                        String defaultHashtags = "#default";

                        VideoMetaData.insertVideoMetadata(defaultUserId, fileName, videoSize, lastModified, subtitleAvailable, defaultHashtags);
                    } else {
                        System.out.println("Skipping duplicate video: " + fileName);
                    }
                } else if (fileName.toLowerCase().endsWith(".txt")) {

                    PlaylistServlet.processPlaylistFile(file);
                }
            }
        }*/


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
        out.println("Fetching metadata for: " + fileName);

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
        try {
            fileName = java.net.URLDecoder.decode(fileName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            System.err.println("Error decoding filename: " + e.getMessage());
        }

        File videoFile = new File(AppConfig.VIDEO_DIRECTORY, fileName);

        if (!videoFile.exists() || !videoFile.isFile()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video not found: " + videoFile.getAbsolutePath());
            return;
        }

        response.setContentType("video/mp4");
        response.setContentLengthLong(videoFile.length());

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(videoFile));
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                os.flush();
            }
        } catch (IOException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error streaming video");
        }
    }

    private void enableCORS(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }

}

