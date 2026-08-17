package com.jhcwcolin.lyricliveson;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

/** List adapter for the music library. */
public class TrackAdapter extends BaseAdapter {
    private final Context ctx;
    private final List<Track> tracks;
    private final LayoutInflater inflater;

    public TrackAdapter(Context ctx, List<Track> tracks) {
        this.ctx = ctx;
        this.tracks = tracks;
        this.inflater = LayoutInflater.from(ctx);
    }

    @Override public int getCount() {
        return tracks.size();
    }

    @Override public Object getItem(int i) {
        return tracks.get(i);
    }

    @Override public long getItemId(int i) {
        return tracks.get(i).id;
    }

    @Override public View getView(int i, View convert, ViewGroup parent) {
        View v = convert;
        if (v == null) {
            v = inflater.inflate(R.layout.item_track, parent, false);
        }
        Track t = tracks.get(i);
        TextView title = (TextView) v.findViewById(R.id.track_title);
        TextView artist = (TextView) v.findViewById(R.id.track_artist);
        TextView dur = (TextView) v.findViewById(R.id.track_duration);
        title.setText(t.title);
        artist.setText(t.artistLabel());
        dur.setText(t.durationLabel());
        return v;
    }
}
