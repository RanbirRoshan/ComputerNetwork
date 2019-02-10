package peerProcess;

import java.io.Serializable;
import java.util.Arrays;

public class HandshakeMsg implements Serializable {
    private String   HdrBuffer;
    private byte[]   Reserved = new byte [10];
    private int      PeerId = 0;

    HandshakeMsg (String pHdrMsg, int pPeerId){
        SetPeerId (pPeerId);
        SetHdrBuf (pHdrMsg);
    }

    public boolean SetHdrBuf (String pHdrMsg) {

        if (pHdrMsg.length() > 18)
            return false;

        HdrBuffer = pHdrMsg;

        return true;
    }

    public String GetHdrBuf (){

        if (HdrBuffer == null)
            return "";

        return HdrBuffer;
    }

    public void SetPeerId (int pPeerId) { PeerId = pPeerId; }

    public int GetPeerId () { return  PeerId; }
}
