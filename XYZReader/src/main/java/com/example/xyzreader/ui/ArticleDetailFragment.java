package com.example.xyzreader.ui;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;

/**
 * A fragment representing a single Article detail screen. This fragment is
 * either contained in a {@link ArticleListActivity} in two-pane mode (on
 * tablets) or a {@link ArticleDetailActivity} on handsets.
 */
public class ArticleDetailFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ArticleDetailFragment";

    public static final String ARG_ITEM_ID = "item_id";

    private Cursor mCursor;
    private long mItemId;
    private View mRootView;
    private RecyclerView mScrollViewText;

    public ImageView mPhotoView;
    Toolbar mToolbar;
    private CollapsingToolbarLayout mCollapsingToolbar;

    String[] mTextToShowParagraphList;

    private SimpleDateFormat dateFormat = null;
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    private String title;
    private String author;
    private Integer backgroundTitleColor;
    private Context mContext;
    private TextView mTitleView;
    private TextView mBylineView;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ArticleDetailFragment() {
    }

    public static ArticleDetailFragment newInstance(long itemId) {
        Bundle arguments = new Bundle();
        arguments.putLong(ARG_ITEM_ID, itemId);
        ArticleDetailFragment fragment = new ArticleDetailFragment();
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getActivity().getApplicationContext();

        dateFormat = new SimpleDateFormat(getString(R.string.date_format));

        if (getArguments().containsKey(ARG_ITEM_ID)) {
            mItemId = getArguments().getLong(ARG_ITEM_ID);
        }

        setHasOptionsMenu(true);

        if (backgroundTitleColor == null) {
            backgroundTitleColor = ContextCompat.getColor(
                    mContext,
                    R.color.primaryColor);
        }
    }

    public ArticleDetailActivity getActivityCast() {
        return (ArticleDetailActivity) getActivity();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // In support library r8, calling initLoader for a fragment in a FragmentPagerAdapter in
        // the fragment's onCreate may cause the same LoaderManager to be dealt to multiple
        // fragments because their mIndex is -1 (haven't been added to the activity yet). Thus,
        // we do this in onActivityCreated.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        mRootView = inflater.inflate(R.layout.fragment_article_detail, container, false);

        mScrollViewText = (RecyclerView) mRootView.findViewById(R.id.body_text);

        mPhotoView = (ImageView) mRootView.findViewById(R.id.photo);

        // CHANGE: Using toolbar, and defining navigation listener there
        mCollapsingToolbar = (CollapsingToolbarLayout) mRootView.findViewById(R.id.details_collapsing_toolbar);
        mToolbar = (Toolbar) mRootView.findViewById(R.id.fragment_details_toolbar);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().onBackPressed();
            }
        });

        mRootView.findViewById(R.id.share_fab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // CHANGE: Sharing message customized for every article. If article not loaded, use
                // a snackbar for reporting it
                if ((title == null) || (author == null)) {
                    Snackbar snackbar = Snackbar.make(view, getString(R.string.article_not_loaded), Snackbar.LENGTH_LONG);
                    View sbView = snackbar.getView();
                    sbView.setBackgroundColor(backgroundTitleColor);
                    TextView textView = (TextView) sbView.findViewById(android.support.design.R.id.snackbar_text);
                    textView.setTextColor(ContextCompat.getColor(
                            mContext,
                            R.color.secondaryTextColor));
                    snackbar.show();
                } else {
                    startActivity(Intent.createChooser(ShareCompat.IntentBuilder.from(getActivity())
                            .setType("text/plain")
                            .setText(getString(R.string.article_share_phrase, title, author))
                            .getIntent(), getString(R.string.action_share)));
                }
            }
        });

        bindViews();
        return mRootView;
    }

    @Override
    public void onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPhotoView.setTransitionName(null);
        }
        super.onStop();
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

    private void bindViews() {
        if (mRootView == null) {
            return;
        }

        mTitleView = (TextView) mRootView.findViewById(R.id.article_title);
        mBylineView = (TextView) mRootView.findViewById(R.id.article_byline);
        mBylineView.setMovementMethod(new LinkMovementMethod());

        if (mCursor != null) {
            title = mCursor.getString(ArticleLoader.Query.TITLE);
            author = mCursor.getString(ArticleLoader.Query.AUTHOR);
            mRootView.setAlpha(0);
            mRootView.setVisibility(View.VISIBLE);
            mRootView.animate().alpha(1)
                    .setDuration(getResources().getInteger(R.integer.anim_duration_long)).start();
            mTitleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {
                mBylineView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + " by "
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)));

            } else {
                // If date is before 1902, just show the string
                mBylineView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate) + " by "
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)));

            }
            // We divide the article test into an array of paragraphs, in order to use a recycler view
            // for loading the text (much better performance in UI thread)
            mTextToShowParagraphList = mCursor.getString(ArticleLoader.Query.BODY).split("(\r\n|\n)");
            LinearLayoutManager layoutManager = new LinearLayoutManager(mContext);
            mScrollViewText.setLayoutManager(layoutManager);
            BodyTextAdapter mParagraphsAdapter = new BodyTextAdapter(mTextToShowParagraphList);
            mScrollViewText.setAdapter(mParagraphsAdapter);
            // CHANGE: Use Glide for loading cached images faster
            Glide.with(getActivity())
                    .asBitmap()
                    .load(mCursor.getString(ArticleLoader.Query.PHOTO_URL))
                    .listener(new RequestListener<Bitmap>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                            // Change: use dark colors of the photo as background color of the
                            // toolbar
                            if (resource != null) {
                                Palette palette = Palette.generate(resource, 12);
                                Palette.Swatch swatch = palette.getDarkVibrantSwatch();
                                if (swatch == null) {
                                    swatch = palette.getDarkMutedSwatch();
                                }
                                backgroundTitleColor = ContextCompat.getColor(
                                        mContext,
                                        R.color.primaryColor);
                                if(swatch != null){
                                    backgroundTitleColor = swatch.getRgb();
                                }
                                mCollapsingToolbar.setContentScrimColor(backgroundTitleColor);
                            }
                            return false;
                        }
                    })
                    .into(mPhotoView);
        } else {
            title = null;
            author = null;
            mRootView.setVisibility(View.GONE);
            mTitleView.setText("N/A");
            mBylineView.setText("N/A" );
            mScrollViewText.setAdapter(null);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newInstanceForItemId(getActivity(), mItemId);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (!isAdded()) {
            if (cursor != null) {
                cursor.close();
            }
            return;
        }

        mCursor = cursor;
        if (mCursor != null && !mCursor.moveToFirst()) {
            Log.e(TAG, "Error reading item detail cursor");
            mCursor.close();
            mCursor = null;
        }

        bindViews();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        bindViews();
    }

    //CHANGE: Adapter to be used for populating the recycler view with the text paragraphs.
    private class BodyTextAdapter extends RecyclerView.Adapter<ViewHolder> {
        private String[] mParagraphs;

        public BodyTextAdapter(String[] paragraphs) {
            mParagraphs = paragraphs;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getActivity().getLayoutInflater().inflate(R.layout.item_paragraph_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            return vh;
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            if ((mParagraphs != null) && (position >= 0) && (position < mParagraphs.length)) {
                holder.textView.setText(Html.fromHtml(mParagraphs[position]));
                // Change: set the textview to be clickable depending on links, in order to open them
                holder.textView.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }

        @Override
        public int getItemCount() {
            if (mParagraphs != null) return mParagraphs.length;
            else return 0;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView textView;

        public ViewHolder(View view) {
            super(view);
            textView = (TextView) view.findViewById(R.id.article_paragraph);
        }
    }

}
