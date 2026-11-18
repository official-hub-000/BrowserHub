solutions = [
  {
    'name': 'src',
    'url': 'https://chromium.googlesource.com/chromium/src.git',
    'managed': False,
    'custom_deps': {
      'src/third_party/angle/third_party/VK-GL-CTS/src': None,
    },
    'custom_vars': {
      'checkout_pgo_profiles': True,
      'checkout_telemetry_dependencies': False,
    },
  },
]
hooks = [
  {
    'name': 'fetch_filter_lists',
    'condition': 'checkout_android',
    'action': ['python3',
              '../vanadium/tools/filter_lists/filter_list_download.py',
              '--output',
              'src/titanium/android_config/filter_lists/filter_lists_easylist.txt',
              '--urls',
              'https://easylist-downloads.adblockplus.org/antiadblockfilters.txt',
              'https://easylist.to/easylist/easylist.txt',
              'https://easylist.to/easylist/easyprivacy.txt']
  },
  {
    'name': 'apply_subprojects_patches',
    'condition': 'checkout_android',
    'action': ['python3',
              '../vanadium/tools/common/apply_subprojects_patches.py',
              '--base_patch_dir',
              '../vanadium/subprojects_patches',
              '--src_dir',
              'src']
  },
  {
    'name': 'fetch_titanium_extension',
    'condition': 'checkout_android',
    'action': ['python3',
              '../extensions/bundle.py',
              '../extensions/dist',
              'titanium',
              # Must be a URL that actually serves a .crx. The upstream default
              # pointed at a third-party release asset, which a BrowserHub build
              # cannot rely on. To bundle the Titanium Extension, point this at
              # your own release asset; leave it unset and bundle.py simply
              # stages nothing, which is a valid build.
              None]
  },
]
target_os = ['android']