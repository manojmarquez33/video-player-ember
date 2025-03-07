package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.sql.*;

@WebServlet("/CommentServlet")
public class CommentServlet extends HttpServlet {


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
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

            int mediaId = jsonObject.getInt("mediaId");
            String commentText = jsonObject.getString("comment");

            if (commentText.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid input: Comment text is empty");
                return;
            }

            String sql = "INSERT INTO comments (media_id, comment_text) VALUES (?, ?)";
            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, mediaId);
                stmt.setString(2, commentText);
                stmt.executeUpdate();

                response.setStatus(HttpServletResponse.SC_OK);
                PrintWriter writer = response.getWriter();
                writer.write("{\"message\":\"Comment posted successfully!\"}");
                writer.flush();
            } catch (SQLException | ClassNotFoundException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String mediaIdParam = request.getParameter("mediaId");

        if (mediaIdParam == null || mediaIdParam.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing mediaId parameter");
            return;
        }

        int mediaId = Integer.parseInt(mediaIdParam);

        String sql = "SELECT id, comment_text, created_at FROM comments WHERE media_id = ? ORDER BY created_at DESC";

        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, mediaId);
            ResultSet rs = stmt.executeQuery();
            JSONArray commentsArray = new JSONArray();

            while (rs.next()) {
                JSONObject comment = new JSONObject();
                comment.put("commentId", rs.getInt("id"));
                comment.put("commentText", rs.getString("comment_text"));
                comment.put("createdAt", rs.getTimestamp("created_at").toString());
                commentsArray.put(comment);
            }

            response.getWriter().write(commentsArray.toString());
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
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
            String newCommentText = jsonObject.getString("comment");

            if (newCommentText.isEmpty()) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid input: Comment text is empty");
                return;
            }

            String selectSql = "SELECT created_at FROM comments WHERE id = ?";

            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {

                selectStmt.setInt(1, commentId);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        Timestamp createdAt = rs.getTimestamp("created_at");
                        long currentTimeMillis = System.currentTimeMillis();
                        long diffMillis = currentTimeMillis - createdAt.getTime();


                        if (diffMillis > 10 * 60 * 1000) {
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Comment cannot be edited after 10 minutes");
                            return;
                        }
                    } else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Comment not found");
                        return;
                    }
                }
            } catch (SQLException | ClassNotFoundException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error during comment edit");
                return;
            }

            String updateSql = "UPDATE comments SET comment_text = ? WHERE id = ?";
            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {

                updateStmt.setString(1, newCommentText);
                updateStmt.setInt(2, commentId);
                int updated = updateStmt.executeUpdate();
                if (updated > 0) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    PrintWriter writer = response.getWriter();
                    writer.write("{\"message\":\"Comment updated successfully!\"}");
                    writer.flush();
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "Comment not found");
                }
            } catch (SQLException | ClassNotFoundException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error during comment update");
            }
        }
    }


    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String commentIdParam = request.getParameter("commentId");
        if (commentIdParam == null || commentIdParam.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing commentId parameter");
            return;
        }
        int commentId = Integer.parseInt(commentIdParam);

        String sql = "DELETE FROM comments WHERE id = ?";
        try (Connection conn = DatabaseConnect.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, commentId);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("{\"message\":\"Comment deleted successfully!\"}");
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Comment not found");
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error during comment deletion");
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void enableCORS(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }
}
