package com.telerik.plugins.mapbox;

import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.mapbox.mapboxgl.annotations.MarkerOptions;
import com.mapbox.mapboxgl.annotations.PolygonOptions;
import com.mapbox.mapboxgl.geometry.LatLng;
import com.mapbox.mapboxgl.geometry.LatLngZoom;
import com.mapbox.mapboxgl.views.MapView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;


// TODO for screen rotation, see https://www.mapbox.com/mapbox-android-sdk/#screen-rotation
// TODO fox Xwalk compat, see nativepagetransitions plugin

// TODO look at demo app: https://github.com/mapbox/mapbox-gl-native/blob/master/android/java/MapboxGLAndroidSDKTestApp/src/main/java/com/mapbox/mapboxgl/testapp/MainActivity.java
public class Mapboxgl extends CordovaPlugin {

  private static final String MAPBOX_ACCESSTOKEN_RESOURCE_KEY = "mapbox_accesstoken";

  private static final String ACTION_SHOW = "show";
  private static final String ACTION_HIDE = "hide";
  private static final String ACTION_ADD_MARKERS = "addMarkers";
  private static final String ACTION_ADD_MARKER_CALLBACK = "addMarkerCallback";
  private static final String ACTION_ADD_POLYGON = "addPolygon";
  private static final String ACTION_ADD_GEOJSON = "addGeoJSON";
  private static final String ACTION_GET_ZOOMLEVEL = "getZoomLevel";
  private static final String ACTION_SET_ZOOMLEVEL = "setZoomLevel";
  private static final String ACTION_GET_CENTER = "getCenter";
  private static final String ACTION_SET_CENTER = "setCenter";

  public static MapView mapView;
  private static float retinaFactor;
  private String accessToken;

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);

    DisplayMetrics metrics = new DisplayMetrics();
    cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
    retinaFactor = metrics.density;

    try {
      int mapboxAccesstokenResourceId = cordova.getActivity().getResources().getIdentifier(MAPBOX_ACCESSTOKEN_RESOURCE_KEY, "string", cordova.getActivity().getPackageName());
      accessToken = cordova.getActivity().getString(mapboxAccesstokenResourceId);
    } catch (Resources.NotFoundException e) {
      // we'll deal with this when the accessToken property is read, but for now let's dump the error:
      e.printStackTrace();
    }
  }

  @Override
  public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
    try {
      if (ACTION_SHOW.equals(action)) {
        final JSONObject options = args.getJSONObject(0);
        final String style = getStyle(options.optString("style"));

        final JSONObject margins = options.isNull("margins") ? null : options.getJSONObject("margins");
        final int left = applyRetinaFactor(margins == null || margins.isNull("left") ? 0 : margins.getInt("left"));
        final int right = applyRetinaFactor(margins == null || margins.isNull("right") ? 0 : margins.getInt("right"));
        final int top = applyRetinaFactor(margins == null || margins.isNull("top") ? 0 : margins.getInt("top"));
        final int bottom = applyRetinaFactor(margins == null || margins.isNull("bottom") ? 0 : margins.getInt("bottom"));

        final JSONObject center = options.isNull("center") ? null : options.getJSONObject("center");

        cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            if (accessToken == null) {
              callbackContext.error(MAPBOX_ACCESSTOKEN_RESOURCE_KEY + " not set in strings.xml");
              return;
            }
            mapView = new MapView(webView.getContext(), accessToken);

            // need to do this to register a receiver which onPause later needs
            mapView.onResume();
            mapView.onCreate(null);

            try {
              mapView.setCompassEnabled(options.isNull("hideCompass") || !options.getBoolean("hideCompass"));
              mapView.setRotateEnabled(options.isNull("disableRotation") || !options.getBoolean("disableRotation"));
              mapView.setScrollEnabled(options.isNull("disableScroll") || !options.getBoolean("disableScroll"));
              mapView.setZoomEnabled(options.isNull("disableZoom") || !options.getBoolean("disableZoom"));

              // placing these offscreen in case the user wants to hide them
              if (!options.isNull("hideAttribution") && options.getBoolean("hideAttribution")) {
                mapView.setAttributionMargins(-300, 0, 0, 0);
              }
              if (!options.isNull("hideLogo") && options.getBoolean("hideLogo")) {
                mapView.setLogoMargins(-300, 0, 0, 0);
              }

              final boolean showUserLocation = !options.isNull("showUserLocation") && options.getBoolean("showUserLocation");
              mapView.setMyLocationEnabled(showUserLocation);

              final double zoomLevel = options.isNull("zoomLevel") ? 10 : options.getDouble("zoomLevel");
              if (center != null) {
                final double lat = center.getDouble("lat");
                final double lng = center.getDouble("lng");
                mapView.setCenterCoordinate(new LatLngZoom(lat, lng, zoomLevel));
              } else {
                mapView.setZoomLevel(zoomLevel);
              }

              if (options.has("markers")) {
                addMarkers(options.getJSONArray("markers"));
              }
            } catch (JSONException e) {
              callbackContext.error(e.getMessage());
              return;
            }

            mapView.setStyleUrl("asset://styles/" + style + "-v8.json");

            // position the mapView overlay
            int webViewWidth = webView.getView().getWidth();
            int webViewHeight = webView.getView().getHeight();
            final FrameLayout layout = (FrameLayout) webView.getView().getParent();
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(webViewWidth - left - right, webViewHeight - top - bottom);
            params.setMargins(left, top, right, bottom);
            mapView.setLayoutParams(params);

            layout.addView(mapView);
            callbackContext.success();
          }
        });

      } else if (ACTION_HIDE.equals(action)) {
        if (mapView != null) {
          cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
              ViewGroup vg = (ViewGroup) mapView.getParent();
              if (vg != null) {
                vg.removeView(mapView);
              }
              callbackContext.success();
            }
          });
        }

      } else if (ACTION_GET_ZOOMLEVEL.equals(action)) {
        if (mapView != null) {
          cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
              final double zoomLevel = mapView.getZoomLevel();
              callbackContext.success("" + zoomLevel);
            }
          });
        }

      } else if (ACTION_SET_ZOOMLEVEL.equals(action)) {
        if (mapView != null) {
          cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
              try {
                final JSONObject options = args.getJSONObject(0);
                final double zoom = options.getDouble("level");
                if (zoom >= 0 && zoom <= 20) {
                  final boolean animated = !options.isNull("animated") && options.getBoolean("animated");
                  mapView.setZoomLevel(zoom, animated);
                  callbackContext.success();
                } else {
                  callbackContext.error("invalid zoomlevel, use any double value from 0 to 20 (like 8.3)");
                }
              } catch (JSONException e) {
                callbackContext.error(e.getMessage());
              }
            }
          });
        }

      } else if (ACTION_GET_CENTER.equals(action)) {
        if (mapView != null) {
          cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
              final LatLng center = mapView.getCenterCoordinate();
              Map<String, Double> result = new HashMap<String, Double>();
              result.put("lat", center.getLatitude());
              result.put("lng", center.getLongitude());
              callbackContext.success(new JSONObject(result));
            }
          });
        }

      } else if (ACTION_SET_CENTER.equals(action)) {
        if (mapView != null) {
          cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
              try {
                final JSONObject options = args.getJSONObject(0);
                final boolean animated = !options.isNull("animated") && options.getBoolean("animated");
                final double lat = options.getDouble("lat");
                final double lng = options.getDouble("lng");
                mapView.setCenterCoordinate(new LatLng(lat, lng), animated);
                callbackContext.success();
              } catch (JSONException e) {
                callbackContext.error(e.getMessage());
              }
            }
          });
        }

      } else if (ACTION_ADD_POLYGON.equals(action)) {
        cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            try {
              final PolygonOptions polygon = new PolygonOptions();
              final JSONObject options = args.getJSONObject(0);
              final JSONArray points = options.getJSONArray("points");
              for (int i=0; i<points.length(); i++) {
                final JSONObject marker = points.getJSONObject(i);
                final double lat = marker.getDouble("lat");
                final double lng = marker.getDouble("lng");
                polygon.add(new LatLng(lat, lng));
              }
              mapView.addPolygon(polygon);

              callbackContext.success();
            } catch (JSONException e) {
              callbackContext.error(e.getMessage());
            }
          }
        });

      } else if (ACTION_ADD_GEOJSON.equals(action)) {
        cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            // TODO implement
            callbackContext.success();
          }
        });

      } else if (ACTION_ADD_MARKERS.equals(action)) {
        cordova.getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            try {
              addMarkers(args.getJSONArray(0));
              callbackContext.success();
            } catch (JSONException e) {
              callbackContext.error(e.getMessage());
            }
          }
        });

      } else {
        return false;
      }
    } catch (Throwable t) {
      t.printStackTrace();
      callbackContext.error(t.getMessage());
    }
    return true;
  }

  private void addMarkers(JSONArray markers) throws JSONException {
    for (int i=0; i<markers.length(); i++) {
      final JSONObject marker = markers.getJSONObject(i);
      final MarkerOptions mo = new MarkerOptions();
      mo.title(marker.isNull("title") ? null : marker.getString("title"));
      mo.snippet(marker.isNull("subtitle") ? null : marker.getString("subtitle"));
      mo.position(new LatLng(marker.getDouble("lat"), marker.getDouble("lng")));
      mapView.addMarker(mo);
    }
  }

  private static int applyRetinaFactor(int i) {
    return (int) (i * retinaFactor);
  }

  private static String getStyle(final String requested) {
    if ("light".equalsIgnoreCase(requested)) {
      return "light";
    } else if ("dark".equalsIgnoreCase(requested)) {
      return "dark";
    } else if ("emerald".equalsIgnoreCase(requested)) {
      return "emerald";
    } else if ("satellite".equalsIgnoreCase(requested)) {
      return "satellite";
    } else {
      return "streets";
    }
  }

  public void onPause(boolean multitasking) {
    mapView.onPause();
  }

  public void onResume(boolean multitasking) {
    mapView.onResume();
  }

  public void onDestroy() {
    mapView.onDestroy();
  }
}