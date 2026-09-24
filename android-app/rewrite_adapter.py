import re

with open('app/src/main/java/com/anonymous/chat/adapters/MessageAdapter.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Imports
content = content.replace('import android.widget.VideoView;', '''import androidx.media3.ui.PlayerView;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;''')

# Fields
content = content.replace('private static VideoView activePlayingVideo = null;', '''private static ExoPlayer activePlayingPlayer = null;
    private static PlayerView activePlayingView = null;''')

content = content.replace('final VideoView vvMsgVideoInline;', 'final PlayerView vvMsgVideoInline;')
content = content.replace('VideoView vvMsgVideoInline', 'PlayerView vvMsgVideoInline')

# cleanup
cleanup_old = '''    public void cleanup() {
        if (activePlayingVideo != null) {
            try {
                activePlayingVideo.stopPlayback();
                activePlayingVideo.setVisibility(View.GONE);
            } catch (Exception ignored) {}
            activePlayingVideo = null;
        }
    }'''
cleanup_new = '''    public void cleanup() {
        if (activePlayingPlayer != null) {
            try {
                activePlayingPlayer.stop();
                activePlayingPlayer.release();
            } catch (Exception ignored) {}
            activePlayingPlayer = null;
        }
        if (activePlayingView != null) {
            activePlayingView.setVisibility(View.GONE);
            activePlayingView = null;
        }
    }'''
content = content.replace(cleanup_old, cleanup_new)

# recycle
recycle_old = '''                    if (activePlayingVideo == vvMsgVideoInline) {
                        activePlayingVideo = null;
                    }
                    vvMsgVideoInline.stopPlayback();
                    vvMsgVideoInline.setVisibility(View.GONE);'''
recycle_new = '''                    if (activePlayingView == vvMsgVideoInline) {
                        if (activePlayingPlayer != null) {
                            activePlayingPlayer.stop();
                            activePlayingPlayer.release();
                            activePlayingPlayer = null;
                        }
                        activePlayingView = null;
                    } else if (vvMsgVideoInline.getPlayer() != null) {
                        vvMsgVideoInline.getPlayer().stop();
                        vvMsgVideoInline.getPlayer().release();
                        vvMsgVideoInline.setPlayer(null);
                    }
                    vvMsgVideoInline.setVisibility(View.GONE);'''
content = content.replace(recycle_old, recycle_new)

# streamVideoInline definition
stream_old = '''    private static void streamVideoInline(
            Context context,
            VideoView vv,
            String videoUrl,
            ProgressBar pb,
            ImageView thumb,
            ImageView btnFs,
            ImageView btnPlay
    ) {
        try {
            String serverUrl = PreferenceManager.getInstance(context).getServerBaseUrl();
            String full = ImageUtils.getFullMediaUrl(serverUrl, videoUrl);
            vv.setVideoURI(Uri.parse(full));
            setupVideoListeners(vv, pb, thumb, btnFs, btnPlay);
        } catch (Exception ex) {
            pb.setVisibility(View.GONE);
            btnPlay.setVisibility(View.VISIBLE);
            thumb.setVisibility(View.VISIBLE);
            vv.setVisibility(View.GONE);
        }
    }'''
stream_new = '''    private static void streamVideoInline(
            Context context,
            PlayerView vv,
            String videoUrl,
            ProgressBar pb,
            ImageView thumb,
            ImageView btnFs,
            ImageView btnPlay
    ) {
        try {
            String serverUrl = PreferenceManager.getInstance(context).getServerBaseUrl();
            String full = ImageUtils.getFullMediaUrl(serverUrl, videoUrl);
            
            ExoPlayer player = new ExoPlayer.Builder(context).build();
            vv.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(Uri.parse(full)));
            player.prepare();
            
            setupVideoListeners(vv, player, pb, thumb, btnFs, btnPlay);
        } catch (Exception ex) {
            pb.setVisibility(View.GONE);
            btnPlay.setVisibility(View.VISIBLE);
            thumb.setVisibility(View.VISIBLE);
            vv.setVisibility(View.GONE);
        }
    }'''
content = content.replace(stream_old, stream_new)

# setupVideoListeners definition
listeners_old = '''    private static void setupVideoListeners(VideoView vv, ProgressBar pb, ImageView thumb, ImageView btnFs, ImageView btnPlay) {
        vv.setOnPreparedListener(mp -> {
            pb.setVisibility(View.GONE);
            thumb.setVisibility(View.GONE);
            btnFs.setVisibility(View.VISIBLE);
            activePlayingVideo = vv;
            mp.setLooping(true);
            mp.setOnInfoListener((player, what, extra) -> {
                if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    pb.setVisibility(View.VISIBLE);
                } else if (what == android.media.MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    pb.setVisibility(View.GONE);
                }
                return false;
            });
            mp.start();
        });

        vv.setOnErrorListener((mp, what, extra) -> {
            pb.setVisibility(View.GONE);
            btnPlay.setVisibility(View.VISIBLE);
            thumb.setVisibility(View.VISIBLE);
            vv.setVisibility(View.GONE);
            return true;
        });
    }'''
listeners_new = '''    private static void setupVideoListeners(PlayerView vv, ExoPlayer player, ProgressBar pb, ImageView thumb, ImageView btnFs, ImageView btnPlay) {
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    pb.setVisibility(View.GONE);
                    thumb.setVisibility(View.GONE);
                    btnFs.setVisibility(View.VISIBLE);
                    
                    if (activePlayingPlayer != null && activePlayingPlayer != player) {
                        activePlayingPlayer.stop();
                        activePlayingPlayer.release();
                    }
                    activePlayingPlayer = player;
                    activePlayingView = vv;
                    
                    player.setRepeatMode(Player.REPEAT_MODE_ALL);
                    player.play();
                } else if (state == Player.STATE_BUFFERING) {
                    pb.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                pb.setVisibility(View.GONE);
                btnPlay.setVisibility(View.VISIBLE);
                thumb.setVisibility(View.VISIBLE);
                vv.setVisibility(View.GONE);
            }
        });
    }'''
content = content.replace(listeners_old, listeners_new)

# setupVideoBinding changes
content = content.replace('if (vvMsgVideoInline.isPlaying())', 'if (vvMsgVideoInline.getPlayer() != null && vvMsgVideoInline.getPlayer().isPlaying())')
content = content.replace('currentPos = vvMsgVideoInline.getCurrentPosition();', 'currentPos = (int) vvMsgVideoInline.getPlayer().getCurrentPosition();')
content = content.replace('vvMsgVideoInline.pause();', 'vvMsgVideoInline.getPlayer().pause();')
content = content.replace('if (activePlayingVideo != null && activePlayingVideo != vvMsgVideoInline)', 'if (activePlayingPlayer != null && activePlayingView != vvMsgVideoInline)')
content = content.replace('''                        try {
                            activePlayingVideo.stopPlayback();
                            activePlayingVideo.setVisibility(View.GONE);
                        } catch (Exception ignored) {}
                        activePlayingVideo = null;''', '''                        try {
                            activePlayingPlayer.stop();
                            activePlayingPlayer.release();
                            if (activePlayingView != null) activePlayingView.setVisibility(View.GONE);
                        } catch (Exception ignored) {}
                        activePlayingPlayer = null;
                        activePlayingView = null;''')

# file path playback
file_cache_old = '''                            vvMsgVideoInline.setVideoPath(cached.getAbsolutePath());
                            setupVideoListeners(vvMsgVideoInline, pbVideoLoading, ivMsgVideoThumb, btnFullscreenVideo, btnPlayVideo);'''
file_cache_new = '''                            ExoPlayer player = new ExoPlayer.Builder(itemView.getContext()).build();
                            vvMsgVideoInline.setPlayer(player);
                            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(cached)));
                            player.prepare();
                            setupVideoListeners(vvMsgVideoInline, player, pbVideoLoading, ivMsgVideoThumb, btnFullscreenVideo, btnPlayVideo);'''
content = content.replace(file_cache_old, file_cache_new)

with open('app/src/main/java/com/anonymous/chat/adapters/MessageAdapter.java', 'w', encoding='utf-8') as f:
    f.write(content)
