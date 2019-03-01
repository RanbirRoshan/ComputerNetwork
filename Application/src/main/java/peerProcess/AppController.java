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

            // perform handshake and abandon the processing in case handshake fails
            if (PerformHandshake () != eSocketReturns.E_SOCRET_SUCCESS){
                Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": " + PeerId + " fails performing handshake. THE PEER IS ABANDONED.");
                return;
            }

            // handshake is always followed by BitSetExchange
            if (PerformBitSetExchange() != eSocketReturns.E_SOCRET_SUCCESS){
                Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": " + PeerId + " fails performing BitSet Excahnge with peer: " + ClientPeerId+" . THE PEER IS ABANDONED.");
                return;
            }

            Thread.sleep(10000);

            ConnSocket.close();
        }
        catch (IOException | InterruptedException ex){
            System.out.println ("Unable to set keep alive on socket.");
            System.out.println (ex.getMessage());
        }
    }

    private eSocketReturns ProcessBitSetResponse (){

        BitFieldMessage                     bitFieldMsg;
        Message                             msg;
        peerProcess.PeerConfigurationData   data;

        try {
            msg = (Message) SocketInputStream.readObject();
        }
        catch (ClassNotFoundException | IOException ex){
            System.out.println ("*******************EXCEPTION*******************");
            System.out.println ("IOException occurred while de-serializing BitField message.");
            System.out.println (ex.getMessage());
            return  eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }

        if (msg.OperationType != eOperationType.OPERATION_BITFIELD.GetVal()){
            System.out.println ("Error in BitField message exchange received Operation Type: " + msg.OperationType +" when expecting :" + eOperationType.OPERATION_BITFIELD);
            return eSocketReturns.E_SOCRET_FAILED;
        }

        // not thaw we have verified the message to be of right type we can cast it into our actual message structure
        bitFieldMsg = (BitFieldMessage)msg;

        data = peerProcess.PeerMap.get(ClientPeerId);

        // the bit field file sate length should be same as the file iis same for all if they are not same we have some error in the system
        if (bitFieldMsg.BitField.length != data.FileState.length){
            System.out.println ("Error in BitField message exchange received Operation Type: " + msg.OperationType +" when expecting :" + eOperationType.OPERATION_BITFIELD);
            return eSocketReturns.E_SOCRET_FAILED;
        }

        // overwrite the current client file state info with the latest available info
        data.FileState = bitFieldMsg.BitField;

        return  eSocketReturns.E_SOCRET_SUCCESS;
    }

    private eSocketReturns PerformBitSetExchange (){

        BitFieldMessage    msg = new BitFieldMessage();

        msg.SetBitFieldInfo(peerProcess.PeerMap.get(PeerId).FileState);

        try {
            SocketOutStream.writeObject(msg);
            SocketOutStream.flush();
        }
        catch (IOException ex){
            System.out.println ("*******************EXCEPTION*******************");
            System.out.println ("IOException occurred while sending BitField message.");
            System.out.println (ex.getMessage());
            return  eSocketReturns.E_SOCRET_IO_EXCEPTION;

        }
        return  ProcessBitSetResponse();
    }

    private eSocketReturns WaitAndProcessHandshakeResponse (String pHandshakeHeader) {

        int             responseProtocol;
        HandshakeMsg    msg;

        try {
            msg = (HandshakeMsg) SocketInputStream.readObject();
        }
        catch (IOException | ClassNotFoundException ex){
            System.out.println ("*******************EXCEPTION*******************");
            System.out.println ("IOException occurred while de-serializing handshake message.");
            System.out.println (ex.getMessage());
            return  eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }

        if (!msg.GetHdrBuf().equals(pHandshakeHeader)){
            Logger.GetLogger().Log("Handshake failed. Received invalid handshake header.");
            return eSocketReturns.E_SOCRET_FAILED;
        }

        // if the peer is invalid as per known peer from configuration or the peer has the same ID as the current application
        if (!peerProcess.PeerMap.containsKey(msg.GetPeerId()) || msg.GetPeerId() == PeerId){
            Logger.GetLogger().Log("Handshake failed. Received invalid PeerId.");
            return eSocketReturns.E_SOCRET_FAILED;
        }

        ClientPeerId = msg.GetPeerId();

        Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": " + PeerId + " makes a connection to " + ClientPeerId);

        return eSocketReturns.E_SOCRET_SUCCESS;
    }

    private eSocketReturns PerformHandshake ()
    {
        try {
            HandshakeMsg    msg;
            final String    handshakeHeader = "P2PFILESHARINGPROJ";

            msg = new HandshakeMsg(handshakeHeader, PeerId);
            SocketOutStream.writeObject(msg);
            SocketOutStream.flush();

            return WaitAndProcessHandshakeResponse(handshakeHeader);
        }
        catch (IOException ex){
            System.out.println ("*******************EXCEPTION*******************");
            System.out.println ("IOException occurred while serializing handshake message.");
            System.out.println (ex.getMessage());
            return  eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }
    }
}
