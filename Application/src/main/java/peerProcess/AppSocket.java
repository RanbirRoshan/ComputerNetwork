package peerProcess;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class AppSocket {

    private Socket      vSocket;
    private Integer     vPortNumber;
    private String      vHostString;

    /**
     * Constructor for the class
     *
     * @param pAddress      string address of the host
     * @param pPortNumber   integer port number
     *
     *
     */
    public void AppSocket(String pAddress, int pPortNumber)
    {
        vHostString = pAddress;
        vPortNumber = pPortNumber;
    }

    /**
     * The function associates a socket to the class
     *
     * @return eSocketReturn
     * @throws UnknownHostException
     * @throws IOException
     */
    public eSocketReturns Initialize ()
    {
        try {
            vSocket = new Socket(InetAddress.getByName(vHostString), vPortNumber);
        }
        catch (IOException ex){
            System.out.println ("*******************EXCEPTION*******************");
            System.out.println ("IOException occurred while creating socket.");
            return eSocketReturns.E_SOCRET_FAILED;
        }

        return eSocketReturns.E_SOCRET_SUCCESS;
    }

    /**
     * Tries to close the connection and return the execution result
     *
     * @return  enum value that maps to the end status
     *          The following are the possible end states:
     *              - E_SOCRET_SUCCESS
     *              - E_SOCRET_IOBLOCK
     */
    public eSocketReturns CloseSocket ()
    {
        eSocketReturns retStatus = eSocketReturns.E_SOCRET_UNKNOWN;

        try {
            vSocket.close ();
            retStatus = eSocketReturns.E_SOCRET_SUCCESS;
        } catch (IOException e){
            retStatus = eSocketReturns.E_SOCRET_IOBLOCK;
            System.out.println ("*******************EXCEPTION*******************");
            System.out.println ("IOException occurred while closing a connection.");
            System.out.println (e.getMessage());
        }

        return retStatus;
    }
}