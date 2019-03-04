package peerProcess;

import java.util.concurrent.atomic.AtomicIntegerArray;

class BitFieldMessage extends Message {

    AtomicIntegerArray BitField;

    BitFieldMessage(){
        // initializing the value in the parent class
        super(eOperationType.OPERATION_BITFIELD.GetVal());
    }

    void SetBitFieldInfo (AtomicIntegerArray pBitFieldInfo){

        BitField = pBitFieldInfo;

        //setting the size of the BitFieldMessage
        SetMessageLength(MessageClassLen + pBitFieldInfo.length() * Integer.BYTES );
    }
}
