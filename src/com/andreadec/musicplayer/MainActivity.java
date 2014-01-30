/*
 * Copyright 2012-2014 Andrea De Cesare
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

import com.andreadec.musicplayer.adapters.*;
import com.readystatesoftware.systembartint.*;

import android.media.AudioManager;
import android.os.*;
import android.preference.*;
import android.support.v4.util.LruCache;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.app.*;
import android.text.TextUtils;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.*;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.view.*;
import android.view.WindowManager.LayoutParams;
import android.view.View.*;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.*;
import android.widget.SeekBar.*;

public class MainActivity extends FragmentActivity implements OnClickListener, OnSeekBarChangeListener {
	public final static int PAGE_BROWSER=0, PAGE_PLAYLISTS=1, PAGE_RADIOS=2, PAGE_PODCASTS=3;
	public ColorStateList defaultTextViewColor;
	
	private TextView textViewArtist, textViewTitle, textViewTime;
	private ImageButton imageButtonPrevious, imageButtonPlayPause, imageButtonNext, imageButtonToggleExtendedMenu;
	private SeekBar seekBar;
	private View extendedMenu;
	private ImageView imageViewSongImage;
	private ImageButton imageButtonShuffle, imageButtonRepeat, imageButtonRepeatAll;
	private Button buttonBassBoost, buttonEqualizer, buttonShake;
	private MusicService musicService; // The application service
	private Intent serviceIntent;
	private BroadcastReceiver broadcastReceiver;
	private SharedPreferences preferences;
	private View buttonQuit;
	
	private DrawerLayout drawerLayout;
	private RelativeLayout drawerContainer;
	private ListView drawerList;
	private NavigationDrawerArrayAdapter navigationAdapter;
	private ActionBarDrawerToggle drawerToggle;
	
	private static final int POLLING_INTERVAL = 450; // Refresh time of the seekbar
	private boolean pollingThreadRunning; // true if thread is active, false otherwise
	private boolean startPollingThread = true;
	private boolean showRemainingTime = false;
	private boolean showSongImage;
	
	// Variables used to reduce computing on polling thread
	private boolean isLengthAvailable = false;
	private String songDurationString = "";
	
	private String[] pages;
	private int currentPage = 0;
	private MusicPlayerFragment currentFragment;
	private FragmentManager fragmentManager;
	
	private LruCache<String,Bitmap> imagesCache;
	
	private String intentFile;
	
	
	
	/* Initializes the activity. */
    @SuppressLint({ "InlinedApi", "NewApi" })
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(preferences.getBoolean(Constants.PREFERENCE_DISABLELOCKSCREEN, Constants.DEFAULT_DISABLELOCKSCREEN)) {
        	getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD); // Disable lock screen for this activity
        }
        
        if(preferences.getBoolean(Constants.PREFERENCE_DARKTHEME, Constants.DEFAULT_DARKTHEME)) {
        	setTheme(R.style.DarkTheme);
        }
        
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        if(preferences.getBoolean(Constants.PREFERENCE_SHOWHELPOVERLAYMAINACTIVITY, true)) {
        	final FrameLayout frameLayout = new FrameLayout(this);
        	LayoutInflater layoutInflater = getLayoutInflater();
        	layoutInflater.inflate(R.layout.layout_main, frameLayout);
        	layoutInflater.inflate(R.layout.layout_helpoverlay_main, frameLayout);
        	final View overlayView = frameLayout.getChildAt(1);
        	overlayView.setOnClickListener(new OnClickListener() {
				@Override public void onClick(View v) {
					frameLayout.removeView(overlayView);
					SharedPreferences.Editor editor = preferences.edit();
					editor.putBoolean(Constants.PREFERENCE_SHOWHELPOVERLAYMAINACTIVITY, false);
					editor.commit();
				}
        	});
        	setContentView(frameLayout);
        } else {
        	setContentView(R.layout.layout_main);
        }
        
        pages = new String[5];
        pages[PAGE_BROWSER] = getResources().getString(R.string.browser);
        pages[PAGE_PLAYLISTS] = getResources().getString(R.string.playlist);
        pages[PAGE_RADIOS] = getResources().getString(R.string.radio);
        pages[PAGE_PODCASTS] = getResources().getString(R.string.podcasts);
        fragmentManager = getSupportFragmentManager();
        setTitle(pages[currentPage]);
        
        
        /* NAVIGATION DRAWER */
        drawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                setTitle(pages[currentPage]);
            }
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                setTitle(getResources().getString(R.string.app_name));
            }
        };
        drawerLayout.setDrawerListener(drawerToggle);
        if(Build.VERSION.SDK_INT>=11) getActionBar().setDisplayHomeAsUpEnabled(true);
        drawerContainer = (RelativeLayout)findViewById(R.id.navigation_container);
        drawerList = (ListView)findViewById(R.id.navigation_list);
        navigationAdapter = new NavigationDrawerArrayAdapter(this, pages);
        drawerList.setAdapter(navigationAdapter);
        drawerList.setOnItemClickListener(new ListView.OnItemClickListener() {
        	@Override
    	    public void onItemClick(@SuppressWarnings("rawtypes") AdapterView parent, View view, int position, long id) {
        		openPage(position);
        		drawerLayout.closeDrawer(drawerContainer);
    	    }
        });
        buttonQuit = findViewById(R.id.navigation_buttonQuit);
        buttonQuit.setOnClickListener(this);
        
        
        
        if(Build.VERSION.SDK_INT >= 19) {
        	// Android 4.4+ only
        	boolean translucentStatus = preferences.getBoolean(Constants.PREFERENCE_TRANSLUCENTSTATUSBAR, Constants.DEFAULT_TRANSLUCENTSTATUSBAR);
        	boolean translucentNavigation = preferences.getBoolean(Constants.PREFERENCE_TRANSLUCENTNAVIGATIONBAR, Constants.DEFAULT_TRANSLUCENTNAVIGATIONBAR);
        	
        	if(translucentStatus) getWindow().addFlags(LayoutParams.FLAG_TRANSLUCENT_STATUS);
        	if(translucentNavigation) getWindow().addFlags(LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            
        	SystemBarTintManager tintManager = new SystemBarTintManager(this);
            if(translucentStatus) {
            	tintManager.setStatusBarTintEnabled(true);
                tintManager.setStatusBarTintResource(R.color.actionBarBackground);
            }
        }
        
        
    	textViewArtist = (TextView)findViewById(R.id.textViewArtist);
        textViewTitle = (TextView)findViewById(R.id.textViewTitle);
        textViewTime = (TextView)findViewById(R.id.textViewTime);
        imageViewSongImage = (ImageView)findViewById(R.id.imageViewSongImage);
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
        
        defaultTextViewColor = textViewTitle.getTextColors();
        
        
        showSongImage = preferences.getBoolean(Constants.PREFERENCE_SHOWSONGIMAGE, Constants.DEFAULT_SHOWSONGIMAGE);
        imagesCache = new LruCache<String,Bitmap>(Constants.IMAGES_CACHE_SIZE);
        
        serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent); // Starts the service if it is not running
        
        openPage(PAGE_BROWSER);
        loadSongFromIntent();
    }
    
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerToggle.syncState();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
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
    	intentFilter.addAction("com.andreadec.musicplayer.podcastdownloadcompleted");
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            	if(intent.getAction().equals("com.andreadec.musicplayer.newsong")) {
            		updatePlayingItem();
            	} else if(intent.getAction().equals("com.andreadec.musicplayer.playpausechanged")) {
            		updatePlayPauseButton();
            	} else if(intent.getAction().equals("com.andreadec.musicplayer.podcastdownloadcompleted")) {
            		if(currentPage==PAGE_PODCASTS) {
            			PodcastsFragment podcastsFragment = (PodcastsFragment)currentFragment;
            			podcastsFragment.updateListView(true);
            		}
            	}

            }
        };
        registerReceiver(broadcastReceiver, intentFilter);
        updatePlayPauseButton();
    }
    
    @SuppressLint("NewApi")
	@Override
    public void setTitle(CharSequence title) {
    	super.setTitle(title);
    	if(Build.VERSION.SDK_INT>=11) {
    		getActionBar().setTitle(title);
    	}
    }
    
    private void openPage(int page) {
    	MusicPlayerFragment fragment;
    	switch(page) {
    	case PAGE_BROWSER:
    		fragment = new BrowserFragment();
    		break;
    	case PAGE_PLAYLISTS:
    		fragment = new PlaylistFragment();
    		break;
    	case PAGE_RADIOS:
    		fragment = new RadioFragment();
    		break;
    	case PAGE_PODCASTS:
    		fragment = new PodcastsFragment();
    		break;
    	default:
    		return;
    	}
    	currentPage = page;
    	currentFragment = fragment;
    	FragmentTransaction transaction = fragmentManager.beginTransaction();
    	transaction.remove(currentFragment);
    	transaction.replace(R.id.page, fragment);
    	transaction.addToBackStack(null);
    	transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    	transaction.commit();
    	fragmentManager.executePendingTransactions();
    	drawerList.setItemChecked(currentPage, true);
    	setTitle(pages[currentPage]);
    }
    
    /* Called before onStop or when the screen is switched off. */
    @Override
    public void onPause() {
    	super.onPause();
    	stopPollingThread(); // Stop the polling thread
    	unregisterReceiver(broadcastReceiver); // Disable broadcast receiver
    }
    
    /* Updates information about the current song. */
    private void updatePlayingItem() {
    	PlayableItem playingItem = musicService.getCurrentPlayingItem();
    	
    	String titleLines = preferences.getString(Constants.PREFERENCE_TITLELINES, Constants.DEFAULT_TITLELINES);
    	if(titleLines.equals("Auto")) {
    		textViewTitle.setMinLines(1);
    		textViewTitle.setMaxLines(5);
    	} else {
    		int lines = Integer.parseInt(titleLines);
    		textViewTitle.setLines(lines);
    		if(lines==1) {
    			textViewTitle.setSingleLine();
    			textViewTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
    			textViewTitle.setHorizontallyScrolling(true);
    			textViewTitle.setMarqueeRepeatLimit(-1);
    			textViewTitle.setSelected(true);
    		}
    	}
    	
    	if(playingItem!=null) {
    		// Song loaded
	    	textViewTitle.setText(playingItem.getTitle());
	    	textViewArtist.setText(playingItem.getArtist());
	    	seekBar.setMax(musicService.getDuration());
	    	seekBar.setProgress(musicService.getCurrentPosition());
	    	seekBar.setClickable(true);
	    	isLengthAvailable = playingItem.isLengthAvailable();
	    	if(isLengthAvailable) {
	    		songDurationString = "/" + Utils.formatTime(musicService.getDuration());
	    		seekBar.setVisibility(SeekBar.VISIBLE);
	    	} else {
	    		songDurationString = "";
	    		seekBar.setVisibility(SeekBar.GONE);
	    	}
	    	
	    	if(showSongImage) {
	    		Bitmap image = playingItem.getImage();
	    		if(image==null) {
	    			imageViewSongImage.setVisibility(View.GONE);
	    		} else {
	    			imageViewSongImage.setImageBitmap(image);
	    			imageViewSongImage.setVisibility(View.VISIBLE);
	    		}
	    	} else {
	    		imageViewSongImage.setVisibility(View.GONE);
	    	}
    	} else {
    		// No song loaded
    		textViewTitle.setText(R.string.noSong);
	    	textViewArtist.setText("");
    		seekBar.setMax(10);
	    	seekBar.setProgress(0);
	    	seekBar.setClickable(false);
	    	isLengthAvailable = true;
	    	songDurationString = "";
	    	seekBar.setVisibility(SeekBar.GONE);
	    	imageViewSongImage.setVisibility(View.GONE);
    	}
    	updatePlayPauseButton();
    	updatePosition();
    	
    	currentFragment.updateListView();
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
		if(showRemainingTime && isLengthAvailable) {
			time = "-" + Utils.formatTime(musicService.getDuration()-progress);
		} else {
			time = Utils.formatTime(progress);
		}
		time += songDurationString;
		textViewTime.setText(time);
	}
    
    /* Updates the shuffle/repeat/repeat_all icons according to the playing song */
    private void updateExtendedMenu() {
    	final int on = R.drawable.green_button;
    	final int off = R.drawable.orange_button;
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
    	updatePlayingItem();
    	updateExtendedMenu();
    	
    	// Opens the song from the intent, if necessary
    	if(intentFile!=null) {
    		BrowserSong song = new BrowserSong(intentFile);
    		playItem(song);
    	}
    	
    	// Starts the thread to update the seekbar and position information
    	if(startPollingThread) startPollingThread();
    }
    
    /* Manages songs opened from an external application */
    @Override
    protected void onNewIntent(Intent newIntent) {
    	setIntent(newIntent);
    	loadSongFromIntent();
    }
    private void loadSongFromIntent() {
    	Intent intent = getIntent();
    	if(intent!=null && intent.getAction()==Intent.ACTION_VIEW) {
        	try {
				intentFile = URLDecoder.decode(intent.getDataString(), "UTF-8");
				intentFile = intentFile.replace("file://", "");
			} catch (Exception e) {}
        }
    }
    
    /* Thread which updates song position polling the information from the service */
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
    	boolean navigationDrawerOpen = drawerLayout.isDrawerOpen(drawerContainer);
    	if(currentPage==PAGE_BROWSER && !navigationDrawerOpen) {
    		menu.findItem(R.id.menu_setAsBaseFolder).setVisible(true);
    		menu.findItem(R.id.menu_gotoBaseFolder).setVisible(true);
    	} else {
    		menu.findItem(R.id.menu_setAsBaseFolder).setVisible(false);
    		menu.findItem(R.id.menu_gotoBaseFolder).setVisible(false);
    	}
    	if(currentPage==PAGE_PODCASTS && !navigationDrawerOpen) {
    		menu.findItem(R.id.menu_removeDownloadedPodcasts).setVisible(true);
    	} else {
    		menu.findItem(R.id.menu_removeDownloadedPodcasts).setVisible(false);
    	}
    	if(navigationDrawerOpen || musicService==null || musicService.getCurrentPlayingItem()==null) {
    		menu.findItem(R.id.menu_songInfo).setVisible(false);
    	} else {
    		menu.findItem(R.id.menu_songInfo).setVisible(true);
    	}
    	return true;
    }
    
    /* A menu item has been selected. */
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
    	if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
          }
		switch (item.getItemId()) {
		case R.id.menu_gotoPlayingSongDirectory:
			gotoPlayingItemPosition();
			return true;
		case R.id.menu_gotoBaseFolder:
			((BrowserFragment)currentFragment).gotoBaseFolder();
			return true;
		case R.id.menu_search:
			if(preferences.getBoolean(Constants.PREFERENCE_ENABLECACHE, Constants.DEFAULT_ENABLECACHE)) {
				startActivityForResult(new Intent(this, SearchActivity.class), 1);
			} else {
				Utils.showMessageDialog(this, R.string.search, R.string.searchNotPossible);
			}
			return true;
		case R.id.menu_songInfo:
			showItemInfo(musicService.getCurrentPlayingItem());
			return true;
		case R.id.menu_setAsBaseFolder:
			setBaseFolder(((MusicPlayerApplication)getApplication()).getCurrentDirectory().getDirectory());
			return true;
		case R.id.menu_removeDownloadedPodcasts:
			((PodcastsFragment)currentFragment).removeDownloadedPodcasts();
			return true;
		case R.id.menu_preferences:
			startActivity(new Intent(this, PreferencesActivity.class));
			return true;
		case R.id.menu_quit:
			quitApplication();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
    
    @Override
	public boolean onContextItemSelected(MenuItem item) {
    	return currentFragment.onContextItemSelected(item);
    }

    /* Button click handler. */
	@Override
	public void onClick(View view) {
		if (view.equals(imageButtonPlayPause)) {
			musicService.playPause();
			updatePlayPauseButton();
		} else if(view.equals(imageButtonNext)) {
			musicService.nextItem();
		} else if(view.equals(imageButtonPrevious))  {
			musicService.previousItem(false);
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
				Utils.showMessageDialog(this, R.string.error, R.string.errorBassBoost);
			}
		} else if(view.equals(buttonEqualizer)) {
			if(musicService.getEqualizerAvailable()) {
				equalizerSettings();
			} else {
				Utils.showMessageDialog(this, R.string.error, R.string.errorEqualizer);
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
		} else if(view.equals(buttonQuit)) {
			quitApplication();
		}
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		// Called when SearchActivity returns
		super.onActivityResult(requestCode, resultCode, intent);
		if (resultCode == 1) { // If result code is 0, the user canceled the operation
			BrowserSong song = (BrowserSong)intent.getSerializableExtra("song");
			File songDirectory = new File(song.getPlayableUri()).getParentFile();
			BrowserDirectory browserDirectory = new BrowserDirectory(songDirectory);
			song.setBrowser(browserDirectory);
			playSongFromSearch(song);
			gotoPlayingItemPosition();
		}
	}
	
	/* ALWAYS CALL THIS FUNCTION TO COMPLETELY CLOSE THE APPLICATION */
	public void quitApplication() {
		stopService(serviceIntent); // Stop the service!
		finish();
	}
	
	public void playItem(PlayableItem item) {
		boolean ok = musicService.playItem(item);
		if(!ok) {
			Utils.showMessageDialog(this, R.string.errorSong, R.string.errorSongMessage);
		}
	}
	
	public void playSongFromSearch(BrowserSong song) {
		boolean ok = musicService.playItem(song);
		if (!ok) {
			Utils.showMessageDialog(this, R.string.errorSong, R.string.errorSongMessage);
		}
	}
	
	public void playRadio(Radio radio) {
		new PlayRadioTask(radio).execute();
	}
	
	public PlayableItem getCurrentPlayingItem() {
		if(musicService==null) return null;
		return musicService.getCurrentPlayingItem();
	}
	
	public void gotoPlayingItemPosition() {
		final PlayableItem playingItem = musicService.getCurrentPlayingItem();
		if(playingItem==null) return;
		if(playingItem instanceof BrowserSong) {
			openPage(PAGE_BROWSER);
		} else if(playingItem instanceof PlaylistSong) {
			openPage(PAGE_PLAYLISTS);
		} else if(playingItem instanceof Radio) {
			openPage(PAGE_RADIOS);
		} else if(playingItem instanceof PodcastItem) {
			openPage(PAGE_PODCASTS);
		}
		currentFragment.gotoPlayingItemPosition(playingItem);
	}
	
	public void setBaseFolder(final File folder) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.setAsBaseFolder);
		builder.setMessage(R.string.setBaseFolderConfirm);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
		      public void onClick(DialogInterface dialog, int which) {
		    	  saveBaseFolder(folder);
		      }
		});
		builder.setNegativeButton(R.string.no, null);
		builder.show();
	}
	
	private void saveBaseFolder(final File folder) {
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(Constants.PREFERENCE_BASEFOLDER, folder.getAbsolutePath());
		editor.commit();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.setAsBaseFolder);
		builder.setMessage(R.string.indexBaseFolderConfirm);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
		      public void onClick(DialogInterface dialog, int which) {
		    	  Intent indexIntent = new Intent(MainActivity.this, IndexFolderService.class);
		    	  indexIntent.putExtra("folder", folder.getAbsolutePath());
		    	  startService(indexIntent);
		      }
		});
		builder.setNegativeButton(R.string.no, null);
		builder.show();
	}
	
	/* Back button click handler. Overwrites default behaviour. */
	boolean backPressedOnce = false; // Necessary to implement double-tap-to-quit-app
	@Override
	public void onBackPressed() {
		if(backPressedOnce) {
			quitApplication();
			return;
		}
		boolean executed = currentFragment.onBackPressed();
		if(!executed && preferences.getBoolean(Constants.PREFERENCE_ENABLEBACKDOUBLEPRESSTOQUITAPP, Constants.DEFAULT_ENABLEBACKDOUBLEPRESSTOQUITAPP)) {
			backPressedOnce = true;
			Toast.makeText(this, R.string.pressAgainToQuitApp, Toast.LENGTH_SHORT).show();
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					backPressedOnce = false;   
				}
			}, 2000);
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
	@Override public void onStartTrackingTouch(SeekBar seekBar) {}
	@Override public void onStopTrackingTouch(SeekBar seekBar) {}
	
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
	
	
	
	private class PlayRadioTask extends AsyncTask<Void, Void, Boolean> {
		private ProgressDialog progressDialog;
		private Radio radio;
		public PlayRadioTask(Radio radio) {
			this.radio = radio;
			progressDialog = new ProgressDialog(MainActivity.this);
		}
		@Override
		protected void onPreExecute() {
	        progressDialog.setIndeterminate(true);
	        progressDialog.setCancelable(true);
	        progressDialog.setOnCancelListener(new OnCancelListener() {
				@Override
				public void onCancel(DialogInterface dialog) {
					cancel(true);
				}
			});
	        progressDialog.setCanceledOnTouchOutside(false);
	        progressDialog.setMessage(MainActivity.this.getString(R.string.loadingRadio, radio.getTitle()));
			progressDialog.show();
			startPollingThread = false;
			stopPollingThread(); // To prevent polling thread activation
	    }
		@Override
		protected Boolean doInBackground(Void... params) {
			return musicService.playItem(radio);
		}
		@Override
	    protected void onCancelled() {
			musicService.playItem(null);
	    }
		@Override
		protected void onPostExecute(final Boolean success) {
			updatePlayingItem();
			if(progressDialog.isShowing()) {
				progressDialog.dismiss();
	        }
			startPollingThread = true;
			if(!pollingThreadRunning) startPollingThread();
			
			if(!success) {
				Utils.showMessageDialog(MainActivity.this, R.string.errorWebRadio, R.string.errorWebRadioMessage);
			}
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
	
	public boolean getShowSongImage() {
		return showSongImage;
	}
	public LruCache<String,Bitmap> getImagesCache() {
		return imagesCache;
	}
	
	private void showItemInfo(PlayableItem item) {
		if(item==null || item.getInformation()==null) return;
		ArrayList<Information> information = item.getInformation();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.songInfo);
		View view = getLayoutInflater().inflate(R.layout.layout_songinfo, null, false);
		LinearLayout linearLayout = (LinearLayout)view.findViewById(R.id.linearLayoutInformation);
		
		Bitmap image = item.getImage();
		if(image!=null) {
			ImageView imageView = new ImageView(this);
			imageView.setImageBitmap(image);
			linearLayout.addView(imageView);
		}
		
		for(Information info : information) {
			TextView info1 = new TextView(this);
			info1.setTextAppearance(this, android.R.style.TextAppearance_Medium);
			info1.setText(getResources().getString(info.key));
			TextView info2 = new TextView(this);
			info2.setText(info.value);
			info2.setPadding(0, 0, 0, 10);
			linearLayout.addView(info1);
			linearLayout.addView(info2);
		}
		
		builder.setView(view);
		builder.setPositiveButton(R.string.ok, null);
		builder.show();
	}
}
