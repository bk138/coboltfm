package com.coboltforge.dontmind.coboltfm;

import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class TuneActivity extends ListActivity {
	protected static final int ENTER_ARTIST = 0;
	protected static final int ENTER_TAG = 1;
	protected static final int CHOOSE_FRIEND = 2;
	String mUsername;	

	   @Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);	        	       

        	SharedPreferences settings = getSharedPreferences(Constants.PREFSNAME, 0);
	        mUsername = settings.getString("username", "empty");

	        
	        final String[] STATION_TYPES = new String[] {
	        		getString(R.string.artist), 
	        		"Tag", 
	        		getString(R.string.myrecommendations),
	        		getString(R.string.myradiostation),
	        		getString(R.string.myneighbourradio),
	        		getString(R.string.myfriends),
	        		getString(R.string.mymix)} ;
	        
	        setListAdapter(new ArrayAdapter<String>(this,
	                android.R.layout.simple_list_item_1, STATION_TYPES));

		    getListView().setItemsCanFocus(false);
		    getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		    
		    getListView().setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				Uri.Builder builder = new Uri.Builder();
				builder.scheme("lastfm");
				builder.fragment("");
				builder.query("");
				switch (arg2) {
				case STATION_TYPE_NEIGHBOR:
					builder.authority("user");
					builder.appendPath(mUsername);
					builder.appendPath("neighbours");					
					setResult(RESULT_OK, new Intent("play", builder.build(),
							TuneActivity.this, PlayerActivity.class));
					finish();										
					break;				
//				case STATION_TYPE_PLAYLIST:
//					builder.authority("user");
//					builder.appendPath(mUsername);
//					builder.appendPath("playlist");					
//					setResult(RESULT_OK, new Intent("play", builder.build(),
//							TuneActivity.this, PlayerActivity.class));
//					finish();										
//					break;				
				case STATION_TYPE_RECOMMENDED:
					builder.authority("user");
					builder.appendPath(mUsername);
					builder.appendPath("recommended");					
					builder.appendPath("100");					
					setResult(RESULT_OK, new Intent("play", builder.build(),
							TuneActivity.this, PlayerActivity.class));
					finish();										
					break;				
				case STATION_TYPE_PERSONAL:
					builder.authority("user");
					builder.appendPath(mUsername);
					builder.appendPath("personal");					
					setResult(RESULT_OK, new Intent("play", builder.build(),
							TuneActivity.this, PlayerActivity.class));
					finish();										
					break;		
				case STATION_TYPE_MIX:
					builder.authority("user");
					builder.appendPath(mUsername);
					builder.appendPath("mix");					
					setResult(RESULT_OK, new Intent("play", builder.build(),
							TuneActivity.this, PlayerActivity.class));
					finish();										
					break;	
				case STATION_TYPE_ARTIST:
					startActivityForResult(new Intent(TuneActivity.this,
							EnterArtistNameActivity.class), ENTER_ARTIST);
					break;
				case STATION_TYPE_TAG:
					startActivityForResult(new Intent(TuneActivity.this,
							EnterTagActivity.class), ENTER_TAG);
					break;
				case STATION_TYPE_FRIENDS:
					startActivityForResult(new Intent(TuneActivity.this,
							ChooseFriendActivity.class), CHOOSE_FRIEND);
					break;
				}
			}
	    	   
	       });

	   }
	   
		protected void onActivityResult(int requestCode, int resultCode, Intent data) {
			if (requestCode == ENTER_ARTIST) {
				if (resultCode == RESULT_OK) {
						Uri.Builder builder = new Uri.Builder();
						builder.scheme("lastfm");
						builder.fragment("");
						builder.query("");
						builder.authority("artist");
						builder.appendPath(data.getStringExtra("result"));
						builder.appendPath("similarartists");		
						Uri stationUri = builder.build();
						setResult(RESULT_OK, new Intent("play", stationUri,
								this, PlayerActivity.class));
						finish();
					}
				}
			else
				if (requestCode == ENTER_TAG && resultCode == RESULT_OK) {
					Uri.Builder builder = new Uri.Builder();
					builder.scheme("lastfm");
					builder.fragment("");
					builder.query("");
					builder.authority("globaltags");
					builder.appendPath(data.getStringExtra("result"));
					Uri stationUri = builder.build();
					setResult(RESULT_OK, new Intent("play", stationUri,
							this, PlayerActivity.class));
					finish();					
				}
				else 
					if (requestCode == CHOOSE_FRIEND && resultCode == RESULT_OK) {
						Uri.Builder builder = new Uri.Builder();
						builder.scheme("lastfm");
						builder.fragment("");
						builder.query("");
						builder.authority("user");
						builder.appendPath(data.getStringExtra("result"));
						builder.appendPath("personal");
						Uri stationUri = builder.build();
						setResult(RESULT_OK, new Intent("play", stationUri,
								this, PlayerActivity.class));
						finish();											
					}
					
		}	   
	   
	   private final int STATION_TYPE_ARTIST = 0;
	   private final int STATION_TYPE_TAG = 1;
	   private final int STATION_TYPE_RECOMMENDED = 2;
	   private final int STATION_TYPE_PERSONAL = 3;
	   private final int STATION_TYPE_NEIGHBOR = 4;
	   private final int STATION_TYPE_FRIENDS = 5;
	   private final int STATION_TYPE_MIX = 6;
//	   private final int STATION_TYPE_PLAYLIST = 6;
//	   private final int STATION_TYPE_GROUP = 6;
//	   private final int STATION_TYPE_LOVED = 7;
	   
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
