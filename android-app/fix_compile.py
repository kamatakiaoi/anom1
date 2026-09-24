with open('app/src/main/java/com/anonymous/chat/adapters/MessageAdapter.java', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    line = line.replace('if (activePlayingPlayer != null && activePlayingView != vvMsgVideoInline)', 'if (activePlayingPlayer != null && activePlayingView != null && activePlayingView.getParent() != playerContainer)')
    line = line.replace('private static void setupVideoListeners(FrameLayout container, ExoPlayer player, ProgressBar pb, ImageView thumb, ImageView btnFs, ImageView btnPlay)', 'private static void setupVideoListeners(androidx.media3.ui.PlayerView vv, ExoPlayer player, ProgressBar pb, ImageView thumb, ImageView btnFs, ImageView btnPlay)')
    line = line.replace('container.setVisibility(View.GONE);', 'vv.setVisibility(View.GONE);')
    line = line.replace('activePlayingView = container;', 'activePlayingView = vv;')
    new_lines.append(line)

with open('app/src/main/java/com/anonymous/chat/adapters/MessageAdapter.java', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
