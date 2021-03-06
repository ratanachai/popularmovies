package com.ratanachai.popularmovies;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.support.v7.widget.ShareActionProvider;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ratanachai.popularmovies.data.MovieContract.MovieEntry;
import com.ratanachai.popularmovies.data.MovieContract.ReviewEntry;
import com.ratanachai.popularmovies.data.MovieContract.VideoEntry;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class DetailActivityFragment extends BaseFragment {

    public static final String LOG_TAG = DetailActivityFragment.class.getSimpleName();
    static final String MOVIE_INFO = "MOVIE_INFO";
    private Movie mMovie;
    private ArrayList<Video> mVideos = new ArrayList<>();
    private ArrayList<Review> mReviews = new ArrayList<>();
    private View mRootview;
    private boolean mAddVideosAndReviews = false;
    private boolean mTwoPane = false;
    private ShareActionProvider mShareActionProvider;
    private TrailerAdapter mTrailerAdapter;
    private Activity mActivity;

    // All activity that contain this fragment must implement this Callback
    // MainActivity for tablet and DetailActivity for phone
    public interface Callback {
        void onAddRemoveMovieFromFavorite(boolean needReFetch);
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        mSortBy = getSortBy();
        mTwoPane = getActivity().getLocalClassName().equals("MainActivity");

        // Get data passed from Activity
        Bundle arguments = getArguments();
        if (arguments != null) {
            String[] m = arguments.getStringArray(MOVIE_INFO);
            mMovie = new Movie(m[0], m[1], m[2], m[3], m[4], m[5], m[6]);

            /** Fetch if no savedInstance at all, or for both video and review */
            // http://stackoverflow.com/questions/12503836/how-to-save-custom-arraylist-on-android-screen-rotate
            if (savedInstanceState == null
                    || !savedInstanceState.containsKey("videos")
                    || !savedInstanceState.containsKey("reviews")) {
                String tmdb_movie_id = mMovie.getTmdbId();
                // Fetch from the internet for DB in offline mode
                getVideosFromInternetOrDb(tmdb_movie_id);
                getReviewsFromInternetOrDb(tmdb_movie_id);
                getSecondBatchOfMovieInfo(tmdb_movie_id);

            } else {
                // Restore from savedInstanceState
                mVideos = savedInstanceState.getParcelableArrayList("videos");
                mReviews = savedInstanceState.getParcelableArrayList("reviews");
                mMovie = savedInstanceState.getParcelable("movie");
                mAddVideosAndReviews = true;
            }
        }
        // Create Adapter
        mTrailerAdapter = new TrailerAdapter(mVideos);
    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putParcelable("movie", mMovie);
        if(!mVideos.isEmpty()) outState.putParcelableArrayList("videos", mVideos);
        if(!mReviews.isEmpty()) outState.putParcelableArrayList("reviews", mReviews);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Early Exit if no argument from MainActivity
        if (getArguments() == null)
            return mRootview;

        final Activity activity = getActivity();
        mRootview = inflater.inflate(R.layout.fragment_detail, container, false);

        // Setup Share action and up button
        setHasOptionsMenu(true);
        Toolbar toolBar = (Toolbar) mRootview.findViewById(R.id.tool_bar);
        toolBar.setTitle("");
        ((AppCompatActivity)getActivity()).setSupportActionBar(toolBar);
        ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
        if (actionBar != null && !mTwoPane)
            actionBar.setDisplayHomeAsUpEnabled(true);

        // Put data into Overview card
        setMovieInfoViews();
        setMovieOverviewView(mMovie.getOverview());

        // Download High Resolution poster, Move up cards
        final ImageView iv = (ImageView) mRootview.findViewById(R.id.movie_poster);
        final CardView overview_card = (CardView) mRootview.findViewById(R.id.movie_overview_card);
        final String highResUri = "http://image.tmdb.org/t/p/original" + mMovie.getPosterPath();
        // In Try-catch in case file cannot be open
        try {
            // Get/Prepare "InterimPoster" jpeg file to be placeholder image
            Bitmap bitmap = BitmapFactory.decodeStream(activity.openFileInput("InterimPoster"));
            final Drawable lowResPoster = new BitmapDrawable(getResources(), bitmap);
            // Set Background Poster with low-res image first
            iv.setImageDrawable(lowResPoster);

            // Set High resolution image later after transition ends
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                Transition tr = activity.getWindow().getSharedElementEnterTransition();
                tr.addListener(new Transition.TransitionListener() {
                    @Override
                    public void onTransitionEnd(Transition transition) {
                        loadHighResMoveUpCards(overview_card, activity, highResUri, lowResPoster, iv);
                    }
                    @Override public void onTransitionStart(Transition transition) {}
                    @Override public void onTransitionCancel(Transition transition) {}
                    @Override public void onTransitionPause(Transition transition) {}
                    @Override public void onTransitionResume(Transition transition) {}
                });
                // In case screen rotated and view is recreated without transition from MainActivity
                // Or in Tablet (DetailActivity resides in MainActivity)
                if (savedInstanceState != null || mTwoPane)
                    loadHighResMoveUpCards(overview_card, activity, highResUri, lowResPoster, iv);

            // For API<21 (No Transition animation), just repalce with high-res ASAP.
            } else {
                loadHighResMoveUpCards(overview_card, activity, highResUri, lowResPoster, iv);
            }

        } catch (FileNotFoundException e) {
            // If low-res file can't be opened, then just use high res with picasso
            loadHighResMoveUpCards(overview_card, activity, highResUri, null, iv);
            e.printStackTrace();
        }

        // FAB - Set Listener: Add/Remove TMDB_MOV_ID on checked/unchecked
        FloatingActionButton favButton = (FloatingActionButton) mRootview.findViewById(R.id.favorite_toggle);
        favButton.setContentDescription(getString(R.string.add_to_fav));
        final String tmdb_id = mMovie.getTmdbId();

        // Set OnCheckChanged
        favButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                ContentResolver cr = getActivity().getContentResolver();
                Cursor movieCursor = cr.query(MovieEntry.CONTENT_URI,
                        new String[]{MovieEntry.TABLE_NAME + "." + MovieEntry._ID},
                        MovieEntry.COLUMN_TMDB_MOVIE_ID + " = ? ", new String[]{tmdb_id}, null);

                // Add/Remove to Database
                if(movieCursor.getCount() == 0) {
                    // Save the Movie, its Videos and Reviews
                    long movieRowId = saveMovieOffline(mMovie.getAll());
                    saveVideosOffline(movieRowId);
                    saveReviewOffline(movieRowId);

                    // Sort by Fav: MainFragment will need to refetch movies if movie added
                    if ( isSortByFavorite(mSortBy) )
                        ((Callback) activity).onAddRemoveMovieFromFavorite(true);
                }
                else{
                    // Delete the Movie, its videos will be DELETE CASCADE via Foreign key constrain
                    removeOfflineMovie(tmdb_id);

                    // Sort by Fav: MainFragment will need to refetch movies if movie removed
                    if ( isSortByFavorite(mSortBy) )
                        ((Callback) activity).onAddRemoveMovieFromFavorite(true);
                }
            }
        });

        // Set Movie Trailer
        GridView gv = (GridView) mRootview.findViewById(R.id.gridview_trailers);
        gv.setNumColumns(activity.getResources().getInteger(R.integer.trailer_num_columns));
        gv.setAdapter(mTrailerAdapter);

        // Restore Trailer Videos and Reviews (First time added via OnPostExecute)
        if (mAddVideosAndReviews) {
            showTrailers();
            addReviewsTextView(mReviews);
        }

        return mRootview;
    }

    private void setMovieInfoViews() {
        // Set Movie Title TextView
        TextView titleTv = (TextView) mRootview.findViewById(R.id.movie_title);
        titleTv.setText(mMovie.getTitle());
        titleTv.setContentDescription(getString(R.string.movie_title, mMovie.getTitle()));

        // Set Movie Rating (User Vote average) TextView and RatingBar
        TextView voteAvgTv = (TextView) mRootview.findViewById(R.id.movie_rating);
        voteAvgTv.setText(mMovie.getVoteAverage());
        voteAvgTv.setContentDescription(getString(R.string.movie_rating, mMovie.getVoteAverage(), mMovie.getVoteCount()));
        TextView voteCountTv = (TextView) mRootview.findViewById(R.id.movie_vote_count);
        voteCountTv.setText("(" + mMovie.getVoteCount() + " " + getString(R.string.movie_votes) + ")");

        RatingBar ratingBar = (RatingBar)mRootview.findViewById(R.id.movie_rating_bar);
        ratingBar.setRating(Float.parseFloat(mMovie.getVoteAverage()));

        // Set Movie Release date
        TextView releaseDateTv = (TextView) mRootview.findViewById(R.id.movie_release);
        releaseDateTv.append(" " + mMovie.getReleaseDate());
        releaseDateTv.setContentDescription(getString(R.string.movie_release_date, mMovie.getReleaseDate()));
    }
    private void setMovieOverviewView(String overview) {
        // Set Movie Overview TextView
        TextView overviewTv = (TextView) mRootview.findViewById(R.id.movie_overview);
        overviewTv.setText(overview);
        overviewTv.setContentDescription(getString(R.string.movie_overview, overview));
    }

    private void loadHighResMoveUpCards(CardView overview_card, Activity activity, String highResUri, Drawable lowResPoster, ImageView iv) {
        // Download higher resolution image after transition ends to avoid load complete
        // before animation finishes (causing some flicker/overflow image problem).

        if (lowResPoster != null) {
            Picasso.with(activity).load(highResUri)
                    // still need placeholder here otherwise will flash of white image
                    .placeholder(lowResPoster).error(lowResPoster)
                    .fit().centerCrop()
                    .noFade() // without this image replacement will not be smooth
                    .into(iv);
        } else {
            Picasso.with(activity).load(highResUri)
                    .error(R.drawable.film)
                    .fit().centerCrop()
                    .noFade()
                    .into(iv);
        }
        // Finally Set cards moving up animation
        animateCardsMoveUp(activity);
        overview_card.setVisibility(View.VISIBLE);
    }

    private void animateCardsMoveUp(Activity activity) {
        LinearLayout cards = (LinearLayout) mRootview.findViewById(R.id.movie_info_cards);
        Animation anim = AnimationUtils.loadAnimation(activity, R.anim.move_up);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        cards.setAnimation(anim);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu,inflater);
        inflater.inflate(R.menu.menu_detail_fragment, menu);

        /** Based on Android ActionBarCompat-ShareActionProvider Sample
           https://github.com/googlesamples/android-ActionBarCompat-ShareActionProvider */
        // Get ActionProvider
        MenuItem item = menu.findItem(R.id.menu_item_share);
        mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);

        // Set Share Intent with/without Video URL depend whether Video fetching onPostExec() has finished
        if (mVideos.isEmpty()) {
            mShareActionProvider.setShareIntent(createShareVideoLinkIntent("", mMovie.getTitle()));
            Log.v(LOG_TAG, "=== onCreateOptionsMenu() Set intent with EMPTY STRING");
        }else {
            mShareActionProvider.setShareIntent(createShareVideoLinkIntent(mVideos.get(0).getYoutubeUrl(), mMovie.getTitle()));
            Log.v(LOG_TAG, "=== onCreateOptionsMenu() Set intent with " + mVideos.get(0).getYoutubeUrl());
        }
    }
    private Intent createShareVideoLinkIntent(String url, String movieTitle) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");

        String text = getString(R.string.share_action_text) + " - " + movieTitle + "\n" + url;
        intent.putExtra(Intent.EXTRA_TEXT, text);
        return intent;
    }
    /** SAVE MOVIE into Favorite List */
    long saveMovieOffline(String[] movieInfo){

        // QUERY to check before INSERT
        long rowId;
        ContentResolver cr = getActivity().getContentResolver();
        Cursor movieCursor = cr.query(MovieEntry.CONTENT_URI, Movie.MOVIE_COLUMNS,
                MovieEntry.COLUMN_TMDB_MOVIE_ID + " = ? ", new String[]{movieInfo[0]}, null);
        if (movieCursor.getCount() == 0) {

            // Prepare ContentValues, then INSERT
            ContentValues movieValues = new ContentValues();
            movieValues.put(MovieEntry.COLUMN_TMDB_MOVIE_ID, movieInfo[0]);
            movieValues.put(MovieEntry.COLUMN_TITLE, movieInfo[1]);
            movieValues.put(MovieEntry.COLUMN_POSTER_PATH, movieInfo[2]);
            movieValues.put(MovieEntry.COLUMN_OVERVIEW, movieInfo[3]);
            movieValues.put(MovieEntry.COLUMN_RELEASE_DATE, movieInfo[4]);
            movieValues.put(MovieEntry.COLUMN_VOTE_AVERAGE, movieInfo[5]);
            Uri movieUri = cr.insert(MovieEntry.CONTENT_URI, movieValues);
            Toast.makeText(getActivity(), getString(R.string.add_to_fav), Toast.LENGTH_SHORT).show();
            rowId = ContentUris.parseId(movieUri);

        }else{
            Log.v(LOG_TAG, "Movie with the same TMDB ID already saved");
            rowId = -1;
        }
        movieCursor.close();
        return rowId;
    }
    void saveVideosOffline(long movieRowId){
        ContentResolver cr = getActivity().getContentResolver();

        // FOR each Video...
        for (int i = 0; i < mVideos.size(); i++) {
            Video video = mVideos.get(i);

            // QUERY to check before INSERT
            Cursor videoCursor = cr.query(VideoEntry.CONTENT_URI, null,
                    VideoEntry.COLUMN_KEY + " = ? ", new String[]{video.getKey()}, null);
            Log.v(LOG_TAG, "== getCount() = " + Integer.toString(videoCursor.getCount()));
            if (videoCursor.getCount() == 0) {

                // Prepare ContentValues, then INSERT
                ContentValues videoValues = new ContentValues();
                videoValues.put(VideoEntry.COLUMN_MOV_KEY, movieRowId);
                videoValues.put(VideoEntry.COLUMN_KEY, video.getKey());
                videoValues.put(VideoEntry.COLUMN_NAME, video.getName());
                videoValues.put(VideoEntry.COLUMN_TYPE, video.getType());
                videoValues.put(VideoEntry.COLUMN_SITE, video.getSite());
                cr.insert(VideoEntry.CONTENT_URI, videoValues);
                Log.v(LOG_TAG, "== a Video inserted into DB");
            } else {
                Log.v(LOG_TAG, "== a Video with the same Key is already in DB");
            }
            videoCursor.close();
        }
    }
    void saveReviewOffline(long movieRowId){
        ContentResolver cr = getActivity().getContentResolver();

        for (int i = 0; i < mReviews.size(); i++ ){
            Review review = mReviews.get(i);
            // Query to check before insert
            Cursor cur = cr.query(ReviewEntry.CONTENT_URI, null,
                    ReviewEntry.COLUMN_TMDB_REVIEW_ID + " = ?", new String[]{review.getId()}, null);
            if (cur.getCount() == 0){
                ContentValues reviewValues = new ContentValues();
                reviewValues.put(ReviewEntry.COLUMN_MOV_KEY, movieRowId);
                reviewValues.put(ReviewEntry.COLUMN_TMDB_REVIEW_ID, review.getId());
                reviewValues.put(ReviewEntry.COLUMN_AUTHOR, review.getAuthor());
                reviewValues.put(ReviewEntry.COLUMN_CONTENT, review.getContent());
                reviewValues.put(ReviewEntry.COLUMN_URL, review.getUrl());
                cr.insert(ReviewEntry.CONTENT_URI, reviewValues);
                Log.v(LOG_TAG, "== a Review inserted into DB");
            } else {
                Log.v(LOG_TAG, "== a Review with the same Review ID is already in DB");
            }
            cur.close();
        }
    }
    // This will remove not just Movie, but its associated videos and reviews too.
    void removeOfflineMovie(String tmdbMovieId){
        ContentResolver cr = getActivity().getContentResolver();
        int rowsDeleted = cr.delete(MovieEntry.CONTENT_URI,
                MovieEntry.COLUMN_TMDB_MOVIE_ID + " = ?", new String[]{tmdbMovieId});

        if (rowsDeleted != 0)
            Toast.makeText(getActivity(), getString(R.string.remove_from_fav_success), Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(getActivity(), getString(R.string.remove_from_fav_fail), Toast.LENGTH_SHORT).show();
    }

    private long getMovieRowId(String tmdb_movie_id){
        ContentResolver cr = getActivity().getContentResolver();
        Cursor movieCursor = cr.query(MovieEntry.CONTENT_URI, Movie.MOVIE_COLUMNS,
                MovieEntry.COLUMN_TMDB_MOVIE_ID + " = ? ", new String[]{tmdb_movie_id}, null);
        movieCursor.moveToNext();
        long movieRowId = movieCursor.getLong(Movie.COL_MOVIE_ROW_ID);
        movieCursor.close();
        return movieRowId;
    }
    /** Code for Movie Video (Trailer) ---------------------------------------------------------- */
    private void getVideosFromInternetOrDb(String tmdb_movie_id){
        // Get Videos from Database
        if(isSortByFavorite(mSortBy)) {
            ContentResolver cr = getActivity().getContentResolver();
            long movieRowId = getMovieRowId(tmdb_movie_id);

            // Query Video for the movie, then Populate ArrayList of Videos
            Cursor cur  = cr.query(VideoEntry.buildMovieVideosUri(movieRowId), Video.VIDEO_COLUMNS,
                    null, null , null);
            mVideos.clear();
            while (cur.moveToNext()){
                Video videoObj = new Video(cur.getString(Video.COL_KEY), cur.getString(Video.COL_NAME),
                        cur.getString(Video.COL_TYPE), cur.getString(Video.COL_SITE));
                mVideos.add(videoObj);
            }
            mAddVideosAndReviews = true;

        // Fetch videos from the Internet in background
        }else if(Utility.isNetworkAvailable(getActivity())) {
            FetchVideosTask fetchVideosTask = new FetchVideosTask();
            fetchVideosTask.execute(tmdb_movie_id);

        }else{
            Utility.networkToast(getActivity());
            Log.v(LOG_TAG, "== No Network, Not in offline mode: will not try to fetch videos");
        }
    }

    public class FetchVideosTask extends AsyncTask<String, Void, ArrayList<Video>> {

        private final String LOG_TAG = FetchVideosTask.class.getSimpleName();

        /** Takes the JSON string and converts it into an Object hierarchy. */
        private ArrayList<Video> getVideosFromJson(String videosJsonStr)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String TMDB_VIDEOS = "results";
            final String TMDB_VIDEO_KEY = "key";
            final String TMDB_VIDEO_NAME = "name";
            final String TMDB_VIDEO_SITE = "site";
            final String TMDB_VIDEO_TYPE = "type";

            JSONObject videosJson = new JSONObject(videosJsonStr);
            JSONArray videosArray = videosJson.getJSONArray(TMDB_VIDEOS);

            // Create Video objects and put them into ArrayList
            mVideos.clear(); // Must clear the list before adding new
            for(int i = 0; i < videosArray.length(); i++){
                JSONObject aVideo = videosArray.getJSONObject(i);
                Video videoObj = new Video(aVideo.getString(TMDB_VIDEO_KEY),
                                            aVideo.getString(TMDB_VIDEO_NAME),
                                            aVideo.getString(TMDB_VIDEO_TYPE),
                                            aVideo.getString(TMDB_VIDEO_SITE));
                mVideos.add(videoObj);
            }

            return mVideos;
        }

        @Override
        protected ArrayList<Video> doInBackground(String... params) {

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String videosJsonStr = null;
            String movie_id = params[0];
            String api_key = getString(R.string.api_key);

            try {
                // Construct the URL for TMDB query
                // http://api.themoviedb.org/3/movie/15121/videos?api_key=<api_key>
                final String BASE_URL = "http://api.themoviedb.org/3/movie/";
                final String API_KEY_PARAM = "api_key";

                Uri builtUri = Uri.parse(BASE_URL).buildUpon()
                        .appendPath(movie_id)
                        .appendPath("videos")
                        .appendQueryParameter(API_KEY_PARAM, api_key)
                        .build();

                URL url = new URL(builtUri.toString());

                // Create the request to TMDB, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                videosJsonStr = buffer.toString();

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                return getVideosFromJson(videosJsonStr);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            // This will only happen if there was an error getting or parsing the forecast.
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<Video> videos) {
            if (videos != null) {
                showTrailers();
                // Update ShareIntent IF onCreateOptionsMenu already happened and there's some videos
                if (mShareActionProvider != null & !mVideos.isEmpty()) {
                    Log.v(LOG_TAG, "=== onPostExec() Updating intent with " + videos.get(0).getYoutubeUrl());
                    mShareActionProvider.setShareIntent(
                            createShareVideoLinkIntent(videos.get(0).getYoutubeUrl(), mMovie.getTitle()) );
                }
            }
        }
    }

    /** How-to use Picasso with ArrayAdapter from Big Nerd Ranch
     https://www.bignerdranch.com/blog/solving-the-android-image-loading-problem-volley-vs-picasso/
     */
    private class TrailerAdapter extends ArrayAdapter<Video> {

        public TrailerAdapter(ArrayList<Video> videos) {
            super(getActivity(), 0, videos);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            if (convertView == null)
                convertView = getActivity().getLayoutInflater()
                        .inflate(R.layout.grid_item_trailer, parent, false);

            // Set TextView
            TextView tv = (TextView) convertView.findViewById(R.id.movie_trailer_title);
            tv.setText(getItem(position).getName());

            // Download Image from Youtube
            ImageView iv = (ImageView)convertView.findViewById(R.id.movie_trailers_thumbnail);
            Picasso.with(getActivity())
                    .load("http://img.youtube.com/vi/" + getItem(position).getKey() + "/0.jpg")
                    .placeholder(R.drawable.film)
                    .into(iv);

            // Setup OnItemClick to launch Youtube App with the video
            iv.setTag(getItem(position).getKey());
            iv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "vnd.youtube:" + v.getTag()));
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                                "http://www.youtube.com/watch?v=" + v.getTag()));
                        startActivity(intent);
                    }
                }
            });

            // a11y for ImageView
            iv.setContentDescription(getString(R.string.movie_trailer, getItem(position).getName()));

            return convertView;
        }
    }

    /** Code for Movie Review ------------------------------------------------------------------- */
    private void getReviewsFromInternetOrDb(String tmdb_movie_id){

        // Get from Database
        if(isSortByFavorite(mSortBy)) {
            ContentResolver cr = getActivity().getContentResolver();
            long movieRowId = getMovieRowId(tmdb_movie_id);

            // Query Reviews for the movie, then Populate ArrayList of Reviews
            Cursor cur  = cr.query(ReviewEntry.buildMovieReviewsUri(movieRowId), Review.REVIEW_COLUMNS,
                    null, null , null);
            mReviews.clear();
            while (cur.moveToNext()){
                Review reviewObj = new Review(cur.getString(Review.COL_TMDB_REVIEW_ID), cur.getString(
                        Review.COL_AUTHOR), cur.getString(Review.COL_CONTENT), cur.getString(Review.COL_URL));
                mReviews.add(reviewObj);
            }
            mAddVideosAndReviews = true;

        // Fetch videos from the Internet in background
        }else if(Utility.isNetworkAvailable(getActivity())) {

            FetchReviewsTask fetchReviewsTask = new FetchReviewsTask();
            fetchReviewsTask.execute(tmdb_movie_id);

        }else{
            Utility.networkToast(getActivity());
            Log.v(LOG_TAG, "== No Network, Not in offline mode: will not try to fetch reviews");
        }
    }

    private void showTrailers() {
        mTrailerAdapter.notifyDataSetChanged();
        if(mTrailerAdapter.getCount() > 0)
            mRootview.findViewById(R.id.movie_trailers_card).setVisibility(View.VISIBLE);
    }

    private void addReviewsTextView(ArrayList<Review> reviews) {
        if (reviews.size() > 0)
            mRootview.findViewById(R.id.movie_reviews_card).setVisibility(View.VISIBLE);

        ViewGroup containerView = (ViewGroup) mRootview.findViewById(R.id.movie_reviews_container);
        for (int i=0; i < reviews.size(); i++) {

            View v = mActivity.getLayoutInflater().inflate(R.layout.review_item, containerView, false);
            TextView reviewAuthor = (TextView) v.findViewById(R.id.movie_review_author);
            TextView reviewContent = (TextView) v.findViewById(R.id.movie_review_content);
            reviewAuthor.setText(reviews.get(i).getAuthor());
            reviewContent.setText(reviews.get(i).getContent());
//            reviewContent.setContentDescription(getString(R.string.movie_review,
//                    reviewAuthor.getText(), reviewContent.getText()));
            containerView.addView(v);

            // Add separator line if it is not the last item
            if(i == (reviews.size() - 1))
                v.findViewById(R.id.separator).setVisibility(View.GONE);

        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = (Activity)context;
    }

    public class FetchReviewsTask extends AsyncTask<String, Void, ArrayList<Review>> {

        private final String LOG_TAG = FetchReviewsTask.class.getSimpleName();

        private ArrayList<Review> getReviewsFromJson(String reviewsJsonStr) throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String TMDB_REVIEWS = "results";
            final String TMDB_REVIEW_ID = "id";
            final String TMDB_REVIEW_AUTHOR = "author";
            final String TMDB_REVIEW_CONTENT = "content";
            final String TMDB_REVIEW_URL = "url";

            JSONObject reviewsJson = new JSONObject(reviewsJsonStr);
            JSONArray reviewsArray = reviewsJson.getJSONArray(TMDB_REVIEWS);

            // Create Review objects and put them into ArrayList
            mReviews.clear(); // Must clear the list before adding new
            for(int i = 0; i < reviewsArray.length(); i++){
                JSONObject aReview = reviewsArray.getJSONObject(i);
                Review reviewObj = new Review(aReview.getString(TMDB_REVIEW_ID),
                        aReview.getString(TMDB_REVIEW_AUTHOR),
                        aReview.getString(TMDB_REVIEW_CONTENT),
                        aReview.getString(TMDB_REVIEW_URL));
                mReviews.add(reviewObj);
            }

            return mReviews;
        }

        @Override
        protected ArrayList<Review> doInBackground(String... params) {

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String reviewsJsonStr = null;
            String movie_id = params[0];
            String api_key = getString(R.string.api_key);

            try {
                // Construct the URL for TMDB query
                // http://api.themoviedb.org/3/movie/15121/reviews?api_key=<api_key>
                final String BASE_URL = "http://api.themoviedb.org/3/movie/";
                final String API_KEY_PARAM = "api_key";

                Uri builtUri = Uri.parse(BASE_URL).buildUpon()
                        .appendPath(movie_id).appendPath("reviews")
                        .appendQueryParameter(API_KEY_PARAM, api_key)
                        .build();

                URL url = new URL(builtUri.toString());

                // Create the request to TMDB, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                reviewsJsonStr = buffer.toString();

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the data, no point in attempting to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                return getReviewsFromJson(reviewsJsonStr);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            // This will only happen if there was an error getting or parsing the forecast.
            return null;
        }

        @Override
        protected void onPostExecute(ArrayList<Review> reviews) {
            if (reviews != null) {
                addReviewsTextView(reviews);
            }
        }
    }

    private void getSecondBatchOfMovieInfo(String tmdb_movie_id){

        if(Utility.isNetworkAvailable(getActivity())) {
            FetchMovieDetailTask fetchMovieDetailTask = new FetchMovieDetailTask();
            fetchMovieDetailTask.execute(tmdb_movie_id);

        }else{
            Utility.networkToast(getActivity());
            Log.v(LOG_TAG, "== No Network will not try to fetch 2nd batch of movie info");
        }
    }

    public class FetchMovieDetailTask extends AsyncTask<String, Void, Movie> {
        private final String LOG_TAG = FetchMovieDetailTask.class.getSimpleName();

        private Movie getMovieDetailFromJson(String movieJsonStr) throws JSONException {
            final String BUDGET = "budget";
            final String GENRES = "genres";
            final String HOMEPAGE = "homepage";
            final String IMDB_ID = "imdb_id";
            final String ORIGINAL_LANG = "original_language";
            final String ORIGINAL_TITLE = "original_title";
            final String OVERVIEW = "overview";
            final String REVENUE = "revenue";
            final String RUNTIME = "runtime";
            final String STATUS = "status";
            final String TAGLINE = "tagline";

            JSONObject aMovie = new JSONObject(movieJsonStr);
            if(mMovie.getOverview().equals(""))
                mMovie.setOverview(aMovie.getString(OVERVIEW));
//            mMovie.setTagline(aMovie.getString(TAGLINE));

            return mMovie;
        }

        @Override
        protected Movie doInBackground(String... params) {
//            if(!isAdded())
//                return null;

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String movieJsonStr = null;
            String api_key = getString(R.string.api_key);

            try {
                // Construct the URL for TMDB query
                // http://api.themoviedb.org/3/movie/<id>?language=en&api_key=4275cdb955fd96533adb7f51ec340f21
                final String BASE_URL = "http://api.themoviedb.org/3/movie/" + params[0];
                final String LANG = "language";
                final String API_KEY_PARAM = "api_key";

                Uri.Builder ub = Uri.parse(BASE_URL).buildUpon()
                        .appendQueryParameter(LANG, "en")
                        .appendQueryParameter(API_KEY_PARAM, api_key);

                Uri builtUri = ub.build();
                URL url = new URL(builtUri.toString());
                Log.v(LOG_TAG, "Fetch: "+builtUri.toString());
                // Create the request to TMDB, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                movieJsonStr = buffer.toString();

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                return getMovieDetailFromJson(movieJsonStr);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null; // This will only happen if there was an error getting or parsing
        }

        @Override
        protected void onPostExecute(Movie movie) {
//            if(isAdded() && movie != null)
                setMovieOverviewView(movie.getOverview());
        }
    }
}
