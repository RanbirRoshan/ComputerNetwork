package peerProcess;

public enum eOperationType {

    OPERATION_CHOKE         ((byte)0),
    OPERATION_UNCHOKE       ((byte)1),
    OPERATION_INTERESTED    ((byte)2),
    OPERATION_NOT_INTERESTED((byte)3),
    OPERATION_HAVE          ((byte)4),
    OPERATION_BITFIELD      ((byte)5),
    OPERATION_REQUEST       ((byte)6),
    OPERATION_PIECE         ((byte)7);

    private final byte Value;

    eOperationType (byte pType){
        Value = pType;
    }

    public byte GetVal () {return Value;}
}
