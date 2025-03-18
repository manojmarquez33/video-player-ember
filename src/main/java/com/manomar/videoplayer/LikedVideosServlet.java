package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.*;

@WebServlet("/liked-videos")
public class LikedVideosServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String username = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("username".equals(cookie.getName())) {
                    username = cookie.getValue();
                    break;
                }
            }
        }

        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Unauthorized: No username found in cookies.\"}");
            return;
        }

        try (Connection conn = DatabaseConnect.getConnection()) {
            int userId = getUserIdByUsername(conn, username);
            if (userId == -1) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\": \"User not found.\"}");
                return;
            }

            JSONObject likedVideos = fetchLikedVideos(conn, userId);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(likedVideos.toString());
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"Database error: " + e.getMessage() + "\"}");
        }
    }

    private int getUserIdByUsername(Connection conn, String username) throws SQLException {
        String query = "SELECT id FROM users WHERE username = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return -1;
    }

    private JSONObject fetchLikedVideos(Connection conn, int userId) throws SQLException {
        JSONObject result = new JSONObject();
        JSONArray likedVideos = new JSONArray();
        JSONArray dislikedVideos = new JSONArray();

        String query = "SELECT l.media_id, m.file_name, l.like_status FROM likes l JOIN media m ON l.media_id = m.id WHERE l.user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject video = new JSONObject();
                    video.put("media_id", rs.getInt("media_id"));
                    video.put("name", rs.getString("file_name"));
                    video.put("like_status", rs.getInt("like_status"));

                    if (rs.getInt("like_status") == 1) {
                        likedVideos.put(video);
                    } else if (rs.getInt("like_status") == -1) {
                        dislikedVideos.put(video);
                    }
                }
            }
        }

        result.put("liked_videos", likedVideos);
        result.put("disliked_videos", dislikedVideos);
        return result;
    }

    private void enableCORS(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
