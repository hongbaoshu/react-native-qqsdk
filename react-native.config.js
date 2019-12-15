module.exports = {
    // config for a library is scoped under "dependency" key
    dependency: {
      platforms: {
        ios: {
            "sharedLibraries": [
              "libz",
              "libiconv",
              "libsqlite3",
              "libstdc++",
              "Security",
              "SystemConfiguration",
              "CoreTelephony",
              "CoreGraphics"
            ]
          },
        android: {}, // projects are grouped into "platforms"
      },
      // hooks are considered anti-pattern, please avoid them
      hooks: {
        "postlink": "node node_modules/react-native-qqsdk/scripts/postlink/postlink",
        "postunlink": "node node_modules/react-native-qqsdk/scripts/postunlink/postunlink"
      },
    },
  };