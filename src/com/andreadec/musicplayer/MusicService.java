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
import java.util.*;
import java.util.concurrent.locks.*;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.media.*;
import android.media.MediaPlayer.*;
import android.media.audiofx.*;
import android.os.*;
import android.preference.*;
import android.support.v4.app.*;
import android.telephony.*;

import com.andreadec.musicplayer.comparators.*;
import com.andreadec.musicplayer.database.*;
import com.andreadec.musicplayer.filters.*;

public class MusicService extends Service implements OnCompletionListener {
	private final IBinder musicBinder = new MusicBinder();
	private final static int NOTIFICATION_ID = 1;
	public final static String PREFERENCE_BASEFOLDER = "baseFolder";
	private final static String PREFERENCE_LASTDIRECTORY = "lastDirectory";
	private final static String PREFERENCE_LASTPLAYINGSONG = "lastPlayingSong";
	private final static String PREFERENCE_LASTSONGPOSITION = "lastSongPosition";
	private final static String PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID = "lastPlyaingSongFromPlaylistId";
	private final static String PREFERENCE_SHUFFLE = "shuffle";
	private final static String PREFERENCE_REPEAT = "repeat";
	private final static String PREFERENCE_REPEATALL = "repeatAll";
	private final static String PREFERENCE_BASSBOOST = "bassBoost";
	private final static String PREFERENCE_BASSBOOSTSTRENGTH = "bassBoostStrength";
	private final static String PREFERENCE_EQUALIZER = "equalizer";
	private final static String PREFERENCE_EQUALIZERPRESET = "equalizerPreset";
	private final static String PREFERENCE_SHAKEENABLED = "shakeEnabled";
	private NotificationManager notificationManager;
	private Notification notification;
	private SharedPreferences preferences;
	
	private PendingIntent pendingIntent;
	private PendingIntent previousPendingIntent;
	private PendingIntent playpausePendingIntent;
	private PendingIntent nextPendingIntent;
	
	private File browsingDir;
	private ArrayList<File> browsingSubdirs;
	private ArrayList<Song> browsingSongs;
	
	private Song playingSong;
	private ArrayList<? extends Song> playingSongs;
	
	private ArrayList<Playlist> playlists;
	private boolean playingPlaylist = false; // Are we playing a playlist?
	
	private MediaPlayer mediaPlayer;
	private BassBoost bassBoost;
	private Equalizer equalizer;
	private boolean bassBoostAvailable;
	private boolean equalizerAvailable;
	
	private boolean shuffle, repeat, repeatAll;
	private Random random;
	
	private TelephonyManager telephonyManager;
	private MusicPhoneStateListener phoneStateListener;
	
	private BroadcastReceiver broadcastReceiver;
	
	private ShakeListener shakeListener;
	
	private boolean ready = false;
	public Lock lock = new ReentrantLock();
	public Condition condition = lock.newCondition();
	
	/**
	 * Called when the service is created.
	 */
	@Override
	public void onCreate() {
		// Initialize the telephony manager
		telephonyManager = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		phoneStateListener = new MusicPhoneStateListener();
		notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		
		// Initialize pending intents
		previousPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.andreadec.musicplayer.previous"), 0);
		playpausePendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.andreadec.musicplayer.playpause"), 0);
		nextPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.andreadec.musicplayer.next"), 0);
		pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT);
		
		// Read saved user preferences
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
				
		// Initialize the media player
		mediaPlayer = new MediaPlayer();
		mediaPlayer.setOnCompletionListener(this);
		mediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK); // Enable the wake lock to keep CPU running when the screen is switched off
		
		shuffle = preferences.getBoolean(PREFERENCE_SHUFFLE, false);
		repeat = preferences.getBoolean(PREFERENCE_REPEAT, false);
		repeatAll = preferences.getBoolean(PREFERENCE_REPEATALL, false);
		try { // This may fail if the device doesn't support bass boost
			bassBoost = new BassBoost(1, mediaPlayer.getAudioSessionId());
			bassBoost.setEnabled(preferences.getBoolean(PREFERENCE_BASSBOOST, false));
			setBassBoostStrength(preferences.getInt(PREFERENCE_BASSBOOSTSTRENGTH, 0));
			bassBoostAvailable = true;
		} catch(Exception e) {
			bassBoostAvailable = false;
		}
		try { // This may fail if the device doesn't support equalizer
			equalizer = new Equalizer(1, mediaPlayer.getAudioSessionId());
			equalizer.setEnabled(preferences.getBoolean(PREFERENCE_EQUALIZER, false));
			setEqualizerPreset(preferences.getInt(PREFERENCE_EQUALIZERPRESET, 0));
			equalizerAvailable = true;
		} catch(Exception e) {
			equalizerAvailable = false;
		}
		random = new Random(System.nanoTime()); // Necessary for song shuffle
		
		shakeListener = new ShakeListener(this);
		if(preferences.getBoolean(PREFERENCE_SHAKEENABLED, false)) {
			shakeListener.enable();
		}
		
		loadPlaylists();
		
		final String lastDirectory = preferences.getString(PREFERENCE_LASTDIRECTORY, null); // Read the last used directory from preferences
		
		// The loading of the folder content is done in a separate thread to avoid keep the application blocked.
		// When the service is ready the variable "ready" is set to true.
		new Thread() {
			public void run() {
				if(lastDirectory==null) {
					File startDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
					if(!startDir.exists()) startDir = new File("/");
					gotoDirectory(startDir);
				} else {
					File lastDir = new File(lastDirectory);
					if(!lastDir.exists()) lastDir = new File("/");
					gotoDirectory(lastDir);
				}
				loadLastSong();
				ready = true;
				lock.lock();
				condition.signalAll();
				lock.unlock();
			}
		}.start();
		
	}
	
	/* Called when service is started. */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		updateNotificationMessage();
		telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE); // Start listen for telephony events
		
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("com.andreadec.musicplayer.previous");
		intentFilter.addAction("com.andreadec.musicplayer.playpause");
		intentFilter.addAction("com.andreadec.musicplayer.next");
		intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            	if(intent.getAction().equals("com.andreadec.musicplayer.previous")) {
            		previousSong();
            	} else if(intent.getAction().equals("com.andreadec.musicplayer.playpause")) {
            		playPause();
            	} else if(intent.getAction().equals("com.andreadec.musicplayer.next")) {
            		nextSong();
            	} else if(intent.getAction().equals(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
            		if(preferences.getBoolean("stopPlayingWhenHeadsetDisconnected", false)) {
	            		pause();
            		}
            	}
            }
        };
        registerReceiver(broadcastReceiver, intentFilter);
        
        startForeground(NOTIFICATION_ID, notification);
        
		return START_STICKY;
	}
	
	// Returns true if the song has been successfully loaded
	private void loadLastSong() {
		if(preferences.getBoolean("openLastSongOnStart", false)) {
	        String lastPlayingSong = preferences.getString(PREFERENCE_LASTPLAYINGSONG, null);
        	long lastPlayingSongFromPlaylistId = preferences.getLong(PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID, -1);
        	if(lastPlayingSong!=null && (new File(lastPlayingSong).exists())) {
        		if(lastPlayingSongFromPlaylistId!=-1) {
        			playSavedSongFromPlaylist(lastPlayingSongFromPlaylistId);
        		} else if(lastPlayingSongFromPlaylistId==-1 && browsingSongs!=null) {
        			File songDirectory = new File(lastPlayingSong).getParentFile();
        			if(songDirectory==null) return;
        			
        			boolean found = false;
        			for(Song s : browsingSongs) {
		        		if(s.getUri().equals(lastPlayingSong)) {
		        			playingSongs = browsingSongs;
		        			playSong(new Song(lastPlayingSong), false);
		        			found = true;
		        			break;
		        		}
		        	}
        			if(!found) {
	        			gotoDirectory(songDirectory);
	        			playingSongs = browsingSongs;
	        			playSong(new Song(lastPlayingSong), false);
        			}
		        }
		        if(preferences.getBoolean("saveSongPosition", false)) {
		        	int lastSongPosition = preferences.getInt(PREFERENCE_LASTSONGPOSITION, 0);
		        	if(lastSongPosition<getDuration()) seekTo(lastSongPosition);
		        }
        	}
        }
	}
	
	/* Called when the activity is destroyed. */
	@Override
	public void onDestroy() {
		telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE); // Stop listen for telephony events
		notificationManager.cancel(NOTIFICATION_ID);
		unregisterReceiver(broadcastReceiver); // Disable broadcast receiver
		
		SharedPreferences.Editor editor = preferences.edit();
		editor.putBoolean(PREFERENCE_SHUFFLE, shuffle);
		editor.putBoolean(PREFERENCE_REPEAT, repeat);
		editor.putBoolean(PREFERENCE_REPEATALL, repeatAll);
		if(bassBoostAvailable) {
			editor.putBoolean(PREFERENCE_BASSBOOST, getBassBoostEnabled());
			editor.putInt(PREFERENCE_BASSBOOSTSTRENGTH, getBassBoostStrength());
		} else {
			editor.remove(PREFERENCE_BASSBOOST);
			editor.remove(PREFERENCE_BASSBOOSTSTRENGTH);
		}
		if(equalizerAvailable) {
			editor.putBoolean(PREFERENCE_EQUALIZER, getEqualizerEnabled());
			editor.putInt(PREFERENCE_EQUALIZERPRESET, getEqualizerPreset());
		} else {
			editor.remove(PREFERENCE_EQUALIZER);
			editor.remove(PREFERENCE_EQUALIZERPRESET);
		}
		editor.putBoolean(PREFERENCE_SHAKEENABLED, isShakeEnabled());
		if(playingSong!=null && !playingSong.isWebRadio()) {
			editor.putString(PREFERENCE_LASTPLAYINGSONG, playingSong.getUri());
			editor.putInt(PREFERENCE_LASTSONGPOSITION, getCurrentPosition());
			if(playingPlaylist) {
				editor.putLong(PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID, ((PlaylistSong)playingSong).getId());
			} else {
				editor.putLong(PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID, -1);
			}
		} else {
			editor.putString(PREFERENCE_LASTPLAYINGSONG, null);
			editor.putLong(PREFERENCE_LASTPLAYINGSONGFROMPLAYLISTID, -1);
		}
		editor.commit();
		
		shakeListener.disable();
		mediaPlayer.release();
		stopForeground(true);
	}
	
	/* Moves to a new directory */
	public void gotoDirectory(File directory) {
		browsingDir = directory;
		browsingSubdirs = getSubfoldersInDirectory(directory);
		browsingSongs = getSongsInDirectory(directory);
		
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(PREFERENCE_LASTDIRECTORY, directory.getAbsolutePath());
		editor.commit();
	}
	
	public boolean playSongFromBrowser(Song song) {
		if(playingSongs==null || !playingSongs.contains(song) || playingPlaylist) playingSongs = browsingSongs;
		playingPlaylist = false;
		return playSong(song, true);
	}
	
	public boolean playSongFromPlaylist(PlaylistSong song, boolean startPlaying) {
		playingSongs = song.getPlaylist().getSongs();
		playingPlaylist = true;
		return playSong(song, startPlaying);
	}
	
	public boolean playSongFromSearch(Song song) {
		File songDirectory = new File(song.getUri()).getParentFile();
		gotoDirectory(songDirectory);
		playingSongs = browsingSongs;
		playingPlaylist = false;
		return playSong(song, true);
	}
	
	public boolean playWebRadio(Song song) {
		playingSongs = new ArrayList<Song>();
		playingPlaylist = false;
		return playSong(song, true);
	}
	
	private boolean playSong(Song song) {
		return playSong(song, true);
	}
	
	private boolean playSong(Song song, boolean startPlaying) {
		playingSong = song;
		mediaPlayer.reset();
		mediaPlayer.setOnCompletionListener(null); // Sets an handler for "song completed" event
		try {
			mediaPlayer.setDataSource(song.getUri());
			try {
				mediaPlayer.prepare();
			} catch (IOException e) {
				playingSong = null;
				return false;
			}
			mediaPlayer.setOnCompletionListener(this);
			if(startPlaying) mediaPlayer.start();
			
			updateNotificationMessage();
			if(startPlaying) {
				sendBroadcast(new Intent("com.andreadec.musicplayer.newsong")); // Sends a broadcast to the activity
			}
			
			return true;
		} catch (Exception e) {
			playingSong = null;
			updateNotificationMessage();
			sendBroadcast(new Intent("com.andreadec.musicplayer.newsong")); // Sends a broadcast to the activity
			return false;
		}
	}
	
	
	

	/* BASS BOOST */
	public boolean getBassBoostAvailable() {
		return bassBoostAvailable;
	}
	public boolean toggleBassBoost() {
		boolean newState = !bassBoost.getEnabled();
		bassBoost.setEnabled(newState);
		return newState;
	}
	public boolean getBassBoostEnabled() {
		if(!bassBoostAvailable || bassBoost==null) return false;
		return bassBoost.getEnabled();
	}
	public void setBassBoostStrength(int strength) {
		bassBoost.setStrength((short)strength);
	}
	public int getBassBoostStrength() {
		return bassBoost.getRoundedStrength();
	}
	
	
	
	
	/* EQUALIZER */
	public boolean getEqualizerAvailable() {
		return equalizerAvailable;
	}
	public boolean toggleEqualizer() {
		boolean newState = !equalizer.getEnabled();
		equalizer.setEnabled(newState);
		return newState;
	}
	public boolean getEqualizerEnabled() {
		if(!equalizerAvailable || equalizer==null) return false;
		return equalizer.getEnabled();
	}
	public void setEqualizerPreset(int preset) {
		equalizer.usePreset((short)preset);
	}
	public short getEqualizerPreset() {
		return equalizer.getCurrentPreset();
	}
	public String[] getEqualizerAvailablePresets() {
		int n = equalizer.getNumberOfPresets();
		String[] presets = new String[n];
		for(short i=0; i<n; i++) {
			presets[i] = equalizer.getPresetName(i);
		}
		return presets;
	}
	
	
	
	
	/* PLAYLISTS */
	
	private void loadPlaylists() {
		playlists = new ArrayList<Playlist>();
		PlaylistsDatabase playlistsDatabase = new PlaylistsDatabase(this);
		SQLiteDatabase db = playlistsDatabase.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT id, name FROM Playlists ORDER BY position", null);
		while(cursor.moveToNext()) {
			long id = cursor.getLong(0);
			String name = cursor.getString(1);
			Playlist playlist = new Playlist(this, id, name);
			playlists.add(playlist);
		}
		cursor.close();
		db.close();
	}
	
	public ArrayList<Playlist> getPlaylists() {
		return playlists;
	}
	
	public Playlist addPlaylist(String name) {
		long id = -1;
		PlaylistsDatabase playlistsDatabase = new PlaylistsDatabase(this);
		SQLiteDatabase db = playlistsDatabase.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put("name", name);
		try {
			id = db.insertOrThrow("Playlists", null, values);
		} catch(Exception e) {
		} finally {
			db.close();
		}
		if(id==-1) return null; // Something went wrong
		
		Playlist playlist = new Playlist(this, id, name);
		playlists.add(playlist);
		Collections.sort(playlists, new PlaylistsComparator(preferences.getString("playlistsSortingMethod", "name")));
		return playlist;
	}
	
	public void deletePlaylist(Playlist playlist) {
		PlaylistsDatabase playlistsDatabase = new PlaylistsDatabase(this);
		SQLiteDatabase db = playlistsDatabase.getWritableDatabase();
		db.delete("SongsInPlaylist", "idPlaylist="+playlist.getId(), null);
		db.delete("Playlists", "id="+playlist.getId(), null);
		db.close();
		playlists.remove(playlist);
	}
	
	public void sortPlaylists(int from, int to) {
		if(to>from) {
			Collections.rotate(playlists.subList(from, to+1), -1);
		} else {
			Collections.rotate(playlists.subList(to, from+1), +1);
		}
		PlaylistsDatabase playlistsDatabase = new PlaylistsDatabase(this);
		SQLiteDatabase db = playlistsDatabase.getWritableDatabase();
		for(int i=0; i<playlists.size(); i++) {
			Playlist playlist = playlists.get(i);
			ContentValues values = new ContentValues();
			values.put("position", i);
			db.update("Playlists", values, "id="+playlist.getId(), null);
		}
		db.close();
	}

	private void playSavedSongFromPlaylist(long idSong) {
		PlaylistsDatabase playlistsDatabase = new PlaylistsDatabase(this);
		SQLiteDatabase db = playlistsDatabase.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT idPlaylist, uri, artist, title FROM SongsInPlaylist WHERE idSong="+idSong, null);
		if(cursor.moveToNext()) {
			Playlist playlist = null;
			long idPlaylist = cursor.getLong(0);
			for(Playlist p : playlists) {
				if(p.getId()==idPlaylist) playlist=p;
				break;
			}
			String uri = cursor.getString(1);
			String artist = cursor.getString(2);
			String title = cursor.getString(3);
			PlaylistSong song = new PlaylistSong(uri, artist, title, idSong, playlist);
			playSongFromPlaylist(song, false);
		}
		cursor.close();
		db.close();
	}
	
	public boolean getPlayingPlaylist() {
		return playingPlaylist;
	}
	
	
	
	
	
	
	/* SHAKE SENSOR */
	public boolean isShakeEnabled() {
		return shakeListener.isEnabled();
	}
	
	public void toggleShake() {
		if(shakeListener.isEnabled()) shakeListener.disable();
		else shakeListener.enable();
	}
	
	
	
	
	
	
	
	
	/* Updates the notification. */
	private void updateNotificationMessage() {
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this);
		notificationBuilder.setSmallIcon(R.drawable.audio_white);
		notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
		notificationBuilder.setOngoing(true);
		
		// Add previous/play_pause/next buttons to the notification
		int playPauseIcon = isPlaying() ? R.drawable.pause : R.drawable.play;
		int playPauseString = isPlaying() ? R.string.pause : R.string.play;
		notificationBuilder.setContentIntent(pendingIntent);
		
		String notificationMessage = "";
		if(playingSong==null) {
			notificationMessage = getResources().getString(R.string.noSong);
		} else {
			if(!playingSong.getArtist().equals("")) notificationMessage = playingSong.getArtist()+" - ";
			notificationMessage += playingSong.getTitle();
		}
		
		notificationBuilder.addAction(R.drawable.previous, getResources().getString(R.string.previous), previousPendingIntent);
		notificationBuilder.addAction(playPauseIcon, getResources().getString(playPauseString), playpausePendingIntent);
		notificationBuilder.addAction(R.drawable.next, getResources().getString(R.string.next), nextPendingIntent);
		notificationBuilder.setContentTitle(getResources().getString(R.string.app_name));
		notificationBuilder.setContentText(notificationMessage);
		notification = notificationBuilder.build();
		
		notificationManager.notify(NOTIFICATION_ID, notification);
	}
	
	/* Toggles play/pause status. */
	public void playPause() {
		if(playingSong==null) return;
		if (mediaPlayer.isPlaying()) {
			mediaPlayer.pause();
		} else {
			mediaPlayer.start();
		}
		updateNotificationMessage();
		sendBroadcast(new Intent("com.andreadec.musicplayer.playpausechanged"));
	}
	
	/* Starts playing song. */
	public void play() {
		if(playingSong==null) return;
		if(!mediaPlayer.isPlaying()) mediaPlayer.start();
		updateNotificationMessage();
		sendBroadcast(new Intent("com.andreadec.musicplayer.playpausechanged"));
	}
	
	/* Pauses playing song. */
	public void pause() {
		if(playingSong==null) return;
		if (mediaPlayer.isPlaying()) mediaPlayer.pause();
		updateNotificationMessage();
		sendBroadcast(new Intent("com.andreadec.musicplayer.playpausechanged"));
	}
	
	/* Seeks to a position. */
	public void seekTo(int progress) {
		/*if(mediaPlayer.isPlaying())*/ mediaPlayer.seekTo(progress);
	}
	
	/* Plays the previous song */
	public void previousSong() {
		if(playingSong==null || playingSongs==null || playingSongs.size()<1 || playingSong.isWebRadio()) return;
		
		if(isPlaying() && getCurrentPosition()>2000) {
			playSong(playingSong);
			return;
		}
		
		int index = playingSongs.indexOf(playingSong);
		
		if(repeat) {
			playSong(playingSong);
			return;
		}
		
		if(shuffle) {
			playSong(playingSongs.get(random.nextInt(playingSongs.size())));
			return;
		}
		
		if(index>0) playSong(playingSongs.get(index-1));
		else playSong(playingSong);
	}
	
	/* Plays the next song */
	public void nextSong() {
		if(playingSong==null || playingSongs==null || playingSongs.size()<1 || playingSong.isWebRadio()) return;
		int index = playingSongs.indexOf(playingSong);
		
		if(repeat) {
			playSong(playingSong);
			return;
		}
		
		if(shuffle) {
			playSong(playingSongs.get(random.nextInt(playingSongs.size())));
			return;
		}
		
		if(index<playingSongs.size()-1) {
			playSong(playingSongs.get(index+1));
		} else {
			if(repeatAll) {
				playSong(playingSongs.get(0));
			} else {
				if(!isPlaying()) {
					playingSong = null;
					sendBroadcast(new Intent("com.andreadec.musicplayer.newsong"));
				}
			}
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return musicBinder;
	}
	
	public class MusicBinder extends Binder {
		public MusicService getService() {
			return MusicService.this;
		}
	}
	
	/* Gets current browsing directory. */
	public File getBrowsingDir() {
		return browsingDir;
	}
	
	/* Gets subdirectories of current browsing directory. */
	public ArrayList<File> getBrowsingSubdirs() {
		return browsingSubdirs;
	}
	
	/* Gets songs in the current browsing directory. */
	public ArrayList<Song> getBrowsingSongs() {
		return browsingSongs;
	}
	
	/* Gets current playing song. */
	public Song getPlayingSong() {
		return playingSong;
	}
	
	/* Gets current song durations. */
	public int getDuration() {
		if(playingSong==null) return 100;
		return mediaPlayer.getDuration();
	}
	/* Gets current position in the song. */
	public int getCurrentPosition() {
		if(playingSong==null) return 0;
		return mediaPlayer.getCurrentPosition();
	}
	/* Checks if a song is currently being played */
	public boolean isPlaying() {
		if(playingSong==null) return false;
		return mediaPlayer.isPlaying();
	}

	@Override
	public void onCompletion(MediaPlayer player) {
		nextSong();
	}
	
	public boolean getRepeat() {
		return repeat;
	}

	public void setRepeat(boolean repeat) {
		this.repeat = repeat;
	}

	public boolean getShuffle() {
		return shuffle;
	}

	public void setShuffle(boolean shuffle) {
		this.shuffle = shuffle;
	}

	public boolean getRepeatAll() {
		return repeatAll;
	}

	public void setRepeatAll(boolean repeatAll) {
		this.repeatAll = repeatAll;
	}
	
	public boolean isReady() {
		return ready;
	}

	/* Phone state listener class. */
	private class MusicPhoneStateListener extends PhoneStateListener {
		private boolean wasPlaying = false;
		public void onCallStateChanged(int state, String incomingNumber) {
	    	switch(state) {
	            case TelephonyManager.CALL_STATE_IDLE:
	            	if(preferences.getBoolean("restartPlaybackAfterPhoneCall", false) && wasPlaying) play();
	                break;
	            case TelephonyManager.CALL_STATE_OFFHOOK:
	            	wasPlaying = isPlaying();
	            	pause();
	                break;
	            case TelephonyManager.CALL_STATE_RINGING:
	            	wasPlaying = isPlaying();
	            	pause();
	            	break;
	        }
	    }
	}
	
	public ArrayList<Song> getSongsInDirectory(File directory) {
		String sortingMethod = preferences.getString("songsSortingMethod", "nat");
		if(preferences.getBoolean("enableCache", true)) {
			return getSongsInDirectoryWithCache(directory, sortingMethod);
		} else {
			return getSongsInDirectoryWithoutCache(directory, sortingMethod);
		}
	}
	
	private ArrayList<Song> getSongsInDirectoryWithoutCache(File directory, String sortingMethod) {
		ArrayList<Song> songs = new ArrayList<Song>();
		File files[] = directory.listFiles(new AudioFileFilter());
		for (File file : files) {
			Song song = new Song(file.getAbsolutePath());
			songs.add(song);
		}
		Collections.sort(songs, new SongsComparator(sortingMethod));
		return songs;
	}
	
	/*
	 * ----- VERSION WITH SEPARATE THREAD TO SAVE SONGS IN DB -----
	 */
	private ArrayList<Song> getSongsInDirectoryWithCache(File directory, String sortingMethod) {
		ArrayList<Song> songs = new ArrayList<Song>();
		File files[] = directory.listFiles(new AudioFileFilter());
		
		SongsDatabase songsDatabase = new SongsDatabase(this);
		final LinkedList<Song> songsToInsertInDB = new LinkedList<Song>();
		SQLiteDatabase db = songsDatabase.getWritableDatabase();
		
		for (File file : files) {
			String uri = file.getAbsolutePath();
			Song song;
			
			Cursor cursor = db.rawQuery("SELECT artist, title, trackNumber FROM Songs WHERE uri=\""+uri+"\"", null);
			if(cursor.moveToNext()) {
	        	Integer trackNumber = cursor.getInt(2);
	        	if(trackNumber==-1) trackNumber=null;
	        	song = new Song(uri, cursor.getString(0), cursor.getString(1), trackNumber);
	        } else {
	        	song = new Song(file.getAbsolutePath());
				songsToInsertInDB.add(song);
	        }
			songs.add(song);
		}
		db.close();
		
		Collections.sort(songs, new SongsComparator(sortingMethod));
		
		if(songsToInsertInDB.size()>0) {
			new Thread() {
				public void run() {
					SongsDatabase songsDatabase2 = new SongsDatabase(MusicService.this);
					SQLiteDatabase db2 = songsDatabase2.getWritableDatabase();
					for(Song song : songsToInsertInDB) {
						ContentValues values = new ContentValues();
						values.put("uri", song.getUri());
						values.put("artist", song.getArtist());
						values.put("title", song.getTitle());
						Integer trackNumber = song.getTrackNumber();
						if(trackNumber==null) trackNumber=-1;
						values.put("trackNumber", trackNumber);
						db2.insertWithOnConflict("Songs", null, values, SQLiteDatabase.CONFLICT_REPLACE);
					}
					db2.close();
				}
			}.start();
		}
		
		return songs;
	}
	
	// Lists all the subfolders of a given directory
	private ArrayList<File> getSubfoldersInDirectory(File directory) {
		ArrayList<File> subfolders = new ArrayList<File>();
		File files[] = directory.listFiles(new DirectoryFilter());
		for (File file : files) {
			subfolders.add(file);
		}
		Collections.sort(subfolders);
		return subfolders;
	}
}
