package peerProcess;

import java.io.Serializable;

public class Message implements Serializable {
    private int         MessageLength;
    byte                OperationType;

    //the message class contributes only 1 byte to the overallMessageLength
    transient protected static final int       MessageClassLen = 5;

    Message (byte pOperationType){
        MessageLength = MessageClassLen;
        OperationType = pOperationType;
    }

    protected void SetMessageLength (int pMsgLen){
        MessageLength = pMsgLen;
    }

    public int getMessageLength (){
        return MessageLength;
    }
}
