package peerProcess;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The logger class provides the log file generation for the project. It provides the following feature:
 *      Opening and closing log files.
 *      Renaming old log files if they exits.
 *      Handling indents for the requests.
 *      The operations of the class are thread safe. But the use has to make sure that initialization and close/destroy happens in when no other thread are running.
 *
 * @apiNote The class follows the singleton design pattern.
 *
 * @apiNote The
 *
 */
public class Logger {

    private String              vFileName;
    private AtomicInteger       vIndent;
    private RandomAccessFile    vFile;
    private boolean             vInitialized;
    private static final String vEndOfLine = "\r\n";

    // global for the class
    private static Semaphore    gMutexlock = new Semaphore(1);
    private static Logger       gLogger = null;
    private static int          gLockKey;

    /**
     * Creates the logger interface for the application.
     *
     */
    private Logger(){

        vInitialized = false;
        vFile       = null;
        vIndent     = new AtomicInteger(0);
    }

    static Logger GetLogger ()
    {
        if (gLogger == null)
            gLogger = new Logger();

        return gLogger;
    }

    private eLoggerErrors CreateNewLogFile (Path pPath){

        FileChannel fChannel;
        FileLock    fLock;
        File        newFile;

        newFile = new File (vFileName);

        try {

            Files.createFile(pPath);

            vFile = new RandomAccessFile(newFile, "rws");

            fChannel = vFile.getChannel();

            fLock = fChannel.tryLock();

            if (fLock == null){
                System.out.println("Executable could not acquire lock on the log file.");
                return eLoggerErrors.E_LE_FAILED;
            }
        }
        catch (IOException e)
        {
            System.out.println ("*******************EXCEPTION*******************");
            System.out.println (e.getMessage());
            return eLoggerErrors.E_LE_FAILED;
        }

        return eLoggerErrors.E_LE_SUCCESS;
    }

    private eLoggerErrors RenameOldLogFile (int pClientId, Path pPath){

        int         count   = 1;
        Path        targetPath;
        String      newFileName;
        boolean     retry = true;

        do {
            newFileName = String.format("log_peer_%d_%d.log", pClientId, count);

            targetPath = Paths.get (newFileName);

            // rename old logfile to newFileName only if there is no file with same name in the current directory
            if (Files.notExists (targetPath)){

                try {
                    Files.move(pPath, targetPath);
                }
                catch (Exception e){
                    System.out.println ("*******************EXCEPTION*******************");
                    System.out.println("Old log file exists. Unable to rename it to new file");
                    System.out.println(e.getMessage());
                    return eLoggerErrors.E_LE_FAILED;
                }

                retry = false;
            }

            count++;
        } while (retry);

        return eLoggerErrors.E_LE_SUCCESS;
    }

    /**
     * Initializes the logger class for use.
     *
     * <p>
     *     No API of logger can be used without initialization. If an initialization fails the logger cannot be re-initialized. The logger interface creates a new log file. The name of the file is of the form "log_peer_{pClientId}.log". If a file with same name already exists, the existing file is renamed to another file with a number added to it as suffix. The logger append the write requests to the file. The operation of the logger is thread safe for atomic write requests. Logger at present does not guarantees the sequence of multiple write requests from same thread in a multi threaded environment.
     * </p>
     *
     * @return The returned value is from the @eLoggerErrors enum and denotes the end-result state for the requested operation.
     */
    eLoggerErrors Initialize(int pClientId)
    {
        Path            srcPath;
        eLoggerErrors   ret = eLoggerErrors.E_LE_SUCCESS;

        // re initialization is not allowed
        if (vFile != null || vInitialized)
            return eLoggerErrors.E_LE_ALREADY_INITIALIZED;

        vInitialized = true;

        vFileName   = "log_peer_" + pClientId + ".log";

        srcPath  = Paths.get (vFileName);
        srcPath = srcPath.toAbsolutePath();

        if (!Files.notExists(srcPath))
             ret = RenameOldLogFile(pClientId, srcPath);

        if (ret == eLoggerErrors.E_LE_SUCCESS)
            ret = CreateNewLogFile(srcPath);

        return  ret;
    }

    /**
     * Checks for initialization or file closed error.
     *
     * @return The returned value is from the @eLoggerErrors enum and denotes the end-result state for the requested operation.
     */
    private eLoggerErrors CheckInitializedClosed ()
    {
        if (!vInitialized)
            return eLoggerErrors.E_LE_UNINITIALIZED;

        if (vFile == null)
            return eLoggerErrors.E_LE_CLOSED;

        return eLoggerErrors.E_LE_SUCCESS;
    }

    /**
     * Close the log file.
     *
     * @return The returned value is from the @eLoggerErrors enum and denotes the end-result state for the requested operation.
     */
    eLoggerErrors Close()
    {

        eLoggerErrors retStatus;

        retStatus = CheckInitializedClosed();

        if (retStatus != eLoggerErrors.E_LE_SUCCESS)
            return retStatus;

        try {
            vFile.close();
        }
        catch (Exception e){
            System.out.println("Error trying to close the log file");
            System.out.println (e.getMessage());
            return eLoggerErrors.E_LE_FAILED;
        }

        vFile   = null;
        gLogger = null;

        return retStatus;
    }

    /**
     * The function provides a way to gain control over the logger across multiple log operations. However take a lock on the logger will hamper progress of other thread and decrease performance. If not used judiciously can lead to deadlocks in the program.
     *
     * @return A int value. 0 indicates failure else it returns a integer key that is required to release the lock
     */
    int GetExclusiveRights ()
    {
        Random  random = new Random();

        try {
            gMutexlock.acquire();
        }
        catch (InterruptedException ex){
            return 0;
        }

        gLockKey = random.nextInt();

        return gLockKey;
    }

    /**
     * The function gives away the exclusive right over the logger.
     *
     * @param pLockKey The key provided by the API during the lock operation. The lock will not be released in case the key dont match.
     * @return
     */
    eLoggerErrors GiveUpExclusiveRights (int pLockKey)
    {
        if (pLockKey != gLockKey)
            return eLoggerErrors.E_LE_FAILED;

        gMutexlock.release();

        return eLoggerErrors.E_LE_SUCCESS;
    }

    private String IndentMsg (String pLogMessage){

        int     indentValue;

        indentValue = vIndent.intValue();

        if (indentValue < 1)
            return pLogMessage;

        String indent = "";

        for (int iter = 0; iter < indentValue; iter++){
            indent += "\t";
        }

        pLogMessage = indent + pLogMessage;

        indent = "\n"+indent;

        // for log to be proper ever newline in the message should also be properly indented
        return pLogMessage.replace("\n", indent);
    }

    /**
     * Logs the message to the message file. The data is appended at the end. The operation is thread safe. But no guarantee of the same to two consecutive calls to be printed one after another in a multi threaded environment. If the user wants to achieve this then user is expected to use LoggerLock API.
     *
     * @param pLogMessage
     * @return
     */
    eLoggerErrors Log(String pLogMessage){

        eLoggerErrors ret;

        ret = CheckInitializedClosed();

        if (ret!= eLoggerErrors.E_LE_SUCCESS)
            return ret;

        try {

            pLogMessage = IndentMsg(pLogMessage);

            pLogMessage = pLogMessage + vEndOfLine;

            // must not allow file write in parallel
            gMutexlock.acquire();
            vFile.write(pLogMessage.getBytes());
            //System.out.println(pLogMessage);
            gMutexlock.release();
        }
        catch (IOException | InterruptedException e){
            System.out.println ("*******************EXCEPTION*******************");
            if (e instanceof IOException)
                System.out.println("Encountered exception while logging.");
            else
                System.out.println("An interrupt was received.");
            System.out.println(e.getMessage());
            return eLoggerErrors.E_LE_FAILED;
        }

        return  ret;
    }

    /**
     * This is a thread safe code to change the value of indent of logger.
     *
     * @param pCount    the count by which indent needs to be changed. positive value for increase and -ve for decrease
     */
    eLoggerErrors Indent (int pCount){

        int expectedValue;
        int newValue;

        do {
            expectedValue = vIndent.intValue();

            newValue = vIndent.intValue() + pCount;

            // the indent value can never become negative
            if (newValue < 0)
                return eLoggerErrors.E_LE_FAILED;

        }while (!vIndent.compareAndSet(expectedValue, newValue));

        return eLoggerErrors.E_LE_SUCCESS;
    }
}
