package com.manomar.videoplayer;

public class AppConfig {

    public static final String VideoServlet_API_URL = "http://localhost:8080/VideoPlayer_war_exploded/VideoServlet";

    public static final String PlaylistServlet_API_URL = "http://localhost:8080/VideoPlayer_war_exploded/PlaylistServlet";

    public static final String VIDEO_DIRECTORY = "C:/Users/inc-5388/Desktop/ZoTube/videos/";

    public static final String SUBTITLE_DIRECTORY = "C:/Users/manoj/eclipse-workspace/VideoPlayer/videos/subtitles/";

    public static final String VIDEO_API = VideoServlet_API_URL + "?video=";

    public static final String SUBTITLE_API = VideoServlet_API_URL + "?subtitle=";
    public static final String PLAYLIST_API = PlaylistServlet_API_URL + "?playlist=";
    public static final String SEARCH_API = VideoServlet_API_URL + "?search=";
}
