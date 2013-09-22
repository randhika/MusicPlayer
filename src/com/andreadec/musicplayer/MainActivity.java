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

import android.media.AudioManager;
import android.os.*;
import android.preference.*;
import android.support.v4.view.*;
import android.support.v4.app.*;
import android.app.*;
import android.content.*;
import android.content.DialogInterface.OnCancelListener;
import android.util.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.*;
import android.widget.SeekBar.*;

import com.andreadec.musicplayer.adapters.*;

public class MainActivity extends FragmentActivity implements OnClickListener, OnSeekBarChangeListener {
	private final static int PAGE_BROWSER=0, PAGE_PLAYLIST=1, PAGE_RADIO=2;
	
	private TextView textViewArtist, textViewTitle, textViewTime;
	private ImageButton imageButtonPrevious, imageButtonPlayPause, imageButtonNext, imageButtonToggleExtendedMenu;
	private SeekBar seekBar;
	private View extendedMenu;
	private ImageButton imageButtonShuffle, imageButtonRepeat, imageButtonRepeatAll;
	private Button buttonBassBoost, buttonEqualizer, buttonShake;
	private MusicService musicService; // The application service
	private Intent serviceIntent;
	private BroadcastReceiver broadcastReceiver;
	
	private SharedPreferences preferences;
	
	private static final int POLLING_INTERVAL = 450; // Refresh time of the seekbar
	private boolean pollingThreadRunning; // true if thread is active, false otherwise
	private boolean startPollingThread = true;
	private boolean showRemainingTime = false;
	
	// Variables used to reduce computing on polling thread
	private boolean isWebRadio = false;
	private String songDurationString = "";
	private float textViewTimeDefaultTextSize;
	
	private Playlist currentPlaylist = null;
	
	List<android.support.v4.app.Fragment> fragments;
	List<String> fragmentsTitles;
	private PagerAdapter pagerAdapter;
	private ViewPager viewPager;
	
	/* Initializes the activity. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(preferences.getBoolean("disableLockScreen", false)) {
        	getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD); // Disable lock screen for this activity
        }
        
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        if(preferences.getBoolean("showHelpOverlayMainActivity", true)) {
        	final FrameLayout frameLayout = new FrameLayout(this);
        	LayoutInflater layoutInflater = getLayoutInflater();
        	layoutInflater.inflate(R.layout.layout_main, frameLayout);
        	layoutInflater.inflate(R.layout.layout_helpoverlay_main, frameLayout);
        	final View overlayView = frameLayout.getChildAt(1);
        	overlayView.setOnClickListener(new OnClickListener() {
				@Override public void onClick(View v) {
					frameLayout.removeView(overlayView);
					SharedPreferences.Editor editor = preferences.edit();
					editor.putBoolean("showHelpOverlayMainActivity", false);
					editor.commit();
				}
        	});
        	setContentView(frameLayout);
        } else {
        	setContentView(R.layout.layout_main);
        }
        
    	textViewArtist = (TextView)findViewById(R.id.textViewArtist);
        textViewTitle = (TextView)findViewById(R.id.textViewTitle);
        textViewTime = (TextView)findViewById(R.id.textViewTime);
        imageButtonPrevious = (ImageButton)findViewById(R.id.imageButtonPrevious);
        imageButtonPlayPause = (ImageButton)findViewById(R.id.imageButtonPlayPause);
        imageButtonNext = (ImageButton)findViewById(R.id.imageButtonNext);
        imageButtonToggleExtendedMenu = (ImageButton)findViewById(R.id.imageButtonToggleExtendedMenu);
        seekBar = (SeekBar)findViewById(R.id.seekBar);
        extendedMenu = findViewById(R.id.extendedMenu);
        imageButtonShuffle = (ImageButton)findViewById(R.id.imageButtonShuffle);
        imageButtonRepeat = (ImageButton)findViewById(R.id.imageButtonRepeat);
        imageButtonRepeatAll = (ImageButton)findViewById(R.id.imageButtonRepeatAll);
        buttonBassBoost = (Button)findViewById(R.id.buttonBassBoost);
        buttonEqualizer = (Button)findViewById(R.id.buttonEqualizer);
        buttonShake = (Button)findViewById(R.id.buttonShake);
        
        imageButtonShuffle.setOnClickListener(this);
        imageButtonRepeat.setOnClickListener(this);
        imageButtonRepeatAll.setOnClickListener(this);
        buttonBassBoost.setOnClickListener(this);
        buttonEqualizer.setOnClickListener(this);
        buttonShake.setOnClickListener(this);
        
        imageButtonPrevious.setOnClickListener(this);
        imageButtonPlayPause.setOnClickListener(this);
        imageButtonNext.setOnClickListener(this);
        imageButtonToggleExtendedMenu.setOnClickListener(this);
        seekBar.setOnSeekBarChangeListener(this);
        seekBar.setClickable(false);
        textViewTime.setOnClickListener(this);
        textViewTimeDefaultTextSize = textViewTime.getTextSize();
        
        fragments = new Vector<android.support.v4.app.Fragment>();
    	fragments.add(android.support.v4.app.Fragment.instantiate(this, BrowserFragment.class.getName()));
        fragments.add(android.support.v4.app.Fragment.instantiate(this, PlaylistFragment.class.getName()));
        fragments.add(android.support.v4.app.Fragment.instantiate(this, RadioFragment.class.getName()));
        fragmentsTitles = new Vector<String>();
        fragmentsTitles.add(getResources().getString(R.string.browser));
        fragmentsTitles.add(getResources().getString(R.string.playlist));
        fragmentsTitles.add(getResources().getString(R.string.radio));
        
        pagerAdapter = new PagerAdapter(getSupportFragmentManager(), fragments, fragmentsTitles);
        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(pagerAdapter);
        
        serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent); // Starts the service if it is not running
    }
    
    /* Activity comes foreground */
    @Override
    public void onStart() {
    	super.onStart();
    	// The service is bound to this activity
    	if(musicService==null) bindService(serviceIntent, musicConnection, Context.BIND_AUTO_CREATE);
    }
    
    /* Called after onStart or when the screen is switched on. */
    @Override
    public void onResume() {
    	super.onResume();
    	if (musicService!=null) startRoutine();
    	
    	// Enable the broadcast receiver
    	IntentFilter intentFilter = new IntentFilter();
    	intentFilter.addAction("com.andreadec.musicplayer.newsong");
    	intentFilter.addAction("com.andreadec.musicplayer.playpausechanged");
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            	if(intent.getAction().equals("com.andreadec.musicplayer.newsong")) {
            		updatePlayingSong();
            	} else if(intent.getAction().equals("com.andreadec.musicplayer.playpausechanged")) {
            		updatePlayPauseButton();
            	}

            }
        };
        registerReceiver(broadcastReceiver, intentFilter);
        
        updatePlayPauseButton();
    }
    
    /* Called before onStop or when the screen is switched off. */
    @Override
    public void onPause() {
    	super.onPause();
    	stopPollingThread(); // Stop the polling thread
    	unregisterReceiver(broadcastReceiver); // Disable broadcast receiver
    }
    
    public SongsArrayAdapter getBrowserAdapter() {
    	if(musicService==null) return null;
    	ArrayList<File> browsingSubdirs = musicService.getBrowsingSubdirs();
        ArrayList<Song> browsingSongs = musicService.getBrowsingSongs();
        ArrayList<Object> items = new ArrayList<Object>();
        items.add(".."); // Add the button to come back to the parent directory
        items.addAll(browsingSubdirs);
        items.addAll(browsingSongs);
        Song playingSong = musicService.getPlayingSong();
        SongsArrayAdapter songsArrayAdapter = new SongsArrayAdapter(this, items, playingSong, musicService.getBrowsingDir().getAbsolutePath());
        return songsArrayAdapter;
    }
    
    /* Updates the files' list. */
    private void updateListViewBrowser(boolean restoreOldPosition) {
    	ArrayList<File> browsingSubdirs = musicService.getBrowsingSubdirs();
        ArrayList<Song> browsingSongs = musicService.getBrowsingSongs();
        ArrayList<Object> items = new ArrayList<Object>();
        items.add(".."); // Add the button to come back to the parent directory
        items.addAll(browsingSubdirs);
        items.addAll(browsingSongs);
        Song playingSong = null;
        if(!musicService.getPlayingPlaylist()) playingSong = musicService.getPlayingSong();
        SongsArrayAdapter songsArrayAdapter = new SongsArrayAdapter(this, items, playingSong, musicService.getBrowsingDir().getAbsolutePath());
        
        BrowserFragment browserFragment = (BrowserFragment)pagerAdapter.getItem(PAGE_BROWSER);
        browserFragment.setSongsArrayAdapter(songsArrayAdapter);
        browserFragment.updateListView(restoreOldPosition);
    }
    
    /* Updates information about the current song. */
    private void updatePlayingSong() {
    	Song playingSong = musicService.getPlayingSong();
    	
    	String titleLines = preferences.getString("titleLines", "2");
    	if(titleLines.equals("Auto")) {
    		textViewTitle.setMinLines(1);
    		textViewTitle.setMaxLines(5);
    	} else {
    		textViewTitle.setLines(Integer.parseInt(titleLines));
    	}
    	
    	if(playingSong!=null) {
    		// Song loaded
	    	textViewTitle.setText(playingSong.getTitle());
	    	textViewArtist.setText(playingSong.getArtist());
	    	seekBar.setMax(musicService.getDuration());
	    	seekBar.setProgress(musicService.getCurrentPosition());
	    	seekBar.setClickable(true);
	    	isWebRadio = playingSong.isWebRadio();
	    	if(isWebRadio) {
	    		songDurationString = "";
	    		seekBar.setVisibility(SeekBar.GONE);
	    	} else {
	    		songDurationString = "/" + formatTime(musicService.getDuration());
	    		
	    		// Reduce time font size to avoid overflow on small screens
	    		if(musicService.getDuration()>3600000) { // 1 hour
	    			textViewTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, textViewTimeDefaultTextSize-5);
	    		} else {
	    			textViewTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, textViewTimeDefaultTextSize);
	    		}
	    		
	    		seekBar.setVisibility(SeekBar.VISIBLE);
	    	}
    	} else {
    		// No song loaded
    		textViewTitle.setText(R.string.noSong);
	    	textViewArtist.setText("");
    		seekBar.setMax(10);
	    	seekBar.setProgress(0);
	    	seekBar.setClickable(false);
	    	isWebRadio = false;
	    	songDurationString = "";
	    	seekBar.setVisibility(SeekBar.GONE);
    	}
    	updatePlayPauseButton();
    	updatePosition();
    	updateListViewBrowser(true);
    	updateListViewPlaylist();
    }
    
    /* Updates the play/pause button status according to the playing song. */
    private void updatePlayPauseButton() {
    	if (musicService!=null && musicService.isPlaying()) imageButtonPlayPause.setImageResource(R.drawable.pause);
		else imageButtonPlayPause.setImageResource(R.drawable.play);
    }
    
    /* Updates the seekbar and the position information according to the playing song. */
    private void updatePosition() {
		int progress = musicService.getCurrentPosition();
		seekBar.setProgress(progress);
		String time;
		if(showRemainingTime && !isWebRadio) {
			time = "-" + formatTime(musicService.getDuration()-progress);
		} else {
			time = formatTime(progress);
		}
		time += songDurationString;
		textViewTime.setText(time);
	}
    
    /* Updates the shuffle/repeat/repeat_all icons according to the playing song */
    private void updateExtendedMenu() {
    	int on = R.drawable.green_button;
    	int off = R.drawable.blue_button;
    	
    	if(musicService.getShuffle()) imageButtonShuffle.setBackgroundResource(on);
    	else imageButtonShuffle.setBackgroundResource(off);
    	if(musicService.getRepeat()) imageButtonRepeat.setBackgroundResource(on);
    	else imageButtonRepeat.setBackgroundResource(off);
    	if(musicService.getRepeatAll()) imageButtonRepeatAll.setBackgroundResource(on);
    	else imageButtonRepeatAll.setBackgroundResource(off);
    	if(musicService.getBassBoostEnabled()) buttonBassBoost.setBackgroundResource(on);
    	else buttonBassBoost.setBackgroundResource(off);
    	if(musicService.getEqualizerEnabled()) buttonEqualizer.setBackgroundResource(on);
    	else buttonEqualizer.setBackgroundResource(off);
    	if(musicService.isShakeEnabled()) buttonShake.setBackgroundResource(on);
    	else buttonShake.setBackgroundResource(off);
    }
    
    /* Called after the service has been bounded. */
    private void startRoutine() {
    	/* If the service is already ready, startRoutineExec is executed directly,
    	 * otherwise a loading dialog is showed while waiting for the service to be ready.
    	 */
    	if(!musicService.isReady()) new StartRoutineTask().execute();
    	else startRoutineExec();
    }
    
    private void startRoutineExec() {
    	updatePlayingSong();
    	updateExtendedMenu();
    	
    	// Starts the thread to update the seekbar and position information
    	if(startPollingThread) startPollingThread();
    }
    
    private void startPollingThread() {
    	pollingThreadRunning = true;
        new Thread() {
        	public void run() {
        		while(pollingThreadRunning) {
        			runOnUiThread(new Runnable() {
						public void run() {
							updatePosition();
						}
					});
        			try{ Thread.sleep(POLLING_INTERVAL); } catch(Exception e) {}
        		}
        	}
        }.start();
    }
    
    private void stopPollingThread() {
    	pollingThreadRunning = false;
    }

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	unbindService(musicConnection); // Unbinds from the service
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	if(viewPager.getCurrentItem()==PAGE_RADIO) {
    		menu.findItem(R.id.menu_addRadio).setVisible(true);
    	} else {
    		menu.findItem(R.id.menu_addRadio).setVisible(false);
    	}
    	if(viewPager.getCurrentItem()==PAGE_BROWSER) {
    		menu.findItem(R.id.menu_setAsBaseFolder).setVisible(true);
    	} else {
    		menu.findItem(R.id.menu_setAsBaseFolder).setVisible(false);
    	}
    	return true;
    }
    
    /* A menu item has been selected. */
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_addRadio:
			((RadioFragment)pagerAdapter.getItem(PAGE_RADIO)).newRadio();
			return true;
		case R.id.menu_newPlaylist:
			editPlaylist(null);
			return true;
		case R.id.menu_gotoPlayingSongDirectory:
			gotoPlayingSongDirectory();
			return true;
		case R.id.menu_search:
			if(preferences.getBoolean("enableCache", true)) {
				startActivityForResult(new Intent(this, SearchActivity.class), 1);
			} else {
				Dialogs.showMessageDialog(this, R.string.search, R.string.searchNotPossible);
			}
			return true;
		case R.id.menu_songInfo:
			showSongInfo(musicService.getPlayingSong());
			return true;
		case R.id.menu_setAsBaseFolder:
			setBaseFolder(musicService.getBrowsingDir());
			return true;
		case R.id.menu_preferences:
			Intent intentPreferences = new Intent(this, PreferencesActivity.class);
			startActivity(intentPreferences);
			return true;
		case R.id.menu_quit: // Close the application
			quitApplication();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
    
    @Override
	public boolean onContextItemSelected(MenuItem item) {
    	if(viewPager.getCurrentItem()==PAGE_BROWSER) {
    		BrowserFragment browserFragment = (BrowserFragment)pagerAdapter.getItem(PAGE_BROWSER);
    		return browserFragment.onContextItemSelectedBrowser(item);
    	} else if(viewPager.getCurrentItem()==PAGE_PLAYLIST) {
    		PlaylistFragment playlistFragment = (PlaylistFragment)pagerAdapter.getItem(PAGE_PLAYLIST);
    		return playlistFragment.onContextItemSelectedPlaylist(item);
    	} else if(viewPager.getCurrentItem()==PAGE_RADIO) {
    		RadioFragment radioFragment = (RadioFragment)pagerAdapter.getItem(PAGE_RADIO);
    		return radioFragment.onContextItemSelectedRadio(item);
    	}
    	return false;
    }

    /* Button click handler. */
	@Override
	public void onClick(View view) {
		if (view.equals(imageButtonPlayPause)) {
			musicService.playPause();
			updatePlayPauseButton();
		} else if(view.equals(imageButtonNext)) {
			musicService.nextSong();
		} else if(view.equals(imageButtonPrevious))  {
			musicService.previousSong();
		} else if(view.equals(textViewTime)) {
			showRemainingTime = !showRemainingTime;
		} else if(view.equals(imageButtonShuffle)) {
			musicService.setShuffle(!musicService.getShuffle());
			updateExtendedMenu();
		} else if(view.equals(imageButtonRepeat)) {
			musicService.setRepeat(!musicService.getRepeat());
			updateExtendedMenu();
		} else if(view.equals(imageButtonRepeatAll)) {
			musicService.setRepeatAll(!musicService.getRepeatAll());
			updateExtendedMenu();
		} else if(view.equals(buttonBassBoost)) {
			if(musicService.getBassBoostAvailable()) {
				bassBoostSettings();
			} else {
				Dialogs.showMessageDialog(this, R.string.error, R.string.errorBassBoost);
			}
		} else if(view.equals(buttonEqualizer)) {
			if(musicService.getEqualizerAvailable()) {
				equalizerSettings();
			} else {
				Dialogs.showMessageDialog(this, R.string.error, R.string.errorEqualizer);
			}
		} else if(view.equals(buttonShake)) {
			musicService.toggleShake();
			updateExtendedMenu();
		} else if(view.equals(imageButtonToggleExtendedMenu)) {
			if(extendedMenu.getVisibility()==View.VISIBLE) {
				extendedMenu.setVisibility(View.GONE);
				imageButtonToggleExtendedMenu.setImageResource(R.drawable.expand);
			} else {
				extendedMenu.setVisibility(View.VISIBLE);
				imageButtonToggleExtendedMenu.setImageResource(R.drawable.collapse);
			}
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// Called when SearchActivity returns
		super.onActivityResult(requestCode, resultCode, intent);
		if (resultCode == 1) { // If result code is 0, the user canceled the operation
			Song song = (Song) intent.getSerializableExtra("song");
			playSongFromSearch(song);
			gotoPlayingSongDirectory();
		}
	}
	
	private void quitApplication() {
		stopService(serviceIntent); // Stop the service!
		finish();
	}
	
	public void playSong(Song song, boolean fromPlaylist) {
		boolean ok;
		if(fromPlaylist) {
			ok = musicService.playSongFromPlaylist((PlaylistSong)song, true);
		} else {
			ok = musicService.playSongFromBrowser(song);
		}
		if (!ok) {
			Dialogs.showMessageDialog(this, R.string.errorSong, R.string.errorSongMessage);
		}
		updateListViewBrowser(true);
		updateListViewPlaylist();
	}
	
	public void playSongFromSearch(Song song) {
		boolean ok = musicService.playSongFromSearch(song);
		if (!ok) {
			Dialogs.showMessageDialog(this, R.string.errorSong, R.string.errorSongMessage);
		}
	}
	
	public void playRadio(Song song) {
		new PlayRadioTask(song).execute();
	}
	
	public void editPlaylist(final Playlist playlist) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		int title = playlist==null ? R.string.newPlaylist : R.string.editPlaylist;
		builder.setTitle(getResources().getString(title));
		final View view = getLayoutInflater().inflate(R.layout.layout_editplaylist, null);
		builder.setView(view);
		
		final EditText editTextName = (EditText)view.findViewById(R.id.editTextPlaylistName);
		if(playlist!=null) editTextName.setText(playlist.getName());
		
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				String name = editTextName.getText().toString();
				if(name==null || name.equals("")) {
					Dialogs.showMessageDialog(MainActivity.this, R.string.error, R.string.errorPlaylistName);
					return;
				}
				if(playlist==null) {
					addPlaylist(name);
				} else {
					playlist.editName(name);
					updateListViewPlaylist();
				}
			}
		});
		builder.setNegativeButton(R.string.cancel, null);
		AlertDialog dialog = builder.create();
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		dialog.show();
	}
	
	public void showPlaylistsList() {
		currentPlaylist = null;
		updateListViewPlaylist();
	}
	
	public void showPlaylist(Playlist playlist) {
		if(playlist.equals(currentPlaylist)) return;
		currentPlaylist = playlist;
		updateListViewPlaylist();
	}
	
	public Playlist addPlaylist(String name) {
		Playlist playlist = musicService.addPlaylist(name);
		updateListViewPlaylist();
		return playlist;
	}
	
	public void deletePlaylist(final Playlist playlist) {
		AlertDialog dialog = new AlertDialog.Builder(this).create();
		dialog.setTitle(R.string.delete);
		dialog.setMessage(getResources().getString(R.string.deletePlaylistConfirm));
		dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
		      public void onClick(DialogInterface dialog, int which) {
		    	  musicService.deletePlaylist(playlist);
		    	  updateListViewPlaylist();
		      }
		});
		dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getString(R.string.no), new DialogInterface.OnClickListener() {public void onClick(DialogInterface dialog, int which) {}});
		dialog.show();
	}
	
	public void addSongToPlaylist(Playlist playlist, Song song) {
		playlist.addSong(song);
		updateListViewPlaylist();
	}
	
	public void addFolderToPlaylist(Playlist playlist, File folder) {
		new AddFolderToPlaylistTask(playlist, folder).execute();
	}
	
	public void deleteSongFromPlaylist(PlaylistSong song) {
		song.getPlaylist().deleteSong(song);
		updateListViewPlaylist();
	}
	
	public ArrayList<Playlist> getPlaylists() {
		return musicService.getPlaylists();
	}
	
	public void sortPlaylist(int from, int to) {
		if(currentPlaylist==null) { // Sorting playlists
			musicService.sortPlaylists(from, to);
		} else { // Sorting songs in playlist
			if(to==0) return; // The user is trying to put the song above the button to go back to the playlists' list
			currentPlaylist.sort(from-1, to-1); // -1 is due to first element being link to previous folder
		}
		updateListViewPlaylist();
	}
	
	public void updateListViewPlaylist() {
		Song playingSong = null;
        if(musicService.getPlayingPlaylist()) playingSong = musicService.getPlayingSong();
		ArrayList<Object> values = new ArrayList<Object>();
		if(currentPlaylist==null) { // Show all playlists
			ArrayList<Playlist> playlists = getPlaylists();
			values.addAll(playlists);
		} else {
			values.add(new String(currentPlaylist.getName()));
			values.addAll(currentPlaylist.getSongs());
		}
		PlaylistArrayAdapter playlistArrayAdapter = new PlaylistArrayAdapter(this, values, playingSong);
		PlaylistFragment playlistFragment = (PlaylistFragment)pagerAdapter.getItem(PAGE_PLAYLIST);
		playlistFragment.setArrayAdapter(playlistArrayAdapter);
		playlistFragment.updateListView();
	}
	
	/* Moves to the parent directory. Shows a "toast" if an error occurs. */
	public void gotoParentDir() {
		File currentDir = musicService.getBrowsingDir();
		final File parentDir = currentDir.getParentFile();
		String baseDirectory = preferences.getString(MusicService.PREFERENCE_BASEFOLDER, null);
		
		if(baseDirectory!=null && new File(baseDirectory).equals(currentDir)) {
			AlertDialog dialog = new AlertDialog.Builder(this).create();
			dialog.setTitle(R.string.baseFolderReachedTitle);
			dialog.setMessage(getResources().getString(R.string.baseFolderReachedMessage));
			dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					new ChangeDirTask(parentDir, null).execute();
				}
			});
			dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getString(R.string.no), new DialogInterface.OnClickListener() {public void onClick(DialogInterface dialog, int which) {}});
			dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getResources().getString(R.string.quitApp), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					quitApplication();
				}
			});
			dialog.show();
		} else {
			new ChangeDirTask(parentDir, null).execute();
		}
	}
	
	public void gotoDirectory(File newDirectory, Song scrollToSong) {
		new ChangeDirTask(newDirectory, scrollToSong).execute();
	}
	
	public void gotoPlayingSongDirectory() {
		Song playingSong = musicService.getPlayingSong();
		if(playingSong==null) return;
		
		if(playingSong.isWebRadio()) {
			viewPager.setCurrentItem(PAGE_RADIO);
			return;
		}
		
		if(playingSong instanceof PlaylistSong) { // A playlist is being played
			showPlaylist(((PlaylistSong)playingSong).getPlaylist());
			viewPager.setCurrentItem(PAGE_PLAYLIST);
			scrollToSong(playingSong);
		} else { // A file from browser is being played
			File songDirectory = new File(playingSong.getUri()).getParentFile();
			if(!songDirectory.equals(musicService.getBrowsingDir())) {
				gotoDirectory(songDirectory, playingSong);
			} else {
				scrollToSong(playingSong);
			}
			viewPager.setCurrentItem(PAGE_BROWSER);
		}
	}
	
	private void scrollToSong(final Song song) {
		new Thread() {
			public void run() {
				runOnUiThread(new Runnable() {
					public void run() {
						if(song instanceof PlaylistSong) {
							PlaylistFragment playlistFragment = (PlaylistFragment)pagerAdapter.getItem(PAGE_PLAYLIST);
							playlistFragment.scrollToSong((PlaylistSong)song);
						} else {
							BrowserFragment browserFragment = (BrowserFragment)pagerAdapter.getItem(PAGE_BROWSER);
							browserFragment.scrollToSong(song);
						}
					}
				});
			}
		}.start();
	}
	
	public void setBaseFolder(final File folder) {
		AlertDialog dialog = new AlertDialog.Builder(this).create();
		dialog.setTitle(R.string.setAsBaseFolder);
		dialog.setMessage(getResources().getString(R.string.setBaseFolderConfirm));
		dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
		      public void onClick(DialogInterface dialog, int which) {
		    	  saveBaseFolder(folder);
		      }
		});
		dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getString(R.string.no), new DialogInterface.OnClickListener() {public void onClick(DialogInterface dialog, int which) {}});
		dialog.show();
	}
	
	private void saveBaseFolder(final File folder) {
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(MusicService.PREFERENCE_BASEFOLDER, folder.getAbsolutePath());
		editor.commit();
		
		AlertDialog dialog = new AlertDialog.Builder(this).create();
		dialog.setTitle(R.string.setAsBaseFolder);
		dialog.setMessage(getResources().getString(R.string.indexBaseFolderConfirm));
		dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getString(R.string.yes), new DialogInterface.OnClickListener() {
		      public void onClick(DialogInterface dialog, int which) {
		    	  Intent indexIntent = new Intent(MainActivity.this, IndexFolderService.class);
		    	  indexIntent.putExtra("folder", folder.getAbsolutePath());
		    	  startService(indexIntent);
		      }
		});
		dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getResources().getString(R.string.no), new DialogInterface.OnClickListener() {public void onClick(DialogInterface dialog, int which) {}});
		dialog.show();
	}
	
	/* Back button click handler. Overwrites default behaviour. */
	@Override
	public void onBackPressed() {
		if(viewPager.getCurrentItem()==PAGE_BROWSER) {
			gotoParentDir();
			updateListViewBrowser(false);
		} else if(viewPager.getCurrentItem()==PAGE_PLAYLIST) {
			if(currentPlaylist!=null) showPlaylistsList();
		}
	}

	/* Seekbar click handler. */
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if(fromUser) { // Event is triggered only if the seekbar position was modified by the user
			musicService.seekTo(progress);
			updatePosition();
		}
	}
	
	@Override public void onStartTrackingTouch(SeekBar arg0) {}
	@Override public void onStopTrackingTouch(SeekBar arg0) {}
	
	private ServiceConnection musicConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			musicService = ((MusicService.MusicBinder)service).getService();
			startRoutine();
		}
		@Override
		public void onServiceDisconnected(ComponentName className) {
			musicService = null;
		}
	};
	
	private class ChangeDirTask extends AsyncTask<Void, Void, Boolean> {
		private File newDirectory;
		private Song gotoSong;
		public ChangeDirTask(File newDirectory, Song gotoSong) {
			this.newDirectory = newDirectory;
			this.gotoSong = gotoSong;
		}
		@Override
		protected void onPreExecute() {
			setProgressBarIndeterminateVisibility(true);
	    }
		@Override
		protected Boolean doInBackground(Void... params) {
			if (newDirectory!=null && newDirectory.canRead()) {
				musicService.gotoDirectory(newDirectory);
				return true;
			} else {
				return false;
			}
		}
		@Override
		protected void onPostExecute(final Boolean success) {
			if(success) {
				updateListViewBrowser(false);
				updateListViewPlaylist();
				if(gotoSong!=null) {
					scrollToSong(gotoSong);
				}
			} else {
				Toast.makeText(MainActivity.this, R.string.dirError, Toast.LENGTH_SHORT).show();
			}
			setProgressBarIndeterminateVisibility(false);
		}
	}
	
	private class PlayRadioTask extends AsyncTask<Void, Void, Boolean> {
		private ProgressDialog progressDialog;
		private Song song;
		public PlayRadioTask(Song song) {
			this.song = song;
			progressDialog = new ProgressDialog(MainActivity.this);
		}
		@Override
		protected void onPreExecute() {
	        progressDialog.setIndeterminate(true);
	        progressDialog.setCancelable(false);
	        progressDialog.setMessage(MainActivity.this.getString(R.string.loadingRadio, song.getTitle()));
			progressDialog.show();
			startPollingThread = false;
			stopPollingThread(); // To prevent polling thread activation
	    }
		@Override
		protected Boolean doInBackground(Void... params) {
			return musicService.playWebRadio(song);
		}
		@Override
		protected void onPostExecute(final Boolean success) {
			updatePlayingSong();
			if(progressDialog.isShowing()) {
				progressDialog.dismiss();
	        }
			startPollingThread = true;
			if(!pollingThreadRunning) startPollingThread();
			
			if(!success) {
				Dialogs.showMessageDialog(MainActivity.this, R.string.errorWebRadio, R.string.errorWebRadioMessage);
			}
		}
	}
	
	private String formatTime(int milliseconds) {
		String ret = "";
		int seconds = (int) (milliseconds / 1000) % 60 ;
		int minutes = (int) ((milliseconds / (1000*60)) % 60);
		int hours   = (int) ((milliseconds / (1000*60*60)) % 24);
		if(hours>0) ret += hours+":";
		ret += minutes<10 ? "0"+minutes+":" : minutes+":";
		ret += seconds<10 ? "0"+seconds : seconds+"";
		return ret;
	}
	
	private class StartRoutineTask extends AsyncTask<Void, Void, Void> {
		private ProgressDialog progressDialog;
		@Override
		protected void onPreExecute() {
			progressDialog = new ProgressDialog(MainActivity.this);
	        progressDialog.setIndeterminate(true);
	        progressDialog.setCancelable(false);
	        progressDialog.setMessage(MainActivity.this.getString(R.string.loading));
			progressDialog.show();
	    }
		@Override
		protected Void doInBackground(Void... params) {
			musicService.lock.lock();
			while(!musicService.isReady()) {
				try{musicService.condition.await();}catch(Exception e) {}
			}
			musicService.lock.unlock();
			return null;
		}
		@Override
		protected void onPostExecute(final Void success) {
			startRoutineExec();
			if(progressDialog.isShowing()) progressDialog.dismiss();
		}
	}
	
	
	
	private class AddFolderToPlaylistTask extends AsyncTask<Void, Void, Void> {
		private ProgressDialog progressDialog;
		private Playlist playlist;
		private File folder;
		public AddFolderToPlaylistTask(Playlist playlist, File folder) {
			this.playlist = playlist;
			this.folder = folder;
		}
		@Override
		protected void onPreExecute() {
			progressDialog = new ProgressDialog(MainActivity.this);
	        progressDialog.setIndeterminate(true);
	        progressDialog.setCancelable(false);
	        progressDialog.setMessage(MainActivity.this.getString(R.string.addingSongsToPlaylist));
			progressDialog.show();
	    }
		@Override
		protected Void doInBackground(Void... params) {
			List<Song> songs = musicService.getSongsInDirectory(folder);
			for(Song song : songs) {
				playlist.addSong(song);
			}
			return null;
		}
		@Override
		protected void onPostExecute(final Void success) {
			updateListViewPlaylist();
			if(progressDialog.isShowing()) progressDialog.dismiss();
		}
	}
	
	
	private void bassBoostSettings() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.bassBoost);
		View view = getLayoutInflater().inflate(R.layout.layout_bassboost, null);
		builder.setView(view);
		
		builder.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				updateExtendedMenu();
			}
		});
		builder.setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				updateExtendedMenu();
			}
		});
		
		CheckBox checkBoxBassBoostEnable = (CheckBox)view.findViewById(R.id.checkBoxBassBoostEnabled);
		checkBoxBassBoostEnable.setChecked(musicService.getBassBoostEnabled());
		checkBoxBassBoostEnable.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				musicService.toggleBassBoost();
				updateExtendedMenu();
			}
		});
		
		SeekBar seekbar = (SeekBar)view.findViewById(R.id.seekBarBassBoostStrength);
		seekbar.setMax(1000);
		seekbar.setProgress(musicService.getBassBoostStrength());
		seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if(fromUser) {
					musicService.setBassBoostStrength(seekBar.getProgress());
				}
			}
			@Override public void onStartTrackingTouch(SeekBar arg0) {}
			@Override public void onStopTrackingTouch(SeekBar arg0) {}
		});
		
		builder.show();
	}
	
	private void equalizerSettings() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.equalizer);
		View view = getLayoutInflater().inflate(R.layout.layout_equalizer, null);
		builder.setView(view);
		
		builder.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				updateExtendedMenu();
			}
		});
		builder.setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				updateExtendedMenu();
			}
		});
		
		CheckBox checkBoxEqualizerEnabled = (CheckBox)view.findViewById(R.id.checkBoxEqualizerEnabled);
		checkBoxEqualizerEnabled.setChecked(musicService.getEqualizerEnabled());
		checkBoxEqualizerEnabled.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				musicService.toggleEqualizer();
				updateExtendedMenu();
			}
		});
		
		String[] availablePresets = musicService.getEqualizerAvailablePresets();
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, availablePresets);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		Spinner spinnerEqualizerPreset = (Spinner)view.findViewById(R.id.spinnerEqualizerPreset);
		spinnerEqualizerPreset.setAdapter(adapter);
		spinnerEqualizerPreset.setSelection(musicService.getEqualizerPreset());
		
		spinnerEqualizerPreset.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				musicService.setEqualizerPreset(position);
			}
			@Override public void onNothingSelected(AdapterView<?> parent) {}
		});
		builder.show();
	}
	
	private void showSongInfo(Song song) {
		if(song==null) return;
		AlertDialog dialog = new AlertDialog.Builder(this).create();
		dialog.setTitle(R.string.songInfo);
		View view = getLayoutInflater().inflate(R.layout.layout_songinfo, null, false);
		TextView textViewSongInfoArtist = (TextView)view.findViewById(R.id.textViewSongInfoArtist);
		TextView textViewSongInfoTitle = (TextView)view.findViewById(R.id.textViewSongInfoTitle);
		TextView textViewSongInfoFileName = (TextView)view.findViewById(R.id.textViewSongInfoFileName);
		TextView textViewSongInfoTrackNumber = (TextView)view.findViewById(R.id.textViewSongInfoTrackNumber);
		TextView textViewSongInfoFileSize = (TextView)view.findViewById(R.id.textViewSongInfoFileSize);
		textViewSongInfoArtist.setText(song.getArtist());
		textViewSongInfoTitle.setText(song.getTitle());
		if(song.getTrackNumber()!=null) {
			textViewSongInfoTrackNumber.setText(song.getTrackNumber().toString());
		} else {
			textViewSongInfoTrackNumber.setText("-");
		}
		textViewSongInfoFileName.setText(song.getUri());
		if(song.isWebRadio()) {
			
		} else {
			textViewSongInfoFileSize.setText(song.getFileSize());
		}
		dialog.setView(view);
		dialog.setButton(AlertDialog.BUTTON_POSITIVE, getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {public void onClick(DialogInterface dialog, int which) {}});
		dialog.show();
	}
}
