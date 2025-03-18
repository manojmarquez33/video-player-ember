package com.manomar.videoplayer;//package com.manomar.videoplayer;
//
//import java.io.*;
//import java.util.concurrent.TimeUnit;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.annotation.WebServlet;
//import jakarta.servlet.http.HttpServlet;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.json.JSONObject;
//import org.json.JSONArray;
//
//@WebServlet("/api/videos/*")
//public class VideoApiServlet extends HttpServlet {
//    private static final long serialVersionUID = 1L;
//    private static final String VIDEO_DIRECTORY = "C:/Users/manoj/eclipse-workspace/VideoPlayer/videos/";
//
//    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//        response.setCharacterEncoding("UTF-8");
//        response.setContentType("application/json");
//
//        String pathInfo = request.getPathInfo();
//
//        if (pathInfo == null || pathInfo.equals("/")) {
//            listVideos(response);
//            return;
//        }
//
//        String[] pathParts = pathInfo.split("/");
//        if (pathParts.length < 2) {
//            sendJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid API request");
//            return;
//        }
//
//        String action = pathParts[1];
//        String fileName = (pathParts.length > 2) ? pathParts[2] : null;
//
//        if ("stream".equals(action) && fileName != null) {
//            streamVideo(response, fileName);
//        } else if (fileName != null) {
//            getVideoDetails(response, fileName);
//        } else {
//            sendJsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid API request");
//        }
//    }
//
//    private void listVideos(HttpServletResponse response) throws IOException {
//        File directory = new File(VIDEO_DIRECTORY);
//        File[] files = directory.listFiles();
//        JSONArray videoList = new JSONArray();
//
//        if (files != null) {
//            for (File file : files) {
//                JSONObject videoJson = new JSONObject();
//                videoJson.put("name", file.getName());
//                videoJson.put("extension", getFileExtension(file));
//                videoJson.put("size", String.format("%.2f", file.length() / (1024.0 * 1024.0)) + " MB");
//                videoJson.put("created", getDaysAgo(file.lastModified()));
//
//                videoList.put(videoJson);
//            }
//        }
//
//        response.getWriter().write(videoList.toString());
//    }
//
//    private void getVideoDetails(HttpServletResponse response, String fileName) throws IOException {
//        File videoFile = new File(VIDEO_DIRECTORY + fileName);
//        if (!videoFile.exists()) {
//            sendJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Video file not found");
//            return;
//        }
//
//        JSONObject videoJson = new JSONObject();
//        videoJson.put("name", videoFile.getName());
//        videoJson.put("extension", getFileExtension(videoFile));
//        videoJson.put("size", String.format("%.2f", videoFile.length() / (1024.0 * 1024.0)) + " MB");
//        videoJson.put("created", getDaysAgo(videoFile.lastModified()));
//
//        response.getWriter().write(videoJson.toString());
//    }
//
//    private void streamVideo(HttpServletResponse response, String fileName) throws IOException {
//        File videoFile = new File(VIDEO_DIRECTORY + fileName);
//        if (!videoFile.exists()) {
//            sendJsonError(response, HttpServletResponse.SC_NOT_FOUND, "Video file not found");
//            return;
//        }
//
//        response.setContentType("video/mp4");
//        response.setHeader("Accept-Ranges", "bytes");
//        response.setContentLengthLong(videoFile.length());
//
//        try (FileInputStream fis = new FileInputStream(videoFile);
//             OutputStream os = response.getOutputStream()) {
//
//            byte[] buffer = new byte[8192];
//            int bytesRead;
//            while ((bytesRead = fis.read(buffer)) != -1) {
//                os.write(buffer, 0, bytesRead);
//                os.flush();
//            }
//        }
//    }
//
//    private String getFileExtension(File file) {
//        String name = file.getName();
//        int lastIndex = name.lastIndexOf(".");
//        return lastIndex == -1 ? "unknown" : name.substring(lastIndex + 1);
//    }
//
//    private long getDaysAgo(long lastModified) {
//        long diffInMillis = System.currentTimeMillis() - lastModified;
//        return TimeUnit.MILLISECONDS.toDays(diffInMillis);
//    }
//
//    private void sendJsonError(HttpServletResponse response, int statusCode, String message) throws IOException {
//        response.setStatus(statusCode);
//        JSONObject errorJson = new JSONObject();
//        errorJson.put("error", message);
//        response.getWriter().write(errorJson.toString());
//    }
//}
