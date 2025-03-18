package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/CommentServlet")
public class CommentServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject jsonRequest = new JSONObject(sb.toString());
            int mediaId = jsonRequest.getInt("mediaId");
            String username = jsonRequest.getString("username");
            String commentText = jsonRequest.getString("comment");
            String videoTime  = jsonRequest.getString("videoTime");

            int userId = -1;
            String userQuery = "SELECT id FROM users WHERE username = ?";
            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(userQuery)) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    userId = rs.getInt("id");
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid username");
                    return;
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            String sql = "INSERT INTO comments (media_id, user_id, comment_text,video_time) VALUES (?, ?, ?,?)";
            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, mediaId);
                stmt.setInt(2, userId);
                stmt.setString(3, commentText);
                stmt.setString(4, videoTime);
                stmt.executeUpdate();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            response.getWriter().write("{\"message\":\"Comment posted successfully!\"}");

        } catch (SQLException | JSONException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String mediaId = request.getParameter("mediaId");

        if (mediaId == null || mediaId.trim().isEmpty()) {
            response.getWriter().write("{\"error\": \"Media ID is required\"}");
            return;
        }

        JSONArray commentArray = new JSONArray();

        String sql = "SELECT c.id, c.comment_text, c.created_at,c.video_time,c.user_id, u.username " +
                "FROM comments c " +
                "JOIN users u ON c.user_id = u.id " +
                "WHERE c.media_id = ? "+
                "ORDER BY c.created_at DESC";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Integer.parseInt(mediaId));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JSONObject commentObj = new JSONObject();
                commentObj.put("comment_id", rs.getInt("id"));
                commentObj.put("user_id", rs.getInt("id"));
                commentObj.put("username", rs.getString("username"));
                commentObj.put("comment_text", rs.getString("comment_text"));
                commentObj.put("created_at", rs.getTimestamp("created_at").toInstant().toString());
                commentObj.put("video_time", rs.getString("video_time"));


                commentArray.put(commentObj);
            }

            response.getWriter().write(commentArray.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            response.getWriter().write("{\"error\": \"Database error occurred\"}");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try (BufferedReader reader = request.getReader()) {
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
            JSONObject jsonObject = new JSONObject(requestBody.toString());
            int commentId = jsonObject.getInt("commentId");
            String username = jsonObject.getString("username");
            int userId = getUserIdByUsername(username);

            String newCommentText = jsonObject.getString("comment");

            String checkSql = "SELECT user_id, created_at FROM comments WHERE id = ?";
            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setInt(1, commentId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    if (rs.getInt("user_id") != userId) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "User not access to edit this comment");
                        return;
                    }
    
                    long diffMillis = System.currentTimeMillis() - rs.getTimestamp("created_at").getTime();

                    if (diffMillis > 10 * 60 * 1000) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Comment cannot edit after 10 minutes");
                        return;
                    }
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Comment not found");
                    return;
                }
            }

            String updateSql = "UPDATE comments SET comment_text = ? WHERE id = ?";
            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, newCommentText);
                stmt.setInt(2, commentId);
                stmt.executeUpdate();
                response.getWriter().write("{\"message\":\"Comment updated successfully!\"}");
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            int commentId = Integer.parseInt(request.getParameter("commentId"));
            String username = request.getParameter("username");

            if (username == null || username.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Username is required");
                return;
            }

            String userIdQuery = "SELECT id FROM users WHERE username = ?";
            int userId = -1;

            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement userStmt = conn.prepareStatement(userIdQuery)) {
                userStmt.setString(1, username);
                ResultSet rs = userStmt.executeQuery();
                if (rs.next()) {
                    userId = rs.getInt("id");
                } else {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "User not found");
                    return;
                }
            }

            String checkSql = "SELECT user_id FROM comments WHERE id = ?";
            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setInt(1, commentId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next() && rs.getInt("user_id") != userId) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "User not access to delete this comment");
                    return;
                }
            }

            String sql = "DELETE FROM comments WHERE id = ?";
            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, commentId);
                stmt.executeUpdate();
                response.getWriter().write("{\"message\":\"Comment deleted successfully!\"}");
            }

        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    private int getUserIdByUsername(String username) throws SQLException, ClassNotFoundException {
        String query = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id");
            } else {
                throw new SQLException("User not found");
            }
        }
    }

    private void enableCORS(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }
}
