package peerProcess;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Calendar;

public class AppController extends Thread {

    private Socket                      ConnSocket;
    private int                         PeerId;
    private int                         ClientPeerId;
    private boolean                     MakesConnection;
    private final static String         HandshakeHeader = "P2PFILESHARINGPROJ";

    private peerProcess.PeerConfigurationData   ClientData;
    private peerProcess.PeerConfigurationData   SelfData;

    private ObjectOutputStream   SocketOutStream;
    private ObjectInputStream    SocketInputStream;

    class ResponseOutput {
        eSocketReturns      Error;
        Message             Response;
    }

    AppController (Socket pSocket, int pPeerId, boolean pMakesConnection) {
        ConnSocket       = pSocket;
        PeerId           = pPeerId;
        ClientData       = null;
        SelfData         = peerProcess.PeerMap.get(pPeerId);
        MakesConnection  = pMakesConnection;
    }

    public void run () {
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

            // handshake is always followed by BitSetExchange
            if (PerformFileOp () != eSocketReturns.E_SOCRET_SUCCESS){
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

    private eSocketReturns PerformFileOp () {
        eSocketReturns ret;

        // perform send receive activity
        do {

            ret = SendActivity ();

            if (ret == eSocketReturns.E_SOCRET_SUCCESS)
                ret = ReceiveAndActActivity ();

            if (ret != eSocketReturns.E_SOCRET_SUCCESS)
                return  ret;

        } while (true);

        //return eSocketReturns.E_SOCRET_SUCCESS;
    }

    private byte GetBitSetPos (int pValue){
        int     val = ~pValue;
        byte    ans = 0;

        while (val != 0){
            val = val >>> 1;
            ans++;
        }

        return (byte)(peerProcess.BitPerBufVal - ans);
    }

    private eSocketReturns SendObj (Object pObj, String pFailureMsg){

        try {
            SocketOutStream.writeObject(pObj);
            SocketOutStream.flush();
        }
        catch (IOException ex){
            System.out.println (pFailureMsg);
            System.out.println ("*******************EXCEPTION*******************");
            System.out.println (ex.getMessage());
            return  eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }

        return  eSocketReturns.E_SOCRET_SUCCESS;
    }

    private ResponseOutput RecieveObj (String pFailureMsg){

        ResponseOutput out = new ResponseOutput();

        try {
            out.Response = (Message) SocketInputStream.readObject();
            out.Error    = eSocketReturns.E_SOCRET_SUCCESS;
        }
        catch (ClassNotFoundException | IOException ex){
            System.out.println ("*******************EXCEPTION*******************");
            System.out.println (pFailureMsg);
            System.out.println (ex.getMessage());
            out.Response = null;
            out.Error = eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }

        return out;
    }

    private eSocketReturns SendPieceRequest (int pPieceId){
        RequestMessage msg = new RequestMessage();

        msg.PieceNum = pPieceId;

        return SendObj (msg, "IOException occurred while sending piece Request message.");
    }

    private eSocketReturns SendPieceResponse (int pPieceId){
        PieceMessage msg = new PieceMessage();
        byte[] data;

        msg.PieceNum = pPieceId;
        data = new byte[3];
        data[0] = 99;
        data [1] = 100;
        data [2] = 101;

        msg.SetPieceData(data);

        return SendObj(msg, "IOException occurred while sending piece message.");
    }

    private eSocketReturns ProcessPieceRequest (RequestMessage pMsg){
        if (pMsg.PieceNum < 0){
            System.out.println ("*******************Error*******************");
            System.out.println ("Piece Request With Invalid Content");
            return eSocketReturns.E_SOCRET_UNKNOWN;
        }
        return SendPieceResponse (pMsg.PieceNum);
    }

    private eSocketReturns ProcessPieceResponse (PieceMessage pMsg){

        if (pMsg.GetPieceData() == null || pMsg.PieceNum == -1){
            System.out.println ("*******************Error*******************");
            System.out.println ("Piece Response With Invalid Content");
            return eSocketReturns.E_SOCRET_UNKNOWN;
        }

        // updating the bit value
        SelfData.FileState[pMsg.PieceNum/peerProcess.BitPerBufVal] |= (1 << peerProcess.BitPerBufVal - pMsg.PieceNum%peerProcess.BitPerBufVal -1);

        return  eSocketReturns.E_SOCRET_SUCCESS;
    }

    private eSocketReturns RecievePacket (){
        ResponseOutput ret;

        ret = RecieveObj("IOException occurred while de-serializing *** message.");

        if (ret.Error != eSocketReturns.E_SOCRET_SUCCESS)
            return ret.Error;

        if (ret.Response.OperationType == eOperationType.OPERATION_PIECE.GetVal()){
            return ProcessPieceResponse((PieceMessage) ret.Response);
        } else if (ret.Response.OperationType == eOperationType.OPERATION_REQUEST.GetVal()){
            return ProcessPieceRequest ((RequestMessage) ret.Response);
        }

        return eSocketReturns.E_SOCRET_FAILED;
    }

    private eSocketReturns SendActivity () {

        int block_num = -1;
        int packet_num;
        int bit_num;

        for (int iter = 0; iter < SelfData.FileState.length; iter++)
        {
            if (SelfData.FileState[iter] != -1){
                block_num = iter;
                break;
            }
        }

        if (block_num == -1)
            return eSocketReturns.E_SOCRET_SUCCESS;

        bit_num = GetBitSetPos(SelfData.FileState[block_num]);

        packet_num = (block_num * peerProcess.BitPerBufVal) + bit_num;

        return SendPieceRequest (packet_num);
    }

    private eSocketReturns ReceiveAndActActivity () {
        return RecievePacket();
    }

    private eSocketReturns ProcessBitSetResponse (){

        BitFieldMessage                     bitFieldMsg;
        Message                             msg;

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

        ClientData = peerProcess.PeerMap.get(ClientPeerId);

        // the bit field file sate length should be same as the file iis same for all if they are not same we have some error in the system
        if (bitFieldMsg.BitField.length != ClientData.FileState.length){
            System.out.println ("Error in BitField message exchange received Operation Type: " + msg.OperationType +" when expecting :" + eOperationType.OPERATION_BITFIELD);
            return eSocketReturns.E_SOCRET_FAILED;
        }

        // overwrite the current client file state info with the latest available info
        ClientData.FileState = bitFieldMsg.BitField;

        return  eSocketReturns.E_SOCRET_SUCCESS;
    }

    private eSocketReturns PerformBitSetExchange (){
        eSocketReturns ret;

        BitFieldMessage    msg = new BitFieldMessage();

        msg.SetBitFieldInfo(peerProcess.PeerMap.get(PeerId).FileState);

        ret = SendObj(msg, "IOException occurred while sending BitField message.");

        if (ret == eSocketReturns.E_SOCRET_SUCCESS)
            return  ProcessBitSetResponse();

        return  ret;
    }

    private eSocketReturns WaitAndProcessHandshakeResponse () {

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

        if (!msg.GetHdrBuf().equals(HandshakeHeader)){
            Logger.GetLogger().Log("Handshake failed. Received invalid handshake header.");
            return eSocketReturns.E_SOCRET_FAILED;
        }

        // if the peer is invalid as per known peer from configuration or the peer has the same ID as the current application
        if (!peerProcess.PeerMap.containsKey(msg.GetPeerId()) || msg.GetPeerId() == PeerId){
            Logger.GetLogger().Log("Handshake failed. Received invalid PeerId.");
            return eSocketReturns.E_SOCRET_FAILED;
        }

        ClientPeerId = msg.GetPeerId();

        // logging as per log requirement specification
        if (MakesConnection)
            Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": " + PeerId + " makes a connection to " + ClientPeerId);
        else
            Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": " + PeerId + " is connected from " + ClientPeerId);

        return eSocketReturns.E_SOCRET_SUCCESS;
    }

    private eSocketReturns PerformHandshake (){
        eSocketReturns  ret;
        HandshakeMsg    msg;
        msg = new HandshakeMsg(HandshakeHeader, PeerId);

        ret = SendObj(msg, "IOException occurred while serializing handshake message.");

        if (ret == eSocketReturns.E_SOCRET_SUCCESS)
            return WaitAndProcessHandshakeResponse();

        return ret;
    }
}
