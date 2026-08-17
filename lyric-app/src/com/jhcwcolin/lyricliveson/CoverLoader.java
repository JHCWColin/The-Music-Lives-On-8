package com.jhcwcolin.lyricliveson;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Reads embedded audio metadata/cover art and caches the cover to app storage. */
public final class CoverLoader {

    public static class Meta {
        public String title;
        public String artist;
        public String album;
        public long durationMs;
        public String coverPath;
    }

    public static Meta readMeta(Context c, Uri uri, String fallbackTitle) {
        Meta m = new Meta();
        m.title = fallbackTitle;
        m.artist = "";
        m.album = "";
        m.durationMs = 0;
        m.coverPath = null;

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(c, uri);
            String t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (t != null && t.length() > 0) m.title = t;
            String a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (a != null) m.artist = a;
            String al = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (al != null) m.album = al;
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) {
                try { m.durationMs = Long.parseLong(d); } catch (NumberFormatException e) {}
            }
            byte[] art = mmr.getEmbeddedPicture();
            if (art != null && art.length > 0) {
                m.coverPath = saveCover(c, uri, art);
            }
        } catch (Exception e) {
            // keep defaults
        } finally {
            try { mmr.release(); } catch (Exception e) {}
        }
        return m;
    }

    private static String saveCover(Context c, Uri uri, byte[] art) {
        try {
            File dir = new File(c.getFilesDir(), "covers");
            if (!dir.exists()) dir.mkdirs();
            String name = "cover_" + Math.abs(uri.toString().hashCode()) + ".jpg";
            File f = new File(dir, name);
            if (!f.exists()) {
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(art);
                fos.close();
            }
            return f.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    public static Bitmap decodeSampled(String path, int reqSize) {
        if (path == null) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int w = o.outWidth;
            int h = o.outHeight;
            int sample = 1;
            while (w / 2 >= reqSize && h / 2 >= reqSize) {
                sample *= 2;
                w /= 2;
                h /= 2;
            }
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, o);
        } catch (Exception e) {
            return null;
        }
    }

    /** Decode cover bytes served through a provider fd (used in shared-library mode). */
    public static Bitmap decodeSampledFd(ParcelFileDescriptor pfd, int reqSize) {
        if (pfd == null) return null;
        InputStream is = null;
        try {
            is = new FileInputStream(pfd.getFileDescriptor());
            byte[] data = readAll(is);
            if (data == null || data.length == 0) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int w = o.outWidth;
            int h = o.outHeight;
            int sample = 1;
            while (w / 2 >= reqSize && h / 2 >= reqSize) {
                sample *= 2;
                w /= 2;
                h /= 2;
            }
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) { try { is.close(); } catch (Exception e) {} }
        }
    }

    private static byte[] readAll(InputStream is) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
