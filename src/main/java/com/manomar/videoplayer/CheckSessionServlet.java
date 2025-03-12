package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;

import java.io.IOException;

@WebServlet("/check-session")
public class CheckSessionServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        JSONObject jsonResponse = new JSONObject();


        if (session != null && session.getAttribute("username") != null) {
            String username = (String) session.getAttribute("username");

            jsonResponse.put("success", true);
            jsonResponse.put("username", username);
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            jsonResponse.put("success", false);
            jsonResponse.put("message", "No session found or no username.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        response.getWriter().write(jsonResponse.toString());
    }

    public static void enableCORS(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }
}

