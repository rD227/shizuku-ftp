package org.primftpd.shizuku.data;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Parcelable file information for cross-process communication via AIDL.
 * Replaces LsOutputBean for Shizuku service.
 */
public class FileInfo implements Parcelable {
    private final String absolutePath;
    private final String name;
    private final boolean exists;
    private final boolean isFile;
    private final boolean isDirectory;
    private final boolean isSymlink;
    private final long size;
    private final long lastModified;
    private final boolean canRead;
    private final boolean canWrite;
    private final boolean canExecute;
    private final String symlinkTarget;

    public FileInfo(String absolutePath, String name, boolean exists, boolean isFile,
                    boolean isDirectory, boolean isSymlink, long size, long lastModified,
                    boolean canRead, boolean canWrite, boolean canExecute, String symlinkTarget) {
        this.absolutePath = absolutePath;
        this.name = name;
        this.exists = exists;
        this.isFile = isFile;
        this.isDirectory = isDirectory;
        this.isSymlink = isSymlink;
        this.size = size;
        this.lastModified = lastModified;
        this.canRead = canRead;
        this.canWrite = canWrite;
        this.canExecute = canExecute;
        this.symlinkTarget = symlinkTarget;
    }

    protected FileInfo(Parcel in) {
        absolutePath = in.readString();
        name = in.readString();
        exists = in.readByte() != 0;
        isFile = in.readByte() != 0;
        isDirectory = in.readByte() != 0;
        isSymlink = in.readByte() != 0;
        size = in.readLong();
        lastModified = in.readLong();
        canRead = in.readByte() != 0;
        canWrite = in.readByte() != 0;
        canExecute = in.readByte() != 0;
        symlinkTarget = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(absolutePath);
        dest.writeString(name);
        dest.writeByte((byte) (exists ? 1 : 0));
        dest.writeByte((byte) (isFile ? 1 : 0));
        dest.writeByte((byte) (isDirectory ? 1 : 0));
        dest.writeByte((byte) (isSymlink ? 1 : 0));
        dest.writeLong(size);
        dest.writeLong(lastModified);
        dest.writeByte((byte) (canRead ? 1 : 0));
        dest.writeByte((byte) (canWrite ? 1 : 0));
        dest.writeByte((byte) (canExecute ? 1 : 0));
        dest.writeString(symlinkTarget);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<FileInfo> CREATOR = new Creator<FileInfo>() {
        @Override
        public FileInfo createFromParcel(Parcel in) {
            return new FileInfo(in);
        }

        @Override
        public FileInfo[] newArray(int size) {
            return new FileInfo[size];
        }
    };

    // Getters
    public String getAbsolutePath() { return absolutePath; }
    public String getName() { return name; }
    public boolean exists() { return exists; }
    public boolean isFile() { return isFile; }
    public boolean isDirectory() { return isDirectory; }
    public boolean isSymlink() { return isSymlink; }
    public long getSize() { return size; }
    public long getLastModified() { return lastModified; }
    public boolean canRead() { return canRead; }
    public boolean canWrite() { return canWrite; }
    public boolean canExecute() { return canExecute; }
    public String getSymlinkTarget() { return symlinkTarget; }

    /**
     * Create a non-existent FileInfo
     */
    public static FileInfo nonExistent(String absolutePath, String name) {
        return new FileInfo(absolutePath, name, false, false, false, false, 
                          0, 0, false, false, false, null);
    }
}
