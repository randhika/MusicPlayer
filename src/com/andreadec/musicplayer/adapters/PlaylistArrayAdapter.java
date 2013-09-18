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

package com.andreadec.musicplayer.adapters;

import java.util.*;

import android.content.*;
import android.view.*;
import android.widget.*;

import com.andreadec.musicplayer.*;

public class PlaylistArrayAdapter extends ArrayAdapter<Object> {
	private final Context context;
	private final ArrayList<Object> values;
	private Song playingSong;
 
	public PlaylistArrayAdapter(Context context, ArrayList<Object> values, Song playingSong) {
		super(context, R.layout.song_item, values);
		this.context = context;
		this.values = values;
		this.playingSong = playingSong;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		Object value = values.get(position);
		if (value instanceof String) {
			String playlistName = (String)value;
			LayoutInflater inflater = ((MainActivity) context).getLayoutInflater();
			view = inflater.inflate(R.layout.folder_item, parent, false);
			TextView textViewFolderItemFolder = (TextView)view.findViewById(R.id.textViewFolderItemFolder);
			textViewFolderItemFolder.setTextColor(view.getResources().getColor(R.color.blue));
			ImageView imageViewFolderItemImage = (ImageView)view.findViewById(R.id.imageViewItemImage);
			textViewFolderItemFolder.setText(playlistName);
			imageViewFolderItemImage.setImageResource(R.drawable.back);
		} else if(value instanceof PlaylistSong) {
			PlaylistSong song = (PlaylistSong)value;
			LayoutInflater inflater = ((MainActivity) context).getLayoutInflater();
			view = inflater.inflate(R.layout.song_item, parent, false);
			TextView textViewSongItemTitle = (TextView)view.findViewById(R.id.textViewSongItemTitle);
			TextView textViewSongItemArtist = (TextView)view.findViewById(R.id.textViewSongItemArtist);
			String trackNumber = "";
			if(song.getTrackNumber()!=null) trackNumber = song.getTrackNumber() + ". ";
			textViewSongItemTitle.setText(trackNumber + song.getTitle());
			textViewSongItemArtist.setText(song.getArtist());
			if(song.equals(playingSong)) {
				view.setBackgroundResource(R.color.light_blue);
				ImageView imageViewSongItemImage = (ImageView)view.findViewById(R.id.imageViewItemImage);
				imageViewSongItemImage.setImageResource(R.drawable.play_blue);
			}
		} else if(value instanceof Playlist) {
			Playlist playlist = (Playlist)value;
			LayoutInflater inflater = ((MainActivity) context).getLayoutInflater();
			view = inflater.inflate(R.layout.folder_item, parent, false);
			TextView textViewFolderItemFolder = (TextView)view.findViewById(R.id.textViewFolderItemFolder);
			textViewFolderItemFolder.setText(playlist.getName());
		}
		return view;
	}
}