#import <AudioToolbox/AudioServices.h>
#import <AudioToolbox/ExtendedAudioFile.h>
#import <TargetConditionals.h>
#if TARGET_OS_OSX
#import <FlutterMacOS/FlutterMacOS.h>
#else
#import <Flutter/Flutter.h>
#endif

@interface JustWaveformPlugin : NSObject<FlutterPlugin>
@end
