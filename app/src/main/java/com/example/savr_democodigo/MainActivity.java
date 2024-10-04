package com.example.savr_democodigo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import org.opencv.objdetect.BarcodeDetector;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private JavaCameraView javaCameraView;
    private Mat mRgba;
    private TextView barcodeText;
    private boolean isProcessingFrame = false;

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;

    private BarcodeDetector barcodeDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa OpenCV
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "No se pudo cargar OpenCV", Toast.LENGTH_SHORT).show();
        }

        // Inicializa el detector de códigos de barras
        barcodeDetector = new BarcodeDetector();

        // Solicita permisos de cámara si es necesario
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            initializeCamera();
        }
    }

    private void initializeCamera() {
        // Inicializa la vista de la cámara y el TextView
        javaCameraView = findViewById(R.id.java_camera_view);
        javaCameraView.setVisibility(SurfaceView.VISIBLE);
        javaCameraView.setCameraIndex(0); // Establece la cámara trasera
        javaCameraView.setCvCameraViewListener(this);
        javaCameraView.enableView();

        barcodeText = findViewById(R.id.barcode_text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (javaCameraView != null) {
            javaCameraView.enableView();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (javaCameraView != null)
            javaCameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (javaCameraView != null)
            javaCameraView.disableView();
    }

    // Métodos de la interfaz CvCameraViewListener2
    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat();
    }

    @Override
    public void onCameraViewStopped() {
        if (mRgba != null)
            mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        if (!isProcessingFrame) {
            isProcessingFrame = true;

            Mat gray = inputFrame.gray();

            // Procesa el frame en un hilo separado
            new Thread(() -> {
                String decodedText = detectAndDecodeBarcode(gray);

                runOnUiThread(() -> {
                    if (decodedText != null) {
                        barcodeText.setText("Código de Barras: " + decodedText);
                    } else {
                        barcodeText.setText("No se detectó código de barras");
                    }
                    isProcessingFrame = false;
                });
            }).start();
        }

        return mRgba;
    }

    // Método para detectar y decodificar el código de barras usando OpenCV
    private String detectAndDecodeBarcode(Mat img) {
        // Listas para almacenar la información decodificada
        List<String> decodedInfo = new ArrayList<>();
        List<String> decodedType = new ArrayList<>();
        Mat points = new Mat();

        // Ambos detecta y decodifica el código de barras
        boolean found = barcodeDetector.detectAndDecodeWithType(img, decodedInfo, decodedType, points);

        if (found && !decodedInfo.isEmpty()) {
            // Dibuja los contornos del código de barras en la imagen
            drawBarcodeContours(points);

            // Retorna el primer código de barras detectado
            return decodedInfo.get(0);
        } else {
            return null;
        }
    }

    // Método para dibujar los contornos del código de barras en la imagen
    private void drawBarcodeContours(Mat points) {
        if (points.empty()) return;

        List<MatOfPoint> corners = new ArrayList<>();
        int totalCodes = points.rows() / 4; // Cada código de barras tiene 4 puntos

        for (int i = 0; i < totalCodes; i++) {
            // Extraemos los 4 puntos para cada código de barras
            MatOfPoint2f corner2f = new MatOfPoint2f(
                    new org.opencv.core.Point(points.get(i * 4, 0)),
                    new org.opencv.core.Point(points.get(i * 4 + 1, 0)),
                    new org.opencv.core.Point(points.get(i * 4 + 2, 0)),
                    new org.opencv.core.Point(points.get(i * 4 + 3, 0))
            );

            // Convertimos MatOfPoint2f a MatOfPoint
            MatOfPoint corner = new MatOfPoint();
            corner2f.convertTo(corner, CvType.CV_32S);

            corners.add(corner);
        }

        // Dibuja los contornos en la imagen mRgba
        Imgproc.polylines(mRgba, corners, true, new Scalar(0, 255, 0), 2);
    }

    // Maneja la respuesta de la solicitud de permisos
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido, inicializa la cámara
                initializeCamera();
            } else {
                // Permiso denegado, muestra un mensaje y cierra la aplicación
                Toast.makeText(this, "Permiso de cámara es necesario", Toast.LENGTH_LONG).show();
                finish();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
