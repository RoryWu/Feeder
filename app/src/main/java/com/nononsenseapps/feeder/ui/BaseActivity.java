package com.nononsenseapps.feeder.ui;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TypeEvaluator;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ExpandableListView;
import android.widget.TextView;

import com.nononsenseapps.feeder.R;
import com.nononsenseapps.feeder.model.RssNotifications;
import com.nononsenseapps.feeder.model.TaggedFeedsAdapter;
import com.nononsenseapps.feeder.util.LPreviewUtils;
import com.nononsenseapps.feeder.util.LPreviewUtilsBase;
import com.nononsenseapps.feeder.util.PrefUtils;
import com.nononsenseapps.feeder.views.ObservableScrollView;

import java.util.ArrayList;

/**
 * Base activity which handles navigation drawer and other bloat common
 * between activities.
 */
public class BaseActivity extends ActionBarActivity
        implements LoaderManager.LoaderCallbacks<Cursor>, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String SHOULD_FINISH_BACK = "SHOULD_FINISH_BACK";
    // Durations for certain animations we use:
    public static final int HEADER_HIDE_ANIM_DURATION = 300;
    // Special Navdrawer items
    protected static final int NAVDRAWER_ITEM_INVALID = -1;
    // delay to launch nav drawer item, to allow close animation to play
    private static final int NAVDRAWER_LAUNCH_DELAY = 250;
    // fade in and fade out durations for the main content when switching between
    // different Activities of the app through the Nav Drawer
    private static final int MAIN_CONTENT_FADEOUT_DURATION = 150;
    private static final int MAIN_CONTENT_FADEIN_DURATION = 250;
    private static final TypeEvaluator ARGB_EVALUATOR = new ArgbEvaluator();
    // Positive numbers reserved for children
    private static final int NAV_TAGS_LOADER = -2;
    protected boolean mActionBarShown = true;
    // If pressing home should finish or start new activity
    protected boolean mShouldFinishBack = false;
    protected Toolbar mActionBarToolbar;
    //protected MultiScrollListener mMultiScrollListener;
    private ObjectAnimator mStatusBarColorAnimator;
    // When set, these components will be shown/hidden in sync with the action bar
    // to implement the "quick recall" effect (the Action Bar and the header views disappear
    // when you scroll down a list, and reappear quickly when you scroll up).
    private ArrayList<View> mHideableHeaderViews = new ArrayList<View>();
    private ArrayList<View> mHideableFooterViews = new ArrayList<View>();
    // variables that control the Action Bar auto hide behavior (aka "quick recall")
    private boolean mActionBarAutoHideEnabled = false;
    private int mActionBarAutoHideSensivity = 0;
    private int mActionBarAutoHideMinY = 0;
    private int mActionBarAutoHideSignal = 0;
    private int mThemedStatusBarColor;
    private LPreviewUtilsBase mLPreviewUtils;
    private DrawerLayout mDrawerLayout;
    private LPreviewUtilsBase.ActionBarDrawerToggleWrapper mDrawerToggle;
    // A Runnable that we should execute when the navigation drawer finishes its closing animation
    private Runnable mDeferredOnDrawerClosedRunnable;
    private TaggedFeedsAdapter mNavAdapter;
    private ExpandableListView mDrawerListView;
    private boolean firstload = true;
    private TextView mUserText;
    private TextView mServerText;

    /**
     * Converts an intent into a {@link Bundle} suitable for use as fragment
     * arguments.
     */
    public static Bundle intentToFragmentArguments(Intent intent) {
        Bundle arguments = new Bundle();
        if (intent == null) {
            return arguments;
        }

        final Uri data = intent.getData();
        if (data != null) {
            arguments.putParcelable("_uri", data);
        }

        final Bundle extras = intent.getExtras();
        if (extras != null) {
            arguments.putAll(intent.getExtras());
        }

        return arguments;
    }

    /**
     * Converts a fragment arguments bundle into an intent.
     */
    public static Intent fragmentArgumentsToIntent(Bundle arguments) {
        Intent intent = new Intent();
        if (arguments == null) {
            return intent;
        }

        final Uri data = arguments.getParcelable("_uri");
        if (data != null) {
            intent.setData(data);
        }

        intent.putExtras(arguments);
        intent.removeExtra("_uri");
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent i = getIntent();
        if (i != null) {
            mShouldFinishBack = i.getBooleanExtra(SHOULD_FINISH_BACK, false);
        }

        mLPreviewUtils = LPreviewUtils.getInstance(this);
        mThemedStatusBarColor = getResources().getColor(R.color.primary_dark);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Send notifications for configured feeds
        RssNotifications.notify(this);
    }

    protected Toolbar getActionBarToolbar() {
        if (mActionBarToolbar == null) {
            mActionBarToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
            if (mActionBarToolbar != null) {
                setSupportActionBar(mActionBarToolbar);
            }
        }
        return mActionBarToolbar;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        setupNavDrawer();

        View mainContent = findViewById(R.id.main_content);
        if (mainContent != null) {
            mainContent.setAlpha(0);
            mainContent.animate().alpha(1)
                    .setDuration(MAIN_CONTENT_FADEIN_DURATION);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (mDrawerToggle != null &&
                mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        if (id == android.R.id.home && mShouldFinishBack) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAfterTransition();
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        PrefUtils.unregisterOnSharedPreferenceChangeListener(this, this);
        super.onDestroy();
    }

    private void setupNavDrawer() {
    // What nav drawer item should be selected?
        int selfItem = getSelfNavDrawerItem();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (mDrawerLayout == null) {
            return;
        }

        // Set up account fields
        mUserText = (TextView) findViewById(R.id.user_text);
        mServerText = (TextView) findViewById(R.id.server_text);

        final String user = PrefUtils.getUsername(this, null);
        final String server = PrefUtils.getServerUrl(this);

        if (user != null) {
            mUserText.setText(user);
        }
        if (server != null) {
            mServerText.setText(server);
        }
        // Listen for changes to account and server
        PrefUtils.registerOnSharedPreferenceChangeListener(this, this);

        // Listener for editor
        View accountBox = findViewById(R.id.account_box);
        accountBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open server editor
                DialogFragment dialog = new ServerEditorFragment();
                dialog.show(getSupportFragmentManager(), "server_dialog");
            }
        });

        if (selfItem == NAVDRAWER_ITEM_INVALID) {
            // do not show a nav drawer
            View navDrawer = mDrawerLayout.findViewById(R.id.navdrawer);
            if (navDrawer != null) {
                ((ViewGroup) navDrawer.getParent()).removeView(navDrawer);
            }
            mDrawerLayout = null;
            return;
        }

        mDrawerToggle = mLPreviewUtils.setupDrawerToggle(mDrawerLayout,
                new DrawerLayout.DrawerListener() {
                    @Override
                    public void onDrawerClosed(View drawerView) {
                        // run deferred action, if we have one
                        if (mDeferredOnDrawerClosedRunnable != null) {
                            mDeferredOnDrawerClosedRunnable.run();
                            mDeferredOnDrawerClosedRunnable = null;
                        }
                        invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                        updateStatusBarForNavDrawerSlide(0f);
                        onNavDrawerStateChanged(false, false);
                    }

                    @Override
                    public void onDrawerOpened(View drawerView) {
                        invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
                        updateStatusBarForNavDrawerSlide(1f);
                        onNavDrawerStateChanged(true, false);
                    }

                    @Override
                    public void onDrawerStateChanged(int newState) {
                        invalidateOptionsMenu();
                        onNavDrawerStateChanged(isNavDrawerOpen(),
                                newState != DrawerLayout.STATE_IDLE);
                    }

                    @Override
                    public void onDrawerSlide(View drawerView,
                                              float slideOffset) {
                        updateStatusBarForNavDrawerSlide(slideOffset);
                        onNavDrawerSlide(slideOffset);
                    }
                });
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, Gravity.START);

        mDrawerToggle.syncState();

        mNavAdapter = new TaggedFeedsAdapter(this);
        mDrawerListView = (ExpandableListView) mDrawerLayout
                .findViewById(R.id.navdrawer_list);
        //mDrawerListView.setLayoutManager(new LinearLayoutManager(this));
        mDrawerListView.setAdapter(mNavAdapter);
        mDrawerListView.setOnChildClickListener(
                new ExpandableListView.OnChildClickListener() {
                    @Override
                    public boolean onChildClick(final ExpandableListView parent,
                                                final View v, final int groupPosition,
                                                final int childPosition, final long id) {
                        if (mDrawerLayout != null) {
                            mDrawerLayout.closeDrawer(Gravity.START);
                        }

                        if (mNavAdapter != null) {
                            Cursor c = mNavAdapter
                                    .getChild(groupPosition, childPosition);
                            // Make sure these ints match ordering in projection if
                            // changed
                            onNavigationDrawerItemSelected(c.getLong(0),
                                    c.getString(1), c.getString(2),
                                    c.getString(3));
                        }
                        return true;
                    }
                });
        mDrawerListView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(final ExpandableListView parent,
                                        final View v, final int groupPosition, final long id) {
                if (mDrawerLayout != null) {
                    mDrawerLayout.closeDrawer(Gravity.START);
                }

                if (mNavAdapter != null) {
                    Cursor c = mNavAdapter
                            .getGroup(groupPosition);
                    // Make sure these ints match ordering in projection if
                    // changed
                    onNavigationDrawerItemSelected(-1,
                            c.getString(1), null,
                            c.getString(1));
                }
                return true;
            }
        });
        mNavAdapter.setOnExpandClickListener(new TaggedFeedsAdapter.OnExpandClickListener() {
            @Override
            public void onExpandClick(final int groupPosition,
                                      final boolean isExpanded) {
                if (isExpanded) {
                    mDrawerListView.collapseGroup(groupPosition);
                } else {
                    mDrawerListView.expandGroup(groupPosition);
                }
            }
        });

        populateNavDrawer();

        // When the user runs the app for the first time, we want to land them with the
        // navigation drawer open. But just the first time.
        if (!PrefUtils.isWelcomeDone(this)) {
            // first run of the app starts with the nav drawer open
            PrefUtils.markWelcomeDone(this);
            mDrawerLayout.openDrawer(Gravity.START);
        }
    }

    /**
     * Open the nav drawer
     */
    public void openNavDrawer() {
        mDrawerLayout.openDrawer(Gravity.START);
    }

    /**
     * Returns the navigation drawer item that corresponds to this Activity.
     * Subclasses
     * of BaseActivity override this to indicate what nav drawer item
     * corresponds to them
     * Return NAVDRAWER_ITEM_INVALID to mean that this Activity should not have
     * a Nav Drawer.
     */
    protected int getSelfNavDrawerItem() {
        return NAVDRAWER_ITEM_INVALID;
    }

    private void updateStatusBarForNavDrawerSlide(float slideOffset) {
        if (mStatusBarColorAnimator != null) {
            mStatusBarColorAnimator.cancel();
        }

        if (!mActionBarShown) {
            mLPreviewUtils.setStatusBarColor(Color.BLACK);
            return;
        }

        mLPreviewUtils.setStatusBarColor((Integer) ARGB_EVALUATOR
                .evaluate(slideOffset, mThemedStatusBarColor, Color.BLACK));
    }

    // Subclasses can override this for custom behavior
    protected void onNavDrawerStateChanged(boolean isOpen,
                                           boolean isAnimating) {
        if (mActionBarAutoHideEnabled && isOpen) {
            autoShowOrHideActionBar(true);
        }
    }

    protected boolean isNavDrawerOpen() {
        return mDrawerLayout != null &&
                mDrawerLayout.isDrawerOpen(Gravity.START);
    }

    protected void onNavDrawerSlide(float offset) {
    }

    // Subclasses can override to decide what happens on nav item selection
    protected void onNavigationDrawerItemSelected(long id, String title,
                                                  String url, String tag) {
        // TODO add default start activity with arguments
    }

    private void populateNavDrawer() {
        getLoaderManager().restartLoader(NAV_TAGS_LOADER, new Bundle(), this);
    }

    public void showActionBar() {
        autoShowOrHideActionBar(true);
    }

    protected void autoShowOrHideActionBar(boolean show) {
        if (show == mActionBarShown) {
            return;
        }

        mActionBarShown = show;
        onActionBarAutoShowOrHide(show);
    }

    protected void onActionBarAutoShowOrHide(boolean shown) {
        if (mStatusBarColorAnimator != null) {
            mStatusBarColorAnimator.cancel();
        }
        mStatusBarColorAnimator = ObjectAnimator
                .ofInt(mLPreviewUtils, "statusBarColor",
                        shown ? mThemedStatusBarColor : Color.BLACK)
                .setDuration(250);
        mStatusBarColorAnimator.setEvaluator(ARGB_EVALUATOR);
        mStatusBarColorAnimator.start();

        for (View view : mHideableHeaderViews) {
            if (shown) {
                view.animate().translationY(0).alpha(1)
                        .setDuration(HEADER_HIDE_ANIM_DURATION)
                        .setInterpolator(new DecelerateInterpolator());
            } else {
                view.animate().translationY(-view.getBottom()).alpha(0)
                        .setDuration(HEADER_HIDE_ANIM_DURATION)
                        .setInterpolator(new DecelerateInterpolator());
            }
        }
        for (View view : mHideableFooterViews) {
            if (shown) {
                view.animate().translationY(0).alpha(1)
                        .setDuration(HEADER_HIDE_ANIM_DURATION)
                        .setInterpolator(new DecelerateInterpolator());
            } else {
                view.animate().translationY(view.getHeight()).alpha(0)
                        .setDuration(HEADER_HIDE_ANIM_DURATION)
                        .setInterpolator(new DecelerateInterpolator());
            }
        }
    }

    protected void enableActionBarAutoHide(final RecyclerView listView) {
        initActionBarAutoHide();
        final LinearLayoutManager layoutManager =
                (LinearLayoutManager) listView.getLayoutManager();
        mActionBarAutoHideSignal = 0;
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            final static int ITEMS_THRESHOLD = 0;
            int lastFvi = 0;

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                boolean force = false;
                int firstVisibleItem =
                        layoutManager.findFirstVisibleItemPosition();
                if (recyclerView.getAdapter() != null) {
                    int lastPos = recyclerView.getAdapter().getItemCount() - 1;
                    if (layoutManager.findLastVisibleItemPosition() == lastPos) {
                        // Show when last item is visible
                       force = true;
                    }
                }
                onMainContentScrolled(firstVisibleItem <= ITEMS_THRESHOLD ?
                                0 :
                                Integer.MAX_VALUE,
                        lastFvi - firstVisibleItem > 0 ?
                                Integer.MIN_VALUE :
                                lastFvi == firstVisibleItem ? 0 : Integer.MAX_VALUE,
                        force);
                lastFvi = firstVisibleItem;
            }
        });
    }

    /**
     * Initializes the Action Bar auto-hide (aka Quick Recall) effect.
     */
    private void initActionBarAutoHide() {
        mActionBarAutoHideEnabled = true;
        mActionBarAutoHideMinY = getResources()
                .getDimensionPixelSize(R.dimen.action_bar_auto_hide_min_y);
        mActionBarAutoHideSensivity = getResources()
                .getDimensionPixelSize(R.dimen.action_bar_auto_hide_sensivity);
    }

    /**
     * Indicates that the main content has scrolled (for the purposes of
     * showing/hiding
     * the action bar for the "action bar auto hide" effect). currentY and
     * deltaY may be exact
     * (if the underlying view supports it) or may be approximate indications:
     * deltaY may be INT_MAX to mean "scrolled forward indeterminately" and
     * INT_MIN to mean
     * "scrolled backward indeterminately".  currentY may be 0 to mean
     * "somewhere close to the
     * start of the list" and INT_MAX to mean "we don't know, but not at the
     * start of the list"
     */
    private void onMainContentScrolled(int currentY, int deltaY, boolean force) {
        if (deltaY > mActionBarAutoHideSensivity) {
            deltaY = mActionBarAutoHideSensivity;
        } else if (deltaY < -mActionBarAutoHideSensivity) {
            deltaY = -mActionBarAutoHideSensivity;
        }

        if (Math.signum(deltaY) * Math.signum(mActionBarAutoHideSignal) < 0) {
            // deltaY is a motion opposite to the accumulated signal, so reset signal
            mActionBarAutoHideSignal = deltaY;
        } else {
            // add to accumulated signal
            mActionBarAutoHideSignal += deltaY;
        }

        boolean shouldShow = currentY < mActionBarAutoHideMinY ||
                (mActionBarAutoHideSignal <=
                        -mActionBarAutoHideSensivity);
        autoShowOrHideActionBar(shouldShow | force);
    }

    protected void enableActionBarAutoHide(
            final ObservableScrollView scrollView) {
        initActionBarAutoHide();
        mActionBarAutoHideSignal = 0;
        scrollView.addOnScrollChangedListener(
                new ObservableScrollView.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged(final int deltaX,
                                                final int deltaY) {
                        onMainContentScrolled(scrollView.getScrollY(), deltaY, false);
                    }
                });
    }

    protected void registerHideableHeaderView(View hideableHeaderView) {
        if (!mHideableHeaderViews.contains(hideableHeaderView)) {
            mHideableHeaderViews.add(hideableHeaderView);
        }
    }

    protected void deregisterHideableHeaderView(View hideableHeaderView) {
        if (mHideableHeaderViews.contains(hideableHeaderView)) {
            mHideableHeaderViews.remove(hideableHeaderView);
        }
    }

    protected void registerHideableFooterView(View hideableFooterView) {
        if (!mHideableFooterViews.contains(hideableFooterView)) {
            mHideableFooterViews.add(hideableFooterView);
        }
    }

    protected void deregisterHideableFooterView(View hideableFooterView) {
        if (mHideableFooterViews.contains(hideableFooterView)) {
            mHideableFooterViews.remove(hideableFooterView);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle bundle) {
        if (id == NAV_TAGS_LOADER) {
            return mNavAdapter.getGroupCursorLoader();
        } else {
            // Using id as group position
            return mNavAdapter.getChildCursorLoader(bundle.getString("tag"));
        }
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> cursorLoader,
                               final Cursor cursor) {
        if (cursorLoader.getId() == NAV_TAGS_LOADER) {
            mNavAdapter.setGroupCursor(cursor);
            // Load child cursors
            for (int i = 0; i < cursor.getCount(); i++) {
                //if (firstload) {
                    // Expand by default
                    //mDrawerListView.expandGroup(i);
                //}
                Cursor group = mNavAdapter.getGroup(i);
                Bundle b = new Bundle();
                // Make sure position is correct
                b.putString("tag", group.getString(1));
                getLoaderManager().restartLoader(i, b, this);
            }

        } else {
            // Child loader
            mNavAdapter.setChildrenCursor(cursorLoader.getId(), cursor);
        }
        // Put this last
        firstload = false;
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> cursorLoader) {
        if (cursorLoader.getId() == NAV_TAGS_LOADER) {
            mNavAdapter.setGroupCursor(null);
        } else {
            mNavAdapter.setChildrenCursor(cursorLoader.getId(), null);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (PrefUtils.PREF_USERNAME.equals(key)) {
            mUserText.setText(sp.getString(key, ""));
        } else if (PrefUtils.PREF_SERVER_URL.equals(key)) {
            mServerText.setText(sp.getString(key, ""));
        }
    }

    //    public class MultiScrollListener implements RecyclerView.OnScrollListener {
    //        private final Set<RecyclerView.OnScrollListener> children =
    //                new HashSet<RecyclerView.OnScrollListener>();
    //
    //        public void addScrollListener(RecyclerView.OnScrollListener child) {
    //            children.add(child);
    //        }
    //
    //        @Override
    //        public void onScrollStateChanged(final int i) {
    //            for (RecyclerView.OnScrollListener child : children) {
    //                child.onScrollStateChanged(i);
    //            }
    //        }
    //
    //        @Override
    //        public void onScrolled(final int dx, final int dy) {
    //            for (RecyclerView.OnScrollListener child : children) {
    //                child.onScrolled(dx, dy);
    //            }
    //        }
    //    }
/*
    private class FeedsAdapter extends RecyclerView
            .Adapter<FeedsAdapter.ViewHolder> {

        Cursor data = null;

        public void setData(Cursor c) {
            data = c;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(final ViewGroup parent,
                final int i) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(android.R.layout
                    .simple_list_item_1, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder viewHolder,
                final int position) {
            this.data.moveToPosition(position);
            viewHolder.bind(this.data.getString(1), this.data.getString(2));
        }

        @Override
        public int getItemCount() {
            if (this.data == null)
                return 0;
            else
                return this.data.getCount();
        }

        class ViewHolder extends RecyclerView.ViewHolder implements View
                .OnClickListener {

            public String title;
            public String url;
            private final TextView text;

            public ViewHolder(final View v) {
                super(v);
                v.setOnClickListener(this);
                text = (TextView) v.findViewById(android.R.id.text1);
            }

            public void bind(String title, String url) {
                this.title = title;
                this.url = url;
                text.setText(title);
            }

            @Override
            public void onClick(final View v) {
                // TODO
                //mCurrentSelectedPosition = getPosition();
                //        if (mDrawerListView != null) {
                //            mDrawerListView.setItemChecked(position, true);
                //        }
                if (mDrawerLayout != null) {
                    mDrawerLayout.closeDrawer(Gravity.START);
                }
                if (mNavAdapter != null) {
                    onNavigationDrawerItemSelected(title, url);
                }
            }
        }
    }*/
}
