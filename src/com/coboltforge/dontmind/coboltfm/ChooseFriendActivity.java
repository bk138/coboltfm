package com.coboltforge.dontmind.coboltfm;

import java.util.ArrayList;
import java.util.List;

import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AdapterView.OnItemClickListener;

public class ChooseFriendActivity extends ListActivity {
    private static final int DIALOG_KEY = 0;
    private FriendLoader loader;
    private ProgressDialog dialog;
    
	private OnCancelListener listener = new OnCancelListener() {

		@Override
		public void onCancel(DialogInterface dialog) {
			setResult(RESULT_CANCELED);
			finish();
		}		
	};		
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    	SharedPreferences settings = getSharedPreferences(Constants.PREFSNAME, 0);		

    	String username = settings.getString("username", "<none>");
        loader = new FriendLoader(username);
        showDialog(DIALOG_KEY);        
        
        getListView().setTextFilterEnabled(true);
        
        getListView().setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View vuew, int position,
					long id) {
				String friendName = (String)getListView().getItemAtPosition(position);

				Intent res = new Intent(Intent.ACTION_MAIN);
				res.putExtra("result", friendName);
				setResult(RESULT_OK, res);
				finish();				
			}
        });
        loader.start();
    }
    
    private void setListContents(String[] strings) {
    	if (dialog != null)
    		dialog.dismiss();

    	SharedPreferences settings = getSharedPreferences(Constants.PREFSNAME, 0);		
    	Uri stationUri = PlayerActivity.getStationUri(settings);
        
    	int lastFriendId = -1;
        if (stationUri != null)
        {
        	List<String> path = stationUri.getPathSegments();
        	if (stationUri.getScheme().equals("lastfm")) {
    			if (stationUri.getAuthority().equals("user") && path.size() > 0)
    			{
    				String lastFriendName = path.get(0);
        			for (int i = 0; i < strings.length; i++) {
        				if (strings[i].equals(lastFriendName))
        					lastFriendId = i;
        			}
    			}
        	}
        }
    	
        
        // Use an existing ListAdapter that will map an array
        // of strings to TextViews
        setListAdapter(new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, strings));
        if (lastFriendId != -1)
        	getListView().setSelection(lastFriendId);
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_KEY: {
                dialog = new ProgressDialog(this);
                dialog.setMessage(getString(R.string.pleasewait));
                dialog.setIndeterminate(true);
                dialog.setCancelable(true);
                dialog.setOnCancelListener(listener);
                return dialog;
            }
        }
        return null;
    }
    
    class FriendLoader extends Thread {
    	
    	ArrayList<FriendInfo> friends;
    	String mUsername;
    	
    	FriendLoader(String username) {
    		mUsername = username;
    	}
    	
    	@Override
    	public void run() {
    		ArrayList<FriendInfo> friends = PlayerThread.downloadFriendsList(mUsername);
    		final ArrayList<String> friendNames = new ArrayList<String>();
    		try{ // in case friends == null
    			for (FriendInfo f : friends) {
    				friendNames.add(f.getName());
    			}
    		}
    		catch(NullPointerException e) {
    		}
    		
    		ChooseFriendActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {													
					String[] s = {};
					ChooseFriendActivity.this.setListContents(friendNames.toArray(s));
				}    							
    		});
    	}
    }

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// app icon in action bar clicked; go home
			Intent intent = new Intent(this, PlayerActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

    
}

