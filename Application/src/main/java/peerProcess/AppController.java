package peerProcess;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

public class AppController extends Thread {

    private Socket    ConnSocket;

    AppController (Socket pSocket)
    {
        ConnSocket = pSocket;
    }

    public void run ()
    {
        if (!ConnSocket.isConnected()){
            System.out.println("A connection is not successful.");
            return;
        }

        System.out.println("A connection is successfully established.");

        try {
            ConnSocket.setKeepAlive(true);

            Thread.sleep(10000);

            ConnSocket.close();
        }
        catch (IOException | InterruptedException ex){
            System.out.println ("Unable to set keep alive on socket.");
            System.out.println (ex.getMessage());
        }
    }
}
