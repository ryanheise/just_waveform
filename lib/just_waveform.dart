import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/services.dart';

class JustWaveform {
  static const MethodChannel _channel =
      MethodChannel('com.ryanheise.just_waveform');

  /// Extract a waveform from [audioInFile] and write it to [waveOutFile].
  // XXX: It would be better to return a stream of the actual [Waveform], with
  // progress => wave.data.length / (wave.length*2)
  static Stream<WaveformProgress> extract({
    required File audioInFile,
    required File waveOutFile,
  }) {
    final progressController = StreamController<WaveformProgress>.broadcast();
    progressController.add(WaveformProgress._(0.0, null));
    _channel.setMethodCallHandler((MethodCall call) async {
      switch (call.method) {
        case 'onProgress':
          // ignore: avoid_print
          print("received onProgress: ${call.arguments}}");
          int progress = call.arguments;
          //print("_progressSubject.add($progress)");
          Waveform? waveform;
          if (progress == 100) {
            waveform = await parse(waveOutFile);
          }
          progressController.add(WaveformProgress._(progress / 100, waveform));
          if (progress == 100) {
            progressController.close();
          }
          break;
      }
    });
    _channel.invokeMethod('extract', [
      audioInFile.path,
      waveOutFile.path,
    ]).catchError(progressController.addError);
    return progressController.stream;
  }

  static Future<Waveform> parse(File waveformFile) async {
    final bytes = Uint8List.fromList(await waveformFile.readAsBytes()).buffer;
    const headerLength = 20;
    final header = Uint32List.view(bytes, 0, headerLength);
    final data = Int16List.view(bytes, headerLength);
    return Waveform(
      version: header[0],
      flags: header[1],
      sampleRate: header[2],
      samplesPerPixel: header[3],
      length: header[4],
      data: data,
    );
  }
}

class WaveformProgress {
  final double progress;
  final Waveform? waveform;

  WaveformProgress._(this.progress, this.waveform);
}

/// Audio waveform data in the
/// [audiowaveform](https://github.com/bbc/audiowaveform) format, suitable for
/// visual rendering.
class Waveform {
  /// The format version.
  final int version;

  /// A bit field where bit 0 indicates the resolution of each pixel value. 0
  /// indicates 16-bit resolution, while 1 indicates 8-bit resolution.
  final int flags;

  /// The sample rate in Hertz of the original audio.
  final int sampleRate;

  /// The number of samples captured for each pixel.
  final int samplesPerPixel;

  /// The number of pixels in the data.
  final int length;

  /// A list of min/max pairs, each representing a pixel. For each pixel, the
  /// min/max pair represents the minimum and maximum sample over the
  /// [samplesPerPixel] range.
  final Int16List data;

  Waveform({
    required this.version,
    required this.flags,
    required this.sampleRate,
    required this.samplesPerPixel,
    required this.length,
    required this.data,
  });

  /// Returns the [data] at index [i], or zero when out of bounds.
  int operator [](int i) => i >= 0 && i < data.length ? data[i] : 0;

  /// Returns the minimum sample for pixel [i].
  int getPixelMin(int i) => this[2 * i];

  /// Returns the maximum sample for pixel [i].
  int getPixelMax(int i) => this[2 * i + 1];

  /// The duration of audio, inferred from the length of the waveform data.
  Duration get duration => Duration(
      microseconds: 1000 * 1000 * length * samplesPerPixel ~/ sampleRate);

  /// Converts an audio position to a pixel position. The returned position is a
  /// [double] for accuracy, but can be converted `toInt` and used to access the
  /// nearest pixel value via [getPixelMin]/[getPixelMax].
  double positionToPixel(Duration position) =>
      position.inMicroseconds * sampleRate / (samplesPerPixel * 1000000);
}
