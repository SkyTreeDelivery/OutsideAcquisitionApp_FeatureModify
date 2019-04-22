package com.example.hp.myapplication;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Environment;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureCollection;
import com.esri.arcgisruntime.data.FeatureTable;
import com.esri.arcgisruntime.data.ShapefileFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.GeometryType;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.layers.Layer;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.symbology.Renderer;
import com.esri.arcgisruntime.symbology.SimpleFillSymbol;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleRenderer;

import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class Util {

    public static GeometryType loadShapeFile(final MapView mapView, String path, final Context context){
       /* String s = Environment.getExternalStorageDirectory().getAbsolutePath();
        s += "/Atestdata/Export_Output.shp";*/
       final ArcGISMap arcGISMap = mapView.getMap();
        final ShapefileFeatureTable shapefileFeatureTable = new ShapefileFeatureTable(path);
        shapefileFeatureTable.loadAsync();
        final GeometryType[] geometryType = {null};
        shapefileFeatureTable.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                if(shapefileFeatureTable.getLoadStatus() == LoadStatus.LOADED){
                    geometryType[0] = shapefileFeatureTable.getGeometryType();
                    final FeatureLayer featureLayer = new FeatureLayer(shapefileFeatureTable);
                    SimpleLineSymbol lineSymbol  = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID,Color.RED,1.0f);
                    SimpleFillSymbol fillSymbol = new SimpleFillSymbol(SimpleFillSymbol.Style.SOLID,Color.YELLOW,lineSymbol);
                    Renderer renderer = new SimpleRenderer(fillSymbol);
                    featureLayer.setRenderer(renderer);
                    featureLayer.addDoneLoadingListener(new Runnable() {
                        @Override
                        public void run() {
                            if(shapefileFeatureTable.getLoadStatus() == LoadStatus.LOADED){
                                final ListenableFuture<Boolean> setResult =  mapView.setViewpointGeometryAsync(featureLayer.getFullExtent());
                                setResult.addDoneListener(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            boolean isSetExtent = setResult.get();
                                            if(isSetExtent == false){
                                                Toast.makeText(context, "extent设置失败：", Toast.LENGTH_SHORT).show();
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
                    });
                    if(arcGISMap != null){
                        arcGISMap.getOperationalLayers().add(featureLayer);
                    }
                }else{
                    String ss = shapefileFeatureTable.getLoadError().toString();
                    Toast.makeText(context, "读取失败：" + ss, Toast.LENGTH_SHORT).show();
                }
            }
        });
        return geometryType[0];
    }

    public static boolean updateFeature(FeatureLayer layer, List<Feature> features, final Context activity){
        final ListenableFuture<Void> updateResult = layer.getFeatureTable().updateFeaturesAsync(features);
        boolean result = false;
        try {
            updateResult.get();
            if(updateResult.isDone()){
                result = true;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void deleteFeature(FeatureLayer layer, List<Feature> features, final Context activity){
        final ListenableFuture<Void> updateResult = layer.getFeatureTable().deleteFeaturesAsync(features);
        updateResult.addDoneListener(new Runnable() {
            @Override
            public void run() {
                try {
                    updateResult.get();
                    if(updateResult.isDone()){
                        Toast.makeText(activity, "保存成功", Toast.LENGTH_SHORT).show();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    public static String getPath(Context context, Uri uri) throws URISyntaxException {
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = { "_data" };
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, projection, null, null, null);
                int column_index = cursor.getColumnIndexOrThrow("_data");
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                // Eat it  Or Log it.
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }
}
