package nader.tools.topc;

import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import fi.iki.elonen.NanoHTTPD;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class HttpServer extends NanoHTTPD {
    private static final String TAG = "HttpServer";
    private static final String BOUNDARY = "----WebKitFormBoundary7MA4YWxkTrZu0gW"; // Adjust boundary as needed
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

        if (Method.POST.equals(method)) {
            try {
                // Read the entire request body
                InputStream inputStream = session.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                byte[] requestBody = baos.toByteArray();
                String bodyString = new String(requestBody);

                // Parse the multipart form data manually
                parseMultipartData(bodyString);

            } catch (Exception e) {
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
                InputStream fileInputStream;
                Response response = null;
                Log.d(TAG, "downloading file " + rootFile.getAbsolutePath());
                if (mStatusUpdateListener != null) {
                    mStatusUpdateListener.onDownloadingFile(rootFile, false);
                }

                try {
                    fileInputStream = new FileInputStream(rootFile);
                    response = new DownloadResponse(rootFile, fileInputStream);
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

    private void parseMultipartData(String bodyString) {
        // Parse the bodyString manually based on your boundary
        // This is a very basic example and may need improvements

        String[] parts = bodyString.split("--" + BOUNDARY);
        for (String part : parts) {
            if (part.contains("Content-Disposition: form-data")) {
                // Extract filename and content
                String[] lines = part.split("\r\n");
                String contentDisposition = lines[1];
                String content = lines[4];

                // Extract filename
                String filename = contentDisposition.split("filename=")[1].split("\r\n")[0];
                filename = filename.replace("\"", "");

                // Save file
                File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename);
                if (mStatusUpdateListener != null) {
                    mStatusUpdateListener.onUploadingFile(file, false);
                }

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(content.getBytes());
                    if (mStatusUpdateListener != null) {
                        mStatusUpdateListener.onUploadingFile(file, true);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void setOnStatusUpdateListener(OnStatusUpdateListener listener) {
        mStatusUpdateListener = listener;
    }
}
