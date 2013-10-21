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

import android.graphics.*;
import android.os.*;
import android.support.v4.util.*;
import android.widget.*;

public class ImageLoaderTask extends AsyncTask<Void, Void, Bitmap> {
	private PlayableItem item;
	private ImageView imageView;
	private LruCache<String,Bitmap> imagesCache;
	private int listImageSize;
	
	public ImageLoaderTask(PlayableItem item, ImageView imageView, LruCache<String,Bitmap> imagesCache, int listImageSize) {
		this.item = item;
		this.imageView = imageView;
		this.listImageSize = listImageSize;
		this.imagesCache = imagesCache;
	}

	@Override
	protected Bitmap doInBackground(Void... params) {
		Bitmap originalImage = item.getImage();
		if(originalImage==null) return null;
		Bitmap image = Bitmap.createScaledBitmap(originalImage, listImageSize, listImageSize, true);
		synchronized(imagesCache) {
			imagesCache.put(item.getPlayableUri(), image);
		}
		originalImage = null;
		return image;
	}
	
	@Override
	protected void onPostExecute(Bitmap image) {
		if(image!=null) {
			imageView.setImageBitmap(image);
		}
	}
}
