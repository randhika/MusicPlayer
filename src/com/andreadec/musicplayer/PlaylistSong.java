/*
 * Copyright 2012-2013 Andrea De Cesare
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andreadec.musicplayer;

/* Class representing a song in a playlist */
public class PlaylistSong extends Song {
	private static final long serialVersionUID = 1L;
	private long id;
	private Playlist playlist;
	
	public PlaylistSong(String uri, String artist, String title, long id, Playlist playlist) {
		super(uri, artist, title, null);
		this.id = id;
		this.playlist = playlist;
	}
	
	public long getId() {
		return id;
	}
	
	public Playlist getPlaylist() {
		return playlist;
	}
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof PlaylistSong)) return false;
		PlaylistSong s2 = (PlaylistSong)o;
		return id==s2.id;
	}
}
