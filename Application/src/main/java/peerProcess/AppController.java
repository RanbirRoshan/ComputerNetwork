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
    private BroadcastStruct             HaveBroadcastList;
    private BroadcastStructData         LastBroadcastData;
    private final static String         HandshakeHeader = "P2PFILESHARINGPROJ";

    private peerProcess.PeerConfigurationData   ClientData;
    private peerProcess.PeerConfigurationData   SelfData;

    private ObjectOutputStream   SocketOutStream;
    private ObjectInputStream    SocketInputStream;

    class ResponseOutput {
        eSocketReturns      Error;
        Message             Response;
    }

    AppController (Socket pSocket, int pPeerId, boolean pMakesConnection, BroadcastStruct pHaveBroadcastList) {
        ConnSocket       = pSocket;
        PeerId           = pPeerId;
        ClientData       = null;
        SelfData         = peerProcess.PeerMap.get(pPeerId);
        MakesConnection  = pMakesConnection;
        HaveBroadcastList= pHaveBroadcastList;
        LastBroadcastData= null;
    }

    /**
     * <p>
     *     The thread execution function initiates the entire the entire communication with a client
     * </p>
     */
    public void run () {

        // debug helper
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

            ret = BroadcastActivity ();

            ret = SendActivity ();

            if (ret == eSocketReturns.E_SOCRET_SUCCESS)
                ret = ReceiveAndActActivity ();

            if (ret != eSocketReturns.E_SOCRET_SUCCESS)
                return  ret;

        } while (true);

        //return eSocketReturns.E_SOCRET_SUCCESS;
    }

    /**
     * <p>
     *     Generic implementation for all the types of broadcast activity
     * </p>
     *
     * @param pSrc Object containing broadcast info
     * @return The status of the operation.
     */
    private eSocketReturns SendBroadcastMsg (BroadcastStructData pSrc){

        if (pSrc.OperationType == eOperationType.OPERATION_HAVE.GetVal()){

            HaveMessage msg = new HaveMessage();
            msg.PieceId = (Integer)pSrc.Data;

            return SendObj(msg, "IOException occurred while sending Have message.");
        }

        return eSocketReturns.E_SOCRET_FAILED;
    }

    /**
     * <p>
     *     The procedure is responsible for broadcasting the have data to the client
     * </p>
     *
     * @return Status of the operations
     */
    private eSocketReturns BroadcastHaveData (){

        eSocketReturns ret = eSocketReturns.E_SOCRET_SUCCESS;

        while (LastBroadcastData.Next != null){

            ret = SendBroadcastMsg (LastBroadcastData.Next);

            if (ret != eSocketReturns.E_SOCRET_SUCCESS)
                return ret;

            LastBroadcastData = LastBroadcastData.Next;
        }

        return ret;
    }

    /**
     * <p>
     *     The procedure to trigger all the broadcast activities
     * </p>
     * @return the status of the operation
     */
    private eSocketReturns BroadcastActivity (){
        return BroadcastHaveData ();
    }

    /**
     * <p>
     *     The procedure finds the most significant bit that is not set in the input. The returned value is the position of the bit starting from the MSB as 0.
     * </p>
     *
     * @implNote The function assumes that there is at least a bit taht is not set in the given integer value
     *
     * @param pValue    The input integer value for calculation
     * @return The position
     */
    private byte GetBitSetPos (int pValue){

        int     val = ~pValue;
        byte    ans = 0;

        while (val != 0){
            val = val >>> 1;
            ans++;
        }

        return (byte)(peerProcess.BitPerBufVal - ans);
    }

    /**
     * <p>
     *     The procedure is responsible for transmitting the object to the client and handle exceptions while the operation is attempted
     * </p>
     *
     * @param pObj      The object to be sent to the client
     * @param pFailureMsg   The message to be printed on console in case of failure
     * @return  The end state of the operations.
     */
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

    /**
     * <p>
     *     The procedure for receiving the object from Socket Input Stream and handling the exceptions
     * </p>
     *
     * @param pFailureMsg   The message to be printed on the console
     * @return A object containing error code and received object in case of success
     */
    private ResponseOutput ReceiveObj (String pFailureMsg){

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

    /**
     * <p>
     *      The function is responsible for preparing the request for the given piece ID and sending it over to the client
     * </p>
     *
     * @param pPieceId The piece id that is being requested from the client
     *
     * @return The end status of the operation.
     */
    private eSocketReturns SendPieceRequest (int pPieceId){
        RequestMessage msg = new RequestMessage();

        msg.PieceId = pPieceId;

        return SendObj (msg, "IOException occurred while sending piece Request message.");
    }

    /**
     * <p>
     *     The function is responsible for preparing the message with piece data and sending it over to the client
     * </p>
     * 
     * @// TODO: 3/2/2019 send actual file data to the client instead of dummy data
     *
     * @param pPieceId The piece id that is to be sent
     *
     * @return the status of the operation
     */
    private eSocketReturns SendPieceResponse (int pPieceId){

        PieceMessage msg = new PieceMessage();
        byte[]       data;

        msg.PieceId = pPieceId;

        /****************************DUMMY FILLED DATA************************************/
        data = new byte[3];
        data[0] = 99;
        data [1] = 100;
        data [2] = 101;
        /****************************************************************/

        msg.SetPieceData(data);

        return SendObj(msg, "IOException occurred while sending piece message.");
    }

    /**
     * <p>
     *     The function is responsible for sending the requested piece to the client.
     * </p>
     * 
     * @// TODO: 3/2/2019 Implement choke unchoke and should sent a piece request to only one client
     * 
     * @param pMsg The string containing the message to be printed on console
     * @return The end status of the operation.
     */
    private eSocketReturns ProcessPieceRequest (RequestMessage pMsg){

        // the situation is never possible except for code bugs
        if (pMsg.PieceId < 0)
            return PrintErrorMessageToConsole ("Piece Request With Invalid Content");

        return SendPieceResponse (pMsg.PieceId);
    }

    /**
     * <p>
     *     The function is responsible for handling the Piece/Packet sent by the client in response for the request made from the server
     * </p>
     * 
     * @// TODO: 3/2/2019 Validate if the client has sent the correct package as per the request. Implement one packet at a time.
     * 
     * @param pMsg The message as recieved from client
     * @return The end status of the operation.
     */
    private eSocketReturns ProcessPieceResponse (PieceMessage pMsg){

        if (pMsg.GetPieceData() == null || pMsg.PieceId == -1)
            return PrintErrorMessageToConsole("Piece Response With Invalid Content");

        // The process just receive a piece/package this must be informed to all other online peers
        HaveBroadcastList.AddForBroadcast(eOperationType.OPERATION_HAVE.GetVal(), pMsg.PieceId);

        // logging as per the specification
        Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": Peer " + PeerId + " has downloaded the piece " + pMsg.PieceId + " from  " + ClientPeerId + ". Now the number of pieces it has is " + ++(SelfData.NumPiecesAvailable));

        // updating the bit value so that the same packet is requested again from any other client
        SelfData.FileState[pMsg.PieceId /peerProcess.BitPerBufVal] |= (1 << peerProcess.BitPerBufVal - pMsg.PieceId %peerProcess.BitPerBufVal -1);

        return  eSocketReturns.E_SOCRET_SUCCESS;
    }

    /**
     * <p>
     *     This is just a helping procedure to log the error message on the console
     * </p>
     *
     * @param pErrorMsg     String containing the message to be printed
     * @return  Always returns E_SOCRET_FAILED
     */
    private eSocketReturns PrintErrorMessageToConsole (String pErrorMsg){
        System.out.println ("*******************Error*******************");
        System.out.println (pErrorMsg);
        return eSocketReturns.E_SOCRET_FAILED;
    }

    /**
     * <p>
     *     'Have' is a message that a client sends when it has successfully received a piece/packet from any of its peers.
     *     The function handles all the accounting activity for the have message. The activity includes updating the associated
     *     bitset value for the piece that corresponds to the client. Update the number of pieces available with the client and
     *     records/logs the activity as per the specification document.
     * </p>
     *
     * @param pMsg The message as received from the client
     * @return The end status of the operation.
     */
    private  eSocketReturns ProcessHaveRequest (HaveMessage pMsg){

        // the below scenario is not possible
        if (pMsg.PieceId < 0)
            return PrintErrorMessageToConsole("Piece Request With Invalid Content");

        // update the client piece bit for the given piece ID
        ClientData.FileState[pMsg.PieceId/peerProcess.BitPerBufVal] = ClientData.FileState[pMsg.PieceId/peerProcess.BitPerBufVal] | (1 << (peerProcess.BitPerBufVal - (pMsg.PieceId%peerProcess.BitPerBufVal) - 1));

        // the number of pieces with the client has increased by 1
        ClientData.NumPiecesAvailable++;

        // log the message as per specification
        Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": Peer " + PeerId + " received the 'have' message from " + ClientPeerId + " for the piece " + pMsg.PieceId);

        return  eSocketReturns.E_SOCRET_SUCCESS;
    }

    /**
     * <p>
     *     The function is responsible for triggering object fetch from the client and then calling the suitable handler
     *     for the same.
     * </p>
     *
     * @return E_SOCRET_SUCCESS if successful else a relevant error code. The function will return fail if an unsupported/invalid operation request is received
     */
    private eSocketReturns ReceivePacket(){

        ResponseOutput ret;

        ret = ReceiveObj("IOException occurred while de-serializing *** message.");

        // error receiving the object
        if (ret.Error != eSocketReturns.E_SOCRET_SUCCESS)
            return ret.Error;

        // handling the client data by operation type
        if (ret.Response.OperationType == eOperationType.OPERATION_PIECE.GetVal())
            return ProcessPieceResponse((PieceMessage) ret.Response);

        if (ret.Response.OperationType == eOperationType.OPERATION_REQUEST.GetVal())
            return ProcessPieceRequest ((RequestMessage) ret.Response);

        if (ret.Response.OperationType == eOperationType.OPERATION_HAVE.GetVal())
            return ProcessHaveRequest ((HaveMessage) ret.Response);

        return eSocketReturns.E_SOCRET_FAILED;
    }

    /**
     * @// TODO: 3/2/2019 Improvise this
     * @return The end status of the operation.
     */
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

    /**
     * @// TODO: 3/2/2019 Improvise this
     * @return The end status of the operation.
     */
    private eSocketReturns ReceiveAndActActivity () {
        return ReceivePacket();
    }

    /**
     *
     * @// TODO: 3/2/2019 The functionaliy for counting the number of blocks the client already has is yet to be implemented. The extra bit that may be there in the header should also be accounted.
     *
     * @return returns the status for the requested operation
     */
    private eSocketReturns ProcessBitSetResponse (){

        BitFieldMessage bitFieldMsg;
        ResponseOutput  out;

        out = ReceiveObj("IOException occurred while de-serializing BitField message.");

        if (out.Error != eSocketReturns.E_SOCRET_SUCCESS)
            return out.Error;

        // the bit request can only get bit response and nothing else
        if (out.Response.OperationType != eOperationType.OPERATION_BITFIELD.GetVal())
            return PrintErrorMessageToConsole("Error in BitField message exchange received Operation Type: " + out.Response.OperationType +" when expecting :" + eOperationType.OPERATION_BITFIELD);

        // not thaw we have verified the message to be of right type we can cast it into our actual message structure
        bitFieldMsg = (BitFieldMessage)out.Response;

        ClientData = peerProcess.PeerMap.get(ClientPeerId);

        // the bit field file sate length should be same as the file iis same for all if they are not same we have some error in the system
        if (bitFieldMsg.BitField.length != ClientData.FileState.length)
            return PrintErrorMessageToConsole("Error in BitField message exchange received Operation Type: " + out.Response.OperationType +" when expecting :" + eOperationType.OPERATION_BITFIELD);

        // overwrite the current client file state info with the latest available info
        ClientData.FileState = bitFieldMsg.BitField;

        for (int iter = 0; iter < ClientData.FileState.length; iter++){
            //count the number of packets the client already has
            ClientData.NumPiecesAvailable = 0;
        }

        return  eSocketReturns.E_SOCRET_SUCCESS;
    }

    /**
     * <p>
     *     The procedure is responsible for ensuring that the process communicates its bit field with its client and receives and updates the client bit field values.
     * </p>
     *
     * @return Status of the operation
     */
    private eSocketReturns PerformBitSetExchange (){

        eSocketReturns ret;

        BitFieldMessage    msg = new BitFieldMessage();

        msg.SetBitFieldInfo(peerProcess.PeerMap.get(PeerId).FileState);

        //recording the current end broadcast element as the current client needs data only beyond this
        LastBroadcastData = HaveBroadcastList.GetLast();

        ret = SendObj(msg, "IOException occurred while sending BitField message.");

        // if the bitField were sent successfully wait and process the clients response BitFields
        if (ret == eSocketReturns.E_SOCRET_SUCCESS)
            return  ProcessBitSetResponse();

        return  ret;
    }

    /**
     * <p>
     *     The procedures waits for the client handshake message validates it and returns the status as response
     * </p>
     *
     * @return the status of operation
     */
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

    /**
     * <p>
     *     The procedure is responsible for initiating and completing the handshake process. This involves sending handshake message and processing the client handshake message as well.
     * </p>
     *
     * @return The end status for the operation
     */
    private eSocketReturns PerformHandshake (){
        eSocketReturns  ret;
        HandshakeMsg    msg;

        msg = new HandshakeMsg(HandshakeHeader, PeerId);

        ret = SendObj(msg, "IOException occurred while serializing handshake message.");

        // if teh handshake data was sent successfully from the process wait and validate the client handshake response
        if (ret == eSocketReturns.E_SOCRET_SUCCESS)
            return WaitAndProcessHandshakeResponse();

        return ret;
    }
}
