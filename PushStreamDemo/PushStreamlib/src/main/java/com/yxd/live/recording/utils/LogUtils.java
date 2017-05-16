package com.yxd.live.recording.utils;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class LogUtils {

	static private enum TYPE {
        INFO,
        DEBUG,
        VERBOSE,
        WARN,
        ERROR
    }

    public static final int LOG_NONE_TO_FILE = 0;
    public static final int LOG_ERROR_TO_FILE = 2;
    public static final int LOG_WARN_TO_FILE = 1;
    public static final int LOG_INFO_TO_FILE = 4;
    public static final int LOG_ALL_TO_FILE = 3;
    
    public static String TAG = "LRSDK";
    protected static boolean isEnable = false;
    protected static String logDirPath = (new StringBuilder()).append(Environment.getExternalStorageDirectory()).toString();
    protected static String logFileBaseName = "/log";
    protected static String logFileSuffix = "log";
    private static String path = "";
    protected static int policy = 0;
    private static ExecutorService executor = null;
    private static long logFileSize = 0xa00000L;

    private LogUtils() {
        
    }

    public static void d(String tag, String msg) {
      /*  if (isEnable)
            if (tag == null || tag == "")
                d(msg);
            else
                Log.d(tag, buildMessage(TYPE.DEBUG, tag, msg, null));*/
    }

    public static void d(String msg) {
        if (isEnable)
            Log.d(TAG, buildMessage(TYPE.DEBUG, TAG, msg, null));
    }

    @SuppressLint("SimpleDateFormat") 
    protected static String buildMessage(TYPE type, String tag, String msg, Throwable thr) {
        if (TextUtils.isEmpty(path)) {
            System.out.println((new StringBuilder("LogUtils path = ")).append(path).toString());
            setPath(logDirPath, logFileBaseName, logFileSuffix);
        }
        StackTraceElement caller = (new Throwable()).fillInStackTrace().getStackTrace()[2];
        boolean isLog2File = false;
        switch (policy) {
        default:
            break;

        case 0: // '\0'
            isLog2File = false;
            break;

        case 1: // '\001'
            if (type == TYPE.WARN)
                isLog2File = true;
            else
                isLog2File = false;
            break;

        case 2: // '\002'
            if (type == TYPE.ERROR)
                isLog2File = true;
            else
                isLog2File = false;
            break;

        case 3: // '\003'
            isLog2File = true;
            break;

        case 4: // '\004'
            if (type == TYPE.INFO)
                isLog2File = true;
            else
                isLog2File = false;
            break;
        }
        StringBuffer bufferlog = new StringBuffer();
        bufferlog.append(caller.getMethodName());
        bufferlog.append("( ");
        bufferlog.append(caller.getFileName());
        bufferlog.append(": ");
        bufferlog.append(caller.getLineNumber());
        bufferlog.append(")");
        bufferlog.append(" : ");
        bufferlog.append(msg);
        if (thr != null) {
            bufferlog.append(System.getProperty("line.separator"));
            bufferlog.append(Log.getStackTraceString(thr));
        }
        if (isLog2File) {
            StringBuffer filelog = new StringBuffer();
            Date myDate = new Date();
            SimpleDateFormat fdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            String myDateString = fdf.format(myDate);
            filelog.append(myDateString);
            filelog.append("    ");
            filelog.append("v1.0.15");
            filelog.append("    ");
            filelog.append(type.name().charAt(0));
            filelog.append("    ");
            filelog.append(tag);
            filelog.append("    ");
            filelog.append(bufferlog);
            log2file(path, filelog.toString());
        }
        return bufferlog.toString();
    }

    public static void d(String tag, String msg, Throwable thr) {
        if (isEnable)
            if (tag == null || tag == "")
                d(msg, thr);
            else
                Log.d(tag, buildMessage(TYPE.DEBUG, tag, msg, thr), thr);
    }

    public static void d(String msg, Throwable thr) {
        if (isEnable)
            Log.d(TAG, buildMessage(TYPE.DEBUG, TAG, msg, thr), thr);
    }

    public static void e(String tag, String msg) {
        if (isEnable)
            if (tag == null || tag == "")
                e(msg);
            else
                Log.e(tag, buildMessage(TYPE.ERROR, tag, msg, null));
    }

    public static void e(String msg) {
        if (isEnable)
            Log.e(TAG, buildMessage(TYPE.ERROR, TAG, msg, null));
    }

    public static void e(String tag, String msg, Throwable thr) {
        if (isEnable)
            if (tag == null || tag == "")
                e(msg, thr);
            else
                Log.e(tag, buildMessage(TYPE.ERROR, tag, msg, thr), thr);
    }

    public static void e(String msg, Throwable thr) {
        if (isEnable)
            Log.e(TAG, buildMessage(TYPE.ERROR, TAG, msg, thr), thr);
    }

    public static void v(String tag, String msg) {
        if (isEnable)
            if (tag == null || tag == "")
                v(msg);
            else
                Log.v(tag, buildMessage(TYPE.VERBOSE, tag, msg, null));
    }

    public static void v(String msg) {
        if (isEnable)
            Log.v(TAG, buildMessage(TYPE.VERBOSE, TAG, msg, null));
    }

    public static void v(String tag, String msg, Throwable thr) {
        if (isEnable)
            if (tag == null || tag == "")
                v(msg, thr);
            else
                Log.v(tag, buildMessage(TYPE.VERBOSE, tag, msg, thr), thr);
    }

    public static void v(String msg, Throwable thr) {
        if (isEnable)
            Log.v(TAG, buildMessage(TYPE.VERBOSE, TAG, msg, thr), thr);
    }

    public static void i(String tag, String msg) {
        if (isEnable)
            if (tag == null || tag == "")
                i(msg);
            else
                Log.i(tag, buildMessage(TYPE.INFO, tag, msg, null));
    }

    public static void i(String msg) {
        if (isEnable)
            Log.i(TAG, buildMessage(TYPE.INFO, TAG, msg, null));
    }

    public static void i(String tag, String msg, Throwable thr) {
        if (isEnable)
            if (tag == null || tag == "")
                i(msg, thr);
            else
                Log.i(tag, buildMessage(TYPE.INFO, tag, msg, thr), thr);
    }

    public static void i(String msg, Throwable thr) {
        if (isEnable)
            Log.i(TAG, buildMessage(TYPE.INFO, TAG, msg, thr), thr);
    }

    public static void w(Throwable thr) {
        if (isEnable)
            Log.w(TAG, buildMessage(TYPE.WARN, TAG, "", thr), thr);
    }

    public static void w(String tag, String msg) {
        if (isEnable)
            if (tag == null || tag == "")
                w(msg);
            else
                Log.w(tag, buildMessage(TYPE.WARN, tag, msg, null));
    }

    public static void w(String msg) {
        if (isEnable)
            Log.w(TAG, buildMessage(TYPE.WARN, TAG, msg, null));
    }

    public static void w(String tag, String msg, Throwable thr) {
        if (isEnable)
            if (tag == null || tag == "")
                w(msg, thr);
            else
                Log.w(tag, buildMessage(TYPE.WARN, tag, msg, thr), thr);
    }

    public static void w(String msg, Throwable thr) {
        if (isEnable)
            Log.w(TAG, buildMessage(TYPE.WARN, TAG, msg, thr), thr);
    }

    public static ExecutorService getExecutor() {
        return getExecutor();
    }

    public static void setExecutor(ExecutorService executor) {
        setExecutor(executor);
    }

    public static String getPath() {
        return path;
    }

    public static void setPath(String path) {
        LogUtils.path = path;
        System.out.println((new StringBuilder("LogUtils.path = ")).append(path).toString());
        createLogDir(path);
    }

    private static void createLogDir(String path) {
        Log.e("Path", path);
        if (TextUtils.isEmpty(path)) {
            Log.e("Error", "The path is not valid.");
            return;
        }
        File file = new File(path);
        boolean exist = file.getParentFile().exists();
        if (!exist) {
            boolean ret = file.getParentFile().mkdirs();
            if (!ret) {
                Log.e("Error", "The Log Dir can not be created!");
                return;
            }
            Log.i("Success", (new StringBuilder("The Log Dir was successfully created! -")).append(file.getParent()).toString());
        }
    }

    public static int getPolicy() {
        return policy;
    }

    public static void setPolicy(int policy) {
        LogUtils.policy = policy;
    }

    public static String getStackTraceString(Throwable tr)
    {
        return Log.getStackTraceString(tr);
    }

    public static String getTag()
    {
        return TAG;
    }

    public static void setTag(String tag)
    {
        TAG = tag;
    }

    public static boolean isEnabled()
    {
        return isEnable;
    }

    public static void setEnabled(boolean enabled)
    {
        isEnable = enabled;
    }

    public static boolean isLoggable(String tag, int level)
    {
        return Log.isLoggable(tag, level);
    }

    public static int println(int priority, String tag, String msg)
    {
        return Log.println(priority, tag, msg);
    }

    @SuppressLint("SimpleDateFormat") 
    public static void setPath(String logDirPath, String logFileBaseName, String logFileSuffix)
    {
        if (!TextUtils.isEmpty(logDirPath))
            LogUtils.logDirPath = logDirPath;
        if (!TextUtils.isEmpty(logFileBaseName))
            LogUtils.logFileBaseName = logFileBaseName;
        if (!TextUtils.isEmpty(logFileSuffix))
            LogUtils.logFileSuffix = logFileSuffix;
        Date myDate = new Date();
        SimpleDateFormat fdf = new SimpleDateFormat("yyyy-MM-dd");
        String myDateString = fdf.format(myDate);
        StringBuffer buffer = new StringBuffer();
        buffer.append(logDirPath);
        if (!logDirPath.endsWith("/"))
            buffer.append("/");
        buffer.append(myDateString);
        buffer.append(".");
        buffer.append(logFileSuffix);
        setPath(buffer.toString());
    }

    protected static void log2file(final String path, final String str)
    {
        if (executor == null)
            executor = Executors.newSingleThreadExecutor();
        if (executor != null)
            executor.execute(new Runnable() {

//                private final String val$path;
//                private final String val$str;

                public void run() {
                    PrintWriter out;
                    File file;
                    out = null;
                    file = LogUtils.GetFileFromPath(path);
                    try {
                        out = new PrintWriter(new BufferedWriter(new FileWriter(file, true)));
                        out.println(str);
                        out.flush();
//                        break MISSING_BLOCK_LABEL_78;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (out != null)
                        out.close();
                    /*
                    break MISSING_BLOCK_LABEL_86;
                    Exception exception;
                    exception;
                    if (out != null)
                        out.close();
                    throw exception;
                    if (out != null)
                        out.close();
                    */
                }

            /*
            {
                path = s;
                str = s1;
                super();
            }
            */
            });
    }

    private static File GetFileFromPath(String path)
    {
        File file = null;
        if (TextUtils.isEmpty(path))
        {
            Log.e("Error", "The path of Log file is Null.");
            return file;
        }
        file = new File(path);
        boolean isExist = file.exists();
        boolean isWritable = file.canWrite();
        if (isExist)
        {
            if (file.length() > logFileSize)
            {
                boolean isDelete = file.delete();
                if (isDelete)
                    return GetFileFromPath(path);
                Log.e(TAG, "delete logfile failed");
            }
            if (!isWritable)
                Log.e("Error", "The Log file can not be written.");
        } else
        {
            try
            {
                boolean ret = file.createNewFile();
                if (ret)
                    Log.i("Success", (new StringBuilder("The Log file was successfully created! -")).append(file.getAbsolutePath()).toString());
                else
                    Log.i("Success", (new StringBuilder("The Log file exist! -")).append(file.getAbsolutePath()).toString());
                isWritable = file.canWrite();
                if (!isWritable)
                    Log.e("Error", "The Log file can not be written.");
            }
            catch (IOException e)
            {
                Log.e("Error", "Failed to create The Log file.");
                e.printStackTrace();
            }
        }
        return file;
    }
}
