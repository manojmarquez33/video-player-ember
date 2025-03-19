package com.manomar.videoplayer;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.*;

@WebServlet("/profile/*")
public class ProfileImageServlet extends HttpServlet {

    private static final String PROFILE_IMAGE_FOLDER = "C:\\Users\\inc-5388\\Desktop\\ZoTube\\profile";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String requestedFile = request.getPathInfo();
        if (requestedFile == null || requestedFile.equals("/")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "  Invalid file name.");
            return;
        }

        File imageFile = new File(PROFILE_IMAGE_FOLDER, requestedFile);
        if (!imageFile.exists() || imageFile.isDirectory()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found.");
            return;
        }

        response.setContentType(getServletContext().getMimeType(imageFile.getName()));
        response.setContentLength((int) imageFile.length());

        try (FileInputStream fis = new FileInputStream(imageFile);
             OutputStream os = response.getOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }
}
