/// A carrier that can carry the Host's permanent session deletion itself.
///
/// [DshHttpCarrier] reaches the two Host routes (`/dsh-go/v1/sessions/deletion` and
/// `/dsh-go/v1/sessions/delete`) with plain HTTP. A carrier that tunnels its calls — the
/// DSH Remote relay — cannot reach an HTTP route, so it answers the same contract as two
/// RPC endpoints instead. The capability is optional on purpose: a Host that does not
/// serve it keeps answering "unsupported" and archive stays the fallback.
library;

/// Permanent-deletion capability of one carrier.
abstract interface class SessionDeletionCarrier {
  /// A fresh capability read; a Host that does not serve the endpoint answers false.
  Future<bool> sessionDeletionSupported({Duration? timeout});

  /// Deletes exactly one confirmed session. Never retried: an unanswered call leaves the
  /// outcome unknown instead of replaying a destructive request.
  Future<void> deleteSessionPermanently(String sessionId, {Duration? timeout});
}
