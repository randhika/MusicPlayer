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
import com.andreadec.musicplayer.database.*;
import com.andreadec.musicplayer.filters.*;
import android.app.*;
import android.content.*;
import android.database.sqlite.*;
import android.support.v4.app.*;

/* This service indexes a folder and all its subfolders */
/* The operation may require some time, depending on the number of audio files in the directories */
public class IndexFolderService extends IntentService {
	private final static int ONGOING_NOTIFICATION_ID = 2;
	private final static int COMPLETION_NOTIFICATION_ID = 3;
	private NotificationManager notificationManager;
	private Notification notification;
	
	public IndexFolderService() {
		super("IndexFolderService");
	}
	
	@Override
	public void onCreate () {
		super.onCreate();
		notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
		notificationBuilder.setSmallIcon(R.drawable.audio_white);
		notificationBuilder.setContentTitle(getResources().getString(R.string.app_name));
		notificationBuilder.setContentText(getResources().getString(R.string.indexingWait));
		notificationBuilder.setOngoing(true);
		notification = notificationBuilder.build();
		notificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		notificationManager.cancel(ONGOING_NOTIFICATION_ID);		
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
		notificationBuilder.setSmallIcon(R.drawable.audio_white);
		notificationBuilder.setContentTitle(getResources().getString(R.string.app_name));
		notificationBuilder.setContentText(getResources().getString(R.string.indexingCompleted));
		notificationManager.notify(COMPLETION_NOTIFICATION_ID, notificationBuilder.build());
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		File folder = new File(intent.getStringExtra("folder"));
		clearSongsDatabase();
		index(folder);
	}

	private void clearSongsDatabase() {
		SQLiteDatabase db = new SongsDatabase(getApplicationContext()).getWritableDatabase();
		db.delete("Songs", "", null);
		db.close();
	}
	
	private void index(File folder) {
		File files[] = folder.listFiles(new AudioFileFilter());
		File subfolders[] = folder.listFiles(new DirectoryFilter());
		
		for(File subfolder : subfolders) {
			if(subfolder!=null && subfolder.canRead()) {
				index(subfolder);
			}
		}
		
		SongsDatabase songsDatabase = new SongsDatabase(getApplicationContext());
		SQLiteDatabase db = songsDatabase.getWritableDatabase();
		
		for (File file : files) {
			String uri = file.getAbsolutePath();
			Song song = new Song(file.getAbsolutePath());
			ContentValues values = new ContentValues();
			values.put("uri", uri);
			values.put("artist", song.getArtist());
			values.put("title", song.getTitle());
			Integer trackNumber = song.getTrackNumber();
			if(trackNumber==null) trackNumber=-1;
			values.put("trackNumber", trackNumber);
			db.insertWithOnConflict("Songs", null, values, SQLiteDatabase.CONFLICT_REPLACE);
		}
	
		db.close();
	}
}
