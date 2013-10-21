/*
 * Copyright 2013 Andrea De Cesare
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
import java.net.*;
import java.util.*;
import android.app.*;
import android.content.*;
import android.support.v4.app.*;

public class PodcastItemDownloaderService extends IntentService {
	private final static int UPDATE_INTERVAL = 500000;
	private final static String STOP_DOWNLOAD_INTENT = "com.andreadec.musicplayer.stopdownload";
	private NotificationManager notificationManager;
	private NotificationCompat.Builder notificationBuilder;
	private PendingIntent stopDownloadPendingIntent;
	
	private InputStream input;
	private FileOutputStream output;
	
	public PodcastItemDownloaderService() {
		super("PodcastItemDownloader");
	}
	
	@Override
	public void onCreate () {
		super.onCreate();
		notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		notificationBuilder = new NotificationCompat.Builder(this);
		notificationBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
		notificationBuilder.setOngoing(true);
		notificationBuilder.setProgress(100, 0, true);
		stopDownloadPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(STOP_DOWNLOAD_INTENT), 0);
		notificationBuilder.addAction(R.drawable.cancel, getResources().getString(R.string.stopDownload), stopDownloadPendingIntent);
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(STOP_DOWNLOAD_INTENT);
		BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            	if(intent.getAction().equals(STOP_DOWNLOAD_INTENT)) {
            		try {
            			input.close();
            		} catch(Exception e) {}
            	}
            }
        };
        registerReceiver(broadcastReceiver, intentFilter);
		
		
		String type = intent.getStringExtra("type");
		String title = intent.getStringExtra("title");
		String idItem = intent.getStringExtra("idItem");
		String podcastsDirectory = intent.getStringExtra("podcastsDirectory");
		notificationBuilder.setContentTitle(getResources().getString(R.string.podcastDownloading, title));
		notificationBuilder.setContentText(getResources().getString(R.string.podcastDownloading, title));
		notificationManager.notify(Constants.NOTIFICATION_PODCAST_ITEM_DOWNLOAD_ONGOING, notificationBuilder.build());

		String filename = podcastsDirectory+"/"+UUID.randomUUID().toString();
		Intent intentCompleted = new Intent("com.andreadec.musicplayer.podcastdownloadcompleted");
		
		try {
			if(type.equalsIgnoreCase("audio/mpeg") || type.equalsIgnoreCase("audio/.mp3")) filename+=".mp3";
			else if(type.equalsIgnoreCase("audio/ogg")) filename+=".ogg";
			else throw new Exception("Unsupported format");
			
			URL url = new URL(intent.getStringExtra("url"));
	        HttpURLConnection httpConnection = (HttpURLConnection)url.openConnection();
	        if(httpConnection.getResponseCode()!=200) throw new Exception("Failed to connect");
	        int length = httpConnection.getContentLength();
	        String lengthString = Utils.formatFileSize(length);
	        input = httpConnection.getInputStream();
	        output = new FileOutputStream(filename);
	        
	        byte[] buffer = new byte[1024];
	        int read = 0;
	        int totalRead = 0;
	        int nextNotification = UPDATE_INTERVAL;
	        while((read = input.read(buffer))>0) {
	        	output.write(buffer, 0, read);
	        	totalRead += read;
	        	if(totalRead>nextNotification) {
	        		String progress = Utils.formatFileSize(totalRead)+"/"+lengthString;
	        		notificationBuilder.setContentText(progress);
		        	notificationBuilder.setProgress(length, totalRead, false);
		        	notificationManager.notify(Constants.NOTIFICATION_PODCAST_ITEM_DOWNLOAD_ONGOING, notificationBuilder.build());
		        	nextNotification = totalRead+UPDATE_INTERVAL;
	        	}
	        }
	        
	        intentCompleted.putExtra("success", true);
			PodcastItem.setDownloadedFile(idItem, filename);
		} catch(Exception e) {
			new File(filename).delete();
        	PodcastItem.setDownloadCanceled(idItem);
			intentCompleted.putExtra("success", false);
			intentCompleted.putExtra("reason", e.getMessage());
			showErrorNotification(e.getMessage());
		} finally {
			try {
				output.flush();
		        output.close();
			} catch(Exception e) {}
			try {
				input.close();
			} catch(Exception e) {}
		}
		
		unregisterReceiver(broadcastReceiver);
		
		sendBroadcast(intentCompleted);
		notificationManager.cancel(Constants.NOTIFICATION_PODCAST_ITEM_DOWNLOAD_ONGOING);
	}

	private void showErrorNotification(String msg) {
		NotificationCompat.Builder errorNotification = new NotificationCompat.Builder(this);
		errorNotification.setSmallIcon(android.R.drawable.stat_sys_download_done);
		errorNotification.setContentTitle(getResources().getString(R.string.error));
		errorNotification.setContentText(getResources().getString(R.string.podcastDownloadError)+": "+msg);
		notificationManager.notify(Constants.NOTIFICATION_PODCAST_ITEM_DOWNLOAD_ERROR, errorNotification.build());
	}
}
