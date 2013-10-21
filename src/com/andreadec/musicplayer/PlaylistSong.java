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

import java.util.*;

import android.graphics.Bitmap;

/* Class representing a song in a playlist */
public class PlaylistSong implements PlayableItem {
	private String uri;
	private long id;
	private Playlist playlist;
	private String artist;
	private String title;
	private boolean hasImage;
	
	public PlaylistSong(String uri, String artist, String title, long id, boolean hasImage, Playlist playlist) {
		this.uri = uri;
		this.id = id;
		this.playlist = playlist;
		this.title = title;
		this.artist = artist;
		this.hasImage = hasImage;
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

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public String getArtist() {
		return artist;
	}

	@Override
	public String getPlayableUri() {
		return uri;
	}
	
	@Override
	public boolean hasImage() {
		return hasImage;
	}
	
	@Override
	public Bitmap getImage() {
		return Utils.getMusicFileImage(uri);
	}

	@Override
	public PlayableItem getNext(boolean repeatAll) {
		ArrayList<PlaylistSong> songs = playlist.getSongs();
		int index = songs.indexOf(this);
		if(index<songs.size()-1) {
			return songs.get(index+1);
		} else {
			if(repeatAll) return songs.get(0);
		}
		return null;
	}

	@Override
	public PlayableItem getPrevious() {
		ArrayList<PlaylistSong> songs = playlist.getSongs();
		int index = songs.indexOf(this);
		if(index>0) {
			return songs.get(index-1);
		} else {
			return null;
		}
	}

	@Override
	public PlayableItem getRandom(Random random) {
		ArrayList<PlaylistSong> songs = playlist.getSongs();
		return songs.get(random.nextInt(songs.size()));
	}

	@Override
	public boolean isLengthAvailable() {
		return true;
	}
	
	@Override
	public ArrayList<Information> getInformation() {
		ArrayList<Information> info = new ArrayList<Information>();
		info.add(new Information(R.string.artist, artist));
		info.add(new Information(R.string.title, title));
		info.add(new Information(R.string.playlist, playlist.getName()));
		info.add(new Information(R.string.fileName, uri));
		info.add(new Information(R.string.fileSize, Utils.getFileSize(uri)));
		return info;
	}
}
