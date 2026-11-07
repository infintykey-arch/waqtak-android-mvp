package com.wirdi.app;

import android.net.Uri;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Parses supported YouTube video and playlist URLs. */
public final class YoutubeLink {
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern PLAYLIST_ID = Pattern.compile("[A-Za-z0-9_-]{2,128}");

    public final String videoId;
    public final String playlistId;

    private YoutubeLink(String videoId, String playlistId) {
        this.videoId = videoId;
        this.playlistId = playlistId;
    }

    public static YoutubeLink parse(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String candidate = input.trim();
        if (!candidate.matches("(?i)^https?://.*")) candidate = "https://" + candidate;

        final Uri uri;
        try {
            uri = Uri.parse(candidate);
        } catch (Exception ignored) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return null;
        String host = uri.getHost();
        if (host == null || !isYoutubeHost(host.toLowerCase(Locale.ROOT))) return null;

        String videoId = "";
        String queryVideo = uri.getQueryParameter("v");
        if (isVideoId(queryVideo)) videoId = queryVideo;
        if (videoId.isEmpty()) {
            List<String> segments = uri.getPathSegments();
            if (host.toLowerCase(Locale.ROOT).endsWith("youtu.be")) {
                if (!segments.isEmpty() && isVideoId(segments.get(0))) videoId = segments.get(0);
            } else if (segments.size() >= 2) {
                String kind = segments.get(0).toLowerCase(Locale.ROOT);
                if (kind.equals("embed") || kind.equals("shorts") || kind.equals("live") || kind.equals("v")) {
                    if (isVideoId(segments.get(1))) videoId = segments.get(1);
                }
            }
        }

        String playlistId = uri.getQueryParameter("list");
        if (!isPlaylistId(playlistId)) playlistId = "";
        if (videoId.isEmpty() && playlistId.isEmpty()) return null;
        return new YoutubeLink(videoId, playlistId);
    }

    public static boolean isVideoId(String value) {
        return value != null && VIDEO_ID.matcher(value).matches();
    }

    public static boolean isPlaylistId(String value) {
        return value != null && PLAYLIST_ID.matcher(value).matches();
    }

    private static boolean isYoutubeHost(String host) {
        return host.equals("youtube.com")
                || host.equals("www.youtube.com")
                || host.equals("m.youtube.com")
                || host.equals("music.youtube.com")
                || host.equals("youtu.be")
                || host.equals("www.youtu.be")
                || host.equals("youtube-nocookie.com")
                || host.equals("www.youtube-nocookie.com");
    }
}
