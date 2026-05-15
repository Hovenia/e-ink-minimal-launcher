# E-ink Minimal Launcher

A minimalist, E-ink friendly Android launcher designed for Boox Palma, Leaf 3, Lenovo Y700, and similar devices.

<p align="center">
  <img src="assets/photo.jpg" width="400" alt="Launcher on Device" />
</p>

## Features

- **Minimalist Design**: Clean interface optimized for E-ink displays and tablets.
- **E-ink Optimization**: High-contrast white theme, forced light mode, and removed shadows/animations for a ghosting-free experience.
- **Dynamic Column Grid**: Choose between 1 to 5 columns for favorites (supports 4 to 20 apps) depending on your device's screen size.
- **Niagara-style Index Bar**: "Wave" effect with large bold fonts and high-contrast selection markers for easy navigation.
- **Improved Accessibility**: Larger touch targets for buttons (48dp settings button).
- **Korean Support**: Full support for Korean consonants (ㄱ, ㄴ, ㄷ...) in the index bar.
- **Drag & Drop**: Long press items in the favorites view to reorder them.

## Versioning

- **v1.4.0**: 
    - Complete E-ink UI optimization (forced light mode, high-contrast white theme for all dialogs).
    - Removed dialog shadows, frames, and animations to eliminate ghosting and black artifacts.
    - Increased settings button touch target (48dp) for better usability.
    - Refined index bar colors for maximum contrast.
- **v1.3.9**: Initial internal test for settings button fixes.
- **v1.3.7**: Fixed an issue where the scrolling app list overlapped with the top header in the "All Apps" view.
- **v1.3.6**: Fully localized the UI and settings menu based on the selected date language (Korean, English, Japanese).
- **v1.3.5**: Added a setting to choose the date language/format (Korean, English, Japanese).
- **v1.3.4**: Fixed a bug where the day of the week was displayed in English on some devices (e.g., Leaf 3) by forcing the Korean locale for date formatting.
- **v1.3.3**: Added a star (★) index to the top of the index bar in the "All Apps" view for quick access to favorite apps.
- **v1.3.2**: Changed default animation setting to 'off' for better compatibility with low-spec E-ink devices. Added option to enable it in settings.
- **v1.3.1**: Fixed an issue where certain manufacturer-specific apps (like Onyx Launcher) would not open.
- **v1.3.0**: Expanded column layout up to 5 columns (for tablets like Y700) and added an animation toggle setting for E-ink optimization (e.g., Boox Leaf 3).
- **v1.2.1**: Reduced index pop-out intensity and minor UI refinements.
- **v1.2**: Fixed index bar layering (always on top), refined "wave" pop-out visibility.
- **v1.1**: Added column selection (1/2/3), improved index bar aesthetics, and centered favorites layout.
- **v1.0**: Initial stable release.

## Installation

```bash
adb install e-ink-minimal-launcher-v1.4.0.apk
```

## License

MIT License - Copyright (c) 2026 bc1qwerty
