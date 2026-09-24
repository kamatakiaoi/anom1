import re

for file_path in ['app/src/main/res/layout/item_message_me.xml', 'app/src/main/res/layout/item_message_other.xml']:
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    old = '''                        <androidx.media3.ui.PlayerView
                            android:id="@+id/vvMsgVideoInline"
                            android:layout_width="match_parent"
                            android:layout_height="match_parent"
                            android:layout_gravity="center"
                            app:use_controller="false"
                            android:visibility="gone" />'''
    
    new = '''                        <FrameLayout
                            android:id="@+id/playerContainer"
                            android:layout_width="match_parent"
                            android:layout_height="match_parent"
                            android:layout_gravity="center"
                            android:visibility="gone" />'''
    
    content = content.replace(old, new)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)
