package peerProcess;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Calendar;

public class AppController extends Thread {

    private Socket          ConnSocket;
    private int             PeerId;
    private int             ClientPeerId;

    private ObjectOutputStream   SocketOutStream;
    private ObjectInputStream    SocketInputStream;

    AppController (Socket pSocket, int pPeerId)
    {
        ConnSocket       = pSocket;
        PeerId           = pPeerId;
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

            SocketOutStream   = new ObjectOutputStream (ConnSocket.getOutputStream());
            SocketInputStream = new ObjectInputStream  (ConnSocket.getInputStream());

            SocketOutStream.flush();

            PerformHandshake ();

            Thread.sleep(10000);

            ConnSocket.close();
        }
        catch (IOException | InterruptedException ex){
            System.out.println ("Unable to set keep alive on socket.");
            System.out.println (ex.getMessage());
        }
    }

    private eSocketReturns PerformHandshake ()
    {
        try {
            HandshakeMsg        msg;
            int                 responseProtocol;

            String handshakeHeader = "P2PFILESHARINGPROJ";
            msg = new HandshakeMsg(handshakeHeader, PeerId);

            SocketOutStream.writeInt(eProtocolType.PROTOCOL_HANDSHAKE.GetVal());
            SocketOutStream.writeObject(msg);
            SocketOutStream.flush();

            responseProtocol = SocketInputStream.readInt ();

            if (responseProtocol != eProtocolType.PROTOCOL_HANDSHAKE.GetVal()){
                Logger.GetLogger().Log("Handshake Message failed. Received Protocol Type: " + responseProtocol + " when expecting 1.")
                return eSocketReturns.E_SOCRET_FAILED;
            }

            msg = (HandshakeMsg) SocketInputStream.readObject();

            if (!msg.GetHdrBuf().equals(handshakeHeader)){
                Logger.GetLogger().Log("Handshake failed. Received invalid handshake header.");
                return eSocketReturns.E_SOCRET_FAILED;
            }

            if (!peerProcess.PeerMap.containsKey(msg.GetPeerId()) || msg.GetPeerId() == PeerId){
                Logger.GetLogger().Log("Handshake failed. Received invalid PeerId.");
                return eSocketReturns.E_SOCRET_FAILED;
            }

            ClientPeerId = msg.GetPeerId();

            Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": " + PeerId + " makes a connection to " + ClientPeerId);
        }
        catch (IOException | ClassNotFoundException ex){
            System.out.println ("*******************EXCEPTION*******************");
            System.out.println ("IOException occurred while serializing or de-serializing handshake message.");
            System.out.println (ex.getMessage());
            return  eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }

        return eSocketReturns.E_SOCRET_SUCCESS;
    }
}
