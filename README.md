# Music Player

Music player application for Android.

## Why another music player for Android
There are many good music players for Android but I couldn't find one which let me organize my music as I used to, as files into folders.
So I decided to write this application for my personal use and to publish it hoping it could be useful for other people too.

## Features
- Easily browse songs in folders
- Manage playlists
- Web radios playback
- Playback control from notification panel (Android 4.1+ only)
- Replay, shuffle
- Bass booster, equalizer
- Songs search by title or artist
- Possibility to execute commands shaking the device


## Required permissions
- READ\_PHONE\_STATE (read phone status and identity): necessary to be notified if a phone call arrives, so that the playback can be stopped;
- WAKE\_LOCK (prevent phone from sleeping): necessary to let the music keep playing when the screen is turned off;
- INTERNET (full network access): necessary to listen to web radios;
- READ\_EXTERNAL\_STORAGE (test access to protected storage): necessary to import web radios list;
- WRITE\_EXTERNAL\_STORAGE (modify or delete contents of your SD card): necessary to export web radios list.


## License
Copyright 2012-2013 Andrea De Cesare

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.  
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


## Used libraries
This project uses the following open source libraries.

### Android Support Library v4
Copyright (c) 2005-2008, The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.  
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

### Icons
Some icons are from the [Tango Desktop Project](http://tango.freedesktop.org), released into the Public Domain.


## Special thanks
I'd like to thank the following people who helped me during the development:

- Spierpa
- Matteo
