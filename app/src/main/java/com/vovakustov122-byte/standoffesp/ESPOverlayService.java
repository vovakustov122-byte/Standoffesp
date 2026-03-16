package com.vovakustov122_byte.standoffesp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.core.app.NotificationCompat;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ESPOverlayService extends Service {
    
    private static final String CHANNEL_ID = "ESPChannel";
    private static final int NOTIFICATION_ID = 1;
    
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    
    private WindowManager windowManager;
    private FrameLayout overlayLayout;
    private ESPView espView;
    private Button closeButton;
    
    private HandlerThread frameHandlerThread;
    private Handler frameHandler;
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    
    private int screenWidth, screenHeight;
    private int resultCode;
    private Intent data;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Инициализация OpenCV
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, null);
        }
        
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        // Получаем размер экрана
        screenWidth = windowManager.getDefaultDisplay().getWidth();
        screenHeight = windowManager.getDefaultDisplay().getHeight();
        
        // Создаем поток для обработки кадров
        frameHandlerThread = new HandlerThread("FrameProcessor");
        frameHandlerThread.start();
        frameHandler = new Handler(frameHandlerThread.getLooper());
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            resultCode = intent.getIntExtra("resultCode", 0);
            data = intent.getParcelableExtra("data");
            
            // Создаем оверлей
            createOverlay();
            
            // Запускаем захват экрана
            startScreenCapture();
            
            // Запускаем обработку кадров
            startFrameProcessing();
        }
        return START_STICKY;
    }
    
    private void createOverlay() {
        // Параметры оверлея
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        
        // Создаем layout для оверлея
        overlayLayout = new FrameLayout(this);
        
        // Создаем View для ESP
        espView = new ESPView(this);
        overlayLayout.addView(espView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ));
        
        // Создаем кнопку закрытия
        closeButton = new Button(this);
        closeButton.setText("✕");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setBackgroundColor(Color.RED);
        closeButton.setAlpha(0.7f);
        
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
            dpToPx(50), dpToPx(50)
        );
        btnParams.gravity = Gravity.TOP | Gravity.END;
        btnParams.topMargin = dpToPx(10);
        btnParams.rightMargin = dpToPx(10);
        
        closeButton.setLayoutParams(btnParams);
        closeButton.setOnClickListener(v -> stopESP());
        
        overlayLayout.addView(closeButton);
        
        // Добавляем на экран
        windowManager.addView(overlayLayout, params);
    }
    
    private void startScreenCapture() {
        MediaProjectionManager projectionManager = (MediaProjectionManager) 
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        
        // Создаем ImageReader для получения кадров
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, 
            PixelFormat.RGBA_8888, 2);
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            screenWidth, screenHeight,
            windowManager.getDefaultDisplay().getRefreshRate(),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(),
            null, null
        );
    }
    
    private void startFrameProcessing() {
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image == null) return;
            
            frameHandler.post(() -> processImage(image));
        }, frameHandler);
    }
    
    private void processImage(Image image) {
        if (!isRunning.get()) {
            image.close();
            return;
        }
        
        // Конвертируем Image в Bitmap
        Bitmap bitmap = imageToBitmap(image);
        
        // Конвертируем в Mat для OpenCV
        Mat frame = new Mat();
        Utils.bitmapToMat(bitmap, frame);
        
        // 1. Преобразуем в HSV для поиска по цвету
        Mat hsv = new Mat();
        Imgproc.cvtColor(frame, hsv, Imgproc.COLOR_RGB2HSV);
        
        // 2. Ищем врагов (красные ники/элементы)
        Scalar lowerRed1 = new Scalar(0, 100, 100);
        Scalar upperRed1 = new Scalar(10, 255, 255);
        Scalar lowerRed2 = new Scalar(160, 100, 100);
        Scalar upperRed2 = new Scalar(179, 255, 255);
        
        Mat mask1 = new Mat();
        Mat mask2 = new Mat();
        Core.inRange(hsv, lowerRed1, upperRed1, mask1);
        Core.inRange(hsv, lowerRed2, upperRed2, mask2);
        
        Mat mask = new Mat();
        Core.bitwise_or(mask1, mask2, mask);
        
        // 3. Находим контуры
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(mask, contours, hierarchy, 
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        
        // 4. Фильтруем и собираем врагов
        List<Enemy> enemies = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            
            // Фильтруем по размеру (должен быть похож на человека/врага)
            if (rect.width > 30 && rect.width < 200 && 
                rect.height > 60 && rect.height < 400) {
                
                // Добавляем врага
                enemies.add(new Enemy(
                    rect.x, rect.y, 
                    rect.x + rect.width, 
                    rect.y + rect.height
                ));
            }
        }
        
        // 5. Обновляем ESP View
        espView.updateEnemies(enemies);
        
        // Очистка
        frame.release();
        hsv.release();
        mask.release();
        mask1.release();
        mask2.release();
        
        image.close();
          }
      private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        
        Bitmap bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride, 
            screenHeight, 
            Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);
        
        return Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
    }
    
    // View для рисования ESP
    private class ESPView extends View {
        
        private List<Enemy> enemies = new ArrayList<>();
        private Paint boxPaint = new Paint();
        private Paint textPaint = new Paint();
        
        public ESPView(Context context) {
            super(context);
            
            boxPaint.setColor(Color.RED);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(5f);
            
            textPaint.setColor(Color.RED);
            textPaint.setTextSize(30f);
            textPaint.setStyle(Paint.Style.FILL);
        }
        
        public void updateEnemies(List<Enemy> newEnemies) {
            this.enemies = newEnemies;
            postInvalidate(); // Перерисовать
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            for (Enemy enemy : enemies) {
                // Рисуем рамку
                canvas.drawRect(
                    enemy.left, enemy.top, 
                    enemy.right, enemy.bottom, 
                    boxPaint
                );
                
                // Рисуем метку
                canvas.drawText(
                    "ENEMY", 
                    enemy.left, 
                    enemy.top - 10, 
                    textPaint
                );
            }
        }
    }
    
    // Класс для хранения координат врага
    private static class Enemy {
        int left, top, right, bottom;
        
        Enemy(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }
    
    private void stopESP() {
        isRunning.set(false);
        
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        if (overlayLayout != null) {
            windowManager.removeView(overlayLayout);
            overlayLayout = null;
        }
        
        stopForeground(true);
        stopSelf();
    }
    
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "ESP Service",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Standoff ESP")
            .setContentText("ESP активен")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        stopESP();
        super.onDestroy();
    }
}
