package com.example.xyzreader.ui;

import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.transition.Explode;
import android.support.transition.Transition;
import android.support.transition.TransitionManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.xyzreader.BuildConfig;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = ArticleListActivity.class.toString();
    private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;
    private ImageView mNoElementsView;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);
    private Adapter listAdapter;
    private Context mContext;

    // CHANGE: variable provided for checking before populating article list that the info has
    // started to be refreshed or it's not necessary to refresh info
    private boolean mCanInfoBeDraw = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);

        mContext = getApplicationContext();

        mToolbar = (Toolbar) findViewById(R.id.toolbar);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);

        mNoElementsView = (ImageView) findViewById(R.id.no_elements_in_list);

        // Change: avoid to create again the loader if not necessary
        if (getLoaderManager().getLoader(0) == null) {
            getLoaderManager().initLoader(0, null, this);
        } else {
            getLoaderManager().restartLoader(0, null, this);
        }

        /* Change: Swipe refresh layout funcionality implemented, making the elements of the
        list to explode before loading them again.
         */
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Transition explode = new Explode();
                explode.setDuration(getResources().getInteger(R.integer.anim_duration_long));
                explode.addListener(new Transition.TransitionListener() {
                    @Override
                    public void onTransitionStart(@NonNull Transition transition) {}

                    @Override
                    public void onTransitionEnd(@NonNull Transition transition) {
                        refresh();
                    }

                    @Override
                    public void onTransitionCancel(@NonNull Transition transition) {
                        refresh();
                    }

                    @Override
                    public void onTransitionPause(@NonNull Transition transition) {}

                    @Override
                    public void onTransitionResume(@NonNull Transition transition) {}
                });
                TransitionManager.beginDelayedTransition(mRecyclerView, explode);
                mRecyclerView.setAdapter(null);
            }
        });

        if (savedInstanceState == null) {
            refresh();
            mCanInfoBeDraw = false;
        } else {
            mCanInfoBeDraw = true;
        }
    }

    private void refresh() {
        mRecyclerView.setAdapter(null);
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getLoaderManager().destroyLoader(0);
    }

    private boolean mIsRefreshing = true;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                if (mIsRefreshing)
                    mCanInfoBeDraw = true;
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);

    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!mCanInfoBeDraw) return;
        if (cursor.getCount() > 0) {
            mNoElementsView.setVisibility(View.GONE);
        } else {
            mNoElementsView.setVisibility(View.VISIBLE);
        }
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);
        // CHANGE: populate list using an animation (if lollipor or above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final LayoutAnimationController controller =
                    AnimationUtils.loadLayoutAnimation(mContext, R.anim.layout_animation_from_bottom);
            mRecyclerView.setLayoutAnimation(controller);
        }
        listAdapter = new Adapter(cursor);
        listAdapter.setHasStableIds(true);
        mRecyclerView.setAdapter(listAdapter);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // CHANGE: use directly the activity for creating the intent, it's more direct
                    Intent intent  = new Intent(getApplicationContext(), ArticleDetailActivity.class);
                    intent.setData(ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition())));
                    startActivity(intent);
                }
            });
            return vh;
        }

        private Date parsePublishedDate() {
            try {
                String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                return dateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                return new Date();
            }
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            if (mCursor == null) return;
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {

                holder.subtitleView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + "<br/>" + " by "
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            } else {
                holder.subtitleView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate)
                        + "<br/>" + " by "
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            }
            // CHANGE: Use glide for loading image, we can use the cache of images
            Glide.with(ArticleListActivity.this)
                    .asBitmap()
                    .load(mCursor.getString(ArticleLoader.Query.THUMB_URL))
                    .listener(new RequestListener<Bitmap>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                            // Change, set most dominant dark colors of the image as background color of the card of the item
                            if (resource != null) {
                                Palette palette = Palette.generate(resource, 12);
                                Palette.Swatch swatch = palette.getDarkVibrantSwatch();
                                if (swatch == null) {
                                    swatch = palette.getDarkMutedSwatch();
                                }
                                int backgroundColor = ContextCompat.getColor(getApplicationContext(),
                                        R.color.primaryColor);
                                if(swatch != null){
                                    backgroundColor = swatch.getRgb();
                                }
                                holder.layout.setBackgroundColor(backgroundColor);
                            }
                            return false;
                        }
                    })
                    .into(holder.thumbnailView);
            // Change: setting the aspect ratio using ConstraintLayout attributes
            ConstraintSet set = new ConstraintSet();
            set.clone(holder.layout);
            set.setDimensionRatio(holder.thumbnailView.getId(), "" +
                    mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO) + ":1");
            set.applyTo(holder.layout);
        }

        @Override
        public int getItemCount() {
            if (mCursor != null) return mCursor.getCount();
            else return 0;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;
        public ConstraintLayout layout;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (ImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
            layout = (ConstraintLayout) view.findViewById(R.id.card_layout);
        }
    }
}
