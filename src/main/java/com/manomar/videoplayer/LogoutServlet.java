package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import org.json.JSONObject;
import java.io.IOException;

import static com.manomar.videoplayer.LoginServlet.enableCORS;

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
        }

        clearCookie(response, "username");
        clearCookie(response, "sessionId");
        clearCookie(response, "JSESSIONID");

        jsonResponse.put("success", true);
        jsonResponse.put("message", "Logout successful. Cookies removed.");

        response.getWriter().write(jsonResponse.toString());
    }

    private void clearCookie(HttpServletResponse response, String cookieName) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setPath("/");
        cookie.setHttpOnly(false);
        cookie.setSecure(false);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
