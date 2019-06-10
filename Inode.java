/**
 * Created by
 * Aaron
 * 2019-06-07
 */

public class Inode {
    private final static int iNodeSize = 32;       // fix to 32 bytes
    private final static int directSize = 11;      // # direct pointers

    public int length;                             // file size in bytes
    public short count;                            // # file-table entries pointing to this
    public short flag;                             // 0 = unused, 1 = used, ...
    public short direct[] = new short[directSize]; // direct pointers
    public short indirect;                         // a indirect pointer


    /*----------------default constructor--------------------*/
    Inode() {
        length = 0;
        count = 0;
        flag = 1;
        for (int i = 0; i < directSize; i++) {
            direct[i] = -1;
        }
        indirect = -1;
    }

    /*-----------------constructor with param-----------------*/
    Inode(short iNumber) {                       // retrieving inode from disk

        // get number of blocks and allocate storage
        int blockNum = 1 + iNumber / 16;
        byte[] blockData = new byte[Disk.blockSize];
        SysLib.rawread(blockNum, blockData);

        int offset = (iNumber % 16) * iNodeSize;

        length = SysLib.bytes2int(blockData, offset);
        offset += 4;
        count = SysLib.bytes2short(blockData, offset);
        offset += 2;
        flag = SysLib.bytes2short(blockData, offset);
        offset += 2;

        for (int i = 0; i < directSize; i++) {
            direct[i] = SysLib.bytes2short(blockData, offset);
            offset += 2;
        }

        indirect = SysLib.bytes2short(blockData, offset);
    }

    /*---------------------------------toDisk-------------------------------------*/
    void toDisk(short iNumber) {                  // save to disk as the i-th inode

        if(iNumber < 0) return;
        byte [] blockData = new byte[iNodeSize];

        int offset = 0;

        SysLib.int2bytes(length, blockData, offset);
        offset += 4;
        SysLib.short2bytes(count, blockData, offset);
        offset += 2;
        SysLib.short2bytes(flag, blockData, offset);
        offset += 2;

        for (int i = 0; i < directSize; i++){
            SysLib.short2bytes(direct[i], blockData, offset);
            offset += 2;
        }

        SysLib.short2bytes(indirect, blockData, offset);

        int blockNumber = 1 + iNumber / 16;
        byte[] newData = new byte[Disk.blockSize];
        SysLib.rawread(blockNumber, newData);

        offset = (iNumber % 16) * iNodeSize;

        System.arraycopy(blockData, 0, newData, offset, iNodeSize);
        SysLib.rawwrite(blockNumber, newData);
    }


    /*---------------------------------registerIndexBlock-------------------------------------*/
    public boolean registerIndexBlock(short blockNum){

        for (int i = 0; i < directSize; i++) {
            if(direct[i] == -1){ // check for invalid direct blocks
                return false;
            }
        }
        if(indirect != -1) { // check for invalid indirect blocks
            return false;
        }

        indirect = blockNum;
        byte[] blockData = new byte[Disk.blockSize];

        // set everything in the new block number to default vals
        for (int i = 0; i < Disk.blockSize/2; i++) {
            SysLib.short2bytes((short)(-1), blockData, i * 2);
        }
        SysLib.rawwrite(indirect, blockData); // write to disk
        return true;
    }


    /*---------------------------------findTargetBlock-------------------------------------*/
    public int findTargetBlock(int location){

        int target = location / Disk.blockSize;

        if(target < directSize){ // check if still in direct blocks
            return direct[target];
        }
        else if(indirect < 0){
            return -1;
        }

        byte[] blockData = new byte[Disk.blockSize];
        //get number of indirect blocks begin pointed to
        SysLib.rawread(indirect, blockData);
        return SysLib.bytes2short(blockData, (target - directSize) * 2); // change offset val to bytes
    }

    /*---------------------------------registerTargetBlock-------------------------------------*/
    int registerTargetBlock(int location, short blockNum){

        // index decides how far into the block to go
        int index = location / Disk.blockSize;

        if(index < directSize){ // check that index is still in direct blocks
            if(direct[index] >= 3){
                return -1;
            }
            else if (index > 0 && direct[index - 1] == -1) { // last location not valid
                 return -2;
            }
            else {
                direct[index] = blockNum; // set block in direct to current blockNum
                return 0;
            }
        }
        else if(indirect < 0){ //indirect not accessible
            return -3;
        }
        else {
            byte[] newBlockData = new byte[Disk.blockSize];
            // read the data in the indirect block
            SysLib.rawread(indirect, newBlockData);

            int offset = index - directSize; // set stop point
            if (SysLib.bytes2short(newBlockData, offset * 2) > 0) {

                return -1;
            }
            else {
                // write the blockdata into indirect
                SysLib.short2bytes(blockNum, newBlockData, offset * 2);
                SysLib.rawwrite(indirect, newBlockData);
                return 0;
            }
        }
    }

    /*---------------------------------findIndexBlock-------------------------------------*/
    int findIndexBlock(){
        return indirect;
    }


    /*---------------------------------unregisterIndexBlock-------------------------------------*/
    byte[] unregisterIndexBlock(){
        if(indirect >= 0){
            byte[] blockData = new byte[Disk.blockSize];
            SysLib.rawread(indirect, blockData);
            indirect = -1; // set to default value
            return blockData;
        }
        return null;
    }
}