<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    String videoName = (String) request.getAttribute("videoName");
    Double videoSize = (Double) request.getAttribute("videoSize");
    String videoDuration = (String) request.getAttribute("videoDuration");
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title><%= videoName != null ? videoName : "Video Player" %></title>
    <link rel="stylesheet" type="text/css" href="css/style.css">
    <link rel="icon" type="image/png" href="./VideoPlayer/images/video-player.png">
</head>
<body>
    <a href="index.jsp" class="back-button">&#8592; Back</a>
    <div class="container">

        <div class="video-player">
                <div class="video-player">
                    <div class="video-container">
                        <div class="video-wrapper">
                            <video id="videoPlayer">
                                <source src="VideoServlet?video=<%= videoName %>" type="video/mp4">
                            </video>
                        </div>
                        <div class="video-controls">
                            <button id="playPause">Play</button>
                            <span id="currentTime">0:00</span>
                            <input type="range" id="videoBar" value="0" min="0" max="100" step="0.1">
                            <span id="totalTime">0:00</span>
                            <button id="skipBackward">&lt;</button>
                            <button id="skipForward">&gt;</button>
                            <button id="fastRewind">&lt;&lt;</button>
                            <button id="fastForward">&gt;&gt;</button>

                            <select id="playbackSpeed">
                                <option value="0.5">0.5x</option>
                                <option value="1" selected>1x</option>
                                <option value="1.5">1.5x</option>
                                <option value="2">2x</option>
                                <option value="10">10x</option>
                            </select>
                            <input type="range" id="volumeBar" value="1" min="0" max="1" step="0.01">
                            <button id="zoomIn">+</button>
                            <button id="zoomOut">-</button>
                            <button id="fullscreen">[]</button>
                        </div>
                    </div>
                </div>


            <div class="video-details">
                <p><strong>Video Name:</strong> <%= videoName %></p>
                <p><strong>Size:</strong> <%= String.format("%.2f", videoSize) %> MB</p>
                <p><strong>Duration:</strong> <span id="videoDuration">Loading...</span></p>
            </div>
        </div>

        <div class="button-controls">
            <div class="group">
                <button onclick="startVideo()">Start Video</button>
                <button onclick="stopVideo()">Stop Video</button>
                <button onclick="initial()">Initial</button>
            </div>

            <div class="group">
                <button onclick="skipBackward()">Skip Backward</button>
                <button onclick="skipForward()">Skip Forward</button>
            </div>

            <div class="speed-control">
                <label for="speedInput">Speed</label>
                <input type="text" id="speedInput" placeholder="1" />
                <button onclick="setSpeed()">Set Speed</button>
            </div>
             <div class="group">
                 <button onclick="fastRewind()"><< Fast Rewind</button>

                 <button onclick="fastForward()">Fast Forward >></button>
             </div>
            <div class="skip-control">
                <label for="skipInput">Skip</label>
                <input type="number" id="skipInput" placeholder="Enter seconds" />
                <button onclick="skipToSec()">Skip</button>
            </div>

            <div class="group">
                <button onclick="downloadFrame()">Download Frame</button>
                <button onclick="convertBlackAndWhite()">Black and White</button>
            </div>
            <div class="group">
                <button onclick="zoomIn()">Zoom In</button>
                <button onclick="zoomOut()">Zoom Out</button>
                <button onclick="playNormal()">Reset</button>
            </div>
        </div>
    </div>
    <script>
        const isPlaylist = false;
    </script>

    <script src="videoDetails.js"></script>
</body>
</html>