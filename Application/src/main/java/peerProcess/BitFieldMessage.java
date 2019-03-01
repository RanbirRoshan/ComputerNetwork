package peerProcess;

public class BitFieldMessage extends Message {

    byte[]  BitField;

    BitFieldMessage(){
        // initializing the value in the parent class
        super(eOperationType.OPERATION_BITFIELD.GetVal());
    }

    public void SetBitFieldInfo (byte[] pBitFieldInfo){

        BitField = pBitFieldInfo;

        //setting the size of the BitFieldMessage
        SetMessageLength(MessageClassLen + pBitFieldInfo.length );
    }
}
