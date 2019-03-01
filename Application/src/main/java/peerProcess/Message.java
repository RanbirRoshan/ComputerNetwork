package peerProcess;

import java.io.Serializable;

public class Message implements Serializable {
    int                 MessageLength;
    byte                OperationType;

    //the message class contributes only 1 byte to the overallMessageLength
    transient protected static final int       MessageClassLen = 1;

    Message (byte pOperationType){
        MessageLength = 0;
        OperationType = pOperationType;
    }

    protected void SetMessageLength (int pMsgLen){
        MessageLength = pMsgLen;
    }

    public int getMessageLength (){
        return MessageLength;
    }
}
