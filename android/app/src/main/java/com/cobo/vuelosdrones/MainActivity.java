package com.cobo.vuelosdrones;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * App de Vuelos Drones: abre el dashboard publicado en GitHub Pages.
 * Los cambios que se suben al index.html se ven en la app sin reinstalarla.
 */
public class MainActivity extends Activity {

    private static final String URL_APP = "https://cobocuburjosue-glitch.github.io/VUELOS-DRONES/";
    private static final String HOST_APP = "cobocuburjosue-glitch.github.io";
    private static final int PEDIR_ARCHIVO = 7;

    private WebView web;
    private TextView sinConexion;
    private ProgressBar cargando;
    private ValueCallback<Uri[]> esperaArchivo;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);

        FrameLayout raiz = new FrameLayout(this);
        raiz.setBackgroundColor(Color.parseColor("#141A13"));

        web = new WebView(this);
        raiz.addView(web, new FrameLayout.LayoutParams(-1, -1));

        cargando = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        cargando.setMax(100);
        FrameLayout.LayoutParams lpBarra = new FrameLayout.LayoutParams(-1, 8);
        lpBarra.gravity = Gravity.TOP;
        raiz.addView(cargando, lpBarra);

        sinConexion = new TextView(this);
        sinConexion.setText("No hay conexión a internet.\n\nToca aquí para intentar otra vez.");
        sinConexion.setTextColor(Color.WHITE);
        sinConexion.setTextSize(18);
        sinConexion.setGravity(Gravity.CENTER);
        sinConexion.setPadding(48, 48, 48, 48);
        sinConexion.setBackgroundColor(Color.parseColor("#2F5D34"));
        sinConexion.setVisibility(View.GONE);
        sinConexion.setOnClickListener(v -> { sinConexion.setVisibility(View.GONE); web.reload(); });
        raiz.addView(sinConexion, new FrameLayout.LayoutParams(-1, -1));

        setContentView(raiz);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString() + " VuelosDronesApp/1");

        web.addJavascriptInterface(new Puente(), "AppAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return abrirFuera(r.getUrl());
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) sinConexion.setVisibility(View.VISIBLE);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                cargando.setProgress(p);
                cargando.setVisibility(p < 100 ? View.VISIBLE : View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (esperaArchivo != null) esperaArchivo.onReceiveValue(null);
                esperaArchivo = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel", "text/csv", "text/comma-separated-values",
                        "application/octet-stream"});
                try {
                    startActivityForResult(Intent.createChooser(i, "Elegir Excel de DJI"), PEDIR_ARCHIVO);
                } catch (Exception ex) {
                    esperaArchivo = null;
                    return false;
                }
                return true;
            }
        });

        web.setDownloadListener((url, agente, disposicion, tipo, largo) -> {
            if (url.startsWith("http")) abrirFuera(Uri.parse(url));
        });

        if (estado != null) web.restoreState(estado);
        else web.loadUrl(URL_APP);
    }

    /** Las páginas del dashboard se quedan en la app; lo demás (Drive, correo) se abre afuera. */
    private boolean abrirFuera(Uri u) {
        String esquema = u.getScheme();
        if (("https".equals(esquema) || "http".equals(esquema)) && HOST_APP.equals(u.getHost())) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception e) {
            Toast.makeText(this, "No hay una app para abrir ese enlace.", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent datos) {
        super.onActivityResult(pedido, resultado, datos);
        if (pedido != PEDIR_ARCHIVO || esperaArchivo == null) return;
        Uri[] r = null;
        if (resultado == RESULT_OK && datos != null && datos.getData() != null) r = new Uri[]{datos.getData()};
        esperaArchivo.onReceiveValue(r);
        esperaArchivo = null;
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle salida) {
        super.onSaveInstanceState(salida);
        web.saveState(salida);
    }

    private void guardarArchivo(String base64, String nombre, String tipo) {
        try {
            byte[] datos = Base64.decode(base64, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
                cv.put(MediaStore.Downloads.MIME_TYPE, tipo);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                try (OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(datos); }
                Toast.makeText(this, "Guardado en Descargas: " + nombre, Toast.LENGTH_LONG).show();
            } else {
                File carpeta = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                File f = new File(carpeta, nombre);
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(datos); }
                Toast.makeText(this, "Guardado en " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar el archivo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Funciones que el dashboard llama desde JavaScript (window.AppAndroid). */
    private class Puente {
        @JavascriptInterface
        public boolean esApp() { return true; }

        @JavascriptInterface
        public void guardarArchivo(String base64, String nombre, String tipo) {
            runOnUiThread(() -> MainActivity.this.guardarArchivo(base64, nombre, tipo));
        }

        @JavascriptInterface
        public void imprimir(String titulo) {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                pm.print(titulo, web.createPrintDocumentAdapter(titulo),
                        new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
            });
        }
    }
}
