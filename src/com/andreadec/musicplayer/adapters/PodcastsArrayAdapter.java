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

package com.andreadec.musicplayer.adapters;

import java.util.*;

import com.andreadec.musicplayer.*;

import android.graphics.*;
import android.view.*;
import android.widget.*;

public class PodcastsArrayAdapter extends ArrayAdapter<Object> {
	private ArrayList<Object> values;
	private LayoutInflater inflater;
	private PodcastItem currentPodcastItem;
	private final static int TYPE_ACTION=0, TYPE_PODCAST=1, TYPE_PODCAST_ITEM=2;
	
	public PodcastsArrayAdapter(MainActivity activity, ArrayList<Object> values, PodcastItem currentPodcastItem) {
		super(activity, R.layout.song_item, values);
		this.values = values;
		this.currentPodcastItem = currentPodcastItem;
		inflater = activity.getLayoutInflater();
	}
	
	@Override
	public int getViewTypeCount() {
		return 3;
	}
	@Override
	public int getItemViewType(int position) {
		Object value = values.get(position);
		if(value instanceof Action) return TYPE_ACTION;
		else if(value instanceof Podcast) return TYPE_PODCAST;
		else return TYPE_PODCAST_ITEM;
	}
	
	@SuppressWarnings("deprecation")
	@Override
	public View getView(int position, View view, ViewGroup parent) {
		Object value = values.get(position);
		int type = getItemViewType(position);
		ViewHolder viewHolder;
		
		if(view==null) {
			viewHolder = new ViewHolder();
			if(type==TYPE_ACTION) {
				view = inflater.inflate(R.layout.action_item, parent, false);
				viewHolder.text1 = (TextView)view.findViewById(R.id.textView);
				viewHolder.image = (ImageView)view.findViewById(R.id.imageView);
			} else if(type==TYPE_PODCAST) {
				view = inflater.inflate(R.layout.folder_item, parent, false);
				viewHolder.text1 = (TextView)view.findViewById(R.id.textViewFolderItemFolder);
				viewHolder.image = (ImageView)view.findViewById(R.id.imageViewItemImage);
			} else {
				view = inflater.inflate(R.layout.podcast_item, parent, false);
				viewHolder.text1 = (TextView)view.findViewById(R.id.textViewPodcastTitle);
				viewHolder.text2 = (TextView)view.findViewById(R.id.textViewPodcastInfo);
				viewHolder.text3 = (TextView)view.findViewById(R.id.textViewPodcastStatus);
				viewHolder.image = (ImageView)view.findViewById(R.id.imageViewItemImage);
			}
		} else {
			viewHolder = (ViewHolder)view.getTag();
		}
		
		if (value instanceof Action) {
			Action action = (Action)value;
			viewHolder.text1.setText(action.msg);
			if(action.action==Action.ACTION_GO_BACK) {
				viewHolder.image.setImageResource(R.drawable.back);
			} else if(action.action==Action.ACTION_UPDATE) {
				viewHolder.image.setImageResource(R.drawable.refresh);
			} else if(action.action==Action.ACTION_NEW) {
				viewHolder.image.setImageResource(R.drawable.newcontent);
			}
		} else if(value instanceof Podcast) {
			Podcast podcast = (Podcast)value;
			viewHolder.text1.setText(podcast.getName());
			Bitmap podcastImage = podcast.getImage();
			if(podcastImage!=null) {
				viewHolder.image.setImageBitmap(podcastImage);
			}
		} else if(value instanceof PodcastItem) {
			PodcastItem podcastItem = (PodcastItem)value;
			viewHolder.text1.setText(podcastItem.getTitle());
			String duration = podcastItem.getDuration();
			if(duration!=null) {
				viewHolder.text2.setText(duration);
			} else {
				viewHolder.text2.setVisibility(View.GONE);
			}
			viewHolder.text3.setText(podcastItem.getStatusString());
			if(podcastItem.equals(currentPodcastItem)) {
				view.setBackgroundResource(R.color.light_blue);
				viewHolder.image.setImageResource(R.drawable.play_blue);
			} else {
				view.setBackgroundDrawable(null);
				viewHolder.image.setImageResource(R.drawable.audio);
			}
		}
		
		view.setTag(viewHolder);
		return view;
	}
	
	private class ViewHolder {
		public TextView text1;
		public TextView text2;
		public TextView text3;
		public ImageView image;
	}
}
