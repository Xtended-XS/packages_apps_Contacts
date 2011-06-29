/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.contacts.list;

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

/**
 * A cursor adapter for the {@link Phone#CONTENT_TYPE} content type.
 */
public class PhoneNumberListAdapter extends ContactEntryListAdapter {

    protected static final String[] PHONES_PROJECTION = new String[] {
        Phone._ID,                          // 0
        Phone.TYPE,                         // 1
        Phone.LABEL,                        // 2
        Phone.NUMBER,                       // 3
        Phone.DISPLAY_NAME_PRIMARY,         // 4
        Phone.DISPLAY_NAME_ALTERNATIVE,     // 5
        Phone.CONTACT_ID,                   // 6
        Phone.PHOTO_ID,                     // 7
        Phone.PHONETIC_NAME,                // 8
    };

    protected static final int PHONE_ID_COLUMN_INDEX = 0;
    protected static final int PHONE_TYPE_COLUMN_INDEX = 1;
    protected static final int PHONE_LABEL_COLUMN_INDEX = 2;
    protected static final int PHONE_NUMBER_COLUMN_INDEX = 3;
    protected static final int PHONE_PRIMARY_DISPLAY_NAME_COLUMN_INDEX = 4;
    protected static final int PHONE_ALTERNATIVE_DISPLAY_NAME_COLUMN_INDEX = 5;
    protected static final int PHONE_CONTACT_ID_COLUMN_INDEX = 6;
    protected static final int PHONE_PHOTO_ID_COLUMN_INDEX = 7;
    protected static final int PHONE_PHONETIC_NAME_COLUMN_INDEX = 8;

    private CharSequence mUnknownNameText;
    private int mDisplayNameColumnIndex;
    private int mAlternativeDisplayNameColumnIndex;
    private boolean mHighlightSearchPrefix;

    public PhoneNumberListAdapter(Context context) {
        super(context);

        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    protected CharSequence getUnknownNameText() {
        return mUnknownNameText;
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        Uri uri;

        if (isSearchMode()) {
            String query = getQueryString();
            Builder builder = Phone.CONTENT_FILTER_URI.buildUpon();
            if (TextUtils.isEmpty(query)) {
                builder.appendPath("");
            } else {
                builder.appendPath(query);      // Builder will encode the query
            }

            builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                    String.valueOf(directoryId));
            applyDataRestriction(builder);
            uri = builder.build();
            // TODO a projection that includes the search snippet
            loader.setProjection(PHONES_PROJECTION);
        } else {
            uri = Phone.CONTENT_URI.buildUpon().appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                    .build();
            if (isSectionHeaderDisplayEnabled()) {
                uri = buildSectionIndexerUri(uri);
            }
            uri = applyDataRestriction(uri);

            loader.setProjection(PHONES_PROJECTION);
        }

        loader.setUri(uri);

        // TODO: we probably want to use default sort order in search mode.
        if (getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            loader.setSortOrder(Phone.SORT_KEY_PRIMARY);
        } else {
            loader.setSortOrder(Phone.SORT_KEY_ALTERNATIVE);
        }
    }

    protected static Uri buildSectionIndexerUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true").build();
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor)getItem(position)).getString(mDisplayNameColumnIndex);
    }

    @Override
    public void setContactNameDisplayOrder(int displayOrder) {
        super.setContactNameDisplayOrder(displayOrder);
        if (getContactNameDisplayOrder() == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            mDisplayNameColumnIndex = PHONE_PRIMARY_DISPLAY_NAME_COLUMN_INDEX;
            mAlternativeDisplayNameColumnIndex = PHONE_ALTERNATIVE_DISPLAY_NAME_COLUMN_INDEX;
        } else {
            mDisplayNameColumnIndex = PHONE_ALTERNATIVE_DISPLAY_NAME_COLUMN_INDEX;
            mAlternativeDisplayNameColumnIndex = PHONE_PRIMARY_DISPLAY_NAME_COLUMN_INDEX;
        }
    }

    /**
     * Builds a {@link Data#CONTENT_URI} for the given cursor position.
     */
    public Uri getDataUri(int position) {
        Cursor cursor = ((Cursor)getItem(position));
        long id = cursor.getLong(PHONE_ID_COLUMN_INDEX);
        return ContentUris.withAppendedId(Data.CONTENT_URI, id);
    }

    @Override
    protected View newView(Context context, int partition, Cursor cursor, int position,
            ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(mUnknownNameText);
        view.setTextWithHighlightingFactory(getTextWithHighlightingFactory());
        view.setQuickContactEnabled(isQuickContactEnabled());
        return view;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        ContactListItemView view = (ContactListItemView)itemView;

        view.setHighlightedPrefix(mHighlightSearchPrefix && isSearchMode() ?
                getUpperCaseQueryString() : null);

        // Look at elements before and after this position, checking if contact IDs are same.
        // If they have one same contact ID, it means they can be grouped.
        //
        // In one group, only the first entry will show its photo and names (display name and
        // phonetic name), and the other entries in the group show just their data (e.g. phone
        // number, email address).
        cursor.moveToPosition(position);
        boolean isFirstEntry = true;
        boolean showBottomDivider = true;
        final long currentContactId = cursor.getLong(PHONE_CONTACT_ID_COLUMN_INDEX);
        if (cursor.moveToPrevious() && !cursor.isBeforeFirst()) {
            final long previousContactId = cursor.getLong(PHONE_CONTACT_ID_COLUMN_INDEX);
            if (currentContactId == previousContactId) {
                isFirstEntry = false;
            }
        }
        cursor.moveToPosition(position);
        if (cursor.moveToNext() && !cursor.isAfterLast()) {
            final long nextContactId = cursor.getLong(PHONE_CONTACT_ID_COLUMN_INDEX);
            if (currentContactId == nextContactId) {
                // The following entry should be in the same group, which means we don't want a
                // divider between them.
                // TODO: we want a different divider than the divider between groups. Just hiding
                // this divider won't be enough.
                showBottomDivider = false;
            }
        }
        cursor.moveToPosition(position);

        bindSectionHeaderAndDivider(view, position);
        if (isFirstEntry) {
            bindName(view, cursor);
            bindPhoto(view, cursor);
        } else {
            unbindName(view);
            unbindPhoto(view);
        }
        bindPhoneNumber(view, cursor);
        view.setDividerVisible(showBottomDivider);
    }

    protected void bindPhoneNumber(ContactListItemView view, Cursor cursor) {
        CharSequence label = null;
        if (!cursor.isNull(PHONE_TYPE_COLUMN_INDEX)) {
            final int type = cursor.getInt(PHONE_TYPE_COLUMN_INDEX);
            final String customLabel = cursor.getString(PHONE_LABEL_COLUMN_INDEX);

            // TODO cache
            label = Phone.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        view.showData(cursor, PHONE_NUMBER_COLUMN_INDEX);
    }

    protected void bindSectionHeaderAndDivider(final ContactListItemView view, int position) {
        if (isSectionHeaderDisplayEnabled()) {
            Placement placement = getItemPlacementInSection(position);
            view.setSectionHeader(placement.firstInSection ? placement.sectionHeader : null);
            view.setDividerVisible(!placement.lastInSection);
        } else {
            view.setSectionHeader(null);
            view.setDividerVisible(true);
        }
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, mDisplayNameColumnIndex, mAlternativeDisplayNameColumnIndex,
                isNameHighlightingEnabled(), getContactNameDisplayOrder());
        view.showPhoneticName(cursor, PHONE_PHONETIC_NAME_COLUMN_INDEX);
    }

    protected void unbindName(final ContactListItemView view) {
        view.hideDisplayName();
        view.hidePhoneticName();
    }

    protected void bindPhoto(final ContactListItemView view, Cursor cursor) {
        long photoId = 0;
        if (!cursor.isNull(PHONE_PHOTO_ID_COLUMN_INDEX)) {
            photoId = cursor.getLong(PHONE_PHOTO_ID_COLUMN_INDEX);
        }

        getPhotoLoader().loadPhoto(view.getPhotoView(), photoId);
    }

    protected void unbindPhoto(final ContactListItemView view) {
        view.removePhotoView(true, false);
    }

    public void setHighlightSearchPrefix(boolean highlight) {
        mHighlightSearchPrefix = highlight;
    }
}
