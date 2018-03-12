package io.digitallibrary.reader.catalog;

import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import org.threeten.bp.OffsetDateTime;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import io.digitallibrary.reader.Gdl;
import io.digitallibrary.reader.utilities.LanguageUtil;
import io.digitallibrary.reader.utilities.UIThread;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Parser for the GDL OPDS feed.
 */
public class OpdsParser {
    private static final String TAG = "OpdsParser";

    // This sets the default language
    public static final String INITIAL_LANGUAGE_TEXT = "English";
    public static final String INITIAL_LANGUAGE = "https://opds.staging.digitallibrary.io/eng/root.xml";
    private static final String INITIAL_REQUEST_URL = INITIAL_LANGUAGE;

    // TAGS - acquisition root
    private static final String ID = "id";
    private static final String TITLE = "title";
    private static final String UPDATED = "updated";
    private static final String LINK = "link";
    private static final String ENTRY = "entry";

    // Links
    private static final String LINK_ATTR_HREF = "href";
    private static final String LINK_ATTR_TITLE = "title";
    private static final String LINK_ATTR_REL = "rel";
    // Paging
    private static final String LINK_ATTR_REL_VALUE_NEXT = "next";

    // Facet links
    private static final String LINK_ATTR_REL_VALUE_FACET = "http://opds-spec.org/facet";
    private static final String LINK_ATTR_FACET_GROUP = "opds:facetGroup";
    private static final String LINK_ATTR_FACET_GROUP_VALUE_SELECTION = "Selection";
    private static final String LINK_ATTR_FACET_GROUP_VALUE_LANGUAGE = "Languages";
    private static final String LINK_ATTR_FACET_IS_ACTIVE = "opds:activeFacet";
    private static final String LINK_ATTR_FACET_IS_ACTIVE_VALUE_TRUE = "true";
    private static final String LINK_ATTR_FACET_IS_ACTIVE_VALUE_FALSE = "false";

    // Entries
    // id, title, updated, and root same as in acquisition root
    private static final String AUTHOR = "author";
    private static final String AUTHOR_NAME = "name";
    private static final String LICENSE = "dc:license";
    private static final String PUBLISHER = "dc:publisher";
    private static final String CREATED = "dc:created";
    private static final String PUBLISHED = "published";
    private static final String LEVEL = "lrmi:educationalAlignment";
    private static final String LEVEL_ATTR_TYPE = "alignmentType";
    private static final String LEVEL_ATTR_TYPE_LEVEL = "readingLevel";
    private static final String LEVEL_ATTR_TARGET = "targetName";
    private static final String SUMMARY = "summary";

    // Entry links
    private static final String LINK_ATTR_REL_VALUE_IMAGE = "http://opds-spec.org/image";
    private static final String LINK_ATTR_REL_VALUE_THUMB = "http://opds-spec.org/image/thumbnail";
    private static final String LINK_ATTR_REL_VALUE_ACQU_OPEN = "http://opds-spec.org/acquisition/open-access";
    private static final String LINK_ATTR_TYPE = "type";
    private static final String LINK_ATTR_TYPE_VALUE_JPEG = "image/jpeg";
    private static final String LINK_ATTR_TYPE_VALUE_PNG = "image/png";
    private static final String LINK_ATTR_TYPE_VALUE_EPUB = "application/epub+zip";
    private static final String LINK_ATTR_TYPE_VALUE_PDF = "application/pdf";

    private ProgressMonitor progressMonitor = new ProgressMonitor();

    /**
     * Class to keep track of all tasks to update selections and books.
     * Multiple languages can be updated at the same time.
     */
    private static class ProgressMonitor {
        private final ReentrantLock lock = new ReentrantLock();
        private Map<String, LanguageTasksMonitor> tasks = new HashMap<>(10);

        public LanguageTasksMonitor getLanguageTask(String languageLink) {
            LanguageTasksMonitor task = tasks.get(languageLink);
            if (task == null) {
                task = new LanguageTasksMonitor(languageLink);
                tasks.put(languageLink, task);
            }
            return task;
        }

        /**
         * Class to keep track of all tasks that needs to run to update selections and books
         * for one language. Synchronises all parsing threads.
         *
         * Also contains some data needed by all tasks.
         */
        private class LanguageTasksMonitor {
            private List<Callback> callbacks = new ArrayList<>(10);
            private Set<AsyncTask> subTasks = new HashSet<>(10);
            private XmlPullParserFactory xmlPullParserFactory;
            private boolean haveError = false;
            private long version = -1;
            private String languageLink;
            private OffsetDateTime updated;

            LanguageTasksMonitor(String languageLink) {
                this.languageLink = languageLink;
            }

            public void failed(final Error error, final String message) {
                lock.lock();
                haveError = true;
                List<Callback> cs = callbacks;
                callbacks = new ArrayList<>(10);
                lock.unlock();

                for (final Callback c : cs) {
                    // All callbacks should be called on the UI thread
                    UIThread.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            c.onError(error, message);
                        }
                    });
                }

                lock.lock();
                try {
                    tasks.remove(languageLink);
                } finally {
                    lock.unlock();
                }
            }

            public void addTask(AsyncTask task) {
                lock.lock();
                try {
                    subTasks.add(task);
                } finally {
                    lock.unlock();
                }
            }

            public void removeTask(AsyncTask task) {
                lock.lock();
                try {
                    subTasks.remove(task);
                    if (subTasks.size() == 0) {
                        runPostUpdateJob();
                    }
                } finally {
                    lock.unlock();
                }
            }

            public boolean isRunning() {
                lock.lock();
                try {
                    return subTasks.size() > 0;
                } finally {
                    lock.unlock();
                }
            }

            // Called from onPostExecute from the PostUpdateTask and runs on the UI thread
            public void finish() {
                lock.lock();
                List<Callback> cs = callbacks;
                callbacks = new ArrayList<>(10);
                lock.unlock();

                for (Callback c : cs) {
                    c.onFinished();
                }

                lock.lock();
                try {
                    tasks.remove(languageLink);
                } finally {
                    lock.unlock();
                }
            }

            private void runPostUpdateJob() {
                PostUpdateTask pu = new PostUpdateTask(this);
                pu.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }

            void ensureHaveStarted(Callback callback) {
                lock.lock();
                try {
                    if (callback != null) {
                        callbacks.add(callback);
                    }
                    if (!isRunning()) {
                        ParseFacetsTask updateJob = new ParseFacetsTask(this);
                        addTask(updateJob);
                        updateJob.execute(languageLink);

                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * Task that parsers the facets part of the root of a OPDS feed for one specific language.
     * This will update the database with the full language list, and the selections for the supplied
     * language. This task runs alone, but will start multiple ParseSelectionPageTask in parallel
     * for each selection.
     */
    private static class ParseFacetsTask extends AsyncTask<String, Void, Void> {
        private ProgressMonitor.LanguageTasksMonitor taskMonitor;
        private String currentLanguage = null;
        private List<Language> languages = new ArrayList<>(20);
        private List<Selection> selections = new ArrayList<>(10);
        private OffsetDateTime oldUpdated = null;

        ParseFacetsTask(ProgressMonitor.LanguageTasksMonitor taskMonitor) {
            this.taskMonitor = taskMonitor;
        }

        @Override
        protected Void doInBackground(String... langLinks) {
            try {
                OkHttpClient client = Gdl.Companion.getHttpClient();
                LanguageDao langDao = Gdl.Companion.getDatabase().languageDao();
                SelectionDao selectionDao = Gdl.Companion.getDatabase().selectionDao();
                String languageLink = INITIAL_LANGUAGE;

                String url = INITIAL_REQUEST_URL;

                if (langLinks.length > 0) {
                    String langRoot = langLinks[0];
                    if (langRoot != null) {
                        url = langRoot;
                        languageLink = langRoot;
                    }
                }

                // We do not care about the books before we parse each selection
                url += "?page-size=0";

                Language oldLang = langDao.getLanguage(languageLink);
                if (oldLang != null) {
                    oldUpdated = oldLang.getUpdated();
                }

                Request request = new Request.Builder().url(url).build();
                Response response;

                Log.v(TAG, "ParseFacetsTask calling url: " + url);

                try {
                    response = client.newCall(request).execute();
                } catch (IOException e) {
                    Log.e(TAG, "HTTP I/O error for url: " + url);
                    e.printStackTrace();
                    taskMonitor.failed(Error.HTTP_IO_ERROR, e.getMessage());
                    return null;
                }

                if (!response.isSuccessful()) {
                    // Response not in [200..300)
                    Log.e(TAG, "HTTP request (" + url + ") failed with response code " + response.code());
                    taskMonitor.failed(Error.HTTP_REQUEST_FAILED, null);
                    return null;
                }

                try {
                    // Save the XmlPullParserFactory in the taskMonitor, so we don't recreate it later.
                    taskMonitor.xmlPullParserFactory = XmlPullParserFactory.newInstance();
                    XmlPullParser xpp = taskMonitor.xmlPullParserFactory.newPullParser();
                    xpp.setInput(response.body().charStream());

                    int langCounter = 0;
                    int selectionCounter = 0;

                    // Parse facets
                    int eventType = xpp.getEventType();
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && xpp.getName().equals(LINK)) {
                            String rel = xpp.getAttributeValue(null, LINK_ATTR_REL);
                            if (rel != null && rel.equals(LINK_ATTR_REL_VALUE_FACET)) {
                                String title = xpp.getAttributeValue(null, LINK_ATTR_TITLE);
                                String groupValue = xpp.getAttributeValue(null, LINK_ATTR_FACET_GROUP);
                                String activeValue = xpp.getAttributeValue(null, LINK_ATTR_FACET_IS_ACTIVE);
                                String href = xpp.getAttributeValue(null, LINK_ATTR_HREF);

                                if (!(title == null || groupValue == null || activeValue == null || href == null)) {
                                    if (groupValue.equals(LINK_ATTR_FACET_GROUP_VALUE_LANGUAGE)) {
                                        Language l = new Language();
                                        l.setLanguageName(title);
                                        l.setLink(href);
                                        l.setViewOrder(++langCounter);
                                        if (activeValue.equals(LINK_ATTR_FACET_IS_ACTIVE_VALUE_TRUE)) {
                                            currentLanguage = href;
                                        }
                                        languages.add(l);
                                    } else if (groupValue.equals(LINK_ATTR_FACET_GROUP_VALUE_SELECTION)) {
                                        Selection c = new Selection();
                                        // Languages should appear before selection
                                        c.setLanguageLink(currentLanguage);
                                        c.setRootLink(href);
                                        c.setTitle(title);
                                        c.setViewOrder(++selectionCounter);
                                        selections.add(c);
                                    }
                                }
                            }
                        } else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals(UPDATED)) {
                            while (!(eventType == XmlPullParser.END_TAG && xpp.getName().equals(UPDATED))) {
                                if (eventType == XmlPullParser.TEXT) {
                                    String value = xpp.getText();
                                    taskMonitor.updated = TimeTypeConverters.toOffsetDateTime(value);
                                }
                                eventType = xpp.next();
                            }
                        } else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals(ENTRY)) {
                            // We are past the facets, so stop parsing
                            break;
                        }
                        eventType = xpp.next();
                    }

                    List<Language> oldLanguagesList = langDao.getLanguages();
                    Map<String, Language> oldLanguages = new HashMap<>(oldLanguagesList.size());
                    for (Language l : oldLanguagesList) {
                        oldLanguages.put(l.getLink(), l);
                    }
                    List<Language> newLanguages = new ArrayList<>(languages.size());
                    List<Language> updatedLanguages = new ArrayList<>(languages.size());

                    for (Language l : languages) {
                        Language old = oldLanguages.get(l.getLink());

                        if (old != null) {
                            // Need to copy over the updated field, as it's not from the OPDS feed
                            l.setUpdated(old.getUpdated());
                            if (!l.equals(old)) {
                                updatedLanguages.add(l);
                            }
                            oldLanguages.remove(l.getLink());
                        } else {
                            newLanguages.add(l);
                        }
                    }

                    if (!(newLanguages.isEmpty() && oldLanguages.isEmpty() && updatedLanguages.isEmpty())) {
                        langDao.updateLanguages(newLanguages, new ArrayList<>(oldLanguages.values()), updatedLanguages);
                    }

                    List<Selection> oldSelectionList = selectionDao.getSelections(languageLink);
                    Map<String, Selection> oldSelections = new HashMap<>(oldSelectionList.size());
                    for (Selection s : oldSelectionList) {
                        oldSelections.put(s.getRootLink(), s);
                    }
                    List<Selection> newSelections = new ArrayList<>(selections.size());
                    List<Selection> updatedSelections = new ArrayList<>(selections.size());

                    for (Selection s : selections) {
                        Selection old = oldSelections.get(s.getRootLink());

                        if (old != null) {
                            if (!s.equals(old)) {
                                updatedSelections.add(s);
                            }
                            oldSelections.remove(s.getRootLink());
                        } else {
                            newSelections.add(s);
                        }
                    }

                    if (!(newSelections.isEmpty() && oldSelections.isEmpty() && updatedSelections.isEmpty())) {
                        selectionDao.updateSelections(newSelections, new ArrayList<>(oldSelections.values()), updatedSelections);
                    }

                    if (taskMonitor.updated != null && taskMonitor.updated.equals(oldUpdated)) {
                        // We are done - we are not setting taskMonitor.version, so
                        // we can check for that in PostUpdateTask
                        Log.v(TAG, "No need to update");
                        return null;
                     }

                    taskMonitor.version = Gdl.Companion.getDatabase().bookDao().maxVersion(languageLink) + 1;

                    for (Selection s : selections) {
                        ParseSelectionPageTask parseJob = new ParseSelectionPageTask(taskMonitor);
                        taskMonitor.addTask(parseJob);
                        ParseSelectionPageData selectionData = new ParseSelectionPageData(s, 1, null);
                        // Want these to run in parallel
                        parseJob.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, selectionData);
                    }

                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    taskMonitor.failed(Error.XML_PARSING_ERROR, null);
                    return null;
                } finally {
                    response.close();
                }
                return null;
            } finally {
                taskMonitor.removeTask(this);
            }
        }
    }

    /**
     * Data needed by the ParseSelectionPageTask
     */
    private static class ParseSelectionPageData {
        Selection selection;
        int startViewOrder;
        String url;

        ParseSelectionPageData(Selection selection, int startViewOrder, String url) {
            this.selection = selection;
            this.startViewOrder = startViewOrder;
            this.url = url;
        }
    }

    /**
     * Task to parse one page of a OPDS feed. Will continue to create Tasks for next links as
     * long as it can find one.
     */
    private static class ParseSelectionPageTask extends AsyncTask<ParseSelectionPageData, Void, Void> {
        private ProgressMonitor.LanguageTasksMonitor taskMonitor;
        private List<Book> books = new ArrayList<>(20);
        private List<BookSelectionMap> bookSelectionMaps = new ArrayList<>(20);
        private String next = null;

        ParseSelectionPageTask(ProgressMonitor.LanguageTasksMonitor taskMonitor) {
            this.taskMonitor = taskMonitor;
        }

        @Override
        protected Void doInBackground(ParseSelectionPageData... parseSelectionPageDatas) {
            try {
                OkHttpClient client = Gdl.Companion.getHttpClient();
                Response response;

                if (parseSelectionPageDatas.length == 0) {
                    throw new IllegalArgumentException("Missing ParseSelectionPageData");
                }

                ParseSelectionPageData parseSelectionPageData = parseSelectionPageDatas[0];
                Selection selection = parseSelectionPageData.selection;
                int viewOrderCounter = parseSelectionPageData.startViewOrder;

                String url;
                if (parseSelectionPageData.url != null) {
                    url = parseSelectionPageData.url;
                } else {
                    url = selection.getRootLink();
                }

                Log.v(TAG, "ParseSelectionPageTask calling url: " + url);

                Request request = new Request.Builder().url(url).build();

                try {
                    response = client.newCall(request).execute();
                } catch (IOException e) {
                    Log.e(TAG, "HTTP IO error for url: " + url);
                    e.printStackTrace();
                    taskMonitor.failed(Error.HTTP_IO_ERROR, e.getMessage());
                    return null;
                }

                if (!response.isSuccessful()) {
                    // Response not in [200..300)
                    Log.e(TAG, "HTTP request (" + url + ") failed with response code " + response.code());
                    taskMonitor.failed(Error.HTTP_REQUEST_FAILED, null);
                    return null;
                }

                try {
                    XmlPullParser xpp = taskMonitor.xmlPullParserFactory.newPullParser();
                    xpp.setInput(response.body().charStream());

                    // Parse facets
                    int eventType = xpp.getEventType();
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && xpp.getName().equals(ENTRY)) {
                            // Skip past entry start tag
                            xpp.next();

                            Book b = new Book();
                            b.setLanguageLink(selection.getLanguageLink());
                            b.setVersion(taskMonitor.version);

                            while (!(eventType == XmlPullParser.END_TAG && xpp.getName().equals(ENTRY))) {
                                if (taskMonitor.haveError) {
                                    return null;
                                }
                                if (eventType == XmlPullParser.START_TAG) {
                                    String tagName = xpp.getName();
                                    switch (tagName) {
                                        case ID:
                                        case TITLE:
                                        case SUMMARY:
                                        case LICENSE:
                                        case PUBLISHER:
                                            while (!(eventType == XmlPullParser.END_TAG && xpp.getName().equals(tagName))) {
                                                if (eventType == XmlPullParser.TEXT) {
                                                    String value = xpp.getText();
                                                    switch (tagName) {
                                                        case ID:
                                                            b.setId(value);
                                                            break;
                                                        case TITLE:
                                                            b.setTitle(value);
                                                            break;
                                                        case SUMMARY:
                                                            b.setDescription(value);
                                                            break;
                                                        case LICENSE:
                                                            b.setLicense(value);
                                                            break;
                                                        case PUBLISHER:
                                                            b.setPublisher(value);
                                                            break;
                                                    }
                                                }
                                                eventType = xpp.next();
                                            }
                                            break;
                                        case UPDATED:
                                        case CREATED:
                                        case PUBLISHED:
                                            while (!(eventType == XmlPullParser.END_TAG && xpp.getName().equals(tagName))) {
                                                if (eventType == XmlPullParser.TEXT) {
                                                    String value = xpp.getText();
                                                    OffsetDateTime time = TimeTypeConverters.toOffsetDateTime(value);
                                                    switch (tagName) {
                                                        case UPDATED:
                                                            b.setUpdated(time);
                                                            break;
                                                        case CREATED:
                                                            b.setCreated(time);
                                                            break;
                                                        case PUBLISHED:
                                                            b.setPublished(time);
                                                            break;
                                                    }
                                                }
                                                eventType = xpp.next();
                                            }
                                            break;
                                        case AUTHOR:
                                            StringBuilder author = null;
                                            while (!(eventType == XmlPullParser.END_TAG && xpp.getName().equals(tagName))) {
                                                if (eventType == XmlPullParser.START_TAG && xpp.getName().equals(AUTHOR_NAME)) {
                                                    while (!(eventType == XmlPullParser.END_TAG && xpp.getName().equals(AUTHOR_NAME))) {
                                                        if (eventType == XmlPullParser.TEXT) {
                                                            if (author != null) {
                                                                author.append(", ").append(xpp.getText());
                                                            } else {
                                                                author = new StringBuilder(xpp.getText());
                                                            }
                                                        }
                                                        eventType = xpp.next();
                                                    }
                                                }
                                                eventType = xpp.next();
                                            }
                                            if (author != null) {
                                                b.setAuthor(author.toString());
                                            }
                                            break;
                                        case LEVEL:
                                            String levelType = xpp.getAttributeValue(null, LEVEL_ATTR_TYPE);
                                            if (levelType != null && levelType.equals(LEVEL_ATTR_TYPE_LEVEL)) {
                                                String level = xpp.getAttributeValue(null, LEVEL_ATTR_TARGET);
                                                if (level != null) {
                                                    b.setReadingLevel(Integer.parseInt(level));
                                                }
                                            }
                                            break;
                                        case LINK:
                                            String rel = xpp.getAttributeValue(null, LINK_ATTR_REL);
                                            String link = xpp.getAttributeValue(null, LINK_ATTR_HREF);
                                            if (rel != null && link != null) {
                                                switch (rel) {
                                                    case LINK_ATTR_REL_VALUE_IMAGE:
                                                        b.setImage(link);
                                                        break;
                                                    case LINK_ATTR_REL_VALUE_THUMB:
                                                        b.setThumb(link);
                                                        break;
                                                    case LINK_ATTR_REL_VALUE_ACQU_OPEN:
                                                        String type = xpp.getAttributeValue(null, LINK_ATTR_TYPE);
                                                        if (type != null) {
                                                            switch (type) {
                                                                case LINK_ATTR_TYPE_VALUE_EPUB:
                                                                    b.setEPubLink(link);
                                                                    break;
                                                                case LINK_ATTR_TYPE_VALUE_PDF:
                                                                    b.setPdfLink(link);
                                                                    break;
                                                            }
                                                        }
                                                        break;
                                                }
                                            }
                                            break;
                                    }
                                }
                                eventType = xpp.next();
                            }

                            books.add(b);

                            BookSelectionMap bsm = new BookSelectionMap();
                            bsm.setBookId(b.getId());
                            bsm.setLanguageLink(selection.getLanguageLink());
                            bsm.setSelectionLink(selection.getRootLink());
                            bsm.setViewOrder(viewOrderCounter++);
                            bsm.setVersion(taskMonitor.version);
                            bookSelectionMaps.add(bsm);

                        } else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals(LINK)) {
                            String rel = xpp.getAttributeValue(null, LINK_ATTR_REL);
                            if (rel != null && rel.equals(LINK_ATTR_REL_VALUE_NEXT)) {
                                String href = xpp.getAttributeValue(null, LINK_ATTR_HREF);
                                if (href != null) {
                                    next = href;
                                }
                            }
                        }
                        eventType = xpp.next();
                    }

                    Gdl.Companion.getDatabase().runInTransaction(new Runnable() {
                        @Override
                        public void run() {
                            Gdl.Companion.getDatabase().bookDao().insertOrUpdate(books);
                            Gdl.Companion.getDatabase().bookSelectionMapDao().insertOrUpdate(bookSelectionMaps);
                        }
                    });

                    // Should only get default page site of root (New arrivals)
                    if (next != null && !next.contains("root.xml") && !taskMonitor.haveError) {
                        ParseSelectionPageData usd = new ParseSelectionPageData(selection, viewOrderCounter, next);
                        ParseSelectionPageTask newJob = new ParseSelectionPageTask(taskMonitor);
                        taskMonitor.addTask(newJob);
                        newJob.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, usd);
                    }
                } catch (XmlPullParserException | IOException e) {
                    Log.e(TAG, "Parsing " + url + " failed");
                    e.printStackTrace();
                    taskMonitor.failed(Error.XML_PARSING_ERROR, null);
                    return null;
                } finally {
                    response.close();
                }
                return null;
            } finally {
                taskMonitor.removeTask(this);
            }
        }
    }

    /**
     * Task that runs when all OPDS feeds have been parsed.
     *
     * Cleans up and calls callbacks.
     */
    private static class PostUpdateTask extends AsyncTask<Void, Void, Void> {
        private ProgressMonitor.LanguageTasksMonitor taskMonitor;

        PostUpdateTask(ProgressMonitor.LanguageTasksMonitor taskMonitor) {
            this.taskMonitor = taskMonitor;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Log.v(TAG, "PostUpdateTask for " + taskMonitor.languageLink + " running");
            // If taskMonitor.version isn't set, we haven't updated the books
            if (taskMonitor.version != -1) {
                // Delete old not downloaded books
                BookDao bookDao = Gdl.Companion.getDatabase().bookDao();
                bookDao.deleteOldNotDownloaded(taskMonitor.languageLink, taskMonitor.version);
                List<Book> oldDownloadedBooks = bookDao.getOldDownloaded(taskMonitor.languageLink, taskMonitor.version);
                for (Book b : oldDownloadedBooks) {
                    b.setState(Catalog_dbKt.BOOK_STATE_REMOVED_FROM_GDL);
                    bookDao.update(b);
                }
                Gdl.Companion.getDatabase().bookSelectionMapDao().deleteOld(taskMonitor.languageLink, taskMonitor.version);

                if (taskMonitor.updated != null) {
                    LanguageDao langDao = Gdl.Companion.getDatabase().languageDao();
                    Language lang = langDao.getLanguage(taskMonitor.languageLink);
                    lang.setUpdated(taskMonitor.updated);
                    langDao.update(lang);
                }

                Gdl.Companion.getDatabase().bookSelectionMapDao().deleteOld(taskMonitor.languageLink, taskMonitor.version);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            // onPostExecute is called from the UI thread
            taskMonitor.finish();
        }
    }

    /**
     * Error types returned by the {@link Callback} given to {@link #start}.
     */
    public enum Error {
        HTTP_IO_ERROR,
        HTTP_REQUEST_FAILED,
        XML_PARSING_ERROR,
    }

    /**
     * Callback called by {@link #start} when parsing current language is done.
     */
    public interface Callback {
        /**
         * Called when finished to parse current language without errors.
         */
        void onFinished();

        /**
         * Called when parsing current language failed.
         * @param error Type of {@link Error}
         * @param message Might be a message describing the error, or null.
         */
        void onError(Error error, @Nullable String message);
    }

    /**
     * Fetch, parse, and save the OPDS feed for the currently selected language.
     *
     * If a job to parse current language is already started, this will not start a
     * new one, but it will still call the callback when the already running job is
     * finished.
     *
     * @param callback Callback that will be called after currently selected
     *                 language job is finished successfully, or have failed.
     *                 Will be called on the UI thread.
     */
    public void start(Callback callback) {
        String langLink = LanguageUtil.getCurrentLanguageLink();
        ProgressMonitor.LanguageTasksMonitor task = progressMonitor.getLanguageTask(langLink);
        task.ensureHaveStarted(callback);
    }
}