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

import java.util.*;
import android.support.v4.app.*;

public class PagerAdapter extends FragmentPagerAdapter {
	private List<Fragment> fragments;
	private List<String> fragmentsTitles;
	private FragmentManager fragmentManager;

	public PagerAdapter(FragmentManager fragmentManager, List<Fragment> fragments, List<String> fragmentsTitles) {
		super(fragmentManager);
		this.fragmentManager = fragmentManager;
		this.fragments = fragments;
		this.fragmentsTitles = fragmentsTitles;
	}

	@Override
	public Fragment getItem(int position) {
		Fragment fragment = fragmentManager.findFragmentByTag("android:switcher:"+R.id.pager+":"+position);
		if(fragment==null) return fragments.get(position);
		else return fragment;
	}

	@Override
	public int getCount() {
		return fragments.size();
	}
	
	@Override
    public String getPageTitle(int position) {
		if (fragmentsTitles==null || fragmentsTitles.size() <= position) return "";
		return fragmentsTitles.get(position);
    }
}
