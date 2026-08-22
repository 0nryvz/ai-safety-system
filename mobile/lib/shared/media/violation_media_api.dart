import '../../core/network/authenticated_api.dart';
import 'media_url.dart';

/// Presigned clip/cover URL istemcisi.
///
/// Yalnız `AuthenticatedApi` kullanır; ikinci HTTP/auth client yoktur.
/// İstek path'inde yalnızca `violationId` vardır — `objectKey` /
/// `coverImageKey` / `playbackUrl` gönderilmez ve okunmaz.
class ViolationMediaApi {
  final Future<Map<String, dynamic>> Function(String path) getJson;

  ViolationMediaApi({required this.getJson});

  factory ViolationMediaApi.fromAuthenticated(AuthenticatedApi api) {
    return ViolationMediaApi(getJson: api.getJson);
  }

  static String clipUrlPath(String violationId) =>
      '/api/v1/violations/${Uri.encodeComponent(violationId)}/clip-url';

  static String coverUrlPath(String violationId) =>
      '/api/v1/violations/${Uri.encodeComponent(violationId)}/cover-url';

  /// `GET /api/v1/violations/{id}/clip-url`
  Future<MediaUrl> fetchClipUrl(String violationId) {
    return _fetch(clipUrlPath(violationId));
  }

  /// `GET /api/v1/violations/{id}/cover-url`
  Future<MediaUrl> fetchCoverUrl(String violationId) {
    return _fetch(coverUrlPath(violationId));
  }

  Future<MediaUrl> _fetch(String path) async {
    final json = await getJson(path);
    return MediaUrl.fromJson(json);
  }
}
