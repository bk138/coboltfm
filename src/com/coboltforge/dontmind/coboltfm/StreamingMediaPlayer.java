package com.coboltforge.dontmind.coboltfm;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.annotation.SuppressLint;
import android.content.Context;

import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.ParseException;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.DefaultClientConnection;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.impl.conn.DefaultResponseParser;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.io.HttpMessageParser;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicLineParser;
import org.apache.http.message.ParserCursor;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class StreamingMediaPlayer extends MediaPlayer {

	private static final String TAG = "StreamingMediaPlayer";
	private static final String TAG_PROXY = StreamProxy.class.getName();

	private StreamProxy proxy = null;
	private String streamUrl;
	private boolean useProxy;
	private Context mContext;
	private String mSessionId;

	public StreamingMediaPlayer(Context c, String sessionId, boolean useProxy) {
		this.useProxy = useProxy;
		mSessionId = sessionId;
		mContext = c;
	}


	@Override
	public void stop() {

		// stop the mediaplayer
		super.stop();

		// and the proxy, if it exists
		try{
			proxy.stop();
			proxy = null;
		}
		catch(NullPointerException e)  {
		}

	}


	@Override
	public void release() {

		// release the mediaplayer
		super.release();

		// and stop the proxy, if it exists
		try{
			proxy.stop();
			proxy = null;
		}
		catch(NullPointerException e)  {
		}

	}


	@SuppressLint("NewApi")
	public void setStreamUrl(String path) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {

		if(useProxy) {
			Log.d(TAG, "Using proxy");
			proxy = new StreamProxy();
			proxy.init();
			proxy.start();
			streamUrl = String.format("http://127.0.0.1:%d/%s", proxy.getPort(), path);
		}
		else {
			Log.d(TAG, "Using direct");
			streamUrl = path;
		}

		if(Build.VERSION.SDK_INT >= 14) {
			Log.d(TAG, "Adding headers");
			Map<String, String> httpHeader = new HashMap<String, String>();
			httpHeader.put("Cookie", "Session=" + mSessionId);
			setDataSource(mContext, Uri.parse(streamUrl), httpHeader);
		}
		else
			setDataSource(streamUrl);
	}


	public final String getStreamUrl() {
		return streamUrl;
	}



	private class StreamProxy implements Runnable {

		private int port = 0;

		public int getPort() {
			return port;
		}

		private boolean isRunning = true;
		private ServerSocket socket;
		private Thread thread;

		public void init() {
			try {
				socket = new ServerSocket(port, 0, InetAddress.getByAddress(new byte[] {127,0,0,1}));
				socket.setSoTimeout(5000);
				port = socket.getLocalPort();
				Log.d(TAG_PROXY, "port " + port + " obtained");
			} catch (UnknownHostException e) {
				Log.e(TAG_PROXY, "Error initializing server", e);
			} catch (IOException e) {
				Log.e(TAG_PROXY, "Error initializing server", e);
			}
		}

		public void start() {

			if (socket == null) {
				throw new IllegalStateException("Cannot start proxy; it has not been initialized.");
			}

			thread = new Thread(this);
			thread.start();
		}

		public void stop() {
			isRunning = false;

			if (thread == null) {
				throw new IllegalStateException("Cannot stop proxy; it has not been started.");
			}

			thread.interrupt();
			try {
				thread.join(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void run() {
			Log.d(TAG_PROXY, "running");
			while (isRunning) {
				try {
					Socket client = socket.accept();
					if (client == null) {
						continue;
					}
					Log.d(TAG_PROXY, "client connected");
					HttpRequest request = readRequest(client);
					processRequest(request, client);
				} catch (SocketTimeoutException e) {
					// Do nothing
				} catch (IOException e) {
					Log.e(TAG_PROXY, "Error connecting to client", e);
				}
			}
			Log.d(TAG_PROXY, "Proxy interrupted. Shutting down.");
		}

		private HttpRequest readRequest(Socket client) {
			HttpRequest request = null;
			InputStream is;
			String firstLine;
			try {
				is = client.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				firstLine = reader.readLine();
			} catch (IOException e) {
				Log.e(TAG_PROXY, "Error parsing request", e);
				return request;
			}

			if (firstLine == null) {
				Log.i(TAG_PROXY, "Proxy client closed connection without a request.");
				return request;
			}

			StringTokenizer st = new StringTokenizer(firstLine);
			String method = st.nextToken();
			String uri = st.nextToken();
			Log.d(TAG_PROXY, uri);
			String realUri = uri.substring(1);
			Log.d(TAG_PROXY, realUri);
			request = new BasicHttpRequest(method, realUri);
			return request;
		}

		private HttpResponse download(String url) {
			DefaultHttpClient seed = new DefaultHttpClient();
			SchemeRegistry registry = new SchemeRegistry();
			registry.register(
					new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			SingleClientConnManager mgr = new MyClientConnManager(seed.getParams(),
					registry);
			DefaultHttpClient http = new DefaultHttpClient(mgr, seed.getParams());
			http.getCookieStore().addCookie(new BasicClientCookie("Session", mSessionId));
			HttpGet method = new HttpGet(url);
			HttpResponse response = null;
			try {
				Log.d(TAG_PROXY, "starting download, session: " + mSessionId);
				response = http.execute(method);
				Log.d(TAG_PROXY, "downloaded");
			} catch (ClientProtocolException e) {
				Log.e(TAG_PROXY, "Error downloading", e);
			} catch (IOException e) {
				Log.e(TAG_PROXY, "Error downloading", e);
			} catch (IllegalStateException e) {
				Log.e(TAG_PROXY, "Error downloading", e);
			}
			return response;
		}

		private void processRequest(HttpRequest request, Socket client)
		throws IllegalStateException, IOException {
			if (request == null) {
				return;
			}
			Log.d(TAG_PROXY, "processing");
			String url = request.getRequestLine().getUri();
			HttpResponse realResponse = download(url);
			if (realResponse == null) {
				return;
			}

			Log.d(TAG_PROXY, "downloading...");

			InputStream data = realResponse.getEntity().getContent();
			StatusLine line = realResponse.getStatusLine();
			HttpResponse response = new BasicHttpResponse(line);
			response.setHeaders(realResponse.getAllHeaders());

			Log.d(TAG_PROXY, "reading headers");
			StringBuilder httpString = new StringBuilder();
			httpString.append(response.getStatusLine().toString());

			httpString.append("\n");
			for (Header h : response.getAllHeaders()) {
				httpString.append(h.getName()).append(": ").append(h.getValue()).append(
				"\n");
			}
			httpString.append("\n");
			Log.d(TAG_PROXY, "headers done");

			try {
				byte[] buffer = httpString.toString().getBytes();
				int readBytes = -1;
				Log.d(TAG_PROXY, "writing to client");
				client.getOutputStream().write(buffer, 0, buffer.length);

				// Start streaming content.
				byte[] buff = new byte[1024 * 50];
				while (isRunning && (readBytes = data.read(buff, 0, buff.length)) != -1) {
					client.getOutputStream().write(buff, 0, readBytes);
				}
			} catch (Exception e) {
				Log.e("", e.getMessage(), e);
			} finally {
				if (data != null) {
					data.close();
				}
				client.close();
			}
		}

		private class IcyLineParser extends BasicLineParser {
			private static final String ICY_PROTOCOL_NAME = "ICY";
			private IcyLineParser() {
				super();
			}

			@Override
			public boolean hasProtocolVersion(CharArrayBuffer buffer,
					ParserCursor cursor) {
				boolean superFound = super.hasProtocolVersion(buffer, cursor);
				if (superFound) {
					return true;
				}
				int index = cursor.getPos();

				final int protolength = ICY_PROTOCOL_NAME.length();

				if (buffer.length() < protolength)
					return false; // not long enough for "HTTP/1.1"

				if (index < 0) {
					// end of line, no tolerance for trailing whitespace
					// this works only for single-digit major and minor version
					index = buffer.length() - protolength;
				} else if (index == 0) {
					// beginning of line, tolerate leading whitespace
					while ((index < buffer.length()) &&
							HTTP.isWhitespace(buffer.charAt(index))) {
						index++;
					}
				} // else within line, don't tolerate whitespace

				if (index + protolength > buffer.length())
					return false;

				return buffer.substring(index, index + protolength).equals(ICY_PROTOCOL_NAME);
			}

			@Override
			public Header parseHeader(CharArrayBuffer buffer) throws ParseException {
				return super.parseHeader(buffer);
			}

			@Override
			public ProtocolVersion parseProtocolVersion(CharArrayBuffer buffer,
					ParserCursor cursor) throws ParseException {

				if (buffer == null) {
					throw new IllegalArgumentException("Char array buffer may not be null");
				}
				if (cursor == null) {
					throw new IllegalArgumentException("Parser cursor may not be null");
				}

				final int protolength = ICY_PROTOCOL_NAME.length();

				int indexFrom = cursor.getPos();
				int indexTo = cursor.getUpperBound();

				skipWhitespace(buffer, cursor);

				int i = cursor.getPos();

				// long enough for "HTTP/1.1"?
				if (i + protolength + 4 > indexTo) {
					throw new ParseException
					("Not a valid protocol version: " +
							buffer.substring(indexFrom, indexTo));
				}

				// check the protocol name and slash
				if (!buffer.substring(i, i + protolength).equals(ICY_PROTOCOL_NAME)) {
					return super.parseProtocolVersion(buffer, cursor);
				}

				cursor.updatePos(i + protolength);

				return createProtocolVersion(1, 0);
			}

			@Override
			public RequestLine parseRequestLine(CharArrayBuffer buffer,
					ParserCursor cursor) throws ParseException {
				return super.parseRequestLine(buffer, cursor);
			}

			@Override
			public StatusLine parseStatusLine(CharArrayBuffer buffer,
					ParserCursor cursor) throws ParseException {
				StatusLine superLine = super.parseStatusLine(buffer, cursor);
				return superLine;
			}
		}

		class MyClientConnection extends DefaultClientConnection {
			@Override
			protected HttpMessageParser createResponseParser(
					final SessionInputBuffer buffer,
					final HttpResponseFactory responseFactory, final HttpParams params) {
				return new DefaultResponseParser(buffer, new IcyLineParser(),
						responseFactory, params);
			}
		}

		class MyClientConnectionOperator extends DefaultClientConnectionOperator {
			public MyClientConnectionOperator(final SchemeRegistry sr) {
				super(sr);
			}

			@Override
			public OperatedClientConnection createConnection() {
				return new MyClientConnection();
			}
		}

		class MyClientConnManager extends SingleClientConnManager {
			private MyClientConnManager(HttpParams params, SchemeRegistry schreg) {
				super(params, schreg);
			}

			@Override
			protected ClientConnectionOperator createConnectionOperator(
					final SchemeRegistry sr) {
				return new MyClientConnectionOperator(sr);
			}
		}

	}







	/*
	 * backend detection stuff
	 */

	public static boolean isStreamingWorkingNatively() {

		// 4-9: ok
		// 10-13: probably most of these need the proxy
		// 14-.. : probably ok
		if (Build.VERSION.SDK_INT <= 9)
			return true;
		else
			if(Build.VERSION.SDK_INT <= 13)
				return false;
			else
				return true;

	}

}
