package peerProcess;

import java.io.Serializable;

public class ProtocolData implements Serializable {

    public byte     ProtocolType;
    public byte[]   Data;

    ProtocolData (Byte pProtocolType, byte[] pData){
        ProtocolType = pProtocolType;
        Data = pData;
    }

    ProtocolData (){

    }

    public byte GetType (){
        return ProtocolType;
    }

    public byte[] GetData(){
        return Data;
    }

    public void SetData (byte[] pData){
        Data = pData;
    }

    public void SetType (Byte pProtocolType){
        ProtocolType = pProtocolType;
    }
}
