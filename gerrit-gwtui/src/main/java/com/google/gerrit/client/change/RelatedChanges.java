// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeApi;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.CommitInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.changes.ChangeList;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.common.changes.ListChangesOption;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.TabBar;
import com.google.gwt.user.client.ui.TabBar.Tab;
import com.google.gwt.user.client.ui.TabPanel;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

class RelatedChanges extends TabPanel {
  private static final int RELATED_CHANGES = 0;

  private List<RelatedChangesTab> tabs;
  private RelatedChangesTab conflictingChangesTab;
  private int maxHeight;
  private int selectedTab;

  RelatedChanges() {
    tabs = new ArrayList<RelatedChangesTab>();
    addStyleName(Gerrit.RESOURCES.css().relatedChangesTabPanel());
  }

  private RelatedChangesTab createTab(String title, String tooltip) {
    FlowPanel panel = new FlowPanel();
    add(panel, title);
    selectedTab = -1;
    TabBar tabBar = getTabBar();

    tabBar.addSelectionHandler(new SelectionHandler<Integer>() {
      @Override
      public void onSelection(SelectionEvent<Integer> event) {
        if (selectedTab >= 0) {
          tabs.get(selectedTab).registerKeys(false);
        }
        selectedTab = event.getSelectedItem();
        tabs.get(selectedTab).registerKeys(true);
      }
    });

    int index = tabBar.getTabCount() - 1;
    Tab tab = tabBar.getTab(index);
    tab.setWordWrap(false);
    ((Composite) tab).setTitle(tooltip);
    RelatedChangesTab relatedChangesTab =
        new RelatedChangesTab(this, index, panel);
    tabs.add(relatedChangesTab);
    relatedChangesTab.setMaxHeight(maxHeight);
    return relatedChangesTab;
  }

  void setTabTitle(int index, String title) {
    getTabBar().setTabText(index, title);
  }

  void set(final ChangeInfo info, final String revision) {
    if (info.status().isClosed()) {
      setVisible(false);
      return;
    }

    createTab(Resources.C.relatedChanges(),
        Resources.C.relatedChangesTooltip());
    selectTab(RELATED_CHANGES);

    ChangeApi.revision(info.legacy_id().get(), revision).view("related")
        .get(new AsyncCallback<RelatedInfo>() {
          @Override
          public void onSuccess(RelatedInfo result) {
            RelatedChangesTab tab = tabs.get(RELATED_CHANGES);
            tab.setTitle(Resources.M.relatedChanges(result.changes().length()));
            tab.setChanges(info.project(), revision, result.changes());
          }

          @Override
          public void onFailure(Throwable err) {
            RelatedChangesTab tab = tabs.get(RELATED_CHANGES);
            tab.setTitle(Resources.M.relatedChanges(Resources.C.notAvailable()));
            tab.setError(err.getMessage());
          }
        });

    StringBuilder conflictsQuery = new StringBuilder();
    conflictsQuery.append("status:open");
    conflictsQuery.append(" conflicts:").append(info.legacy_id().get());
    ChangeList.query(conflictsQuery.toString(),
        EnumSet.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT),
        new AsyncCallback<ChangeList>() {
          @Override
          public void onSuccess(ChangeList result) {
            if (result.length() > 0) {
              getTab().setTitle(Resources.M.conflictingChanges(result.length()));
              getTab().setChanges(info.project(), revision,
                  convertChangeList(result));
            }
          }

          @Override
          public void onFailure(Throwable err) {
            getTab().setTitle(
                Resources.M.conflictingChanges(Resources.C.notAvailable()));
            getTab().setError(err.getMessage());
          }

          private RelatedChangesTab getTab() {
            if (conflictingChangesTab == null) {
              conflictingChangesTab =
                  createTab(Resources.C.conflictingChanges(),
                      Resources.C.conflictingChangesTooltip());
            }
            return conflictingChangesTab;
          }
        });
  }

  void setMaxHeight(int height) {
    this.maxHeight = height;
    for (int i = 0; i < getTabBar().getTabCount(); i++) {
      tabs.get(i).setMaxHeight(height);
    }
  }

  private JsArray<ChangeAndCommit> convertChangeList(ChangeList l) {
    JsArray<ChangeAndCommit> arr = JavaScriptObject.createArray().cast();
    for (ChangeInfo i : Natives.asList(l)) {
      RevisionInfo currentRevision = i.revision(i.current_revision());
      ChangeAndCommit c = ChangeAndCommit.create();
      c.set_id(i.id());
      c.set_commit(currentRevision.commit());
      c.set_change_number(i.legacy_id().get());
      c.set_revision_number(currentRevision._number());
      arr.push(c);
    }
    return arr;
  }

  private static class RelatedInfo extends JavaScriptObject {
    final native JsArray<ChangeAndCommit> changes() /*-{ return this.changes }-*/;
    protected RelatedInfo() {
    }
  }

  static class ChangeAndCommit extends JavaScriptObject {
    static ChangeAndCommit create() {
      return (ChangeAndCommit) createObject();
    }

    final native String id() /*-{ return this.change_id }-*/;
    final native CommitInfo commit() /*-{ return this.commit }-*/;

    final native void set_id(String i)
    /*-{ if(i)this.change_id=i; }-*/;

    final native void set_commit(CommitInfo c)
    /*-{ if(c)this.commit=c; }-*/;

    final Change.Id legacy_id() {
      return has_change_number() ? new Change.Id(_change_number()) : null;
    }

    final PatchSet.Id patch_set_id() {
      return has_change_number() && has_revision_number()
          ? new PatchSet.Id(legacy_id(), _revision_number())
          : null;
    }

    final native boolean has_change_number()
    /*-{ return this.hasOwnProperty('_change_number') }-*/;

    final native boolean has_revision_number()
    /*-{ return this.hasOwnProperty('_revision_number') }-*/;

    final native int _change_number()
    /*-{ return this._change_number }-*/;

    final native int _revision_number()
    /*-{ return this._revision_number }-*/;

    final native void set_change_number(int n)
    /*-{ this._change_number=n; }-*/;

    final native void set_revision_number(int n)
    /*-{ this._revision_number=n; }-*/;

    protected ChangeAndCommit() {
    }
  }
}
