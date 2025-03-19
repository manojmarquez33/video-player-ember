package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import org.json.JSONObject;

import java.io.*;
import java.net.URLEncoder;
import java.sql.*;
import java.util.*;
@WebServlet("/user-profile")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024 * 2,
        maxFileSize = 1024 * 1024 * 10,
        maxRequestSize = 1024 * 1024 * 50
)

public class UserProfileServlet extends HttpServlet {

    private static final String PROFILE_IMAGE_FOLDER = "C:/Users/inc-5388/Desktop/ZoTube/profile/";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        setCORSHeaders(response);

        String username = getUsernameFromCookies(request);
        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Unauthorized: No username found in cookies.\"}");
            return;
        }

        try (Connection conn = DatabaseConnect.getConnection()) {
            int userId = getUserId(conn, username);
            if (userId == -1) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\": \"User not found.\"}");
                return;
            }
            JSONObject userProfile = getUserProfile(conn, userId, username);
            List<Map<String, Object>> userVideos = getUserVideos(conn, userId);

            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put("profile", userProfile);
            jsonResponse.put("videos", userVideos);

            PrintWriter out = response.getWriter();
            out.print(jsonResponse.toString());
            out.flush();
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Database error occurred.\"}");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        setCORSHeaders(response);

        String username = getUsernameFromCookies(request);
        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Unauthorized: No username found in cookies.\"}");
            return;
        }

        try (Connection conn = DatabaseConnect.getConnection()) {
            int userId = getUserId(conn, username);
            if (userId == -1) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\": \"User not found.\"}");
                return;
            }

            System.out.println("Received datas:");
            for (Part part : request.getParts()) {
                System.out.println("Field: " + part.getName());
            }

            String newUsername = getFormField(request, "username");
            String email = getFormField(request, "email");
            String fullname = getFormField(request, "fullname");

            if (newUsername == null || newUsername.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            if (fullname == null || fullname.trim().isEmpty()) {
                fullname = "Unknown User";
            }

            Part filePart = request.getPart("profilePicture");
            String profilePictureFilename = null;

            if (filePart != null && filePart.getSize() > 0) {
                profilePictureFilename = saveProfilePicture(filePart, username);
            }

            updateUserProfile(conn, userId, newUsername, email, fullname, profilePictureFilename);

            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put("message", "Profile updated successfully");
            response.getWriter().write(jsonResponse.toString());

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Database error occurred.\"}");
        }
    }

    private String getFormField(HttpServletRequest request, String fieldName) throws IOException, ServletException {
        Part part = request.getPart(fieldName);
        if (part == null) {
            return null;
        }
        InputStream inputStream = part.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder value = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            value.append(line);
        }
        return value.toString().trim();
    }


    private String saveProfilePicture(Part filePart, String username) throws IOException {
        File folder = new File(PROFILE_IMAGE_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        String fileName = username + "_" + System.currentTimeMillis() + ".jpg";
        File file = new File(PROFILE_IMAGE_FOLDER, fileName);

        try (InputStream input = filePart.getInputStream();
             OutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
        return fileName;
    }

    private void updateUserProfile(Connection conn, int userId, String username, String email, String fullname, String profilePicture) throws SQLException {
        String sql = "UPDATE users SET username = ?, email = ?, fullname = ?, profile_picture = ? WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setString(3, fullname);

            if (profilePicture != null) {
                stmt.setString(4, profilePicture);
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }

            stmt.setInt(5, userId);
            stmt.executeUpdate();
        }
    }

    private String getUsernameFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("username".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private int getUserId(Connection conn, String username) throws SQLException {
        String sql = "SELECT id FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        return -1;
    }

    private JSONObject getUserProfile(Connection conn, int userId, String username) throws SQLException {

        String sql = "SELECT email, fullname, profile_picture FROM users WHERE id = ?";
        JSONObject userProfile = new JSONObject();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                userProfile.put("username", username);
                userProfile.put("email", rs.getString("email"));
                userProfile.put("fullname", rs.getString("fullname"));

                String profilePicture = rs.getString("profile_picture");
                if (profilePicture != null) {
                    String profilePicUrl = "http://localhost:8080/VideoPlayer_war_exploded/profile/" + profilePicture;
                    userProfile.put("profilePicture", profilePicUrl);
                } else {
                    userProfile.put("profilePicture", JSONObject.NULL);
                }
            }
        }
        return userProfile;
    }

    private List<Map<String, Object>> getUserVideos(Connection conn, int userId) throws SQLException {
        List<Map<String, Object>> videos = new ArrayList<>();
        String query = "SELECT file_name, size, last_modified FROM media WHERE user_id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> video = new HashMap<>();
                String fileName = rs.getString("file_name");

                String videoUrl = AppConfig.VIDEO_API + URLEncoder.encode(fileName, "UTF-8");

                video.put("file_name", fileName);
                video.put("url", videoUrl);
                video.put("size", rs.getLong("size"));
                video.put("last_modified", rs.getTimestamp("last_modified").toString());

                videos.add(video);
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return videos;
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setCORSHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void setCORSHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }
}
