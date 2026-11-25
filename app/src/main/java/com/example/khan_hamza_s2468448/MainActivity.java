package com.example.khan_hamza_s2468448;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private final ArrayList<TrafficParser.Situation> allSituations =
            new ArrayList<>();

    private final ArrayList<TrafficParser.Situation> filteredSituations =
            new ArrayList<>();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private ListView trafficList;
    private EditText searchInput;
    private TextView statusText;
    private ProgressBar progressBar;
    private ArrayAdapter<TrafficParser.Situation> adapter;

    // Fixed reference point near Glasgow city centre (not live GPS).
    private static final double REFERENCE_LAT = 55.8642;
    private static final double REFERENCE_LON = -4.2518;
    private static final int[] RADII_KM = {0, 5, 10, 25, 50};
    private int radiusKm = 0;
    private Button distanceButton;

    private String selectedCategory = "All";
    private boolean loaded = false;
    private int savedPosition = 0;
    private int savedTop = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep content clear of system bars.
        View root = findViewById(R.id.main);

        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars());

            v.setPadding(left + bars.left, top + bars.top,
                    right + bars.right, bottom + bars.bottom);

            return insets;
        });

        trafficList = findViewById(R.id.trafficList);
        searchInput = findViewById(R.id.searchInput);
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);

        Button btnAll = findViewById(R.id.btnAll);
        Button btnIncidents = findViewById(R.id.btnIncidents);
        Button btnRoadworks = findViewById(R.id.btnRoadworks);

        if (savedInstanceState != null) {
            selectedCategory =
                    savedInstanceState.getString("category", "All");

            searchInput.setText(
                    savedInstanceState.getString("search", ""));

            radiusKm = savedInstanceState.getInt("radiusKm", 0);
            savedPosition = savedInstanceState.getInt("position");
            savedTop = savedInstanceState.getInt("top");
        }

        // Both layout variants place the search box in a LinearLayout.
        LinearLayout controls = (LinearLayout) searchInput.getParent();
        distanceButton = new androidx.appcompat.widget.AppCompatButton(this);
        distanceButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                Color.rgb(23, 90, 156)));
        distanceButton.setTextColor(Color.WHITE);
        distanceButton.setTextSize(12);
        distanceButton.setAllCaps(false);
        distanceButton.setMinHeight(0);
        controls.addView(distanceButton, controls.indexOfChild(searchInput) + 1,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.round(48 * getResources().getDisplayMetrics().density)));
        updateDistanceButton();
        distanceButton.setOnClickListener(v -> chooseDistance());

        adapter = new ArrayAdapter<TrafficParser.Situation>(
                this,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                filteredSituations) {

            @Override
            public View getView(int position, View convertView,
                                ViewGroup parent) {

                View row = super.getView(position, convertView, parent);

                TrafficParser.Situation item = getItem(position);

                TextView title = row.findViewById(android.R.id.text1);
                TextView description = row.findViewById(android.R.id.text2);

                if (item != null) {

                    // Show an icon for each traffic category.
                    int iconRes = "Incident".equals(item.category)
                            ? R.drawable.ic_incident
                            : R.drawable.ic_roadworks;

                    title.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            iconRes, 0, 0, 0);

                    title.setCompoundDrawablePadding(Math.round(
                            8 * getResources().getDisplayMetrics().density));


                    title.setText(item.category + " - "
                            + readable(item.type));

                    String summary = value(item.description);
                    if (radiusKm > 0) {
                        summary = String.format(Locale.UK,
                                "%.1f km from Glasgow (straight line)\n%s",
                                distanceKm(item), summary);
                    }
                    description.setText(summary);
                    description.setMaxLines(5);

                    if ("veryLongDelays".equals(item.delay)) {
                        title.setTextColor(Color.rgb(220, 70, 70));
                    } else if ("longDelays".equals(item.delay)) {
                        title.setTextColor(Color.rgb(230, 130, 20));
                    } else if ("delays".equals(item.delay)) {
                        title.setTextColor(Color.rgb(40, 155, 80));
                    } else {
                        title.setTextColor(description.getCurrentTextColor());
                    }
                }

                return row;
            }
        };

        trafficList.setAdapter(adapter);

        btnAll.setOnClickListener(v -> {
            selectedCategory = "All";
            filterSituations();
        });

        btnIncidents.setOnClickListener(v -> {
            selectedCategory = "Incident";
            filterSituations();
        });

        btnRoadworks.setOnClickListener(v -> {
            selectedCategory = "Roadworks";
            filterSituations();
        });

        searchInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s,
                                          int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s,
                                      int start, int before, int count) {
                filterSituations();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        trafficList.setOnItemClickListener(
                (parent, view, position, id) ->
                        showDetails(filteredSituations.get(position)));

        loadTrafficData();
    }

    // Load and parse XML on a background thread.
    private void loadTrafficData() {

        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("Loading traffic data...");

        executor.execute(() -> {

            try {
                TrafficParser parser = new TrafficParser();

                ArrayList<TrafficParser.Situation> results =
                        parser.parse(
                                readAsset("unplannedContentExample.xml"),
                                "Incident");

                results.addAll(parser.parse(
                        readAsset("roadworksContentExample.xml"),
                        "Roadworks"));

                handler.post(() -> {
                    if (isDestroyed()) return;

                    allSituations.clear();
                    allSituations.addAll(results);

                    loaded = true;
                    progressBar.setVisibility(View.GONE);

                    filterSituations();

                    trafficList.setSelectionFromTop(
                            savedPosition, savedTop);
                });

            } catch (Exception e) {

                android.util.Log.e("TrafficApp", "Loading failed", e);

                handler.post(() -> {
                    if (isDestroyed()) return;

                    progressBar.setVisibility(View.GONE);
                    statusText.setText("Unable to load traffic data.");
                });
            }
        });
    }

    // Filter by category and road name.
    private void filterSituations() {

        if (!loaded) return;

        filteredSituations.clear();

        String query = searchInput.getText().toString().trim();

        for (TrafficParser.Situation item : allSituations) {

            boolean categoryMatches =
                    selectedCategory.equals("All")
                            || selectedCategory.equals(item.category);

            if (categoryMatches && matchesSearch(item, query)
                    && (radiusKm == 0 || distanceKm(item) <= radiusKm)) {
                filteredSituations.add(item);
            }
        }

        adapter.notifyDataSetChanged();

        statusText.setText(filteredSituations.size()
                + " of " + allSituations.size()
                + " situations | Sample data"
                + (radiusKm > 0 ? " | Within " + radiusKm + " km" : ""));
    }

    private void updateDistanceButton() {
        distanceButton.setText(radiusKm == 0
                ? "Distance from Glasgow: Any"
                : "Distance from Glasgow: " + radiusKm + " km");
    }

    private void chooseDistance() {
        String[] choices = {"Any distance", "Within 5 km", "Within 10 km",
                "Within 25 km", "Within 50 km"};
        int checked = 0;
        for (int i = 0; i < RADII_KM.length; i++) {
            if (RADII_KM[i] == radiusKm) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("Distance from Glasgow")
                .setSingleChoiceItems(choices, checked, (dialog, which) -> {
                    radiusKm = RADII_KM[which];
                    updateDistanceButton();
                    filterSituations();
                    trafficList.setSelection(0);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Use the nearest available recorded point (not road travel distance).
    private double distanceKm(TrafficParser.Situation item) {
        double nearest = Double.POSITIVE_INFINITY;
        float[] result = new float[1];
        for (TrafficParser.Location point : item.locations) {
            try {
                double lat = Double.parseDouble(point.latitude);
                double lon = Double.parseDouble(point.longitude);
                if (Double.isNaN(lat) || Double.isNaN(lon)
                        || Math.abs(lat) > 90 || Math.abs(lon) > 180) continue;
                android.location.Location.distanceBetween(REFERENCE_LAT,
                        REFERENCE_LON, lat, lon, result);
                nearest = Math.min(nearest, result[0] / 1000.0);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // Skip missing or invalid coordinates.
            }
        }
        return nearest;
    }

    private boolean matchesSearch(TrafficParser.Situation item,
                                  String query) {

        if (query.isEmpty()) return true;

        if (matchesText(item.description, query)) return true;

        for (TrafficParser.Location location : item.locations) {
            for (String name : location.names) {
                if (matchesText(name, query)) return true;
            }
        }

        return false;
    }

    private boolean matchesText(String text, String query) {

        if (text == null) return false;

        String source = normalise(text);
        String search = normalise(query);

        // Prevent M8 from incorrectly matching M80.
        if (search.matches("[AMB]\\d+M?")) {

            String expression = "(?<![A-Z0-9])"
                    + Pattern.quote(search)
                    + "(?![A-Z0-9])";

            return Pattern.compile(expression).matcher(source).find();
        }

        return source.contains(search);
    }

    private String normalise(String text) {
        return text.toUpperCase(Locale.ROOT)
                .replace("(", "")
                .replace(")", "");
    }

    // Display detailed information about a selected situation.
    private void showDetails(TrafficParser.Situation item) {

        // Hide the search keyboard before opening the details dialog.
        InputMethodManager keyboard = (InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
        searchInput.clearFocus();

        StringBuilder details = new StringBuilder();

        details.append("Description:\n")
                .append(value(item.description));

        details.append("\n\nType: ")
                .append(readable(item.type));

        details.append("\nEvent: ")
                .append(readable(item.eventType));

        details.append("\nReference: ")
                .append(value(item.id));

        details.append("\n\nStart: ")
                .append(formatDate(item.startTime));

        details.append("\nEnd: ")
                .append(formatDate(item.endTime));

        details.append("\nDirection: ")
                .append(readable(item.direction));

        details.append("\nDelay: ")
                .append(readable(item.delay));

        details.append("\nRestricted lanes: ")
                .append(value(item.restrictedLanes));

        details.append("\nOperational lanes: ")
                .append(value(item.operationalLanes));

        for (TrafficParser.Location location : item.locations) {

            details.append("\n\nLocation: ")
                    .append(location.position);

            for (String name : location.names) {
                details.append("\n").append(name);
            }

            details.append("\nLatitude: ")
                    .append(value(location.latitude));

            details.append("\nLongitude: ")
                    .append(value(location.longitude));
        }

        new AlertDialog.Builder(this)
                .setTitle(item.category + " details")
                .setMessage(details.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private String value(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "Not provided";
        }
        return text;
    }

    // Convert ISO timestamps to readable UK local dates and times.
    private String formatDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "Not provided";
        }

        String input = text.trim().replaceFirst(
                "\\.\\d+(?=Z$|[+-]\\d{2}:\\d{2}$)", "");
        SimpleDateFormat source = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX", Locale.UK);
        source.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date date = source.parse(input, position);

        // Keep the original XML value if its format is unexpected.
        if (date == null || position.getIndex() != input.length()) {
            return text;
        }

        SimpleDateFormat display = new SimpleDateFormat(
                "dd MMM yyyy, HH:mm z", Locale.UK);
        display.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        return display.format(date);
    }

    private String readable(String text) {
        return value(text).replaceAll(
                "([a-z])([A-Z])", "$1 $2");
    }

    // Read lecturer's XML files into strings.
    private String readAsset(String filename) throws Exception {

        StringBuilder builder = new StringBuilder();

        try (InputStream input = getAssets().open(filename);
             InputStreamReader reader = new InputStreamReader(
                     input, java.nio.charset.StandardCharsets.UTF_8)) {

            char[] buffer = new char[4096];
            int count;

            while ((count = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, count);
            }
        }

        return builder.toString();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        super.onSaveInstanceState(outState);

        outState.putString("category", selectedCategory);
        outState.putInt("radiusKm", radiusKm);
        outState.putString("search", searchInput.getText().toString());

        outState.putInt("position",
                trafficList.getFirstVisiblePosition());

        View first = trafficList.getChildAt(0);

        outState.putInt("top", first == null ? 0 : first.getTop());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
