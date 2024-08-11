package nader.tools.topc;

import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;  // AndroidX import
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.ProgressListener;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.util.Streams;
import org.nanohttpd.fileupload.NanoFileUpload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
	private static final String TAG = "HttpServer";
	private final NanoFileUpload mFileUpload;
	private OnStatusUpdateListener mStatusUpdateListener;
	
	public interface OnStatusUpdateListener {
		void onUploadingProgressUpdate(int progress);
		void onUploadingFile(@NonNull File file, boolean done);  // Annotated with @NonNull
		void onDownloadingFile(@NonNull File file, boolean done);  // Annotated with @NonNull
	}
	
	public class DownloadResponse extends Response {
		private final File downloadFile;
		
		DownloadResponse(@NonNull File downloadFile, InputStream stream) {  // Annotated with @NonNull
			super(Response.Status.OK, "application/octet-stream", stream, downloadFile.length());
			this.downloadFile = downloadFile;
		}
		
		@Override
		protected void send(OutputStream outputStream) {
			super.send(outputStream);
			if (mStatusUpdateListener != null) {
				mStatusUpdateListener.onDownloadingFile(downloadFile, true);
			}
		}
	}
	
	public HttpServer(int port) {
		super(port);
		mFileUpload = new NanoFileUpload(new DiskFileItemFactory());
		mFileUpload.setProgressListener(new ProgressListener() {
			int progress = 0;
			
			@Override
			public void update(long pBytesRead, long pContentLength, int pItems) {
				if (mStatusUpdateListener != null) {
					int p = (int) (pBytesRead * 100 / pContentLength);
					if (p != progress) {
						progress = p;
						mStatusUpdateListener.onUploadingProgressUpdate(progress);
					}
				}
			}
		});
	}
	
	@Override
	public Response serve(IHTTPSession session) {
		String uri = session.getUri();
		Method method = session.getMethod();
		Map<String, String> header = session.getHeaders();
		Map<String, String> parms = session.getParms();
		String answer = "Success!";
		Log.d(TAG, "uri=" + uri);
		Log.d(TAG, "method=" + method);
		Log.d(TAG, "header=" + header);
		Log.d(TAG, "params=" + parms);
		
		// File upload handling
		if (NanoFileUpload.isMultipartContent(session)) {
			try {
				FileItemIterator iterator = mFileUpload.getItemIterator(session);
				while (iterator.hasNext()) {
					FileItemStream item = iterator.next();
					String name = item.getFieldName();
					InputStream inputStream = item.openStream();
					if (item.isFormField()) {
						Log.d(TAG, "Item is form field, name=" +
						name + ", value=" + Streams.asString(inputStream));
						} else {
						String fileName = item.getName();
						Log.d(TAG, "Item is file field, name=" + name + ", fileName=" + fileName);
						
						File file = new File(Environment.getExternalStoragePublicDirectory(
						Environment.DIRECTORY_DOWNLOADS), fileName);
						String path = file.getAbsolutePath();
						Log.d(TAG, "Save file to " + path);
						if (mStatusUpdateListener != null) {
							mStatusUpdateListener.onUploadingFile(file, false);
						}
						
						FileOutputStream fos = new FileOutputStream(file);
						Streams.copy(inputStream, fos, true);
						if (mStatusUpdateListener != null) {
							mStatusUpdateListener.onUploadingFile(file, true);
						}
					}
				}
				} catch (Exception e) {
				e.printStackTrace();
			}
			} else if (method.equals(Method.GET)) {
			// File browse and download handling
			File rootFile = Environment.getExternalStorageDirectory();
			uri = uri.replace(rootFile.getAbsolutePath(), "");
			rootFile = new File(rootFile + uri);
			if (!rootFile.exists()) {
				return newFixedLengthResponse("Error! No such file or directory");
			}
			
			if (rootFile.isDirectory()) {
				// List directory files
				Log.d(TAG, "list " + rootFile.getPath());
				File[] files = rootFile.listFiles();
				answer = "<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; " +
				"charset=utf-8\"><title>HTTP File Browser</title>";
				
				for (File file : files) {
					answer += "<a href=\"" + file.getAbsolutePath()
					+ "\" alt = \"\">" + file.getAbsolutePath()
					+ "</a><br>";
				}
				
				answer += "</head></html>";
				} else {
				// Serve file download
				InputStream inputStream;
				Response response = null;
				Log.d(TAG, "downloading file " + rootFile.getAbsolutePath());
				if (mStatusUpdateListener != null) {
					mStatusUpdateListener.onDownloadingFile(rootFile, false);
				}
				
				try {
					inputStream = new FileInputStream(rootFile);
					response = new DownloadResponse(rootFile, inputStream);
					} catch (Exception e) {
					e.printStackTrace();
				}
				
				if (response != null) {
					response.addHeader(
					"Content-Disposition", "attachment; filename=" + rootFile.getName());
					return response;
					} else {
					return newFixedLengthResponse("Error downloading file!");
				}
			}
		}
		
		return newFixedLengthResponse(answer);
	}
	
	public void setOnStatusUpdateListener(OnStatusUpdateListener listener) {
		mStatusUpdateListener = listener;
	}
}