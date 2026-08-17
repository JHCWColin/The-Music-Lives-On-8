package com.jhcwcolin.lyricliveson;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports music / lyrics / folders via the Storage Access Framework (SAF).
 * No storage permission needed; persisted URI grants make entries permanent.
 * There is deliberately no text input anywhere in this flow.
 */
public final class FileImporter {

    public interface Listener {
        void onDone();
    }

    public static void importFiles(Context c, Intent data, MusicDatabase db) {
        List<Uri> uris = new ArrayList<Uri>();
        if (data.getClipData() != null) {
            int n = data.getClipData().getItemCount();
            for (int i = 0; i < n; i++) {
                Uri u = data.getClipData().getItemAt(i).getUri();
                if (u != null) uris.add(u);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        for (Uri u : uris) {
            grantPersist(c, u);
            String name = queryName(c, u);
            if (name == null) name = u.getLastPathSegment();
            classifyAndImport(c, db, u, name, null);
        }
    }

    public static void importFolder(Context c, Intent data, MusicDatabase db) {
        Uri tree = data.getData();
        if (tree == null) return;
        grantPersist(c, tree);
        String docId = DocumentsContract.getTreeDocumentId(tree);
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        scanChildren(c, tree, children, db);
    }

    private static void scanChildren(Context c, Uri tree, Uri children, MusicDatabase db) {
        ContentResolver cr = c.getContentResolver();
        Cursor cur = null;
        try {
            cur = cr.query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            }, null, null, null);
        } catch (Exception e) {
            return;
        }
        if (cur == null) return;

        // First pass: gather entries and detect a folder cover image.
        List<Entry> entries = new ArrayList<Entry>();
        String folderCover = null;
        try {
            while (cur.moveToNext()) {
                Entry e = new Entry();
                e.docId = cur.getString(0);
                e.name = cur.getString(1);
                e.mime = cur.getString(2);
                e.uri = DocumentsContract.buildDocumentUriUsingTree(tree, e.docId);
                e.dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(e.mime);
                entries.add(e);
                if (!e.dir && folderCover == null && isCoverImage(e.name)) {
                    folderCover = e.uri.toString();
                }
            }
        } finally {
            cur.close();
        }

        for (Entry e : entries) {
            if (e.dir) {
                Uri sub = DocumentsContract.buildChildDocumentsUriUsingTree(tree, e.docId);
                scanChildren(c, tree, sub, db);
            } else {
                classifyAndImport(c, db, e.uri, e.name, folderCover);
            }
        }
    }

    private static class Entry {
        String docId;
        String name;
        String mime;
        Uri uri;
        boolean dir;
    }

    private static boolean isCoverImage(String name) {
        if (!Util.isImage(name)) return false;
        String b = Util.baseName(name).toLowerCase();
        return b.equals("cover") || b.equals("folder") || b.equals("album")
                || b.equals("front") || b.equals("artwork") || b.equals("albumart");
    }

    private static void classifyAndImport(Context c, MusicDatabase db, Uri uri, String name, String folderCover) {
        if (name == null) return;
        if (Util.isLyrics(name)) {
            importLrc(c, db, uri, name);
        } else if (Util.isAudio(name)) {
            importTrack(c, db, uri, name, folderCover);
        }
        // anything else is ignored
    }

    private static void importTrack(Context c, MusicDatabase db, Uri uri, String name, String folderCoverUri) {
        if (db.hasTrackUri(uri.toString())) return;
        String fallback = Util.baseName(name);
        if (fallback.length() == 0) fallback = "未知曲目";
        CoverLoader.Meta m = CoverLoader.readMeta(c, uri, fallback);
        Track t = new Track();
        t.uri = uri.toString();
        t.title = m.title != null && m.title.length() > 0 ? m.title : fallback;
        t.artist = m.artist != null ? m.artist : "";
        t.album = m.album != null ? m.album : "";
        t.durationMs = m.durationMs;
        t.coverPath = m.coverPath;
        t.matchKey = Util.matchKey(name);
        db.addTrack(t);
    }

    private static void importLrc(Context c, MusicDatabase db, Uri uri, String name) {
        String content = readText(c, uri);
        if (content == null) return;
        db.addLrc(uri.toString(), Util.matchKey(name), content);
    }

    private static String readText(Context c, Uri uri) {
        byte[] data = readBytes(c, uri);
        if (data == null) return null;
        String s = decode(data, "UTF-8");
        if (s != null && s.indexOf('�') >= 0) {
            // LRC files are frequently GBK/GB18030 encoded.
            String g = decode(data, "GB18030");
            if (g != null) return g;
        }
        return s;
    }

    private static byte[] readBytes(Context c, Uri uri) {
        InputStream is = null;
        try {
            is = c.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            if (is != null) { try { is.close(); } catch (Exception e) {} }
        }
    }

    private static String decode(byte[] data, String cs) {
        try {
            return new String(data, cs);
        } catch (Exception e) {
            return null;
        }
    }

    private static void grantPersist(Context c, Uri uri) {
        try {
            c.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            // provider may not support persistence; entry still usable this session
        }
    }

    private static String queryName(Context c, Uri uri) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null);
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception e) {
            // ignore
        } finally {
            if (cur != null) cur.close();
        }
        return null;
    }
}
