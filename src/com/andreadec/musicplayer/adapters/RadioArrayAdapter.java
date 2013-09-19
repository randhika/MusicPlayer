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

package com.andreadec.musicplayer.adapters;

import java.util.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import com.andreadec.musicplayer.*;

public class RadioArrayAdapter extends ArrayAdapter<Song> {
	private final Context context;
	private final ArrayList<Song> values;
 
	public RadioArrayAdapter(Context context, ArrayList<Song> values) {
		super(context, R.layout.song_item, values);
		this.context = context;
		this.values = values;
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		Song value = values.get(position);
		LayoutInflater inflater = ((MainActivity)context).getLayoutInflater();
		view = inflater.inflate(R.layout.radio_item, parent, false);
		TextView textViewRadioItem = (TextView)view.findViewById(R.id.textViewRadioItem);
		textViewRadioItem.setText(value.getTitle());
		return view;
	}
}
