package peerProcess;

class RequestMessage extends Message {

    int         PieceNum;

    RequestMessage(){
        // initializing the value in the parent class
        super(eOperationType.OPERATION_REQUEST.GetVal());
        PieceNum = -1;
        //setting the size of the BitFieldMessage
        SetMessageLength(MessageClassLen + Integer.BYTES);
    }
}
