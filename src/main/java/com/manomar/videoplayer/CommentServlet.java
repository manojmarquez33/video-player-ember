    package com.manomar.videoplayer;
    
    import jakarta.servlet.ServletException;
    import jakarta.servlet.annotation.WebServlet;
    import jakarta.servlet.http.HttpServlet;
    import jakarta.servlet.http.HttpServletRequest;
    import jakarta.servlet.http.HttpServletResponse;
    import org.json.JSONArray;
    import org.json.JSONObject;
    import java.io.*;
    import  java.sql.*;
    
    @WebServlet("/CommentServlet")
    public class CommentServlet extends HttpServlet {
    
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
                int mediaId = jsonObject.getInt("mediaId");  // Get mediaId from request
                String commentText = jsonObject.getString("comment");
    
                if (commentText.isEmpty()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid input");
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
    
            String sql = "SELECT comment_text, created_at FROM comments WHERE media_id = ? ORDER BY created_at DESC";
    
            try (Connection conn = DatabaseConnect.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
    
                stmt.setInt(1, mediaId);
                ResultSet rs = stmt.executeQuery();
                JSONArray commentsArray = new JSONArray();
    
                while (rs.next()) {
                    JSONObject comment = new JSONObject();
                    comment.put("commentText", rs.getString(        "comment_text"));
                    comment.put("createdAt", rs.getTimestamp("created_at").toString());
                    commentsArray.put(comment);
                }
    
                response.getWriter().write(commentsArray.toString());
            } catch (SQLException | ClassNotFoundException e) {
                e.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
            }
        }
    
        protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            enableCORS(response);
            response.setStatus(HttpServletResponse.SC_OK);
        }
    
        private void enableCORS(HttpServletResponse response) {
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
            response.setHeader("Access-Control-Allow-Credentials", "true");
        }
    }
