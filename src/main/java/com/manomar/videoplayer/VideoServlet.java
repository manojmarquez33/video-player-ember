    package com.manomar.videoplayer;

    import jakarta.servlet.ServletException;
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
    import static java.lang.System.out;


    @WebServlet("/VideoServlet")
    public class VideoServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        @Override
        public void init() throws ServletException {
            checkFile();
            System.out.println("check file 1");
        }

        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            enableCORS(response);

            String fileName = request.getParameter("video");
            String metadataOnly = request.getParameter("metadata");
            String searchQuery = request.getParameter("search");
            String playlistName = request.getParameter("playlist");
            String username = request.getParameter("username");
            String mediaIdParam = request.getParameter("mediaId");

            try {
                if (fileName != null && !fileName.isEmpty()) {
                    if ("true".equals(metadataOnly)) {
                        returnSingleVideoMetadata(response, fileName);
                    } else {
                        streamVideo(response, fileName);
                    }
                    return;
                }

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");

                PrintWriter out = response.getWriter();

                if (username != null && mediaIdParam != null) {
                    try {
                        int mediaId = Integer.parseInt(mediaIdParam);
                        int userId = getUserIdFromDatabase(username);
                        if (userId == -1) {
                            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
                            return;
                        }
                        returnLikeStatus(response, userId, mediaId);
                        return;
                    } catch (NumberFormatException e) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid media ID");
                        return;
                    }
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
                    int userId = getUserIdFromDatabase(username);
                    if (userId == -1) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
                        return;
                    }

                    try {
                        JSONObject videoData = fetchRecommendedAndOtherVideos(userId);

                        if (videoData == null || videoData.isEmpty()) {
                            response.setStatus(HttpServletResponse.SC_OK);
                            out.write("{\"message\": \"No videos found.\"}");
                            return;
                        }

                        System.out.println("Returning videos: " + videoData.toString());
                        response.setStatus(HttpServletResponse.SC_OK);
                        out.write(videoData.toString());

                    } catch (SQLException | ClassNotFoundException e) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        JSONObject errorResponse = new JSONObject();
                        errorResponse.put("error", "Database error: " + e.getMessage());
                        out.write(errorResponse.toString());
                        e.printStackTrace();
                    }
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing username parameter");
                }

            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject errorResponse = new JSONObject();
                errorResponse.put("error", "Internal Server Error: " + e.getMessage());
                response.getWriter().write(errorResponse.toString());
                e.printStackTrace();
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

                    if (username.isEmpty()) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
                        return;
                    }

                    if (mediaId == -1) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid media ID");
                        return;
                    }

                    out.println("Received Like Status Update - Username: " + username + ", Media ID: " + mediaId + ", Like Status: " + likeStatus);

                    int userId = getUserIdFromDatabase(username);
                    if (userId == -1) {
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "User not found");
                        return;
                    }

                    if (checkIfLikeExists(userId, mediaId)) {
                        updateLikeStatus(userId, mediaId, likeStatus);
                    } else {
                        insertLikeStatus(userId, mediaId, likeStatus);
                    }

                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write("{\"message\": \"Like status updated successfully\"}");

                    response.setStatus(HttpServletResponse.SC_OK);
                } catch (JSONException e) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format: " + e.getMessage());
                }

            } catch (IOException e) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error reading request: " + e.getMessage());
            }
        }


        public JSONObject fetchRecommendedAndOtherVideos(int userId) throws SQLException, ClassNotFoundException {
            JSONObject response = new JSONObject();

            JSONArray recommendedVideos = new JSONArray();
            JSONArray otherVideos = new JSONArray();

            Connection conn = DatabaseConnect.getConnection();
            try {
                String interestQuery = "SELECT i.interest_name FROM user_interests ui " +
                        "JOIN available_interests i ON ui.interest_id = i.id WHERE ui.user_id = ?";
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

                String videoQuery = "SELECT id, file_name, size, last_modified, hashtags FROM media";
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
                        String[] hashtagList = hashtags.toLowerCase().split(",");
                        for (String hashtag : hashtagList) {
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

                recommendedVideoMap.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                        .forEach(entry -> recommendedVideos.put(entry.getKey()));

                response.put("recommendedVideos", recommendedVideos);
                response.put("otherVideos", otherVideos);

            } finally {
                conn.close();
            }

            return response;
        }


        private void returnLikeStatus(HttpServletResponse response, int userId, int mediaId) throws IOException {
            String sql = "SELECT " +
                    "(SELECT COUNT(*) FROM likes WHERE media_id = ? AND like_status = 1) AS likeCount, " +
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

        private void checkFile() {

            File directory = new File(AppConfig.VIDEO_DIRECTORY);
            File[] files = directory.listFiles();
            System.out.println("check file 2");
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();

                    if (fileName.endsWith(".mp4")) {
                        double videoSize = file.length() / (1024.0 * 1024.0);
                        long lastModified = file.lastModified();

                        if (!VideoMetaData.isVideoInDatabase(fileName)) {
                            boolean subtitleAvailable = new File(AppConfig.VIDEO_DIRECTORY + fileName.replace(".mp4", ".vtt")).exists();
                            out.println("Insert new video: " + fileName);
                            VideoMetaData.insertVideoMetadata(fileName, videoSize, lastModified, subtitleAvailable);
                        } else {
                            out.println("skipiing duplicate video: " + fileName);
                        }
                    } else if (fileName.endsWith(".txt")) {
                        //System.out.println("file found: " + fileName);
                        PlaylistServlet.processPlaylistFile(file);
                    }
                }
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
                fileName = java.net.URLDecoder.decode(fileName, "UTF-8"); // ✅ Proper decoding
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
