import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../../core/realtime/realtime_connection_state.dart';
import '../../../core/theme/strix_brand.dart';
import '../data/notification_event_store.dart';
import '../data/notification_item.dart';
import 'widgets/notification_card.dart';
import 'widgets/realtime_connection_banner.dart';

class NotificationsPage extends StatefulWidget {
  final NotificationEventStore store;
  final RealtimeConnectionState connectionState;
  final Stream<RealtimeConnectionState> connectionStates;
  final ValueChanged<String>? onOpenViolation;

  const NotificationsPage({
    super.key,
    required this.store,
    required this.connectionState,
    required this.connectionStates,
    this.onOpenViolation,
  });

  @override
  State<NotificationsPage> createState() => _NotificationsPageState();
}

class _NotificationsPageState extends State<NotificationsPage> {
  final Set<String> _dismissedIds = {};
  final Set<String> _seenIds = {};

  List<NotificationItem> _visible(List<NotificationItem> items) => [
        for (final item in items)
          if (!_dismissedIds.contains(item.violationId)) item,
      ];

  void _dismiss(NotificationItem item) {
    setState(() => _dismissedIds.add(item.violationId));
  }

  void _open(NotificationItem item) {
    setState(() => _seenIds.add(item.violationId));
    widget.onOpenViolation?.call(item.violationId);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: StrixBrand.background,
      body: StreamBuilder<List<NotificationItem>>(
        initialData: widget.store.items,
        stream: widget.store.changes,
        builder: (context, itemSnapshot) {
          return StreamBuilder<RealtimeConnectionState>(
            initialData: widget.connectionState,
            stream: widget.connectionStates,
            builder: (context, connectionSnapshot) {
              final items = _visible(
                itemSnapshot.data ?? widget.store.items,
              );
              final connection =
                  connectionSnapshot.data ?? widget.connectionState;
              return _buildBody(items, connection);
            },
          );
        },
      ),
    );
  }

  Widget _buildBody(
    List<NotificationItem> visible,
    RealtimeConnectionState connection,
  ) {
    final connecting = connection == RealtimeConnectionState.connecting;
    final reconnecting = connection == RealtimeConnectionState.reconnecting;

    if (visible.isEmpty && connecting) {
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          _header(),
          RealtimeConnectionBanner(state: connection),
          const SizedBox(height: 80),
          const Center(
            child: CircularProgressIndicator(color: StrixBrand.primary),
          ),
        ],
      );
    }

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        _header(),
        RealtimeConnectionBanner(state: connection),
        if (visible.isEmpty)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 20),
            decoration: BoxDecoration(
              color: StrixBrand.surface,
              borderRadius: BorderRadius.circular(StrixBrand.radiusCard),
              border: Border.all(color: StrixBrand.border),
            ),
            child: Column(
              children: [
                Icon(
                  connection == RealtimeConnectionState.offline
                      ? Icons.cloud_off_outlined
                      : Icons.notifications_none_outlined,
                  size: 36,
                  color: StrixBrand.textSecondary,
                ),
                const SizedBox(height: 12),
                Text(
                  reconnecting
                      ? 'Yeniden bağlanırken bildirim bekleniyor.'
                      : connection == RealtimeConnectionState.offline
                          ? 'Çevrimdışı. Yeni bildirim alınamıyor.'
                          : 'Bildirim bulunmuyor.',
                  textAlign: TextAlign.center,
                  style: GoogleFonts.inter(
                    fontWeight: FontWeight.w600,
                    color: StrixBrand.textPrimary,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  connection == RealtimeConnectionState.offline
                      ? 'Önceki bildirimler korunur; bağlantı gelince yenileri düşer.'
                      : 'Yeni ihlal uyarıları burada görünür.',
                  textAlign: TextAlign.center,
                  style: GoogleFonts.inter(
                    fontSize: 13,
                    color: StrixBrand.textSecondary,
                  ),
                ),
              ],
            ),
          )
        else
          ...visible.map(
            (item) => NotificationCard(
              key: ValueKey(item.violationId),
              item: item,
              seen: _seenIds.contains(item.violationId),
              onOpen: () => _open(item),
              onDismiss: () => _dismiss(item),
            ),
          ),
      ],
    );
  }

  Widget _header() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Bildirimler',
            style: GoogleFonts.inter(
              fontSize: 22,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 6),
          Text(
            'Canlı ihlal uyarıları. Gizlenen kartlar yalnızca bu oturumda kaybolur.',
            style: GoogleFonts.inter(
              fontSize: 14,
              color: StrixBrand.textSecondary,
            ),
          ),
        ],
      ),
    );
  }
}
