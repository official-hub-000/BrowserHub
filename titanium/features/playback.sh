# playback — Titanium feature block
#
# Neutralise the Android media-playback-options guard so background/autoplay behaviour matches desktop.
#
# Sourced by ../patch.sh when ENABLE_PLAYBACK=1 (default: on).
# Statements below are verbatim from upstream Titanium patch.sh.

sed -i 's|#if BUILDFLAG(IS_ANDROID)|#if 0|' content/public/renderer/render_frame_media_playback_options.cc
