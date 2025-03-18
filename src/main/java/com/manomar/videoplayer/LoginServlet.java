package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        StringBuilder jsonData = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                jsonData.append(line);
            }
        }

        JSONObject jsonObject = new JSONObject(jsonData.toString());
        String username = jsonObject.getString("username");
        String password = jsonObject.getString("password");

        try (Connection conn = DatabaseConnect.getConnection()) {
            String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                try (ResultSet rs = stmt.executeQuery()) {
                    JSONObject jsonResponse = new JSONObject();

                    if (rs.next()) {
                        int userId = rs.getInt("id");

                        HttpSession session = request.getSession();

                        session.setAttribute("userId", userId);
                        session.setAttribute("username", username);
                        session.setMaxInactiveInterval(30 * 60);

                        Cookie username_Cookie = new Cookie("username", username);
                        Cookie session_Cookie = new Cookie("sessionId", session.getId());

                        username_Cookie.setHttpOnly(false);
                        session_Cookie.setHttpOnly(false);
                        username_Cookie.setPath("/");
                        session_Cookie.setPath("/");

                        int expiry = 24 * 60 * 60;
                        username_Cookie.setMaxAge(expiry);
                        session_Cookie.setMaxAge(expiry);

                        response.addCookie(username_Cookie);
                        response.addCookie(session_Cookie);

                        jsonResponse.put("success", true);
                        jsonResponse.put("user", username);
                        jsonResponse.put("sessionId", session.getId());

                        response.setStatus(HttpServletResponse.SC_OK);
                    } else {
                        jsonResponse.put("success", false);
                        jsonResponse.put("message", "Invalid username or password.");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    }
                    response.getWriter().write(jsonResponse.toString());
                    
                }
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("success", false);
            errorResponse.put("error", "Error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(errorResponse.toString());
        }
    }

    public static void enableCORS(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }
}
