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

/// Hold the media route for one peer link.
///
/// libwebrtc re-asserts its communication route when audio focus or the device list changes,
/// which is what happens the moment local playback starts, so a single restore at connect time
/// loses the loudspeaker again. The native watchdog re-checks once a second while a link is up
/// and only touches the audio system when the route actually drifted.
Future<void> startMediaAudioWatch() async {
  if (!Platform.isAndroid) return;
  try {
    await _channel.invokeMethod<Object?>('startMediaAudioWatch');
  } on MissingPluginException {
    // Nothing to hold on a host without the native channel.
  } on PlatformException {
    // A refused hold must not fail the link.
  }
}

/// Release one peer link's hold on the media route.
Future<void> stopMediaAudioWatch() async {
  if (!Platform.isAndroid) return;
  try {
    await _channel.invokeMethod<Object?>('stopMediaAudioWatch');
  } on MissingPluginException {
    // See [startMediaAudioWatch].
  } on PlatformException {
    // Releasing twice, or after a native teardown, is harmless.
  }
}
