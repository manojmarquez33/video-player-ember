package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;

@WebServlet("/signup")
public class SignUpServlet extends HttpServlet {

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
        String fullname = jsonObject.getString("fullname");
        String email = jsonObject.getString("email");
        String password = jsonObject.getString("password");
        JSONArray interestIds = jsonObject.getJSONArray("interestIds");

        try (Connection conn = DatabaseConnect.getConnection()) {
            conn.setAutoCommit(false);

            String userSql = "INSERT INTO users (fullname,username, email, password) VALUES (?, ?, ?,?)";
            try (PreparedStatement stmt = conn.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, fullname);
                stmt.setString(2, username);
                stmt.setString(3, email);
                stmt.setString(4, password);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int userId = rs.getInt(1);

                    String interestSql = "INSERT INTO user_interests (user_id, interest_id) VALUES (?, ?)";
                    try (PreparedStatement interestStmt = conn.prepareStatement(interestSql)) {
                        for (int i = 0; i < interestIds.length(); i++) {
                            interestStmt.setInt(1, userId);
                            interestStmt.setInt(2, interestIds.getInt(i));
                            interestStmt.addBatch();
                        }
                        interestStmt.executeBatch();
                    }
                }
            }

            conn.commit();
            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write("{\"message\":\"User registered successfully.\"}");

        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "GET method is not supported for /signup");
    }

    public static void enableCORS(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }
}
