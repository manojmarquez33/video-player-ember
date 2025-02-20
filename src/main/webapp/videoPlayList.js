 const isPlaylist = true;
       // let videoList = <%= videoList.toString() %>.map(v => v.replace(/"/g, ''));
        let currentVideoIndex = 0;
        let totalDuration = 0;
        let videoDurations = [];
        let isSeeking = false;
        let seekTime = 0;


function preloadDurations(index = 0) {
    if (index >= videoList.length) {
        totalDuration = videoDurations.reduce((sum, dur) => sum + dur, 0);

        sessionStorage.setItem("playlistTotalTime", totalDuration);

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
    if (currentVideoIndex < videoList.length - 1) {
        currentVideoIndex++;

        videoSource.src = "VideoServlet?video=" + encodeURIComponent(videoList[currentVideoIndex]);
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
    let accumulatedTime = 0;

    for (let i = 0; i < videoList.length; i++) {
        if (seekTime < accumulatedTime + videoDurations[i]) {
            if (currentVideoIndex !== i) {
                currentVideoIndex = i;

                videoSource.src = "VideoServlet?video=" + encodeURIComponent(videoList[i]);
                videoPlayer.load();

                videoPlayer.onloadeddata = function () {
                    videoPlayer.currentTime = seekTime - accumulatedTime;
                    videoPlayer.play();
                };
            } else {
                videoPlayer.currentTime = seekTime - accumulatedTime;
            }
            break;
        }
        accumulatedTime += videoDurations[i];
    }
});

       videoPlayer.addEventListener("timeupdate", function () {
            if (isSeeking) return;
            let elapsedTime = videoDurations.slice(0, currentVideoIndex).reduce((sum, dur) => sum + dur, 0);
            let overallTime = elapsedTime + videoPlayer.currentTime;
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