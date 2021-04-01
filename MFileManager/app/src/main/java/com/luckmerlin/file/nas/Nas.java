package com.luckmerlin.file.nas;

import com.luckmerlin.core.debug.Debug;
import com.luckmerlin.core.util.Closer;
import com.luckmerlin.file.MD5;
import com.luckmerlin.file.NasPath;
import com.luckmerlin.file.api.Label;
import com.luckmerlin.file.api.Reply;
import com.luckmerlin.file.api.What;
import com.luckmerlin.file.retrofit.Retrofit;
import com.luckmerlin.file.task.Progress;
import com.luckmerlin.file.util.FileSize;
import com.luckmerlin.task.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okio.BufferedSink;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

public final class Nas {
    private final Retrofit mRetrofit=new Retrofit();

    public interface OnUploadProgressChange{
        Boolean onProgressChanged(Progress progress);
    }

    public interface ApiSaveFile {
        @POST("/file/upload")
        @Multipart
        Call<Reply<List<Reply<NasPath>>>> save(@PartMap Map<String, RequestBody> args, @Part MultipartBody.Part file);

        @GET("/file/head")
        Call<Reply<NasPath>> getFileData(@QueryMap Map<String,String> maps);

        @POST("/file/create")
        Call<Reply<NasPath>> createFile(@Query(Label.LABEL_FOLDER) boolean isDirectory, @Query(Label.LABEL_PATH) String path);
    }

    private <T> T call(Call<T> call){
        try {
            retrofit2.Response<T> response=null!=call?call.execute():null;
            return null!=response?response.body():null;
        }catch (Exception e) {
            Debug.E("Exception call nas file.e="+e,e);
            e.printStackTrace();
        }
        return null;
    }

    public final Reply<NasPath> getNasFileData(String serverUrl, Map<String,String> maps){
        if (null!=serverUrl&&serverUrl.length()>0&&null!=maps&&maps.size()>0){
            return call(mRetrofit.prepare(ApiSaveFile.class,serverUrl).getFileData(maps));
        }
        return null;
    }

    public final Reply<NasPath> createFile(String serverUrl,boolean isDirectory, String path){
        if (null!=serverUrl&&serverUrl.length()>0){
            Retrofit retrofit=mRetrofit;
            return call(retrofit.prepare(ApiSaveFile.class,serverUrl).createFile(isDirectory,path));
        }
        return null;
    }

    public final Response upload(File file, String serverUrl, String toPath, long seek, int cover,String localMd5,
                                 OnUploadProgressChange callback, String debug){
        final UploadRequestBody uploadBody=new UploadRequestBody(file,seek){
            @Override
            protected Boolean onProgress(long upload, long length,float speed) {
                return null!=callback?callback.onProgressChanged(mProgress):null;
            }
        };
        try {
            Map<String, RequestBody> map = new HashMap<>();
            map.put(Label.LABEL_PATH,RequestBody.create(MediaType.parse("text/plain"), toPath+"我爱你"));
            Debug.D("Upload file "+file.getName()+" to "+toPath+" "+(null!=debug?debug:"."));
            StringBuilder disposition = new StringBuilder("form-data; name=" + file.getName()+ ";filename=luckmerlin");
            Headers.Builder headersBuilder = new Headers.Builder().addUnsafeNonAscii("Content-Disposition", disposition.toString());
            final String encoding="utf-8";
            localMd5=null!=localMd5&&localMd5.length()>0?localMd5:new MD5().getFileMD5(file);
            headersBuilder.add(Label.LABEL_PATH,encode(toPath, "", encoding));
            headersBuilder.add(Label.LABEL_LENGTH,encode(Long.toString(file.length()), "", encoding));
            headersBuilder.add(Label.LABEL_POSITION,encode(Long.toString(seek), "", encoding));
            headersBuilder.add(Label.LABEL_MD5,encode(localMd5, "", encoding));
            headersBuilder.add(Label.LABEL_MODE,encode(Long.toString(cover), "", encoding));
            Call<Reply<List<Reply<NasPath>>>> call= mRetrofit.prepare(ApiSaveFile.class, serverUrl).save(map,
                    MultipartBody.Part.create(headersBuilder.build(),uploadBody));
            retrofit2.Response<Reply<List<Reply<NasPath>>>> response=null!=call?call.execute():null;
            Reply<List<Reply<NasPath>>> reply= null!=response&&response.isSuccessful()?response.body():null;
            List<Reply<NasPath>> list=null!=reply?reply.getData():null;
            Reply<NasPath> nasPathReply=null!=list&&list.size()>0?list.get(0):null;
            boolean succeed=null!=nasPathReply&&nasPathReply.getWhat()==What.WHAT_SUCCEED;
            NasPath nasPath=null!=nasPathReply?nasPathReply.getData():null;
            String md5=succeed&&null!=nasPath?nasPath.getMd5(null):null;
            int code=succeed&&((null==md5&&null==localMd5)||(null!=md5&&null!=localMd5&&
                    md5.equals(localMd5)))?What.WHAT_SUCCEED:What.WHAT_FAIL;
            return new Response() {
                @Override
                public int getCode() {
                    return code;
                }

                @Override
                public Object getResult() {
                    return nasPath;
                }
            };
        } catch (IOException e) {
            Debug.E("Exception upload file to nas.e="+e,e);
            e.printStackTrace();
        }
        return null;
    }

    private static abstract class UploadRequestBody extends RequestBody {
        private final File mFile;
        private final String mTitle;
        private boolean mCancel=false;
        private long mUploaded ;
        private final long mTotal;
        private long mSpeed;

        final Progress mProgress=new Progress() {
            @Override
            public Object getProgress(int type) {
                switch (type){
                    case Progress.TYPE_DONE:
                        return mUploaded;
                    case Progress.TYPE_SPEED:
                        return FileSize.formatSizeText(mSpeed)+"/s";
                    case Progress.TYPE_TOTAL:
                        return mTotal;
                    case Progress.TYPE_TITLE:
                        return mTitle;
                    case Progress.TYPE_PERCENT:
                        long total=mTotal;
                        long upload=mUploaded;
                        return String.format("%.2f",total>0?(upload<=0?0:upload)*100.f/total:0);
                }
                return null;
            }
        };

        protected abstract Boolean onProgress(long upload,long length,float speed);

        private UploadRequestBody(File file,long seek){
            mFile=file;
            mUploaded=seek;
            mTotal=null!=file?file.length():0;
            mTitle=null!=file?file.getName():null;
        }

        @Override
        public final MediaType contentType() {
            return MediaType.parse("application/otcet-stream");
        }

        @Override
        public final void writeTo(BufferedSink sink) throws IOException {
            final File file = mFile;
            boolean succeed = false;
            if (null != file && file.exists()) {
                if (file.isFile()) {
                    final long length=file.length();
                    FileInputStream in = null;
                    try {
                        int bufferSize = 1024*1024;
                        byte[] buffer = new byte[bufferSize];
                        long startTime=0;
                        in = new FileInputStream(file);
                        if (!mCancel) {
                            long seek=mUploaded;
                            if ((mUploaded=(seek=(seek<=0?0:seek)))>0&&seek<=length){
                                in.skip(seek);
                            }
                            Debug.D("Uploading file  "+FileSize.formatSizeText(mUploaded)+"-"
                                    + FileSize.formatSizeText(length) +" "+file.getAbsolutePath());
                            int read;
                            succeed = true;
                            while ((read = in.read(buffer)) >=0) {
                                if (mCancel) {
                                    break;
                                }else if (read>0){
                                    if ((startTime=startTime>0?System.nanoTime()-startTime:-1)>0){
                                        startTime=TimeUnit.NANOSECONDS.toMillis(startTime);
                                        mSpeed=startTime>0?read*1000/startTime:0;
                                    }
                                    startTime=System.nanoTime();
                                    mUploaded += read;
                                    sink.write(buffer, 0, read);
                                    Thread.sleep(1000);
                                    Boolean interruptUpload=onProgress(mUploaded, length ,-1);
                                    if (null!=interruptUpload&&interruptUpload){
                                        Debug.D("File upload interrupted.");
                                        break;
                                    }
                                }
                            }
                        }
                        sink.flush();
                        sink.emit();
                    } catch (Exception e) {
                        succeed = false;
                    } finally {
                        new Closer().close(in);
                    }
                }
            }
        }
    }

    private final String encode(String name, String def,String encoding){
        try {
            return null!=name&&name.length()>0? URLEncoder.encode(name,null!=encoding&&encoding.
                    length()>0?encoding: "UTF-8"):def;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return def;
    }
}
