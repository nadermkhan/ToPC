package nader.tools.topc;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity; // Updated to AndroidX
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

//add 

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.wifi.WifiManager;
import android.os.Build;

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
		StringBuilder serverInfo = new StringBuilder().append("HTTP Server Address: http://")
		.append(getWifiIpAddress()).append(":8080\n")
		.append("Using \"curl -F file=@myfile").append(" http://")
		.append(getWifiIpAddress()).append(":8080\" ").append(" to upload file");
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
	

public String getWifiIpAddress() {
    WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
    WifiInfo info = wifiManager.getConnectionInfo();
    int ipAddress = info.getIpAddress();

    if (ipAddress != 0) {
        return android.text.format.Formatter.formatIpAddress(ipAddress);
    } else {
        // Try to get IP address via ConnectivityManager
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (Network network : cm.getAllNetworks()) {
                    LinkProperties lp = cm.getLinkProperties(network);
                    if (lp != null && lp.getInterfaceName() != null && lp.getInterfaceName().contains("wlan")) {
                        for (android.net.LinkAddress address : lp.getLinkAddresses()) {
                            String ip = address.getAddress().getHostAddress();
                            if (ip != null && !ip.startsWith("0") && ip.indexOf(':') < 0) { // Avoid IPv6 addresses
                                return ip;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    return "Unable to obtain IP address"; // or fallback to 127.0.0.1 for testing purposes
}

}