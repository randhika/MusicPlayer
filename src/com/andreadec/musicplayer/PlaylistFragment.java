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

import android.os.*;
import android.support.v4.app.*;
import android.view.*;
import android.view.ContextMenu.*;
import android.widget.*;
import android.widget.AdapterView.*;

import com.andreadec.musicplayer.adapters.*;
import com.mobeta.android.dslv.*;
import com.mobeta.android.dslv.DragSortListView.*;

public class PlaylistFragment extends Fragment implements OnItemClickListener, DropListener {
	private DragSortListView listViewPlaylist;
	private PlaylistArrayAdapter playlistArrayAdapter;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (container == null) return null;
		View view = inflater.inflate(R.layout.layout_playlist, container, false);
		listViewPlaylist = (DragSortListView)view.findViewById(R.id.listViewPlaylist);
		listViewPlaylist.setOnItemClickListener(this);
		listViewPlaylist.setDropListener(this);
		listViewPlaylist.setEmptyView(view.findViewById(R.id.listViewPlaylistEmpty));
		registerForContextMenu(listViewPlaylist);
		updateListView();
		return view;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		int position = ((AdapterContextMenuInfo)menuInfo).position;
		
		Object item = playlistArrayAdapter.getItem(position);
		if(item instanceof String) return;
		
		super.onCreateContextMenu(menu, view, menuInfo);
		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.contextmenu_editdelete, menu);

		if(item instanceof Playlist) {
			menu.setHeaderTitle(((Playlist)item).getName());
		} else if(item instanceof PlaylistSong) {
			PlaylistSong song = (PlaylistSong)item;
			menu.setHeaderTitle(song.getArtist()+" - "+song.getTitle());
			menu.findItem(R.id.menu_delete).setTitle(R.string.removeFromPlaylist);
			menu.removeItem(R.id.menu_edit);
		}
	}
	
	public boolean onContextItemSelectedPlaylist (MenuItem item) {
		MainActivity activity = (MainActivity)getActivity();
		int position = ((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).position;
		Object listItem = playlistArrayAdapter.getItem(position);
		if(listItem instanceof Playlist) {
			switch (item.getItemId()) {
			case R.id.menu_edit:
				activity.editPlaylist((Playlist)listItem);
				return true;
			case R.id.menu_delete:
				activity.deletePlaylist((Playlist)listItem);
				return true;
			}
		} else if(listItem instanceof PlaylistSong) {
			switch (item.getItemId()) {
			case R.id.menu_delete:
				activity.deleteSongFromPlaylist((PlaylistSong)listItem);
				return true;
			}
		}
		return false;
	}
	
	public void setArrayAdapter(PlaylistArrayAdapter playlistArrayAdapter) {
		this.playlistArrayAdapter = playlistArrayAdapter;
	}
	
	public void updateListView() {
		if(playlistArrayAdapter==null || listViewPlaylist==null) return;
		Parcelable state = listViewPlaylist.onSaveInstanceState();
        listViewPlaylist.setAdapter(playlistArrayAdapter);
        listViewPlaylist.onRestoreInstanceState(state);
	}
	
	public void scrollToSong(PlaylistSong song) {
		for(int i=0; i<playlistArrayAdapter.getCount(); i++) {
			Object item = playlistArrayAdapter.getItem(i);
			if(item instanceof Song) {
				if(((Song)item).equals(song)) {
					listViewPlaylist.smoothScrollToPosition(i);
					break;
				}
			}
		}
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		MainActivity activity = (MainActivity)getActivity();
		Object item = playlistArrayAdapter.getItem(position);
		if(item instanceof String) {
			activity.showPlaylistsList();
		} else if(item instanceof PlaylistSong) {
			activity.playSong((PlaylistSong)item, true);
		} else if(item instanceof Playlist) {
			activity.showPlaylist((Playlist)item);
		}
	}

	@Override
	public void drop(int from, int to) {
		MainActivity activity = (MainActivity)getActivity();
		activity.sortPlaylist(from, to);
	}
}
