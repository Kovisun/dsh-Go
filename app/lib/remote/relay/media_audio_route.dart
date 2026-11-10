import 'dart:io';

import 'package:flutter/services.dart';

/// Puts Android's media audio back on the loudspeaker.
///
/// libwebrtc (flutter_webrtc, the transport of the DSH Remote peer link) switches the audio
/// system into the communication route, so everything else the app plays — a chat video, a
/// Douyin clip — comes out of the earpiece while the link is up. The native half restores
/// `MODE_NORMAL` and the speakerphone route; platforms without that channel and ROMs that
/// refuse the change answer `false` and the caller carries on.
const MethodChannel _channel = MethodChannel('dsh/native-media');

/// Ask the native half to restore the media route.
/// @returns whether the audio system had to be changed.
Future<bool> restoreMediaAudioRoute() async {
  if (!Platform.isAndroid) return false;
  try {
    return await _channel.invokeMethod<bool>('restoreMediaAudioRoute') ?? false;
  } on MissingPluginException {
    // Desktop and test hosts have no native audio route to restore.
    return false;
  } on PlatformException {
    return false;
  }
}
