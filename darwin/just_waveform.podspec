#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint just_waveform.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'just_waveform'
  s.version          = '0.0.1'
  s.summary          = 'Flutter audio waveform extractor'
  s.description      = <<-DESC
A Flutter plugin to extract the waveform from an audio file.
                       DESC
  s.homepage         = 'https://github.com/ryanheise/just_waveform'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'Ryan Heise' => 'ryan@ryanheise.com' }
  s.source           = { :path => '.' }
  s.source_files = 'just_waveform/Sources/just_waveform/**/*.{h,m}'
  s.public_header_files = 'just_waveform/Sources/just_waveform/include/**/*.h'
  s.ios.dependency 'Flutter'
  s.osx.dependency 'FlutterMacOS'
  s.ios.deployment_target = '12.0'
  s.osx.deployment_target = '10.14'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
end
