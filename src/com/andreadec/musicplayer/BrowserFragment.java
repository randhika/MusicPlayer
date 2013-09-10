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

import java.io.File;
import java.util.ArrayList;
import android.os.*;
import android.support.v4.app.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import android.widget.AdapterView.*;
import com.andreadec.musicplayer.adapters.*;

public class BrowserFragment extends Fragment implements OnItemClickListener {
	private final static int MENU_SETASBASEFOLDER = -1;
	private final static int MENU_ADDFOLDERTOPLAYLIST = -2;
	private ListView listViewBrowser;
	private SongsArrayAdapter songsArrayAdapter;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (container == null) return null;
		View view = inflater.inflate(R.layout.layout_browser, container, false);
		listViewBrowser = (ListView)view.findViewById(R.id.listViewBrowser);
		listViewBrowser.setOnItemClickListener(this);
		registerForContextMenu(listViewBrowser);
		updateListView(true);
		return view;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		int position = ((AdapterContextMenuInfo)menuInfo).position;
		Object item = songsArrayAdapter.getItem(position);
		if(item instanceof String) return;
		
		super.onCreateContextMenu(menu, view, menuInfo);
		MainActivity mainActivity = (MainActivity)getActivity();
		
		if(item instanceof File) {
			menu.add(ContextMenu.NONE, MENU_SETASBASEFOLDER, 0, mainActivity.getResources().getString(R.string.setAsBaseFolder));
			ArrayList<Playlist> playlists = mainActivity.getPlaylists();
			if(playlists.size()>0) {
				SubMenu playlistMenu = menu.addSubMenu(ContextMenu.NONE, MENU_ADDFOLDERTOPLAYLIST, 1, R.string.addFolderToPlaylist);
				playlistMenu.setHeaderTitle(R.string.addToPlaylist);
				for(int i=0; i<playlists.size(); i++) {
					Playlist playlist = playlists.get(i);
					playlistMenu.add(ContextMenu.NONE, i, i, playlist.getName());
				}
			}
		} else if(item instanceof Song) {
			menu.setHeaderTitle(R.string.addToPlaylist);
			ArrayList<Playlist> playlists = mainActivity.getPlaylists();
			for(int i=0; i<playlists.size(); i++) {
				Playlist playlist = playlists.get(i);
				menu.add(ContextMenu.NONE, i, i, playlist.getName());
			}
		}
	}
	
	int oldPosition;
	public boolean onContextItemSelectedBrowser (MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		int position;
		if(info!=null) {
			position = info.position;
			oldPosition = position;
		} else {
			position = oldPosition;
		}
		Object listItem = songsArrayAdapter.getItem(position);
		
		if(listItem instanceof String) return true;
		
		MainActivity mainActivity = (MainActivity)getActivity();
		ArrayList<Playlist> playlists = mainActivity.getPlaylists();
		
		if(listItem instanceof Song) {
			Song song = (Song)listItem;
			mainActivity.addSongToPlaylist(playlists.get(item.getItemId()), song);
		} else if(listItem instanceof File) {
			File folder = (File)listItem;
			switch(item.getItemId()) {
			case MENU_SETASBASEFOLDER:
				mainActivity.setBaseFolder(folder);
				break;
			case MENU_ADDFOLDERTOPLAYLIST:
				super.onContextItemSelected(item);
				break;
			default:
				mainActivity.addFolderToPlaylist(playlists.get(item.getItemId()), folder);
				break;
			}
		}
		
		return true;
	}
	
	public void setSongsArrayAdapter(SongsArrayAdapter songsArrayAdapter) {
		this.songsArrayAdapter = songsArrayAdapter;
	}
	
	public void updateListView(boolean restoreOldPosition) {
		if(songsArrayAdapter==null || listViewBrowser==null) return;
		if(restoreOldPosition) {
        	Parcelable state = listViewBrowser.onSaveInstanceState();
        	listViewBrowser.setAdapter(songsArrayAdapter);
        	listViewBrowser.onRestoreInstanceState(state);
        } else {
        	listViewBrowser.setAdapter(songsArrayAdapter);
        }
	}
	
	public void scrollToSong(Song song) {
		for(int i=0; i<songsArrayAdapter.getCount(); i++) {
			Object item = songsArrayAdapter.getItem(i);
			if(item instanceof Song) {
				if(((Song)item).equals(song)) {
					listViewBrowser.smoothScrollToPosition(i);
					break;
				}
			}
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		MainActivity activity = (MainActivity)getActivity();
		Object item = songsArrayAdapter.getItem(position);
		if (item instanceof File) {
			File newDirectory = (File) item;
			activity.gotoDirectory(newDirectory, null);
		} else if (item instanceof Song) {
			activity.playSong((Song)item, false);
		} else {
			activity.gotoParentDir();
		}
	}
}
