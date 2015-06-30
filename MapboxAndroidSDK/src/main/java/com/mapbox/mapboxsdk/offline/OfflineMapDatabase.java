package com.mapbox.mapboxsdk.offline;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import com.mapbox.mapboxsdk.constants.MapboxConstants;
import com.mapbox.mapboxsdk.exceptions.OfflineDatabaseException;
import java.util.Map;

public class OfflineMapDatabase implements MapboxConstants {

    private static final String TAG = "OfflineMapDatabase";

    private Context context;

    private SQLiteDatabase db;

    private String uniqueID;
    private String mapID;
    private boolean includesMetadata;
    private boolean includesMarkers;
    private RasterImageQuality imageQuality;
    private String path;
    private boolean invalid;

    private final Object lock;

    {
        lock = new Object();
    }

    /**
     * Default Constructor
     *
     * @param context Context of Android app
     */
    public OfflineMapDatabase(Context context) {
        super();
        this.context = context;
    }

    /**
     * Constructor
     * @param context Context of Android app
     * @param mapID MapId
     */
    public OfflineMapDatabase(Context context, String mapID) {
        super();
        this.context = context;
        this.mapID = mapID;
    }

    public OfflineMapDatabase(Context context, String mapID, Map<String, String> metadata) {
        super();
        this.context = context;
        this.mapID = mapID;
        updateMetadata(metadata);
    }

    public String getUniqueID() {
        synchronized (lock) {
            return uniqueID;
        }
    }

    public String getMapID() {
        synchronized (lock) {
            return mapID;
        }
    }

    public String getPath() {
        synchronized (lock) {
            return path;
        }
    }

    public RasterImageQuality getImageQuality() {
        synchronized (lock) {
            return imageQuality;
        }
    }

    public boolean includesMetadata() {
        synchronized (lock) {
            return includesMetadata;
        }
    }

    public boolean includesMarkers() {
        synchronized (lock) {
            return includesMarkers;
        }
    }

    public boolean initializeDatabase() {
        synchronized (lock) {
            if (!refreshMetadata()) {
                return false;
            }
            SQLiteDatabase db = database();
            if (db == null) {
                return false;
            }
            this.path = db.getPath();
            closeDatabase();
            return true;
        }
    }

    public byte[] dataForURL(String url) throws OfflineDatabaseException {
        byte[] data = sqliteDataForURL(url);
/*
        if (data == null || data.length == 0) {
            String reason = String.format("The offline database has no data for %s", url);
            throw new OfflineDatabaseException(reason);
        }
*/
        return data;
    }

    public void invalidate() {
        synchronized (lock) {
            this.invalid = true;
            closeDatabase();
        }
    }

    public String sqliteMetadataForName(String name) {
        SQLiteDatabase db = database();
        if (db == null) {
            return null;
        }

        String query = "SELECT " + OfflineDatabaseHandler.FIELD_METADATA_VALUE + " FROM " + OfflineDatabaseHandler.TABLE_METADATA + " WHERE " + OfflineDatabaseHandler.FIELD_METADATA_NAME + "=?;";
        String[] selectionArgs = new String[] { name };
        Cursor cursor = db.rawQuery(query, selectionArgs);
        if (cursor == null) {
            return null;
        }

        String res = null;
        if (cursor.moveToFirst()) {
            res = cursor.getString(cursor.getColumnIndex(OfflineDatabaseHandler.FIELD_METADATA_VALUE));
        }
        cursor.close();
        return res;
    }

    public void updateMetadata(Map<String, String> metadata) {
        SQLiteDatabase db = database();
        if (db == null)
            return;

        db.beginTransaction();
        for (String key : metadata.keySet()) {
            ContentValues cv = new ContentValues();
            cv.put(OfflineDatabaseHandler.FIELD_METADATA_NAME, key);
            cv.put(OfflineDatabaseHandler.FIELD_METADATA_VALUE, metadata.get(key));
            db.replace(OfflineDatabaseHandler.TABLE_METADATA, null, cv);
        }
        db.setTransactionSuccessful();
        db.endTransaction();

        refreshMetadata();
    }

    private boolean refreshMetadata() {
        synchronized (lock) {
            String uniqueID = sqliteMetadataForName("uniqueID");
            String mapID = sqliteMetadataForName("mapID");
            String includesMetadata = sqliteMetadataForName("includesMetadata");
            String includesMarkers = sqliteMetadataForName("includesMarkers");
            String imageQuality = sqliteMetadataForName("imageQuality");

            if (TextUtils.isEmpty(uniqueID) || TextUtils.isEmpty(mapID) || TextUtils.isEmpty(includesMetadata) ||
                    TextUtils.isEmpty(includesMarkers) || TextUtils.isEmpty(imageQuality)) {
                return false;
            }

            this.uniqueID = uniqueID;
            this.mapID = mapID;
            this.includesMetadata = "YES".equalsIgnoreCase(includesMetadata);
            this.includesMarkers = "YES".equalsIgnoreCase(includesMarkers);
            this.imageQuality = RasterImageQuality.getEnumForValue(Integer.parseInt(imageQuality));

            return true;
        }
    }

    public void setURLData(String url, byte[] data) {
        setURLData(url, data, 200);
    }

    public void setURLData(String url, byte[] data, int status) {
        SQLiteDatabase db = database();
        if (db == null) {
            return;
        }
        db.beginTransaction();

//      String query2 = "INSERT INTO data(value) VALUES(?);";
        ContentValues values = new ContentValues();
        values.put(OfflineDatabaseHandler.FIELD_RESOURCES_URL, url);
        values.put(OfflineDatabaseHandler.FIELD_RESOURCES_DATA, data);
        values.put(OfflineDatabaseHandler.FIELD_RESOURCES_STATUS, status);
        db.replace(OfflineDatabaseHandler.TABLE_RESOURCES, null, values);

        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public byte[] sqliteDataForURL(String url) {
        SQLiteDatabase db = database();
        if (db == null) {
            return null;
        }

        String query = "SELECT " + OfflineDatabaseHandler.FIELD_RESOURCES_DATA + " FROM " + OfflineDatabaseHandler.TABLE_RESOURCES + " WHERE " + OfflineDatabaseHandler.FIELD_RESOURCES_URL + "=?;";
        String[] selectionArgs = new String[] { url };
        Cursor cursor = db.rawQuery(query, selectionArgs);
        if (cursor == null) {
            return null;
        }

        byte[] res = null;
        if (cursor.moveToFirst()) {
            res = cursor.getBlob(cursor.getColumnIndex(OfflineDatabaseHandler.FIELD_RESOURCES_DATA));
        }
        cursor.close();
        return res;
    }

    public boolean isURLAlreadyInDatabase(String url) {
        SQLiteDatabase db = database();
        if (db == null) {
            return false;
        }

        String query = "SELECT COUNT(*) as count FROM " + OfflineDatabaseHandler.TABLE_RESOURCES + " WHERE " + OfflineDatabaseHandler.FIELD_RESOURCES_URL + " =?;";
        String[] selectionArgs = new String[] { url };
        Cursor result = db.rawQuery(query, selectionArgs);
        boolean alreadyDownloaded = false;
        if (result.moveToNext()) {
            int count = result.getInt(result.getColumnIndex("count"));
            alreadyDownloaded = count == 1;
        }
        result.close();
        return alreadyDownloaded;
    }

    public void deleteDataForURL(String url) {
        SQLiteDatabase db = database();
        if (db == null) {
            return;
        }

        String[] whereArgs = new String[] { url };
        db.delete(OfflineDatabaseHandler.TABLE_RESOURCES, OfflineDatabaseHandler.FIELD_RESOURCES_URL + " = ?", whereArgs);
    }

    private SQLiteDatabase database() {
        synchronized (lock) {
            if (invalid || mapID == null) {
                return null;
            }

            if (db != null && !db.isOpen()) {
                db = null;
            }

            if (db == null) {
                db = OfflineDatabaseManager.getOfflineDatabaseManager(context).getOfflineDatabaseHandlerForMapId(mapID).getWritableDatabase();
            }

            return db;
        }
    }

    public void closeDatabase() {
        synchronized (lock) {
            if (db != null && db.isOpen()) {
                db.close();
            }
            db = null;
        }
    }
}
