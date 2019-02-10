package peerProcess;

public enum eProtocolType {
    PROTOCOL_UNKNOWN(0),
    PROTOCOL_HANDSHAKE(1);

    private final byte Value;

    eProtocolType (int ptype){
        Value = (byte)ptype;
    }

    public byte GetVal () {return Value;}
}
