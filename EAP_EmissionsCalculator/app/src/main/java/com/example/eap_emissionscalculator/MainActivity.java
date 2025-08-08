package com.example.eap_emissionscalculator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static class EnergySource {
        String name;
        String unit;
        double energyFactor;
        double emissionFactor;

        EnergySource(String name, String unit, double energyFactor, double emissionFactor) {
            this.name = name;
            this.unit = unit;
            this.energyFactor = energyFactor;
            this.emissionFactor = emissionFactor;
        }
    }

    private static class CalculationRecord {
        EnergySource source;
        double quantity;
        double energy;
        double emissions;
        EditText quantityEditText;
        TextView energyTextView;
        TextView emissionsTextView;

        CalculationRecord(EnergySource source, double quantity) {
            this.source = source;
            this.quantity = quantity;
            this.energy = quantity * source.energyFactor;
            this.emissions = quantity * source.emissionFactor;
        }
    }

    private static final int STORAGE_PERMISSION_CODE = 100;

    private Spinner energySourceSpinner;
    private TextInputEditText quantityInput;
    private TextInputLayout quantityInputLayout;
    private TextView totalEnergyTextView, totalEmissionsTextView;
    private MaterialButton calculateButton, exportButton, resetButton, editTableButton;
    private TableLayout resultsTable;
    private HorizontalScrollView tableHorizontalScrollView;
    private ScrollView tableVerticalScrollView;
    private ImageView scrollLeftButton, scrollRightButton;
    private TextView tableNavigationHint;

    private final List<EnergySource> energySources = new ArrayList<>();
    private final List<CalculationRecord> calculationHistory = new ArrayList<>();
    private double totalEnergy = 0.0;
    private double totalEmissions = 0.0;
    private Handler scrollHandler = new Handler();
    private Runnable scrollRunnable;
    private boolean isEditMode = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scrollView), (v, insets) -> {
            int systemBars = insets.getSystemWindowInsetTop();
            v.setPadding(v.getPaddingLeft(), systemBars,
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        initializeEnergySources();
        initializeViews();
        setupSpinner();
        setupTable();
        setupButtonListeners();
        setupTableScrollListeners();
    }

    private void initializeEnergySources() {
        energySources.add(new EnergySource("Природен газ", "м³", 9.3, 1.9));
        energySources.add(new EnergySource("Нафта", "л", 10.00, 2.70));
        energySources.add(new EnergySource("Пропан-бутан", "л", 7.30, 1.7));
        energySources.add(new EnergySource("Черни каменни въглища", "kg", 5.80, 2.0));
        energySources.add(new EnergySource("Антрацитни въглища", "kg", 8.6, 3));
        energySources.add(new EnergySource("Брикети от кафяви въглища", "kg", 5.60, 2));
        energySources.add(new EnergySource("Кафяви въглища", "kg", 2.9, 1.1));
        energySources.add(new EnergySource("Литнитни/кафяви каменни въглища", "kg", 3.7, 1.4));
        energySources.add(new EnergySource("Дървени пелети, брикети", "kg", 4.70, 0.20));
        energySources.add(new EnergySource("Иглолистна дървесина", "m³", 1358, 58.4));
        energySources.add(new EnergySource("Широколистна дървесина", "m³", 1940, 83.4));
        energySources.add(new EnergySource("Електричество", "kWh", 1.00, 0.8));
        energySources.add(new EnergySource("Топлина от централизирано топлоснабдяване", "kWh", 1, 0.3));
    }

    private void initializeViews() {
        energySourceSpinner = findViewById(R.id.energySourceSpinner);
        quantityInput = findViewById(R.id.quantityInput);
        quantityInputLayout = findViewById(R.id.quantityInputLayout);
        totalEnergyTextView = findViewById(R.id.totalEnergyTextView);
        totalEmissionsTextView = findViewById(R.id.totalEmissionsTextView);
        calculateButton = findViewById(R.id.calculateButton);
        exportButton = findViewById(R.id.exportButton);
        resetButton = findViewById(R.id.resetButton);
        editTableButton = findViewById(R.id.editTableButton);
        resultsTable = findViewById(R.id.resultsTable);
        tableHorizontalScrollView = findViewById(R.id.tableHorizontalScrollView);
        tableVerticalScrollView = findViewById(R.id.tableVerticalScrollView);
        scrollLeftButton = findViewById(R.id.scrollLeftButton);
        scrollRightButton = findViewById(R.id.scrollRightButton);
        tableNavigationHint = findViewById(R.id.tableNavigationHint);
    }

    private void setupSpinner() {
        List<String> sourceNames = new ArrayList<>();
        for (EnergySource source : energySources) {
            sourceNames.add(source.name + " (" + source.unit + ")");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, R.layout.spinner_item, sourceNames);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        energySourceSpinner.setAdapter(adapter);

        energySourceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                quantityInput.setText("");
                calculateButton.setEnabled(true);
                ((TextView) view).setTextColor(ContextCompat.getColor(MainActivity.this, R.color.dark_blue));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupTable() {
        resultsTable.removeAllViews();

        TableRow headerRow = new TableRow(this);
        headerRow.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_blue));

        String[] headers = {"Източник", "Количество", "Ед.", "Енергия (kWh)", "CO₂ (kg)"};
        for (String header : headers) {
            TextView textView = createTableCell(header, true);
            headerRow.addView(textView);
        }

        resultsTable.addView(headerRow);
    }

    private TextView createTableCell(String text, boolean isHeader) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setPadding(24, 16, 24, 16);
        textView.setTextSize(14);
        textView.setGravity(Gravity.CENTER);
        textView.setSingleLine(false);

        if (isHeader) {
            textView.setTextColor(Color.WHITE);
            textView.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            textView.setTextColor(ContextCompat.getColor(this, R.color.dark_blue));
            textView.setBackgroundResource(R.drawable.cell_border);
        }

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(1, 1, 1, 1);
        textView.setLayoutParams(params);

        return textView;
    }

    private void setupButtonListeners() {
        calculateButton.setOnClickListener(v -> calculateEmissions());
        exportButton.setOnClickListener(v -> handleExport());
        resetButton.setOnClickListener(v -> resetCalculator());
        editTableButton.setOnClickListener(v -> toggleEditMode());

        scrollLeftButton.setOnClickListener(v -> {
            tableHorizontalScrollView.smoothScrollBy(-200, 0);
            checkScrollButtonsVisibility();
        });

        scrollRightButton.setOnClickListener(v -> {
            tableHorizontalScrollView.smoothScrollBy(200, 0);
            checkScrollButtonsVisibility();
        });
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;

        if (isEditMode) {
            editTableButton.setText("Потвърди промените");
            editTableButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.green)));
            enableTableEditing();
            tableNavigationHint.setText("Редактиране на данните е активирано");
        } else {
            editTableButton.setText("Редактиране");
            editTableButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dark_blue)));
            disableTableEditing();
            tableNavigationHint.setText("Дръпнете за навигация в таблицата");
            saveTableChanges();

            new Handler().postDelayed(() -> {
                tableVerticalScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                tableHorizontalScrollView.fullScroll(HorizontalScrollView.FOCUS_LEFT);
            }, 100);
        }
    }

    private void enableTableEditing() {
        for (CalculationRecord record : calculationHistory) {
            if (record.quantityEditText != null) {
                record.quantityEditText.setEnabled(true);
                record.quantityEditText.setFocusable(true);
                record.quantityEditText.setFocusableInTouchMode(true);
            }
        }
    }

    private void disableTableEditing() {
        for (CalculationRecord record : calculationHistory) {
            if (record.quantityEditText != null) {
                record.quantityEditText.setEnabled(false);
                record.quantityEditText.setFocusable(false);
                record.quantityEditText.setFocusableInTouchMode(false);
                record.quantityEditText.clearFocus();
            }
        }
    }

    private void saveTableChanges() {
        for (CalculationRecord record : calculationHistory) {
            if (record.quantityEditText != null && record.quantityEditText.getText().toString().trim().isEmpty()) {
                record.quantityEditText.setText("0");
                record.quantity = 0;
                record.energy = 0;
                record.emissions = 0;
                record.energyTextView.setText("0.00");
                record.emissionsTextView.setText("0.00");
            }
        }

        recalculateTotals();
        Toast.makeText(this, "Промените са запазени!", Toast.LENGTH_SHORT).show();
    }

    private void setupTableScrollListeners() {
        tableHorizontalScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            checkScrollButtonsVisibility();
        });

        scrollRunnable = new Runnable() {
            @Override
            public void run() {
                checkScrollButtonsVisibility();
                scrollHandler.postDelayed(this, 100);
            }
        };
        scrollHandler.postDelayed(scrollRunnable, 100);
    }

    private void checkScrollButtonsVisibility() {
        int scrollX = tableHorizontalScrollView.getScrollX();
        int maxScrollX = tableHorizontalScrollView.getChildAt(0).getWidth() - tableHorizontalScrollView.getWidth();

        scrollLeftButton.setVisibility(scrollX > 0 ? View.VISIBLE : View.GONE);
        scrollRightButton.setVisibility(scrollX < maxScrollX - 10 ? View.VISIBLE : View.GONE);
    }

    private void calculateEmissions() {
        try {
            String quantityStr = quantityInput.getText().toString();
            if (quantityStr.isEmpty()) {
                quantityInputLayout.setError("Моля въведете количество");
                return;
            }
            double quantity = Double.parseDouble(quantityStr);

            if (quantity <= 0) {
                quantityInputLayout.setError("Въведете положително число");
                return;
            }
            quantityInputLayout.setError(null);

            int selectedPosition = energySourceSpinner.getSelectedItemPosition();
            EnergySource source = energySources.get(selectedPosition);

            CalculationRecord record = new CalculationRecord(source, quantity);
            calculationHistory.add(record);
            addTableRow(record);

            totalEnergy += record.energy;
            totalEmissions += record.emissions;
            updateTotals();

            new Handler().postDelayed(() -> {
                tableHorizontalScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT);
                tableVerticalScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }, 100);

            Toast.makeText(this, "Изчислението е добавено!", Toast.LENGTH_SHORT).show();
            quantityInput.setText("");
            quantityInput.requestFocus();

        } catch (NumberFormatException e) {
            quantityInputLayout.setError("Моля въведете валидно количество");
        }
    }

    private void addTableRow(CalculationRecord record) {
        TableRow row = new TableRow(this);
        int position = calculationHistory.size() - 1;

        TextView sourceView = createTableCell(record.source.name, false);
        row.addView(sourceView);

        EditText quantityView = new EditText(this);
        setupEditableCell(quantityView, String.format(Locale.getDefault(), "%.2f", record.quantity), position);
        record.quantityEditText = quantityView;
        quantityView.setFocusable(false);
        quantityView.setFocusableInTouchMode(false);
        quantityView.setEnabled(false);
        row.addView(quantityView);

        TextView unitView = createTableCell(record.source.unit, false);
        row.addView(unitView);

        TextView energyView = createTableCell(String.format(Locale.getDefault(), "%.2f", record.energy), false);
        record.energyTextView = energyView;
        row.addView(energyView);

        TextView emissionsView = createTableCell(String.format(Locale.getDefault(), "%.2f", record.emissions), false);
        record.emissionsTextView = emissionsView;
        row.addView(emissionsView);

        resultsTable.addView(row);
    }

    private void setupEditableCell(EditText editText, String initialValue, final int position) {
        editText.setText(initialValue);
        editText.setPadding(24, 16, 24, 16);
        editText.setTextSize(14);
        editText.setGravity(Gravity.CENTER);
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        editText.setTextColor(ContextCompat.getColor(this, R.color.dark_blue));
        editText.setBackgroundResource(R.drawable.cell_border);

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                TableRow.LayoutParams.WRAP_CONTENT,
                TableRow.LayoutParams.WRAP_CONTENT,
                1f
        );
        params.setMargins(1, 1, 1, 1);
        editText.setLayoutParams(params);

        editText.addTextChangedListener(new TextWatcher() {
            private Runnable updateRunnable;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (updateRunnable != null) {
                    editText.removeCallbacks(updateRunnable);
                }

                updateRunnable = () -> {
                    String newQuantityStr = s.toString().trim();
                    double newQuantity = 0.0;

                    if (!newQuantityStr.isEmpty()) {
                        try {
                            newQuantity = Double.parseDouble(newQuantityStr);
                            if (newQuantity < 0) return;
                        } catch (NumberFormatException e) {
                            return;
                        }
                    }

                    CalculationRecord record = calculationHistory.get(position);

                    // Only update if the value has actually changed
                    if (record.quantity != newQuantity) {
                        record.quantity = newQuantity;
                        record.energy = newQuantity * record.source.energyFactor;
                        record.emissions = newQuantity * record.source.emissionFactor;

                        record.energyTextView.setText(String.format(Locale.getDefault(), "%.2f", record.energy));
                        record.emissionsTextView.setText(String.format(Locale.getDefault(), "%.2f", record.emissions));

                        recalculateTotals();
                    }
                };

                editText.postDelayed(updateRunnable, 300);
            }
        });
    }

    private void recalculateTotals() {
        totalEnergy = 0.0;
        totalEmissions = 0.0;

        for (CalculationRecord record : calculationHistory) {
            totalEnergy += record.energy;
            totalEmissions += record.emissions;
        }

        // Ensure we always show two decimal places
        totalEnergy = Math.round(totalEnergy * 100) / 100.0;
        totalEmissions = Math.round(totalEmissions * 100) / 100.0;

        updateTotals();
    }
    private void updateTotals() {
        runOnUiThread(() -> {
            totalEnergyTextView.setText(String.format(Locale.getDefault(), "%.2f", totalEnergy));
            totalEmissionsTextView.setText(String.format(Locale.getDefault(), "%.2f", totalEmissions));
        });
    }

    private void handleExport() {
        if (calculationHistory.isEmpty()) {
            Toast.makeText(this, "Няма данни за експорт", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Избор на място за запис")
                .setMessage("Къде искате да запишете CSV файла?")
                .setPositiveButton("Външно хранилище", (dialog, which) -> {
                    if (checkOrRequestStoragePermission()) {
                        saveToExternalStorage();
                    }
                })
                .setNegativeButton("Локално в приложението", (dialog, which) -> {
                    saveToInternalStorage();
                })
                .setNeutralButton("Отказ", null)
                .show();
    }

    private boolean checkOrRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
            return false;
        }
    }

    private void saveToExternalStorage() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "CO2_Emissions_" + timeStamp + ".csv";

            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File emissionsDir = new File(downloadsDir, "Emissions");
            if (!emissionsDir.exists()) {
                emissionsDir.mkdirs();
            }

            File outputFile = new File(emissionsDir, fileName);
            writeCsvContent(outputFile);

            Toast.makeText(this,
                    "Файлът е запазен в: Downloads/Emissions/" + fileName,
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Грешка при запис: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("FileError", "Грешка при запис във външно хранилище", e);
        }
    }

    private void saveToInternalStorage() {
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "CO2_Emissions_" + timeStamp + ".csv";

            File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Emissions");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            File outputFile = new File(outputDir, fileName);
            writeCsvContent(outputFile);

            Toast.makeText(this,
                    "Файлът е запазен в локалното хранилище на приложението",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Грешка при запис: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("FileError", "Грешка при запис във вътрешно хранилище", e);
        }
    }

    private void writeCsvContent(File outputFile) throws IOException {
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Източник,Количество,Ед.,Енергия (kWh),CO₂ (kg)\n");

        for (CalculationRecord record : calculationHistory) {
            csvContent.append(escapeCsv(record.source.name)).append(",")
                    .append(record.quantity).append(",")
                    .append(record.source.unit).append(",")
                    .append(record.energy).append(",")
                    .append(record.emissions).append("\n");
        }

        csvContent.append("ОБЩО,,,").append(totalEnergy).append(",").append(totalEmissions);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(csvContent.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private String escapeCsv(String content) {
        if (content == null) return "";
        if (content.contains(",") || content.contains("\n")) {
            return "\"" + content.replace("\"", "\"\"") + "\"";
        }
        return content;
    }

    private void resetCalculator() {
        calculationHistory.clear();
        setupTable();
        totalEnergy = 0.0;
        totalEmissions = 0.0;
        totalEnergyTextView.setText("0.00");
        totalEmissionsTextView.setText("0.00");
        quantityInput.setText("");
        quantityInputLayout.setError(null);
        calculateButton.setEnabled(true);
        Toast.makeText(this, "Калкулаторът е нулиран", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveToExternalStorage();
            } else {
                Toast.makeText(this,
                        "Експортът няма да бъде възможен без разрешение за запис",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        scrollHandler.removeCallbacks(scrollRunnable);
    }
}