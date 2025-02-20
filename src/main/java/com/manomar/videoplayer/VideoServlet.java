package com.manomar.videoplayer;

import java.io.*;
import java.net.URLEncoder;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

@WebServlet("/VideoServlet")
public class VideoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String VIDEO_DIRECTORY = "C:/Users/manoj/eclipse-workspace/VideoPlayer/videos/";

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:4200");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setHeader("Access-Control-Allow-Credentials", "true");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        String fileName = request.getParameter("video");

        if (fileName != null && !fileName.isEmpty()) {
            File videoFile = new File(VIDEO_DIRECTORY + fileName);

            if (!videoFile.exists()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Video not found");
                return;
            }

            streamVideo(response, videoFile);
            return;
        }
        getVideoDetails(response);
    }

    private void getVideoDetails(HttpServletResponse response) throws IOException {
        File directory = new File(VIDEO_DIRECTORY);
        File[] files = directory.listFiles();
        JSONArray videoArray = new JSONArray();

        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);
                double videoSize = file.length() / (1024.0 * 1024.0);
                long daysDiff = (System.currentTimeMillis() - file.lastModified()) / (1000 * 3600 * 24);

                JSONObject videoObject = new JSONObject();
                videoObject.put("fileName", fileName);
                videoObject.put("fileExtension", fileExtension);
                videoObject.put("size", String.format("%.2f MB", videoSize));
                videoObject.put("daysAgo", daysDiff);
                videoObject.put("url", "http://localhost:8080/VideoPlayer_war_exploded/VideoServlet?video="
                        + URLEncoder.encode(fileName, "UTF-8"));

                videoArray.put(videoObject);
            }
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(videoArray.toString());
    }

    private void streamVideo(HttpServletResponse response, File videoFile) throws IOException {
        response.setContentType("video/mp4");
        response.setHeader("Accept-Ranges", "bytes");
        response.setContentLengthLong(videoFile.length());

        try (FileInputStream fis = new FileInputStream(videoFile);
             OutputStream os = response.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
                os.flush();
            }
        }
    }
}
