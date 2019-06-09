/**
 * Created by
 * Aaron
 * 2019-06-07
 */

public class FileSystem {

    private final int SEEK_SET = 0;
    private final int SEEK_CUR = 1;
    private final int SEEK_END = 2;

    private Directory directory;
    private SuperBlock superBlock;
    private FileTable fileTable;

    public FileSystem(int numBlocks){

        superBlock = new SuperBlock(numBlocks);
        directory = new Directory(superBlock.inodeBlocks);
        fileTable = new FileTable(directory);

        FileTableEntry dirEntry = this.open("/", "r");
        int dirSize = fsize(dirEntry);

        if(dirSize > 0){
            byte[] dirData = new byte[dirSize];
            read(dirEntry, dirData);

            directory.bytes2directory(dirData);
        }
        close(dirEntry);
    }

    public boolean close(FileTableEntry dirEntry) {
        synchronized (dirEntry){
            dirEntry.count--;
            if(dirEntry.count > 0) return true;
        }
        return fileTable.ffree(dirEntry);
    }

    public FileTableEntry open(String fileName, String mode) {
        FileTableEntry fileTableEntry = fileTable.falloc(fileName, mode);

        if ((mode.equals("w")) && !this.deallocateBlocks(fileTableEntry)) {
            return null;
        }
        else {
            return fileTableEntry;
        }
    }

    int fsize(FileTableEntry ftEntry){
        synchronized (ftEntry){
            return ftEntry.inode.length;
        }
    }

    public void sync(){
        FileTableEntry fileTableEntry = open("/", "w");
        byte[] dirData = directory.directory2bytes();
        write(fileTableEntry, dirData);
        close(fileTableEntry);
        superBlock.sync();
    }

    public boolean format(int data){
        superBlock.format(data);
        directory = new Directory(superBlock.inodeBlocks);
        fileTable = new FileTable(directory);
        return true;
    }

    public int seek(FileTableEntry ftEntry, int position, int offset){
        synchronized (ftEntry){
            switch (position){
                case SEEK_SET:
                    if(offset <= fsize(ftEntry) && offset >0){
                        ftEntry.seekPtr = offset;
                        break;
                    }
                    return -1;


                case SEEK_CUR:
                    if(ftEntry.seekPtr + offset <= fsize(ftEntry) && ftEntry.seekPtr + offset > 0){
                        ftEntry.seekPtr += offset;
                        break;
                    }
                    return -1;

                case SEEK_END:
                    if(fsize(ftEntry) + offset > fsize(ftEntry) || fsize(ftEntry) + offset < 0){
                        return -1;
                    }
                    ftEntry.seekPtr = fsize(ftEntry) + offset;
            }
            return ftEntry.seekPtr;
        }
    }


    public int read(FileTableEntry ftEntry, byte[] buffer){
        if (!ftEntry.mode.equals("w") && !ftEntry.mode.equals("a")) {

            int bufferLength = buffer.length;
            int trackData = 0;
            int trackError = -1;
            int blockSize = Disk.blockSize;
            int remainingRead = 0;

            synchronized (ftEntry) {
                while (bufferLength > 0 && ftEntry.seekPtr < fsize(ftEntry)) {
                    int currentBlock = ftEntry.inode.findBlock(ftEntry.seekPtr);
                    if (currentBlock == trackError) break;

                    byte[] blockData = new byte[blockSize];
                    SysLib.rawread(currentBlock, blockData);
                    int offset = ftEntry.seekPtr % blockSize;
                    int remainingBlocks = blockSize - remainingRead;
                    int remainingFile = fsize(ftEntry) - ftEntry.seekPtr;

                    remainingRead = Math.min(Math.min(remainingBlocks, bufferLength), remainingFile);
                    System.arraycopy(blockData, offset, buffer, trackData, remainingRead);
                    ftEntry.seekPtr += remainingRead;
                    trackData += remainingRead;
                    bufferLength -= remainingRead;
                }
                return trackData;
            }
        }
        else {
            return -1;
        }
    }



    public int write(FileTableEntry ftEntry, byte[] buffer){
        if(ftEntry.mode.equals("r") || buffer == null) return -1;

        synchronized (ftEntry){

            int blockSize = Disk.blockSize;
            int bufferLength = buffer.length;
            int offset = 0;

            while(bufferLength > 0){
                int currentBlock = ftEntry.inode.findBlock(ftEntry.seekPtr);
                if(currentBlock == -1){
                    short newBLock = (short)superBlock.getFreeBlock();
                    int index = ftEntry.inode.setTargetBlock(ftEntry.seekPtr, newBLock);

                    if(index == -3){

                        short freeBlock = (short)superBlock.getFreeBlock();
                        if(!ftEntry.inode.registerIndexBlock(freeBlock)){
                            return -1;
                        }
                        else if(ftEntry.inode.setTargetBlock(ftEntry.seekPtr, newBLock) != 0){
                            return -1;
                        }
                    }
                    else if(index == -2 || index == -1){
                        return -1;
                    }

                    currentBlock = newBLock;
                }

                byte[] blockData = new byte[Disk.blockSize];
                SysLib.rawread(currentBlock, blockData);
                int newSeekPtr = ftEntry.seekPtr % Disk.blockSize;
                int remaining = Disk.blockSize - newSeekPtr;
                int position = Math.min(remaining, bufferLength);
                System.arraycopy(buffer, offset, blockData, newSeekPtr, position);
                ftEntry.seekPtr += position;
                bufferLength -= position;

                ftEntry.inode.length = (ftEntry.seekPtr > ftEntry.inode.length) ? ftEntry.seekPtr : ftEntry.inode.length;

            }
            ftEntry.inode.toDisk(ftEntry.iNumber);
            return offset;
        }
    }

    public boolean delete(String name){
        FileTableEntry ftEntry = open(name, "w");

        if(close(ftEntry) && directory.ifree(ftEntry.iNumber))
            return true;
        return false;
    }

    public boolean deallocateBlocks(FileTableEntry ftEntry){
        if(ftEntry.inode.count != 1)
            return false;

        byte[] data = new byte[Disk.blockSize];
        if(data != null){
            int offset = 0;
            short blockNum = SysLib.bytes2short(data, offset);
            while(blockNum != -1){
                superBlock.returnBlock(blockNum);
            }
        }

        int blockID = 0;
        int inodeDirectSize = 11;

        while(true){
            Inode node = ftEntry.inode;
            if(blockID >= inodeDirectSize){
                ftEntry.inode.toDisk(ftEntry.iNumber);
                return true;
            }
            if(ftEntry.inode.direct[blockID] != -1){
                superBlock.returnBlock(ftEntry.inode.direct[blockID]);
                ftEntry.inode.direct[blockID] = -1;
            }

            blockID++;
        }
    }
}
