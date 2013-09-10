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
import java.io.FileOutputStream;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmlpull.v1.XmlSerializer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.*;
import android.preference.*;
import android.preference.Preference.*;
import android.util.Log;
import android.util.Xml;
import android.view.*;
import android.widget.*;

import com.andreadec.musicplayer.database.*;

public class PreferencesActivity extends PreferenceActivity implements OnPreferenceClickListener {
	private final static String DEFAULT_RADIO_FILENAME = Environment.getExternalStorageDirectory() + "/musicplayer_webradio.xml";
	
	private SharedPreferences preferences;
	private Preference preferenceClearCache, preferenceIndexBaseFolder, preferenceAbout, importRadio, exportRadio;
	
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    	if(Build.VERSION.SDK_INT >= 11) {
			getActionBar().setHomeButtonEnabled(true);
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
    	
    	preferences = PreferenceManager.getDefaultSharedPreferences(this);
    	
    	preferenceClearCache = findPreference("clearCache");
    	preferenceIndexBaseFolder = findPreference("indexBaseFolder");
    	preferenceAbout = findPreference("about");
    	importRadio = findPreference("importRadio");
    	exportRadio = findPreference("exportRadio");
    	
    	updateCacheSize();
    	updateBaseFolder();
    	
    	preferenceClearCache.setOnPreferenceClickListener(this);
    	preferenceIndexBaseFolder.setOnPreferenceClickListener(this);
    	preferenceAbout.setOnPreferenceClickListener(this);
    	importRadio.setOnPreferenceClickListener(this);
    	exportRadio.setOnPreferenceClickListener(this);
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
	public boolean onPreferenceClick(Preference preference) {
		if(preference.equals(preferenceClearCache)) {
			AlertDialog dialog = new AlertDialog.Builder(this).create();
			dialog.setTitle(R.string.clearCache);
			dialog.setMessage(getResources().getString(R.string.clearCacheConfirm));
			dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
			      public void onClick(DialogInterface dialog, int which) {
			    	  clearCache();
			      }
			});
			dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getString(R.string.no), new DialogInterface.OnClickListener() {public void onClick(DialogInterface dialog, int which) {}});
			dialog.show();
			return true;
		} else if(preference.equals(preferenceIndexBaseFolder)) {
			String baseFolder = preferences.getString(MusicService.PREFERENCE_BASEFOLDER, "/");
			if(baseFolder.equals("/")) {
				Dialogs.showMessageDialog(this, R.string.baseFolderNotSetTitle, R.string.baseFolderNotSetMessage);
				return true;
			}
			updateBaseFolder();
			preferenceIndexBaseFolder.setEnabled(false);
			Intent indexIntent = new Intent(this, IndexFolderService.class);
			indexIntent.putExtra("folder", baseFolder);
			startService(indexIntent);
		} else if(preference.equals(preferenceAbout)) {
			startActivity(new Intent(this, AboutActivity.class));
		} else if(preference.equals(importRadio)) {
			importRadio();
		} else if(preference.equals(exportRadio)) {
			exportRadio();
		}
		return false;
	}
	
	private void updateCacheSize() {
		File f = getDatabasePath("Songs");
    	double dbSize = f.length()/1024.0;
    	preferenceClearCache.setSummary(getResources().getString(R.string.clearCacheSummary, dbSize+""));
	}
	
	private void updateBaseFolder() {
		String baseFolder = preferences.getString(MusicService.PREFERENCE_BASEFOLDER, null);
		String summary = getResources().getString(R.string.indexBaseFolderSummary) + "\n\n";
		summary += getResources().getString(R.string.currentBaseFolder) + " ";
		if(baseFolder==null) {
			summary += getResources().getString(R.string.notSet);
			summary += "\n\n";
			summary += getResources().getString(R.string.baseFolderInstructions);
		} else {
			summary += baseFolder;
		}
		preferenceIndexBaseFolder.setSummary(summary);
	}
	
	private void clearCache() {
		SQLiteDatabase db = new SongsDatabase(this).getWritableDatabase();
		db.delete("Songs", "", null);
		db.close();
		Toast.makeText(this, R.string.cacheCleared, Toast.LENGTH_LONG).show();
		updateCacheSize();
	}
	
	private void importRadio() {
		AlertDialog dialog = new AlertDialog.Builder(this).create();
		dialog.setTitle(R.string.importRadio);
		dialog.setMessage(getResources().getString(R.string.importConfirm, DEFAULT_RADIO_FILENAME));
		dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				importRadio(DEFAULT_RADIO_FILENAME);
			}
		});
		dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getString(R.string.no), new DialogInterface.OnClickListener() {public void onClick(DialogInterface dialog, int which) {}});
		dialog.show();
	}
	
	private void importRadio(String filename) {
		if(filename==null) return;
		Log.i("Import file", filename);
		File file = new File(filename.replace("file://", ""));
		
		try {
			DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(file);
			doc.getDocumentElement().normalize();
	
			NodeList streams = doc.getElementsByTagName("stream");
			for(int i=0; i<streams.getLength(); i++) {
				Element stream = (Element)streams.item(i);
				String url = stream.getAttribute("url");
				String name = stream.getAttribute("name");
				if(url==null || url.equals("")) continue;
				if(name==null || name.equals("")) name = url;
				
				WebRadioDatabase webRadioDatabase = new WebRadioDatabase(this);
				SQLiteDatabase db = webRadioDatabase.getWritableDatabase();
				ContentValues values = new ContentValues();
				values.put("url", url);
				values.put("name", name);
				try {
					db.insertOrThrow("WebRadio", null, values);
				} catch(Exception e) {
				} finally {
					db.close();
				}
			}
			
			Toast.makeText(this, R.string.importSuccess, Toast.LENGTH_LONG).show();
		} catch(Exception e) {
			Toast.makeText(this, R.string.importError, Toast.LENGTH_LONG).show();
			Log.e("WebRadioAcitivity", "doImport", e);
		}
	}
	
	private void exportRadio() {
		AlertDialog dialog = new AlertDialog.Builder(this).create();
		dialog.setTitle(R.string.exportRadio);
		dialog.setMessage(getResources().getString(R.string.exportConfirm, DEFAULT_RADIO_FILENAME));
		dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				doExportRadio();
			}
		});
		dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getString(R.string.no), new DialogInterface.OnClickListener() {public void onClick(DialogInterface dialog, int which) {}});
		dialog.show();
	}
	
	private void doExportRadio() {
		WebRadioDatabase webRadioDatabase = new WebRadioDatabase(this);
		SQLiteDatabase db = webRadioDatabase.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT url, name FROM WebRadio ORDER BY NAME", null);
        ArrayList<String> urls = new ArrayList<String>();
    	ArrayList<String> names = new ArrayList<String>();

        while (cursor.moveToNext()) {
        	urls.add(cursor.getString(0));
        	names.add(cursor.getString(1));
        }
        db.close();
		
		File file = new File(DEFAULT_RADIO_FILENAME);
		try {
			FileOutputStream fos = new FileOutputStream(file);
			XmlSerializer serializer = Xml.newSerializer();
			serializer.setOutput(fos, "UTF-8");
	        serializer.startDocument(null, Boolean.valueOf(true));
	        serializer.startTag(null, "streams");
	        for(int i=0; i<urls.size(); i++) {
	        	serializer.startTag(null, "stream");
	        	serializer.attribute(null, "url", urls.get(i));
	        	serializer.attribute(null, "name", names.get(i));
		        serializer.endTag(null, "stream");
	        }
	        serializer.endTag(null, "streams");
	        serializer.endDocument();
	        serializer.flush();
			fos.close();
			
			Toast.makeText(this, R.string.exportSuccess, Toast.LENGTH_LONG).show();
		} catch(Exception e) {
			Toast.makeText(this, R.string.exportError, Toast.LENGTH_LONG).show();
			Log.e("WebRadioAcitivity", "doExport", e);
		}
	}
}
