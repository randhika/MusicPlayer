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

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.*;
import android.database.sqlite.*;
import android.os.*;
import android.support.v4.app.*;
import android.view.*;
import android.view.ContextMenu.*;
import android.widget.AdapterView.*;
import android.widget.*;

import com.andreadec.musicplayer.adapters.*;
import com.andreadec.musicplayer.database.*;

public class RadioFragment extends Fragment implements OnItemClickListener {
	private ListView listViewRadio;
	private WebRadioDatabase webRadioDatabase;
	/*private ArrayList<String> urls;
	private ArrayList<String> names;*/
	private RadioArrayAdapter adapter;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		webRadioDatabase = new WebRadioDatabase(this.getActivity());
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		if (container == null) return null;
		View view = inflater.inflate(R.layout.layout_webradio, container, false);
		listViewRadio = (ListView)view.findViewById(R.id.listViewRadio);
		listViewRadio.setOnItemClickListener(this);
		listViewRadio.setEmptyView(view.findViewById(R.id.listViewRadioEmpty));
		registerForContextMenu(listViewRadio);
		updateListView();
		return view;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, view, menuInfo);
		int position = ((AdapterContextMenuInfo)menuInfo).position;
		MenuInflater inflater = getActivity().getMenuInflater();
		inflater.inflate(R.menu.contextmenu_editdelete, menu);
		menu.setHeaderTitle(adapter.getItem(position).getTitle());
	}
	
	public boolean onContextItemSelectedRadio(MenuItem item) {
		int position = ((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).position;
		switch (item.getItemId()) {
		case R.id.menu_edit:
			editRadio(adapter.getItem(position));
			return true;
		case R.id.menu_delete:
			deleteRadio(adapter.getItem(position));
			return true;
		}
		return false;
	}
	
	private void updateListView() {
		SQLiteDatabase db = webRadioDatabase.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT url, name FROM WebRadio ORDER BY NAME", null);
        ArrayList<Song> radios = new ArrayList<Song>();
        while (cursor.moveToNext()) {
        	Song radio = new Song(cursor.getString(0));
        	radio.setTitle(cursor.getString(1));
        	radios.add(radio);
        }
        db.close();
        adapter = new RadioArrayAdapter(getActivity(), radios);
        listViewRadio.setAdapter(adapter);
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		((MainActivity)getActivity()).playRadio(adapter.getItem(position));
	}
	
	private void deleteRadio(final Song radio) {
		AlertDialog dialog = new AlertDialog.Builder(getActivity()).create();
		dialog.setTitle(R.string.delete);
		dialog.setMessage(getResources().getString(R.string.deleteRadioConfirm));
		dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
		      public void onClick(DialogInterface dialog, int which) {
		    	  SQLiteDatabase db = webRadioDatabase.getWritableDatabase();
		    	  db.delete("WebRadio", "url = '" + radio.getUri() + "'", null);
		    	  db.close();
		    	  updateListView();
		      }
		});
		dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getString(R.string.no), new DialogInterface.OnClickListener() {
		      public void onClick(DialogInterface dialog, int which) {}
		});
		dialog.show();
	}
	
	private void editRadio(final Song oldSong) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		int title = oldSong==null ? R.string.addRadio : R.string.edit;
		builder.setTitle(getResources().getString(title));
		final View view = getActivity().getLayoutInflater().inflate(R.layout.layout_editwebradio, null);
		builder.setView(view);
		
		final EditText editTextUrl = (EditText)view.findViewById(R.id.editTextUrl);
		final EditText editTextName = (EditText)view.findViewById(R.id.editTextName);
		if(oldSong!=null) {
			editTextUrl.setText(oldSong.getUri());
			editTextName.setText(oldSong.getTitle());
		}
		
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				String url = editTextUrl.getText().toString();
				String name = editTextName.getText().toString();
				if(url.equals("") || url.equals("http://")) {
					Toast.makeText(getActivity(), R.string.errorInvalidURL, Toast.LENGTH_SHORT).show();
					return;
				}
	        	if(name.equals("")) name = url;
	        	
	        	if(oldSong!=null) {
		        	SQLiteDatabase db = webRadioDatabase.getWritableDatabase();
		        	db.delete("WebRadio", "url = '" + oldSong.getUri() + "'", null);
		        	db.close();
	        	}

				SQLiteDatabase db = webRadioDatabase.getWritableDatabase();
				ContentValues values = new ContentValues();
				values.put("url", url);
				values.put("name", name);
				try {
					db.insertOrThrow("WebRadio", null, values);
				} catch(Exception e) {
				} finally {
					db.close();
					updateListView();
				}
			}
		});
		
		builder.setNegativeButton(R.string.cancel, null);
		
		builder.show();
	}
	
	public void newRadio() {
		editRadio(null);
	}
}
