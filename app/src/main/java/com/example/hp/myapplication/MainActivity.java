package com.example.hp.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.GeometryType;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polygon;
import com.esri.arcgisruntime.geometry.PolygonBuilder;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.GeoElement;
import com.esri.arcgisruntime.mapping.SelectionProperties;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.IdentifyLayerResult;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.mapping.view.SketchCreationMode;
import com.esri.arcgisruntime.mapping.view.SketchEditor;
import com.esri.arcgisruntime.mapping.view.WrapAroundMode;
import com.esri.arcgisruntime.symbology.SimpleFillSymbol;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    MapView mMapView;
    private static final int FILE_SELECT_CODE = 0;
    private static final String TAG = "ChooseFile";

    private enum ReadyState{
        NO_READY,READY;
    }
    private enum EditMode{
        MODIFY;
    }
    private enum EditState {
        ON,OFF;
    }
    private Context thisActivity = null;
    private ReadyState dataLoadingState = ReadyState.NO_READY;
    private ReadyState featureSelectState = ReadyState.NO_READY;
    private EditState editState = EditState.OFF;
    private EditMode editMode = EditMode.MODIFY;
    private boolean isSelecting = false;
    private boolean hasUpdate = false;

    private Feature mSelectedFeature = null;
    private GeometryType geometryType = null;
    private Graphic graphic = null;
    private FeatureLayer mCurrentLayer = null;
    private ArcGISMap mmap = null;
    private com.esri.arcgisruntime.mapping.view.SketchEditor mSkretchEditor = null;
    private List<Feature> mToSaveFeatures = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMapView = findViewById(R.id.mapView);
        mToSaveFeatures = new ArrayList<Feature>();
        thisActivity = this;
        invokeInitFunctions();
    }

    /**
     * 配置map，指定初始化位置和levelofdetails，basemap乐行
     * 包括mmap渲染的颜色
     */

    private void invokeInitFunctions(){
        requestReadPermission();
        initMapView();
        intiLoadShapeFileButton();
        initSelectButton();
        initMapListener();
        initOnAndOffButton();
        initLastStepButton();
        initSaveButton();
        initRemoveAllEditButton();
    }
    private void intiLoadShapeFileButton(){
        Button loadShapeFileBtn = findViewById(R.id.loadShapefile);
        loadShapeFileBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFileChooser();
            }
        });
    }

    private void initMapView(){
        //初始化mmap
        mmap = new ArcGISMap(Basemap.Type.TOPOGRAPHIC,34.056295, -117.195800, 16);
        mMapView.setMap(mmap);
        SimpleLineSymbol lineSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, 0xFFFF0000, 1f);
        SimpleFillSymbol fillSymbol = new SimpleFillSymbol(SimpleFillSymbol.Style.SOLID,0xFFFF0000,lineSymbol );
        //设置selection颜色
        SelectionProperties selectionProperties = mMapView.getSelectionProperties();
        selectionProperties.setColor(Color.RED);
        //添加skretcheditor
        mSkretchEditor = new SketchEditor();
        mMapView.setSketchEditor(mSkretchEditor);
        //设置不能wrao
        mMapView.setWrapAroundMode(WrapAroundMode.DISABLED);
    }

    /**
     * 初始化select按钮，设置样式，点选的时候改变颜色
     */
    private void initSelectButton(){
        final Button selectButton = findViewById(R.id.select_feature);
        if(mMapView.getGraphicsOverlays().size() == 0){
            mMapView.getGraphicsOverlays().add(new GraphicsOverlay());
        }
        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!isSelecting){
                    selectButton.setBackgroundColor(Color.YELLOW);
                    selectButton.setSelected(true);
                    isSelecting = true;
                }else {
                    selectButton.setBackgroundColor(Color.GRAY);
                    selectButton.setSelected(false);
                    isSelecting = false;
                }
            }
        });
    }

    /**
     * 初始化编辑开关按钮
     */
    private void initOnAndOffButton(){
        final Button onAndOffButton = findViewById(R.id.on_and_off);
        onAndOffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(dataLoadingState == ReadyState.NO_READY){
                    Toast.makeText(thisActivity, "请加载数据.", Toast.LENGTH_SHORT).show();
                }else if(dataLoadingState == ReadyState.READY){
                   if(featureSelectState == ReadyState.NO_READY){
                       Toast.makeText(thisActivity, "请选择要素.", Toast.LENGTH_SHORT).show();
                   }else if(featureSelectState == ReadyState.READY){
                       //开始编辑workflow
                       if(editState == EditState.OFF){
                           //根据currentlayer的geomertrytype选择编辑模式
                           if(geometryType == GeometryType.POLYGON){
                               mSkretchEditor.start(mSelectedFeature.getGeometry(),SketchCreationMode.POLYGON);
                           }else if(geometryType == GeometryType.POLYLINE){
                               mSkretchEditor.start(mSelectedFeature.getGeometry(),SketchCreationMode.POLYLINE);
                           }else if(geometryType == GeometryType.POINT){
                               mSkretchEditor.start(mSelectedFeature.getGeometry(),SketchCreationMode.POINT);
                           }else if(geometryType == GeometryType.MULTIPOINT){
                               mSkretchEditor.start(mSelectedFeature.getGeometry(),SketchCreationMode.MULTIPOINT);
                           }
                           Button button = findViewById(R.id.select_feature);
                           if (button.hasSelection() == true) {
                               findViewById(R.id.select_feature).callOnClick();
                           }
                           editState = EditState.ON;
                           onAndOffButton.setText("结束编辑");
                           hasUpdate = true;
                           Toast.makeText(thisActivity, "开始编辑.", Toast.LENGTH_SHORT).show();
                       }else if(editState == EditState.ON){
                           //结束编辑workflow
                           if(mSkretchEditor.isSketchValid() == false){
                               Toast.makeText(thisActivity, "本次编辑无效.", Toast.LENGTH_SHORT).show();
                           }else{
                               //保存编辑后的geomertry和Tosave的feature
                               Geometry skretchGeometry = mSkretchEditor.getGeometry();
                               mSelectedFeature.setGeometry(skretchGeometry);
                               mToSaveFeatures.add(mSelectedFeature);
                               Toast.makeText(thisActivity, "编辑成功.", Toast.LENGTH_SHORT).show();
                           }
                           //更新状态
                           mSkretchEditor.stop();
                           editState = EditState.OFF;
                           onAndOffButton.setText("开始编辑");
                           mCurrentLayer.selectFeature(mSelectedFeature);
                       }
                   }
                }
            }
        });
    }
    /**
     * 初始化map时间监听器
     */
    @SuppressLint("ClickableViewAccessibility")
    private void initMapListener(){
        mMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(thisActivity,mMapView){
            @Override
            public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
                if(dataLoadingState == ReadyState.READY){
                    if(editState == EditState.OFF && isSelecting == true){
                        if(mSelectedFeature != null){
                            mCurrentLayer.clearSelection();
                        }
                        identifyFeature(getScreenPoint(motionEvent));
                    }
                }
                return true;
            }
        });
    }

    private void initLastStepButton(){
        Button lastStepButton = findViewById(R.id.last_step);
        lastStepButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mSkretchEditor.canUndo()){
                    mSkretchEditor.undo();
                    Toast.makeText(thisActivity, "回退成功.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initRemoveAllEditButton(){
        Button removeAllEditButton = findViewById(R.id.removeAllEdit);
        removeAllEditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                while(mSkretchEditor.canUndo()){
                    mSkretchEditor.undo();
                    Toast.makeText(thisActivity, "回退成功.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initSaveButton(){
        Button saveButton = findViewById(R.id.save);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(editState == EditState.ON){
                     Toast.makeText(thisActivity, "请先结束编辑", Toast.LENGTH_SHORT).show();
                     return;
                }
                if(hasUpdate == false){
                    Toast.makeText(thisActivity, "请先编辑要素", Toast.LENGTH_SHORT).show();
                    return;
                }
               if(editMode == EditMode.MODIFY && editState == EditState.OFF){
                  boolean result = Util.updateFeature(mCurrentLayer,mToSaveFeatures,thisActivity);
                  if(result == true){
                      Toast.makeText(thisActivity, "保存成功", Toast.LENGTH_SHORT).show();
                  }else{
                      Toast.makeText(thisActivity, "保存失败", Toast.LENGTH_SHORT).show();
                  }
               }
            }
        });
    }
    //调用系统文件选择器，选择shapefile文件
    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult( Intent.createChooser(intent, "Select a File to Upload"), FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
        }
    }


    //相应文件选择的回调结果，得到path，加载shapefile
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();
                    Log.d(TAG, "File Uri: " + uri.toString());
                    // Get the path
                    String path = null;
                    try {
                        path = Util.getPath(this, uri);
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "File Path: " + path);
                    //加载shapfile layer
                    geometryType = Util.loadShapeFile(mMapView, path,thisActivity);
                    if(geometryType != null){
                        int layerSize = mmap.getOperationalLayers().size();
                        //选取最新的featurelayer
                        mCurrentLayer = (FeatureLayer) mmap.getOperationalLayers().get(layerSize - 1);
                        if(mCurrentLayer.getLoadStatus() == LoadStatus.LOADED){
                            dataLoadingState = ReadyState.READY;
                        }
                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void identifyFeature(android.graphics.Point screenPoint) {
        if(isSelecting == true){
            final ListenableFuture<IdentifyLayerResult> identifyLayerResult= mMapView.identifyLayerAsync(mCurrentLayer,screenPoint,10,false,1);
            identifyLayerResult.addDoneListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        IdentifyLayerResult result = identifyLayerResult.get();
                        GeoElement geoElement = null;
                        if (result.getElements().size()> 0) {
                            geoElement = result.getElements().get(0);
                            Feature feature = (Feature)geoElement;
                            mCurrentLayer.selectFeature((Feature) geoElement);
                            featureSelectState = ReadyState.READY;
                            Button selectButton = findViewById(R.id.select_feature);
                            mSelectedFeature = feature;
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }


    private android.graphics.Point getScreenPoint(MotionEvent motionEvent) {
        // get the screen point
        android.graphics.Point screenPoint = new android.graphics.Point(Math.round(motionEvent.getX()),
                Math.round(motionEvent.getY()));
        // return the point that was clicked in map coordinates
        return screenPoint;
    }

    private Point getMapPoint(android.graphics.Point point){
        return mMapView.screenToLocation(point);
    }

    private void requestReadPermission() {
        // define permission to request
        String[] reqPermission = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        int requestCode = 2;
        // For API level 23+ request permission at runtime
        for (String permission : reqPermission) {
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                   permission) != PackageManager.PERMISSION_GRANTED) {
                // request permission
                ActivityCompat.requestPermissions(MainActivity.this, reqPermission, requestCode);
            }
        }
    }

}
