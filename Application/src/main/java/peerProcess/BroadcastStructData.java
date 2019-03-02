package peerProcess;

class BroadcastStructData{
    byte                    OperationType;
    Object                  Data;
    BroadcastStructData     Next;

    BroadcastStructData (){
        Data          = null;
        Next          = null;
        OperationType = -1;
    }
}
