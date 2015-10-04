package com.ratanachai.popularmovies;

import android.os.Parcel;
import android.os.Parcelable;

import com.ratanachai.popularmovies.data.MovieContract;

public class Movie implements Parcelable{
    private String id;
    private String title;
    private String posterUrl;
    private String overview;
    private String userRating;
    private String releaseDate;

    // PROJECTION for Content Provider Query
    static final String[] MOVIE_COLUMNS = {
            MovieContract.MovieEntry.TABLE_NAME + "." + MovieContract.MovieEntry._ID,
            MovieContract.MovieEntry.COLUMN_TMDB_MOVIE_ID,
            MovieContract.MovieEntry.COLUMN_TITLE,
            MovieContract.MovieEntry.COLUMN_POSTER_PATH,
            MovieContract.MovieEntry.COLUMN_OVERVIEW,
            MovieContract.MovieEntry.COLUMN_USER_RATING,
            MovieContract.MovieEntry.COLUMN_RELEASE_DATE
    };
    static final int COL_MOVIE_ROW_ID = 0;
    static final int COL_TMDB_MOVIE_ID = 1;
    static final int COL_TITLE = 2;
    static final int COL_POSTER_PATH = 3;
    static final int COL_OVERVIEW = 4;
    static final int COL_USER_RATING = 5;
    static final int COL_RELEASE_DATE = 6;

    //Take Json string and create an instance
    public Movie(String id, String title, String poster, String overview, String userRating, String releaseDate){
        this.id = id;
        this.title = title;
        this.posterUrl = poster;
        this.overview = overview;
        this.userRating = userRating;
        this.releaseDate = releaseDate;
    }
    public String getPosterUrl(){
        return posterUrl;
    }
    public String[] getAll(){
        String[] all = {id, title, posterUrl, overview, userRating, releaseDate};
        return all;
    }

    /** Methods needed for implementing Parcelable
     * http://stackoverflow.com/questions/12503836/how-to-save-custom-arraylist-on-android-screen-rotate */

    private Movie(Parcel in){
        id = in.readString();
        title = in.readString();
        posterUrl = in.readString();
        overview = in.readString();
        userRating = in.readString();
        releaseDate = in.readString();
    }
    public int describeContents() {
        return 0;
    }
    public void writeToParcel(Parcel out, int flags){
        out.writeString(id);
        out.writeString(title);
        out.writeString(posterUrl);
        out.writeString(overview);
        out.writeString(userRating);
        out.writeString(releaseDate);
    }
    public static final Parcelable.Creator<Movie> CREATOR = new Parcelable.Creator<Movie>(){
        public Movie createFromParcel(Parcel in){
            return new Movie(in);
        }
        public Movie[] newArray(int size){
            return new Movie[size];
        }
    };

}
