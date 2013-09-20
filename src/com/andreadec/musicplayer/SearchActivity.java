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
import java.util.*;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.os.*;
import android.preference.PreferenceManager;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import android.widget.AdapterView.*;

import com.andreadec.musicplayer.adapters.*;
import com.andreadec.musicplayer.database.*;

public class SearchActivity extends Activity implements OnClickListener, OnItemClickListener, OnKeyListener {
	private EditText editTextSearch;
	private Button buttonSearch;
	private ListView listViewSearch;
	private SharedPreferences preferences;
	
	@SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        
        if(preferences.getBoolean("showHelpOverlayIndexing", true) && preferences.getString(MusicService.PREFERENCE_BASEFOLDER, "/").equals("/")) {
        	final FrameLayout frameLayout = new FrameLayout(this);
        	LayoutInflater layoutInflater = getLayoutInflater();
        	layoutInflater.inflate(R.layout.layout_search, frameLayout);
        	layoutInflater.inflate(R.layout.layout_helpoverlay_indexing, frameLayout);
        	final View overlayView = frameLayout.getChildAt(1);
        	overlayView.setOnClickListener(new OnClickListener() {
				@Override public void onClick(View v) {
					frameLayout.removeView(overlayView);
					SharedPreferences.Editor editor = preferences.edit();
					editor.putBoolean("showHelpOverlayIndexing", false);
					editor.commit();
				}
        	});
        	setContentView(frameLayout);
        } else {
        	setContentView(R.layout.layout_search);
        }
        
        if(Build.VERSION.SDK_INT >= 11) {
			getActionBar().setHomeButtonEnabled(true);
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
        
        editTextSearch = (EditText)findViewById(R.id.editTextSearch);
        editTextSearch.setOnKeyListener(this);
        buttonSearch = (Button)findViewById(R.id.buttonSearch);
        buttonSearch.setOnClickListener(this);
        listViewSearch = (ListView)findViewById(R.id.listViewSearch);
        listViewSearch.setOnItemClickListener(this);
        
        setResult(0, getIntent());
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			Intent intent = new Intent(this, MainActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onClick(View view) {
		if(view.equals(buttonSearch)) {
			search();
		}
	}
	
	private void search() {
		search(editTextSearch.getText().toString());
	}
	
	private void search(String str) {
		str = str.replace("\"", "");
		str = str.trim();
		ArrayList<Song> results = new ArrayList<Song>();
		
		SongsDatabase songsDatabase = new SongsDatabase(this);
		SQLiteDatabase db = songsDatabase.getWritableDatabase();
		
		Cursor cursor = db.rawQuery("SELECT uri, artist, title, trackNumber FROM Songs WHERE artist LIKE \"%"+str+"%\" OR title LIKE \"%"+str+"%\"", null);
		while(cursor.moveToNext()) {
			String uri = cursor.getString(0);
			String artist = cursor.getString(1);
			String title = cursor.getString(2);
        	Integer trackNumber = cursor.getInt(3);
        	if(trackNumber==-1) trackNumber=null;
        	Song song = new Song(uri, artist, title, trackNumber);
        	results.add(song);
        }
		db.close();
		
		if(results.size()==0) {
			Dialogs.showMessageDialog(this, R.string.noResultsFoundTitle, R.string.noResultsFoundMessage);
		} else {
			SearchResultsArrayAdapter adapter = new SearchResultsArrayAdapter(this, results);
			listViewSearch.setAdapter(adapter);
		}
	}
	
	private void deleteSongFromCache(Song song) {
		SongsDatabase songsDatabase = new SongsDatabase(this);
		SQLiteDatabase db = songsDatabase.getWritableDatabase();
		db.delete("Songs", "uri=\""+song.getUri()+"\"", null);
		db.close();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		SearchResultsArrayAdapter adapter = (SearchResultsArrayAdapter)listViewSearch.getAdapter();
		Song song = adapter.getItem(position);
		Intent intent = getIntent();
		intent.putExtra("song", song);
		File songFile = new File(song.getUri());
		if(!songFile.exists()) {
			Dialogs.showMessageDialog(this, R.string.notFound, R.string.songNotFound);
			deleteSongFromCache(song);
			return;
		}
		setResult(1, intent);
		finish();
	}

	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		// Manage "enter" key on keyboard
		if(event.getAction()==KeyEvent.ACTION_DOWN && keyCode==KeyEvent.KEYCODE_ENTER) {
			search();
			return true;
		}
		return false;
	}
}
