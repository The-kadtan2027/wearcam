package com.gajuk.wearcam;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public class CaptureProvider extends ContentProvider {

    public static final String AUTHORITY = "com.gajuk.wearcam.capture";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/photo.jpg");
    public static final String FILE_NAME = "remote_capture.jpg";

    public static File file(android.content.Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File target = file(getContext());
        return ParcelFileDescriptor.open(target,
                ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_WRITE_ONLY
                        | ParcelFileDescriptor.MODE_TRUNCATE);
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
