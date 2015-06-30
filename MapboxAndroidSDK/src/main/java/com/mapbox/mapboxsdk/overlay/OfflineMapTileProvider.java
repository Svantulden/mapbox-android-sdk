package com.mapbox.mapboxsdk.overlay;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.mapbox.mapboxsdk.constants.MapboxConstants;
import com.mapbox.mapboxsdk.exceptions.OfflineDatabaseException;
import com.mapbox.mapboxsdk.offline.OfflineMapDatabase;
import com.mapbox.mapboxsdk.tileprovider.MapTile;
import com.mapbox.mapboxsdk.tileprovider.MapTileLayerBase;
import com.mapbox.mapboxsdk.util.MapboxUtils;
import java.io.ByteArrayInputStream;

import uk.co.senab.bitmapcache.CacheableBitmapDrawable;

public class OfflineMapTileProvider extends MapTileLayerBase implements MapboxConstants {

    private static final String TAG = "OfflineMapTileProvider";

    private OfflineMapDatabase[] offlineMapDatabases;

    public OfflineMapTileProvider(Context context, OfflineMapDatabase offlineMapDatabase) {
        super(context, null);
        this.offlineMapDatabases = new OfflineMapDatabase[] { offlineMapDatabase };
    }

    public OfflineMapTileProvider(Context context, OfflineMapDatabase[] databases) {
        super(context, null);
        this.offlineMapDatabases = databases;
    }

    @Override
    public Drawable getMapTile(MapTile pTile, boolean allowRemote) {
        try {
            CacheableBitmapDrawable cacheable = mTileCache.getMapTile(pTile);
            if (cacheable != null) {
                return cacheable;
            }

            // Build URL to match url in database
            byte[] data = null;
            for (OfflineMapDatabase offlineMapDatabase : offlineMapDatabases) {
                String url = MapboxUtils.getMapTileURL(context, offlineMapDatabase.getMapID(), pTile.getZ(), pTile.getX(), pTile.getY(), offlineMapDatabase.getImageQuality());
                data = offlineMapDatabase.dataForURL(url);
                if (data != null)
                    break;
            }

            if (data == null || data.length == 0) {
                // No data found, just return null so that default gray screen is displayed.
                return null;
            }

            // Return the tile image
            BitmapDrawable drawable = new BitmapDrawable(context.getResources(), new ByteArrayInputStream(data));
            return mTileCache.putTile(pTile, drawable);
        } catch (OfflineDatabaseException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void detach() {
        if (getTileSource() != null) {
            getTileSource().detach();
        }
        for (OfflineMapDatabase offlineMapDatabase : offlineMapDatabases) {
            offlineMapDatabase.closeDatabase();
        }
    }
}
