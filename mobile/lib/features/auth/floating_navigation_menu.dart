import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

import '../../core/theme/strix_brand.dart';
import 'shell_destinations.dart';

/// Floating speed-dial navigation overlay. Open/close, animation and
/// dismiss behavior live here; destination policy stays in [AppShell].
class FloatingNavigationMenu extends StatefulWidget {
  static const Key toggleKey = Key('floating_nav_toggle');
  static const Key overlayKey = Key('floating_nav_overlay');

  /// Extra bottom inset so nested page FABs / lists clear the toggle.
  static const double actionClearance = 72;

  static Key itemKey(ShellTab tab) => Key('floating_nav_item_${tab.name}');

  final List<ShellDestination> items;
  final int selectedIndex;
  final ValueChanged<int> onSelected;
  final Widget child;

  /// When false, system back is intercepted and [onBlockedPop] is called
  /// (after the open menu is dismissed).
  final bool allowRoutePop;
  final VoidCallback? onBlockedPop;

  const FloatingNavigationMenu({
    super.key,
    required this.items,
    required this.selectedIndex,
    required this.onSelected,
    required this.child,
    this.allowRoutePop = true,
    this.onBlockedPop,
  });

  @override
  State<FloatingNavigationMenu> createState() => _FloatingNavigationMenuState();
}

class _FloatingNavigationMenuState extends State<FloatingNavigationMenu>
    with SingleTickerProviderStateMixin {
  static const _duration = Duration(milliseconds: 220);
  static const _toggleSize = 56.0;
  static const _itemButtonSize = 48.0;

  late final AnimationController _controller;
  bool _open = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: _duration);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _openMenu() {
    if (_open) {
      return;
    }
    setState(() => _open = true);
    _controller.forward();
  }

  void _closeMenu() {
    if (!_open && _controller.isDismissed) {
      return;
    }
    setState(() => _open = false);
    _controller.reverse();
  }

  void _toggle() {
    if (_open) {
      _closeMenu();
    } else {
      _openMenu();
    }
  }

  void _select(int index) {
    _closeMenu();
    widget.onSelected(index);
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: !_open && widget.allowRoutePop,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) {
          return;
        }
        if (_open) {
          _closeMenu();
          return;
        }
        widget.onBlockedPop?.call();
      },
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, _) {
          final progress = _controller.value;
          final showingItems = progress > 0;

          return Stack(
            children: [
              widget.child,
              Positioned.fill(
                child: IgnorePointer(
                  ignoring: progress == 0,
                  child: GestureDetector(
                    key: FloatingNavigationMenu.overlayKey,
                    onTap: _closeMenu,
                    behavior: HitTestBehavior.opaque,
                    child: ColoredBox(
                      color: StrixBrand.textPrimary.withValues(
                        alpha: 0.22 * progress,
                      ),
                    ),
                  ),
                ),
              ),
              Positioned(
                right: 16,
                bottom: 16,
                left: showingItems ? 16 : null,
                child: SafeArea(
                  child: _buildCluster(context, progress, showingItems),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _buildCluster(
    BuildContext context,
    double progress,
    bool showingItems,
  ) {
    final media = MediaQuery.of(context);
    final maxMenuHeight = math.max(
      96.0,
      media.size.height -
          media.padding.top -
          media.padding.bottom -
          _toggleSize -
          48,
    );
    final maxLabelWidth = math.min(180.0, media.size.width - 120);

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        if (showingItems)
          ConstrainedBox(
            constraints: BoxConstraints(maxHeight: maxMenuHeight),
            child: SingleChildScrollView(
              reverse: true,
              padding: const EdgeInsets.only(bottom: 12),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  for (var i = widget.items.length - 1; i >= 0; i--)
                    _NavAction(
                      destination: widget.items[i],
                      selected: i == widget.selectedIndex,
                      maxLabelWidth: maxLabelWidth,
                      progress: _staggeredProgress(
                        progress,
                        widget.items.length - 1 - i,
                        widget.items.length,
                      ),
                      buttonSize: _itemButtonSize,
                      onTap: () => _select(i),
                    ),
                ],
              ),
            ),
          ),
        _ToggleButton(
          open: _open,
          size: _toggleSize,
          onPressed: _toggle,
        ),
      ],
    );
  }

  double _staggeredProgress(double progress, int visualIndex, int count) {
    if (count <= 1) {
      return progress;
    }
    final start = math.min(0.35, visualIndex * 0.06);
    if (progress <= start) {
      return 0;
    }
    return ((progress - start) / (1 - start)).clamp(0.0, 1.0);
  }
}

class _ToggleButton extends StatelessWidget {
  final bool open;
  final double size;
  final VoidCallback onPressed;

  const _ToggleButton({
    required this.open,
    required this.size,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: open ? 'Kapat' : 'Menü',
      child: Tooltip(
        message: open ? 'Kapat' : 'Menü',
        child: Material(
          key: FloatingNavigationMenu.toggleKey,
          color: StrixBrand.primary,
          elevation: 4,
          shadowColor: StrixBrand.textPrimary.withValues(alpha: 0.28),
          shape: const CircleBorder(),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onPressed,
            customBorder: const CircleBorder(),
            child: SizedBox.square(
              dimension: size,
              child: Icon(
                open ? Icons.close_rounded : Icons.menu_rounded,
                color: Colors.white,
                size: 26,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _NavAction extends StatelessWidget {
  final ShellDestination destination;
  final bool selected;
  final double maxLabelWidth;
  final double progress;
  final double buttonSize;
  final VoidCallback onTap;

  const _NavAction({
    required this.destination,
    required this.selected,
    required this.maxLabelWidth,
    required this.progress,
    required this.buttonSize,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final curved = Curves.easeOutCubic.transform(progress);

    return Opacity(
      opacity: curved,
      child: Transform.translate(
        offset: Offset(0, 10 * (1 - curved)),
        child: Transform.scale(
          scale: 0.92 + (0.08 * curved),
          alignment: Alignment.centerRight,
          child: Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Align(
              alignment: Alignment.centerRight,
              child: Semantics(
                button: true,
                selected: selected,
                label: destination.label,
                child: Material(
                  key: FloatingNavigationMenu.itemKey(destination.tab),
                  color: Colors.transparent,
                  child: InkWell(
                    onTap: onTap,
                    borderRadius: BorderRadius.circular(28),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        _LabelChip(
                          label: destination.label,
                          selected: selected,
                          maxWidth: maxLabelWidth,
                        ),
                        const SizedBox(width: 8),
                        _ItemButton(
                          icon: selected
                              ? destination.selectedIcon
                              : destination.icon,
                          selected: selected,
                          size: buttonSize,
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _LabelChip extends StatelessWidget {
  final String label;
  final bool selected;
  final double maxWidth;

  const _LabelChip({
    required this.label,
    required this.selected,
    required this.maxWidth,
  });

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: BoxConstraints(maxWidth: maxWidth),
      child: Material(
        color: selected
            ? StrixBrand.primary.withValues(alpha: 0.12)
            : StrixBrand.surface,
        elevation: selected ? 2 : 1,
        shadowColor: StrixBrand.textPrimary.withValues(alpha: 0.16),
        borderRadius: BorderRadius.circular(20),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: selected ? StrixBrand.primary : StrixBrand.border,
              width: selected ? 1.5 : 1,
            ),
          ),
          child: Text(
            label,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: GoogleFonts.inter(
              fontSize: 13,
              fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
              color: selected ? StrixBrand.primary : StrixBrand.textPrimary,
            ),
          ),
        ),
      ),
    );
  }
}

class _ItemButton extends StatelessWidget {
  final IconData icon;
  final bool selected;
  final double size;

  const _ItemButton({
    required this.icon,
    required this.selected,
    required this.size,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? StrixBrand.primary : StrixBrand.surface,
      elevation: selected ? 3 : 2,
      shadowColor: StrixBrand.textPrimary.withValues(alpha: 0.2),
      shape: CircleBorder(
        side: BorderSide(
          color: selected ? StrixBrand.primary : StrixBrand.border,
          width: selected ? 2 : 1,
        ),
      ),
      child: SizedBox.square(
        dimension: size,
        child: Icon(
          icon,
          size: 22,
          color: selected ? Colors.white : StrixBrand.primary,
        ),
      ),
    );
  }
}
