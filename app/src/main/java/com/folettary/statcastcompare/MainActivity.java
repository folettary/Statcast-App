package com.folettary.statcastcompare;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.text.Editable;
import android.text.TextWatcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int STATCAST_START_YEAR = 2015;
    private static final int NAVY = Color.rgb(11, 21, 52);
    private static final int TEAL = Color.rgb(17, 181, 164);
    private static final int SALMON = Color.rgb(255, 122, 107);
    private static final int BG = Color.rgb(244, 247, 251);
    private static final int INK = Color.rgb(25, 31, 44);
    private static final int MUTED = Color.rgb(97, 106, 122);
    private static final int CARD = Color.WHITE;

    private final ExecutorService io = Executors.newFixedThreadPool(6);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, String> textCache = Collections.synchronizedMap(new HashMap<>());

    private LinearLayout root;
    private EditText searchInput;
    private ListView suggestionsList;
    private TextView selectedLabel;
    private Spinner seasonSpinner;
    private Button compareButton;
    private ProgressBar loading;
    private TextView errorView;
    private LinearLayout resultsBox;
    private TextView resultTitle;
    private TextView resultMeta;
    private LinearLayout metricBox;
    private Button copyButton;

    private final ArrayList<Player> allPlayers = new ArrayList<>();
    private final ArrayList<Player> filteredPlayers = new ArrayList<>();
    private ArrayAdapter<String> suggestionsAdapter;
    private Player selectedPlayer;
    private Comparison lastComparison;

    private final Metric[] metrics = new Metric[] {
            new Metric("avgEV", "Avg Exit Velocity", "mph", 1, true, "contact"),
            new Metric("avgLA", "Avg Launch Angle", "°", 1, null, "contact"),
            new Metric("hardHitPct", "Hard-Hit Rate", "%", 1, true, "rate"),
            new Metric("barrelPct", "Barrel Rate", "%", 1, true, "rate"),
            new Metric("sweetSpotPct", "Sweet-Spot Rate", "%", 1, true, "rate"),
            new Metric("xBA", "xBA", "", 3, true, "expected"),
            new Metric("xSLG", "xSLG", "", 3, true, "expected"),
            new Metric("xwOBA", "xwOBA", "", 3, true, "expected")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        loadPlayers();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        LinearLayout hero = verticalCard(22, new int[] { NAVY, Color.rgb(21, 51, 89) });
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.addView(hero, matchWrap());

        TextView eyebrow = text("LIVE STATCAST", 12, Color.rgb(173, 241, 234), true);
        eyebrow.setLetterSpacing(0.12f);
        hero.addView(eyebrow);

        TextView title = text("MLB Statcast Compare", 30, Color.WHITE, true);
        title.setPadding(0, dp(7), 0, dp(4));
        hero.addView(title);

        TextView subtitle = text("Pick a hitter and compare this season against league average and his Statcast-era career.", 15, Color.rgb(219, 228, 246), false);
        subtitle.setLineSpacing(dp(2), 1.0f);
        hero.addView(subtitle);

        LinearLayout form = verticalCard(22, null);
        form.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams formLp = matchWrap();
        formLp.setMargins(0, dp(14), 0, 0);
        root.addView(form, formLp);

        TextView searchLabel = text("Player", 13, MUTED, true);
        form.addView(searchLabel);

        searchInput = new EditText(this);
        searchInput.setHint("Type a player name, e.g. Soto");
        searchInput.setSingleLine(true);
        searchInput.setTextSize(17);
        searchInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        searchInput.setBackground(roundedStroke(Color.WHITE, Color.rgb(207, 217, 231), 14, 1));
        LinearLayout.LayoutParams inputLp = matchWrap();
        inputLp.setMargins(0, dp(6), 0, dp(8));
        form.addView(searchInput, inputLp);

        suggestionsList = new ListView(this);
        suggestionsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        suggestionsList.setAdapter(suggestionsAdapter);
        suggestionsList.setVisibility(View.GONE);
        suggestionsList.setDividerHeight(1);
        suggestionsList.setBackground(roundedStroke(Color.WHITE, Color.rgb(230, 235, 244), 14, 1));
        form.addView(suggestionsList, new LinearLayout.LayoutParams(-1, dp(190)));

        selectedLabel = text("Loading active MLB players…", 13, MUTED, false);
        selectedLabel.setPadding(0, dp(8), 0, dp(10));
        form.addView(selectedLabel);

        TextView seasonLabel = text("Season", 13, MUTED, true);
        form.addView(seasonLabel);

        seasonSpinner = new Spinner(this);
        ArrayList<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int y = currentYear; y >= STATCAST_START_YEAR; y--) years.add(String.valueOf(y));
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, years);
        seasonSpinner.setAdapter(yearAdapter);
        form.addView(seasonSpinner, matchWrap());

        compareButton = new Button(this);
        compareButton.setText("Compare Statcast numbers");
        compareButton.setTextColor(Color.WHITE);
        compareButton.setTextSize(16);
        compareButton.setAllCaps(false);
        compareButton.setTypeface(Typeface.DEFAULT_BOLD);
        compareButton.setBackground(rounded(TEAL, 16));
        LinearLayout.LayoutParams btnLp = matchWrap();
        btnLp.setMargins(0, dp(14), 0, 0);
        form.addView(compareButton, btnLp);

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        loadLp.gravity = Gravity.CENTER_HORIZONTAL;
        loadLp.setMargins(0, dp(14), 0, 0);
        form.addView(loading, loadLp);

        errorView = text("", 14, Color.rgb(174, 55, 70), false);
        errorView.setPadding(0, dp(12), 0, 0);
        errorView.setVisibility(View.GONE);
        form.addView(errorView);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        resultsBox.setVisibility(View.GONE);
        LinearLayout.LayoutParams resultsLp = matchWrap();
        resultsLp.setMargins(0, dp(16), 0, 0);
        root.addView(resultsBox, resultsLp);

        resultTitle = text("", 24, INK, true);
        resultsBox.addView(resultTitle);
        resultMeta = text("", 13, MUTED, false);
        resultMeta.setPadding(0, dp(4), 0, dp(12));
        resultsBox.addView(resultMeta);

        copyButton = new Button(this);
        copyButton.setText("Copy comparison table");
        copyButton.setAllCaps(false);
        copyButton.setTextColor(NAVY);
        copyButton.setBackground(roundedStroke(Color.WHITE, Color.rgb(203, 214, 228), 14, 1));
        resultsBox.addView(copyButton, matchWrap());

        metricBox = new LinearLayout(this);
        metricBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams metricsLp = matchWrap();
        metricsLp.setMargins(0, dp(10), 0, 0);
        resultsBox.addView(metricBox, metricsLp);

        TextView notes = text("Notes: career means Statcast-era career, 2015–present. Player values are computed from Statcast Search rows; league average uses the Baseball Savant custom leaderboard when available.", 12, MUTED, false);
        notes.setLineSpacing(dp(2), 1.0f);
        notes.setPadding(0, dp(14), 0, 0);
        resultsBox.addView(notes);

        wireEvents();
    }

    private void wireEvents() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterPlayers(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        suggestionsList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < filteredPlayers.size()) {
                selectedPlayer = filteredPlayers.get(position);
                searchInput.setText(selectedPlayer.fullName);
                searchInput.setSelection(searchInput.getText().length());
                suggestionsList.setVisibility(View.GONE);
                selectedLabel.setText(selectedPlayer.fullName + " · " + selectedPlayer.team + " · " + selectedPlayer.position + " · ID " + selectedPlayer.id);
                hideKeyboard();
            }
        });

        compareButton.setOnClickListener(v -> compareSelected());
        copyButton.setOnClickListener(v -> copyLastComparison());
    }

    private void loadPlayers() {
        setBusy(true, "Loading active MLB players…");
        io.execute(() -> {
            try {
                ArrayList<Player> players = fetchActivePlayers();
                main.post(() -> {
                    allPlayers.clear();
                    allPlayers.addAll(players);
                    selectedLabel.setText("Loaded " + players.size() + " active players. Start typing a name.");
                    setBusy(false, null);
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load MLB player list. Check your connection and reopen the app. " + e.getMessage());
                    selectedLabel.setText("Player list unavailable.");
                });
            }
        });
    }

    private void filterPlayers(String raw) {
        suggestionsAdapter.clear();
        filteredPlayers.clear();
        String q = raw.trim().toLowerCase(Locale.US);
        if (q.length() < 2 || allPlayers.isEmpty()) {
            suggestionsList.setVisibility(View.GONE);
            return;
        }
        for (Player p : allPlayers) {
            if (p.fullName.toLowerCase(Locale.US).contains(q)) {
                filteredPlayers.add(p);
                suggestionsAdapter.add(p.fullName + "  ·  " + p.team + "  ·  " + p.position);
                if (filteredPlayers.size() >= 20) break;
            }
        }
        suggestionsAdapter.notifyDataSetChanged();
        suggestionsList.setVisibility(filteredPlayers.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void compareSelected() {
        if (selectedPlayer == null) {
            showError("Pick a player from the search results first.");
            return;
        }
        hideKeyboard();
        showError(null);
        setBusy(true, "Loading Statcast data… this can take a minute for full career history.");
        int season = Integer.parseInt((String) seasonSpinner.getSelectedItem());
        Player player = selectedPlayer;
        io.execute(() -> {
            try {
                Stats seasonStats = summarizeRawRows(fetchPlayerRowsForSeason(player.id, season));
                Stats careerStats = summarizeRawRows(fetchPlayerCareerRows(player.id, season));
                Stats leagueStats = fetchLeagueAverage(season);
                Comparison comparison = new Comparison(player, season, seasonStats, careerStats, leagueStats, new Date());
                main.post(() -> {
                    lastComparison = comparison;
                    renderComparison(comparison);
                    setBusy(false, null);
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false, null);
                    showError("Could not load Statcast comparison. This can happen if Baseball Savant is slow/rate-limiting or if the player has no current-season batted balls. " + e.getMessage());
                });
            }
        });
    }

    private void renderComparison(Comparison c) {
        resultsBox.setVisibility(View.VISIBLE);
        resultTitle.setText(c.player.fullName + " · " + c.season);
        resultMeta.setText("Updated " + c.updated.toString() + " · Season PA " + fmtCount(c.seasonStats.pa) + " · Career PA " + fmtCount(c.careerStats.pa));
        metricBox.removeAllViews();

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.HORIZONTAL);
        summary.setGravity(Gravity.CENTER);
        summary.addView(summaryPill("Season", fmtCount(c.seasonStats.pa) + " PA\n" + fmtCount(c.seasonStats.bbe) + " BBE"), weightLp());
        summary.addView(summaryPill("League", c.leagueStats == null ? "Unavailable" : fmtCount(c.leagueStats.pa) + " PA\n" + fmtCount(c.leagueStats.bbe) + " BBE"), weightLp());
        summary.addView(summaryPill("Career", fmtCount(c.careerStats.pa) + " PA\n" + fmtCount(c.careerStats.bbe) + " BBE"), weightLp());
        metricBox.addView(summary, matchWrap());

        for (Metric m : metrics) renderMetricCard(c, m);
    }

    private TextView summaryPill(String label, String value) {
        TextView tv = text(label + "\n" + value, 13, INK, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), dp(10), dp(8), dp(10));
        tv.setBackground(roundedStroke(Color.WHITE, Color.rgb(224, 231, 241), 16, 1));
        tv.setLineSpacing(dp(2), 1.0f);
        return tv;
    }

    private void renderMetricCard(Comparison c, Metric m) {
        Double seasonValue = c.seasonStats.get(m.key);
        Double leagueValue = c.leagueStats == null ? null : c.leagueStats.get(m.key);
        Double careerValue = c.careerStats.get(m.key);
        Double vsLeague = diff(seasonValue, leagueValue);
        Double vsCareer = diff(seasonValue, careerValue);

        LinearLayout card = verticalCard(18, null);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, dp(10), 0, 0);
        metricBox.addView(card, lp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labelCol = new LinearLayout(this);
        labelCol.setOrientation(LinearLayout.VERTICAL);
        top.addView(labelCol, new LinearLayout.LayoutParams(0, -2, 1));
        labelCol.addView(text(m.label, 18, INK, true));
        TextView sub = text("Season vs league vs career", 12, MUTED, false);
        sub.setPadding(0, dp(3), 0, 0);
        labelCol.addView(sub);
        TextView big = text(format(seasonValue, m), 24, NAVY, true);
        top.addView(big);
        card.addView(top);

        LinearLayout deltas = new LinearLayout(this);
        deltas.setOrientation(LinearLayout.HORIZONTAL);
        deltas.setPadding(0, dp(10), 0, dp(6));
        deltas.addView(deltaPill("vs lg " + signedFormat(vsLeague, m), deltaColor(vsLeague, m)), weightLp());
        deltas.addView(deltaPill("vs career " + signedFormat(vsCareer, m), deltaColor(vsCareer, m)), weightLp());
        card.addView(deltas);

        double[] widths = scaleValues(new Double[] { seasonValue, leagueValue, careerValue }, m);
        card.addView(barRow("Season", seasonValue, widths[0], m, TEAL));
        card.addView(barRow("League", leagueValue, widths[1], m, Color.rgb(91, 109, 145)));
        card.addView(barRow("Career", careerValue, widths[2], m, SALMON));
    }

    private TextView deltaPill(String text, int color) {
        TextView tv = text(text, 12, color, true);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(6), dp(8), dp(6), dp(8));
        tv.setBackground(roundedStroke(Color.WHITE, Color.rgb(227, 233, 242), 14, 1));
        return tv;
    }

    private View barRow(String label, Double value, double width, Metric m, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        LinearLayout textRow = new LinearLayout(this);
        textRow.setOrientation(LinearLayout.HORIZONTAL);
        textRow.addView(text(label, 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1));
        textRow.addView(text(format(value, m), 12, INK, true));
        row.addView(textRow);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(1000);
        pb.setProgress((int) Math.round(width * 10));
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(235, 240, 247)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(9));
        lp.setMargins(0, dp(4), 0, 0);
        row.addView(pb, lp);
        return row;
    }

    private void copyLastComparison() {
        if (lastComparison == null) return;
        StringBuilder sb = new StringBuilder();
        Comparison c = lastComparison;
        sb.append(c.player.fullName).append(" · ").append(c.season).append(" Statcast comparison\n");
        sb.append("Metric\tSeason\tLeague Avg\tCareer\tVs League\tVs Career\n");
        for (Metric m : metrics) {
            Double season = c.seasonStats.get(m.key);
            Double league = c.leagueStats == null ? null : c.leagueStats.get(m.key);
            Double career = c.careerStats.get(m.key);
            sb.append(m.label).append('\t')
                    .append(format(season, m)).append('\t')
                    .append(format(league, m)).append('\t')
                    .append(format(career, m)).append('\t')
                    .append(signedFormat(diff(season, league), m)).append('\t')
                    .append(signedFormat(diff(season, career), m)).append('\n');
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Statcast comparison", sb.toString()));
        Toast.makeText(this, "Copied comparison table", Toast.LENGTH_SHORT).show();
    }

    // Data loading -----------------------------------------------------------------------------

    private ArrayList<Player> fetchActivePlayers() throws Exception {
        String teamsText = httpGet("https://statsapi.mlb.com/api/v1/teams?sportId=1&activeStatus=Y");
        JSONArray teams = new JSONObject(teamsText).optJSONArray("teams");
        LinkedHashMap<Integer, Player> byId = new LinkedHashMap<>();
        if (teams == null) return new ArrayList<>();

        for (int i = 0; i < teams.length(); i++) {
            JSONObject team = teams.getJSONObject(i);
            int teamId = team.optInt("id");
            String teamName = team.optString("name", "MLB");
            String[] rosterTypes = new String[] { "active", "40Man" };
            for (String rosterType : rosterTypes) {
                try {
                    String rosterText = httpGet("https://statsapi.mlb.com/api/v1/teams/" + teamId + "/roster/" + rosterType + "?hydrate=person");
                    JSONArray roster = new JSONObject(rosterText).optJSONArray("roster");
                    if (roster == null || roster.length() == 0) continue;
                    for (int j = 0; j < roster.length(); j++) {
                        JSONObject item = roster.getJSONObject(j);
                        JSONObject person = item.optJSONObject("person");
                        JSONObject pos = item.optJSONObject("position");
                        if (person == null) continue;
                        int id = person.optInt("id");
                        String name = person.optString("fullName", "");
                        if (id > 0 && !name.isEmpty()) {
                            String position = pos == null ? "" : pos.optString("abbreviation", pos.optString("name", ""));
                            byId.put(id, new Player(id, name, teamName, position));
                        }
                    }
                    break;
                } catch (Exception ignored) {}
            }
        }
        ArrayList<Player> players = new ArrayList<>(byId.values());
        players.sort((a, b) -> a.fullName.compareToIgnoreCase(b.fullName));
        return players;
    }

    private List<Map<String, String>> fetchPlayerRowsForSeason(int playerId, int season) throws Exception {
        String startDate = season + "-03-01";
        String endDate = season == Calendar.getInstance().get(Calendar.YEAR) ? todayIso() : season + "-11-30";
        String csv = httpGet(statcastSearchUrl(playerId, startDate, endDate, season));
        List<Map<String, String>> rows = parseCsv(csv);
        if (!rows.isEmpty() && !rows.get(0).containsKey("game_date")) {
            throw new Exception("Unexpected Baseball Savant CSV format.");
        }
        return rows;
    }

    private List<Map<String, String>> fetchPlayerCareerRows(int playerId, int throughSeason) {
        ArrayList<Map<String, String>> all = new ArrayList<>();
        for (int year = STATCAST_START_YEAR; year <= throughSeason; year++) {
            try { all.addAll(fetchPlayerRowsForSeason(playerId, year)); }
            catch (Exception ignored) {}
        }
        return all;
    }

    private Stats fetchLeagueAverage(int season) {
        try {
            LinkedHashMap<String, String> params = new LinkedHashMap<>();
            params.put("year", String.valueOf(season));
            params.put("type", "batter");
            params.put("filter", "");
            params.put("min", "1");
            params.put("selections", "pa,xba,xslg,woba,xwoba,exit_velocity_avg,launch_angle_avg,sweet_spot_percent,barrel_batted_rate,hard_hit_percent");
            params.put("sort", "xwoba");
            params.put("sortDir", "desc");
            params.put("csv", "true");
            String csv = httpGet("https://baseballsavant.mlb.com/leaderboard/custom" + toQuery(params));
            List<Map<String, String>> rows = parseCsv(csv);
            if (rows.isEmpty()) return null;

            double paTotal = 0, bbeTotal = 0;
            Map<String, Double> paWeighted = new HashMap<>();
            Map<String, Double> bbeWeighted = new HashMap<>();
            for (Map<String, String> row : rows) {
                double pa = nn(pick(row, "pa", "PA"));
                double bbe = nn(pick(row, "batted_ball", "batted_balls", "batted balls", "bbe", "Batted Balls"));
                if (bbe <= 0) bbe = pa;
                String[] expKeys = { "xBA", "xSLG", "xwOBA" };
                String[][] expCols = {
                        { "xba", "xBA" }, { "xslg", "xSLG" }, { "xwoba", "xwOBA" }
                };
                for (int i = 0; i < expKeys.length; i++) {
                    Double val = pick(row, expCols[i]);
                    if (val != null && pa > 0) paWeighted.put(expKeys[i], paWeighted.getOrDefault(expKeys[i], 0.0) + val * pa);
                }
                String[][] contact = {
                        { "avgEV", "exit_velocity_avg", "Avg EV (MPH)", "avg_ev" },
                        { "avgLA", "launch_angle_avg", "Avg LA (°)", "avg_la" },
                        { "hardHitPct", "hard_hit_percent", "Hard Hit %" },
                        { "barrelPct", "barrel_batted_rate", "Barrel%", "barrel_percent" },
                        { "sweetSpotPct", "sweet_spot_percent", "LA Sweet-Spot %" }
                };
                for (String[] def : contact) {
                    Double val = pick(row, Arrays.copyOfRange(def, 1, def.length));
                    if (val != null && bbe > 0) bbeWeighted.put(def[0], bbeWeighted.getOrDefault(def[0], 0.0) + val * bbe);
                }
                if (pa > 0) paTotal += pa;
                if (bbe > 0) bbeTotal += bbe;
            }
            Stats s = new Stats();
            s.pa = (int) Math.round(paTotal);
            s.bbe = (int) Math.round(bbeTotal);
            for (String key : new String[] { "xBA", "xSLG", "xwOBA" }) s.put(key, paTotal > 0 ? paWeighted.getOrDefault(key, 0.0) / paTotal : null);
            for (String key : new String[] { "avgEV", "avgLA", "hardHitPct", "barrelPct", "sweetSpotPct" }) s.put(key, bbeTotal > 0 ? bbeWeighted.getOrDefault(key, 0.0) / bbeTotal : null);
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private String statcastSearchUrl(int playerId, String startDate, String endDate, int season) throws Exception {
        LinkedHashMap<String, String> p = new LinkedHashMap<>();
        p.put("all", "true"); p.put("hfPT", ""); p.put("hfAB", ""); p.put("hfGT", "R|"); p.put("hfPR", ""); p.put("hfZ", "");
        p.put("stadium", ""); p.put("hfBBT", ""); p.put("hfNewZones", ""); p.put("hfPull", ""); p.put("hfC", ""); p.put("hfSea", season + "|");
        p.put("hfSit", ""); p.put("player_type", "batter"); p.put("hfOuts", ""); p.put("opponent", ""); p.put("pitcher_throws", "");
        p.put("batter_stands", ""); p.put("hfSA", ""); p.put("game_date_gt", startDate); p.put("game_date_lt", endDate); p.put("team", "");
        p.put("position", ""); p.put("hfRO", ""); p.put("home_road", ""); p.put("hfFlag", ""); p.put("hfBBL", ""); p.put("metric_1", "");
        p.put("metric_1_gt", ""); p.put("metric_1_lt", ""); p.put("metric_2", ""); p.put("metric_2_gt", ""); p.put("metric_2_lt", "");
        p.put("group_by", "name"); p.put("min_pitches", "0"); p.put("min_results", "0"); p.put("min_pas", "0");
        p.put("sort_col", "pitches"); p.put("player_event_sort", "h_launch_speed"); p.put("sort_order", "desc"); p.put("type", "details");
        return "https://baseballsavant.mlb.com/statcast_search/csv" + toQuery(p) + "&" + enc("batters_lookup[]") + "=" + playerId;
    }

    private String httpGet(String urlString) throws Exception {
        String cached = textCache.get(urlString);
        if (cached != null) return cached;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 Statcast Compare Android");
        conn.setRequestProperty("Accept", "text/csv,text/plain,application/json,text/html,*/*");
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        String text = sb.toString();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        textCache.put(urlString, text);
        return text;
    }

    // Stat calculations -------------------------------------------------------------------------

    private Stats summarizeRawRows(List<Map<String, String>> rows) {
        ArrayList<Map<String, String>> paRows = new ArrayList<>();
        for (Map<String, String> r : rows) if (!string(r.get("events")).trim().isEmpty()) paRows.add(r);
        ArrayList<Map<String, String>> bbeRows = new ArrayList<>();
        for (Map<String, String> r : paRows) if (num(r.get("launch_speed")) != null && num(r.get("launch_angle")) != null) bbeRows.add(r);

        double evSum = 0, laSum = 0;
        int hardHit = 0, sweetSpot = 0, barrels = 0;
        for (Map<String, String> r : bbeRows) {
            double ev = nn(num(r.get("launch_speed")));
            double la = nn(num(r.get("launch_angle")));
            evSum += ev; laSum += la;
            if (ev >= 95) hardHit++;
            if (la >= 8 && la <= 32) sweetSpot++;
            if ("6".equals(string(r.get("launch_speed_angle")).trim())) barrels++;
        }

        int ab = 0;
        double xHits = 0, xSlgBases = 0, xwobaNum = 0, xwobaDen = 0;
        for (Map<String, String> r : paRows) {
            Double wobaDen = num(r.get("woba_denom"));
            Double xwoba = firstNumber(r, "estimated_woba_using_speedangle", "estimated_woba_using_speedangle ");
            Double actualWoba = num(r.get("woba_value"));
            if (wobaDen != null && wobaDen > 0) {
                xwobaDen += wobaDen;
                xwobaNum += (xwoba != null ? xwoba : (actualWoba == null ? 0 : actualWoba)) * wobaDen;
            }
            if (isAtBatEvent(r.get("events"))) {
                ab++;
                xHits += nn(firstNumber(r, "estimated_ba_using_speedangle"));
                xSlgBases += nn(firstNumber(r, "estimated_slg_using_speedangle"));
            }
        }

        Stats s = new Stats();
        s.pa = paRows.size();
        s.bbe = bbeRows.size();
        s.put("avgEV", s.bbe > 0 ? evSum / s.bbe : null);
        s.put("avgLA", s.bbe > 0 ? laSum / s.bbe : null);
        s.put("hardHitPct", s.bbe > 0 ? hardHit * 100.0 / s.bbe : null);
        s.put("barrelPct", s.bbe > 0 ? barrels * 100.0 / s.bbe : null);
        s.put("sweetSpotPct", s.bbe > 0 ? sweetSpot * 100.0 / s.bbe : null);
        s.put("xBA", ab > 0 ? xHits / ab : null);
        s.put("xSLG", ab > 0 ? xSlgBases / ab : null);
        s.put("xwOBA", xwobaDen > 0 ? xwobaNum / xwobaDen : null);
        return s;
    }

    private boolean isAtBatEvent(String event) {
        String e = string(event).toLowerCase(Locale.US);
        return !(e.equals("walk") || e.equals("hit_by_pitch") || e.equals("sac_bunt") || e.equals("sac_fly") || e.equals("catcher_interf") || e.equals("intent_walk") || e.equals("sac_fly_double_play") || e.equals("sac_bunt_double_play"));
    }

    // CSV/format helpers ------------------------------------------------------------------------

    private List<Map<String, String>> parseCsv(String text) {
        ArrayList<ArrayList<String>> rows = new ArrayList<>();
        ArrayList<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (c == '"') {
                if (inQuotes && next == '"') { field.append('"'); i++; }
                else inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                row.add(field.toString()); field.setLength(0);
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r' && next == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                boolean nonEmpty = false; for (String v : row) if (!v.isEmpty()) { nonEmpty = true; break; }
                if (nonEmpty) rows.add(row);
                row = new ArrayList<>();
            } else field.append(c);
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        ArrayList<Map<String, String>> out = new ArrayList<>();
        if (rows.size() < 2) return out;
        ArrayList<String> headers = rows.get(0);
        for (int r = 1; r < rows.size(); r++) {
            HashMap<String, String> map = new HashMap<>();
            ArrayList<String> vals = rows.get(r);
            for (int h = 0; h < headers.size(); h++) map.put(headers.get(h).trim(), h < vals.size() ? vals.get(h) : "");
            out.add(map);
        }
        return out;
    }

    private String toQuery(LinkedHashMap<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private String enc(String s) throws Exception { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
    private String todayIso() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
    private String string(String s) { return s == null ? "" : s; }
    private Double num(String value) {
        if (value == null) return null;
        String cleaned = value.replace("%", "").trim();
        if (cleaned.isEmpty()) return null;
        try { return Double.parseDouble(cleaned); } catch (Exception e) { return null; }
    }
    private double nn(Double d) { return d == null || Double.isNaN(d) ? 0 : d; }
    private Double firstNumber(Map<String, String> row, String... keys) {
        for (String k : keys) { Double d = num(row.get(k)); if (d != null) return d; }
        return null;
    }
    private Double pick(Map<String, String> row, String... names) {
        for (String n : names) if (row.containsKey(n)) return num(row.get(n));
        HashMap<String, String> lowerMap = new HashMap<>();
        for (String k : row.keySet()) lowerMap.put(k.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""), k);
        for (String n : names) {
            String k = lowerMap.get(n.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""));
            if (k != null) return num(row.get(k));
        }
        return null;
    }

    // UI helpers --------------------------------------------------------------------------------

    private void setBusy(boolean busy, String message) {
        loading.setVisibility(busy ? View.VISIBLE : View.GONE);
        compareButton.setEnabled(!busy);
        if (message != null) selectedLabel.setText(message);
    }
    private void showError(String message) {
        errorView.setText(message == null ? "" : message);
        errorView.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }
    private LinearLayout verticalCard(int radius, int[] gradientColors) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setBackground(gradientColors == null ? rounded(CARD, radius) : roundedGradient(gradientColors, radius));
        v.setElevation(dp(2));
        return v;
    }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams weightLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        return lp;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(dp(radius));
        return gd;
    }
    private GradientDrawable roundedStroke(int color, int stroke, int radius, int strokeDp) {
        GradientDrawable gd = rounded(color, radius);
        gd.setStroke(dp(strokeDp), stroke);
        return gd;
    }
    private GradientDrawable roundedGradient(int[] colors, int radius) {
        GradientDrawable gd = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        gd.setCornerRadius(dp(radius));
        return gd;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private String fmtCount(int v) { return String.format(Locale.US, "%,d", v); }
    private Double diff(Double a, Double b) { return a == null || b == null ? null : a - b; }
    private int deltaColor(Double delta, Metric m) {
        if (delta == null || m.higherGood == null || Math.abs(delta) < 0.0005) return MUTED;
        boolean good = m.higherGood ? delta > 0 : delta < 0;
        return good ? Color.rgb(22, 132, 91) : Color.rgb(188, 64, 78);
    }
    private String signedFormat(Double v, Metric m) {
        if (v == null || Double.isNaN(v)) return "—";
        return (v > 0 ? "+" : "") + format(v, m);
    }
    private String format(Double v, Metric m) {
        if (v == null || Double.isNaN(v)) return "—";
        String pattern = m.decimals == 3 ? "0.000" : "0.0";
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(v) + m.unit;
    }
    private double[] scaleValues(Double[] values, Metric m) {
        ArrayList<Double> valid = new ArrayList<>();
        for (Double v : values) if (v != null && !Double.isNaN(v)) valid.add(v);
        double[] out = new double[values.length];
        if (valid.isEmpty()) return out;
        double min = Collections.min(valid), max = Collections.max(valid);
        if (max == min) { for (int i = 0; i < out.length; i++) out[i] = values[i] == null ? 0 : 70; return out; }
        double pad = m.type.equals("expected") ? 0.020 : m.type.equals("rate") ? 2.0 : 0.5;
        double lo = min - pad, hi = max + pad;
        for (int i = 0; i < values.length; i++) {
            Double v = values[i];
            if (v == null || Double.isNaN(v)) out[i] = 0;
            else out[i] = Math.max(7, Math.min(100, ((v - lo) / (hi - lo)) * 100));
        }
        return out;
    }

    static class Player {
        final int id; final String fullName; final String team; final String position;
        Player(int id, String fullName, String team, String position) { this.id = id; this.fullName = fullName; this.team = team; this.position = position; }
    }
    static class Stats {
        int pa = 0; int bbe = 0; final Map<String, Double> vals = new HashMap<>();
        void put(String key, Double value) { vals.put(key, value); }
        Double get(String key) { return vals.get(key); }
    }
    static class Metric {
        final String key, label, unit, type; final int decimals; final Boolean higherGood;
        Metric(String key, String label, String unit, int decimals, Boolean higherGood, String type) {
            this.key = key; this.label = label; this.unit = unit; this.decimals = decimals; this.higherGood = higherGood; this.type = type;
        }
    }
    static class Comparison {
        final Player player; final int season; final Stats seasonStats, careerStats, leagueStats; final Date updated;
        Comparison(Player p, int s, Stats seasonStats, Stats careerStats, Stats leagueStats, Date updated) {
            this.player = p; this.season = s; this.seasonStats = seasonStats; this.careerStats = careerStats; this.leagueStats = leagueStats; this.updated = updated;
        }
    }
}
