package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;
import java.io.IOException;

import static com.manomar.videoplayer.SignUpServlet.enableCORS;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        enableCORS(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        JSONObject jsonResponse = new JSONObject();

        if (session != null) {
            session.invalidate();
            jsonResponse.put("success", true);
            jsonResponse.put("message", "Logout successful.");
        } else {
            jsonResponse.put("success", false);
            jsonResponse.put("message", "No active session found.");
        }

        response.getWriter().write(jsonResponse.toString());
    }
}
