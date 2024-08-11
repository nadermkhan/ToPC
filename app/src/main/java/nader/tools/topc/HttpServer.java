package nader.tools.topc;

import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import fi.iki.elonen.NanoHTTPD;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class HttpServer extends NanoHTTPD {
    private static final String TAG = "HttpServer";
    private OnStatusUpdateListener mStatusUpdateListener;

    public interface OnStatusUpdateListener {
        void onUploadingProgressUpdate(int progress);
        void onUploadingFile(@NonNull File file, boolean done);
        void onDownloadingFile(@NonNull File file, boolean done);
    }

    public class DownloadResponse extends Response {
        private final File downloadFile;

        DownloadResponse(@NonNull File downloadFile, InputStream stream) {
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

        if (Method.POST.equals(method) && session.getHeaders().containsKey("content-type") && 
            session.getHeaders().get("content-type").startsWith("multipart/form-data")) {
            // Handle file upload
            try {
                // Custom file upload handling for NanoHTTPD
                Map<String, String> files = session.getFiles();
                for (Map.Entry<String, String> entry : files.entrySet()) {
                    String name = entry.getKey();
                    String fileName = entry.getValue();
                    
                    File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                    Log.d(TAG, "Save file to " + file.getAbsolutePath());
                    if (mStatusUpdateListener != null) {
                        mStatusUpdateListener.onUploadingFile(file, false);
                    }

                    try (InputStream inputStream = session.getInputStream();
                         FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    if (mStatusUpdateListener != null) {
                        mStatusUpdateListener.onUploadingFile(file, true);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                return newFixedLengthResponse("Error uploading file!");
            }
        } else if (Method.GET.equals(method)) {
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
                    response.addHeader("Content-Disposition", "attachment; filename=" + rootFile.getName());
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
