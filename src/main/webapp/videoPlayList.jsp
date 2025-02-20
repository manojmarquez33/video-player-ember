<%@ page import="java.io.*, java.util.*, java.net.URLEncoder" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%
    String playlistFile = request.getParameter("playlist");
    String videoDir = "C:/Users/manoj/eclipse-workspace/VideoPlayer/videos/";
    List<String> videoList = new ArrayList<>();

    if (playlistFile != null) {
        File file = new File(videoDir + playlistFile);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    videoList.add(line.trim());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Playlist file does not exist.");
        }
    }

%>

<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Video Playlist</title>
    <link rel="stylesheet" type="text/css" href="css/style.css">
    <link rel="icon" type="image/png" href="./VideoPlayer/images/video-player.png">
</head>
<body>

    <a href="index.jsp" class="back-button">&#8592; Back</a>
    <div class="container">
        <div class="video-player">
            <div class="video-container">
                <div class="video-wrapper">
                    <video id="videoPlayer">
                        <source id="videoSource" src="VideoServlet?video=<%= URLEncoder.encode(videoList.get(0).replace("\"", ""), "UTF-8") %>" type="video/mp4">
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

    <script src="videoDetails.js"></script>
    <script>
        const isPlaylist = true;

        let videoList = <%= new com.google.gson.Gson().toJson(videoList) %>;

        let currentVideo = 0;
        let totalDuration = 0;
        let videoDurations = [];
        let isSeeking = false;
        let seekTime = 0;


function preloadDurations(index = 0) {
    if (index >= videoList.length) {

          totalDuration = 0;
          for (let i = 0; i < videoDurations.length; i++) {
              totalDuration += videoDurations[i];
          }

        sessionStorage.setItem("holeListTotalTime", totalDuration);

        document.getElementById("totalTime").textContent = formatTime(totalDuration);
        document.getElementById("videoBar").max = totalDuration;
        return;
    }

    let tempVideo = document.createElement("video");
    tempVideo.src = "VideoServlet?video=" + encodeURIComponent(videoList[index]);
    tempVideo.preload = "metadata";

    tempVideo.onloadedmetadata = function () {
        videoDurations[index] = tempVideo.duration || 0;
        preloadDurations(index + 1);
    };
}

preloadDurations();

function playNextVideo() {
    if (currentVideo < videoList.length - 1) {
        currentVideo++;

        videoSource.src = "VideoServlet?video=" + encodeURIComponent(videoList[currentVideo]);
        videoPlayer.load();

        videoPlayer.onloadeddata = function () {
            videoPlayer.currentTime = 0;
            videoPlayer.play();

            if (isPlaylist && totalTimeValue) {
                videoBar.max = totalTimeValue;
                totalTime.textContent = formatTime(totalTimeValue);
            }
        };
    } else {
        console.log("Playlist finished");
    }
}

videoPlayer.addEventListener("ended", playNextVideo);

videoBar.addEventListener("input", function () {
    let seekTime = parseFloat(videoBar.value);
    let loadeadTime = 0;

    for (let i = 0; i < videoList.length; i++) {
        if (seekTime < loadeadTime + videoDurations[i]) {
            if (currentVideo !== i) {
                currentVideo = i;

                videoSource.src = "VideoServlet?video=" + encodeURIComponent(videoList[i]);
                videoPlayer.load();

                videoPlayer.onloadeddata = function () {
                    videoPlayer.currentTime = seekTime - loadeadTime;
                    videoPlayer.play();
                };
            } else {
                videoPlayer.currentTime = seekTime - loadeadTime;
            }
            break;
        }
        loadeadTime += videoDurations[i];
    }
});

       videoPlayer.addEventListener("timeupdate", function () {
            if (isSeeking) return;

             let previousTime = 0;
             for (let i = 0; i < currentVideo; i++) {
                 previousTime += videoDurations[i];
             }

            let overallTime = previousTime + videoPlayer.currentTime;
            videoBar.value = overallTime;
            currentTime.textContent = formatTime(overallTime);
        });

        document.getElementById("playPause").addEventListener("click", PlayPause);
        document.getElementById("skipForward").addEventListener("click", skipForward);
        document.getElementById("skipBackward").addEventListener("click", skipBackward);
        document.getElementById("fastRewind").addEventListener("click", fastRewind);
        document.getElementById("fastForward").addEventListener("click", fastForward);
        document.getElementById("playbackSpeed").addEventListener("change", setPlayBackRate);
        document.getElementById("volumeBar").addEventListener("input", setVolume);
        document.getElementById("fullscreen").addEventListener("click", activeFullScreen);
    </script>

</body>
</html>
