package nader.tools.topc;

import android.os.Environment;
import android.util.Log;
import androidx.annotation.NonNull;
import fi.iki.elonen.NanoHTTPD;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.util.List;
import java.util.Map;

public class HttpServer extends NanoHTTPD {
    private static final String TAG = "HttpServer";
    private final DiskFileItemFactory fileItemFactory = new DiskFileItemFactory();
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
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream inputStream = session.getInputStream();
                IOUtils.copy(inputStream, baos);
                byte[] bytes = baos.toByteArray();
                
                // Convert bytes to input stream for file upload parsing
                ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                
                ServletFileUpload upload = new ServletFileUpload(fileItemFactory);
                List<FileItem> items = upload.parseRequest(new ByteArrayRequestContext(bais));

                for (FileItem item : items) {
                    String name = item.getFieldName();
                    if (item.isFormField()) {
                        Log.d(TAG, "Item is form field, name=" + name + ", value=" + item.getString());
                    } else {
                        String fileName = item.getName();
                        Log.d(TAG, "Item is file field, name=" + name + ", fileName=" + fileName);

                        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
                        String path = file.getAbsolutePath();
                        Log.d(TAG, "Save file to " + path);
                        if (mStatusUpdateListener != null) {
                            mStatusUpdateListener.onUploadingFile(file, false);
                        }

                        try (InputStream itemInputStream = item.getInputStream();
                             FileOutputStream fos = new FileOutputStream(file)) {
                            IOUtils.copy(itemInputStream, fos);
                        }
                        if (mStatusUpdateListener != null) {
                            mStatusUpdateListener.onUploadingFile(file, true);
                        }
                    }
                }
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

    // Custom class to adapt InputStream for file upload parsing
    private static class ByteArrayRequestContext extends org.apache.commons.fileupload.RequestContext {
        private final InputStream inputStream;

        public ByteArrayRequestContext(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }
    }
}
