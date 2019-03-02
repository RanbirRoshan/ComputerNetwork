package peerProcess;

public class HaveMessage extends Message {

    int        PieceId;

    HaveMessage (){
        // initializing the value in the parent class
        super(eOperationType.OPERATION_HAVE.GetVal());
        //setting the size of the BitFieldMessage
        SetMessageLength(MessageClassLen + Integer.BYTES);
    }
}
