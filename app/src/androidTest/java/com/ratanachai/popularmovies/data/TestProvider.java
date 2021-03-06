package com.ratanachai.popularmovies.data;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.test.AndroidTestCase;
import android.util.Log;

import com.ratanachai.popularmovies.data.MovieContract.MovieEntry;
import com.ratanachai.popularmovies.data.MovieContract.ReviewEntry;
import com.ratanachai.popularmovies.data.MovieContract.VideoEntry;

/**
 * Tests for MovieProvider
 * Created by Ratanachai on 15/09/22.
 */
public class TestProvider extends AndroidTestCase {
    public static final String LOG_TAG = TestProvider.class.getSimpleName();
    private static final long MAD_MAX_ROW_ID = 1L;

    /* This helper function deletes all records from both database tables using the ContentProvider.
       It also queries the ContentProvider to make sure that the database has been successfully deleted */
    public void deleteAllRecordsFromProvider() {
        ContentResolver cr = mContext.getContentResolver();
        cr.delete(ReviewEntry.CONTENT_URI, null, null);
        cr.delete(VideoEntry.CONTENT_URI, null, null);
        cr.delete(MovieEntry.CONTENT_URI, null, null);

        Cursor retCursor = cr.query(MovieEntry.CONTENT_URI, null, null, null, null);
        assertEquals("Error: Records not deleted from Weather table during delete", 0, retCursor.getCount());
        retCursor.close();

        retCursor = cr.query(VideoEntry.CONTENT_URI, null, null, null, null);
        assertEquals("Error: Records not deleted from Weather table during delete", 0, retCursor.getCount());
        retCursor.close();

        retCursor = cr.query(ReviewEntry.CONTENT_URI, null, null, null, null);
        assertEquals("Error: Records not deleted from Weather table during delete", 0, retCursor.getCount());
        retCursor.close();
    }

    /* This helper function deletes all records from both database tables using the database
       functions only.  This is designed to be used to reset the state of the database until the
       delete functionality is available in the ContentProvider.*/
    public void deleteAllRecordsFromDB() {
        MovieDbHelper dbHelper = new MovieDbHelper(mContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        db.delete(MovieEntry.TABLE_NAME, null, null);
        db.delete(VideoEntry.TABLE_NAME, null, null);
        db.delete(ReviewEntry.TABLE_NAME, null, null);
        db.close();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
//        deleteAllRecordsFromDB();
        deleteAllRecordsFromProvider();
    }

    public void testProviderRegistry() {
        PackageManager pm = mContext.getPackageManager();

        // We define the component name based on the package name from the context and the Provider class.
        ComponentName componentName = new ComponentName(mContext.getPackageName(), MovieProvider.class.getName());
        try {
            // Fetch the provider info using the component name from the PackageManager
            // This throws an exception if the provider isn't registered.
            ProviderInfo providerInfo = pm.getProviderInfo(componentName, 0);

            // Make sure that the registered authority matches the authority from the Contract.
            assertEquals("Error: Provider registered with authority: " + providerInfo.authority +
                            " instead of authority: " + MovieContract.CONTENT_AUTHORITY,
                    providerInfo.authority, MovieContract.CONTENT_AUTHORITY);
        } catch (PackageManager.NameNotFoundException e) {

            assertTrue("Error: Provider not registered at " + mContext.getPackageName(), false);
        }
    }

    public void testGetType() {

        // URI and expected Return Type
        // content://com.ratanachai.popularmovies/movie
        // vnd.android.cursor.dir/com.ratanachai.popularmovies/movie
        ContentResolver contentResolver = mContext.getContentResolver();
        String type = contentResolver.getType(MovieEntry.CONTENT_URI);
        assertEquals("Error: MovieEntry CONTENT_URI should return MovieEntry.CONTENT_TYPE",
                MovieEntry.CONTENT_TYPE, type);

        // content://com.ratanachai.popularmovies/movie/1
        // vnd.android.cursor.item/com.ratanachai.popularmovies/movie
        type = contentResolver.getType(MovieEntry.buildMovieUri(MAD_MAX_ROW_ID));
        assertEquals("Error: MovieEntry CONTENT_URI with ID should return MovieEntry.CONTENT_ITEM_TYPE",
                MovieEntry.CONTENT_ITEM_TYPE, type);

        // content://com.ratanachai.popularmovies/movie/1/videos
        // vnd.android.cursor.dir/com.ratanachai.popularmovies/videos
        type = contentResolver.getType(VideoEntry.buildMovieVideosUri(MAD_MAX_ROW_ID));
        assertEquals("Error: VideoEntry CONTENT_URI should return VideoEntry.CONTENT_TYPE",
                VideoEntry.CONTENT_TYPE, type);

        // content://com.ratanachai.popularmovies/movie/1/reviews
        // vnd.android.cursor.dir/com.ratanachai.popularmovies/reviews
        type = contentResolver.getType(ReviewEntry.buildMovieReviewsUri(MAD_MAX_ROW_ID));
        assertEquals("Error: ReviewEntry CONTENT_URI should return ReviewEntry.CONTENT_TYPE",
                ReviewEntry.CONTENT_TYPE, type);

    }


    // Insert directly to DB, then uses the ContentProvider to read out the data.
    public void testBasicMovieQuery(){
        MovieDbHelper dbHelper = new MovieDbHelper(mContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Insert into DB directly
        ContentValues testValues1 = TestUtilities.createMadmaxMovieValues();
        long movieRowId1 = db.insert(MovieEntry.TABLE_NAME, null, testValues1);
        assertTrue("Unable to Insert a Movie into the Database", movieRowId1 != -1);

        // Then query out via Content Provider to compare
        ContentResolver cr = mContext.getContentResolver();

        // Query for all rows should return only 1 row
        Cursor retCursor = cr.query(MovieEntry.CONTENT_URI, null, null, null, null);
        assertEquals("Number of row returned should be 1", 1, retCursor.getCount());
        TestUtilities.validateCursor("testBasicMovieQuery [DIR]: ", retCursor, testValues1);

        // Insert another Movie into DB
        ContentValues testValues2 = TestUtilities.createInterstellarValues();
        long movieRowId2 = db.insert(MovieEntry.TABLE_NAME, null, testValues2);
        assertTrue("Unable to Insert a Movie into the Database", movieRowId2 != -1);

        // Query for all rows now should return only 2 rows
        retCursor = cr.query(MovieEntry.CONTENT_URI, null, null, null, null);
        assertEquals("Number of row returned incorrect", 2, retCursor.getCount());

        // Has the NotificationUri been set correctly? --- we can only test this easily against API
        // level 19 or greater because getNotificationUri was added in API level 19.
        if ( Build.VERSION.SDK_INT >= 19 ) {
            assertEquals("Error: Movie Query did not properly set NotificationUri",
                    retCursor.getNotificationUri(), MovieEntry.CONTENT_URI);
        }

        // Query for specific row
        retCursor = cr.query(MovieEntry.buildMovieUri(movieRowId1), null, null, null, null);
        TestUtilities.validateCursor("testBasicMovieQuery [ITEM]: ", retCursor, testValues1);

        db.close();

    }

    public void testBasicVideoQuery() {

        MovieDbHelper dbHelper = new MovieDbHelper(mContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Insert Movie into DB directly
        long madMaxRowId = TestUtilities.insertMovie(mContext, TestUtilities.createMadmaxMovieValues());
        Cursor retCursor = db.query(MovieEntry.TABLE_NAME, null, null, null, null, null, null);
        assertEquals("Number of row returned incorrect", 1, retCursor.getCount());

        // Query out Movie via Provider (just double check)
        ContentResolver cr = mContext.getContentResolver();
        retCursor = cr.query(MovieEntry.CONTENT_URI, null, null, null, null);
//        Log.d(LOG_TAG, DatabaseUtils.dumpCursorToString(retCursor));
        assertEquals("Number of row returned incorrect", 1, retCursor.getCount());

        // Insert Video into DB directly
        ContentValues videoValue1 = TestUtilities.createVideo1ValuesForMovie(madMaxRowId);
        long rowId1 = db.insert(VideoEntry.TABLE_NAME, null, videoValue1);
        assertTrue("Unable to Insert a video into DB", rowId1 != -1);

        ContentValues videoValue2 = TestUtilities.createVideo2ValuesForMovie(madMaxRowId);
        long rowId2 = db.insert(VideoEntry.TABLE_NAME, null, videoValue2);
        assertTrue("Unable to Insert a video into DB", rowId2 != -1);

        retCursor = db.query(VideoEntry.TABLE_NAME, null, null, null, null, null, null);
        assertEquals("Number of row returned incorrect", 2, retCursor.getCount());

        // Query out Videos via Provider
        retCursor = cr.query(VideoEntry.CONTENT_URI, null, null, null, null);
        assertEquals("Number of row returned incorrect", 2, retCursor.getCount());
        retCursor = cr.query(VideoEntry.buildMovieVideosUri(madMaxRowId), null, null, null, null);
        assertEquals("Number of row returned incorrect", 2, retCursor.getCount());
        Log.d(LOG_TAG, DatabaseUtils.dumpCursorToString(retCursor));

        db.close();
    }

    public void testBasicReviewQuery() {
        MovieDbHelper dbHelper = new MovieDbHelper(mContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        long madMaxRowId = TestUtilities.insertMovie(mContext, TestUtilities.createMadmaxMovieValues());

        // Insert Reviews into DB directly
        ContentValues reviewValue1 = TestUtilities.createReview1ValuesForMovie(madMaxRowId);
        long rowId1 = db.insert(ReviewEntry.TABLE_NAME, null, reviewValue1);
        assertTrue("Unable to Insert a review into DB", rowId1 != -1);

        ContentValues reviewValue2 = TestUtilities.createReview2ValuesForMovie(madMaxRowId);
        long rowId2 = db.insert(ReviewEntry.TABLE_NAME, null, reviewValue2);
        assertTrue("Unable to Insert a review into DB", rowId2 != -1);

        Cursor retCursor = db.query(ReviewEntry.TABLE_NAME, null, null, null, null, null, null);
        assertEquals("Number of row returned incorrect", 2, retCursor.getCount());

        // Query out Reviews via Content Provider
        ContentResolver cr = mContext.getContentResolver();
        retCursor = cr.query(ReviewEntry.CONTENT_URI, null, null, null, null);
        Log.d(LOG_TAG, DatabaseUtils.dumpCursorToString(retCursor));
        assertEquals("Number of row returned incorrect", 2, retCursor.getCount());

        retCursor = cr.query(ReviewEntry.buildMovieReviewsUri(madMaxRowId), null, null, null, null);
        Log.d(LOG_TAG, DatabaseUtils.dumpCursorToString(retCursor));
        assertEquals("Number of row returned incorrect", 2, retCursor.getCount());

        db.close();
    }

    public void testInsertReadProvider() {
        ContentResolver cr = mContext.getContentResolver();
        TestUtilities.TestContentObserver tco = TestUtilities.getTestContentObserver();
        ContentValues madMaxValues = TestUtilities.createMadmaxMovieValues();

        // Register a content observer before insert via ContentResolver to test
        cr.registerContentObserver(MovieEntry.CONTENT_URI, true, tco);
        Uri movieUri = cr.insert(MovieEntry.CONTENT_URI, madMaxValues);
        long madMaxRowId = ContentUris.parseId(movieUri);

        // Did our content observer get called? If this fails, your insert movie
        // isn't calling getContext().getContentResolver().notifyChange(uri, null);
        tco.waitForNotificationOrFail();
        cr.unregisterContentObserver(tco);

        // Verify we got a row back. Then, pull some out and verify it made the round trip.
        Cursor retCursor = cr.query(MovieEntry.CONTENT_URI, null, null, null, null);
        assertEquals("Number of row returned incorrect", 1, retCursor.getCount());
        retCursor.moveToFirst();
        TestUtilities.validateCurrentRecord("testInsertReadProvider. Error validating MovieEntry.",
                retCursor, madMaxValues);
        assertTrue(madMaxRowId != -1);

        //--- 1st Video: Add some videos since now we have a movie
        ContentValues videoValue1 = TestUtilities.createVideo1ValuesForMovie(madMaxRowId);
        tco = TestUtilities.getTestContentObserver();
        cr.registerContentObserver(VideoEntry.CONTENT_URI, true, tco);
        Uri videoUri1 = cr.insert(VideoEntry.CONTENT_URI, videoValue1);
        assertTrue(videoUri1 != null);

        // If this fails, your insert isn't calling notifyChange(uri, null);
        tco.waitForNotificationOrFail();
        cr.unregisterContentObserver(tco);

        // Verify we got a row back. Then, pull some out and verify it made the round trip.
        retCursor = cr.query(VideoEntry.CONTENT_URI, null, null, null, null);
        TestUtilities.validateCursor("testInsertReadProvider. Error validating VideoEntry insert.",
                retCursor, videoValue1);

        //--- 2nd Video
        ContentValues videoValue2 = TestUtilities.createVideo2ValuesForMovie(madMaxRowId);
        tco = TestUtilities.getTestContentObserver();
        cr.registerContentObserver(VideoEntry.CONTENT_URI, true, tco);
        Uri videoUri2 = cr.insert(VideoEntry.CONTENT_URI, videoValue2);
        assertTrue(videoUri2 != null);

        // If this fails, your insert isn't calling notifyChange(uri, null);
        tco.waitForNotificationOrFail();
        cr.unregisterContentObserver(tco);

        // Verify we got a row back. Then, pull some out and verify it made the round trip.
        retCursor = cr.query(VideoEntry.CONTENT_URI, null, null, null, null);
        assertEquals("Num of rows returned incorrect", 2, retCursor.getCount());
        retCursor.moveToFirst();
        TestUtilities.validateCurrentRecord("Error validating VideoEntry insert.", retCursor, videoValue1);
        retCursor.moveToNext();
        TestUtilities.validateCurrentRecord("Error validating VideoEntry insert.", retCursor, videoValue2);

        // Add the Video values in with the Movie data so that we can make
        // sure that the join worked and we actually get all the values back
        videoValue1.putAll(madMaxValues);

        // Get the joined Video and Movie data
        retCursor = cr.query(VideoEntry.buildMovieVideosUri(madMaxRowId), null, null, null, null);
//        Log.d(LOG_TAG, DatabaseUtils.dumpCursorToString(retCursor));
        retCursor.moveToFirst();
        TestUtilities.validateCurrentRecord("Error validating joined Video and Movie Data.",
                retCursor, videoValue1);

        // TODO: The same test for Review
    }

    /*
        This test uses the provider to insert and then update the data.
     */

    public void testUpdateLocation() {

        // Create a test Movie
        ContentValues madMaxValues = TestUtilities.createMadmaxMovieValues();
        ContentResolver cr = mContext.getContentResolver();
        Uri madMaxUri = cr.insert(MovieEntry.CONTENT_URI, madMaxValues);
        long madMaxRowId = ContentUris.parseId(madMaxUri);
        assertTrue(madMaxRowId != -1);
        Log.d(LOG_TAG, "New row id: " + madMaxRowId);

        // Create a new map of values, where column names are the keys
        ContentValues updatedValues = new ContentValues(madMaxValues);
        updatedValues.put(MovieEntry._ID, madMaxRowId);
        updatedValues.put(MovieEntry.COLUMN_OVERVIEW, "Overview should be this short.");

        // Create a cursor with observer to make sure that content provider is notifying the observers
        Cursor movCursor = cr.query(MovieEntry.CONTENT_URI, null, null, null, null);
        TestUtilities.TestContentObserver tco = TestUtilities.getTestContentObserver();
        movCursor.registerContentObserver(tco);

        // Perform Update
        int count = cr.update(MovieEntry.CONTENT_URI, updatedValues, MovieEntry._ID + "= ?",
                new String[]{Long.toString(madMaxRowId)});
        assertEquals(count, 1);

        // Test to make sure our observer is called.  If not, we throw an assertion.
        // If this fail, Content provider isn't calling notifyChange(uri, null)
        tco.waitForNotificationOrFail();
        movCursor.unregisterContentObserver(tco);
        movCursor.close();

        // Query out to verify update
        Cursor cursor = cr.query(MovieEntry.buildMovieUri(madMaxRowId), null, null, null, null);
        TestUtilities.validateCursor("Error validating Movie entry update.", cursor, updatedValues);
        cursor.close();

        // TODO: The same test for Video and Review
    }

    // Make sure we can still delete after adding/updating stuff
    public void testDeleteRecords() {
        // Insert first
        testInsertReadProvider();

        // Register a content observer for our Movie delete.
        TestUtilities.TestContentObserver testMovOb = TestUtilities.getTestContentObserver();
        ContentResolver cr = mContext.getContentResolver();
        cr.registerContentObserver(MovieEntry.CONTENT_URI, true, testMovOb);

        // Register a content observer for our Video delete.
        TestUtilities.TestContentObserver testVideoOb = TestUtilities.getTestContentObserver();
        cr.registerContentObserver(VideoEntry.CONTENT_URI, true, testVideoOb);

        deleteAllRecordsFromProvider();

        // If Fail, did you call NotifyChange(uri, null); in the ContentProvider delete?
        testMovOb.waitForNotificationOrFail();
        testVideoOb.waitForNotificationOrFail();

        cr.unregisterContentObserver(testMovOb);
        cr.unregisterContentObserver(testVideoOb);
    }
}