package org.primftpd.shizuku.data;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Result wrapper for file operations via AIDL.
 */
public class FileOperationResult implements Parcelable {
    private final boolean success;
    private final String errorMessage;

    public FileOperationResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    protected FileOperationResult(Parcel in) {
        success = in.readByte() != 0;
        errorMessage = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (success ? 1 : 0));
        dest.writeString(errorMessage);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<FileOperationResult> CREATOR = new Creator<FileOperationResult>() {
        @Override
        public FileOperationResult createFromParcel(Parcel in) {
            return new FileOperationResult(in);
        }

        @Override
        public FileOperationResult[] newArray(int size) {
            return new FileOperationResult[size];
        }
    };

    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }

    public static FileOperationResult success() {
        return new FileOperationResult(true, null);
    }

    public static FileOperationResult failure(String message) {
        return new FileOperationResult(false, message);
    }
}
