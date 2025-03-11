package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
        String email = jsonObject.getString("email");
        String password = jsonObject.getString("password");

        try (Connection conn = DatabaseConnect.getConnection()) {
            String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, email);
                stmt.setString(3, password); // Consider hashing the password

                int rowsInserted = stmt.executeUpdate();

                JSONObject jsonResponse = new JSONObject();
                if (rowsInserted > 0) {
                    jsonResponse.put("message", "User registered successfully.");
                    response.setStatus(HttpServletResponse.SC_CREATED);
                } else {
                    jsonResponse.put("message", "Registration failed.");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
                response.getWriter().write(jsonResponse.toString());
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
            JSONObject errorResponse = new JSONObject();
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
