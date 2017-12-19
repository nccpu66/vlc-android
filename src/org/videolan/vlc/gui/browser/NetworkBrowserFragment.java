/*
 * *************************************************************************
 *  NetworkBrowserFragment.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.browser;

import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.vlc.R;
import org.videolan.vlc.gui.dialogs.NetworkServerDialog;
import org.videolan.vlc.media.MediaDatabase;
import org.videolan.vlc.media.MediaWrapper;
import org.videolan.vlc.util.AndroidDevices;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;

public class NetworkBrowserFragment extends BaseBrowserFragment implements View.OnClickListener {

    private DialogFragment mDialog;

    public NetworkBrowserFragment() {
        ROOT = "smb";
        mHandler = new BrowserFragmentHandler(this);
        mAdapter = new BaseBrowserAdapter(this);
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (mMrl == null)
            mMrl = ROOT;
        mRoot = ROOT.equals(mMrl);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        if (mRoot) {
            mAddDirectoryFAB = (FloatingActionButton) v.findViewById(R.id.fab_add_custom_dir);
            mAddDirectoryFAB.setVisibility(View.VISIBLE);
            mAddDirectoryFAB.setOnClickListener(this);
        }
        return v;
    }

    public void onStart(){
        super.onStart();

        //Handle network connection state
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        getActivity().registerReceiver(networkReceiver, filter);
    }

    @Override
    protected Fragment createFragment() {
        return new NetworkBrowserFragment();
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(networkReceiver);
    }

    @Override
    protected void update() {
        if (!AndroidDevices.hasLANConnection())
            updateEmptyView();
        else
            super.update();
    }

    protected void updateDisplay() {
        if (mRoot)
            updateFavorites();
        mAdapter.notifyDataSetChanged();
        parseSubDirectories();
    }

    @Override
    protected void browseRoot() {
        updateFavorites();
        mAdapter.setTop(mAdapter.getItemCount());
        mMediaBrowser.discoverNetworkShares();
    }

    @Override
    protected String getCategoryTitle() {
        return getString(R.string.network_browsing);
    }

    private void updateFavorites(){
        ArrayList<MediaWrapper> favs = MediaDatabase.getInstance().getAllNetworkFav();
        int newSize = favs.size(), totalSize = mAdapter.getItemCount();

        if (newSize == 0 && mFavorites == 0)
            return;
        if (mFavorites != 0 && !mAdapter.isEmpty())
            for (int i = 1 ; i <= mFavorites ; ++i) //remove former favorites
                mAdapter.removeItem(1, mReadyToDisplay);

        if (newSize == 0 && !mAdapter.isEmpty()) {
            mAdapter.removeItem(0, mReadyToDisplay); //also remove separator if no more fav
            mAdapter.removeItem(0, mReadyToDisplay); //also remove separator if no more fav
        } else {
            boolean isEmpty =  mAdapter.isEmpty();
            if (mFavorites == 0 || isEmpty)
                mAdapter.addItem(getString(R.string.network_favorites), false, false,0); //add header if needed
            for (int i = 0 ; i < newSize ; )
                mAdapter.addItem(favs.get(i), false, false, ++i); //add new favorites
            if (mFavorites == 0 || isEmpty)
                mAdapter.addItem(getString(R.string.network_shared_folders), false, false, newSize + 1); //add header if needed
            mAdapter.notifyItemRangeChanged(0, newSize+1);
        }
        mFavorites = newSize; //update count
    }

    public void toggleFavorite() {
        MediaDatabase db = MediaDatabase.getInstance();
        if (db.networkFavExists(mCurrentMedia.getUri()))
            db.deleteNetworkFav(mCurrentMedia.getUri());
        else
            db.addNetworkFavItem(mCurrentMedia.getUri(), mCurrentMedia.getTitle());
        getActivity().supportInvalidateOptionsMenu();
    }

    /**
     * Update views visibility and emptiness info
     */
    protected void updateEmptyView() {
        if (AndroidDevices.hasLANConnection()) {
            if (mAdapter.isEmpty()) {
                mEmptyView.setText(mRoot ? R.string.network_shares_discovery : R.string.network_empty);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
                mSwipeRefreshLayout.setEnabled(false);
            } else {
                if (mEmptyView.getVisibility() == View.VISIBLE) {
                    mEmptyView.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                    mSwipeRefreshLayout.setEnabled(true);
                }
            }
        } else {
            if (mEmptyView.getVisibility() == View.GONE) {
                mEmptyView.setText(R.string.network_connection_needed);
                mEmptyView.setVisibility(View.VISIBLE);
                mRecyclerView.setVisibility(View.GONE);
                mSwipeRefreshLayout.setEnabled(false);
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.fab_add_custom_dir){
            showAddServerDialog();
        }
    }

    public void showAddServerDialog() {
        FragmentManager fm = getFragmentManager();
        NetworkServerDialog dialog = new NetworkServerDialog();
        dialog.show(fm, "fragment_add_server");
    }

    private final BroadcastReceiver networkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (mReadyToDisplay && ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                update();
            }
        }
    };
}
