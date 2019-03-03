package peerProcess;

class PieceMessage extends Message {
    int             PieceId;
    private byte[]  PayLoad;

    PieceMessage(){
        // initializing the value in the parent class
        super (eOperationType.OPERATION_PIECE.GetVal());
        PieceId = -1;
        PayLoad  = null;
    }

    void SetPieceData (byte[] pData){
        //setting the size of the BitFieldMessage
        PayLoad = pData;
        SetMessageLength(MessageClassLen + pData.length + Integer.BYTES);
    }

    byte[] GetPieceData ()
    {
        return PayLoad;
    }
}
