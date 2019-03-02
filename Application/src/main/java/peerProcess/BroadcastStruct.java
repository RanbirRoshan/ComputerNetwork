package peerProcess;

import java.util.concurrent.atomic.AtomicReference;

public class BroadcastStruct {

    BroadcastStruct ()
    {
        // initialize an empty list to begin with
        LastListElem.set (null);
    }

    private AtomicReference<BroadcastStructData> LastListElem = new AtomicReference<BroadcastStructData> ();

    void AddForBroadcast (byte pOperationType, Object pData){
        BroadcastStructData     current;
        BroadcastStructData     newData = new BroadcastStructData();


        newData.Data            = pData;
        newData.OperationType   = pOperationType;

        // add this as the last element in the list
        while (true){
            current = LastListElem.get();
            if (LastListElem.compareAndSet(current, newData)){
                break;
            }
        }

        if (current != null)
            current.Next = newData;
    }

    BroadcastStructData GetLast (){
        return LastListElem.get();
    }
}
