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
import android.view.*;
import android.widget.*;
import com.andreadec.musicplayer.*;

public class SearchResultsArrayAdapter extends ArrayAdapter<Song> {
	private final SearchActivity searchActivity;
	private final ArrayList<Song> songs;
 
	public SearchResultsArrayAdapter(SearchActivity searchActivity, ArrayList<Song> songs) {
		super(searchActivity, R.layout.song_item, songs);
		this.searchActivity = searchActivity;
		this.songs = songs;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		Song song = songs.get(position);
		LayoutInflater inflater = searchActivity.getLayoutInflater();
		view = inflater.inflate(R.layout.song_item, parent, false);
		TextView textViewSongItemTitle = (TextView)view.findViewById(R.id.textViewSongItemTitle);
		TextView textViewSongItemArtist = (TextView)view.findViewById(R.id.textViewSongItemArtist);
		textViewSongItemTitle.setText(song.getTitle());
		textViewSongItemArtist.setText(song.getArtist());
		return view;
	}
}
