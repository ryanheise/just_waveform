package com.ryanheise.just_waveform;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import android.os.Handler;
import java.util.List;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import java.util.HashMap;

/** JustWaveformPlugin */
public class JustWaveformPlugin implements FlutterPlugin, MethodCallHandler {
    private MethodChannel channel;
    private Handler handler = new Handler();

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "com.ryanheise.just_waveform");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
        switch (call.method) {
        case "extract":
            String audioInPath = call.argument("audioInPath");
            String waveOutPath = call.argument("waveOutPath");
            String uuid = call.argument("uuid");
            Integer samplesPerPixel = call.argument("samplesPerPixel");
            Integer pixelsPerSecond = call.argument("pixelsPerSecond");
            WaveformExtractor waveformExtractor = new WaveformExtractor(audioInPath, waveOutPath, samplesPerPixel, pixelsPerSecond);
            waveformExtractor.start(new WaveformExtractor.OnProgressListener() {
                @Override
                public void onProgress(int progress) {
                    HashMap<String, Object> args = new HashMap();
                    args.put("progress", progress);
                    args.put("waveOutFile", waveOutPath);
                    args.put("uuid", uuid);

                    invokeMethod("onProgress", args);
                }

                @Override
                public void onComplete() {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            result.success(null);
                        }
                    });
                }

                @Override
                public void onError(final String message) {
                    invokeMethod("onError", message);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            result.error(message, null, null);
                        }
                    });
                }
            });
            break;
        default:
            result.notImplemented();
            break;
        }
    }

    private void invokeMethod(final String method, final Object arguments) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                System.out.println("invokeMethod " + method + "(" + arguments + ")");
                channel.invokeMethod(method, arguments);
            }
        });
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }
}
