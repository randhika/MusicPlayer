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

import java.io.*;
import android.media.*;

public class Song implements Serializable {
	private static final long serialVersionUID = 1L;
	private String title, artist;
	private Integer trackNumber;
	private String uri;
	private boolean webRadio;
	
	public Song(String uri, String artist, String title, Integer trackNumber) {
		this.uri = uri;
		this.artist = artist;
		this.title = title;
		this.trackNumber = trackNumber;
	}

	public Song(String uri) {
		this.uri = uri;
		if(uri.startsWith("http://") || uri.startsWith("rtsp://")) {
			webRadio = true;
			title = uri;
			artist = "[Radio]";
		} else {
			webRadio = false;
			MediaMetadataRetriever mmr = new MediaMetadataRetriever();
			try {
				mmr.setDataSource(uri);
				title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
				artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
				if (title == null || title.equals("")) title = new File(uri).getName();
				if (artist == null) artist = "";
				try {
					trackNumber = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER));
				} catch(Exception ex) {}
			} catch(Exception e) {
				title = new File(uri).getName();
				artist = "";
			} finally {
				mmr.release();
			}
		}
	}

	public String getArtist() {
		return artist;
	}
	
	public String getTitle() {
		return title;
	}

	public Integer getTrackNumber() {
		return trackNumber;
	}

	public String getUri() {
		return uri;
	}

	public void setTitle(String title) {
		this.title = title;
	}
	
	public boolean isWebRadio() {
		return webRadio;
	}

	@Override
	public boolean equals(Object o) {
		if(!(o instanceof Song)) return false;
		Song s2 = (Song)o;
		return uri.equals(s2.uri);
	}
}
