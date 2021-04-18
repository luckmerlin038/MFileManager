package com.luckmerlin.file.adapter;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.appcompat.view.menu.MenuView;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableField;
import androidx.databinding.ViewDataBinding;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.luckmerlin.adapter.OnSectionLoadFinish;
import com.luckmerlin.adapter.recycleview.ItemSlideRemover;
import com.luckmerlin.adapter.recycleview.ItemTouchInterrupt;
import com.luckmerlin.adapter.recycleview.OnItemTouchResolver;
import com.luckmerlin.adapter.recycleview.SectionListAdapter;
import com.luckmerlin.adapter.recycleview.SectionRequest;
import com.luckmerlin.adapter.recycleview.ViewHolder;
import com.luckmerlin.core.Canceler;
import com.luckmerlin.core.debug.Debug;
import com.luckmerlin.databinding.touch.OnViewClick;
import com.luckmerlin.file.Cancel;
import com.luckmerlin.file.Client;
import com.luckmerlin.file.FileDefaultThumb;
import com.luckmerlin.file.Folder;
import com.luckmerlin.file.LocalPath;
import com.luckmerlin.file.OnPathUpdate;
import com.luckmerlin.file.Path;
import com.luckmerlin.file.Query;
import com.luckmerlin.file.R;
import com.luckmerlin.file.Thumb;
import com.luckmerlin.file.api.OnApiFinish;
import com.luckmerlin.file.api.Reply;
import com.luckmerlin.file.api.What;
import com.luckmerlin.file.databinding.ItemBrowserEmptyBinding;
import com.luckmerlin.file.databinding.ItemContentEmptyBinding;
import com.luckmerlin.file.databinding.ItemListFileBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileBrowserAdapter extends SectionListAdapter<Query, Path> implements OnItemTouchResolver, OnViewClick {
    private final ObservableField<Client> mCurrentClient=new ObservableField<Client>();
    private final FileDefaultThumb mDefaultThumb=new FileDefaultThumb();
    private int mLoadWhat;
    private OnSyncApiFinish mLoading=null;

    public FileBrowserAdapter(){
        super(false,true);
    }

    @Override
    protected void onResolveFixedViewItem(RecyclerView recyclerView) {
        Context context=null!=recyclerView?recyclerView.getContext():null;
        if (null!=context){
            setFixHolder(TYPE_EMPTY,generateViewHolder(context,R.layout.item_browser_empty));
        }
    }

    public final boolean replace(Path from,Path to,String debug){
        if (null!=from&&null!=to){
            int index=super.index(from);
            return index>=0&&super.replace(index,to,debug);
        }
        return false;
    }

    @Override
    protected void onAttachRecyclerView(RecyclerView recyclerView) {
        super.onAttachRecyclerView(recyclerView);
        ViewParent parent=null!=recyclerView?recyclerView.getParent():null;
        if (null!=parent&&parent instanceof SwipeRefreshLayout){
            SwipeRefreshLayout layout=(SwipeRefreshLayout)parent;
            layout.setOnRefreshListener(()-> {
                resetSection(getLatestSectionArg(),true,null,"While refresh drag.");
            });
        }
    }

    @Override
    protected void onDetachRecyclerView(RecyclerView recyclerView) {
        super.onDetachRecyclerView(recyclerView);
        ViewParent parent=null!=recyclerView?recyclerView.getParent():null;
        if (null!=parent&&parent instanceof SwipeRefreshLayout){
            SwipeRefreshLayout layout=(SwipeRefreshLayout)parent;
            layout.setOnRefreshListener(null);
        }
    }

    private boolean enableRefreshing(boolean enable){
        RecyclerView recyclerView=getRecyclerView();
        ViewParent parent=null!=recyclerView?recyclerView.getParent():null;
        if (null!=parent&&parent instanceof SwipeRefreshLayout){
            return ((SwipeRefreshLayout)parent).post(()->((SwipeRefreshLayout)parent).setRefreshing(enable));
        }
        return false;
    }

    @Override
    public boolean onViewClick(View view, int i, int i1, Object o) {
        switch (i){
            case R.string.reset:
                return reset("While reset view click.");
        }
        return false;
    }

    @Override
    protected final Canceler onNextSectionLoad(SectionRequest<Query> request, OnSectionLoadFinish<Query, Path> callback, String s) {
        Client client=mCurrentClient.get();
        if (null==client){
            return null;
        }
        enableRefreshing(true);
        if (getDataCount()==0){
            notifyDataSetChanged();//Update empty view
        }
        return client.onNextSectionLoad(request, mLoading=new OnSyncApiFinish<Reply<Folder<Query,Path>>>(){
            @Override
            public void onApiFinish(int what, String note, Reply<Folder<Query, Path>> data, Object arg) {
                mLoadWhat=null!=data?data.getWhat():what;
                boolean succeed=what== What.WHAT_SUCCEED&&null!=data&&data.isSuccess();
                Folder<Query,Path> folder=null!=data?data.getData():null;
                if (null!=callback){
                    callback.onSectionLoadFinish(succeed,note,folder);
                }
                onSectionLoadFinish(succeed,folder);
                OnSyncApiFinish current=mLoading;
                if (null!=current&&current==this){
                    mLoading=null;
                    enableRefreshing(false);
                    if (getDataCount()==0){
                        notifyDataSetChanged();//Update empty view
                    }
                }
            }

            @Override
            public void onPathUpdate(Path reply) {
                RecyclerView recyclerView=getRecyclerView();
                if (null!=recyclerView){
                    recyclerView.post(()-> replace(reply,"While path update."));
                }
            }
        }, s);
    }

    protected void onSectionLoadFinish(boolean succeed,Folder<Query,Path> folder){
         //Do nothing
    }

    public final boolean browser(Query query,String debug){
        Query current=getLatestSectionArg();
        if (!((null==current&&null==query)||(null!=current&&null!=query&&current.equals(query)))){
            return resetSection(query,null,null,"While browser path changed "+(null!=debug?debug:"."));
        }
        return false;
    }

    protected void onClientChanged(Client client){
        //Do nothing
    }

    public final boolean setClient(Client client, String debug) {
        Client current=mCurrentClient.get();
        if (null==client&&null!=current){
            mCurrentClient.set(null);
            boolean succeed= reset(debug);
            onClientChanged(null);
            return succeed;
        }else if (null!=client&&!client.equals(current)){
            mCurrentClient.set(client);
            boolean succeed= reset(debug);
            onClientChanged(client);
            return succeed;
        }
        return false;
    }

    public final Client getCurrentClient() {
        return mCurrentClient.get();
    }

    public final ObservableField<Client> getBrowserClient() {
        return mCurrentClient;
    }

    public final boolean reset(String debug){
        Client client=mCurrentClient.get();
        return null!=client?resetSection(debug):clean("While client reset to NULL.");
    }

    @Override
    protected final Object onResolveDataViewHolder(ViewGroup viewGroup) {
        return R.layout.item_list_file;
    }

    @Override
    public final RecyclerView.LayoutManager onResolveLayoutManager(RecyclerView rv) {
        return new LinearLayoutManager(rv.getContext());
    }

    private Object getPathThumb(Context context,Path path){
        if (null!=path){
            Object thumb=path.getThumb();
            if (null!=thumb){
                return thumb;
            }else if (path.isDirectory()){
                return R.drawable.hidisk_icon_folder;
            }
            String mime = path.getMime();
            if (null == mime || mime.length() <= 0) {
                return R.drawable.hidisk_icon_unknown;
            }else if (path instanceof LocalPath){
                if (path.isAnyType(Path.TYPE_APK)){
                    String pathValue=path.getPath();
                    PackageManager manager = null!=context&&null!=pathValue?context.getPackageManager():null;
                    PackageInfo packageInfo = null!=manager?manager.getPackageArchiveInfo(pathValue, PackageManager.GET_ACTIVITIES):null;
                    if (packageInfo != null){
                        try {
                            ApplicationInfo info = packageInfo.applicationInfo;
                            info.sourceDir = pathValue;
                            info.publicSourceDir = pathValue;
                            return info.loadIcon(manager);
                        } catch (Exception e) {
                            //Do nothing
                        }
                    }
                }else if (path.isAnyType(Path.TYPE_IMAGE,Path.TYPE_VIDEO)){

                }
            }
            return mDefaultThumb.thumb(mime);
        }
        return null;
    }

    @Override
    protected final void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i, ViewDataBinding binding, int i1, Path data, List<Object> list) {
        super.onBindViewHolder(viewHolder, i, binding, i1, data, list);
        if (null!=binding&&binding instanceof ItemListFileBinding){
            View root=binding.getRoot();
            ItemListFileBinding fileBinding=(ItemListFileBinding)binding;
            fileBinding.setThumbImage(getPathThumb(null!=root?root.getContext():null,data));
            fileBinding.setPath(data);
            fileBinding.setPosition(i+1);
            fileBinding.setSyncColor(null!=data&&data instanceof LocalPath?
                    ((LocalPath)data).getSyncColor():Color.TRANSPARENT);
        }else if (null!=binding&&binding instanceof ItemBrowserEmptyBinding){
            ItemBrowserEmptyBinding emptyBinding=(ItemBrowserEmptyBinding)binding;
            boolean resetEnable=true;
            if (null!=mLoading){
                resetEnable=false;
                emptyBinding.setMessage(getText(R.string.doingWhich,getText(R.string.load)));
            }else{
                switch (mLoadWhat){
                    case What.WHAT_SUCCEED:
                        emptyBinding.setMessage(null);
                        resetEnable=false;
                        break;
                    case What.WHAT_NONE_PERMISSION:
                        emptyBinding.setMessage(getText(R.string.noneWhichPermission,getText(R.string.browser)));
                        break;
                    case What.WHAT_TIMEOUT:
                        emptyBinding.setMessage(getText(R.string.timeoutWhich,getText(R.string.load)));
                        break;
                    default:
                        emptyBinding.setMessage(getText(R.string.whichFailed,getText(R.string.browser)));
                        break;
                }
            }
            emptyBinding.setResetEnable(resetEnable);
        }
    }

    @Override
    public final Object onResolveItemTouch(RecyclerView recyclerView) {
        return new ItemTouchInterrupt(){
            @Override
            protected Integer onResolveSlide(RecyclerView.ViewHolder holder, RecyclerView.LayoutManager manager) {
                return new ItemSlideRemover().onResolveSlide(holder,manager);
            }
        };
    }

    private interface OnSyncApiFinish<A> extends OnApiFinish<A>, OnPathUpdate {

    }
}
