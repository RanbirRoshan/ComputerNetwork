package peerProcess;

class BitFieldMessage extends Message {

    int[]  BitField;

    BitFieldMessage(){
        // initializing the value in the parent class
        super(eOperationType.OPERATION_BITFIELD.GetVal());
    }

    void SetBitFieldInfo (int[] pBitFieldInfo){

        BitField = pBitFieldInfo;

        //setting the size of the BitFieldMessage
        SetMessageLength(MessageClassLen + pBitFieldInfo.length );
    }
}
