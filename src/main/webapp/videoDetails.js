const videoPlayer = document.getElementById("videoPlayer");
const videoDuration = document.getElementById("videoDuration");

let intervalRewind;
let intervalForward;


const totalTime = document.getElementById("totalTime");
const videoBar = document.getElementById("videoBar");

let totalTimeValue = sessionStorage.getItem("holeListTotalTime");   

if (totalTimeValue) {
    totalTimeValue = parseFloat(totalTimeValue);
}

videoPlayer.onloadedmetadata = function () {
    if (isPlaylist && totalTimeValue) {
        videoBar.max = totalTimeValue;
        totalTime.textContent = formatTime(totalTimeValue);
    } else {
        videoBar.max = videoPlayer.duration;
        totalTime.textContent = formatTime(videoPlayer.duration);
    }
};


function formatDuration(seconds) {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secondsLeft = Math.floor(seconds % 60);

    let readableDuration = "";
    if (hours > 0) readableDuration += hours + " Hour" + (hours > 1 ? "s " : " ");
    if (minutes > 0) readableDuration += minutes + " Minute" + (minutes > 1 ? "s " : " ");
    if (secondsLeft > 0 || (hours == 0 && minutes == 0)) readableDuration += secondsLeft + " Second" + (secondsLeft > 1 ? "s" : "");
    return readableDuration.trim();
}

function initial() {
    videoPlayer.currentTime = 0;
}

function skipBackward() {
    const currentTime = videoPlayer.currentTime;
    const newTime = currentTime - 10;
    videoPlayer.currentTime = newTime < 0 ? 0 : newTime;
}

function skipForward() {
    const currentTime = videoPlayer.currentTime;
    const duration = videoPlayer.duration;
    const newTime = currentTime + 10;
    videoPlayer.currentTime = newTime > duration ? duration : newTime;
}

function startVideo() {
    videoPlayer.play();
    playPause.textContent = "pause";
}

function stopVideo() {
    clearInterval(intervalRewind);
    clearInterval(intervalForward);
    videoPlayer.pause();
    videoPlayer.playbackRate = 1;
    playPause.textContent = "play";
}

function setSpeed() {
    const speedInput = document.getElementById("speedInput").value;
    const speed = parseFloat(speedInput);
    if (!isNaN(speed) && speed > 0) {
        videoPlayer.playbackRate = speed;
    } else {
        alert("Enter +ve speed values");
    }
    videoPlayer.pause();
}

let selectedSpeed = 1;
function setPlayBackRate() {
    const speedInput = document.getElementById("playbackSpeed").value;
    const speed = parseFloat(speedInput);

    if (!isNaN(speed) && speed > 0) {
        selectedSpeed = speed;
    } else {
        alert("Enter +ve speed values");
    }
}


function fastForward() {
    clearInterval(intervalRewind);
    clearInterval(intervalForward);

    videoPlayer.play();
    videoPlayer.playbackRate = selectedSpeed;
}

function fastRewind() {
    clearInterval(intervalRewind);
    clearInterval(intervalForward);

    const fps = 10;
    intervalRewind = setInterval(function () {
        if (videoPlayer.currentTime <= 0) {
            clearInterval(intervalRewind);
            videoPlayer.currentTime = 0;
            videoPlayer.pause();
        } else {
            videoPlayer.currentTime -= (1 / fps) * selectedSpeed;
        }
    }, 1000 / fps);
}

videoPlayer.addEventListener("pause", function () {
    clearInterval(intervalRewind);
    clearInterval(intervalForward);
    videoPlayer.playbackRate = 1;
});



function getSpeed() {
    const speedInput = document.getElementById("speedInput").value;
    const speed = parseFloat(speedInput);
    return isNaN(speed) ? 1 : speed;
}

function downloadFrame() {
    const canvas = document.createElement("canvas");
    const type = canvas.getContext("2d");

      canvas.width = videoPlayer.videoWidth;
      canvas.height = videoPlayer.videoHeight;

       type.filter = getComputedStyle(videoPlayer).filter;
       type.translate(moveX, moveY);
       type.scale(curZoom, curZoom);

    type.drawImage(videoPlayer, 0, 0, videoPlayer.videoWidth, videoPlayer.videoHeight);

    const image = canvas.toDataURL("image/png");
    const link = document.createElement("a");
    link.href = image;
    link.download = "generated_image.png";
    link.click();
}

function skipToSec() {
    const skipInput = document.getElementById("skipInput").value;
    const skipSec= parseFloat(skipInput);

  let isBuff = false;
  console.log("Buffer end val:");

    for (let i=0;i<videoPlayer.buffered.length;i++) {
        const start = videoPlayer.buffered.start(i);
        const end = videoPlayer.buffered.end(i);

         console.log(end);
    if (skipSec >= start && skipSec <= end) {
           isBuff = true;
           break;
        }
      }

    if (isBuff) {
        videoPlayer.currentTime = skipSec;
    } else {
        alert("you selection cannt load.");
    }
}

function playNormal() {
    clearInterval(intervalRewind);
    clearInterval(intervalForward);
    videoPlayer.playbackRate = 1;

    videoPlayer.style.filter = "grayscale(0%)";
    videoPlayer.style.transform = "none";

     curZoom = 1;
      moveX = 0;
      moveY = 0;
      updateTransform();
    videoPlayer.play();

}


function convertBlackAndWhite() {
    const videoPlayer = document.getElementById("videoPlayer");

  if (videoPlayer.style.filter == "grayscale(0%)") {
    videoPlayer.style.filter = "grayscale(100%)";
    } else {
        videoPlayer.style.filter = "grayscale(0%)";
    }
}

          let drag = false;
          let initialX, initialY;
          let moveX = 0, moveY = 0;
          let curZoom = 1;
          const maxZoom = 3.0;
          const minZoom = 1.0;
    
          function zoomIn() {
              if (curZoom < maxZoom) {
                  curZoom += 0.1;
                  updateTransform();
              }
          }
    
          function zoomOut() {
              if (curZoom > minZoom) {
                  curZoom -= 0.1;
                  updateTransform();
              }
          }
    
          function updateTransform() {
              updateDragMove();
              videoPlayer.style.transform = `translate(${moveX}px, ${moveY}px) scale(${curZoom})`;
              videoPlayer.style.transformOrigin = "center center";
          }
    
          function updateDragMove() {
              const wrapperWidth = videoPlayer.parentElement.clientWidth;
              const wrapperHeight = videoPlayer.parentElement.clientHeight;
    
              const videoWidth = wrapperWidth * curZoom;
              const videoHeight = wrapperHeight * curZoom;
    
              const maxMoveX = (videoWidth - wrapperWidth) / 2;
              const maxMoveY = (videoHeight - wrapperHeight) / 2;
    
              if (videoWidth > wrapperWidth) {
                  moveX = Math.max(-maxMoveX, Math.min(moveX, maxMoveX));
              } else {
                  moveX = 0;
              }
              if (videoHeight > wrapperHeight) {
                  moveY = Math.max(-maxMoveY, Math.min(moveY, maxMoveY));
              } else {
                  moveY = 0;
              }
          }
    
    
          videoPlayer.addEventListener("mousedown", (e) => {
              if (curZoom > 1) {
                  drag = true;
                  initialX = e.clientX - moveX;
                  initialY = e.clientY - moveY;
                  videoPlayer.style.cursor = "grabbing";
                  e.preventDefault();
              }
          });
    
          document.addEventListener("mousemove", (e) => {
              if (drag) {
                  moveX = e.clientX - initialX;
                  moveY = e.clientY - initialY;
                  updateTransform();
              }
          });
    
          document.addEventListener("mouseup", () => {
              drag = false;
              videoPlayer.style.cursor = "grab";
          });


  const playPause = document.getElementById("playPause");

  const volumeBar = document.getElementById("volumeBar");
  const fullscreen = document.getElementById("fullscreen");

  const zoomIn_Control = document.getElementById("zoomIn");
  const zoomOut_Control = document.getElementById("zoomOut");

  const curr_Time_show = document.getElementById("currentTime");

  playPause.addEventListener('click', PlayPause);

  volumeBar.addEventListener('input', setVolume);
  fullscreen.addEventListener('click', activeFullScreen);

  zoomIn_Control.addEventListener('click', zoomIn);
  zoomOut_Control.addEventListener('click', zoomOut);





function PlayPause() {
    if (videoPlayer.paused || videoPlayer.ended) {
        videoPlayer.play();
        playPause.textContent = "Pause";
    } else {
        videoPlayer.pause();
        playPause.textContent = "Play";
    }
}


function updateVideoBar() {
    if (isPlaylist) return;
    let currentTime = videoPlayer.currentTime;
    videoBar.value = currentTime;
    curr_Time_show.textContent = formatTime(currentTime);
    requestAnimationFrame(updateVideoBar);
}


videoPlayer.addEventListener("play", () => {
    if (!isPlaylist) requestAnimationFrame(updateVideoBar);
});


videoBar.addEventListener("input", function () {
    if (isPlaylist) return;
    videoPlayer.currentTime = videoBar.value;
});


      function moveBar() {
          videoPlayer.currentTime = videoBar.value;
      }

      function setVolume() {
          videoPlayer.volume = volumeBar.value;
      }

function activeFullScreen() {
    const videoContainer = document.querySelector(".video-container");

    if (!document.fullscreenElement) {
        if (videoContainer.requestFullscreen) {
            videoContainer.requestFullscreen();
        } else if (videoContainer.mozRequestFullScreen) {
            videoContainer.mozRequestFullScreen();
        } else if (videoContainer.webkitRequestFullscreen) {
            videoContainer.webkitRequestFullscreen();
        } else if (videoContainer.msRequestFullscreen) {
            videoContainer.msRequestFullscreen();
        }
    } else {
        if (document.exitFullscreen) {
            document.exitFullscreen();
        } else if (document.mozCancelFullScreen) {
            document.mozCancelFullScreen();
        } else if (document.webkitExitFullscreen) {
            document.webkitExitFullscreen();
        } else if (document.msExitFullscreen) {
            document.msExitFullscreen();
        }
    }
}


  function formatTime(time) {
      const minutes = Math.floor(time/ 60);

      const seconds = Math.floor(time % 60);
      const formatedTime = seconds < 10 ? `0${seconds}`:seconds;

      return `${minutes}:${formatedTime}`;
  }


    document.getElementById("playPause").addEventListener("click", PlayPause);

   document.getElementById("skipForward").addEventListener("click", skipForward);
   document.getElementById("skipBackward").addEventListener("click", skipBackward);

   document.getElementById("fastRewind").addEventListener("click", fastRewind);
   document.getElementById("fastForward").addEventListener("click", fastForward);

    document.getElementById("playbackSpeed").addEventListener("change", setPlayBackRate);
    document.getElementById("volumeBar").addEventListener("input", setVolume);

    document.getElementById("zoomIn").addEventListener("click", zoomIn);
    document.getElementById("zoomOut").addEventListener("click", zoomOut);

    document.getElementById("fullscreen").addEventListener("click", activeFullScreen);
