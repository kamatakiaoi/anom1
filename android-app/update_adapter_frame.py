with open('app/src/main/java/com/anonymous/chat/adapters/MessageAdapter.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace PlayerView bindings with FrameLayout
content = content.replace('final PlayerView vvMsgVideoInline;', 'final FrameLayout playerContainer;')
content = content.replace('vvMsgVideoInline = itemView.findViewById(R.id.vvMsgVideoInline);', 'playerContainer = itemView.findViewById(R.id.playerContainer);')

content = content.replace('''        void recycle() {
            try {
                if (vvMsgVideoInline != null) {
                    if (activePlayingView == vvMsgVideoInline) {
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
                    vvMsgVideoInline.setVisibility(View.GONE);
                }''', '''        void recycle() {
            try {
                if (playerContainer != null) {
                    if (activePlayingView != null && activePlayingView.getParent() == playerContainer) {
                        if (activePlayingPlayer != null) {
                            activePlayingPlayer.stop();
                            activePlayingPlayer.release();
                            activePlayingPlayer = null;
                        }
                        playerContainer.removeView(activePlayingView);
                        activePlayingView = null;
                    } else if (playerContainer.getChildCount() > 0) {
                        android.view.View child = playerContainer.getChildAt(0);
                        if (child instanceof androidx.media3.ui.PlayerView) {
                            androidx.media3.ui.PlayerView pv = (androidx.media3.ui.PlayerView) child;
                            if (pv.getPlayer() != null) {
                                pv.getPlayer().stop();
                                pv.getPlayer().release();
                            }
                        }
                        playerContainer.removeAllViews();
                    }
                    playerContainer.setVisibility(android.view.View.GONE);
                }''')

content = content.replace('PlayerView vvMsgVideoInline,', 'FrameLayout playerContainer,')
content = content.replace('vvMsgVideoInline.setVisibility(View.GONE);', 'playerContainer.setVisibility(View.GONE);')
content = content.replace('vvMsgVideoInline.setVisibility(View.VISIBLE);', 'playerContainer.setVisibility(View.VISIBLE);')
content = content.replace('vvMsgVideoInline.setOnClickListener(videoClickListener);', 'playerContainer.setOnClickListener(videoClickListener);')
content = content.replace('setupVideoBinding(itemView, cardMsgVideo, ivMsgVideoThumb, vvMsgVideoInline, pbVideoLoading, btnPlayVideo, btnFullscreenVideo, msg.getVideo(), listener);', 'setupVideoBinding(itemView, cardMsgVideo, ivMsgVideoThumb, playerContainer, pbVideoLoading, btnPlayVideo, btnFullscreenVideo, msg.getVideo(), listener);')

content = content.replace('PlayerView vv,', 'FrameLayout container,')
content = content.replace('''            ExoPlayer player = new ExoPlayer.Builder(context).build();
            vv.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(Uri.parse(full)));
            player.prepare();
            
            setupVideoListeners(vv, player, pb, thumb, btnFs, btnPlay);''', '''            ExoPlayer player = new ExoPlayer.Builder(context).build();
            PlayerView pv = new PlayerView(context);
            pv.setUseController(false);
            pv.setPlayer(player);
            container.removeAllViews();
            container.addView(pv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            
            player.setMediaItem(MediaItem.fromUri(Uri.parse(full)));
            player.prepare();
            
            setupVideoListeners(pv, player, pb, thumb, btnFs, btnPlay);''')

content = content.replace('''                            ExoPlayer player = new ExoPlayer.Builder(itemView.getContext()).build();
                            vvMsgVideoInline.setPlayer(player);
                            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(cached)));
                            player.prepare();
                            setupVideoListeners(vvMsgVideoInline, player, pbVideoLoading, ivMsgVideoThumb, btnFullscreenVideo, btnPlayVideo);''', '''                            ExoPlayer player = new ExoPlayer.Builder(itemView.getContext()).build();
                            PlayerView pv = new PlayerView(itemView.getContext());
                            pv.setUseController(false);
                            pv.setPlayer(player);
                            playerContainer.removeAllViews();
                            playerContainer.addView(pv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
                            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(cached)));
                            player.prepare();
                            setupVideoListeners(pv, player, pbVideoLoading, ivMsgVideoThumb, btnFullscreenVideo, btnPlayVideo);''')

content = content.replace('if (vvMsgVideoInline.getPlayer() != null && vvMsgVideoInline.getPlayer().isPlaying())', 'if (activePlayingPlayer != null && activePlayingPlayer.isPlaying() && activePlayingView != null && activePlayingView.getParent() == playerContainer)')
content = content.replace('currentPos = (int) vvMsgVideoInline.getPlayer().getCurrentPosition();', 'if (activePlayingPlayer != null) currentPos = (int) activePlayingPlayer.getCurrentPosition();')
content = content.replace('vvMsgVideoInline.getPlayer().pause();', 'if (activePlayingPlayer != null) activePlayingPlayer.pause();')

content = content.replace('streamVideoInline(itemView.getContext(), vvMsgVideoInline, videoUrl, pbVideoLoading, ivMsgVideoThumb, btnFullscreenVideo, btnPlayVideo);', 'streamVideoInline(itemView.getContext(), playerContainer, videoUrl, pbVideoLoading, ivMsgVideoThumb, btnFullscreenVideo, btnPlayVideo);')

with open('app/src/main/java/com/anonymous/chat/adapters/MessageAdapter.java', 'w', encoding='utf-8') as f:
    f.write(content)
