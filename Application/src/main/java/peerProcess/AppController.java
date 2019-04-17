package peerProcess;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Calendar;

public class AppController extends Thread {

    private Socket ConnSocket;
    private RandomAccessFile DatFile;
    private int RequestedPieceId;
    private int PeerId;
    private int ClientPeerId;
    private boolean MakesConnection;
    private BroadcastStruct HaveBroadcastList;
    private BroadcastStructData LastBroadcastData;
    private final static String HandshakeHeader = "P2PFILESHARINGPROJ";

    private peerProcess.PeerConfigurationData ClientData;
    private peerProcess.PeerConfigurationData SelfData;

    private ObjectOutputStream SocketOutStream;
    private ObjectInputStream SocketInputStream;

    class ResponseOutput {
        eSocketReturns Error;
        Message Response;
    }

    AppController(Socket pSocket, int pPeerId, boolean pMakesConnection, BroadcastStruct pHaveBroadcastList) {
        ConnSocket = pSocket;
        PeerId = pPeerId;
        ClientData = null;
        SelfData = peerProcess.PeerMap.get(pPeerId);
        MakesConnection = pMakesConnection;
        HaveBroadcastList = pHaveBroadcastList;
        LastBroadcastData = pHaveBroadcastList.GetLast();
        RequestedPieceId = -1;

        try {
            DatFile = new RandomAccessFile(new java.io.File(peerProcess.FileName), "rw");
        } catch (FileNotFoundException ex) {
            //System.out.println("Failed to open data file for IO.");
            DatFile = null;
        }
    }

    /**
     * <p>
     * The thread execution function initiates the entire the entire communication
     * with a client
     * </p>
     */
    public void run() {

        // debug helper
        if (!ConnSocket.isConnected()) {
            //System.out.println("A connection is not successful.");
            return;
        }

        //System.out.println("A connection is successfully established.");

        try {

            ConnSocket.setKeepAlive(true);

            SocketOutStream = new ObjectOutputStream(ConnSocket.getOutputStream());
            SocketInputStream = new ObjectInputStream(ConnSocket.getInputStream());

            SocketOutStream.flush();

            // perform handshake and abandon the processing in case handshake fails
            if (PerformHandshake() != eSocketReturns.E_SOCRET_SUCCESS) {
                Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": " + PeerId
                        + " fails performing handshake. THE PEER IS ABANDONED.");
                return;
            }

            ClientData = peerProcess.PeerMap.get(ClientPeerId);

            // handshake is always followed by BitSetExchange
            if (PerformBitSetExchange() != eSocketReturns.E_SOCRET_SUCCESS) {
                Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": " + PeerId
                        + " fails performing BitSet Excahnge with peer: " + ClientPeerId + " . THE PEER IS ABANDONED.");
                return;
            }

            // handshake is always followed by BitSetExchange
            if (PerformFileOp() != eSocketReturns.E_SOCRET_SUCCESS) {
                Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": " + PeerId
                        + " fails performing file exchange with peer: " + ClientPeerId + " . THE PEER IS ABANDONED.");
                return;
            }

            Thread.sleep(10000);

            ConnSocket.close();
        } catch (IOException | InterruptedException ex) {
            //System.out.println("Unable to set keep alive on socket.");
            //System.out.println(ex.getMessage());
        }
    }

    private eSocketReturns PerformFileOp() {
        eSocketReturns ret;



        // perform send receive activity
        do {

            ret = BroadcastActivity();

            if (ret == eSocketReturns.E_SOCRET_SUCCESS)
                ret = SendActivity();

            if (ret == eSocketReturns.E_SOCRET_SUCCESS)
                ret = ReceiveAndActActivity();

            if (ret != eSocketReturns.E_SOCRET_SUCCESS)
                return ret;

            /*try {
                Thread.sleep(10);
            }catch (Exception e){
                //System.out.println("Exception" + e.getMessage());
            }*/
        } while (true);

        // return eSocketReturns.E_SOCRET_SUCCESS;
    }

    /**
     * <p>
     * Generic implementation for all the types of broadcast activity
     * </p>
     *
     * @param pSrc Object containing broadcast info
     * @return The status of the operation.
     */
    private eSocketReturns SendBroadcastMsg(BroadcastStructData pSrc) {

        if (pSrc.OperationType == eOperationType.OPERATION_HAVE.GetVal()) {

            HaveMessage msg = new HaveMessage();
            msg.PieceId = (Integer) pSrc.Data;

            return SendObj(msg, "IOException occurred while sending Have message.");
        }

        return eSocketReturns.E_SOCRET_FAILED;
    }

    /**
     * <p>
     * The procedure is responsible for broadcasting the have data to the client
     * </p>
     *
     * @return Status of the operations
     */
    private eSocketReturns BroadcastHaveData() {

        eSocketReturns ret = eSocketReturns.E_SOCRET_SUCCESS;

        while (LastBroadcastData.Next != null) {

            ret = SendBroadcastMsg(LastBroadcastData.Next);

            if (ret != eSocketReturns.E_SOCRET_SUCCESS)
                return ret;

            LastBroadcastData = LastBroadcastData.Next;
        }

        ClientData.LastData = LastBroadcastData;

        return ret;
    }

    /**
     * <p>
     * The procedure to trigger all the broadcast activities
     * </p>
     * 
     * @return the status of the operation
     */
    private eSocketReturns BroadcastActivity() {
        return BroadcastHaveData();
    }

    /**
     * <p>
     * The procedure is responsible for transmitting the object to the client and
     * handle exceptions while the operation is attempted
     * </p>
     *
     * @param pObj        The object to be sent to the client
     * @param pFailureMsg The message to be printed on console in case of failure
     * @return The end state of the operations.
     */
    private eSocketReturns SendObj(Object pObj, String pFailureMsg) {

        byte extra = 1;
        try {
            SocketOutStream.writeByte(extra);
            SocketOutStream.writeObject(pObj);
            SocketOutStream.flush();
        } catch (IOException ex) {
            //System.out.println(pFailureMsg);
            //System.out.println("*******************EXCEPTION*******************");
            //System.out.println(ex.getMessage());
            return eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }

        return eSocketReturns.E_SOCRET_SUCCESS;
    }

    /**
     * <p>
     * The procedure for receiving the object from Socket Input Stream and handling
     * the exceptions
     * </p>
     *
     * @param pFailureMsg The message to be printed on the console
     * @return A object containing error code and received object in case of success
     */
    private ResponseOutput ReceiveObj(String pFailureMsg) {

        ResponseOutput out = new ResponseOutput();

        try {
            if (SocketInputStream.available() > 0) {
                SocketInputStream.readByte();
                out.Response = (Message) SocketInputStream.readObject();
                out.Error = eSocketReturns.E_SOCRET_SUCCESS;
            } else {
                out.Error = eSocketReturns.E_SOCRET_NOTHING_TO_READ;
                out.Response = null;
            }
        } catch (ClassNotFoundException | IOException ex) {
            //System.out.println("*******************EXCEPTION*******************");
            //System.out.println(pFailureMsg);
            //System.out.println(ex.getMessage());
            out.Response = null;
            out.Error = eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }

        return out;
    }

    /**
     * <p>
     * The function is responsible for preparing the request for the given piece ID
     * and sending it over to the client
     * </p>
     *
     * @param pPieceId The piece id that is being requested from the client
     *
     * @return The end status of the operation.
     */
    private eSocketReturns SendPieceRequest(int pPieceId) {

        eSocketReturns ret;

        RequestMessage msg = new RequestMessage();

        msg.PieceId = pPieceId;

        ret = SendObj(msg, "IOException occurred while sending piece Request message.");

        if (ret == eSocketReturns.E_SOCRET_SUCCESS)
            RequestedPieceId = pPieceId;

        return ret;
    }

    /**
     * <p>
     * The function is responsible for preparing the message with piece data and
     * sending it over to the client
     * </p>
     *
     * @param pPieceId The piece id that is to be sent
     *
     * @return the status of the operation
     */
    private eSocketReturns SendPieceResponse(int pPieceId) {

        int bytesRead;
        PieceMessage msg = new PieceMessage();
        byte[] data;

        data = new byte[peerProcess.PieceSize];

        msg.PieceId = pPieceId;

        if (ClientData.IsChocked.get()) {
            //sendChokeInfo();
            return eSocketReturns.E_SOCRET_SUCCESS;
        }

        try {

            DatFile.seek(pPieceId * peerProcess.PieceSize);
            bytesRead = DatFile.read(data, 0, peerProcess.PieceSize);

            if (bytesRead < peerProcess.PieceSize) {

                data = new byte[bytesRead];

                DatFile.seek(pPieceId * peerProcess.PieceSize);
                bytesRead = DatFile.read(data, 0, bytesRead);
            }
        } catch (IOException ex) {
            return eSocketReturns.E_SOCRET_IO_EXCEPTION;
        } catch (Exception ex) {
            return eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }
        msg.SetPieceData(data);

        return SendObj(msg, "IOException occurred while sending piece message.");
    }

    /**
     * <p>
     * The function is responsible for sending the requested piece to the client.
     * </p>
     * 
     * @// TODO: 3/2/2019 Implement choke unchoke and should sent a piece request to
     * only one client
     * 
     * @param pMsg The string containing the message to be printed on console
     * @return The end status of the operation.
     */
    private eSocketReturns ProcessPieceRequest(RequestMessage pMsg) {

        // the situation is never possible except for code bugs
        if (pMsg.PieceId < 0)
            return PrintErrorMessageToConsole("Piece Request With Invalid Content");

        return SendPieceResponse(pMsg.PieceId);
    }

    /*
     * static ArrayList a = new ArrayList(); private void DebugState(int
     * newDownloadId, boolean pPreAddCheck) { if (a.contains(newDownloadId)){
     * //System.out.println("Fuck Off"); } if (pPreAddCheck == false)
     * a.add(newDownloadId);
     * 
     * if (pPreAddCheck){ int num = newDownloadId; int index =
     * num/peerProcess.BitPerBufVal; int requested =
     * SelfData.RequestedFileState.get(index); int downloaded =
     * SelfData.FileState.get(index);
     * 
     * int bitpos = 1 << (peerProcess.BitPerBufVal - num%peerProcess.BitPerBufVal -
     * 1); if ((requested & bitpos) == 1) { return; }
     * 
     * if ((downloaded & bitpos) == 1) { return; } }
     * 
     * for (int iter = 0; iter < a.size(); iter++){ int num = (int)a.get(iter); int
     * index = num/peerProcess.BitPerBufVal; int requested =
     * SelfData.RequestedFileState.get(index); int downloaded =
     * SelfData.FileState.get(index);
     * 
     * int bitpos = 1 << (peerProcess.BitPerBufVal - num%peerProcess.BitPerBufVal -
     * 1);
     * 
     * if (pPreAddCheck == false) { if ((requested & bitpos) == 0) { return; }
     * 
     * if ((downloaded & bitpos) == 0) { return; } } } }
     */

    private boolean IsPeerInteresting() {
        ArrayList list;

        list = GetInterestingBitsetList(true);

        return list.size() >= 1;
    }

    private eSocketReturns SendInterestedMsg() {
        Message msg;
        msg = new Message(eOperationType.OPERATION_INTERESTED.GetVal());

        return SendObj(msg, "IOException occurred while sending Interested message.");
    }

    private eSocketReturns SendNotInterestedMsg() {
        Message msg;
        msg = new Message(eOperationType.OPERATION_NOT_INTERESTED.GetVal());

        return SendObj(msg, "IOException occurred while sending Interested message.");
    }

    private eSocketReturns ProcessPeerInterestState() {
        if (IsPeerInteresting())
            return SendInterestedMsg();
        else
            return SendNotInterestedMsg();
    }

    /**
     * <p>
     * The function is responsible for handling the Piece/Packet sent by the client
     * in response for the request made from the server
     * </p>
     * 
     * @param pMsg The message as recieved from client
     * @return The end status of the operation.
     */
    private eSocketReturns ProcessPieceResponse(PieceMessage pMsg) {

        int updatedval;
        int originalval;

        if (pMsg.GetPieceData() == null || pMsg.PieceId == -1)
            return PrintErrorMessageToConsole("Piece Response With Invalid Content");

        if (pMsg.PieceId != RequestedPieceId) {
            // Requested piece id is reset so that the application can continue to work
            PrintErrorMessageToConsole(
                    "The piece requested was: " + RequestedPieceId + ". The received piece is: " + pMsg.PieceId + ".");
            if (RequestedPieceId >= 0) {
                int index = (int) (RequestedPieceId / peerProcess.BitPerBufVal);
                int pos = RequestedPieceId % peerProcess.BitPerBufVal;
                int updateBit = ~(1 << (peerProcess.BitPerBufVal - 1 - pos));

                int originalVal, newVal;
                do {
                    originalVal = SelfData.RequestedFileState.get(index);
                    newVal = originalVal & updateBit;
                } while (!SelfData.RequestedFileState.compareAndSet(index, originalVal, newVal));

                RequestedPieceId = -1;
            }

            return eSocketReturns.E_SOCRET_SUCCESS;
        }

        RequestedPieceId = -1;

        do {
            originalval = SelfData.NumPiecesAvailable.get();
            updatedval = originalval + 1;
        }while(!SelfData.NumPiecesAvailable.compareAndSet(originalval,updatedval));


        // logging as per the specification
        Logger.GetLogger()
                .Log(Calendar.getInstance().getTime().toString() + ": Peer " + PeerId + " has downloaded the piece "
                        + pMsg.PieceId + " from  " + ClientPeerId + ". Now the number of pieces it has is "
                        + updatedval);

        try {
            DatFile.seek(pMsg.PieceId * peerProcess.PieceSize);
            DatFile.write(pMsg.GetPieceData());
        } catch (IOException ex) {
            return eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }

        // updating the bit value so that the same packet is not requested again from
        // any other client
        do {
            originalval = SelfData.FileState.get(pMsg.PieceId / peerProcess.BitPerBufVal);
            updatedval = originalval | (1 << (peerProcess.BitPerBufVal - (pMsg.PieceId % peerProcess.BitPerBufVal) - 1));
        } while (!SelfData.FileState.compareAndSet(pMsg.PieceId / peerProcess.BitPerBufVal, originalval, updatedval));

        int initial, changed;
        // DebugState (pMsg.PieceId, false);
        do {
            initial = SelfData.ReceivedPiecesCount.get();
            changed = initial + 1;
        } while (!SelfData.ReceivedPiecesCount.compareAndSet(initial, changed));

        // The process just receive a piece/package this must be informed to all other
        // online peers
        HaveBroadcastList.AddForBroadcast(eOperationType.OPERATION_HAVE.GetVal(), pMsg.PieceId);

        return ProcessPeerInterestState();
    }

    /**
     * <p>
     * This is just a helping procedure to log the error message on the console
     * </p>
     *
     * @param pErrorMsg String containing the message to be printed
     * @return Always returns E_SOCRET_FAILED
     */
    private eSocketReturns PrintErrorMessageToConsole(String pErrorMsg) {
        //System.out.println("*******************Error*******************");
        //System.out.println(pErrorMsg);
        return eSocketReturns.E_SOCRET_FAILED;
    }

    /**
     * <p>
     * 'Have' is a message that a client sends when it has successfully received a
     * piece/packet from any of its peers. The function handles all the accounting
     * activity for the have message. The activity includes updating the associated
     * bitset value for the piece that corresponds to the client. Update the number
     * of pieces available with the client and records/logs the activity as per the
     * specification document.
     * </p>
     *
     * @param pMsg The message as received from the client
     * @return The end status of the operation.
     */
    private eSocketReturns ProcessHaveRequest(HaveMessage pMsg) {

        int origVal;
        int updatedVal;

        // the below scenario is not possible
        if (pMsg.PieceId < 0)
            return PrintErrorMessageToConsole("Piece Request With Invalid Content");

        do {
            origVal = ClientData.FileState.get(pMsg.PieceId/peerProcess.BitPerBufVal);
            updatedVal = origVal | (1 << (peerProcess.BitPerBufVal - (pMsg.PieceId%peerProcess.BitPerBufVal) - 1));
        }while (!ClientData.FileState.compareAndSet(pMsg.PieceId/peerProcess.BitPerBufVal, origVal, updatedVal));

        // BUG the number of pieces with the client has increased by 1
        do {
            origVal = ClientData.NumPiecesAvailable.get();
            updatedVal = origVal + 1;
        }while (!ClientData.NumPiecesAvailable.compareAndSet(origVal, updatedVal));

        // log the message as per specification
        Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": Peer " + PeerId
                + " received the 'have' message from " + ClientPeerId + " for the piece " + pMsg.PieceId);

        return ProcessPeerInterestState();
    }

    /**
     * <p>
     * The function is responsible for triggering object fetch from the client and
     * then calling the suitable handler for the same.
     * </p>
     *
     * @return E_SOCRET_SUCCESS if successful else a relevant error code. The
     *         function will return fail if an unsupported/invalid operation request
     *         is received
     */
    private eSocketReturns ReceivePacket() {

        ResponseOutput ret;

        ret = ReceiveObj("IOException occurred while de-serializing message.");

        if (ret.Error == eSocketReturns.E_SOCRET_NOTHING_TO_READ)
            return eSocketReturns.E_SOCRET_SUCCESS;

        // error receiving the object
        if (ret.Error != eSocketReturns.E_SOCRET_SUCCESS)
            return ret.Error;

        // handling the client data by operation type
        if(ret.Response.OperationType == eOperationType.OPERATION_BITFIELD.GetVal())
            return ProcessBitSetResponse((BitFieldMessage) ret.Response);

        if (ret.Response.OperationType == eOperationType.OPERATION_PIECE.GetVal())
            return ProcessPieceResponse((PieceMessage) ret.Response);

        if (ret.Response.OperationType == eOperationType.OPERATION_REQUEST.GetVal())
            return ProcessPieceRequest((RequestMessage) ret.Response);

        if (ret.Response.OperationType == eOperationType.OPERATION_HAVE.GetVal())
            return ProcessHaveRequest((HaveMessage) ret.Response);

        if (ret.Response.OperationType == eOperationType.OPERATION_NOT_INTERESTED.GetVal())
            return ProcessNotInterestingRequest(ret.Response);

        if (ret.Response.OperationType == eOperationType.OPERATION_INTERESTED.GetVal())
            return ProcessInterestingRequest((Message) ret.Response);

        if (ret.Response.OperationType == eOperationType.OPERATION_CHOKE.GetVal())
            return ProcessChoke(true);

        if (ret.Response.OperationType == eOperationType.OPERATION_UNCHOKE.GetVal())
            return ProcessChoke(false);

        return eSocketReturns.E_SOCRET_FAILED;
    }

    eSocketReturns ProcessNotInterestingRequest(Message pMsg) {

        // log the message as per specification
        Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": Peer " + PeerId
                + " received the 'not interested' message from " + ClientPeerId + ".");

        ClientData.IsInterested.set(false);

        return eSocketReturns.E_SOCRET_SUCCESS;
    }

    eSocketReturns ProcessInterestingRequest(Message pMsg) {

        // log the message as per specification
        Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": Peer " + PeerId
                + " received the 'interested' message from " + ClientPeerId + ".");

        ClientData.IsInterested.set(true);

        return eSocketReturns.E_SOCRET_SUCCESS;
    }

    private eSocketReturns ProcessChoke(boolean pChocked) {

        ClientData.IsChokedByPeer = pChocked;

        if (RequestedPieceId >= 0) {
            int index = (int) (RequestedPieceId / peerProcess.BitPerBufVal);
            int pos = RequestedPieceId % peerProcess.BitPerBufVal;
            int updateBit = ~(1 << (peerProcess.BitPerBufVal - 1 - pos));

            int originalVal, newVal;
            do {
                originalVal = SelfData.RequestedFileState.get(index);
                newVal = originalVal & updateBit;
            } while (!SelfData.RequestedFileState.compareAndSet(index, originalVal, newVal));

            RequestedPieceId = -1;
        }

        if (pChocked)
            Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + " Peer " + SelfData.PeerId + " is choked by " + ClientData.PeerId +".");
        else
            Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + " Peer " + SelfData.PeerId + " is unchoked by " + ClientData.PeerId +".");

        return eSocketReturns.E_SOCRET_SUCCESS;
    }

    class RanPosSelectionNode {
        int Position;
        int Value;
    }

    private int GetRandomSetBitPos(int pValue, int pIndex) {

        ArrayList posOption = new ArrayList();
        int randomSelection = 1 << 31;

        for (int iter = 0; iter < 32; iter++) {
            if ((pValue & randomSelection) != 0) {
                posOption.add(iter);
                // DebugState((pIndex*peerProcess.BitPerBufVal)+iter, true);
            }
            randomSelection = randomSelection >>> 1;
        }

        randomSelection = (int) (Math.random() * posOption.size());

        return (int) posOption.get(randomSelection);
    }

    private boolean ReservePieceForRequest(int pIndex, int pReserveBit) {
        int originalRequestBitSet;
        int newRequestBitSet;

        do {
            originalRequestBitSet = SelfData.RequestedFileState.get(pIndex);

            // case the request was made from some other client before us
            if ((originalRequestBitSet & pReserveBit) > 0)
                return false;

            newRequestBitSet = originalRequestBitSet | pReserveBit;

        } while (!SelfData.RequestedFileState.compareAndSet(pIndex, originalRequestBitSet, newRequestBitSet));

        return true;
    }

    private ArrayList GetInterestingBitsetList(boolean pGetFirstInterestOnly) {

        ArrayList posOption = new ArrayList();
        int clientBits;
        int selfMissingBits;
        int unrequestedBits;
        int candidateBits;

        for (int iter = 0; iter < SelfData.FileState.length(); iter++) {
            clientBits = ClientData.FileState.get(iter);
            selfMissingBits = ~SelfData.FileState.get(iter);
            unrequestedBits = ~SelfData.RequestedFileState.get(iter);

            // get all non requested bits that are not with currently available but can be
            // downloaded from client
            candidateBits = (clientBits & selfMissingBits) & unrequestedBits;

            if (candidateBits != 0) {
                // we have potential data that can be downloaded
                RanPosSelectionNode node = new RanPosSelectionNode();
                node.Position = iter;
                node.Value = candidateBits;
                posOption.add(node);

                // the client is interested in only the first interest point
                if (pGetFirstInterestOnly)
                    return posOption;
            }
        }

        return posOption;
    }

    private int GetRandomPieceIdToRequest() {

        ArrayList posOption;
        int randomSelection;
        int bitPosition;
        int reserveBit;

        posOption = GetInterestingBitsetList(false);

        // try to reserve an interesting packet for request from client
        do {
            // if there is nothing that we need from the client
            if (posOption.size() == 0)
                return -1;

            randomSelection = (int) (Math.random() * posOption.size());

            // now get a random position from the bit set
            bitPosition = GetRandomSetBitPos(((RanPosSelectionNode) posOption.get(randomSelection)).Value,
                    ((RanPosSelectionNode) posOption.get(randomSelection)).Position);

            reserveBit = (1 << (peerProcess.BitPerBufVal - 1 - bitPosition));

            // try to reserve the piece for request from client
            if (ReservePieceForRequest(((RanPosSelectionNode) posOption.get(randomSelection)).Position, reserveBit))
                return ((RanPosSelectionNode) posOption.get(randomSelection)).Position * peerProcess.BitPerBufVal
                        + bitPosition;
            else {
                // someone else requested what we wanted to request
                ((RanPosSelectionNode) posOption
                        .get(randomSelection)).Value = ((RanPosSelectionNode) posOption.get(randomSelection)).Value
                                & (~reserveBit);

                // there is no more interesting bit at this position
                if (((RanPosSelectionNode) posOption.get(randomSelection)).Value == 0)
                    posOption.remove(randomSelection);
            }

        } while (true);
    }

    private eSocketReturns sendChokeInfo() {
        Message msg;

        ClientData.SendChokeInfo.set(false);

        if (ClientData.IsChocked.get())
            msg = new Message(eOperationType.OPERATION_CHOKE.GetVal());
        else
            msg = new Message(eOperationType.OPERATION_UNCHOKE.GetVal());

        return SendObj(msg, "Choke/Unchoke message not sent");
    }

    /**
     * @return The end status of the operation.
     */
    private eSocketReturns SendActivity() {

        int packet_num;

        // If supposed to inform and request send choke info returns bad value then
        // return fail
        if (ClientData.SendChokeInfo.get() && eSocketReturns.E_SOCRET_SUCCESS != sendChokeInfo()) {
            return eSocketReturns.E_SOCRET_FAILED;
        }

        // a new request is not sent till the state of last requested packet is
        // determined. This is treated as a success as waiting for a response is not an
        // error
        if (RequestedPieceId >= 0 || ClientData.IsChokedByPeer)
            return eSocketReturns.E_SOCRET_SUCCESS;

        packet_num = GetRandomPieceIdToRequest();

        // nothing that can be requested for now
        if (packet_num == -1)
            return eSocketReturns.E_SOCRET_SUCCESS;

        return SendPieceRequest(packet_num);
    }

    /**
     * @// TODO: 3/2/2019 Improvise this @return The end status of the operation.
     */
    private eSocketReturns ReceiveAndActActivity() {
        return ReceivePacket();
    }

    /**
     *
     * @// TODO: 3/2/2019 The functionaliy for counting the number of blocks the
     * client already has is yet to be implemented. The extra bit that may be there
     * in the header should also be accounted.
     *
     * @return returns the status for the requested operation
     */
    private eSocketReturns ProcessBitSetResponse(BitFieldMessage pMsg) {

        BitFieldMessage bitFieldMsg;
        ResponseOutput out;
/*
        do {
            out = ReceiveObj("IOException occurred while de-serializing BitField message.");
        } while (out.Error == eSocketReturns.E_SOCRET_NOTHING_TO_READ);

        if (out.Error != eSocketReturns.E_SOCRET_SUCCESS)
            return out.Error;

        // the bit request can only get bit response and nothing else
        if (out.Response.OperationType != eOperationType.OPERATION_BITFIELD.GetVal())
            return PrintErrorMessageToConsole("Error in BitField message exchange received Operation Type: "
                    + out.Response.OperationType + " when expecting :" + eOperationType.OPERATION_BITFIELD);

        // not thaw we have verified the message to be of right type we can cast it into
        // our actual message structure
        bitFieldMsg = (BitFieldMessage) out.Response;*/

        // the bit field file sate length should be same as the file iis same for all if
        // they are not same we have some error in the system
        if (pMsg.BitField.length() != ClientData.FileState.length())
            return PrintErrorMessageToConsole("Error bitfield length mismatch");

        // overwrite the current client file state info with the latest available info
        ClientData.FileState = pMsg.BitField;

        for (int iter = 0; iter < ClientData.FileState.length(); iter++) {
            // count the number of packets the client already has
            ClientData.NumPiecesAvailable.set(0);
        }

        return ProcessPeerInterestState();
    }

    /**
     * <p>
     * The procedure is responsible for ensuring that the process communicates its
     * bit field with its client and receives and updates the client bit field
     * values.
     * </p>
     *
     * @return Status of the operation
     */
    private eSocketReturns PerformBitSetExchange() {

        // recording the current end broadcast element as the current client needs data
        // only beyond this
        LastBroadcastData = HaveBroadcastList.GetLast();

        boolean nothingToShare = true;

        for (int iter = SelfData.FileState.length() - 1; iter >= 0 ; iter--)
        {
            if (SelfData.FileState.get(iter) != 0)
            {
                nothingToShare = false;
                break;
            }
        }

        if (nothingToShare)
            return eSocketReturns.E_SOCRET_SUCCESS;

        eSocketReturns ret;

        BitFieldMessage msg = new BitFieldMessage();

        msg.SetBitFieldInfo(peerProcess.PeerMap.get(PeerId).FileState);

        ret = SendObj(msg, "IOException occurred while sending BitField message.");

        // if the bitField were sent successfully wait and process the clients response
        // BitFields
        //if (ret == eSocketReturns.E_SOCRET_SUCCESS)

        return ret;
    }

    /**
     * <p>
     * The procedures waits for the client handshake message validates it and
     * returns the status as response
     * </p>
     *
     * @return the status of operation
     */
    private eSocketReturns WaitAndProcessHandshakeResponse() {

        HandshakeMsg msg;

        try {
            SocketInputStream.readByte();
            msg = (HandshakeMsg) SocketInputStream.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            //System.out.println("*******************EXCEPTION*******************");
            //System.out.println("IOException occurred while de-serializing handshake message.");
            //System.out.println(ex.getMessage());
            return eSocketReturns.E_SOCRET_IO_EXCEPTION;
        }

        if (!msg.GetHdrBuf().equals(HandshakeHeader)) {
            Logger.GetLogger().Log("Handshake failed. Received invalid handshake header.");
            return eSocketReturns.E_SOCRET_FAILED;
        }

        // if the peer is invalid as per known peer from configuration or the peer has
        // the same ID as the current application
        if (!peerProcess.PeerMap.containsKey(msg.GetPeerId()) || msg.GetPeerId() == PeerId) {
            Logger.GetLogger().Log("Handshake failed. Received invalid PeerId.");
            return eSocketReturns.E_SOCRET_FAILED;
        }

        ClientPeerId = msg.GetPeerId();

        // logging as per log requirement specification
        if (MakesConnection)
            Logger.GetLogger().Log(Calendar.getInstance().getTime().toString() + ": " + PeerId
                    + " makes a connection to " + ClientPeerId);
        else
            Logger.GetLogger().Log(
                    Calendar.getInstance().getTime().toString() + ": " + PeerId + " is connected from " + ClientPeerId);

        return eSocketReturns.E_SOCRET_SUCCESS;
    }

    /**
     * <p>
     * The procedure is responsible for initiating and completing the handshake
     * process. This involves sending handshake message and processing the client
     * handshake message as well.
     * </p>
     *
     * @return The end status for the operation
     */
    private eSocketReturns PerformHandshake() {
        eSocketReturns ret;
        HandshakeMsg msg;

        msg = new HandshakeMsg(HandshakeHeader, PeerId);

        ret = SendObj(msg, "IOException occurred while serializing handshake message.");

        // if teh handshake data was sent successfully from the process wait and
        // validate the client handshake response
        if (ret == eSocketReturns.E_SOCRET_SUCCESS)
            return WaitAndProcessHandshakeResponse();

        return ret;
    }
}
