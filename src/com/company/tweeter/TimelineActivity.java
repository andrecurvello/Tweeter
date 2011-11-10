package com.company.tweeter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.auth.AccessToken;
import android.app.Activity;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.company.tweeter.accountmanager.AccountManager;
import com.company.tweeter.accountmanager.TwitterAccount;
import com.company.tweeter.database.TweeterDbHelper;

public class TimelineActivity extends Activity {
    /** Called when the activity is first created. */
	
	private AccountManager manager;
	private TwitterAccount account;
	
	private TweeterDbHelper dbHelper;
	
	private SimpleCursorAdapter adapter;
	
	private List<Status> statuses;
	
	private ListView timelineList;
	private ImageView userImageView;
//	private TextView username;
//	private TextView time;
//	private TextView tweetText;
//	private TextView retweetedBy;
	
//	private ImageButton showTweets;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        dbHelper = new TweeterDbHelper(this);
        dbHelper.getWritableDatabase();
        
        manager = AccountManager.getInstance();
        account = manager.getAccount();
        Log.d(Constants.TAG, "Return value: " + account.isUserLoggedIn(this));
        if(account.isUserLoggedIn(this)) {
        	Log.d(Constants.TAG, "user is logged in");
        	
        	AccessToken token = account.getTokenFromPreferences();
        	account.setAccessToken(token);
        	
        	setContentView(R.layout.timeline_layout);
        	initializeUI();
//        	showTweets.setOnClickListener(this);
        	getStatuses();
        	updateTimelineUI();
        } else {
        	login();
        	Log.d(Constants.TAG, "user is not logged in");
        }
    }
    
    private Bitmap saveImageFile(InputStream is, String path) {
		try {
			File file = new File(path);
			if (!file.exists())
				file.createNewFile();
			FileOutputStream fo = new FileOutputStream(file);
			byte[] buffer = new byte[1000];
			int n = is.read(buffer, 0, 1000);
			int size = n;
			while (n > 0) {
				fo.write(buffer, 0, n);
				n = is.read(buffer, 0, 1000);
				size += n;
			}
			Log.d("Downloading..", "total size: " + size);
			Bitmap bmp = BitmapFactory.decodeFile(path);
			return bmp;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
    
	private void updateTimelineUI() {
		Cursor data = dbHelper.query(Constants.TABLE_NAME, null, null);
		if (data.moveToFirst()) {
			adapter = new SimpleCursorAdapter(this, R.layout.tweet_row, data, 
					new String[] {Constants.CREATED_TIME, Constants.USERNAME, Constants.PROFILE_IMAGE, Constants.TWEET}, 
					new int[] {R.id.time, R.id.username, R.id.userImageView, R.id.tweetMessage});
			
			SimpleCursorAdapter.ViewBinder viewBinder = new SimpleCursorAdapter.ViewBinder() {
				public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
					if(view != null && view.getId() != R.id.userImageView) {
						return false;
					}
					Bitmap bmp = null;
					String imageUrlString = cursor.getString(columnIndex);
					String username = cursor.getString(cursor.getColumnIndex(Constants.USERNAME));
					String path = getDir("images", MODE_PRIVATE).getAbsolutePath() + "/" + username + ".png";
					Log.d(Constants.TAG, "username: " + username);
					Log.d(Constants.TAG, "imageUrlString: " + imageUrlString);
					Log.d(Constants.TAG, "path: " + path);
					try {
						URL imageUrl = new URL(imageUrlString);
						HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();
						connection.connect();
						
						InputStream is = connection.getInputStream();
						bmp = saveImageFile(is, path);
						
					} catch (MalformedURLException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} 
					
					if (view instanceof ImageView) {
						((ImageView) view).setImageBitmap(bmp);
						Log.d(Constants.TAG, "view is an instance of ImageView");
					}
					return true;
				}
			};
			
			int index = data.getColumnIndex(Constants.PROFILE_IMAGE);
//			Log.d(Constants.TAG, "" + index);
			
			adapter.setViewBinder(viewBinder);
			
			viewBinder.setViewValue(userImageView, data, index);
			
			timelineList.setAdapter(adapter);
		}
	}

	private void getStatuses() {
		try {
			statuses = account.getHomeTimeline();
			for (Status status : statuses) {
				dbHelper.addStatus(status);
			}
		} catch (TwitterException e) {
			Log.d(Constants.TAG, e.getErrorMessage());
		}
	}

	private void initializeUI() {
    	timelineList = (ListView) findViewById(R.id.tweetList);
    	userImageView = (ImageView) findViewById(R.id.userImageView);
//    	username = (TextView) findViewById(R.id.username);
//    	time = (TextView) findViewById(R.id.time);
//    	tweetText = (TextView) findViewById(R.id.tweetMessage);
//    	retweetedBy = (TextView) findViewById(R.id.retweetedBy);
    	
//    	showTweets = (ImageButton) findViewById(R.id.showTweets);
    }
    
    private void login() {
    	WebView webView = new WebView(this);
    	webView.getSettings().setJavaScriptEnabled(true);
    	webView.setWebViewClient(new WebViewClient() {
    		@Override
    		public void onPageFinished(WebView view, String url) {
    			super.onPageFinished(view, url);
    			if(url.contains(Constants.CALLBACK_URL)) {
    				Uri uri = Uri.parse(url);
//    				Toast.makeText(getApplicationContext(), url, Toast.LENGTH_LONG).show();
    				Log.d(Constants.TAG, "onPageFinished: " + url);
    				
    				String oAuthVerifier = uri.getQueryParameter(Constants.OAUTH_VERIFIER);
    				AccessToken token;
					try {
						token = account.getAccessToken(oAuthVerifier);
						Log.d(Constants.TAG, token.getToken());
						account.setAccessToken(token);
						account.writeTokenToPrefs(token);
//						account.setAccessToken(token);
					} catch (TwitterException e) {
						e.printStackTrace();
					}
    				
    				setContentView(R.layout.timeline_layout);
    				initializeUI();
    				getStatuses();
    				updateTimelineUI();
    			}
    		}
    		
    		@Override
    		public void onReceivedError(WebView view, int errorCode,
    				String description, String failingUrl) {
    			super.onReceivedError(view, errorCode, description, failingUrl);
//    			Toast.makeText(getApplicationContext(), description, Toast.LENGTH_LONG).show();
    			Log.d(Constants.TAG, "onReceivedError: " + description);
    		}
    	});
    	
    	try {
			webView.loadUrl(account.getAuthenticationUrl());
		} catch (TwitterException e) {
			Toast.makeText(getApplicationContext(), e.getErrorMessage(), Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
		}
    	setContentView(webView);
    }
}