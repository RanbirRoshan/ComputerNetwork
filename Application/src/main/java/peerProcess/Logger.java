package peerProcess;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

enum eLoggerErrors{
    E_LE_SUCCESS,
    //E_LE_UNKNOWN,
    E_LE_ALREADY_INITIALIZED,
    E_LE_FAILED,
    E_LE_UNINITIALIZED,
    E_LE_CLOSED,
}

public class Logger {

    private String              vFileName;
    private int                 vIndent;
    private RandomAccessFile    vFile;
    private boolean             vIntialized;
    private Integer             vCID;  // the client ID
    private static final String endOfLine = "\r\n";

    /**
     * Creates the logger interface for the application.
     * <p>
     *     The logger interface creates a new log file. The name of the file is of the form "log_peer_{pClientId}.log". If a file with same name already exists, the existing file is renamed to another file with a number added to it as suffix. The logger append the write requests to the file. The operation of the logger is thread safe for atomic write requests. Logger at present does not guarantees the sequence of multiple write requests from same thread in a multi threaded environment.
     * </p>
     *
     * @param pClientId     The unique identification number of the client.
     */
    Logger(int pClientId){
        vIntialized = false;
        vFileName   = "log_peer_" + pClientId + ".log";
        vFile       = null;
        vCID        = pClientId;
        vIndent     = 0;
    }

    /**
     * Initializes the logger class for use.
     *
     * <p>
     *     No API of logger can be used without initialization. If an initialization fails the logger cannot be re-initialized.
     * </p>
     *
     * @return The returned value is from the @eLoggerErrors enum and denotes the end-result state for the requested operation.
     */
    eLoggerErrors Initialize()
    {
        int         count   = 1;
        Path        srcPath;
        Path        targetPath;
        FileChannel fChannel;
        FileLock    fLock;
        File        newFile;
        String      newFileName;


        // re initialization is not allowed
        if (vFile != null || vIntialized)
            return eLoggerErrors.E_LE_ALREADY_INITIALIZED;

        vIntialized = true;

        srcPath  = Paths.get (vFileName);
        srcPath = srcPath.toAbsolutePath();

        if (!Files.notExists(srcPath)) {
            boolean retry = true;
            do {

                newFileName = String.format("log_peer_%s_%d.log", vCID.toString(), count);

                targetPath = Paths.get (newFileName);

                // rename old logfile to newFileName only if there is no file with same name in the current directory
                if (Files.notExists (targetPath)){

                    try {
                        Files.move(srcPath, targetPath);
                    }
                    catch (Exception e){
                        System.out.println("Old log file exists. Unable to rename it to new file");
                        System.out.println(e.getMessage());
                        return eLoggerErrors.E_LE_FAILED;
                    }

                    retry = false;
                }

                count++;
            } while (retry);
        }

        newFile = new File (vFileName);

        try {

            Files.createFile(srcPath);

            vFile = new RandomAccessFile(newFile, "rw");

            fChannel = vFile.getChannel();

            fLock = fChannel.tryLock();

            if (fLock == null){
                System.out.println("Executable could not acquire lock on the log file.");
                return eLoggerErrors.E_LE_FAILED;
            }
        }
        catch (IOException e)
        {
            return eLoggerErrors.E_LE_FAILED;
        }

        return eLoggerErrors.E_LE_SUCCESS;
    }

    /**
     * Checks for initialization or file closed error.
     *
     * @return The returned value is from the @eLoggerErrors enum and denotes the end-result state for the requested operation.
     */
    private eLoggerErrors CheckInitializedClosed ()
    {
        if (!vIntialized)
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

        vFile = null;

        return retStatus;
    }

    eLoggerErrors Log(String pLogMessage){
        eLoggerErrors ret;

        ret = CheckInitializedClosed();

        if (ret!= eLoggerErrors.E_LE_SUCCESS)
            return ret;

        try {
            if (vIndent > 0)
            {
                String indent = "";
                for (int iter = 0; iter < vIndent; iter++){
                    indent += "\t";
                }
                vFile.write(indent.getBytes());
                indent = "\n"+indent;

                pLogMessage = pLogMessage.replace("\n", indent);
            }
            vFile.write(pLogMessage.getBytes());
            vFile.write(endOfLine.getBytes());
        }
        catch (IOException e){
            System.out.println("Encountered exception while logging.");
            System.out.println(e.getMessage());
            return eLoggerErrors.E_LE_FAILED;
        }

        return  ret;
    }

    void Indent (boolean pForward, int pCount){

        if (pCount < 0 || pCount < vIndent)
            return;

        if (pForward)
            vIndent += pCount;
        else
            vIndent -= pCount;
    }
}
