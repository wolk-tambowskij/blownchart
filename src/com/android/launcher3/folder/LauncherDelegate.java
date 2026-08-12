/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.folder;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_CONVERTED_TO_ICON;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.Nullable;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Wrapper around Launcher methods to allow folders in non-launcher context
 */
public class LauncherDelegate {

    private static final String TAG = "LauncherDelegate";

    private final Launcher mLauncher;

    private LauncherDelegate(Launcher launcher) {
        mLauncher = launcher;
    }

    void init(Folder folder, FolderIcon icon) {
        folder.setDragController(mLauncher.getDragController());
        icon.setOnFocusChangeListener(mLauncher.getFocusHandler());
    }

    boolean isDraggingEnabled() {
        return mLauncher.isDraggingEnabled();
    }

    void beginDragShared(View child, DragSource source, DragOptions options) {
        mLauncher.getWorkspace().beginDragShared(child, source, options);
    }

    ModelWriter getModelWriter() {
        return mLauncher.getModelWriter();
    }

    void forEachVisibleWorkspacePage(Consumer<View> callback) {
        mLauncher.getWorkspace().forEachVisiblePage(callback);
    }

    @Nullable
    Launcher getLauncher() {
        return mLauncher;
    }

    boolean replaceFolderWithFinalItem(Folder folder) {
        Folder parentFolder = findParentFolder(folder.mFolderIcon);
        // Diagnostic: which Folder instance is being collapsed, whether it resolved to a nested
        // parent or the top-level path, and how many items each side currently has - added while
        // chasing reports of the *parent* folder disappearing along with its contents during a
        // nested-folder drag-out, to see the actual object identities/counts involved instead of
        // inferring them from a text description of the repro steps.
        Log.d(TAG, "replaceFolderWithFinalItem: folder=" + System.identityHashCode(folder)
                + " items=" + folder.getItemCount()
                + " parentFolder=" + (parentFolder == null
                        ? "null (top-level path)"
                        : System.identityHashCode(parentFolder) + " items=" + parentFolder.getItemCount()));
        if (parentFolder != null) {
            return replaceNestedFolderWithFinalItem(folder, parentFolder);
        }
        // Add the last remaining child to the workspace in place of the folder
        Runnable onCompleteRunnable = new Runnable() {
            @Override
            public void run() {
                int itemCount = folder.getItemCount();
                FolderInfo info = folder.mInfo;
                if (itemCount <= 1) {
                    View newIcon = null;
                    ItemInfo finalItem = null;

                    if (itemCount == 1) {
                        // Move the item from the folder to the workspace, in the position of the
                        // folder
                        CellLayout cellLayout = mLauncher.getCellLayout(info.container,
                                mLauncher.getCellPosMapper().mapModelToPresenter(info).screenId);
                        finalItem =  info.getContents().remove(0);
                        newIcon = mLauncher.getItemInflater().inflateItem(
                                finalItem, mLauncher.getModelWriter(), cellLayout);
                        mLauncher.getModelWriter().addOrMoveItemInDatabase(finalItem,
                                info.container, info.screenId, info.cellX, info.cellY);
                    }

                    // Remove the folder
                    mLauncher.removeItem(folder.mFolderIcon, info, true /* deleteFromDb */,
                            "folder removed because there's only 1 item in it");
                    if (folder.mFolderIcon instanceof DropTarget) {
                        folder.mDragController.removeDropTarget((DropTarget) folder.mFolderIcon);
                    }

                    if (newIcon != null) {
                        // We add the child after removing the folder to prevent both from existing
                        // at the same time in the CellLayout.  We need to add the new item with
                        // addInScreenFromBind() to ensure that hotseat items are placed correctly.
                        mLauncher.getWorkspace().addInScreenFromBind(newIcon, info);

                        // Focus the newly created child
                        newIcon.requestFocus();
                    }
                    if (finalItem != null) {
                        StatsLogger logger = mLauncher.getStatsLogManager().logger()
                                .withItemInfo(finalItem);
                        ((Optional<InstanceId>) folder.mDragController.getLogInstanceId())
                                .map(logger::withInstanceId)
                                .orElse(logger)
                                .log(LAUNCHER_FOLDER_CONVERTED_TO_ICON);
                    }
                }
            }
        };
        View finalChild = folder.mContent.getLastItem();
        if (finalChild != null) {
            folder.mFolderIcon.performDestroyAnimation(onCompleteRunnable);
        } else {
            onCompleteRunnable.run();
        }
        return true;
    }

    /**
     * Finds the {@link Folder} that owns {@param folderIconView} as one of its own content
     * items - i.e. whose {@link FolderPagedView} contains it - or null if it isn't currently
     * shown inside another folder (a top-level, workspace/hotseat folder icon's ancestor chain
     * never passes through a {@link FolderPagedView}, only {@link CellLayout}s that belong
     * directly to the workspace or hotseat).
     */
    @Nullable
    private static Folder findParentFolder(View folderIconView) {
        for (ViewParent p = folderIconView.getParent(); p != null; p = p.getParent()) {
            if (p instanceof FolderPagedView) {
                return ((FolderPagedView) p).getFolder();
            }
        }
        return null;
    }

    /**
     * Nested-folder counterpart of {@link #replaceFolderWithFinalItem}. A nested folder isn't
     * backed by a CellLayout position of its own - it's just an entry, at some rank, in its
     * parent folder's contents - so collapsing it down to its last item means editing that list
     * instead of the workspace grid. Reuses {@link FolderInfo#add}/{@link FolderInfo#remove},
     * the same listener-driven path {@link Folder#onAdd}/{@link Folder#onRemove} already use for
     * every other nested-folder content change, so the parent's database rows and (if currently
     * open) its bound view both stay in sync automatically instead of needing to be duplicated
     * here.
     */
    private boolean replaceNestedFolderWithFinalItem(Folder folder, Folder parentFolder) {
        Runnable onCompleteRunnable = () -> {
            FolderInfo info = folder.mInfo;
            FolderInfo parentInfo = parentFolder.mInfo;
            int rank = parentInfo.getContents().indexOf(info);

            ItemInfo finalItem = folder.getItemCount() == 1 ? info.getContents().remove(0) : null;

            Log.d(TAG, "replaceNestedFolderWithFinalItem: folder=" + System.identityHashCode(folder)
                    + " parentFolder=" + System.identityHashCode(parentFolder)
                    + " rank=" + rank + " finalItem=" + finalItem
                    + " parentInfo.contents.size()=" + parentInfo.getContents().size());

            if (finalItem != null) {
                // Add the replacement before removing the folder, so the parent's own item
                // count never dips to its post-collapse value early - if it did, and the
                // parent itself is down to only this folder plus one other item, its own
                // Folder#onRemove collapse check could fire between these two calls, before
                // the replacement item meant to keep it above that threshold is in place.
                parentInfo.add(finalItem, Math.max(rank, 0), true);
            }
            parentInfo.remove(info, true);
            folder.mFolderIcon.removeListeners();
            mLauncher.getModelWriter().deleteCollectionAndContentsFromDatabase(info);
        };

        View finalChild = folder.mContent.getLastItem();
        if (finalChild != null) {
            folder.mFolderIcon.performDestroyAnimation(onCompleteRunnable);
        } else {
            onCompleteRunnable.run();
        }
        return true;
    }

    boolean interceptOutsideTouch(MotionEvent ev, BaseDragLayer dl, Folder folder) {
        if (mLauncher.getAccessibilityDelegate().isInAccessibleDrag()) {
            // Do not close the container if in drag and drop.
            if (!dl.isEventOverView(mLauncher.getDropTargetBar(), ev)) {
                return true;
            }
        } else {
            // TODO: add ww log if need to gather tap outside to close folder
            folder.close(true);
            return true;
        }
        return false;
    }

    private static class FallbackDelegate extends LauncherDelegate {

        private final ActivityContext mContext;
        private ModelWriter mWriter;

        FallbackDelegate(ActivityContext context) {
            super(null);
            mContext = context;
        }

        @Override
        void init(Folder folder, FolderIcon icon) {
            folder.setDragController(mContext.getDragController());
        }

        @Override
        boolean isDraggingEnabled() {
            return false;
        }

        @Override
        void beginDragShared(View child, DragSource source, DragOptions options) { }

        @Override
        ModelWriter getModelWriter() {
            if (mWriter == null) {
                mWriter = LauncherAppState.getInstance((Context) mContext).getModel().getWriter(
                        false, mContext.getCellPosMapper(), null);
            }
            return mWriter;
        }

        @Override
        void forEachVisibleWorkspacePage(Consumer<View> callback) { }

        @Override
        Launcher getLauncher() {
            return null;
        }

        @Override
        boolean replaceFolderWithFinalItem(Folder folder) {
            return false;
        }

        @Override
        boolean interceptOutsideTouch(MotionEvent ev, BaseDragLayer dl, Folder folder) {
            folder.close(true);
            return true;
        }
    }

    static LauncherDelegate from(ActivityContext context) {
        return context instanceof Launcher
                ? new LauncherDelegate((Launcher) context)
                : new FallbackDelegate(context);
    }
}