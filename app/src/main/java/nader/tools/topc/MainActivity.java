package nader.tools.topc;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity; // Updated to AndroidX
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {
	private static final String TAG = "MainActivity";
	private HttpServer mHttpd;
	private TextView mUploadInfo, mDownloadInfo;
	private ProgressBar mProgressBar;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		TextView tv = (TextView) findViewById(R.id.server_info);
		String ipAddress = getHotspotIPAddress(); // Use the correct method to get IP Address
		StringBuilder serverInfo = new StringBuilder().append("HTTP Server Address: http://")
		.append(ipAddress).append(":8080\n")
		.append("Using \"curl -F file=@myfile").append(" http://")
		.append(ipAddress).append(":8080\" ").append(" to upload file");
		tv.setText(serverInfo);
		
		mUploadInfo = (TextView) findViewById(R.id.upload_info);
		mDownloadInfo = (TextView) findViewById(R.id.download_info);
		mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
		
		mHttpd = new HttpServer(8080);
		mHttpd.setOnStatusUpdateListener(new HttpServer.OnStatusUpdateListener() {
			@Override
			public void onUploadingProgressUpdate(final int progress) {
				MainActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						mProgressBar.setProgress(progress);
					}
				});
			}
			
			@Override
			public void onUploadingFile(final File file, final boolean done) {
				MainActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (done) {
							mUploadInfo.setText("Upload file " + file.getName() + " done!");
							} else {
							mProgressBar.setProgress(0);
							mUploadInfo.setText("Uploading file " + file.getName() + "...");
						}
					}
				});
			}
			
			@Override
			public void onDownloadingFile(final File file, final boolean done) {
				MainActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (done) {
							mDownloadInfo.setText("Download file " + file.getName() + " done!");
							} else {
							mDownloadInfo.setText("Downloading file " + file.getName() + " ...");
						}
					}
				});
			}
		});
		
		try {
			mHttpd.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void onDestroy() {
		mHttpd.stop();
		super.onDestroy();
	}
	
	public String getHotspotIPAddress() {
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
		int ipAddress = dhcpInfo.serverAddress;

		if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
			ipAddress = Integer.reverseBytes(ipAddress);
		}

		byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

		String ipAddressString;
		try {
			ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
		} catch (UnknownHostException ex) {
			ipAddressString = "";
		}

		return ipAddressString;
	}
}
