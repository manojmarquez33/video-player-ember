<%@ page import="java.io.File, java.net.URLEncoder" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>ZoTube</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f9f9f9;
            padding: 20px;
        }
        h1 {
            text-align: center;
            color: #333;
        }
        .search-container {
            text-align: center;
            margin-bottom: 20px;
        }
        .search-bar {
            padding: 10px;
            width: 80%;
            max-width: 400px;
            border-radius: 5px;
            border: 1px solid #ccc;
            font-size: 16px;
        }
        .video-container {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
            gap: 20px;
            max-width: 1200px;
            margin: auto;
        }

        .video-item:hover {
            transform: scale(1.05);
        }

        .video-title {
            font-size: 14px;
            margin-top: 8px;
            color: #000;
            text-align: center;
            font-weight: bold;
        }
        .video-info {
            text-align: center;
            font-size: 12px;
            color: #666;
        }

.video-item {
    position: relative;
    background: white;
    padding: 10px;
    border-radius: 10px;
    box-shadow: 0px 4px 8px rgba(0, 0, 0, 0.1);
    transition: transform 0.2s ease-in-out;
    text-align: center;
}

.thumbnail {
    width: 100%;
    border-radius: 10px;
    display: block;
    margin: 0 auto;
}

.duration-overlay {
    position: absolute;
    bottom: 8px;
    right: 8px;
    background-color: rgba(0, 0, 0, 0.7);
    color: white;
    font-size: 12px;
    padding: 3px 6px;
    border-radius: 3px;
    font-weight: bold;
}

.logo {
    width: 50px;
    height: auto;
    vertical-align: middle;
    margin-right: 10px;
}
    </style>
    <link rel="icon" type="image/png" href="./VideoPlayer/images/video-player.png">

</head>
<body>

    <h1 class="header">
        <img src="./VideoPlayer/images/video-player.png" alt="Logo" class="logo"> ZoTube
    </h1>


    <div class="search-container">
        <input type="text" id="searchInput" class="search-bar" placeholder="Search for videos..." onkeyup="searchVideos()">
    </div>

    <div class="video-container" id="videoContainer">
        <%
            String path = "C:/Users/manoj/eclipse-workspace/VideoPlayer/videos";
            File directory = new File(path);
            File[] files = directory.listFiles();

            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    String fileExtension = fileName.substring(fileName.lastIndexOf(".") + 1);
                    double videoSize = file.length() / (1024.0 * 1024.0);

                    long modifydate = file.lastModified();
                    long currTime = System.currentTimeMillis();

                    long timeDiff = currTime - modifydate;
                    long daysDiff = timeDiff / (1000 * 3600 * 24);
        %>
                    <div class="video-item" data-title="<%= fileName.toLowerCase() %>">
                        <% if (fileExtension.equals("mp4")) { %>
                            <a href="VideoServlet?video=<%= fileName %>&val=1">
                                <video class="thumbnail" id="videoThumbnail_<%= fileName %>" preload="metadata">
                                    <source src="VideoServlet?video=<%= URLEncoder.encode(fileName, "UTF-8") %>" type="video/mp4">
                                </video>

                            </a>
                        <p class="video-title"><%= fileName %></p>
                        <p class="video-info"><%= String.format("%.2f", videoSize) %> MB</p>
                        <p class="video-info">Created <%= daysDiff %> day<%= daysDiff > 1 ? "s" : "" %> ago</p>
                        <div class="duration-overlay" id="duration_<%= fileName %>"></div>
                        <% } else if (fileExtension.equals("txt")) { %>
                            <a href="videoPlayList.jsp?playlist=<%= URLEncoder.encode(fileName, "UTF-8") %>">
                                <img height='150px' src="./VideoPlayer/images/video-playlist.png" class="thumbnail">
                            </a>
                        <p class="video-title"><%= fileName %></p>
                        <p class="video-info"><%= String.format("%.2f", videoSize) %> MB</p>
                        <p class="video-info">Created <%= daysDiff %> day<%= daysDiff > 1 ? "s" : "" %> ago</p>
                        <% } %>

                    </div>
        <%
                }
            } else {
               //out.println("<p>No videos found.</p>");
            }
        %>

    </div>

    <script>
        function formatDuration(seconds) {
            const hours = Math.floor(seconds / 3600);
            const minutes = Math.floor((seconds % 3600) / 60);
            const secondsLeft = Math.floor(seconds % 60);

            let readableDuration = "";
            if (hours > 0) readableDuration += hours + "h ";
            if (minutes > 0) readableDuration += minutes + "m ";
            if (secondsLeft > 0 || (hours === 0 && minutes === 0)) readableDuration += secondsLeft + "s";

            return readableDuration.trim();
        }

        document.addEventListener("DOMContentLoaded", function () {
            var videos = document.querySelectorAll("video");

            videos.forEach(video => {
                video.addEventListener('loadedmetadata', function () {
                    var duration = video.duration;
                    var durationElement = video.parentElement.parentElement.querySelector('.duration-overlay');

                    if (!isNaN(duration)) {
                        durationElement.textContent = formatDuration(duration);
                    } else {
                        durationElement.textContent = "Unknown";
                    }
                });
            });
        });

        function searchVideos() {
            var input = document.getElementById('searchInput').value.toLowerCase();
            var videos = document.querySelectorAll('.video-item');

            videos.forEach(function(video) {
                var title = video.getAttribute('data-title');
                if (title.indexOf(input) !== -1) {
                    video.style.display = '';
                } else {
                    video.style.display = 'none';
                }
            });
        }
    </script>

</body>
</html>
