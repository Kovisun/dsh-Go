import 'package:flutter_webrtc/flutter_webrtc.dart';

/// The audio attributes libwebrtc's playback track gets for DSH Remote's direct path.
///
/// That path carries a DataChannel only, but starting the peer connection factory still builds a
/// `JavaAudioDeviceModule`, and its player defaults to `USAGE_VOICE_COMMUNICATION` with
/// `CONTENT_TYPE_SPEECH`. Android then treats the app as a call: the volume keys move to the call
/// stream, other players are ducked through the call DSP, and everything the app plays afterwards
/// (a chat video, a Douyin clip) follows the communication route to the earpiece. Nothing plays
/// through that track, so media is both the honest usage and the one that leaves the device alone.
///
/// Reported upstream as ccch1mneyyy/dsh-Go#839 (issues #646, #738, #780).
AndroidAudioConfiguration dshRemoteAudioConfiguration() => AndroidAudioConfiguration(
  // No audio stream exists here; a focus request would only take it from other apps.
  manageAudioFocus: false,
  androidAudioMode: AndroidAudioMode.normal,
  androidAudioStreamType: AndroidAudioStreamType.music,
  androidAudioAttributesUsageType: AndroidAudioAttributesUsageType.media,
  androidAudioAttributesContentType: AndroidAudioAttributesContentType.speech,
);

Future<void>? _initialized;

/// Initializes flutter_webrtc with [dshRemoteAudioConfiguration], once.
///
/// `WebRTC.initialize` is process-global and only its first call counts — the plugin initializes
/// itself with the defaults if anything reaches it earlier — so this must run before the first
/// `createPeerConnection`. The Android-only options are ignored on iOS, where this is the call the
/// plugin would have made on its own.
Future<void> ensureWebRtcAudioConfiguration() => _initialized ??= _initialize();

Future<void> _initialize() async {
  try {
    await WebRTC.initialize(options: {'androidAudioConfiguration': dshRemoteAudioConfiguration().toMap()});
  } on Object {
    // A failed initialization must not pin every later connection attempt to it.
    _initialized = null;
    rethrow;
  }
}
