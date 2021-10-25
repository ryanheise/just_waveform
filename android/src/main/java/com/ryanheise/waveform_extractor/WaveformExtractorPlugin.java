package com.ryanheise.waveform_extractor;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import android.os.Handler;
import java.util.List;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/** WaveformExtractorPlugin */
public class WaveformExtractorPlugin implements FlutterPlugin, MethodCallHandler {
    private MethodChannel channel;
    private Handler handler = new Handler();

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getFlutterEngine().getDartExecutor(), "com.ryanheise.waveform_extractor");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
        switch (call.method) {
        case "extract":
            List<?> args = (List<?>)call.arguments;
            String audioInPath = (String)args.get(0);
            String waveOutPath = (String)args.get(1);
            WaveformExtractor waveformExtractor = new WaveformExtractor(audioInPath, waveOutPath);
            waveformExtractor.start(new WaveformExtractor.OnProgressListener() {
                @Override
                public void onProgress(int progress) {
                    invokeMethod("onProgress", progress);
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
