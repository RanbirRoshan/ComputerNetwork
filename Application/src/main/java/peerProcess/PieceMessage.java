package peerProcess;

class PieceMessage extends Message {
    int                 PieceNum;
    private byte[]      PayLoad;

    PieceMessage(){
        // initializing the value in the parent class
        super (eOperationType.OPERATION_PIECE.GetVal());
        PieceNum = -1;
        PayLoad  = null;
    }

    void SetPieceData (byte[] pData){
        //setting the size of the BitFieldMessage
        PayLoad = pData;
        SetMessageLength(MessageClassLen + pData.length);
    }

    byte[] GetPieceData ()
    {
        return PayLoad;
    }
}
