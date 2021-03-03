package com.luckmerlin.file.task;

import com.luckmerlin.core.debug.Debug;
import com.luckmerlin.file.Folder;
import com.luckmerlin.file.LocalPath;
import com.luckmerlin.file.MD5;
import com.luckmerlin.file.NasFolder;
import com.luckmerlin.file.NasPath;
import com.luckmerlin.file.Path;
import com.luckmerlin.file.api.Label;
import com.luckmerlin.file.api.Reply;
import com.luckmerlin.file.api.What;
import com.luckmerlin.file.nas.Nas;
import com.luckmerlin.task.OnTaskUpdate;
import com.luckmerlin.task.Result;
import com.luckmerlin.task.Status;
import com.luckmerlin.task.Task;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class UploadTask extends ActionFolderTask{

    public UploadTask(List<Path> paths, Folder folder) {
        super(paths,folder);
    }

    @Override
    protected Result onExecute(Path child, Task task, OnTaskUpdate callback) {
        Folder folder=getFolder();
        if (null==folder){
            return code(What.WHAT_ERROR);
        }else if (null==child){
            return code(What.WHAT_ERROR);
        }else if (child instanceof LocalPath){
           if (folder instanceof NasFolder){//Upload file into cloud
               String localFile=((LocalPath)child).getPath();
               File file=null!=localFile&&localFile.length()>0?new File(localFile):null;
               return uploadFileToCloud(file,(NasFolder)folder,null,callback);
           }
           return code(What.WHAT_NOT_SUPPORT);
        }
        return code(What.WHAT_NOT_SUPPORT);
    }

    private Result uploadFileToCloud(File file,NasFolder folder,String rootPath,OnTaskUpdate callback){
        final String folderSep=null!=folder?folder.getSep():null;
        final String folderHostUrl=null!=folder?folder.getHostUrl():null;
        if (null==file||null==folder||null==folderSep||null==folderHostUrl||folderHostUrl.length()<=0){
            Debug.D("Can't upload file while args invalid.");
            return code(What.WHAT_ERROR);
        }
        if (null==file||!file.exists()){
            Debug.D("Can't upload file while file not exist.");
            return code(What.WHAT_NOT_EXIST);
        }else if (!file.canRead()){
            Debug.D("Can't upload file while none permission.");
            return code(What.WHAT_NONE_PERMISSION);
        }
        notifyTaskUpdate(Status.PREPARING,callback);
        final Nas nas=new Nas();
        final String filePath=file.getAbsolutePath();
        String namePath=null!=rootPath?filePath.replaceFirst(rootPath,""):filePath;
        String targetPath=null!=namePath?namePath.replaceAll(File.separator,folderSep):null;
        targetPath=null!=targetPath?folder.getChildPath(targetPath):null;
        if (file.isDirectory()){
            Reply<NasPath> folderReply=nas.createFile(folderHostUrl,true,targetPath);
            final int replyWhat=null!=folderReply?folderReply.getWhat():What.WHAT_EXCEPTION;
            if (replyWhat!=What.WHAT_SUCCEED&&replyWhat!=What.WHAT_ALREADY_DONE){
                Debug.D("Can't upload file to cloud while create folder fail.");
                return code(What.WHAT_EXCEPTION);
            }
            File[] files=file.listFiles();
            int count=null!=files?files.length:-1;
            Result result=null;
            for (int i = 0; i < count; i++) {
                File childFile=files[i];
                if (null==childFile){
                    continue;
                }
                Result childResult=uploadFileToCloud(childFile,folder,null!=rootPath
                        &&rootPath.length()>0?rootPath:file.getParent(),callback);
                result=null!=childResult&&!isResultSucceed(childResult)?childResult:result;
            }
            return result;
        }
        //
        final Map<String,String> maps=new HashMap<>();
        final long fileLength=file.length();
        final String localMd5=new MD5().getFileMD5(file);
        maps.put(Label.LABEL_PATH, null!=targetPath?targetPath:"");
        maps.put(Label.LABEL_MD5,null!=localMd5?localMd5:"");
        maps.put(Label.LABEL_LENGTH,Long.toString(fileLength));
        Reply<NasPath> existReply=nas.getNasFileData(folderHostUrl,maps);
        long from=0;
        if (null==existReply||!existReply.isSuccess()){
            Debug.W("Can't upload file while fetch exist fail.");
            return code(What.WHAT_EXCEPTION);
        }else if (existReply.getWhat()==What.WHAT_ALREADY_DONE){//Already exist
            NasPath exist=existReply.getData();
            if ((from = (null!=exist?exist.getLength():0))<0){
                Debug.W("Can't upload file while fetch exist length invalid.");
                return code(What.WHAT_ERROR);
            }else if(from>=fileLength){
                return code(What.WHAT_ALREADY_DONE);
            }
        }
        Reply<NasPath> uploadPath=nas.upload(file,folderHostUrl,targetPath,from,null);
        Debug.D("WWWWWWWWWWW "+uploadPath);
//        QQQQQQQ  "+exist+" "+file +"\n"+targetPath);
        return null;
    }
}
